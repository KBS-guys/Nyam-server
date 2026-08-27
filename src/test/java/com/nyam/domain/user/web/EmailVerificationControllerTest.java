package com.nyam.domain.user.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nyam.domain.user.service.EmailVerificationSendResult;
import com.nyam.domain.user.service.EmailVerificationService;
import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/**
 * 인증번호 발송 API와 confirm 경로 제거를 검증합니다.
 */
@WebMvcTest(controllers = EmailVerificationController.class,
        properties = "NYAM_AUTH_ACCESS_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=")
@AutoConfigureMockMvc(addFilters = false)
class EmailVerificationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    EmailVerificationService emailVerificationService;

    @Test
    void sendsVerificationCode() throws Exception {
        when(emailVerificationService.sendCode("User@Example.COM"))
                .thenReturn(new EmailVerificationSendResult(
                        "User@Example.COM",
                        Instant.parse("2026-08-27T00:05:00Z"),
                        Instant.parse("2026-08-27T00:01:00Z")));

        mockMvc.perform(post("/api/v1/auth/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"User@Example.COM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_CODE_SENT"))
                .andExpect(jsonPath("$.data.email").value("User@Example.COM"));
    }

    @Test
    void concurrentSendLoserReceivesPublicLimitResponse() throws Exception {
        when(emailVerificationService.sendCode("User@Example.COM"))
                .thenThrow(new BusinessException(ErrorCode.EMAIL_VERIFICATION_SEND_LIMITED));

        mockMvc.perform(post("/api/v1/auth/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"User@Example.COM\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_SEND_LIMITED"));
    }

    @Test
    void confirmEndpointNoLongerExists() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-verifications/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"verificationCode\":\"012345\"}"))
                .andExpect(status().isNotFound());
    }
}
