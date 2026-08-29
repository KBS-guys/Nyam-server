package com.nyam.domain.meal.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 기록 시점 food의 이름·단위·섭취량 기준 영양값을 보존하는 식사 항목입니다.
 */
@Entity
@Table(name = "meal_items")
public class MealItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "meal_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meal_id", nullable = false)
    private Meal meal;

    @Column(name = "item_position", nullable = false)
    private short itemPosition;

    @Column(name = "food_id", nullable = false)
    private Long foodId;

    @Column(name = "food_name_snapshot", nullable = false, length = 500)
    private String foodNameSnapshot;

    @Column(name = "consumed_amount", nullable = false, precision = 12, scale = 4)
    private BigDecimal consumedAmount;

    @Column(name = "consumed_unit", nullable = false, length = 8)
    private String consumedUnit;

    @Column(name = "energy_snapshot", precision = 16, scale = 4)
    private BigDecimal energySnapshot;

    @Column(name = "energy_unit", nullable = false, length = 8)
    private String energyUnit;

    @Column(name = "carbohydrate_snapshot", precision = 16, scale = 4)
    private BigDecimal carbohydrateSnapshot;

    @Column(name = "carbohydrate_unit", nullable = false, length = 8)
    private String carbohydrateUnit;

    @Column(name = "protein_snapshot", precision = 16, scale = 4)
    private BigDecimal proteinSnapshot;

    @Column(name = "protein_unit", nullable = false, length = 8)
    private String proteinUnit;

    @Column(name = "fat_snapshot", precision = 16, scale = 4)
    private BigDecimal fatSnapshot;

    @Column(name = "fat_unit", nullable = false, length = 8)
    private String fatUnit;

    /** JPA가 조회 결과를 복원할 때 사용하는 기본 생성자입니다. */
    protected MealItem() {
    }

    /**
     * 원천 food를 다시 읽지 않아도 되는 불변 snapshot 항목을 생성합니다.
     */
    public MealItem(
            int itemPosition,
            Long foodId,
            String foodNameSnapshot,
            BigDecimal consumedAmount,
            String consumedUnit,
            BigDecimal energySnapshot,
            String energyUnit,
            BigDecimal carbohydrateSnapshot,
            String carbohydrateUnit,
            BigDecimal proteinSnapshot,
            String proteinUnit,
            BigDecimal fatSnapshot,
            String fatUnit) {
        this.itemPosition = (short) itemPosition;
        this.foodId = foodId;
        this.foodNameSnapshot = foodNameSnapshot;
        this.consumedAmount = consumedAmount;
        this.consumedUnit = consumedUnit;
        this.energySnapshot = energySnapshot;
        this.energyUnit = energyUnit;
        this.carbohydrateSnapshot = carbohydrateSnapshot;
        this.carbohydrateUnit = carbohydrateUnit;
        this.proteinSnapshot = proteinSnapshot;
        this.proteinUnit = proteinUnit;
        this.fatSnapshot = fatSnapshot;
        this.fatUnit = fatUnit;
    }

    void attachTo(Meal owner) {
        this.meal = owner;
    }

    public Long getId() {
        return id;
    }

    public int getItemPosition() {
        return itemPosition;
    }

    public Long getFoodId() {
        return foodId;
    }

    public String getFoodNameSnapshot() {
        return foodNameSnapshot;
    }

    public BigDecimal getConsumedAmount() {
        return consumedAmount;
    }

    public String getConsumedUnit() {
        return consumedUnit;
    }

    public BigDecimal getEnergySnapshot() {
        return energySnapshot;
    }

    public String getEnergyUnit() {
        return energyUnit;
    }

    public BigDecimal getCarbohydrateSnapshot() {
        return carbohydrateSnapshot;
    }

    public String getCarbohydrateUnit() {
        return carbohydrateUnit;
    }

    public BigDecimal getProteinSnapshot() {
        return proteinSnapshot;
    }

    public String getProteinUnit() {
        return proteinUnit;
    }

    public BigDecimal getFatSnapshot() {
        return fatSnapshot;
    }

    public String getFatUnit() {
        return fatUnit;
    }
}
