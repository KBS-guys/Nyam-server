package com.nyam.global.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 모든 Nyamlog API가 사용하는 공통 응답 봉투입니다.
 *
 * @param <T> 성공 응답에 포함되는 데이터의 타입
 */
@Getter
@Schema(description = "Nyamlog API가 성공과 오류에 공통으로 사용하는 응답 구조")
public class ApiResponse<T> {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "true",
            description = "요청 처리 성공 여부입니다. 성공 응답은 true, 오류 응답은 false입니다.")
    private final boolean success;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "SIGNUP_COMPLETED",
            description = "클라이언트가 처리 결과를 구분할 수 있는 공개 애플리케이션 코드입니다.")
    private final String code;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "회원가입이 완료되었습니다.",
            description = "사용자에게 표시할 수 있는 안전한 결과 메시지입니다. 내부 예외나 데이터베이스 상세는 포함하지 않습니다.")
    private final String message;
    @Schema(description = "성공 시 기능별 응답 데이터입니다. 오류 응답에서는 null입니다.")
    private final T data;

    /**
     * 공통 응답의 성공 여부와 공개 가능한 응답 정보를 구성합니다.
     *
     * @param success 요청 처리 성공 여부
     * @param code 클라이언트가 분기할 수 있는 공개 응답 코드
     * @param message 사용자에게 전달할 공개 메시지
     * @param data 성공 시 반환할 데이터이며 오류 응답에서는 {@code null}
     */
    private ApiResponse(boolean success, String code, String message, T data) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 기본 성공 코드와 메시지를 사용하는 성공 응답을 생성합니다.
     *
     * @param data 응답 데이터
     * @param <T> 응답 데이터 타입
     * @return 기본 성공 정보가 담긴 공통 응답
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "S000", "성공", data);
    }

    /**
     * 기능별 성공 코드와 메시지를 사용하는 성공 응답을 생성합니다.
     *
     * @param code 기능별 공개 성공 코드
     * @param message 사용자에게 전달할 공개 성공 메시지
     * @param data 응답 데이터
     * @param <T> 응답 데이터 타입
     * @return 지정한 성공 정보가 담긴 공통 응답
     */
    public static <T> ApiResponse<T> success(String code, String message, T data) {
        return new ApiResponse<>(true, code, message, data);
    }

    /**
     * 내부 상세나 데이터를 노출하지 않는 오류 응답을 생성합니다.
     *
     * @param code 클라이언트가 분기할 수 있는 공개 오류 코드
     * @param message 사용자에게 전달할 공개 오류 메시지
     * @param <T> 응답 데이터 타입
     * @return 데이터가 {@code null}인 공통 오류 응답
     */
    public static  <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }
}
