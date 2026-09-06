package com.nyam.global.security;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;

import com.nyam.deployment.smoke.PrivateArtifactFile;
import com.nyam.deployment.smoke.SmokeSeedManifest;
import com.nyam.domain.user.service.AccessTokenIssuer;

/**
 * 검증된 seed manifest의 사용자 A/B 중 하나에만 기존 15분 Access JWT를 발급하는 로컬 CLI입니다.
 */
public final class LocalSmokeAccessTokenApplication {

    private LocalSmokeAccessTokenApplication() {
    }

    /**
     * 비밀값을 인자·stdout·로그에 노출하지 않고 새 사용자 전용 파일에만 JWT를 기록합니다.
     *
     * @param args 사용하지 않으며 서명키·ID·토큰을 명령 인자로 받지 않습니다
     */
    public static void main(String[] args) {
        int status = run(System.getenv(), Clock.systemUTC());
        if (status != 0) {
            System.exit(status);
        }
    }

    static int run(Map<String, String> environment, Clock clock) {
        try {
            String target = required(environment, "NYAM_SMOKE_TARGET");
            SmokeSeedManifest.Users users = SmokeSeedManifest.read(
                    Path.of(required(environment, "NYAM_SMOKE_SEED_OUTPUT")), target);
            long userId = switch (required(environment, "NYAM_SMOKE_USER")) {
                case "A" -> users.userAId();
                case "B" -> users.userBId();
                default -> throw new IllegalArgumentException("NYAM_SMOKE_USER must be A or B");
            };

            SecurityConfiguration configuration = new SecurityConfiguration();
            var key = configuration.accessTokenSecretKey(required(environment, "NYAM_AUTH_ACCESS_SECRET"));
            var issuer = new AccessTokenIssuer(configuration.jwtEncoder(key));
            String token = issuer.issue(userId, clock.instant());
            PrivateArtifactFile.writeNew(
                    Path.of(required(environment, "NYAM_SMOKE_JWT_OUTPUT")), token);
            System.out.println("A short-lived access token was written to a private file.");
            return 0;
        } catch (Exception exception) {
            System.err.println("Smoke access token issuance failed.");
            return 1;
        }
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required smoke environment is missing");
        }
        return value;
    }
}
