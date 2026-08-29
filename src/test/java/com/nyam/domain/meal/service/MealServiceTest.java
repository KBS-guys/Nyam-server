package com.nyam.domain.meal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nyam.domain.food.model.Food;
import com.nyam.domain.food.repository.FoodRepository;
import com.nyam.domain.meal.model.Meal;
import com.nyam.domain.meal.model.MealItem;
import com.nyam.domain.meal.repository.MealRepository;
import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/**
 * 식사 생성의 입력 경계, snapshot 계산·null 보존과 소유 삭제 오류를 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class MealServiceTest {

    @Mock
    MealRepository mealRepository;

    @Mock
    FoodRepository foodRepository;

    MealService mealService;

    @BeforeEach
    void setUp() {
        mealService = new MealService(mealRepository, foodRepository);
    }

    /** 후행 0 amount를 무손실 scale 4로 정규화하고 영양값을 한 번 반올림하는지 확인합니다. */
    @Test
    void createsScaledSnapshotAndPreservesNull() {
        Food food = food(10L, "현미밥", "G",
                "100.0000", "123.4567", null, "3.3333", "1.1111");
        when(foodRepository.findAllById(List.of(10L))).thenReturn(List.of(food));
        when(mealRepository.saveAndFlush(any(Meal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Meal meal = mealService.create(7L, command(new BigDecimal("150.00000"), 10L));

        MealItem item = meal.getItems().get(0);
        assertThat(item.getConsumedAmount()).isEqualByComparingTo("150.0000");
        assertThat(item.getConsumedAmount().scale()).isEqualTo(4);
        assertThat(item.getEnergySnapshot()).isEqualByComparingTo("185.1851");
        assertThat(item.getCarbohydrateSnapshot()).isNull();
        assertThat(item.getProteinSnapshot()).isEqualByComparingTo("5.0000");
        assertThat(item.getConsumedUnit()).isEqualTo("G");
        assertThat(item.getEnergyUnit()).isEqualTo("KCAL");
    }

    /** 값 변경이 필요한 amount와 같은 food 중복을 저장소 접근 전에 거절하는지 확인합니다. */
    @Test
    void rejectsRoundedAmountAndDuplicateFood() {
        assertInvalid(() -> mealService.create(7L, command(new BigDecimal("0.12345"), 10L)));
        assertInvalid(() -> mealService.create(7L, new CreateMealCommand(
                LocalDate.of(2026, 8, 29),
                List.of(
                        new CreateMealCommand.Item(10L, BigDecimal.ONE),
                        new CreateMealCommand.Item(10L, BigDecimal.TEN)))));

        verifyNoInteractions(foodRepository);
    }

    /** 최대 섭취량은 허용하고 0·상한 초과와 20개 초과 item은 거절하는지 확인합니다. */
    @Test
    void enforcesAmountAndItemCountBoundaries() {
        Food food = food(10L, "현미밥", "G",
                "100.0000", "100.0000", "20.0000", "3.0000", "1.0000");
        when(foodRepository.findAllById(List.of(10L))).thenReturn(List.of(food));
        when(mealRepository.saveAndFlush(any(Meal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Meal accepted = mealService.create(7L, command(new BigDecimal("10000.0000"), 10L));
        assertThat(accepted.getItems().get(0).getConsumedAmount()).isEqualByComparingTo("10000.0000");

        assertInvalid(() -> mealService.create(7L, command(BigDecimal.ZERO, 10L)));
        assertInvalid(() -> mealService.create(7L, command(new BigDecimal("10000.0001"), 10L)));
        List<CreateMealCommand.Item> tooMany = java.util.stream.LongStream.rangeClosed(1, 21)
                .mapToObj(id -> new CreateMealCommand.Item(id, BigDecimal.ONE))
                .toList();
        assertInvalid(() -> mealService.create(7L, new CreateMealCommand(LocalDate.of(2026, 8, 29), tooMany)));
    }

    /** 한 food라도 없으면 식사 저장 없이 FOOD_NOT_FOUND로 끝나는지 확인합니다. */
    @Test
    void rejectsWholeMealWhenAnyFoodIsMissing() {
        Food existing = mock(Food.class);
        when(existing.getId()).thenReturn(10L);
        when(foodRepository.findAllById(List.of(10L, 11L))).thenReturn(List.of(existing));

        assertThatThrownBy(() -> mealService.create(7L, new CreateMealCommand(
                LocalDate.of(2026, 8, 29),
                List.of(
                        new CreateMealCommand.Item(10L, BigDecimal.ONE),
                        new CreateMealCommand.Item(11L, BigDecimal.ONE)))))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FOOD_NOT_FOUND));

        verifyNoInteractions(mealRepository);
    }

    /** 다른 소유자 식사와 없는 식사를 같은 공개 오류로 처리하는지 확인합니다. */
    @Test
    void hidesNonOwnedMealDuringDelete() {
        when(mealRepository.findByIdAndUserId(99L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mealService.delete(7L, 99L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEAL_NOT_FOUND));
    }

    private CreateMealCommand command(BigDecimal amount, long foodId) {
        return new CreateMealCommand(
                LocalDate.of(2026, 8, 29),
                List.of(new CreateMealCommand.Item(foodId, amount)));
    }

    private Food food(
            long id,
            String name,
            String basisUnit,
            String basis,
            String energy,
            String carbohydrate,
            String protein,
            String fat) {
        Food food = mock(Food.class);
        when(food.getId()).thenReturn(id);
        when(food.getFoodName()).thenReturn(name);
        when(food.getBasisAmount()).thenReturn(new BigDecimal(basis));
        when(food.getBasisUnit()).thenReturn(basisUnit);
        when(food.getEnergy()).thenReturn(decimal(energy));
        when(food.getEnergyUnit()).thenReturn("KCAL");
        when(food.getCarbohydrate()).thenReturn(decimal(carbohydrate));
        when(food.getCarbohydrateUnit()).thenReturn("G");
        when(food.getProtein()).thenReturn(decimal(protein));
        when(food.getProteinUnit()).thenReturn("G");
        when(food.getFat()).thenReturn(decimal(fat));
        when(food.getFatUnit()).thenReturn("G");
        return food;
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
    }
}
