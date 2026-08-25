package com.nyam.domain.user.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.nyam.domain.user.service.AccessTokenIssuer;
import com.nyam.domain.user.service.CurrentUserResult;
import com.nyam.domain.user.service.IssuedAuthentication;
import com.nyam.domain.user.service.LocalLoginService;
import com.nyam.global.exception.BusinessException;
import com.nyam.global.exception.ErrorCode;
import com.nyam.global.security.SecurityConfiguration;
import com.nyam.global.security.SecurityErrorResponder;

/**
 * 실제 보안 필터를 포함해 로컬 로그인 API의 본문, 쿠키, CSRF와 Bearer 계약을 검증합니다.
 */
@WebMvcTest(controllers = LocalLoginController.class, properties =
        "NYAM_AUTH_ACCESS_SECRET=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=")
@Import({
        SecurityConfiguration.class,
        SecurityErrorResponder.class,
        AccessTokenIssuer.class,
        LocalLoginControllerTest.FixedClockConfiguration.class
})
class LocalLoginControllerTest {

    private static final String REFRESH_TOKEN = "A".repeat(43);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AccessTokenIssuer accessTokenIssuer;

    @MockitoBean
    LocalLoginService loginService;

    /**
     * 로그인 성공이 Access Token은 본문에, Refresh Token은 승인된 HttpOnly 쿠키에만 반환하는지 확인합니다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void loginReturnsAccessBodyAndSecureRefreshCookie() throws Exception {
        when(loginService.login("User@Example.COM", "safe-password"))
                .thenReturn(new IssuedAuthentication("access-token", REFRESH_TOKEN, 2_592_000));

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"User@Example.COM","password":"safe-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value("LOGIN_COMPLETED"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(900))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .contains(LocalLoginController.REFRESH_COOKIE_NAME + "=" + REFRESH_TOKEN)
                .contains("Path=/api/v1/auth", "Max-Age=2592000", "Secure", "HttpOnly", "SameSite=Strict")
                .doesNotContain("Domain=");
    }

    /**
     * 필수 입력이 없으면 서비스 호출 전 공통 입력 오류로 거절하는지 확인합니다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void loginRejectsInvalidBodyBeforeCredentialCheck() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verify(loginService, never()).login(any(), any());
    }

    /**
     * 문자 수 제한 안에서도 BCrypt의 UTF-8 72바이트를 넘는 비밀번호를 서비스 호출 전에 거부하는지 확인합니다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void loginRejectsPasswordAboveBcryptByteBoundary() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"%s"}
                                """.formatted("가".repeat(25))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verify(loginService, never()).login(any(), any());
    }

    /**
     * Refresh Token보다 CSRF 표지를 먼저 검증하고 실패 응답에 쿠키를 쓰지 않는지 확인합니다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void refreshPrioritizesCsrfAndWritesNoCookieOnFailure() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie(
                                LocalLoginController.REFRESH_COOKIE_NAME, REFRESH_TOKEN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_REQUEST_REJECTED"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        verify(loginService, never()).refresh(any());
    }

    /**
     * 교체되었거나 잘못된 Refresh Token의 401 응답이 승자 쿠키를 지우거나 덮지 않는지 확인합니다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void refreshLoserReturnsUnauthorizedWithoutSetCookie() throws Exception {
        when(loginService.refresh(REFRESH_TOKEN))
                .thenThrow(new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header(LocalLoginController.CSRF_HEADER_NAME, "1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer stale-token")
                        .cookie(new jakarta.servlet.http.Cookie(
                                LocalLoginController.REFRESH_COOKIE_NAME, REFRESH_TOKEN)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    /**
     * 로그아웃은 토큰 존재 여부와 관계없이 같은 삭제 쿠키와 성공 계약을 반환하는지 확인합니다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void logoutIsIdempotentAndReturnsMatchingDeletionCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/logout")
                        .header(LocalLoginController.CSRF_HEADER_NAME, "1"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value("LOGOUT_COMPLETED"))
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .contains(LocalLoginController.REFRESH_COOKIE_NAME + "=")
                .contains("Path=/api/v1/auth", "Max-Age=0", "Secure", "HttpOnly", "SameSite=Strict")
                .doesNotContain("Domain=");
        verify(loginService).logout(null);
    }

    /**
     * 보호 API가 검증된 JWT subject만 서비스 식별자로 사용하고 표기 이메일을 반환하는지 확인합니다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void meUsesValidatedBearerSubjectFromSecurityContext() throws Exception {
        when(loginService.currentUser(7L)).thenReturn(new CurrentUserResult("User@Example.COM"));
        String accessToken = accessTokenIssuer.issue(7L, Instant.parse("2026-08-25T00:00:00Z"));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATED_USER_RETRIEVED"))
                .andExpect(jsonPath("$.data.email").value("User@Example.COM"));

        verify(loginService).currentUser(7L);
    }

    /**
     * 보호 API의 누락된 Bearer가 안전한 공통 401과 표준 challenge 헤더로 끝나는지 확인합니다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void meWithoutBearerReturnsSafeChallenge() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("E003"));

        verify(loginService, never()).currentUser(anyLong());
    }

    /**
     * Access Token 발급과 검증이 같은 재현 가능한 UTC 시각을 사용하도록 구성합니다.
     */
    @TestConfiguration
    static class FixedClockConfiguration {

        /**
         * JWT 테스트용 고정 UTC 시계를 제공합니다.
         *
         * @return 2026-08-25 자정에 고정된 시계
         */
        @Bean
        Clock fixedAuthenticationClock() {
            return Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);
        }
    }
}
