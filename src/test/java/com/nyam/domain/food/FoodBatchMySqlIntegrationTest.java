package com.nyam.domain.food;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.aop.support.AopUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.nyam.domain.food.batch.FoodCsvFileSupport;
import com.nyam.domain.food.batch.FoodCsvSchema;
import com.nyam.domain.food.batch.FoodImportInput;
import com.nyam.domain.food.batch.FoodImportJobConfiguration;
import com.nyam.domain.food.batch.FoodImportRunner;
import com.nyam.domain.food.service.FoodQueryService;

/**
 * 실제 MySQL 8.4.5에서 Flyway, Batch 영속 상태, chunk rollback과 restart를 검증합니다.
 */
@SpringBootTest(properties = {
        "NYAM_EMAIL_VERIFICATION_HMAC_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "NYAM_AUTH_ACCESS_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE="
})
@Testcontainers(disabledWithoutDocker = true)
class FoodBatchMySqlIntegrationTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.4.5");

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MYSQL_IMAGE);

    @Autowired
    JobLauncher jobLauncher;

    @Autowired
    @Qualifier("foodImportJob")
    Job foodImportJob;

    @Autowired
    JobRepository jobRepository;

    @Autowired
    FoodImportInput foodImportInput;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    FoodQueryService foodQueryService;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    @Qualifier("batchTransactionManager")
    PlatformTransactionManager batchTransactionManager;

    @TempDir
    Path temporaryDirectory;

    /**
     * 각 테스트가 독립적인 Job Instance 식별자와 식품 상태에서 시작하도록 데이터를 정리합니다.
     */
    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM foods");
        jdbcTemplate.update("DELETE FROM BATCH_STEP_EXECUTION_CONTEXT");
        jdbcTemplate.update("DELETE FROM BATCH_JOB_EXECUTION_CONTEXT");
        jdbcTemplate.update("DELETE FROM BATCH_STEP_EXECUTION");
        jdbcTemplate.update("DELETE FROM BATCH_JOB_EXECUTION_PARAMS");
        jdbcTemplate.update("DELETE FROM BATCH_JOB_EXECUTION");
        jdbcTemplate.update("DELETE FROM BATCH_JOB_INSTANCE");
        jdbcTemplate.update("UPDATE BATCH_STEP_EXECUTION_SEQ SET ID = 0");
        jdbcTemplate.update("UPDATE BATCH_JOB_EXECUTION_SEQ SET ID = 0");
        jdbcTemplate.update("UPDATE BATCH_JOB_SEQ SET ID = 0");
    }

    /**
     * 빈 MySQL에 V5/V6가 적용되고 일반 시작에서는 Job이 실행되지 않으며 트랜잭션 관리자가 분리되는지 확인합니다.
     */
    @Test
    void usesPersistentJdbcRepositoryWithoutAutomaticJobLaunch() {
        assertThat(MYSQL.isRunning()).isTrue();
        assertThat(AopUtils.getTargetClass(jobRepository).getName()).contains("SimpleJobRepository");
        assertThat(transactionManager).isInstanceOf(JpaTransactionManager.class);
        assertThat(batchTransactionManager).isInstanceOf(JdbcTransactionManager.class);
        assertThat(batchTransactionManager).isNotSameAs(transactionManager);
        assertThat(count("BATCH_JOB_INSTANCE")).isZero();
        assertThat(count("foods")).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('5', '6') AND success = TRUE",
                Long.class)).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForList("""
                SELECT COLLATION_NAME
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'foods'
                  AND COLUMN_NAME IN ('food_name', 'normalized_name')
                ORDER BY COLUMN_NAME
                """, String.class)).containsExactly("utf8mb4_0900_bin", "utf8mb4_0900_bin");
    }

    /**
     * 유효 fixture의 nullable 영양값, 단위, count와 동일 완료 입력 거절을 확인합니다.
     *
     * @throws Exception fixture 생성 또는 Job 실행에 실패한 경우
     */
    @Test
    void importsValidRowsAndRejectsCompletedIdenticalInput() throws Exception {
        Path csv = writeCsv("valid.csv", List.of(
                row(1, "국밥_돼지머리", "100g", "137", "15.94", "6.70", "5.16"),
                row(2, "마시는 식품", "100ml", "", "0", "1.25", "")));
        JobParameters parameters = parameters(csv, "2026-06-26");

        JobExecution execution = launch(csv, parameters);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        StepExecution chunk = step(execution, FoodImportJobConfiguration.CHUNK_STEP_NAME);
        assertThat(chunk.getReadCount()).isEqualTo(2);
        assertThat(chunk.getWriteCount()).isEqualTo(2);
        assertThat(chunk.getFilterCount()).isZero();
        assertThat(chunk.getSkipCount()).isZero();
        assertThat(count("foods")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList(
                "SELECT PARAMETER_NAME FROM BATCH_JOB_EXECUTION_PARAMS ORDER BY PARAMETER_NAME",
                String.class)).containsExactlyInAnyOrder(
                        FoodImportRunner.RELEASE_DATE_PARAMETER,
                        FoodImportRunner.CHECKSUM_PARAMETER);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM BATCH_JOB_EXECUTION_PARAMS WHERE PARAMETER_VALUE = ?",
                Long.class, csv.toAbsolutePath().normalize().toString())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT energy IS NULL FROM foods WHERE source_food_code = ?",
                Boolean.class, code(2))).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT CONCAT(basis_amount, '/', basis_unit, '/', energy_unit, '/', carbohydrate_unit) "
                        + "FROM foods WHERE source_food_code = ?",
                String.class, code(2))).isEqualTo("100.0000/ML/KCAL/G");
        assertThat(foodQueryService.search("국밥_"))
                .extracting(food -> food.getFoodName())
                .containsExactly("국밥_돼지머리");
        Long foodId = jdbcTemplate.queryForObject(
                "SELECT food_id FROM foods WHERE source_food_code = ?", Long.class, code(1));
        assertThat(foodQueryService.get(foodId).getEnergy()).isEqualByComparingTo("137.0000");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE foods SET source_food_code = ? WHERE source_food_code = ?",
                code(1).toLowerCase(), code(1))).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE foods SET basis_unit = 'g' WHERE source_food_code = ?", code(1)))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE foods SET energy = -1 WHERE source_food_code = ?", code(1)))
                .isInstanceOf(DataAccessException.class);

        LocalDateTime createdAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM foods WHERE source_food_code = ?", LocalDateTime.class, code(1));
        LocalDateTime updatedAt = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM foods WHERE source_food_code = ?", LocalDateTime.class, code(1));
        JobExecution declaredNewRelease = launch(csv, parameters(csv, "2026-06-27"));
        assertThat(declaredNewRelease.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(count("foods")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT created_at FROM foods WHERE source_food_code = ?", LocalDateTime.class, code(1)))
                .isEqualTo(createdAt);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT updated_at FROM foods WHERE source_food_code = ?", LocalDateTime.class, code(1)))
                .isEqualTo(updatedAt);

        Path changedCsv = writeCsv("changed.csv", List.of(
                row(1, "국밥_돼지머리", "100g", "138", "15.94", "6.70", "5.16"),
                row(2, "마시는 식품", "100ml", "", "0", "1.25", "")));
        JobExecution changedRelease = launch(changedCsv, parameters(changedCsv, "2026-06-28"));
        assertThat(changedRelease.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT energy FROM foods WHERE source_food_code = ?", java.math.BigDecimal.class, code(1)))
                .isEqualByComparingTo("138.0000");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT created_at FROM foods WHERE source_food_code = ?", LocalDateTime.class, code(1)))
                .isEqualTo(createdAt);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT updated_at FROM foods WHERE source_food_code = ?", LocalDateTime.class, code(1)))
                .isAfter(updatedAt);

        assertThatThrownBy(() -> launch(csv, parameters))
                .isInstanceOf(JobInstanceAlreadyCompleteException.class);
    }

    /**
     * 표시명의 대소문자 차이를 실제 변경으로 기록하고 검색은 Java 정규화에 없는 악센트 등가성을 추가하지 않는지 확인합니다.
     *
     * @throws Exception fixture 생성 또는 Job 실행에 실패한 경우
     */
    @Test
    void updatesCaseOnlyNameChangeAndKeepsAccentSearchExact() throws Exception {
        Path originalCsv = writeCsv("name-original.csv", List.of(
                row(1, "ABC", "100g", "1", "2", "3", "4"),
                row(2, "café", "100g", "1", "2", "3", "4")));
        JobExecution original = launch(originalCsv, parameters(originalCsv, "2026-06-26"));
        assertThat(original.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        LocalDateTime originalUpdatedAt = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM foods WHERE source_food_code = ?", LocalDateTime.class, code(1));

        Path changedCsv = writeCsv("name-changed.csv", List.of(
                row(1, "abc", "100g", "1", "2", "3", "4"),
                row(2, "café", "100g", "1", "2", "3", "4")));
        JobExecution changed = launch(changedCsv, parameters(changedCsv, "2026-06-27"));

        assertThat(changed.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT food_name FROM foods WHERE source_food_code = ?", String.class, code(1)))
                .isEqualTo("abc");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT updated_at FROM foods WHERE source_food_code = ?", LocalDateTime.class, code(1)))
                .isAfter(originalUpdatedAt);
        assertThat(foodQueryService.search("cafe")).isEmpty();
        assertThat(foodQueryService.search("café"))
                .extracting(food -> food.getFoodName())
                .containsExactly("café");
    }

    /**
     * 두 번째 chunk의 writer 실패가 첫 chunk를 보존하고, checksum 차단 후 복구된 동일 파일이 체크포인트부터 재개되는지 확인합니다.
     *
     * @throws Exception fixture 생성 또는 Job 실행에 실패한 경우
     */
    @Test
    void rollsBackFailedChunkAndRestartsFromCommittedReaderCheckpoint() throws Exception {
        List<String> rows = new ArrayList<>();
        for (int sequence = 1; sequence <= 502; sequence++) {
            rows.add(row(sequence, "테스트 식품 " + sequence, "100g", "1", "2", "3", "4"));
        }
        Path csv = writeCsv("restart.csv", rows);
        String original = Files.readString(csv);
        JobParameters parameters = parameters(csv, "2026-06-26");
        jdbcTemplate.execute("""
                ALTER TABLE foods ADD CONSTRAINT ck_foods_test_failure
                CHECK (source_food_code <> 'P001-000000501-0501')
                """);

        JobExecution failed;
        try {
            failed = launch(csv, parameters);
        } finally {
            jdbcTemplate.execute("ALTER TABLE foods DROP CHECK ck_foods_test_failure");
        }

        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        StepExecution failedChunk = step(failed, FoodImportJobConfiguration.CHUNK_STEP_NAME);
        assertThat(failedChunk.getWriteCount()).isEqualTo(500);
        assertThat(failedChunk.getRollbackCount()).isGreaterThanOrEqualTo(1);
        assertThat(count("foods")).isEqualTo(500);

        Files.writeString(csv, original + System.lineSeparator());
        JobExecution changedInput = launch(csv, parameters);
        assertThat(changedInput.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(changedInput.getStepExecutions())
                .extracting(StepExecution::getStepName)
                .containsExactly(FoodImportJobConfiguration.VALIDATION_STEP_NAME);
        assertThat(count("foods")).isEqualTo(500);

        Files.writeString(csv, original);
        JobExecution restarted = launch(csv, parameters);

        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(step(restarted, FoodImportJobConfiguration.VALIDATION_STEP_NAME).getStatus())
                .isEqualTo(BatchStatus.COMPLETED);
        StepExecution restartedChunk = step(restarted, FoodImportJobConfiguration.CHUNK_STEP_NAME);
        assertThat(restartedChunk.getReadCount()).isEqualTo(2);
        assertThat(restartedChunk.getWriteCount()).isEqualTo(2);
        assertThat(count("foods")).isEqualTo(502);
    }

    /**
     * 첫 chunk보다 뒤에 잘못된 UTF-8 바이트가 있어도 preflight가 전체 파일을 검사해 쓰기 전에 실패하는지 확인합니다.
     *
     * @throws Exception fixture 생성 또는 Job 실행에 실패한 경우
     */
    @Test
    void rejectsMalformedUtf8BeforeAnyFoodWrite() throws Exception {
        List<String> rows = new ArrayList<>();
        for (int sequence = 1; sequence <= 501; sequence++) {
            rows.add(row(sequence, "테스트 식품 " + sequence, "100g", "1", "2", "3", "4"));
        }
        Path csv = writeCsv("malformed-utf8.csv", rows);
        Files.write(csv, new byte[] {'\n', (byte) 0xC3, '('}, StandardOpenOption.APPEND);

        JobExecution failed = launch(csv, parameters(csv, "2026-06-26"));

        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(failed.getStepExecutions())
                .extracting(StepExecution::getStepName)
                .containsExactly(FoodImportJobConfiguration.VALIDATION_STEP_NAME);
        assertThat(count("foods")).isZero();
    }

    /**
     * 파일 경로를 메모리에 설정한 뒤 지정 Job Parameter로 Job을 실행합니다.
     *
     * @param csv 현재 실행 입력 파일
     * @param parameters release date와 checksum 식별 Parameter
     * @return 완료 또는 실패 JobExecution
     * @throws Exception Job 시작에 실패한 경우
     */
    private JobExecution launch(Path csv, JobParameters parameters) throws Exception {
        foodImportInput.configure(csv);
        return jobLauncher.run(foodImportJob, parameters);
    }

    /**
     * 파일 checksum과 release date로 정확히 두 식별 Parameter를 구성합니다.
     *
     * @param csv checksum을 계산할 입력 파일
     * @param releaseDate 운영자가 선언한 원천 release date
     * @return 식품 Job 식별 Parameter
     * @throws Exception checksum 계산에 실패한 경우
     */
    private JobParameters parameters(Path csv, String releaseDate) throws Exception {
        return new JobParametersBuilder()
                .addString(FoodImportRunner.RELEASE_DATE_PARAMETER, releaseDate, true)
                .addString(FoodImportRunner.CHECKSUM_PARAMETER, FoodCsvFileSupport.sha256(csv), true)
                .toJobParameters();
    }

    /**
     * 헤더와 데이터 행을 갖는 UTF-8 BOM fixture를 만듭니다.
     *
     * @param fileName 임시 파일 이름
     * @param rows 데이터 물리 행 목록
     * @return 생성된 fixture 경로
     * @throws Exception 파일 생성에 실패한 경우
     */
    private Path writeCsv(String fileName, List<String> rows) throws Exception {
        Path csv = temporaryDirectory.resolve(fileName);
        List<String> lines = new ArrayList<>();
        lines.add("\uFEFF" + String.join(",", FoodCsvSchema.HEADERS));
        lines.addAll(rows);
        Files.writeString(csv, String.join("\n", lines));
        return csv;
    }

    /**
     * 승인 위치에 값을 넣은 45필드 CSV 행을 구성합니다.
     *
     * @param sequence 식품 코드 순번
     * @param name 식품명
     * @param basis 기준량 원문
     * @param energy 에너지 원문
     * @param carbohydrate 탄수화물 원문
     * @param protein 단백질 원문
     * @param fat 지방 원문
     * @return CSV 데이터 물리 행
     */
    private String row(
            int sequence,
            String name,
            String basis,
            String energy,
            String carbohydrate,
            String protein,
            String fat) {
        List<String> fields = new ArrayList<>(Collections.nCopies(FoodCsvSchema.HEADERS.size(), ""));
        fields.set(0, code(sequence));
        fields.set(1, name);
        fields.set(2, "P");
        fields.set(4, basis);
        fields.set(5, energy);
        fields.set(7, protein);
        fields.set(8, fat);
        fields.set(10, carbohydrate);
        return String.join(",", fields);
    }

    /**
     * 순번으로 승인 형식의 고유 식품 코드를 구성합니다.
     *
     * @param sequence 식품 순번
     * @return 19자 공공 원천 식품 코드
     */
    private String code(int sequence) {
        return "P001-%09d-%04d".formatted(sequence, sequence % 10_000);
    }

    /**
     * 지정 JobExecution에서 이름이 일치하는 유일한 StepExecution을 반환합니다.
     *
     * @param execution 조회할 JobExecution
     * @param name Step 이름
     * @return 일치하는 StepExecution
     */
    private StepExecution step(JobExecution execution, String name) {
        return execution.getStepExecutions().stream()
                .filter(step -> step.getStepName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    /**
     * 지정 테이블의 전체 행 수를 반환합니다.
     *
     * @param table 승인된 테스트 대상 테이블 이름
     * @return 테이블 행 수
     */
    private long count(String table) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return count == null ? 0L : count;
    }
}
