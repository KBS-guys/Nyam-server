package com.nyam.domain.food.batch;

import java.time.Clock;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.JobParametersValidator;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 수동 식품 적재 Job의 preflight, streaming chunk와 null-safe upsert 흐름을 구성합니다.
 */
@Configuration
public class FoodImportJobConfiguration {

    /** 식품 적재 Job 이름입니다. */
    public static final String JOB_NAME = "foodImportJob";
    /** 반복 검증 Step 이름입니다. */
    public static final String VALIDATION_STEP_NAME = "foodImportValidationStep";
    /** CSV chunk 적재 Step 이름입니다. */
    public static final String CHUNK_STEP_NAME = "foodImportChunkStep";
    /** 초기 chunk 크기입니다. */
    public static final int CHUNK_SIZE = 500;

    private static final Set<String> IDENTIFYING_PARAMETERS = Set.of(
            FoodImportRunner.RELEASE_DATE_PARAMETER,
            FoodImportRunner.CHECKSUM_PARAMETER);

    /**
     * 입력 파일 수준 계약을 검사하는 Tasklet을 구성합니다.
     *
     * @param input 비영속 입력 경로 보관자
     * @return preflight 검증 Tasklet
     */
    @Bean
    public FoodImportPreflightTasklet foodImportPreflightTasklet(FoodImportInput input) {
        return new FoodImportPreflightTasklet(input);
    }

    /**
     * 초기 실행과 재시작마다 완료 여부와 관계없이 수행되는 검증 Step을 구성합니다.
     *
     * @param jobRepository JDBC 기반 영속 JobRepository
     * @param batchTransactionManager Batch 전용 JDBC 트랜잭션 관리자
     * @param tasklet 입력 파일 검증 Tasklet
     * @return 반복 실행 가능한 preflight Step
     */
    @Bean
    public Step foodImportValidationStep(
            JobRepository jobRepository,
            @Qualifier("batchTransactionManager") PlatformTransactionManager batchTransactionManager,
            FoodImportPreflightTasklet tasklet) {
        return new StepBuilder(VALIDATION_STEP_NAME, jobRepository)
                .tasklet(tasklet, batchTransactionManager)
                .allowStartIfComplete(true)
                .build();
    }

    /**
     * 현재 프로세스 입력을 스트리밍하고 영속 체크포인트를 사용하는 Reader를 구성합니다.
     *
     * @param input 비영속 입력 경로 보관자
     * @return Step scope CSV Reader
     */
    @Bean
    @StepScope
    public FoodCsvItemReader foodCsvItemReader(FoodImportInput input) {
        return new FoodCsvItemReader(input);
    }

    /**
     * 식품 행의 데이터 무결성 규칙을 적용하는 Processor를 구성합니다.
     *
     * @param clock UTC 적재 시각을 제공하는 시계
     * @return fail-fast 식품 Processor
     */
    @Bean
    public FoodImportProcessor foodImportProcessor(Clock clock) {
        return new FoodImportProcessor(clock);
    }

    /**
     * 외부 식품 코드로 null-safe upsert하고 실제 변경 때만 수정 시각을 갱신하는 Writer를 구성합니다.
     *
     * @param dataSource 식품과 Batch 메타데이터가 함께 저장되는 DataSource
     * @return JDBC batch upsert Writer
     */
    @Bean
    public JdbcBatchItemWriter<FoodImportItem> foodImportWriter(DataSource dataSource) {
        String sql = """
                INSERT INTO foods(
                    source_food_code, food_name, normalized_name, food_type,
                    basis_amount, basis_unit,
                    energy, energy_unit,
                    carbohydrate, carbohydrate_unit,
                    protein, protein_unit,
                    fat, fat_unit,
                    created_at, updated_at
                ) VALUES (
                    :sourceFoodCode, :foodName, :normalizedName, :foodType,
                    :basisAmount, :basisUnit,
                    :energy, 'KCAL',
                    :carbohydrate, 'G',
                    :protein, 'G',
                    :fat, 'G',
                    :importedAt, :importedAt
                )
                ON DUPLICATE KEY UPDATE
                    updated_at = CASE WHEN
                        NOT (food_name <=> :foodName)
                        OR NOT (normalized_name <=> :normalizedName)
                        OR NOT (food_type <=> :foodType)
                        OR NOT (basis_amount <=> :basisAmount)
                        OR NOT (basis_unit <=> :basisUnit)
                        OR NOT (energy <=> :energy)
                        OR NOT (energy_unit <=> 'KCAL')
                        OR NOT (carbohydrate <=> :carbohydrate)
                        OR NOT (carbohydrate_unit <=> 'G')
                        OR NOT (protein <=> :protein)
                        OR NOT (protein_unit <=> 'G')
                        OR NOT (fat <=> :fat)
                        OR NOT (fat_unit <=> 'G')
                    THEN :importedAt ELSE updated_at END,
                    food_name = :foodName,
                    normalized_name = :normalizedName,
                    food_type = :foodType,
                    basis_amount = :basisAmount,
                    basis_unit = :basisUnit,
                    energy = :energy,
                    energy_unit = 'KCAL',
                    carbohydrate = :carbohydrate,
                    carbohydrate_unit = 'G',
                    protein = :protein,
                    protein_unit = 'G',
                    fat = :fat,
                    fat_unit = 'G'
                """;
        return new JdbcBatchItemWriterBuilder<FoodImportItem>()
                .dataSource(dataSource)
                .sql(sql)
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .assertUpdates(false)
                .build();
    }

    /**
     * Reader, Processor와 Writer를 동일 JDBC chunk 트랜잭션으로 실행하는 Step을 구성합니다.
     *
     * @param jobRepository JDBC 기반 영속 JobRepository
     * @param batchTransactionManager Batch 전용 JDBC 트랜잭션 관리자
     * @param reader 체크포인트 가능한 CSV Reader
     * @param processor fail-fast 행 Processor
     * @param writer null-safe JDBC upsert Writer
     * @return 500건 chunk 식품 적재 Step
     */
    @Bean
    public Step foodImportChunkStep(
            JobRepository jobRepository,
            @Qualifier("batchTransactionManager") PlatformTransactionManager batchTransactionManager,
            ItemReader<FoodCsvRow> reader,
            ItemProcessor<FoodCsvRow, FoodImportItem> processor,
            JdbcBatchItemWriter<FoodImportItem> writer) {
        return new StepBuilder(CHUNK_STEP_NAME, jobRepository)
                .<FoodCsvRow, FoodImportItem>chunk(CHUNK_SIZE, batchTransactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    /**
     * release date와 checksum만 허용하고 둘 다 식별 Parameter인지 확인합니다.
     *
     * @return 식품 Job Parameter 검증기
     */
    @Bean
    public JobParametersValidator foodImportJobParametersValidator() {
        return parameters -> validateJobParameters(parameters);
    }

    /**
     * 반복 preflight 뒤 체크포인트 가능한 chunk Step을 수행하는 restartable Job을 구성합니다.
     *
     * @param jobRepository JDBC 기반 영속 JobRepository
     * @param validationStep 모든 실행에서 파일을 다시 검증할 Step
     * @param chunkStep 식품 행을 적재할 chunk Step
     * @param validator 정확히 두 식별 Parameter만 허용하는 검증기
     * @return 수동 식품 적재 Job
     */
    @Bean
    public Job foodImportJob(
            JobRepository jobRepository,
            @Qualifier("foodImportValidationStep") Step validationStep,
            @Qualifier("foodImportChunkStep") Step chunkStep,
            JobParametersValidator validator) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .validator(validator)
                .start(validationStep)
                .next(chunkStep)
                .build();
    }

    /**
     * Job Parameter 이름과 식별 속성을 승인된 두 값으로 제한합니다.
     *
     * @param parameters 검증할 Job Parameter
     * @throws JobParametersInvalidException Parameter가 없거나 추가되거나 비식별인 경우
     */
    private void validateJobParameters(JobParameters parameters) throws JobParametersInvalidException {
        if (parameters == null || !parameters.getParameters().keySet().equals(IDENTIFYING_PARAMETERS)) {
            throw new JobParametersInvalidException("Food import requires exactly release date and checksum");
        }
        for (String name : IDENTIFYING_PARAMETERS) {
            JobParameter<?> parameter = parameters.getParameters().get(name);
            if (parameter == null || !parameter.isIdentifying()) {
                throw new JobParametersInvalidException("Food import parameters must identify the Job Instance");
            }
        }
    }
}
