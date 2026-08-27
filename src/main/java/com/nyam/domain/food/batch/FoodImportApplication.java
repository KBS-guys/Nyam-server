package com.nyam.domain.food.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 식품 적재 Job만 수동 실행하는 non-web 애플리케이션 진입점입니다.
 */
public final class FoodImportApplication {

    /**
     * 인스턴스 생성을 막는 실행 진입점 생성자입니다.
     */
    private FoodImportApplication() {
    }

    /**
     * {@code food-import} 프로필로 웹 서버 없이 Spring Batch Job을 실행합니다.
     *
     * @param args 입력 경로, release date와 checksum 실행 옵션
     */
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(FoodImportApplicationConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setAdditionalProfiles("food-import");
        try (ConfigurableApplicationContext context = application.run(args)) {
            int exitCode = SpringApplication.exit(context);
            if (exitCode != 0) {
                System.exit(exitCode);
            }
        } catch (RuntimeException exception) {
            System.exit(1);
        }
    }
}
