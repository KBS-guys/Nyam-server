package com.nyam.domain.dailysummary;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.nyam.domain.dailysummary.service.DailySummaryResult;
import com.nyam.domain.dailysummary.service.DailySummaryService;

import jakarta.persistence.EntityManagerFactory;

/** 실제 MySQL 8.4.5에서 snapshot 집계, strict-null과 사용자·날짜 격리를 검증합니다. */
@SpringBootTest(properties = {
        "NYAM_EMAIL_VERIFICATION_HMAC_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "NYAM_AUTH_ACCESS_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class DailySummaryMySqlIntegrationTest {

    private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.4.5");
    private static final LocalDate DATE = LocalDate.of(2026, 8, 29);
    private static final long USER_ONE = 701L;
    private static final long USER_TWO = 702L;
    private static final long FOOD_ID = 801L;

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MYSQL_IMAGE);

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    DailySummaryService dailySummaryService;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.update("DELETE FROM meal_items");
        jdbcTemplate.update("DELETE FROM meals");
        jdbcTemplate.update("DELETE FROM foods");
        jdbcTemplate.update("DELETE FROM users");
    }

    /** 같은 소유자·날짜 snapshot만 한 statement로 집계하고 현재 food 변경과 독립적인지 확인합니다. */
    @Test
    void aggregatesSnapshotsAndIsolatesOwnerAndDate() {
        insertUser(USER_ONE);
        insertUser(USER_TWO);
        insertFood();
        insertMeal(901L, USER_ONE, DATE);
        insertItem(1001L, 901L, "10.0000", "5.0000", null, "1.0000");
        insertMeal(902L, USER_ONE, DATE);
        insertItem(1002L, 902L, "20.1234", "7.0000", "3.0000", "0.0000");
        insertMeal(903L, USER_TWO, DATE);
        insertItem(1003L, 903L, "900.0000", "900.0000", "900.0000", "900.0000");
        insertMeal(904L, USER_ONE, DATE.plusDays(1));
        insertItem(1004L, 904L, "800.0000", "800.0000", "800.0000", "800.0000");

        jdbcTemplate.update("UPDATE foods SET energy = 999.0000 WHERE food_id = ?", FOOD_ID);

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        DailySummaryResult result = dailySummaryService.get(USER_ONE, DATE);

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(result.mealItemCount()).isEqualTo(2);
        assertThat(result.energy().value()).isEqualByComparingTo("30.1234");
        assertThat(result.energy().complete()).isTrue();
        assertThat(result.carbohydrate().value()).isEqualByComparingTo("12.0000");
        assertThat(result.protein().value()).isNull();
        assertThat(result.protein().complete()).isFalse();
        assertThat(result.fat().value()).isEqualByComparingTo("1.0000");
    }

    /** 빈 날짜와 item snapshot이 모두 0인 날짜의 공개 의미를 실제 database 결과로 구분합니다. */
    @Test
    void distinguishesEmptyDateFromRecordedZero() {
        insertUser(USER_ONE);
        insertFood();

        DailySummaryResult empty = dailySummaryService.get(USER_ONE, DATE);
        assertThat(empty.date()).isEqualTo(DATE);
        assertThat(empty.mealItemCount()).isZero();
        assertThat(empty.energy().value()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(empty.energy().complete()).isTrue();

        LocalDate zeroDate = DATE.plusDays(2);
        insertMeal(905L, USER_ONE, zeroDate);
        insertItem(1005L, 905L, "0.0000", "0.0000", "0.0000", "0.0000");

        DailySummaryResult recordedZero = dailySummaryService.get(USER_ONE, zeroDate);
        assertThat(recordedZero.mealItemCount()).isOne();
        assertThat(recordedZero.energy().value()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(recordedZero.energy().complete()).isTrue();
    }

    private void insertUser(long userId) {
        jdbcTemplate.update("""
                INSERT INTO users (user_id, display_email, canonical_email, birth_date, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, userId, "summary" + userId + "@example.com", "summary" + userId + "@example.com",
                LocalDate.of(2000, 1, 1), LocalDateTime.of(2026, 8, 29, 0, 0));
    }

    private void insertFood() {
        jdbcTemplate.update("""
                INSERT INTO foods (
                    food_id, source_food_code, food_name, normalized_name, food_type,
                    basis_amount, basis_unit, energy, energy_unit,
                    carbohydrate, carbohydrate_unit, protein, protein_unit, fat, fat_unit,
                    created_at, updated_at)
                VALUES (?, 'P001-000000801-0001', '집계용 식품', '집계용 식품', 'P',
                        100.0000, 'G', 100.0000, 'KCAL', 20.0000, 'G', 3.0000, 'G', 1.0000, 'G', ?, ?)
                """, FOOD_ID, LocalDateTime.of(2026, 8, 29, 0, 0), LocalDateTime.of(2026, 8, 29, 0, 0));
    }

    private void insertMeal(long mealId, long userId, LocalDate date) {
        jdbcTemplate.update("INSERT INTO meals (meal_id, user_id, meal_date) VALUES (?, ?, ?)",
                mealId, userId, date);
    }

    private void insertItem(
            long itemId,
            long mealId,
            String energy,
            String carbohydrate,
            String protein,
            String fat) {
        jdbcTemplate.update("""
                INSERT INTO meal_items (
                    meal_item_id, meal_id, item_position, food_id, food_name_snapshot,
                    consumed_amount, consumed_unit, energy_snapshot, energy_unit,
                    carbohydrate_snapshot, carbohydrate_unit, protein_snapshot, protein_unit,
                    fat_snapshot, fat_unit)
                VALUES (?, ?, 1, ?, '집계용 snapshot', 100.0000, 'G', ?, 'KCAL', ?, 'G', ?, 'G', ?, 'G')
                """, itemId, mealId, FOOD_ID, decimal(energy), decimal(carbohydrate), decimal(protein), decimal(fat));
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
