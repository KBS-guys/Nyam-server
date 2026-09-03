package com.nyam.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;

import com.nyam.domain.user.service.AccessTokenIssuer;

/**
 * Access Token 비밀키와 HS256 클레임 검증 경계를 독립적으로 확인합니다.
 */
class SecurityConfigurationTest {

    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");
    private static final String TEST_SECRET = Base64.getEncoder().encodeToString(new byte[32]);

    private final SecurityConfiguration configuration = new SecurityConfiguration();

    /**
     * 비밀키가 없거나 Base64·최소 길이 규칙을 위반하면 시작 구성이 실패하는지 확인합니다.
     */
    @Test
    void rejectsMissingMalformedAndShortSigningSecrets() {
        assertThatThrownBy(() -> configuration.accessTokenSecretKey(""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> configuration.accessTokenSecretKey("not-base64!"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> configuration.accessTokenSecretKey(
                Base64.getEncoder().encodeToString(new byte[31])))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 발급된 토큰이 승인된 issuer, audience, 양의 subject와 15분 만료만 갖는지 확인합니다.
     */
    @Test
    void issuedTokenPassesTheExactApprovedClaimsContract() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        SecretKey key = configuration.accessTokenSecretKey(TEST_SECRET);
        JwtEncoder encoder = configuration.jwtEncoder(key);
        JwtDecoder decoder = configuration.jwtDecoder(key, clock);

        var jwt = decoder.decode(new AccessTokenIssuer(encoder).issue(7L, NOW));

        assertThat(jwt.getClaimAsString("iss")).isEqualTo("nyamlog");
        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat(jwt.getAudience()).containsExactly("nyamlog-api");
        assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plusSeconds(900));
    }

    /**
     * 만료 누락·경과, 잘못된 audience와 양수가 아닌 subject가 모두 안전한 JWT 검증 실패인지 확인합니다.
     */
    @Test
    void rejectsExpiredAudienceAndSubjectViolations() {
        SecretKey key = configuration.accessTokenSecretKey(TEST_SECRET);
        JwtEncoder encoder = configuration.jwtEncoder(key);
        JwtDecoder currentDecoder = configuration.jwtDecoder(key, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> currentDecoder.decode(tokenWithoutExpiration(encoder)))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> currentDecoder.decode(token(encoder, "7", List.of("another-api"), NOW.plusSeconds(900))))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> currentDecoder.decode(token(encoder, "0", List.of("nyamlog-api"), NOW.plusSeconds(900))))
                .isInstanceOf(JwtException.class);

        String token = new AccessTokenIssuer(encoder).issue(7L, NOW);
        JwtDecoder expiredDecoder = configuration.jwtDecoder(
                key, Clock.fixed(NOW.plus(Duration.ofSeconds(901)), ZoneOffset.UTC));
        assertThatThrownBy(() -> expiredDecoder.decode(token)).isInstanceOf(JwtException.class);
    }

    /**
     * 지정한 공개 클레임으로 HS256 테스트 토큰을 생성합니다.
     *
     * @param encoder 테스트 비밀키를 사용하는 인코더
     * @param subject 내부 사용자 주체 문자열
     * @param audience API 대상 목록
     * @param expiresAt 만료 시각
     * @return 서명된 테스트 JWT 원문
     */
    private String token(JwtEncoder encoder, String subject, List<String> audience, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("nyamlog")
                .subject(subject)
                .audience(audience)
                .expiresAt(expiresAt)
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    /**
     * 만료 클레임이 없는 서명된 HS256 테스트 토큰을 생성합니다.
     *
     * @param encoder 테스트 비밀키를 사용하는 인코더
     * @return 만료 클레임이 없는 서명된 JWT 원문
     */
    private String tokenWithoutExpiration(JwtEncoder encoder) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("nyamlog")
                .subject("7")
                .audience(List.of("nyamlog-api"))
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
