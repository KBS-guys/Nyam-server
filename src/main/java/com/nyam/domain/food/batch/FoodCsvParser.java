package com.nyam.domain.food.batch;

import java.util.ArrayList;
import java.util.List;

/**
 * 한 물리 행 안에서 comma, quoted field와 escaped double quote를 엄격히 파싱합니다.
 */
public final class FoodCsvParser {

    /**
     * 인스턴스 생성을 막는 CSV 파서 생성자입니다.
     */
    private FoodCsvParser() {
    }

    /**
     * 한 물리 행을 필드 목록으로 변환하며 multiline과 잘못된 quote를 거절합니다.
     *
     * @param line 파싱할 한 물리 행
     * @return 순서가 보존된 CSV 필드 목록
     * @throws FoodImportException quote 구조가 잘못되었거나 multiline field가 시작된 경우
     */
    public static List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean closedQuote = false;

        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (inQuotes) {
                if (current == '"') {
                    if (index + 1 < line.length() && line.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        inQuotes = false;
                        closedQuote = true;
                    }
                } else if (current == '\r' || current == '\n') {
                    throw new FoodImportException("Quoted multiline fields are unsupported");
                } else {
                    field.append(current);
                }
            } else if (closedQuote) {
                if (current != ',') {
                    throw new FoodImportException("Unexpected characters follow a quoted CSV field");
                }
                fields.add(field.toString());
                field.setLength(0);
                closedQuote = false;
            } else if (current == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else if (current == '"') {
                if (field.length() != 0) {
                    throw new FoodImportException("A quote appeared inside an unquoted CSV field");
                }
                inQuotes = true;
            } else {
                field.append(current);
            }
        }

        if (inQuotes) {
            throw new FoodImportException("Quoted multiline fields are unsupported");
        }
        fields.add(field.toString());
        return fields;
    }
}
