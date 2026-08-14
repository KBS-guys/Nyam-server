package com.nyam.domain.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nyam.domain.user.model.UserAccount;

/**
 * 사용자 계정 저장과 이메일 중복 확인을 담당하는 저장소입니다.
 */
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    /**
     * 정규화 이메일을 이미 사용 중인 계정이 있는지 확인합니다.
     *
     * @param canonicalEmail 비교할 정규화 이메일
     * @return 동일한 정규화 이메일의 계정이 있으면 {@code true}
     */
    boolean existsByCanonicalEmail(String canonicalEmail);
}
