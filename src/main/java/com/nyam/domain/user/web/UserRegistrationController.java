package com.nyam.domain.user.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nyam.domain.user.service.UserRegistrationService;
import com.nyam.global.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 검증된 요청을 회원가입 서비스에 위임하고 승인된 HTTP 응답 계약을 구성합니다.
 */
@RestController
@RequestMapping(path = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "회원가입", description = "이메일 인증을 완료한 사용자의 로컬 계정을 최종 생성합니다.")
public class UserRegistrationController {

    private final UserRegistrationService registrationService;

    /**
     * 회원가입 유스케이스를 수행할 서비스를 주입받습니다.
     *
     * @param registrationService 회원가입 검증 및 저장 서비스
     */
    public UserRegistrationController(UserRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    /**
     * 일회성 이메일 검증 증명을 소비하여 로컬 사용자 계정을 생성합니다.
     *
     * @param request 검증 증명, 비밀번호, 생년월일, 필수 동의를 담은 요청
     * @return 생성 완료 데이터와 {@code 201 Created} 상태를 담은 공통 응답
    */
    @PostMapping(path = "/signup", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "로컬 회원가입 완료",
            description = "이메일 인증 단계에서 발급된 유효한 일회성 증명을 소비하고, 사용자·로컬 자격 증명·필수 동의 정보를 하나의 트랜잭션으로 저장합니다. "
                    + "가입 성공 후 자동 로그인하거나 Access Token을 발급하지 않습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "회원가입 완료. 공통 응답 코드: `SIGNUP_COMPLETED`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청 JSON 형식이 잘못되었거나 필수 필드 및 기본 형식 검증에 실패했습니다. 공통 응답 코드: `INVALID_INPUT`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "인증된 이메일로 이미 가입된 계정이 존재합니다. 공통 응답 코드: `EMAIL_ALREADY_REGISTERED`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "422",
                    description = "요청 형식은 올바르지만 회원가입 비즈니스 규칙을 충족하지 못했습니다. 가능한 공통 응답 코드: "
                            + "`EMAIL_VERIFICATION_INVALID`, `UNDERAGE_NOT_ALLOWED`, `REQUIRED_CONSENT_MISSING`, "
                            + "`PASSWORD_POLICY_VIOLATION`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "예상하지 못한 서버 오류입니다. 내부 상세는 공개하지 않습니다. 공통 응답 코드: `INTERNAL_SERVER_ERROR`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "이메일 인증 증명, 비밀번호, 생년월일, 현재 버전의 필수 동의 세 항목",
                    required = true)
            @Valid @RequestBody SignupRequest request) {
        String displayEmail = registrationService.register(request.toCommand());
        ApiResponse<SignupResponse> response = ApiResponse.success(
                "SIGNUP_COMPLETED", "회원가입이 완료되었습니다.", new SignupResponse(displayEmail));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
