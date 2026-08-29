package com.nyam.domain.food.batch;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

/**
 * 식품 쓰기 전에 파일·checksum·날짜·전체 UTF-8 인코딩과 정확한 헤더를 반복 검증합니다.
 */
public class FoodImportPreflightTasklet implements Tasklet {

    private static final Pattern LOWERCASE_SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private final FoodImportInput input;

    /**
     * 현재 프로세스의 비영속 입력 경로를 주입받습니다.
     *
     * @param input CSV 입력 경로 보관자
     */
    public FoodImportPreflightTasklet(FoodImportInput input) {
        this.input = input;
    }

    /**
     * 초기 실행과 모든 재시작에서 입력 동일성과 파일 수준 구조를 확인합니다.
     *
     * @param contribution 현재 Step 기여 정보
     * @param chunkContext Job Parameter를 제공하는 실행 컨텍스트
     * @return 검증 완료 상태
     * @throws FoodImportException 파일이나 Parameter가 계약을 위반한 경우
     */
    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        Path path = input.requirePath();
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new FoodImportException("Food CSV must be a readable regular file");
        }

        Map<String, Object> parameters = chunkContext.getStepContext().getJobParameters();
        String releaseDate = requireStringParameter(parameters, FoodImportRunner.RELEASE_DATE_PARAMETER);
        String checksum = requireStringParameter(parameters, FoodImportRunner.CHECKSUM_PARAMETER);
        try {
            LocalDate.parse(releaseDate);
        } catch (DateTimeParseException exception) {
            throw new FoodImportException("Food source release date is invalid", exception);
        }
        if (!LOWERCASE_SHA_256.matcher(checksum).matches()) {
            throw new FoodImportException("Food source checksum is invalid");
        }

        try {
            if (!FoodCsvFileSupport.sha256(path).equals(checksum)) {
                throw new FoodImportException("Food CSV checksum does not match the Job identity");
            }
            try (BufferedReader reader = FoodCsvFileSupport.openStrictUtf8(path)) {
                FoodCsvFileSupport.requireExactHeader(reader.readLine());
                while (reader.readLine() != null) {
                    // 전체 파일을 strict UTF-8로 끝까지 읽어 쓰기 전 인코딩 계약을 검증합니다.
                }
            }
        } catch (IOException exception) {
            throw new FoodImportException("Food CSV preflight could not read the input");
        }
        return RepeatStatus.FINISHED;
    }

    /**
     * 필수 식별 Job Parameter를 문자열로 반환합니다.
     *
     * @param parameters 현재 Job Parameter Map
     * @param name 확인할 Parameter 이름
     * @return 비어 있지 않은 문자열 값
     * @throws FoodImportException Parameter가 없거나 문자열이 아닌 경우
     */
    private String requireStringParameter(Map<String, Object> parameters, String name) {
        Object value = parameters.get(name);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new FoodImportException("A required food import Job Parameter is missing");
        }
        return text;
    }
}
