package com.nyam.domain.user.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nyam.domain.user.service.UserRegistrationService;

import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;

/**
 * 활성화된 OpenAPI 문서가 구현된 회원가입 계약만 안전하게 공개하는지 검증합니다.
 */
@WebMvcTest(controllers = UserRegistrationController.class, properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
})
@ImportAutoConfiguration(classes = {
        SpringDocConfiguration.class,
        SpringDocConfigProperties.class,
        SpringDocWebMvcConfiguration.class
})
class OpenApiContractTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserRegistrationService registrationService;

    /**
     * 회원가입 경로, 한국어 설명, 상태 코드, 요청 필수값, 민감 필드의 쓰기 전용 속성을 확인합니다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void publishesOnlyTheImplementedSignupContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[0].name").value("회원가입"))
                .andExpect(jsonPath("$.tags[0].description")
                        .value(org.hamcrest.Matchers.containsString("로컬 계정")))
                .andExpect(jsonPath("$.paths['/api/v1/auth/signup'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/signup'].post.summary")
                        .value("로컬 회원가입 완료"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/signup'].post.description")
                        .value(org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("일회성 증명"),
                                org.hamcrest.Matchers.containsString("자동 로그인"))))
                .andExpect(jsonPath("$.paths['/test']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/email-verifications']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/email-verifications/confirm']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/signup'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/signup'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/signup'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/signup'].post.responses['422'].description")
                        .value(org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("EMAIL_VERIFICATION_INVALID"),
                                org.hamcrest.Matchers.containsString("UNDERAGE_NOT_ALLOWED"),
                                org.hamcrest.Matchers.containsString("REQUIRED_CONSENT_MISSING"),
                                org.hamcrest.Matchers.containsString("PASSWORD_POLICY_VIOLATION"),
                                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PASSWORD_COMPROMISED")))))
                .andExpect(jsonPath("$.paths['/api/v1/auth/signup'].post.responses['500']").exists())
                .andExpect(jsonPath("$.components.schemas.SignupRequest.required",
                        org.hamcrest.Matchers.hasItems("verificationProof", "password", "birthDate", "consents")))
                .andExpect(jsonPath("$.components.schemas.SignupRequest.properties.password.writeOnly").value(true))
                .andExpect(jsonPath("$.components.schemas.SignupRequest.properties.verificationProof.writeOnly").value(true))
                .andExpect(jsonPath("$.components.schemas.SignupRequest.properties.password.description")
                        .value(org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("8자 이상"),
                                org.hamcrest.Matchers.containsString("72바이트"))))
                .andExpect(jsonPath("$.components.schemas.SignupRequest.properties.password.minLength")
                        .value(8))
                .andExpect(jsonPath("$.components.schemas.SignupRequest.properties.verificationProof.description")
                        .value(org.hamcrest.Matchers.containsString("43자")))
                .andExpect(jsonPath("$.components.schemas.SignupRequest.properties.verificationProof.minLength")
                        .value(43))
                .andExpect(jsonPath("$.components.schemas.SignupRequest.properties.verificationProof.maxLength")
                        .value(43))
                .andExpect(jsonPath("$.components.schemas.SignupRequest.properties.verificationProof.pattern")
                        .value("[A-Za-z0-9_-]{43}"))
                .andExpect(jsonPath("$.components.schemas.SignupRequest.properties.birthDate.description")
                        .value(org.hamcrest.Matchers.containsString("만 19세 이상")))
                .andExpect(jsonPath("$.components.schemas.SignupRequest.properties.consents.description")
                        .value(org.hamcrest.Matchers.containsString("정확히 세 항목")))
                .andExpect(jsonPath("$.components.schemas.ConsentRequest.properties.type.description")
                        .value(org.hamcrest.Matchers.containsString("HEALTH_INFORMATION")))
                .andExpect(jsonPath("$.components.schemas.ConsentRequest.properties.type.enum",
                        org.hamcrest.Matchers.hasItems(
                                "TERMS", "PERSONAL_INFORMATION", "HEALTH_INFORMATION")))
                .andExpect(jsonPath("$.components.schemas.ConsentRequest.properties.version.description")
                        .value(org.hamcrest.Matchers.containsString("정책 버전")))
                .andExpect(jsonPath("$.components.schemas.SignupRequest.properties.password.example").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.SignupRequest.properties.verificationProof.example").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ApiResponse.properties.success.description")
                        .value(org.hamcrest.Matchers.containsString("성공 여부")))
                .andExpect(jsonPath("$.components.schemas.ApiResponse.properties.code.description")
                        .value(org.hamcrest.Matchers.containsString("애플리케이션 코드")))
                .andExpect(jsonPath("$.components.schemas.ApiResponse.properties.message.description")
                        .value(org.hamcrest.Matchers.containsString("안전한 결과 메시지")))
                .andExpect(jsonPath("$.components.schemas.ApiResponse.properties.data.description")
                        .value(org.hamcrest.Matchers.containsString("오류 응답에서는 null")));
    }
}
