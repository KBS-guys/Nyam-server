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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nyam.domain.user.service.UserRegistrationService;

/**
 * 회원가입 OpenAPI의 구조와 민감 필드 비노출 계약을 검증합니다.
 */
@WebMvcTest(controllers = UserRegistrationController.class, properties = {
        "NYAM_AUTH_ACCESS_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
})
@AutoConfigureMockMvc(addFilters = false)
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

    @Test
    void publishesDirectVerificationSignupContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/signup'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/signup'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/signup'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/signup'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/signup'].post.responses['422']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/signup'].post.responses['429']").exists())
                .andExpect(jsonPath("$.components.schemas.SignupRequest.required",
                        org.hamcrest.Matchers.hasItems(
                                "email", "verificationCode", "password", "birthDate",
                                "termsAgreed", "personalInformationAgreed", "healthInformationAgreed")))
                .andExpect(jsonPath("$.components.schemas.SignupRequest.properties.verificationCode.writeOnly")
                        .value(true))
                .andExpect(jsonPath("$.components.schemas.SignupRequest.properties.password.writeOnly")
                        .value(true))
                .andExpect(jsonPath("$.components.schemas.SignupRequest.properties.verificationCode.example")
                        .doesNotExist())
                .andExpect(jsonPath("$.components.schemas.SignupRequest.properties.verificationCode.default")
                        .doesNotExist())
                .andExpect(jsonPath("$.components.schemas.SignupRequest.properties.password.example")
                        .doesNotExist())
                .andExpect(jsonPath("$.components.schemas.SignupRequest.properties.verificationProof")
                        .doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ConsentRequest").doesNotExist());
    }
}
