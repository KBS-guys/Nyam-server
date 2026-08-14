package com.nyam.domain.user.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nyam.domain.user.service.UserRegistrationService;
import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

import java.util.stream.Stream;

/**
 * 회원가입 컨트롤러의 상태 코드, 공통 응답, 공개 필드 계약을 검증합니다.
 */
@WebMvcTest(UserRegistrationController.class)
class UserRegistrationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserRegistrationService registrationService;

    /**
     * 성공 응답에 승인된 이메일만 포함하고 내부 식별자와 토큰을 노출하지 않는지 확인합니다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void returnsApprovedSuccessEnvelopeWithoutInternalIdentityOrToken() throws Exception {
        when(registrationService.register(any())).thenReturn("User@Example.COM");

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestWithExtraEmail()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SIGNUP_COMPLETED"))
                .andExpect(jsonPath("$.data.email").value("User@Example.COM"))
                .andExpect(jsonPath("$.data.userId").doesNotExist())
                .andExpect(jsonPath("$.data.token").doesNotExist())
                .andExpect(jsonPath("$.data.verificationProof").doesNotExist());
    }

    /**
     * 필수 필드가 없는 요청을 공통 입력 오류로 변환하는지 확인합니다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void mapsMalformedRequestToInvalidInput() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    /**
     * 제출된 증명 형식이 잘못되어도 일반 입력 오류가 아니라 단일 인증 실패 계약으로 반환하는지 확인합니다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void mapsMalformedSubmittedProofToEmailVerificationInvalid() throws Exception {
        when(registrationService.register(argThat(command -> "invalid-proof".equals(
                command.verificationProof()))))
                .thenThrow(new BusinessException(ErrorCode.EMAIL_VERIFICATION_INVALID));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestWithProofAndFirstConsentType("invalid-proof", "TERMS")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_INVALID"));
    }

    /**
     * 알 수 없는 동의 문자열을 일반 JSON 오류가 아닌 필수 동의 비즈니스 오류로 반환하는지 확인합니다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void mapsUnknownConsentTypeToRequiredConsentMissing() throws Exception {
        when(registrationService.register(argThat(command -> command.consents().stream()
                .anyMatch(consent -> consent.type() == null))))
                .thenThrow(new BusinessException(ErrorCode.REQUIRED_CONSENT_MISSING));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestWithProofAndFirstConsentType(
                                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "UNKNOWN")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REQUIRED_CONSENT_MISSING"));
    }

    /**
     * 비즈니스 실패가 내부 상세 없이 승인된 오류 응답으로 반환되는지 확인합니다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void mapsApprovedBusinessFailureWithoutInternalDetail() throws Exception {
        when(registrationService.register(any()))
                .thenThrow(new BusinessException(ErrorCode.EMAIL_VERIFICATION_INVALID));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestWithExtraEmail()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_INVALID"))
                .andExpect(jsonPath("$.data").value((Object) null));
    }

    /**
     * 모든 회원가입 공개 오류가 설계된 HTTP 상태와 코드로 매핑되는지 확인합니다.
     *
     * @param errorCode 서비스가 발생시킬 공개 오류
     * @param status 기대하는 HTTP 상태 코드
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @ParameterizedTest
    @MethodSource("businessErrors")
    void mapsEveryApprovedSignupBusinessError(ErrorCode errorCode, int status) throws Exception {
        when(registrationService.register(any())).thenThrow(new BusinessException(errorCode));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestWithExtraEmail()))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(errorCode.getCode()))
                .andExpect(jsonPath("$.data").value((Object) null));
    }

    /**
     * 회원가입에서 공개하는 오류와 HTTP 상태 조합을 제공합니다.
     *
     * @return 매개변수화 테스트용 오류 및 상태 스트림
     */
    private static Stream<Arguments> businessErrors() {
        return Stream.of(
                Arguments.of(ErrorCode.EMAIL_ALREADY_REGISTERED, 409),
                Arguments.of(ErrorCode.EMAIL_VERIFICATION_INVALID, 422),
                Arguments.of(ErrorCode.UNDERAGE_NOT_ALLOWED, 422),
                Arguments.of(ErrorCode.REQUIRED_CONSENT_MISSING, 422),
                Arguments.of(ErrorCode.PASSWORD_POLICY_VIOLATION, 422),
                Arguments.of(ErrorCode.INTERNAL_SERVER_ERROR, 500));
    }

    /**
     * 클라이언트 이메일 필드가 서버의 증명 결합 이메일을 덮어쓰지 못하는지 확인할 요청을 만듭니다.
     *
     * @return 필수 회원가입 값과 무시되어야 할 추가 이메일을 포함한 JSON
     */
    static String validRequestWithExtraEmail() {
        return """
                {
                  "verificationProof": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                  "password": "safe-password-123",
                  "birthDate": "2000-01-01",
                  "email": "client-controlled@example.com",
                  "consents": [
                    {"type": "TERMS", "version": "1.0"},
                    {"type": "PERSONAL_INFORMATION", "version": "1.0"},
                    {"type": "HEALTH_INFORMATION", "version": "1.0"}
                  ]
                }
                """;
    }

    /**
     * 증명과 첫 번째 동의 종류를 바꿀 수 있는 회원가입 요청을 생성합니다.
     *
     * @param proof 요청에 제출할 검증 증명 문자열
     * @param firstConsentType 첫 번째 동의 항목의 공개 문자열
     * @return 지정한 값을 포함한 회원가입 JSON
     */
    private static String requestWithProofAndFirstConsentType(String proof, String firstConsentType) {
        return """
                {
                  "verificationProof": "%s",
                  "password": "safe-password-123",
                  "birthDate": "2000-01-01",
                  "consents": [
                    {"type": "%s", "version": "1.0"},
                    {"type": "PERSONAL_INFORMATION", "version": "1.0"},
                    {"type": "HEALTH_INFORMATION", "version": "1.0"}
                  ]
                }
                """.formatted(proof, firstConsentType);
    }
}
