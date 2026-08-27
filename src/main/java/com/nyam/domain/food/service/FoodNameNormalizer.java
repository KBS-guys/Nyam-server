package com.nyam.domain.food.service;

import java.text.Normalizer;
import java.util.Locale;

/**
 * 식품명과 검색어에 동일한 Unicode 및 공백 정규화 규칙을 적용합니다.
 */
public final class FoodNameNormalizer {

    private static final char LIKE_ESCAPE = '!';

    /**
     * 인스턴스 생성을 막는 유틸리티 생성자입니다.
     */
    private FoodNameNormalizer() {
    }

    /**
     * NFKC, 양끝 공백 제거, 연속 공백 축약과 언어 독립 소문자화를 적용합니다.
     *
     * @param value 정규화할 원본 문자열
     * @return 검색과 저장에 사용할 정규화 문자열
     */
    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        StringBuilder collapsed = new StringBuilder(normalized.length());
        boolean pendingSpace = false;
        for (int offset = 0; offset < normalized.length();) {
            int codePoint = normalized.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                pendingSpace = collapsed.length() > 0;
            } else {
                if (pendingSpace) {
                    collapsed.append(' ');
                    pendingSpace = false;
                }
                collapsed.appendCodePoint(codePoint);
            }
        }
        return collapsed.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * JPQL LIKE의 escape 문자와 wildcard를 리터럴 검색용으로 변환합니다.
     *
     * @param normalizedPrefix 정규화가 끝난 검색 접두사
     * @return {@code !} escape 규칙이 적용된 접두사
     */
    public static String escapeLikePrefix(String normalizedPrefix) {
        return normalizedPrefix
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    /**
     * 데이터베이스 문자 길이와 맞추기 위해 Unicode 코드 포인트 수를 계산합니다.
     *
     * @param value 길이를 확인할 문자열
     * @return Unicode 코드 포인트 수
     */
    public static int characterCount(String value) {
        return value.codePointCount(0, value.length());
    }
}
