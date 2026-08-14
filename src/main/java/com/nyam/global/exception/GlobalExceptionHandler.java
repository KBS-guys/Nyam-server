package com.nyam.global.exception;

import com.nyam.global.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 애플리케이션 예외를 공통 API 오류 계약으로 변환하고 내부 상세 노출을 차단합니다.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 존재하지 않는 정적 또는 API 리소스 요청을 빈 404 응답으로 변환합니다.
     *
     * @param ignored Spring MVC가 전달한 리소스 부재 예외
     * @return 본문이 없는 404 응답
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResource(NoResourceFoundException ignored) {
        return ResponseEntity.notFound().build();
    }

    /**
     * DTO 검증 실패와 읽을 수 없는 요청 본문을 동일한 입력 오류로 변환합니다.
     *
     * @param ignored 검증 또는 역직렬화 과정에서 발생한 예외
     * @return {@code INVALID_INPUT} 공통 오류 응답
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<?>> handleInvalidInput(Exception ignored) {
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT.getCode(), ErrorCode.INVALID_INPUT.getMessage()));
    }

    /**
     * 도메인에서 의도적으로 발생시킨 비즈니스 예외를 승인된 공개 오류로 변환합니다.
     *
     * @param ex 공개 오류 코드를 보유한 비즈니스 예외
     * @return 오류 코드에 대응하는 HTTP 상태와 공통 오류 응답
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<?>> handleBusinessException(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
    }

    /**
     * 예상하지 못한 예외를 내부 상세 없이 일반 서버 오류로 변환합니다.
     *
     * @param ignored 외부에 상세를 공개하지 않을 예외
     * @return {@code INTERNAL_SERVER_ERROR} 공통 오류 응답
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleException(Exception ignored) {
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                        ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }
}
