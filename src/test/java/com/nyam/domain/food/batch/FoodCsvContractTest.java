package com.nyam.domain.food.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * 식품 CSV 구조, 행 변환과 Reader 체크포인트 계약을 단위 수준에서 검증합니다.
 */
@ExtendWith(OutputCaptureExtension.class)
class FoodCsvContractTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC);
    private static final Logger LOGGER = LoggerFactory.getLogger(FoodCsvContractTest.class);

    @TempDir
    Path temporaryDirectory;

    /**
     * comma를 포함한 quoted field와 escaped quote를 원문 값으로 복원하는지 확인합니다.
     */
    @Test
    void parsesQuotedCommaAndEscapedQuote() {
        assertThat(FoodCsvParser.parseLine("\"name, one\",\"say \"\"hello\"\"\",tail"))
                .containsExactly("name, one", "say \"hello\"", "tail");
    }

    /**
     * 닫히지 않은 quote를 multiline 시도로 판단하고 원문 노출 없이 거절하는지 확인합니다.
     */
    @Test
    void rejectsQuotedMultilineAndWrongFieldCount() {
        assertThatThrownBy(() -> FoodCsvParser.parseLine("\"unfinished"))
                .isInstanceOf(FoodImportException.class)
                .hasMessageContaining("multiline");
        assertThatThrownBy(() -> FoodCsvRow.from(List.of("too", "short")))
                .isInstanceOf(FoodImportException.class)
                .hasMessageContaining("45 fields");
    }

    /**
     * surrounding whitespace, 빈 값과 유효한 숫자를 각각 scale 4 값, null과 0으로 구분하는지 확인합니다.
     */
    @Test
    void mapsWhitespaceBlankAndZeroWithoutRounding() throws Exception {
        FoodImportProcessor processor = new FoodImportProcessor(CLOCK);

        FoodImportItem item = processor.process(new FoodCsvRow(
                "P001-000000001-0001", "  Ａ  밥  ", "P", "100g",
                " 1.5 ", "", "0", "2.34000"));

        assertThat(item.normalizedName()).isEqualTo("a 밥");
        assertThat(item.energy()).isEqualByComparingTo("1.5000");
        assertThat(item.carbohydrate()).isNull();
        assertThat(item.protein()).isEqualByComparingTo("0.0000");
        assertThat(item.fat()).isEqualByComparingTo("2.3400");
        assertThat(item.basisAmount()).isEqualByComparingTo("100.0000");
        assertThat(item.basisUnit()).isEqualTo("G");
    }

    /**
     * 내부 공백 제거가 필요한 숫자, 반올림이 필요한 숫자와 잘못된 코드·타입을 fail-fast로 거절합니다.
     */
    @Test
    void rejectsInvalidNutrientsAndSourceIdentity() {
        FoodImportProcessor processor = new FoodImportProcessor(CLOCK);

        assertThatThrownBy(() -> processor.process(validRowWithEnergy("1 5")))
                .isInstanceOf(FoodImportException.class);
        assertThatThrownBy(() -> processor.process(validRowWithEnergy("1.23456")))
                .isInstanceOf(FoodImportException.class);
        assertThatThrownBy(() -> processor.process(new FoodCsvRow(
                "p001-000000001-0001", "식품", "P", "100g", "1", "1", "1", "1")))
                .isInstanceOf(FoodImportException.class);
        assertThatThrownBy(() -> processor.process(new FoodCsvRow(
                "P001-000000001-0001", "식품", "D", "100g", "1", "1", "1", "1")))
                .isInstanceOf(FoodImportException.class);
    }

    /**
     * 원본은 500자이지만 NFKC 결과가 500자를 넘는 이름을 DB 잘림 전에 거절하는지 확인합니다.
     */
    @Test
    void validatesNormalizedNameLengthSeparately() {
        FoodImportProcessor processor = new FoodImportProcessor(CLOCK);
        String expandingLigatureName = "\uFB00".repeat(500);

        assertThatThrownBy(() -> processor.process(new FoodCsvRow(
                "P001-000000001-0001", expandingLigatureName, "P", "100g", "1", "1", "1", "1")))
                .isInstanceOf(FoodImportException.class)
                .hasMessageContaining("Normalized food name");
    }

    /**
     * 저장된 행 위치를 새 Reader가 복원해 마지막 커밋 이후 행만 반환하는지 확인합니다.
     *
     * @throws Exception 임시 CSV 생성이나 Reader 처리에 실패한 경우
     */
    @Test
    void restoresReaderPositionFromExecutionContext() throws Exception {
        Path csv = temporaryDirectory.resolve("fixture.csv");
        Files.writeString(csv, String.join("\n",
                "\uFEFF" + String.join(",", FoodCsvSchema.HEADERS),
                csvLine(1),
                csvLine(2),
                csvLine(3)));
        FoodImportInput input = new FoodImportInput();
        input.configure(csv);
        ExecutionContext checkpoint = new ExecutionContext();

        FoodCsvItemReader first = new FoodCsvItemReader(input);
        first.open(checkpoint);
        assertThat(first.read().sourceFoodCode()).isEqualTo(code(1));
        assertThat(first.read().sourceFoodCode()).isEqualTo(code(2));
        first.update(checkpoint);
        first.close();

        FoodCsvItemReader restarted = new FoodCsvItemReader(input);
        restarted.open(checkpoint);
        assertThat(restarted.read().sourceFoodCode()).isEqualTo(code(3));
        assertThat(restarted.read()).isNull();
        restarted.close();
    }

    /**
     * Reader의 예상 가능한 파일 열기 실패가 stack trace를 통해 로컬 입력 경로를 노출하지 않는지 확인합니다.
     *
     * @param output 테스트 중 캡처한 애플리케이션 로그
     */
    @Test
    void readerOpenFailureDoesNotExposeLocalPath(CapturedOutput output) {
        Path missingCsv = temporaryDirectory.resolve("private-food-source.csv").toAbsolutePath();
        FoodImportInput input = new FoodImportInput();
        input.configure(missingCsv);
        FoodCsvItemReader reader = new FoodCsvItemReader(input);

        Throwable failure = catchThrowable(() -> reader.open(new ExecutionContext()));
        assertThat(failure).isInstanceOf(org.springframework.batch.item.ItemStreamException.class);
        LOGGER.error("Food CSV Reader failed", failure);

        assertThat(output).doesNotContain(missingCsv.toString());
    }

    /**
     * 기본값을 갖춘 유효 행에서 에너지 원문만 교체합니다.
     *
     * @param energy 에너지 원문
     * @return Processor 검증용 CSV 행
     */
    private FoodCsvRow validRowWithEnergy(String energy) {
        return new FoodCsvRow(
                "P001-000000001-0001", "식품", "P", "100g", energy, "1", "1", "1");
    }

    /**
     * 지정 순번의 유효한 45필드 CSV 행을 구성합니다.
     *
     * @param sequence 식품 코드 순번
     * @return comma-delimited CSV 물리 행
     */
    private String csvLine(int sequence) {
        List<String> fields = new ArrayList<>(Collections.nCopies(FoodCsvSchema.HEADERS.size(), ""));
        fields.set(0, code(sequence));
        fields.set(1, "테스트 식품 " + sequence);
        fields.set(2, "P");
        fields.set(4, "100g");
        fields.set(5, "1");
        fields.set(7, "2");
        fields.set(8, "3");
        fields.set(10, "4");
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
}
