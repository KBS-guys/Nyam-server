package com.nyam.domain.user.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.nyam.domain.user.model.ConsentType;
import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/**
 * boolean 동의를 서버 소유 종류와 버전으로 변환하는 정책을 검증합니다.
 */
class ConsentPolicyTest {

    private final ConsentPolicy policy = new ConsentPolicy();

    @Test
    void allRequiredAgreementsResolveToServerVersion() {
        assertThat(policy.resolveRequired(true, true, true))
                .containsExactly(
                        new ConsentAgreement(ConsentType.TERMS, "1.0"),
                        new ConsentAgreement(ConsentType.PERSONAL_INFORMATION, "1.0"),
                        new ConsentAgreement(ConsentType.HEALTH_INFORMATION, "1.0"));
    }

    @Test
    void falseAgreementIsRejected() {
        assertMissing(() -> policy.resolveRequired(true, false, true));
    }

    @Test
    void nullAgreementIsRejectedForDirectServiceCalls() {
        assertMissing(() -> policy.resolveRequired(true, null, true));
    }

    private void assertMissing(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.REQUIRED_CONSENT_MISSING);
    }
}
