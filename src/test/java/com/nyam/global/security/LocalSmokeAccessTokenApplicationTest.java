package com.nyam.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.nyam.deployment.smoke.PrivateArtifactFile;
import com.nyam.deployment.smoke.SmokeSeedManifest;

/**
 * 로컬 JWT 도구가 seed A/B만 기존 Access Token 계약으로 안전하게 발급하는지 검증합니다.
 */
@ExtendWith(OutputCaptureExtension.class)
class LocalSmokeAccessTokenApplicationTest {

    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");
    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);

    @TempDir
    Path tempDirectory;

    /** A manifest ID로 15분 JWT를 발급하고 비밀값·토큰을 출력하지 않습니다. */
    @Test
    void issuesExistingAccessTokenContractOnlyToSelectedSeedUser(CapturedOutput output) throws Exception {
        Path manifest = tempDirectory.resolve("seed.manifest").toAbsolutePath();
        Path tokenFile = tempDirectory.resolve("token.jwt").toAbsolutePath();
        SmokeSeedManifest.write(manifest, "local-recheck", new SmokeSeedManifest.Users(101L, 202L));
        Map<String, String> environment = environment(manifest, tokenFile, "A");
        environment.put("NYAM_SMOKE_USER_ID", "999");

        assertThat(LocalSmokeAccessTokenApplication.run(
                environment, Clock.fixed(NOW, ZoneOffset.UTC))).isZero();

        String token = PrivateArtifactFile.read(tokenFile);
        SecurityConfiguration configuration = new SecurityConfiguration();
        SecretKey key = configuration.accessTokenSecretKey(SECRET);
        var jwt = configuration.jwtDecoder(key, Clock.fixed(NOW, ZoneOffset.UTC)).decode(token);
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("nyamlog");
        assertThat(jwt.getAudience()).containsExactly("nyamlog-api");
        assertThat(jwt.getSubject()).isEqualTo("101");
        assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plusSeconds(900));
        assertThat(output.getAll()).doesNotContain(SECRET, token, "101", "202", "999");
    }

    /** B 이외 별칭, 다른 target, 기존 출력 파일은 모두 토큰 없이 fail-closed합니다. */
    @Test
    void rejectsUnapprovedAliasTargetAndOutputOverwrite() throws Exception {
        Path manifest = tempDirectory.resolve("seed.manifest").toAbsolutePath();
        SmokeSeedManifest.write(manifest, "local-recheck", new SmokeSeedManifest.Users(101L, 202L));

        Path aliasOutput = tempDirectory.resolve("alias.jwt").toAbsolutePath();
        assertThat(LocalSmokeAccessTokenApplication.run(
                environment(manifest, aliasOutput, "C"), fixedClock())).isOne();
        assertThat(aliasOutput).doesNotExist();

        Path targetOutput = tempDirectory.resolve("target.jwt").toAbsolutePath();
        Map<String, String> wrongTarget = environment(manifest, targetOutput, "B");
        wrongTarget.put("NYAM_SMOKE_TARGET", "another-target");
        assertThat(LocalSmokeAccessTokenApplication.run(wrongTarget, fixedClock())).isOne();
        assertThat(targetOutput).doesNotExist();

        Path existingOutput = tempDirectory.resolve("existing.jwt").toAbsolutePath();
        PrivateArtifactFile.writeNew(existingOutput, "existing");
        assertThat(LocalSmokeAccessTokenApplication.run(
                environment(manifest, existingOutput, "B"), fixedClock())).isOne();
        assertThat(PrivateArtifactFile.read(existingOutput)).isEqualTo("existing");
    }

    private Map<String, String> environment(Path manifest, Path output, String user) {
        Map<String, String> environment = new HashMap<>();
        environment.put("NYAM_SMOKE_TARGET", "local-recheck");
        environment.put("NYAM_SMOKE_SEED_OUTPUT", manifest.toString());
        environment.put("NYAM_SMOKE_USER", user);
        environment.put("NYAM_AUTH_ACCESS_SECRET", SECRET);
        environment.put("NYAM_SMOKE_JWT_OUTPUT", output.toString());
        return environment;
    }

    private Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }
}
