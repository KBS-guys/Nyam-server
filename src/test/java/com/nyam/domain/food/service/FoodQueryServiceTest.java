package com.nyam.domain.food.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.nyam.domain.food.repository.FoodRepository;
import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/**
 * 식품 조회 서비스의 검색어 정규화, wildcard escape와 입력 경계를 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class FoodQueryServiceTest {

    @Mock
    FoodRepository foodRepository;

    /**
     * NFKC·공백·소문자 정규화 후 LIKE wildcard를 리터럴로 escape하고 결과를 20개로 제한하는지 확인합니다.
     */
    @Test
    void normalizesEscapesAndLimitsPrefixSearch() {
        when(foodRepository.findByNormalizedPrefix(any(), any())).thenReturn(List.of());
        FoodQueryService service = new FoodQueryService(foodRepository);

        service.search("  Ａ％_  밥  ");

        ArgumentCaptor<String> prefix = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(foodRepository).findByNormalizedPrefix(prefix.capture(), pageable.capture());
        assertThat(prefix.getValue()).isEqualTo("a!%!_ 밥");
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
    }

    /**
     * 정규화 후 빈 검색어와 100자를 초과한 검색어를 공개 입력 오류로 거절하는지 확인합니다.
     */
    @Test
    void rejectsBlankAndOverlongQuery() {
        FoodQueryService service = new FoodQueryService(foodRepository);

        assertThatThrownBy(() -> service.search("  \u3000 "))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        assertThatThrownBy(() -> service.search("가".repeat(101)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
    }
}
