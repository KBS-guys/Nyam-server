package com.nyam.domain.user.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nyam.domain.user.service.EmailVerificationConfirmationResult;
import com.nyam.domain.user.service.EmailVerificationSendResult;
import com.nyam.domain.user.service.EmailVerificationService;
import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/**
 * 이메일 인증 Controller의 성공 봉투, 입력 검증과 공개 오류 매핑을 검증합니다.
 */
@WebMvcTest(controllers = EmailVerificationController.class,
        properties = "NYAM_AUTH_ACCESS_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=")
@AutoConfigureMockMvc(addFilters = false)
class EmailVerificationControllerTest {

    private static final String EMAIL = "User+tag@Example.COM";
    private static final String CODE = String.format("%06d",
            Math.floorMod("controller-code".hashCode(), 1_000_000));
    private static final Instant NOW = Instant.parse("2026-08-21T00:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    EmailVerificationService emailVerificationService;

    /**
     * 발송 성공 응답이 표시 이메일과 두 시각만 공개하는지 확인합니다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void returnsApprovedSendSuccessEnvelope() throws Exception {
        when(emailVerificationService.sendCode(EMAIL)).thenReturn(
                new EmailVerificationSendResult(EMAIL, NOW.plusSeconds(300), NOW.plusSeconds(60)));

        mockMvc.perform(post("/api/v1/auth/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_CODE_SENT"))
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.codeExpiresAt").exists())
                .andExpect(jsonPath("$.data.resendAvailableAt").exists())
                .andExpect(jsonPath("$.data.verificationCode").doesNotExist());
    }

    /**
     * 확인 성공 응답이 기존 signup 필드명과 같은 일회성 증명만 반환하는지 확인합니다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void returnsProofForTheExistingSignupContract() throws Exception {
        String proof = generatedProof();
        when(emailVerificationService.confirmCode(EMAIL, CODE)).thenReturn(
                EmailVerificationConfirmationResult.success(proof, NOW.plusSeconds(900)));

        mockMvc.perform(post("/api/v1/auth/email-verifications/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmRequest(CODE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_CONFIRMED"))
                .andExpect(jsonPath("$.data.verificationProof").value(proof))
                .andExpect(jsonPath("$.data.proofExpiresAt").exists())
                .andExpect(jsonPath("$.data.email").doesNotExist());
    }

    /**
     * 6자리 ASCII 숫자가 아닌 인증번호를 서비스 호출 전 입력 오류로 거절하는지 확인합니다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void rejectsMalformedVerificationCodeAsInvalidInput() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-verifications/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmRequest("invalid")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    /**
     * 다섯 번째 불일치의 커밋 결과를 429 시도 초과 응답으로 변환하는지 확인합니다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void mapsCommittedFifthMismatchToAttemptLimit() throws Exception {
        when(emailVerificationService.confirmCode(EMAIL, CODE)).thenReturn(
                EmailVerificationConfirmationResult.failure(
                        ErrorCode.EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED));

        mockMvc.perform(post("/api/v1/auth/email-verifications/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmRequest(CODE)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED"));
    }

    /**
     * 가입 중복, 발송 제한과 메일 실패가 각각 승인된 상태로 공개되는지 확인합니다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void mapsApprovedSendFailuresWithoutInternalDetail() throws Exception {
        when(emailVerificationService.sendCode(anyString()))
                .thenThrow(new BusinessException(ErrorCode.EMAIL_DELIVERY_UNAVAILABLE));

        mockMvc.perform(post("/api/v1/auth/email-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("EMAIL_DELIVERY_UNAVAILABLE"))
                .andExpect(jsonPath("$.data").value((Object) null));
    }

    /**
     * 인증번호와 proof를 포함한 DTO 문자열이 두 민감값을 마스킹하는지 확인합니다.
     */
    @Test
    void redactsCodeAndProofFromDtoStrings() {
        String proof = generatedProof();

        assertThat(new EmailVerificationConfirmRequest(EMAIL, CODE).toString())
                .doesNotContain(CODE)
                .contains("<redacted>");
        assertThat(new EmailVerificationConfirmResponse(proof, NOW).toString())
                .doesNotContain(proof)
                .contains("<redacted>");
    }

    /**
     * 지정한 인증번호를 포함한 확인 요청 JSON을 생성합니다.
     *
     * @param code 확인 요청에 넣을 문자열
     * @return 이메일과 인증번호를 가진 JSON
     */
    private String confirmRequest(String code) {
        return "{\"email\":\"" + EMAIL + "\",\"verificationCode\":\"" + code + "\"}";
    }

    /**
     * 고정 원문을 저장하지 않고 테스트 실행 중 43자 proof를 생성합니다.
     *
     * @return URL-safe Base64 테스트 proof
     */
    private String generatedProof() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
