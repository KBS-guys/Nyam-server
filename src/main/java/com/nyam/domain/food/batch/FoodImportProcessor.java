package com.nyam.domain.food.batch;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

import org.springframework.batch.item.ItemProcessor;

import com.nyam.domain.food.service.FoodNameNormalizer;

/**
 * CSV 원본 필드를 승인된 식품·단위·정밀도 계약으로 검증하고 변환합니다.
 */
public class FoodImportProcessor implements ItemProcessor<FoodCsvRow, FoodImportItem> {

    private static final Pattern SOURCE_CODE = Pattern.compile("[PD][0-9]{3}-[0-9]{9}-[0-9]{4}");
    private static final Pattern NON_NEGATIVE_DECIMAL = Pattern.compile("[0-9]+(?:\\.[0-9]+)?");
    private static final BigDecimal BASIS_AMOUNT = new BigDecimal("100.0000");
    private static final BigDecimal MAX_DECIMAL = new BigDecimal("99999999.9999");

    private final Clock clock;

    /**
     * 적재 시각을 일관된 UTC 시계에서 얻도록 시계를 주입받습니다.
     *
     * @param clock 애플리케이션 UTC 시계
     */
    public FoodImportProcessor(Clock clock) {
        this.clock = clock;
    }

    /**
     * 한 CSV 행을 검증하며 정책상 제외 없이 JDBC 적재 항목으로 변환합니다.
     *
     * @param row 원본 CSV의 적재 대상 필드
     * @return 검증과 정규화가 끝난 식품 항목
     * @throws FoodImportException 필수값, 코드, 이름, 기준량 또는 영양값이 계약을 위반한 경우
     */
    @Override
    public FoodImportItem process(FoodCsvRow row) {
        requireSourceIdentity(row.sourceFoodCode(), row.foodType());
        requireName(row.foodName(), "Original food name");
        String normalizedName = FoodNameNormalizer.normalize(row.foodName());
        requireName(normalizedName, "Normalized food name");

        String basisUnit = switch (row.basis()) {
            case "100g" -> "G";
            case "100ml" -> "ML";
            default -> throw new FoodImportException("Food nutrition basis is unsupported");
        };

        return new FoodImportItem(
                row.sourceFoodCode(),
                row.foodName(),
                normalizedName,
                row.foodType(),
                BASIS_AMOUNT,
                basisUnit,
                parseOptionalNutrient(row.energy()),
                parseOptionalNutrient(row.carbohydrate()),
                parseOptionalNutrient(row.protein()),
                parseOptionalNutrient(row.fat()),
                LocalDateTime.now(clock));
    }

    /**
     * 식품 코드 형식과 유형의 대소문자 및 접두사 일치를 검증합니다.
     *
     * @param sourceFoodCode 공공 원천 식품 코드
     * @param foodType 원천 식품 유형
     * @throws FoodImportException 코드 또는 유형이 계약을 위반한 경우
     */
    private void requireSourceIdentity(String sourceFoodCode, String foodType) {
        if (sourceFoodCode == null || !SOURCE_CODE.matcher(sourceFoodCode).matches()) {
            throw new FoodImportException("Source food code is invalid");
        }
        if (!("P".equals(foodType) || "D".equals(foodType))
                || sourceFoodCode.charAt(0) != foodType.charAt(0)) {
            throw new FoodImportException("Food type does not match its source code");
        }
    }

    /**
     * 원본 또는 정규화 식품명이 공백이 아니고 500자를 넘지 않는지 확인합니다.
     *
     * @param name 검증할 식품명
     * @param label 원본을 노출하지 않는 안전한 필드 구분
     * @throws FoodImportException 식품명이 비어 있거나 제한을 초과한 경우
     */
    private void requireName(String name, String label) {
        if (name == null || name.isBlank() || FoodNameNormalizer.characterCount(name) > 500) {
            throw new FoodImportException(label + " is blank or exceeds 500 characters");
        }
    }

    /**
     * 주변 공백만 제거한 뒤 빈 값은 {@code null}, 숫자는 scale 4 무반올림 값으로 변환합니다.
     *
     * @param raw 원천 영양값 문자열
     * @return 값이 없으면 {@code null}인 scale 4 영양값
     * @throws FoodImportException 숫자 형식, 음수, 정밀도 또는 범위가 계약을 위반한 경우
     */
    private BigDecimal parseOptionalNutrient(String raw) {
        String stripped = stripSurroundingWhitespace(raw == null ? "" : raw);
        if (stripped.isEmpty()) {
            return null;
        }
        if (!NON_NEGATIVE_DECIMAL.matcher(stripped).matches()) {
            throw new FoodImportException("Nutrient value is not a valid non-negative decimal");
        }
        try {
            BigDecimal value = new BigDecimal(stripped).setScale(4, RoundingMode.UNNECESSARY);
            if (value.compareTo(MAX_DECIMAL) > 0) {
                throw new FoodImportException("Nutrient value exceeds DECIMAL(12,4)");
            }
            return value;
        } catch (ArithmeticException exception) {
            throw new FoodImportException("Nutrient value cannot be represented at scale 4", exception);
        }
    }

    /**
     * 내부 문자는 보존하면서 Unicode 공백으로 분류되는 양끝 코드 포인트만 제거합니다.
     *
     * @param value 공백을 제거할 문자열
     * @return 내부 문자가 변경되지 않은 양끝 공백 제거 문자열
     */
    private String stripSurroundingWhitespace(String value) {
        int start = 0;
        int end = value.length();
        while (start < end) {
            int codePoint = value.codePointAt(start);
            if (!(Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint))) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (end > start) {
            int codePoint = value.codePointBefore(end);
            if (!(Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint))) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return value.substring(start, end);
    }
}
