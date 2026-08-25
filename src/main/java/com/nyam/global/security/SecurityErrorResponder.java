package com.nyam.global.security;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyam.global.common.ApiResponse;
import com.nyam.global.exception.ErrorCode;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Spring Security 필터 실패를 애플리케이션의 안전한 공통 오류 응답으로 기록합니다.
 */
@Component
public class SecurityErrorResponder {

    private final ObjectMapper objectMapper;

    /**
     * 공통 오류 본문을 JSON으로 직렬화할 매퍼를 주입받습니다.
     *
     * @param objectMapper Spring MVC와 동일한 JSON 매퍼
     */
    public SecurityErrorResponder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 내부 예외 상세 없이 공개 오류를 쓰고 필요하면 Bearer 표준 헤더를 유지합니다.
     *
     * @param response 필터가 작성할 HTTP 응답
     * @param errorCode 공개할 상태·코드·메시지
     * @param bearerChallenge Bearer 인증 실패 헤더 포함 여부
     * @throws IOException 응답 스트림에 JSON을 쓰지 못한 경우
     */
    public void write(HttpServletResponse response, ErrorCode errorCode, boolean bearerChallenge)
            throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (bearerChallenge) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        objectMapper.writeValue(response.getOutputStream(),
                ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
    }
}
