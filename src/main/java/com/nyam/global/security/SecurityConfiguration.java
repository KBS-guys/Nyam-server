package com.nyam.global.security;

import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nyam.global.exception.ErrorCode;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

/**
 * 무상태 Bearer JWT 인증과 공개 인증 경로, 안전한 필터 오류 계약을 구성합니다.
 */
@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "로그인 또는 재발급 응답 본문의 Access Token을 사용하는 Bearer 인증")
public class SecurityConfiguration {

    private static final OAuth2Error INVALID_AUDIENCE =
            new OAuth2Error("invalid_token", "Required audience is missing", null);
    private static final OAuth2Error INVALID_EXPIRATION =
            new OAuth2Error("invalid_token", "Expiration is required", null);
    private static final OAuth2Error INVALID_SUBJECT =
            new OAuth2Error("invalid_token", "Subject must be a positive BIGINT", null);

    /**
     * 저장소 밖에서 공급된 Base64 서명키를 HS256 비밀키로 검증하고 변환합니다.
     *
     * @param encodedSecret Base64로 인코딩된 Access Token 서명 비밀값
     * @return 최소 32바이트인 HS256 비밀키
     * @throws IllegalStateException 비밀값이 없거나 Base64 또는 길이 규칙을 위반한 경우
     */
    @Bean
    SecretKey accessTokenSecretKey(
            @Value("${NYAM_AUTH_ACCESS_SECRET:}") String encodedSecret) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encodedSecret);
            if (decoded.length < 32) {
                throw new IllegalStateException("NYAM_AUTH_ACCESS_SECRET must decode to at least 32 bytes");
            }
            return new SecretKeySpec(decoded, "HmacSHA256");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("NYAM_AUTH_ACCESS_SECRET must be valid Base64", exception);
        }
    }

    /**
     * HS256 Access Token을 생성할 Nimbus 인코더를 구성합니다.
     *
     * @param secretKey 검증된 HS256 비밀키
     * @return Access Token 발급용 JWT 인코더
     */
    @Bean
    JwtEncoder jwtEncoder(SecretKey secretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    }

    /**
     * 서명·만료·issuer·audience·양의 BIGINT subject를 엄격히 검증하는 디코더를 구성합니다.
     *
     * @param secretKey 검증된 HS256 비밀키
     * @param clock 인증 전체에서 사용하는 UTC 시계
     * @return Resource Server가 사용할 JWT 디코더
     */
    @Bean
    JwtDecoder jwtDecoder(SecretKey secretKey, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        OAuth2TokenValidator<Jwt> validators = new DelegatingOAuth2TokenValidator<>(
                SecurityConfiguration::validateExpiration,
                timestamps,
                new JwtIssuerValidator("nyamlog"),
                SecurityConfiguration::validateAudience,
                SecurityConfiguration::validateSubject);
        decoder.setJwtValidator(validators);
        return decoder;
    }

    /**
     * 로그인·재발급·로그아웃에서 오래되거나 잘못된 Bearer 헤더를 무시하는 해결기를 구성합니다.
     *
     * @return 인증 복구 경로를 제외한 요청에서만 Bearer 값을 읽는 해결기
     */
    @Bean
    BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> {
            String path = request.getRequestURI();
            if (path.equals("/api/v1/auth/login")
                    || path.equals("/api/v1/auth/refresh")
                    || path.equals("/api/v1/auth/logout")) {
                return null;
            }
            return delegate.resolve(request);
        };
    }

    /**
     * 공개 인증 API와 보호 API의 무상태 Spring Security 경계를 구성합니다.
     *
     * @param http Spring Security HTTP 구성기
     * @param bearerTokenResolver 인증 복구 경로를 제외하는 Bearer 해결기
     * @param errorResponder 필터 실패 공통 JSON 작성기
     * @return 애플리케이션 보안 필터 체인
     * @throws Exception 필터 체인을 구성하지 못한 경우
     */
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            BearerTokenResolver bearerTokenResolver,
            SecurityErrorResponder errorResponder) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/signup",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/email-verifications",
                                "/api/v1/auth/email-verifications/confirm")
                        .permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                errorResponder.write(response, ErrorCode.UNAUTHORIZED, true))
                        .accessDeniedHandler((request, response, exception) ->
                                errorResponder.write(response, ErrorCode.FORBIDDEN, false)))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(bearerTokenResolver)
                        .authenticationEntryPoint((request, response, exception) ->
                                errorResponder.write(response, ErrorCode.UNAUTHORIZED, true))
                        .jwt(Customizer.withDefaults()));
        return http.build();
    }

    /**
     * JWT audience가 Nyamlog API를 정확히 포함하는지 확인합니다.
     *
     * @param jwt 검증할 Access Token
     * @return audience가 유효하면 성공, 아니면 표준 토큰 실패
     */
    private static OAuth2TokenValidatorResult validateAudience(Jwt jwt) {
        return jwt.getAudience().contains("nyamlog-api")
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
    }

    /**
     * JWT에 필수 만료 시각이 존재하는지 확인합니다.
     *
     * @param jwt 검증할 Access Token
     * @return 만료 시각이 존재하면 성공, 아니면 표준 토큰 실패
     */
    private static OAuth2TokenValidatorResult validateExpiration(Jwt jwt) {
        return jwt.getExpiresAt() != null
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INVALID_EXPIRATION);
    }

    /**
     * JWT subject가 내부 BIGINT 범위의 양의 정수인지 확인합니다.
     *
     * @param jwt 검증할 Access Token
     * @return subject가 유효하면 성공, 아니면 표준 토큰 실패
     */
    private static OAuth2TokenValidatorResult validateSubject(Jwt jwt) {
        try {
            long subject = Long.parseLong(jwt.getSubject());
            return subject > 0
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(INVALID_SUBJECT);
        } catch (RuntimeException exception) {
            return OAuth2TokenValidatorResult.failure(INVALID_SUBJECT);
        }
    }
}
