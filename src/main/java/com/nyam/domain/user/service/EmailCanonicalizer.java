package com.nyam.domain.user.service;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/**
 * 이메일 인증에서 지원하는 ASCII 이메일 경계와 정규화 규칙을 적용합니다.
 */
@Component
public class EmailCanonicalizer {

    private static final int MAX_EMAIL_LENGTH = 254;
    private static final Pattern LOCAL_PART = Pattern.compile("[A-Za-z0-9!#$%&'*+/=?^_`{|}~.-]+");
    private static final Pattern DOMAIN_LABEL = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?");

    /**
     * 제출 이메일의 바깥 공백을 제거하고 지원 형식을 검증한 뒤 소문자 정규화 값을 만듭니다.
     *
     * @param submittedEmail 사용자가 제출한 이메일 문자열
     * @return 발송 표기와 정규화 표기를 함께 가진 값
     * @throws BusinessException 이메일이 비어 있거나 ASCII·길이·기본 형식 규칙을 위반한 경우
     */
    public NormalizedEmailAddress normalize(String submittedEmail) {
        if (submittedEmail == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String displayEmail = submittedEmail.strip();
        if (displayEmail.isEmpty() || displayEmail.length() > MAX_EMAIL_LENGTH || !isPrintableAscii(displayEmail)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        int at = displayEmail.indexOf('@');
        if (at <= 0 || at != displayEmail.lastIndexOf('@') || at == displayEmail.length() - 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String localPart = displayEmail.substring(0, at);
        String domain = displayEmail.substring(at + 1);
        if (!isValidLocalPart(localPart) || !isValidDomain(domain)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return new NormalizedEmailAddress(displayEmail, displayEmail.toLowerCase(Locale.ROOT));
    }

    /**
     * 공백과 제어 문자를 제외한 ASCII 문자열인지 확인합니다.
     *
     * @param value 검사할 문자열
     * @return 모든 문자가 출력 가능한 ASCII이면 {@code true}
     */
    private boolean isPrintableAscii(String value) {
        return value.chars().allMatch(character -> character >= 0x21 && character <= 0x7e);
    }

    /**
     * 로컬 파트의 길이, 점 위치, 허용 문자를 확인합니다.
     *
     * @param localPart @ 기호 앞의 이메일 부분
     * @return MVP 기본 형식을 충족하면 {@code true}
     */
    private boolean isValidLocalPart(String localPart) {
        return localPart.length() <= 64
                && !localPart.startsWith(".")
                && !localPart.endsWith(".")
                && !localPart.contains("..")
                && LOCAL_PART.matcher(localPart).matches();
    }

    /**
     * 점으로 구분된 도메인 레이블의 길이와 하이픈 위치를 확인합니다.
     *
     * @param domain @ 기호 뒤의 이메일 도메인
     * @return MVP 기본 형식을 충족하면 {@code true}
     */
    private boolean isValidDomain(String domain) {
        if (domain.length() > 253 || !domain.contains(".")) {
            return false;
        }
        String[] labels = domain.split("\\.", -1);
        for (String label : labels) {
            if (!DOMAIN_LABEL.matcher(label).matches()) {
                return false;
            }
        }
        return true;
    }
}
