package com.nyam.domain.user.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.nyam.domain.user.model.ConsentType;
import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/**
 * 회원가입 필수 동의의 종류, 중복, 버전 검증 규칙을 확인합니다.
 */
class ConsentPolicyTest {

    private final ConsentPolicy policy = new ConsentPolicy();

    /**
     * 현재 버전의 필수 동의 세 종류를 정확히 제출하면 허용하는지 검증합니다.
     */
    @Test
    void acceptsExactlyTheThreeCurrentRequiredConsents() {
        assertThat(policy.validate(validConsents())).hasSize(3);
    }

    /**
     * 누락, 중복, 오래된 버전의 동의를 모두 동일한 공개 오류로 거부하는지 검증합니다.
     */
    @Test
    void rejectsMissingDuplicateAndStaleConsents() {
        assertMissing(validConsents().subList(0, 2));
        assertMissing(List.of(
                agreement(ConsentType.TERMS), agreement(ConsentType.TERMS),
                agreement(ConsentType.HEALTH_INFORMATION)));
        assertMissing(List.of(
                agreement(ConsentType.TERMS),
                new ConsentAgreement(ConsentType.PERSONAL_INFORMATION, "0.9"),
                agreement(ConsentType.HEALTH_INFORMATION)));
    }

    /**
     * 현재 버전의 필수 동의 세 종류를 생성합니다.
     *
     * @return 검증을 통과해야 하는 필수 동의 목록
     */
    private List<ConsentAgreement> validConsents() {
        return List.of(
                agreement(ConsentType.TERMS),
                agreement(ConsentType.PERSONAL_INFORMATION),
                agreement(ConsentType.HEALTH_INFORMATION));
    }

    /**
     * 현재 동의 버전을 사용하는 단일 동의 값을 생성합니다.
     *
     * @param type 생성할 동의 종류
     * @return 현재 버전의 동의 값
     */
    private ConsentAgreement agreement(ConsentType type) {
        return new ConsentAgreement(type, "1.0");
    }

    /**
     * 주어진 동의 목록이 필수 동의 누락 오류로 거부되는지 확인합니다.
     *
     * @param agreements 거부를 기대하는 동의 목록
     */
    private void assertMissing(List<ConsentAgreement> agreements) {
        assertThatThrownBy(() -> policy.validate(agreements))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.REQUIRED_CONSENT_MISSING));
    }
}
