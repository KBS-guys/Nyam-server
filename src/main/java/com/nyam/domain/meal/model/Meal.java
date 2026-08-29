package com.nyam.domain.meal.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * 인증 사용자에게 귀속된 한 날짜의 식사 기록을 나타냅니다.
 */
@Entity
@Table(name = "meals")
public class Meal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "meal_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "meal_date", nullable = false)
    private LocalDate mealDate;

    @OneToMany(mappedBy = "meal", cascade = CascadeType.PERSIST)
    @OrderBy("itemPosition ASC")
    private List<MealItem> items = new ArrayList<>();

    /** JPA가 조회 결과를 복원할 때 사용하는 기본 생성자입니다. */
    protected Meal() {
    }

    /**
     * 인증 소유자와 사용자가 지정한 식사 날짜로 신규 식사를 생성합니다.
     *
     * @param userId JWT subject에서 얻은 내부 사용자 식별자
     * @param mealDate 사용자가 지정한 식사 기준 날짜
     */
    public Meal(Long userId, LocalDate mealDate) {
        this.userId = userId;
        this.mealDate = mealDate;
    }

    /**
     * 요청 순서와 snapshot이 확정된 item을 식사에 연결합니다.
     *
     * @param item 이 식사에 귀속할 item
     */
    public void addItem(MealItem item) {
        item.attachTo(this);
        items.add(item);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDate getMealDate() {
        return mealDate;
    }

    /**
     * 요청 순서대로 정렬된 읽기 전용 item 목록을 반환합니다.
     *
     * @return 변경할 수 없는 meal item 목록
     */
    public List<MealItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
