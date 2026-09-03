package com.nyam.global.config;

import java.io.IOException;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

/**
 * 로컬 ignored 설정 없이도 웹·배치 진입점에 공통 안전 기본값을 제공합니다.
 * 자동 구성과 로깅 초기화 전에 읽되 profile·환경 변수·명령행 설정을 덮어쓰지 않습니다.
 */
public class NyamDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor {

    /** 공통 리소스를 최저 우선순위로 추가하며, 산출물에서 누락되면 기동을 중단합니다. */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            environment.getPropertySources().addLast(new ResourcePropertySource(
                    "nyam-defaults", new ClassPathResource("nyam-defaults.properties")));
        } catch (IOException exception) {
            throw new IllegalStateException("Nyam common defaults are unavailable");
        }
    }
}
