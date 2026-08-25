package com.nyam.domain.user.web;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nyam.domain.user.service.AccessTokenIssuer;
import com.nyam.domain.user.service.CurrentUserResult;
import com.nyam.domain.user.service.IssuedAuthentication;
import com.nyam.domain.user.service.LocalLoginService;
import com.nyam.global.common.ApiResponse;
import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 로그인·재발급·로그아웃·현재 사용자 조회를 승인된 HTTP와 쿠키 계약으로 연결합니다.
 */
@RestController
@RequestMapping(path = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "로컬 로그인", description = "Access Token과 HttpOnly Refresh Token을 사용하는 로컬 로그인 유지 흐름입니다.")
public class LocalLoginController {

    /** Refresh Token이 저장되는 HttpOnly 쿠키 이름입니다. */
    public static final String REFRESH_COOKIE_NAME = "__Secure-nyam-refresh";
    /** 쿠키 기반 인증 요청에 필요한 공개 CSRF 표지 헤더 이름입니다. */
    public static final String CSRF_HEADER_NAME = "X-Nyam-CSRF";

    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private final LocalLoginService loginService;

    /**
     * 로컬 로그인 유스케이스 서비스를 주입받습니다.
     *
     * @param loginService 자격 증명과 토큰 생명주기를 처리하는 서비스
     */
    public LocalLoginController(LocalLoginService loginService) {
        this.loginService = loginService;
    }

    /**
     * 이메일과 비밀번호를 검증하여 Access Token 본문과 Refresh Token 쿠키를 반환합니다.
     *
     * @param request 로그인 이메일과 비밀번호
     * @return 커밋된 Refresh Token 상태에 대응하는 로그인 성공 응답
     */
    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "로컬 로그인", description = "이메일과 비밀번호를 동일한 실패 계약으로 검증하고, "
            + "Access Token은 본문에, Refresh Token은 HttpOnly 쿠키에만 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "로그인 완료. 공통 응답 코드: `LOGIN_COMPLETED`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "요청 JSON 또는 필수 입력 형식 오류. 공통 응답 코드: `INVALID_INPUT`"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "이메일·자격 증명·비밀번호 불일치. 공통 응답 코드: `LOGIN_FAILED`")
    })
    public ResponseEntity<ApiResponse<AccessTokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        IssuedAuthentication issued = loginService.login(request.email(), request.password());
        return tokenResponse("LOGIN_COMPLETED", "로그인이 완료되었습니다.", issued);
    }

    /**
     * HttpOnly 쿠키의 현재 Refresh Token을 원자적으로 회전하고 새 Access Token을 반환합니다.
     *
     * @param csrfMarker 승인된 고정 CSRF 표지
     * @param refreshToken 브라우저가 자동 전송한 Refresh Token 쿠키
     * @return 단일 승자 회전에 성공한 재발급 응답
     * @throws BusinessException CSRF 표지 또는 Refresh Token 계약을 충족하지 못한 경우
     */
    @PostMapping("/refresh")
    @Operation(summary = "Access Token 재발급", description = "HttpOnly Refresh Token 쿠키를 조건부 갱신으로 회전하고 "
            + "새 Access Token을 반환합니다. 실패 응답에는 Set-Cookie가 없습니다.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false,
            description = "요청 본문을 사용하지 않습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "재발급 완료. 공통 응답 코드: `ACCESS_TOKEN_REISSUED`"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Refresh Token이 없거나 만료·교체·폐기되었습니다. 공통 응답 코드: `REFRESH_TOKEN_INVALID`"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "필수 CSRF 표지가 없습니다. 공통 응답 코드: `CSRF_REQUEST_REJECTED`")
    })
    public ResponseEntity<ApiResponse<AccessTokenResponse>> refresh(
            @Parameter(name = CSRF_HEADER_NAME, in = ParameterIn.HEADER, required = true,
                    description = "쿠키 기반 요청에 필요한 공개 고정 표지이며 값은 1입니다.")
            @RequestHeader(name = CSRF_HEADER_NAME, required = false) String csrfMarker,
            @Parameter(hidden = true)
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken) {
        requireCsrfMarker(csrfMarker);
        IssuedAuthentication issued = loginService.refresh(refreshToken);
        return tokenResponse("ACCESS_TOKEN_REISSUED", "Access Token이 재발급되었습니다.", issued);
    }

    /**
     * 제출된 Refresh Token 서버 상태를 가능한 범위에서 삭제하고 동일한 쿠키 삭제 응답을 반환합니다.
     *
     * @param csrfMarker 승인된 고정 CSRF 표지
     * @param refreshToken 선택적으로 전달된 Refresh Token 쿠키
     * @return 토큰 존재 여부를 노출하지 않는 멱등 로그아웃 응답
     * @throws BusinessException CSRF 표지가 일치하지 않는 경우
     */
    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "일치하는 Refresh Token 서버 상태를 삭제하고 항상 동일한 쿠키 삭제 응답을 반환합니다. "
            + "기존 Access Token은 짧은 만료까지 유효할 수 있으며 클라이언트가 메모리에서 제거해야 합니다.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false,
            description = "요청 본문을 사용하지 않습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "멱등 로그아웃 완료. 공통 응답 코드: `LOGOUT_COMPLETED`"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "필수 CSRF 표지가 없습니다. 공통 응답 코드: `CSRF_REQUEST_REJECTED`")
    })
    public ResponseEntity<ApiResponse<Void>> logout(
            @Parameter(name = CSRF_HEADER_NAME, in = ParameterIn.HEADER, required = true,
                    description = "쿠키 기반 요청에 필요한 공개 고정 표지이며 값은 1입니다.")
            @RequestHeader(name = CSRF_HEADER_NAME, required = false) String csrfMarker,
            @Parameter(hidden = true)
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken) {
        requireCsrfMarker(csrfMarker);
        loginService.logout(refreshToken);
        ApiResponse<Void> body = ApiResponse.success("LOGOUT_COMPLETED", "로그아웃이 완료되었습니다.", null);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, deletionCookie().toString())
                .body(body);
    }

    /**
     * Bearer 인증 주체에서 사용자 식별자를 얻어 현재 사용자의 표기 이메일을 반환합니다.
     *
     * @param jwt Spring Security가 검증한 Access Token 주체
     * @return 클라이언트 식별자를 사용하지 않는 현재 사용자 응답
     */
    @GetMapping("/me")
    @Operation(summary = "현재 인증 사용자 조회", description = "요청 파라미터가 아닌 SecurityContext의 JWT subject로 사용자를 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "현재 사용자 조회 완료. 공통 응답 코드: `AUTHENTICATED_USER_RETRIEVED`"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Access Token이 없거나 변조·만료·형식 오류입니다. 공통 응답 코드: `E003`")
    })
    public ResponseEntity<ApiResponse<CurrentUserResponse>> me(@AuthenticationPrincipal Jwt jwt) {
        CurrentUserResult currentUser = loginService.currentUser(Long.parseLong(jwt.getSubject()));
        ApiResponse<CurrentUserResponse> body = ApiResponse.success(
                "AUTHENTICATED_USER_RETRIEVED",
                "현재 사용자를 조회했습니다.",
                new CurrentUserResponse(currentUser.displayEmail()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    /**
     * 토큰 발급 결과를 no-store 본문과 승인된 Refresh Token 쿠키로 변환합니다.
     *
     * @param code 기능별 성공 코드
     * @param message 공개 성공 메시지
     * @param issued 커밋을 마친 민감한 토큰 발급 결과
     * @return Access Token 본문과 Refresh Token Set-Cookie 응답
     */
    private ResponseEntity<ApiResponse<AccessTokenResponse>> tokenResponse(
            String code, String message, IssuedAuthentication issued) {
        AccessTokenResponse data = new AccessTokenResponse(
                issued.accessToken(), "Bearer", AccessTokenIssuer.ACCESS_TOKEN_SECONDS);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, refreshCookie(issued).toString())
                .body(ApiResponse.success(code, message, data));
    }

    /**
     * 발급 결과의 원문 Refresh Token을 승인된 보안 속성의 쿠키로 구성합니다.
     *
     * @param issued Refresh Token과 남은 수명을 가진 발급 결과
     * @return 브라우저에 전달할 Refresh Token 응답 쿠키
     */
    private ResponseCookie refreshCookie(IssuedAuthentication issued) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, issued.refreshToken())
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ofSeconds(issued.refreshMaxAgeSeconds()))
                .build();
    }

    /**
     * 기존 Refresh Token 쿠키와 완전히 같은 범위에 Max-Age 0 삭제 쿠키를 구성합니다.
     *
     * @return 브라우저의 Refresh Token을 제거할 응답 쿠키
     */
    private ResponseCookie deletionCookie() {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
    }

    /**
     * 쿠키 기반 인증 엔드포인트에 고정 CSRF 표지가 정확히 제출되었는지 확인합니다.
     *
     * @param csrfMarker 요청 헤더에서 읽은 공개 표지
     * @throws BusinessException 표지가 없거나 값이 1이 아닌 경우
     */
    private void requireCsrfMarker(String csrfMarker) {
        if (!"1".equals(csrfMarker)) {
            throw new BusinessException(ErrorCode.CSRF_REQUEST_REJECTED);
        }
    }
}
