package com.nyam.domain.user.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nyam.domain.user.service.EmailVerificationService;

/**
 * 이메일 인증 OpenAPI가 승인된 경로와 민감값 비노출 계약을 설명하는지 검증합니다.
 */
@WebMvcTest(controllers = EmailVerificationController.class, properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
})
@AutoConfigureMockMvc(addFilters = false)
@ImportAutoConfiguration(classes = {
        SpringDocConfiguration.class,
        SpringDocConfigProperties.class,
        SpringDocWebMvcConfiguration.class
})
class EmailVerificationOpenApiContractTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    EmailVerificationService emailVerificationService;

    /**
     * 발송·확인 경로, 한국어 정책 설명, 공개 상태와 민감 필드 속성을 확인합니다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void publishesApprovedEmailVerificationContractWithoutSensitiveExamples() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[0].name").value("이메일 인증"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/email-verifications'].post.summary")
                        .value("이메일 인증번호 발송"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/email-verifications/confirm'].post.summary")
                        .value("이메일 인증번호 확인"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/email-verifications'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/email-verifications'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/email-verifications'].post.responses['429']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/email-verifications'].post.responses['503']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/email-verifications/confirm'].post.responses['422']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/email-verifications/confirm'].post.responses['429']").exists())
                .andExpect(jsonPath("$.components.schemas.EmailVerificationConfirmRequest.required",
                        org.hamcrest.Matchers.hasItems("email", "verificationCode")))
                .andExpect(jsonPath("$.components.schemas.EmailVerificationConfirmRequest.properties.verificationCode.writeOnly")
                        .value(true))
                .andExpect(jsonPath("$.components.schemas.EmailVerificationConfirmRequest.properties.verificationCode.pattern")
                        .value("[0-9]{6}"))
                .andExpect(jsonPath("$.components.schemas.EmailVerificationConfirmRequest.properties.verificationCode.example")
                        .doesNotExist())
                .andExpect(jsonPath("$.components.schemas.EmailVerificationConfirmRequest.properties.verificationCode.default")
                        .doesNotExist())
                .andExpect(jsonPath("$.components.schemas.EmailVerificationConfirmResponse.properties.verificationProof.readOnly")
                        .value(true))
                .andExpect(jsonPath("$.components.schemas.EmailVerificationConfirmResponse.properties.verificationProof.example")
                        .doesNotExist())
                .andExpect(jsonPath("$.components.schemas.EmailVerificationConfirmResponse.properties.verificationProof.default")
                        .doesNotExist());
    }
}
