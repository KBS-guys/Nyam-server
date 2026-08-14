package com.nyam.domain.user.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nyam.domain.user.service.UserRegistrationService;

/**
 * 회원가입 실패 로그에 검증 증명과 비밀번호가 노출되지 않는지 검증합니다.
 */
@WebMvcTest(UserRegistrationController.class)
@ExtendWith(OutputCaptureExtension.class)
class RegistrationSecretLoggingTest {

    private static final String PROOF = "Z".repeat(43);
    private static final String PASSWORD = "sentinel-password-987";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserRegistrationService registrationService;

    /**
     * 예상하지 못한 서버 오류가 발생해도 요청 비밀값과 비밀번호 해시가 로그에 없는지 확인합니다.
     *
     * @param output 테스트 실행 중 수집된 애플리케이션 출력
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void requestSecretsAreAbsentFromApplicationLogsOnFailure(CapturedOutput output) throws Exception {
        when(registrationService.register(any())).thenThrow(new IllegalStateException("generic failure"));

        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "verificationProof": "%s",
                          "password": "%s",
                          "birthDate": "2000-01-01",
                          "consents": [
                            {"type": "TERMS", "version": "1.0"},
                            {"type": "PERSONAL_INFORMATION", "version": "1.0"},
                            {"type": "HEALTH_INFORMATION", "version": "1.0"}
                          ]
                        }
                        """.formatted(PROOF, PASSWORD)));

        assertThat(output.getAll()).doesNotContain(PROOF, PASSWORD, "{bcrypt}");
    }
}
