package com.nyam.domain.food.batch;

import java.nio.file.Path;

import org.springframework.stereotype.Component;

/**
 * 현재 프로세스에서만 사용하는 식품 CSV 경로를 보관하며 Job Parameter 영속화를 막습니다.
 */
@Component
public class FoodImportInput {

    private volatile Path path;

    /**
     * 현재 수동 실행에서 사용할 입력 경로를 설정합니다.
     *
     * @param inputPath 사용자가 제공한 CSV 경로
     */
    public void configure(Path inputPath) {
        this.path = inputPath.toAbsolutePath().normalize();
    }

    /**
     * 현재 프로세스에 설정된 입력 경로를 반환합니다.
     *
     * @return 로그나 Job Parameter에 기록하지 않을 CSV 경로
     * @throws IllegalStateException 실행 경로가 설정되지 않은 경우
     */
    public Path requirePath() {
        Path current = path;
        if (current == null) {
            throw new IllegalStateException("Food import input is not configured");
        }
        return current;
    }
}
