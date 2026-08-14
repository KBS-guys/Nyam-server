package com.nyam.domain.user.policy;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/**
 * UTC 기준 날짜로 회원가입 가능한 최소 연령을 검증합니다.
 */
@Component
public class AgePolicy {

    private static final int MINIMUM_AGE = 19;
    private final Clock clock;

    /**
     * 검증 기준 날짜를 제공하는 시계를 주입받습니다.
     *
     * @param clock 현재 날짜 계산과 테스트 고정에 사용할 시계
     */
    public AgePolicy(Clock clock) {
        this.clock = clock;
    }

    /**
     * 생년월일이 유효하고 가입일 기준 만 19세 이상인지 확인합니다.
     *
     * @param birthDate 사용자가 제출한 생년월일
     * @throws BusinessException 생년월일이 없거나 미래이거나 만 19세 미만인 경우
     */
    public void requireEligible(LocalDate birthDate) {
        LocalDate today = LocalDate.now(clock);
        if (birthDate == null || birthDate.isAfter(today) || birthDate.plusYears(MINIMUM_AGE).isAfter(today)) {
            throw new BusinessException(ErrorCode.UNDERAGE_NOT_ALLOWED);
        }
    }
}
