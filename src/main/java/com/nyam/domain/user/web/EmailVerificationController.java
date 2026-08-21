package com.nyam.domain.user.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nyam.domain.user.service.EmailVerificationConfirmationResult;
import com.nyam.domain.user.service.EmailVerificationSendResult;
import com.nyam.domain.user.service.EmailVerificationService;
import com.nyam.global.common.ApiResponse;
import com.nyam.global.exception.BusinessException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 이메일 인증번호 발송과 확인 요청을 서비스에 위임하고 공개 응답을 구성합니다.
 */
@RestController
@RequestMapping(path = "/api/v1/auth/email-verifications", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "이메일 인증", description = "Mailpit으로 인증번호를 받고 확인하여 회원가입용 일회성 증명을 발급합니다.")
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    /**
     * 이메일 인증 유스케이스 서비스를 주입받습니다.
     *
     * @param emailVerificationService 인증번호 발송·확인 트랜잭션 서비스
     */
    public EmailVerificationController(EmailVerificationService emailVerificationService) {
        this.emailVerificationService = emailVerificationService;
    }

    /**
     * 가입되지 않은 이메일로 현재 세션의 인증번호를 발송합니다.
     *
     * @param request 인증번호를 받을 이메일
     * @return 발송 완료 데이터와 {@code 200 OK} 공통 응답
     * @throws BusinessException 입력, 가입 중복, 발송 제한 또는 메일 전달 규칙을 충족하지 못한 경우
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "이메일 인증번호 발송",
            description = "가입되지 않은 ASCII 이메일로 5분간 유효한 6자리 인증번호를 Mailpit에 발송합니다. "
                    + "재전송은 60초 뒤 가능하며 한 세션에서 최대 3회입니다. 이미 가입된 이메일에는 메일을 보내지 않습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "인증번호 발송 완료. 공통 응답 코드: `EMAIL_VERIFICATION_CODE_SENT`",
                    useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "이메일 필드 또는 ASCII·길이·기본 형식 검증에 실패했습니다. 공통 응답 코드: `INVALID_INPUT`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "이미 가입된 이메일이어서 메일을 보내지 않았습니다. 공통 응답 코드: `EMAIL_ALREADY_REGISTERED`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "429",
                    description = "60초 대기, 최대 재전송 또는 오입력 잠금으로 발송할 수 없습니다. 공통 응답 코드: `EMAIL_VERIFICATION_SEND_LIMITED`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503",
                    description = "Mailpit 전달 실패로 데이터베이스 변경도 롤백되었습니다. 공통 응답 코드: `EMAIL_DELIVERY_UNAVAILABLE`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "예상하지 못한 서버 오류이며 내부 상세는 공개하지 않습니다. 공통 응답 코드: `INTERNAL_SERVER_ERROR`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<EmailVerificationSendResponse>> sendCode(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "인증번호를 받을 가입 전 이메일",
                    required = true)
            @Valid @RequestBody EmailVerificationSendRequest request) {
        EmailVerificationSendResult result = emailVerificationService.sendCode(request.email());
        EmailVerificationSendResponse data = new EmailVerificationSendResponse(
                result.email(), result.codeExpiresAt(), result.resendAvailableAt());
        return ResponseEntity.ok(ApiResponse.success(
                "EMAIL_VERIFICATION_CODE_SENT", "인증번호를 발송했습니다.", data));
    }

    /**
     * 현재 인증번호를 확인하고 기존 signup 요청에 사용할 일회성 증명을 발급합니다.
     *
     * @param request 이메일과 6자리 인증번호
     * @return 일회성 증명과 {@code 200 OK} 공통 응답
     * @throws BusinessException 인증 상태가 없거나 만료·불일치·시도 초과인 경우
     */
    @PostMapping(path = "/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "이메일 인증번호 확인",
            description = "현재 인증번호를 확인하고 성공 시 기존 signup에 제출할 15분짜리 일회성 verificationProof를 발급합니다. "
                    + "잘못된 번호는 5회까지 세며, 성공한 번호와 발급된 proof는 각각 한 번만 사용할 수 있습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "인증번호 확인과 proof 발급 완료. 공통 응답 코드: `EMAIL_VERIFICATION_CONFIRMED`",
                    useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "이메일 또는 인증번호 기본 형식 검증에 실패했습니다. 공통 응답 코드: `INVALID_INPUT`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "422",
                    description = "인증 상태가 없거나 만료되었거나 현재 번호와 일치하지 않습니다. 공통 응답 코드: `EMAIL_VERIFICATION_INVALID`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "429",
                    description = "다섯 번째 불일치를 기록했거나 이미 확인 횟수를 초과했습니다. 공통 응답 코드: `EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "예상하지 못한 서버 오류이며 내부 상세는 공개하지 않습니다. 공통 응답 코드: `INTERNAL_SERVER_ERROR`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<EmailVerificationConfirmResponse>> confirmCode(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "발송 요청 이메일과 메일로 받은 현재 6자리 인증번호",
                    required = true)
            @Valid @RequestBody EmailVerificationConfirmRequest request) {
        EmailVerificationConfirmationResult result = emailVerificationService.confirmCode(
                request.email(), request.verificationCode());
        if (result.errorCode() != null) {
            throw new BusinessException(result.errorCode());
        }
        EmailVerificationConfirmResponse data = new EmailVerificationConfirmResponse(
                result.verificationProof(), result.proofExpiresAt());
        return ResponseEntity.ok(ApiResponse.success(
                "EMAIL_VERIFICATION_CONFIRMED", "이메일 인증이 완료되었습니다.", data));
    }
}
