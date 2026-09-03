package com.nyam.deployment.smoke;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;

/**
 * 승인된 대상에 TLS JDBC로 연결해 합성 사용자 A/B seed를 명시적으로 실행하는 로컬 CLI입니다.
 */
public final class SmokeUserSeedApplication {

    private SmokeUserSeedApplication() {
    }

    /**
     * 환경 변수에서만 연결 정보를 읽고 민감한 값이나 endpoint를 출력하지 않습니다.
     *
     * @param args 사용하지 않으며 비밀값을 명령 인자로 받지 않습니다
     */
    public static void main(String[] args) {
        int status = run(System.getenv(), Instant.now());
        if (status != 0) {
            System.exit(status);
        }
    }

    static int run(Map<String, String> environment, Instant now) {
        try {
            String target = required(environment, "NYAM_SMOKE_TARGET");
            Path output = Path.of(required(environment, "NYAM_SMOKE_SEED_OUTPUT"));
            Properties connectionProperties = strictTlsProperties(environment);
            try (Connection connection = DriverManager.getConnection(
                    required(environment, "MYSQL_URL"), connectionProperties)) {
                String product = connection.getMetaData().getDatabaseProductName();
                if (!product.toLowerCase(java.util.Locale.ROOT).contains("mysql")) {
                    throw new IllegalStateException("Smoke seed requires MySQL");
                }
                SmokeSeedManifest.Users users = new SmokeUserSeeder().seed(connection, now);
                SmokeSeedManifest.write(output, target, users);
            }
            System.out.println("Smoke users verified and a private manifest was written.");
            return 0;
        } catch (Exception exception) {
            System.err.println("Smoke user seeding failed without changing an existing fixture.");
            return 1;
        }
    }

    private static Properties strictTlsProperties(Map<String, String> environment) {
        Properties properties = new Properties();
        properties.setProperty("user", required(environment, "MYSQL_USERNAME"));
        properties.setProperty("password", required(environment, "MYSQL_PASSWORD"));
        properties.setProperty("sslMode", "VERIFY_IDENTITY");
        properties.setProperty("fallbackToSystemTrustStore", "false");
        properties.setProperty("trustCertificateKeyStoreType", "PKCS12");
        properties.setProperty("trustCertificateKeyStoreUrl", required(environment, "MYSQL_TRUSTSTORE_URL"));
        properties.setProperty("trustCertificateKeyStorePassword",
                required(environment, "MYSQL_TRUSTSTORE_PASSWORD"));
        return properties;
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required smoke environment is missing");
        }
        return value;
    }
}
