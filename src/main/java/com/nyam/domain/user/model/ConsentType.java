package com.nyam.domain.user.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 회원가입 시 반드시 수집해야 하는 동의 항목의 종류입니다.
 */
@Schema(description = "회원가입 필수 동의 종류: 서비스 이용약관, 개인정보 수집·이용, 건강정보 처리")
public enum ConsentType {
    /** 서비스 이용약관 동의입니다. */
    TERMS,
    /** 개인정보 수집 및 이용 동의입니다. */
    PERSONAL_INFORMATION,
    /** 건강정보 처리 동의입니다. */
    HEALTH_INFORMATION
}
