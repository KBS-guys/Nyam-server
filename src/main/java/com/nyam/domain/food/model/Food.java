package com.nyam.domain.food.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 공공 식품 원천에서 적재된 검색 및 영양 조회 기준 정보를 나타냅니다.
 */
@Entity
@Table(name = "foods")
public class Food {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "food_id")
    private Long id;

    @Column(name = "source_food_code", nullable = false, length = 19, unique = true)
    private String sourceFoodCode;

    @Column(name = "food_name", nullable = false, length = 500)
    private String foodName;

    @Column(name = "normalized_name", nullable = false, length = 500)
    private String normalizedName;

    @Column(name = "food_type", nullable = false, length = 1, columnDefinition = "CHAR(1)")
    private String foodType;

    @Column(name = "basis_amount", nullable = false, precision = 12, scale = 4)
    private BigDecimal basisAmount;

    @Column(name = "basis_unit", nullable = false, length = 8)
    private String basisUnit;

    @Column(name = "energy", precision = 12, scale = 4)
    private BigDecimal energy;

    @Column(name = "energy_unit", nullable = false, length = 8)
    private String energyUnit;

    @Column(name = "carbohydrate", precision = 12, scale = 4)
    private BigDecimal carbohydrate;

    @Column(name = "carbohydrate_unit", nullable = false, length = 8)
    private String carbohydrateUnit;

    @Column(name = "protein", precision = 12, scale = 4)
    private BigDecimal protein;

    @Column(name = "protein_unit", nullable = false, length = 8)
    private String proteinUnit;

    @Column(name = "fat", precision = 12, scale = 4)
    private BigDecimal fat;

    @Column(name = "fat_unit", nullable = false, length = 8)
    private String fatUnit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * JPA가 조회 결과를 복원할 때 사용하는 기본 생성자입니다.
     */
    protected Food() {
    }

    /**
     * 내부 식품 식별자를 반환합니다.
     *
     * @return 식품 식별자
     */
    public Long getId() {
        return id;
    }

    /**
     * 결정적 검색 정렬에 사용하는 외부 식품 코드를 반환합니다.
     *
     * @return 공공 원천 식품 코드
     */
    public String getSourceFoodCode() {
        return sourceFoodCode;
    }

    /**
     * 사용자에게 표시할 원본 식품명을 반환합니다.
     *
     * @return 원본 식품명
     */
    public String getFoodName() {
        return foodName;
    }

    /**
     * 영양정보 기준량을 반환합니다.
     *
     * @return 기준량
     */
    public BigDecimal getBasisAmount() {
        return basisAmount;
    }

    /**
     * 영양정보 기준 단위를 반환합니다.
     *
     * @return 영속 단위 값
     */
    public String getBasisUnit() {
        return basisUnit;
    }

    /**
     * 에너지 값을 반환합니다.
     *
     * @return 값이 없으면 {@code null}인 에너지 값
     */
    public BigDecimal getEnergy() {
        return energy;
    }

    /** 기록 시 snapshot에 복사할 에너지 단위를 반환합니다. */
    public String getEnergyUnit() {
        return energyUnit;
    }

    /**
     * 탄수화물 값을 반환합니다.
     *
     * @return 값이 없으면 {@code null}인 탄수화물 값
     */
    public BigDecimal getCarbohydrate() {
        return carbohydrate;
    }

    /** 기록 시 snapshot에 복사할 탄수화물 단위를 반환합니다. */
    public String getCarbohydrateUnit() {
        return carbohydrateUnit;
    }

    /**
     * 단백질 값을 반환합니다.
     *
     * @return 값이 없으면 {@code null}인 단백질 값
     */
    public BigDecimal getProtein() {
        return protein;
    }

    /** 기록 시 snapshot에 복사할 단백질 단위를 반환합니다. */
    public String getProteinUnit() {
        return proteinUnit;
    }

    /**
     * 지방 값을 반환합니다.
     *
     * @return 값이 없으면 {@code null}인 지방 값
     */
    public BigDecimal getFat() {
        return fat;
    }

    /** 기록 시 snapshot에 복사할 지방 단위를 반환합니다. */
    public String getFatUnit() {
        return fatUnit;
    }
}
