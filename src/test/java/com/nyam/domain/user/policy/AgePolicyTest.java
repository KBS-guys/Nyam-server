package com.nyam.domain.user.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/**
 * 가입 최소 연령 정책의 날짜 경계와 잘못된 생년월일 처리를 검증합니다.
 */
class AgePolicyTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC);
    private final AgePolicy policy = new AgePolicy(CLOCK);

    /**
     * 정확히 만 19세인 사용자는 허용하고 하루 부족한 사용자는 거부하는지 검증합니다.
     */
    @Test
    void acceptsExactlyNineteenAndRejectsOneDayBeforeEligibility() {
        assertThatCode(() -> policy.requireEligible(LocalDate.of(2007, 8, 14))).doesNotThrowAnyException();
        assertUnderage(LocalDate.of(2007, 8, 15));
    }

    /**
     * 현재 날짜보다 미래인 생년월일을 거부하는지 검증합니다.
     */
    @Test
    void rejectsFutureBirthDate() {
        assertUnderage(LocalDate.of(2026, 8, 15));
    }

    /**
     * 윤년 생일의 연령 계산이 {@link LocalDate} 달력 규칙을 따르는지 검증합니다.
     */
    @Test
    void handlesLeapDayWithLocalDateCalendarRules() {
        AgePolicy leapDayPolicy = new AgePolicy(
                Clock.fixed(Instant.parse("2027-02-28T00:00:00Z"), ZoneOffset.UTC));

        assertThatCode(() -> leapDayPolicy.requireEligible(LocalDate.of(2008, 2, 29)))
                .doesNotThrowAnyException();
    }

    /**
     * 주어진 생년월일이 미성년 오류로 거부되는지 확인합니다.
     *
     * @param birthDate 거부를 기대하는 생년월일
     */
    private void assertUnderage(LocalDate birthDate) {
        assertThatThrownBy(() -> policy.requireEligible(birthDate))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNDERAGE_NOT_ALLOWED));
    }
}
