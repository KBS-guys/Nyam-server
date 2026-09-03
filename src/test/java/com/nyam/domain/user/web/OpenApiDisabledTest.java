package com.nyam.domain.user.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springdoc.webmvc.ui.SwaggerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

import com.nyam.domain.user.service.UserRegistrationService;

/**
 * OpenAPI 문서와 Swagger UI가 기본 운영 설정에서 노출되지 않는지 검증합니다.
 */
@WebMvcTest(controllers = UserRegistrationController.class, properties = {
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"
})
@AutoConfigureMockMvc(addFilters = false)
@ImportAutoConfiguration(classes = {
        SpringDocConfiguration.class, SpringDocConfigProperties.class,
        SwaggerUiConfigProperties.class, SwaggerUiOAuthProperties.class,
        SpringDocWebMvcConfiguration.class, SwaggerConfig.class
})
class OpenApiDisabledTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserRegistrationService registrationService;

    /**
     * OpenAPI JSON과 Swagger UI 경로가 비활성 상태에서 404를 반환하는지 확인합니다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void doesNotExposeDocsOrUiByDefault() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
    }

    /**
     * 공통 설정이 문서를 기본 비활성화하되 명시적 활성화 시 요청 실행을 별도로 막지 않는지 확인합니다.
     *
     * @throws Exception 공통 설정 리소스를 읽지 못한 경우
     */
    @Test
    void commonConfigurationKeepsDocsDefaultOffAndAllowsExplicitTryItOut() throws Exception {
        var propertySource = new ResourcePropertySource("nyam-defaults",
                new ClassPathResource("nyam-defaults.properties"));

        assertThat(propertySource.getProperty("springdoc.api-docs.enabled"))
                .isEqualTo("${NYAM_OPENAPI_ENABLED:false}");
        assertThat(propertySource.getProperty("springdoc.swagger-ui.enabled"))
                .isEqualTo("${NYAM_OPENAPI_ENABLED:false}");
        assertThat(propertySource.getProperty("springdoc.swagger-ui.supported-submit-methods"))
                .isNull();
        assertThat(propertySource.getProperty("springdoc.paths-to-match"))
                .isEqualTo("/api/v1/**");
        assertThat(propertySource.getProperty("springdoc.swagger-ui.persist-authorization"))
                .isEqualTo("false");
    }
}
