package com.nyam.domain.user.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nyam.domain.user.service.RegisterUserResult;
import com.nyam.domain.user.service.UserRegistrationService;
import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

/**
 * 직접 인증번호 회원가입의 HTTP 입력과 공개 오류 매핑을 검증합니다.
 */
@WebMvcTest(controllers = UserRegistrationController.class,
        properties = "NYAM_AUTH_ACCESS_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=")
@AutoConfigureMockMvc(addFilters = false)
class UserRegistrationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserRegistrationService registrationService;

    @Test
    void signupReturnsChallengeDisplayEmail() throws Exception {
        when(registrationService.register(any()))
                .thenReturn(RegisterUserResult.success("User@Example.COM"));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SIGNUP_COMPLETED"))
                .andExpect(jsonPath("$.data.email").value("User@Example.COM"));
    }

    @Test
    void missingBooleanIsInvalidInput() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().replace(",\n  \"healthInformationAgreed\": true", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(registrationService);
    }

    @Test
    void malformedVerificationCodeIsInvalidInput() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().replace("012345", "12A345")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void committedMismatchResultBecomesPublicError() throws Exception {
        when(registrationService.register(any()))
                .thenReturn(RegisterUserResult.failure(ErrorCode.EMAIL_VERIFICATION_INVALID));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_INVALID"));
    }

    @Test
    void fifthMismatchResultBecomesPublicLimitError() throws Exception {
        when(registrationService.register(any()))
                .thenReturn(RegisterUserResult.failure(ErrorCode.EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED"));
    }

    @Test
    void businessPolicyErrorKeepsApprovedApplicationCode() throws Exception {
        when(registrationService.register(any()))
                .thenThrow(new BusinessException(ErrorCode.REQUIRED_CONSENT_MISSING));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().replace(
                                "\"personalInformationAgreed\": true",
                                "\"personalInformationAgreed\": false")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REQUIRED_CONSENT_MISSING"));
    }

    private String validRequest() {
        return """
                {
                  "email": "user@example.com",
                  "verificationCode": "012345",
                  "password": "safe-password",
                  "birthDate": "2000-01-01",
                  "termsAgreed": true,
                  "personalInformationAgreed": true,
                  "healthInformationAgreed": true
                }
                """;
    }
}
