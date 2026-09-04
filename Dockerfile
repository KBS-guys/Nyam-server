FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /workspace
COPY gradlew build.gradle settings.gradle ./
COPY gradle/wrapper/ gradle/wrapper/
COPY src/main/java/ src/main/java/
COPY src/main/resources/db/migration/ src/main/resources/db/migration/
COPY src/main/resources/application-deployment.yml src/main/resources/food-batch-defaults.properties src/main/resources/nyam-defaults.properties src/main/resources/
COPY src/main/resources/META-INF/spring.factories src/main/resources/META-INF/
RUN sed -i 's/\r$//' gradlew && sh gradlew --no-daemon bootJar

FROM eclipse-temurin:17-jre-jammy
RUN groupadd --gid 1000 nyam \
    && useradd --uid 1000 --gid 1000 --no-create-home --shell /usr/sbin/nologin nyam \
    && command -v keytool
WORKDIR /app
COPY --from=builder /workspace/build/libs/nyam.jar /app/nyam.jar
COPY docker/entrypoint.sh /app/entrypoint.sh
RUN sed -i 's/\r$//' /app/entrypoint.sh && chmod 0555 /app/entrypoint.sh
ENV SPRING_PROFILES_ACTIVE=deployment
ENV MYSQL_TRUSTSTORE_URL=file:/tmp/nyam-mysql/aiven-truststore.p12
USER 1000:1000
EXPOSE 8080
ENTRYPOINT ["/app/entrypoint.sh"]
