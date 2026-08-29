package com.nyam.domain.food.batch;

import java.time.Clock;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Profile;

import com.nyam.domain.food.model.Food;

/**
 * 웹·인증·사용자 서비스를 제외하고 식품 Batch와 Food 스키마 검증만 시작하는 전용 구성입니다.
 */
@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@ComponentScan(basePackageClasses = FoodImportRunner.class)
@EntityScan(basePackageClasses = Food.class)
@Profile("food-import")
public class FoodImportApplicationConfiguration {

    /**
     * 식품 적재 시각에 사용할 UTC 시스템 시계를 제공합니다.
     *
     * @return UTC 시스템 시계
     */
    @Bean
    public Clock foodImportClock() {
        return Clock.systemUTC();
    }
}
