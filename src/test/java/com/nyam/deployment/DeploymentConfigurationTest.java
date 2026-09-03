package com.nyam.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import com.nyam.domain.user.config.EmailVerificationConfiguration;
import com.zaxxer.hikari.HikariConfig;

/**
 * 네트워크 연결 없이 배포 profile의 TLS·pool·HTTP 설정과 SMTP 없는 기동 기본값을 검증합니다.
 */
class DeploymentConfigurationTest {

    @Test
    void retainsLocalMailpitDefaults() {
        new ApplicationContextRunner().withUserConfiguration(EmailVerificationConfiguration.class)
                .run(context -> {
                    var sender = (JavaMailSenderImpl) context.getBean(JavaMailSender.class);
                    assertThat(sender.getHost()).isEqualTo("localhost");
                    assertThat(sender.getPort()).isEqualTo(1025);
                    assertThat(sender.getUsername()).isNull();
                    assertThat(sender.getPassword()).isNull();
                    assertThat(sender.getJavaMailProperties())
                            .containsEntry("mail.smtp.auth", "false")
                            .containsEntry("mail.smtp.starttls.enable", "false")
                            .containsEntry("mail.smtp.starttls.required", "false")
                            .containsEntry("mail.smtp.timeout", "5000");
                });
    }

    /** TLS secret의 실패 출력도 막기 위해 값 자체가 아닌 일치 여부만 검사합니다. */
    @Test
    void bindsDeploymentTlsHttpAndSmtpFreeContracts() {
        Map<String, Object> variables = deploymentVariables();
        deploymentContext(variables).run(context -> {
            assertThat(context).hasNotFailed();
            var env = context.getEnvironment();
            var pool = Binder.get(env).bind("spring.datasource.hikari", Bindable.of(HikariConfig.class)).get();
            assertThat(pool.getMaximumPoolSize()).isEqualTo(5);
            assertThat(pool.getMinimumIdle()).isZero();
            assertThat(pool.getConnectionTimeout()).isEqualTo(10000);
            var jdbc = pool.getDataSourceProperties();
            assertThat(jdbc.size()).isEqualTo(5);
            assertThat(jdbc.getProperty("sslMode")).isEqualTo("VERIFY_IDENTITY");
            assertThat(jdbc.getProperty("fallbackToSystemTrustStore")).isEqualTo("false");
            assertThat(jdbc.getProperty("trustCertificateKeyStoreType")).isEqualTo("PKCS12");
            assertThat(jdbc.getProperty("trustCertificateKeyStoreUrl"))
                    .isEqualTo("file:/tmp/nyam-mysql/aiven-truststore.p12");
            assertThat(variables.get("MYSQL_TRUSTSTORE_PASSWORD")
                    .equals(jdbc.getProperty("trustCertificateKeyStorePassword"))).isTrue();

            var sender = (JavaMailSenderImpl) context.getBean(JavaMailSender.class);
            assertThat(sender.getHost()).isEqualTo("localhost");
            assertThat(sender.getPort()).isEqualTo(1025);
            assertThat(sender.getUsername()).isNull();
            assertThat(sender.getPassword()).isNull();
            assertThat(sender.getJavaMailProperties())
                    .containsEntry("mail.smtp.auth", "false")
                    .containsEntry("mail.smtp.starttls.enable", "false")
                    .containsEntry("mail.smtp.starttls.required", "false")
                    .containsEntry("mail.smtp.connectiontimeout", "5000")
                    .containsEntry("mail.smtp.timeout", "5000")
                    .containsEntry("mail.smtp.writetimeout", "5000");
            assertThat(env.getProperty("nyam.mail.from")).isNull();
            assertThat(env.getProperty("nyam.deployment.auth-endpoints-enabled", Boolean.class)).isFalse();
            assertThat(env.getProperty("server.port")).isEqualTo("19090");
            assertThat(env.getProperty("server.address")).isEqualTo("0.0.0.0");
            assertThat(env.getProperty("server.shutdown")).isEqualTo("graceful");
            assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
            assertThat(env.getProperty("spring.flyway.enabled", Boolean.class)).isTrue();
            assertThat(env.getProperty("spring.batch.job.enabled", Boolean.class)).isFalse();
            assertThat(env.getProperty("management.endpoint.health.group.render.include")).isEqualTo("ping");
            assertThat(env.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
            assertThat(env.getProperty("management.endpoint.health.show-components")).isEqualTo("never");
            assertThat(env.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
            assertThat(env.getProperty("management.endpoints.web.discovery.enabled", Boolean.class)).isFalse();
            assertThat(env.getProperty("springdoc.api-docs.enabled", Boolean.class)).isTrue();
            assertThat(env.getProperty("springdoc.swagger-ui.enabled", Boolean.class)).isTrue();
            assertThat(env.getProperty("springdoc.swagger-ui.persist-authorization", Boolean.class)).isFalse();
            assertThat(env.getProperty("springdoc.swagger-ui.supported-submit-methods")).isNull();
            assertThat(env.getProperty("springdoc.paths-to-match")).isEqualTo("/api/v1/**");
        });
    }

    @Test
    void deploymentStillRequiresExplicitOpenApiOptInAndDefaultsPortTo8080() {
        var variables = deploymentVariables();
        variables.remove("NYAM_OPENAPI_ENABLED");
        variables.remove("PORT");
        deploymentContext(variables).run(context -> {
            assertThat(context.getEnvironment().getProperty("springdoc.api-docs.enabled", Boolean.class)).isFalse();
            assertThat(context.getEnvironment().getProperty("springdoc.swagger-ui.enabled", Boolean.class)).isFalse();
            assertThat(context.getEnvironment().getProperty("server.port")).isEqualTo("8080");
        });
    }

    private ApplicationContextRunner deploymentContext(Map<String, Object> variables) {
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    var sources = context.getEnvironment().getPropertySources();
                    // 실제 운영 환경 변수와 로컬 ignored 설정이 이 격리 테스트에 들어오지 않는다.
                    sources.remove("systemEnvironment");
                    sources.remove("systemProperties");
                    sources.addFirst(new MapPropertySource("deployment-test", variables));
                    try {
                        new YamlPropertySourceLoader().load("deployment",
                                new ClassPathResource("application-deployment.yml")).forEach(sources::addLast);
                    } catch (java.io.IOException exception) {
                        throw new IllegalStateException("Deployment configuration resource is missing");
                    }
                })
                .withUserConfiguration(EmailVerificationConfiguration.class);
    }

    private Map<String, Object> deploymentVariables() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("MYSQL_URL", "jdbc:mysql://database.example.invalid/nyam");
        variables.put("MYSQL_USERNAME", UUID.randomUUID().toString());
        variables.put("MYSQL_PASSWORD", UUID.randomUUID().toString());
        variables.put("MYSQL_TRUSTSTORE_URL", "file:/tmp/nyam-mysql/aiven-truststore.p12");
        variables.put("MYSQL_TRUSTSTORE_PASSWORD", UUID.randomUUID().toString());
        variables.put("NYAM_OPENAPI_ENABLED", "true");
        variables.put("PORT", "19090");
        return variables;
    }
}
