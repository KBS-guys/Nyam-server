package com.nyam.domain.food.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nyam.domain.food.model.Food;
import com.nyam.domain.food.repository.FoodRepository;
import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/**
 * 인증된 식품 검색과 상세 조회의 입력 및 조회 규칙을 수행합니다.
 */
@Service
public class FoodQueryService {

    private static final int MAX_QUERY_LENGTH = 100;
    private static final int SEARCH_LIMIT = 20;

    private final FoodRepository foodRepository;

    /**
     * 식품 읽기 저장소를 주입받습니다.
     *
     * @param foodRepository 식품 조회 저장소
     */
    public FoodQueryService(FoodRepository foodRepository) {
        this.foodRepository = foodRepository;
    }

    /**
     * 검색어를 정규화하고 최대 20개의 결정적 접두사 검색 결과를 반환합니다.
     *
     * @param query 사용자가 제출한 식품명 접두사
     * @return 정규화 접두사와 일치하는 식품 목록
     * @throws BusinessException 정규화된 검색어가 1자 미만 또는 100자를 초과한 경우
     */
    @Transactional(readOnly = true)
    public List<Food> search(String query) {
        String normalized = FoodNameNormalizer.normalize(query);
        int length = FoodNameNormalizer.characterCount(normalized);
        if (length < 1 || length > MAX_QUERY_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return foodRepository.findByNormalizedPrefix(
                FoodNameNormalizer.escapeLikePrefix(normalized), PageRequest.of(0, SEARCH_LIMIT));
    }

    /**
     * 양의 식품 식별자로 한 식품을 조회합니다.
     *
     * @param foodId 조회할 식품 식별자
     * @return 조회된 식품
     * @throws BusinessException 식별자가 유효하지 않거나 식품이 존재하지 않는 경우
     */
    @Transactional(readOnly = true)
    public Food get(long foodId) {
        if (foodId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return foodRepository.findById(foodId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FOOD_NOT_FOUND));
    }
}
