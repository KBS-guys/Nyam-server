package com.nyam.global.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 생성·수정 및 논리 삭제 감사 정보를 제공하는 JPA 매핑 상위 클래스입니다.
 *
 * <p>회원가입 엔티티는 물리 삭제 계약을 사용하므로 현재 이 클래스를 상속하지 않습니다.</p>
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class Timestamped {

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @CreatedBy
    @Column(updatable = false)
    private Long createdBy;

    @LastModifiedDate
    @Column
    private LocalDateTime modifiedAt;

    @LastModifiedBy
    @Column
    private Long modifiedBy;

    @Column
    private LocalDateTime deletedAt;

    @Column
    private Long deletedBy;

    /**
     * 현재 시각과 수행 사용자를 기록하여 엔티티를 논리 삭제 상태로 만듭니다.
     *
     * @param userId 논리 삭제를 수행한 사용자 식별자
     */
    public void markDeleted(Long userId) {
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = userId;
    }

    /**
     * 논리 삭제 감사 정보를 제거하여 엔티티를 복구 상태로 되돌립니다.
     */
    public void restore() {
        this.deletedAt = null;
        this.deletedBy = null;
    }
}
