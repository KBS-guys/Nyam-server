package com.nyam.domain.meal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.nyam.domain.food.model.Food;
import com.nyam.domain.food.repository.FoodRepository;
import com.nyam.domain.meal.model.Meal;
import com.nyam.domain.meal.model.MealItem;
import com.nyam.domain.meal.service.CreateMealCommand;
import com.nyam.domain.meal.service.MealService;
import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

import jakarta.persistence.EntityManagerFactory;

/**
 * 실제 MySQL 8.4.5에서 Meal Migration, transaction, snapshot 독립성과 소유권 격리를 검증합니다.
 */
@SpringBootTest(properties = {
        "NYAM_EMAIL_VERIFICATION_HMAC_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "NYAM_AUTH_ACCESS_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class MealMySqlIntegrationTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.4.5");
    private static final LocalDate MEAL_DATE = LocalDate.of(2026, 8, 29);
    private static final long USER_ONE = 101L;
    private static final long USER_TWO = 102L;
    private static final long FOOD_ONE = 201L;
    private static final long FOOD_TWO = 202L;

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MYSQL_IMAGE);

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MealService mealService;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @MockitoSpyBean
    FoodRepository foodRepository;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.update("DELETE FROM meal_items");
        jdbcTemplate.update("DELETE FROM meals");
        jdbcTemplate.update("DELETE FROM foods");
        jdbcTemplate.update("DELETE FROM users");
    }

    /** snapshot 변경 독립성, nested 정렬, 사용자 격리와 삭제 존재 은닉을 한 흐름으로 검증합니다. */
    @Test
    void persistsImmutableSnapshotsAndIsolatesOwners() {
        insertUser(USER_ONE);
        insertUser(USER_TWO);
        insertFood(FOOD_ONE, "현미밥", "G", "123.4567", null, "3.3333", "1.1111");
        insertFood(FOOD_TWO, "우유", "ML", "60.0000", "5.0000", "3.0000", "2.0000");

        Meal first = mealService.create(USER_ONE, command(
                item(FOOD_TWO, "200"),
                item(FOOD_ONE, "150.00000")));
        Meal second = mealService.create(USER_ONE, command(item(FOOD_ONE, "100")));
        Meal otherOwner = mealService.create(USER_TWO, command(item(FOOD_ONE, "100")));

        jdbcTemplate.update("UPDATE foods SET food_name = ?, energy = ? WHERE food_id = ?",
                "수정된 현미밥", new BigDecimal("999.0000"), FOOD_ONE);

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        List<Meal> meals = mealService.list(USER_ONE, MEAL_DATE);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(meals).extracting(Meal::getId).containsExactly(second.getId(), first.getId());
        assertThat(meals).noneMatch(meal -> meal.getId().equals(otherOwner.getId()));
        assertThat(meals.get(1).getItems()).extracting(MealItem::getFoodId)
                .containsExactly(FOOD_TWO, FOOD_ONE);
        MealItem foodOneSnapshot = meals.get(1).getItems().get(1);
        assertThat(foodOneSnapshot.getFoodNameSnapshot()).isEqualTo("현미밥");
        assertThat(foodOneSnapshot.getEnergySnapshot()).isEqualByComparingTo("185.1851");
        assertThat(foodOneSnapshot.getCarbohydrateSnapshot()).isNull();

        assertThatThrownBy(() -> mealService.delete(USER_ONE, otherOwner.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEAL_NOT_FOUND));

        mealService.delete(USER_ONE, first.getId());
        assertThat(count("SELECT COUNT(*) FROM meals WHERE meal_id = ?", first.getId())).isZero();
        assertThat(count("SELECT COUNT(*) FROM meal_items WHERE meal_id = ?", first.getId())).isZero();
        assertThat(count("SELECT COUNT(*) FROM foods WHERE food_id = ?", FOOD_ONE)).isOne();
    }

    /** 두 번째 item 쓰기 실패를 강제했을 때 먼저 쓰인 meal과 item까지 모두 rollback되는지 확인합니다. */
    @Test
    void rollsBackWholeMealWhenOneItemWriteFails() {
        insertUser(USER_ONE);
        insertFood(FOOD_ONE, "현미밥", "G", "100.0000", "20.0000", "3.0000", "1.0000");
        insertFood(FOOD_TWO, "우유", "ML", "60.0000", "5.0000", "3.0000", "2.0000");
        Food first = snapshotFood(FOOD_ONE, "G");
        Food invalidSecond = snapshotFood(FOOD_TWO, "ml");
        doReturn(List.of(first, invalidSecond)).when(foodRepository).findAllById(any());

        assertThatThrownBy(() -> mealService.create(USER_ONE, command(
                item(FOOD_ONE, "100"),
                item(FOOD_TWO, "100"))))
                .isInstanceOf(DataAccessException.class);

        assertThat(count("SELECT COUNT(*) FROM meals")).isZero();
        assertThat(count("SELECT COUNT(*) FROM meal_items")).isZero();
    }

    /** binary 단위 CHECK, meal별 food unique, food RESTRICT와 사용자 cascade를 실제 DB에서 검증합니다. */
    @Test
    void enforcesDatabaseConstraintsAndDeleteDirections() {
        insertUser(USER_ONE);
        insertFood(FOOD_ONE, "현미밥", "G", "100.0000", "20.0000", "3.0000", "1.0000");
        insertFood(FOOD_TWO, "우유", "ML", "60.0000", "5.0000", "3.0000", "2.0000");
        insertMeal(301L, USER_ONE);
        insertMealItem(401L, 301L, 1, FOOD_ONE, "G", "KCAL");

        assertThat(jdbcTemplate.queryForObject("""
                SELECT collation_name
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'meal_items'
                  AND column_name = 'consumed_unit'
                """, String.class)).isEqualTo("ascii_bin");

        assertThatThrownBy(() -> insertMealItem(402L, 301L, 2, FOOD_TWO, "g", "KCAL"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertMealItem(403L, 301L, 2, FOOD_ONE, "G", "KCAL"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM foods WHERE food_id = ?", FOOD_ONE))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbcTemplate.update("DELETE FROM meals WHERE meal_id = ?", 301L);
        assertThat(count("SELECT COUNT(*) FROM meal_items WHERE meal_id = ?", 301L)).isZero();
        assertThat(count("SELECT COUNT(*) FROM foods WHERE food_id = ?", FOOD_ONE)).isOne();

        insertMeal(302L, USER_ONE);
        insertMealItem(404L, 302L, 1, FOOD_ONE, "G", "KCAL");
        jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", USER_ONE);
        assertThat(count("SELECT COUNT(*) FROM meals WHERE meal_id = ?", 302L)).isZero();
        assertThat(count("SELECT COUNT(*) FROM meal_items WHERE meal_id = ?", 302L)).isZero();
    }

    private CreateMealCommand command(CreateMealCommand.Item... items) {
        return new CreateMealCommand(MEAL_DATE, List.of(items));
    }

    private CreateMealCommand.Item item(long foodId, String amount) {
        return new CreateMealCommand.Item(foodId, new BigDecimal(amount));
    }

    private void insertUser(long userId) {
        jdbcTemplate.update("""
                INSERT INTO users (user_id, display_email, canonical_email, birth_date, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, userId, "user" + userId + "@example.com", "user" + userId + "@example.com",
                LocalDate.of(2000, 1, 1), LocalDateTime.of(2026, 8, 29, 0, 0));
    }

    private void insertFood(
            long foodId,
            String name,
            String basisUnit,
            String energy,
            String carbohydrate,
            String protein,
            String fat) {
        jdbcTemplate.update("""
                INSERT INTO foods (
                    food_id, source_food_code, food_name, normalized_name, food_type,
                    basis_amount, basis_unit, energy, energy_unit,
                    carbohydrate, carbohydrate_unit, protein, protein_unit, fat, fat_unit,
                    created_at, updated_at)
                VALUES (?, ?, ?, ?, 'P', 100.0000, ?, ?, 'KCAL', ?, 'G', ?, 'G', ?, 'G', ?, ?)
                """,
                foodId,
                "P001-%09d-0001".formatted(foodId),
                name,
                name,
                basisUnit,
                decimal(energy),
                decimal(carbohydrate),
                decimal(protein),
                decimal(fat),
                LocalDateTime.of(2026, 8, 29, 0, 0),
                LocalDateTime.of(2026, 8, 29, 0, 0));
    }

    private void insertMeal(long mealId, long userId) {
        jdbcTemplate.update("INSERT INTO meals (meal_id, user_id, meal_date) VALUES (?, ?, ?)",
                mealId, userId, MEAL_DATE);
    }

    private void insertMealItem(
            long itemId,
            long mealId,
            int position,
            long foodId,
            String consumedUnit,
            String energyUnit) {
        jdbcTemplate.update("""
                INSERT INTO meal_items (
                    meal_item_id, meal_id, item_position, food_id, food_name_snapshot,
                    consumed_amount, consumed_unit, energy_snapshot, energy_unit,
                    carbohydrate_snapshot, carbohydrate_unit, protein_snapshot, protein_unit,
                    fat_snapshot, fat_unit)
                VALUES (?, ?, ?, ?, 'snapshot', 100.0000, ?, 100.0000, ?, 20.0000, 'G',
                        3.0000, 'G', 1.0000, 'G')
                """, itemId, mealId, position, foodId, consumedUnit, energyUnit);
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private Food snapshotFood(long foodId, String basisUnit) {
        Food food = mock(Food.class);
        when(food.getId()).thenReturn(foodId);
        when(food.getFoodName()).thenReturn("rollback food " + foodId);
        when(food.getBasisAmount()).thenReturn(new BigDecimal("100.0000"));
        when(food.getBasisUnit()).thenReturn(basisUnit);
        when(food.getEnergy()).thenReturn(new BigDecimal("100.0000"));
        when(food.getEnergyUnit()).thenReturn("KCAL");
        when(food.getCarbohydrate()).thenReturn(new BigDecimal("20.0000"));
        when(food.getCarbohydrateUnit()).thenReturn("G");
        when(food.getProtein()).thenReturn(new BigDecimal("3.0000"));
        when(food.getProteinUnit()).thenReturn("G");
        when(food.getFat()).thenReturn(new BigDecimal("1.0000"));
        when(food.getFatUnit()).thenReturn("G");
        return food;
    }

    private long count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }
}
