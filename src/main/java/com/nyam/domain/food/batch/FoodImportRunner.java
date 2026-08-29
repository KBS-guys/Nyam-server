package com.nyam.domain.food.batch;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 명시적인 실행 옵션을 검증하고 동일 입력 식별자로 식품 적재 Job을 시작합니다.
 */
@Component
@Profile("food-import")
public class FoodImportRunner implements ApplicationRunner {

    /** 입력 경로 실행 옵션 이름입니다. */
    public static final String PATH_OPTION = "food-import.path";
    /** 원천 release date 실행 옵션 이름입니다. */
    public static final String RELEASE_DATE_OPTION = "food-import.release-date";
    /** 원천 SHA-256 실행 옵션 이름입니다. */
    public static final String CHECKSUM_OPTION = "food-import.checksum";
    /** 영속되는 release date Job Parameter 이름입니다. */
    public static final String RELEASE_DATE_PARAMETER = "sourceReleaseDate";
    /** 영속되는 checksum Job Parameter 이름입니다. */
    public static final String CHECKSUM_PARAMETER = "sourceChecksum";

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private final JobLauncher jobLauncher;
    private final Job foodImportJob;
    private final FoodImportInput input;

    /**
     * Job 실행기와 식품 Job, 비영속 입력 경로 보관자를 주입받습니다.
     *
     * @param jobLauncher 영속 JobRepository를 사용하는 실행기
     * @param foodImportJob 실행할 식품 적재 Job
     * @param input 입력 경로 보관자
     */
    public FoodImportRunner(JobLauncher jobLauncher, Job foodImportJob, FoodImportInput input) {
        this.jobLauncher = jobLauncher;
        this.foodImportJob = foodImportJob;
        this.input = input;
    }

    /**
     * 입력 경로는 메모리에만 설정하고 release date와 checksum만 식별 Parameter로 실행합니다.
     *
     * @param arguments 수동 실행 명령행 옵션
     * @throws Exception Job 시작 또는 실행에 실패한 경우
     */
    @Override
    public void run(ApplicationArguments arguments) throws Exception {
        String pathValue = requireSingleOption(arguments, PATH_OPTION);
        String releaseDateValue = requireSingleOption(arguments, RELEASE_DATE_OPTION);
        String checksum = requireSingleOption(arguments, CHECKSUM_OPTION).toLowerCase(Locale.ROOT);
        validateReleaseDate(releaseDateValue);
        if (!SHA_256.matcher(checksum).matches()) {
            throw new IllegalArgumentException("Food import checksum must be a SHA-256 value");
        }

        try {
            input.configure(Path.of(pathValue));
        } catch (InvalidPathException | SecurityException exception) {
            throw new IllegalArgumentException("Food import path is invalid");
        }
        JobParameters parameters = new JobParametersBuilder()
                .addString(RELEASE_DATE_PARAMETER, releaseDateValue, true)
                .addString(CHECKSUM_PARAMETER, checksum, true)
                .toJobParameters();
        JobExecution execution = jobLauncher.run(foodImportJob, parameters);
        if (execution.getStatus() != BatchStatus.COMPLETED) {
            throw new IllegalStateException("Food import Job did not complete");
        }
    }

    /**
     * 한 번만 제출된 필수 실행 옵션을 반환합니다.
     *
     * @param arguments 전체 애플리케이션 인자
     * @param optionName 확인할 옵션 이름
     * @return 비어 있지 않은 단일 옵션 값
     * @throws IllegalArgumentException 옵션이 없거나 중복되거나 빈 값인 경우
     */
    private String requireSingleOption(ApplicationArguments arguments, String optionName) {
        List<String> values = arguments.getOptionValues(optionName);
        if (values == null || values.size() != 1 || values.get(0).isBlank()) {
            throw new IllegalArgumentException("A single required food import option is missing");
        }
        return values.get(0);
    }

    /**
     * release date가 ISO 날짜인지 확인합니다.
     *
     * @param value 검증할 날짜 문자열
     * @throws IllegalArgumentException ISO 날짜가 아닌 경우
     */
    private void validateReleaseDate(String value) {
        try {
            LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Food import release date must be an ISO date", exception);
        }
    }
}
