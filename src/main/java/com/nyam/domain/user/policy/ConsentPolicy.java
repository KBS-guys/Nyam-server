package com.nyam.domain.user.policy;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.nyam.domain.user.model.ConsentType;
import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/**
 * 회원가입에 필요한 동의 종류와 현재 버전이 정확히 제출되었는지 검증합니다.
 */
@Component
public class ConsentPolicy {

    static final String CURRENT_VERSION = "1.0";
    private static final Set<ConsentType> REQUIRED_TYPES = Set.of(
            ConsentType.TERMS,
            ConsentType.PERSONAL_INFORMATION,
            ConsentType.HEALTH_INFORMATION);

    /**
     * 필수 동의 세 종류가 중복 없이 현재 버전으로 제출되었는지 확인합니다.
     *
     * @param agreements 사용자가 제출한 동의 목록
     * @return 호출자가 변경할 수 없는 검증 완료 동의 목록
     * @throws BusinessException 누락, 중복, 알 수 없는 종류 또는 오래된 버전이 있는 경우
     */
    public List<ConsentAgreement> validate(List<ConsentAgreement> agreements) {
        if (agreements == null || agreements.size() != REQUIRED_TYPES.size()) {
            throw missing();
        }

        Map<ConsentType, ConsentAgreement> distinct = new EnumMap<>(ConsentType.class);
        for (ConsentAgreement agreement : agreements) {
            if (agreement == null || agreement.type() == null || !CURRENT_VERSION.equals(agreement.version())
                    || distinct.putIfAbsent(agreement.type(), agreement) != null) {
                throw missing();
            }
        }
        if (!distinct.keySet().equals(REQUIRED_TYPES)) {
            throw missing();
        }
        return List.copyOf(agreements);
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
