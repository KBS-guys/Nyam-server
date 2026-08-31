package com.nyam.domain.dailysummary.web;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nyam.domain.dailysummary.service.DailySummaryService;
import com.nyam.global.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 인증 사용자의 요청 날짜별 주요 영양 snapshot 합계 HTTP 계약을 제공합니다.
 */
@RestController
@RequestMapping(path = "/api/v1/daily-summaries", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "일별 영양 요약", description = "저장된 meal item snapshot의 날짜별 주요 영양 합계를 조회합니다.")
@SecurityRequirement(name = "bearerAuth")
public class DailySummaryController {

    private final DailySummaryService dailySummaryService;

    public DailySummaryController(DailySummaryService dailySummaryService) {
        this.dailySummaryService = dailySummaryService;
    }

    /**
     * JWT subject와 요청 date가 일치하는 snapshot만 집계해 불완전성을 함께 반환합니다.
     */
    @GetMapping
    @Operation(summary = "일별 영양 요약 조회", description = "요청 date의 자기 meal item snapshot만 집계합니다. "
            + "현재 food 변경은 과거 결과에 영향을 주지 않으며, 누락값이 있으면 부분합 대신 value null과 complete false를 반환합니다. "
            + "mealItemCount로 빈 날짜와 실제 합계가 0인 날짜를 구분합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "일별 영양 요약 조회 완료. 공통 응답 코드: `DAILY_SUMMARY_RETRIEVED`"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "날짜 누락·형식·MySQL DATE 범위 오류. 공통 응답 코드: `INVALID_INPUT`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "유효한 Bearer Access Token이 없습니다. 공통 응답 코드: `E003`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500",
                    description = "예상하지 못한 서버 오류입니다. 공통 응답 코드: `INTERNAL_SERVER_ERROR`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ApiResponse<DailySummaryResponse> get(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(required = true, example = "2026-08-29",
                    description = "조회할 식사 기준 날짜(1000-01-01부터 9999-12-31, 미래 날짜 허용)")
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        DailySummaryResponse data = DailySummaryResponse.from(
                dailySummaryService.get(Long.parseLong(jwt.getSubject()), date));
        return ApiResponse.success("DAILY_SUMMARY_RETRIEVED", "일별 영양 요약을 조회했습니다.", data);
    }
}
