package com.nyam.domain.user.policy;

import java.util.List;

import org.springframework.stereotype.Component;

import com.nyam.domain.user.model.ConsentType;
import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/**
 * 회원가입 필수 동의를 검증하고 서버가 관리하는 현재 동의 내역을 만듭니다.
 */
@Component
public class ConsentPolicy {

    static final String CURRENT_VERSION = "1.0";
    /**
     * 세 필수 동의가 모두 명시적으로 승인됐는지 확인하고 현재 버전을 결합합니다.
     *
     * @param termsAgreed 서비스 이용약관 동의 여부
     * @param personalInformationAgreed 개인정보 수집·이용 동의 여부
     * @param healthInformationAgreed 건강정보 처리 동의 여부
     * @return 서버 현재 버전이 결합된 필수 동의 세 건
     * @throws BusinessException 하나라도 누락되거나 {@code false}인 경우
     */
    public List<ConsentAgreement> resolveRequired(
            Boolean termsAgreed,
            Boolean personalInformationAgreed,
            Boolean healthInformationAgreed) {
        if (!Boolean.TRUE.equals(termsAgreed)
                || !Boolean.TRUE.equals(personalInformationAgreed)
                || !Boolean.TRUE.equals(healthInformationAgreed)) {
            throw missing();
        }
        return List.of(
                new ConsentAgreement(ConsentType.TERMS, CURRENT_VERSION),
                new ConsentAgreement(ConsentType.PERSONAL_INFORMATION, CURRENT_VERSION),
                new ConsentAgreement(ConsentType.HEALTH_INFORMATION, CURRENT_VERSION));
    }

    /**
     * 동의 계약 실패에 사용할 공개 비즈니스 예외를 생성합니다.
     *
     * @return {@code REQUIRED_CONSENT_MISSING} 비즈니스 예외
     */
    private BusinessException missing() {
        return new BusinessException(ErrorCode.REQUIRED_CONSENT_MISSING);
    }
}
