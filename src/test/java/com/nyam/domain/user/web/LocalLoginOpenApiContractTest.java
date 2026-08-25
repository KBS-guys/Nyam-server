package com.nyam.domain.user.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nyam.domain.user.service.LocalLoginService;
import com.nyam.global.security.SecurityConfiguration;
import com.nyam.global.security.SecurityErrorResponder;

/**
 * 로컬 로그인 OpenAPI가 Bearer, 쿠키, CSRF와 민감값 비노출 계약을 설명하는지 검증합니다.
 */
@WebMvcTest(controllers = LocalLoginController.class, properties = {
        "NYAM_AUTH_ACCESS_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
})
@AutoConfigureMockMvc(addFilters = false)
@Import(SecurityConfiguration.class)
@ImportAutoConfiguration(classes = {
        SpringDocConfiguration.class,
        SpringDocConfigProperties.class,
        SpringDocWebMvcConfiguration.class
})
class LocalLoginOpenApiContractTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    LocalLoginService loginService;

    @MockitoBean
    SecurityErrorResponder securityErrorResponder;

    @MockitoBean
    Clock clock;

    /**
     * 네 인증 경로와 Bearer scheme을 공개하되 토큰·비밀번호 예시를 만들지 않는지 확인합니다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void publishesPersistentLoginContractWithoutSensitiveExamples() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.summary").value("로컬 로그인"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post.summary").value("Access Token 재발급"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.summary").value("로그아웃"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/me'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post.parameters[0].name")
                        .value(LocalLoginController.CSRF_HEADER_NAME))
                .andExpect(jsonPath("$.components.schemas.LoginRequest.properties.password.writeOnly").value(true))
                .andExpect(jsonPath("$.components.schemas.LoginRequest.properties.password.example").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.AccessTokenResponse.properties.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.AccessTokenResponse.properties.accessToken.example").doesNotExist());
    }
}
