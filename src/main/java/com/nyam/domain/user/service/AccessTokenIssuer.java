package com.nyam.domain.user.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

/**
 * 승인된 HS256 클레임 계약으로 짧은 Access Token을 발급합니다.
 */
@Component
public class AccessTokenIssuer {

    /** Access Token의 공개 수명(초)입니다. */
    public static final long ACCESS_TOKEN_SECONDS = 900;

    private static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofSeconds(ACCESS_TOKEN_SECONDS);

    private final JwtEncoder jwtEncoder;

    /**
     * JWT 인코더를 주입받습니다.
     *
     * @param jwtEncoder HS256 서명을 생성할 인코더
     */
    public AccessTokenIssuer(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    /**
     * 내부 사용자 식별자를 주체로 갖는 Access Token을 발급합니다.
     *
     * @param userId 인증된 양의 내부 사용자 식별자
     * @param issuedAt 요청 시작 시 한 번 캡처한 발급 기준 시각
     * @return 서명된 Access Token 원문
     */
    public String issue(long userId, Instant issuedAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("nyamlog")
                .subject(Long.toString(userId))
                .audience(List.of("nyamlog-api"))
                .expiresAt(issuedAt.plus(ACCESS_TOKEN_LIFETIME))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
