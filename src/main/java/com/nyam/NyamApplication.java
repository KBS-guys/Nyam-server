package com.nyam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Nyamlog Spring Boot 애플리케이션의 실행 진입점입니다.
 */
@SpringBootApplication
public class NyamApplication {

	/**
	 * Spring 애플리케이션 컨텍스트를 생성하고 Nyamlog 서버를 시작합니다.
	 *
	 * @param args 애플리케이션 시작 시 전달된 명령행 인자
	 */
	public static void main(String[] args) {
		SpringApplication.run(NyamApplication.class, args);
	}

}
