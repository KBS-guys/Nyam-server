package com.nyam.domain.user.web;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nyam.domain.user.service.EmailVerificationService;

/**
 * 이메일 인증 OpenAPI가 발송 계약만 공개하는지 검증합니다.
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

    @Test
    void publishesSendOnlyWithoutProofContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/email-verifications'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/email-verifications'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/email-verifications'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/email-verifications'].post.responses['429']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/email-verifications'].post.responses['503']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/email-verifications/confirm']").doesNotExist())
                .andExpect(content().string(not(containsString("verificationProof"))));
    }
}
