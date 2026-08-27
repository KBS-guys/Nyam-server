package com.nyam;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 실제 MySQL 컨테이너에서 Flyway 적용과 Hibernate 스키마 검증 기반을 확인합니다.
 */
@SpringBootTest(properties = {
        "NYAM_EMAIL_VERIFICATION_HMAC_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "NYAM_AUTH_ACCESS_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE="
})
@Testcontainers(disabledWithoutDocker = true)
class ProjectFoundationMySqlIntegrationTest {

	private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.4.5");

	@Container
	@ServiceConnection
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MYSQL_IMAGE);

	@Autowired
	DataSource dataSource;

	@Autowired
	Environment environment;

	/**
	 * 빈 MySQL에 애플리케이션이 시작되고 승인된 마이그레이션과 테이블 수를 갖는지 검증합니다.
	 *
	 * @throws SQLException 데이터베이스 연결 또는 검증 쿼리 실행에 실패한 경우
	 */
	@Test
	void startsAgainstFreshMySqlWithFlywayAndSchemaValidation() throws SQLException {
		assertThat(MYSQL.isRunning())
				.as("MySQL container must be running")
				.isTrue();

		try (Connection connection = dataSource.getConnection()) {
			assertContainerConnection(connection);

			assertThat(queryCount(connection, """
					SELECT COUNT(*)
					FROM flyway_schema_history
					WHERE version IS NOT NULL
					  AND success = TRUE
					"""))
					.as("Successful versioned application migration count")
					.isEqualTo(4);

			assertThat(queryCount(connection, """
					SELECT COUNT(*)
					FROM information_schema.tables
					WHERE table_schema = DATABASE()
					  AND table_type = 'BASE TABLE'
					  AND table_name <> 'flyway_schema_history'
					"""))
					.as("Application base table count")
					.isEqualTo(5);
		}

		assertThat("validate".equals(environment.getProperty("spring.jpa.hibernate.ddl-auto")))
				.as("Hibernate schema handling must remain validation-only")
				.isTrue();
	}

	/**
	 * 애플리케이션 데이터 소스가 로컬 설정이 아닌 Testcontainers MySQL을 가리키는지 확인합니다.
	 *
	 * @param connection 애플리케이션 데이터 소스에서 얻은 연결
	 * @throws SQLException 연결 메타데이터를 읽지 못한 경우
	 */
	private void assertContainerConnection(Connection connection) throws SQLException {
		URI connectionUri = URI.create(connection.getMetaData().getURL().substring("jdbc:".length()));

		assertThat(MYSQL.getDatabaseName().equals(connection.getCatalog()))
				.as("DataSource must use the container database")
				.isTrue();
		assertThat(MYSQL.getHost().equals(connectionUri.getHost()))
				.as("DataSource must use the container host")
				.isTrue();
		assertThat(MYSQL.getMappedPort(MySQLContainer.MYSQL_PORT) == connectionUri.getPort())
				.as("DataSource must use the container mapped port")
				.isTrue();
	}

	/**
	 * 한 행의 개수를 반환하는 검증 쿼리를 실행합니다.
	 *
	 * @param connection 쿼리를 실행할 MySQL 연결
	 * @param sql 단일 개수 행을 반환해야 하는 SQL
	 * @return 조회된 첫 번째 열의 개수 값
	 * @throws SQLException 쿼리 실행 또는 결과 조회에 실패한 경우
	 */
	private long queryCount(Connection connection, String sql) throws SQLException {
		try (Statement statement = connection.createStatement();
				ResultSet resultSet = statement.executeQuery(sql)) {
			assertThat(resultSet.next())
					.as("Count query must return one row")
					.isTrue();
			return resultSet.getLong(1);
		}
	}
}
