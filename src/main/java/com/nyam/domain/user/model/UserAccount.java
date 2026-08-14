package com.nyam.domain.user.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 회원가입으로 생성되는 최소 사용자 계정 정보를 저장합니다.
 */
@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "display_email", nullable = false, length = 254)
    private String displayEmail;

    @Column(name = "canonical_email", nullable = false, length = 254, unique = true)
    private String canonicalEmail;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * JPA 엔티티 생성을 위한 기본 생성자입니다.
     */
    protected UserAccount() {
    }

    /**
     * 검증된 이메일과 가입 자격 정보를 가진 신규 사용자를 생성합니다.
     *
     * @param displayEmail 응답과 전달에 사용할 원본 표기 이메일
     * @param canonicalEmail 중복 비교에 사용할 정규화 이메일
     * @param birthDate 성인 여부 확인에 사용된 생년월일
     * @param createdAt 사용자 생성 시각
     */
    public UserAccount(String displayEmail, String canonicalEmail, LocalDate birthDate, LocalDateTime createdAt) {
        this.displayEmail = displayEmail;
        this.canonicalEmail = canonicalEmail;
        this.birthDate = birthDate;
        this.createdAt = createdAt;
    }

    /**
     * 데이터베이스가 생성한 사용자 식별자를 반환합니다.
     *
     * @return 사용자 식별자
     */
    public Long getId() {
        return id;
    }

    /**
     * 원본 표기를 보존한 이메일을 반환합니다.
     *
     * @return 원본 표기 이메일
     */
    public String getDisplayEmail() {
        return displayEmail;
    }

    /**
     * 중복 비교에 사용하는 정규화 이메일을 반환합니다.
     *
     * @return 정규화 이메일
     */
    public String getCanonicalEmail() {
        return canonicalEmail;
    }

    /**
     * 가입 시 제출한 생년월일을 반환합니다.
     *
     * @return 생년월일
     */
    public LocalDate getBirthDate() {
        return birthDate;
    }

    /**
     * 사용자가 생성된 UTC 시각을 반환합니다.
     *
     * @return 사용자 생성 시각
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
