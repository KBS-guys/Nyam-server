package com.nyam.domain.meal.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nyam.domain.food.model.Food;
import com.nyam.domain.food.repository.FoodRepository;
import com.nyam.domain.meal.model.Meal;
import com.nyam.domain.meal.model.MealItem;
import com.nyam.domain.meal.repository.MealRepository;
import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/**
 * 인증 사용자 식사의 생성·날짜별 조회·삭제와 불변 영양 snapshot transaction을 수행합니다.
 */
@Service
public class MealService {

    private static final LocalDate MIN_MEAL_DATE = LocalDate.of(1000, 1, 1);
    private static final LocalDate MAX_MEAL_DATE = LocalDate.of(9999, 12, 31);
    private static final int MAX_ITEMS = 20;
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("10000.0000");
    private static final BigDecimal MAX_SNAPSHOT = new BigDecimal("999999999999.9999");

    private final MealRepository mealRepository;
    private final FoodRepository foodRepository;

    public MealService(MealRepository mealRepository, FoodRepository foodRepository) {
        this.mealRepository = mealRepository;
        this.foodRepository = foodRepository;
    }

    /**
     * 현재 food를 일괄 조회해 item snapshot을 계산하고 한 transaction으로 식사를 저장합니다.
     *
     * @param userId JWT subject에서 얻은 내부 사용자 식별자
     * @param command 식사 날짜와 food별 섭취량
     * @return 저장된 meal과 snapshot item
     * @throws BusinessException 입력, 중복 또는 food 존재 계약을 위반한 경우
     */
    @Transactional
    public Meal create(long userId, CreateMealCommand command) {
        requireUserId(userId);
        if (command == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        requireMealDate(command.mealDate());
        List<NormalizedItem> normalizedItems = normalizeItems(command.items());

        List<Long> foodIds = normalizedItems.stream().map(NormalizedItem::foodId).toList();
        Map<Long, Food> foods = new HashMap<>();
        foodRepository.findAllById(foodIds).forEach(food -> foods.put(food.getId(), food));
        if (foods.size() != foodIds.size()) {
            throw new BusinessException(ErrorCode.FOOD_NOT_FOUND);
        }

        Meal meal = new Meal(userId, command.mealDate());
        for (int index = 0; index < normalizedItems.size(); index++) {
            NormalizedItem input = normalizedItems.get(index);
            Food food = foods.get(input.foodId());
            meal.addItem(snapshot(index + 1, input.amount(), food));
        }
        return mealRepository.saveAndFlush(meal);
    }

    /**
     * 현재 사용자의 지정 날짜 meal과 item snapshot을 결정적 순서로 조회합니다.
     */
    @Transactional(readOnly = true)
    public List<Meal> list(long userId, LocalDate mealDate) {
        requireUserId(userId);
        requireMealDate(mealDate);
        return mealRepository.findByUserIdAndMealDateOrderByIdDesc(userId, mealDate);
    }

    /**
     * 현재 사용자가 소유한 식사만 삭제하며 없는 식사와 다른 사용자 식사를 구분하지 않습니다.
     */
    @Transactional
    public void delete(long userId, long mealId) {
        requireUserId(userId);
        if (mealId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Meal meal = mealRepository.findByIdAndUserId(mealId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEAL_NOT_FOUND));
        mealRepository.delete(meal);
        mealRepository.flush();
    }

    private List<NormalizedItem> normalizeItems(List<CreateMealCommand.Item> items) {
        if (items == null || items.isEmpty() || items.size() > MAX_ITEMS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Set<Long> uniqueFoodIds = new HashSet<>();
        return items.stream().map(item -> {
            if (item == null || item.foodId() == null || item.foodId() <= 0
                    || !uniqueFoodIds.add(item.foodId())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            return new NormalizedItem(item.foodId(), normalizeAmount(item.amount()));
        }).toList();
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        try {
            BigDecimal normalized = amount.setScale(4, RoundingMode.UNNECESSARY);
            if (normalized.signum() <= 0 || normalized.compareTo(MAX_AMOUNT) > 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private MealItem snapshot(int position, BigDecimal amount, Food food) {
        return new MealItem(
                position,
                food.getId(),
                food.getFoodName(),
                amount,
                food.getBasisUnit(),
                calculate(food.getEnergy(), amount, food.getBasisAmount()),
                food.getEnergyUnit(),
                calculate(food.getCarbohydrate(), amount, food.getBasisAmount()),
                food.getCarbohydrateUnit(),
                calculate(food.getProtein(), amount, food.getBasisAmount()),
                food.getProteinUnit(),
                calculate(food.getFat(), amount, food.getBasisAmount()),
                food.getFatUnit());
    }

    private BigDecimal calculate(BigDecimal source, BigDecimal amount, BigDecimal basisAmount) {
        if (source == null) {
            return null;
        }
        if (basisAmount == null || basisAmount.signum() <= 0) {
            throw new IllegalStateException("Food nutrition basis must be positive");
        }
        BigDecimal result = source.multiply(amount)
                .divide(basisAmount)
                .setScale(4, RoundingMode.HALF_UP);
        if (result.signum() < 0 || result.compareTo(MAX_SNAPSHOT) > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return result;
    }

    private void requireMealDate(LocalDate mealDate) {
        if (mealDate == null || mealDate.isBefore(MIN_MEAL_DATE) || mealDate.isAfter(MAX_MEAL_DATE)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void requireUserId(long userId) {
        if (userId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private record NormalizedItem(Long foodId, BigDecimal amount) {
    }
}
