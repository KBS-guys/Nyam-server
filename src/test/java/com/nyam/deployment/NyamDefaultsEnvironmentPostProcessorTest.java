package com.nyam.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import com.nyam.global.config.NyamDefaultsEnvironmentPostProcessor;

/** 로컬 파일·실제 환경 변수 없이 공통 기본값과 외부 설정 우선순위를 검증합니다. */
class NyamDefaultsEnvironmentPostProcessorTest {

    @Test
    void defaultsOffWithoutLocalConfiguration() {
        var environment = isolatedEnvironment(Map.of());
        assertThat(environment.getProperty("springdoc.api-docs.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(environment.getProperty("spring.flyway.enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("spring.sql.init.mode")).isEqualTo("never");
    }

    @Test
    void allowsExplicitOpenApiOptInWithoutPersistingAuthorization() {
        var environment = isolatedEnvironment(Map.of("NYAM_OPENAPI_ENABLED", "true"));
        assertThat(environment.getProperty("springdoc.api-docs.enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("springdoc.swagger-ui.persist-authorization", Boolean.class)).isFalse();
        assertThat(environment.getProperty("springdoc.swagger-ui.supported-submit-methods")).isNull();
    }

    @Test
    void keepsExplicitConfigurationAboveFallbacks() {
        var environment = isolatedEnvironment(Map.of(
                "NYAM_OPENAPI_ENABLED", "true", "springdoc.api-docs.enabled", "false"));
        assertThat(environment.getProperty("springdoc.api-docs.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class)).isTrue();
    }

    private StandardEnvironment isolatedEnvironment(Map<String, Object> overrides) {
        var environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("explicit-test-settings", overrides));
        new NyamDefaultsEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());
        return environment;
    }
}
