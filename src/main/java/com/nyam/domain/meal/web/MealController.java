package com.nyam.domain.meal.web;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nyam.domain.meal.service.MealService;
import com.nyam.global.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 인증 사용자의 식사 생성·날짜별 목록·삭제 HTTP 계약을 제공합니다.
 */
@RestController
@RequestMapping(path = "/api/v1/meals", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "식사", description = "인증 사용자의 날짜별 식사와 기록 시점 영양 snapshot을 관리합니다.")
@SecurityRequirement(name = "bearerAuth")
public class MealController {

    private final MealService mealService;

    public MealController(MealService mealService) {
        this.mealService = mealService;
    }

    /**
     * JWT subject를 소유자로 사용해 한 식사와 모든 item snapshot을 원자적으로 생성합니다.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "식사 기록 생성", description = "요청은 식사 날짜, food 식별자와 섭취량만 받습니다. "
            + "식품명·단위·영양 snapshot은 서버가 현재 food에서 계산하며 원천 null은 0으로 바꾸지 않습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "식사 생성 완료. 공통 응답 코드: `MEAL_CREATED`"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "날짜·항목 수·중복 food·섭취량 계약 위반. 공통 응답 코드: `INVALID_INPUT`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "유효한 Bearer Access Token이 없습니다. 공통 응답 코드: `E003`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "하나 이상의 food가 존재하지 않습니다. 공통 응답 코드: `FOOD_NOT_FOUND`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500",
                    description = "예상하지 못한 서버 오류입니다. 공통 응답 코드: `INTERNAL_SERVER_ERROR`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<MealResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateMealRequest request) {
        MealResponse data = MealResponse.from(mealService.create(ownerId(jwt), request.toCommand()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("MEAL_CREATED", "식사가 기록되었습니다.", data));
    }

    /**
     * 요청 날짜와 JWT 소유자가 모두 일치하는 식사 snapshot 목록을 반환합니다.
     */
    @GetMapping
    @Operation(summary = "날짜별 식사 목록 조회", description = "서버 현재 날짜가 아닌 요청 date를 기준으로 자기 식사만 조회합니다. "
            + "응답은 현재 food가 아니라 저장된 meal item snapshot을 사용합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "목록 조회 완료. 공통 응답 코드: `MEALS_RETRIEVED`"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "날짜 형식 또는 범위가 잘못됐습니다. 공통 응답 코드: `INVALID_INPUT`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "유효한 Bearer Access Token이 없습니다. 공통 응답 코드: `E003`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500",
                    description = "예상하지 못한 서버 오류입니다. 공통 응답 코드: `INTERNAL_SERVER_ERROR`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ApiResponse<List<MealResponse>> list(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(required = true, example = "2026-08-29", description = "조회할 식사 기준 날짜")
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<MealResponse> data = mealService.list(ownerId(jwt), date).stream()
                .map(MealResponse::from)
                .toList();
        return ApiResponse.success("MEALS_RETRIEVED", "식사 목록을 조회했습니다.", data);
    }

    /**
     * JWT 소유자의 식사만 삭제하고 다른 사용자 식사와 없는 식사를 같은 오류로 처리합니다.
     */
    @DeleteMapping("/{mealId}")
    @Operation(summary = "식사 기록 삭제", description = "현재 인증 사용자가 소유한 식사와 그 item만 삭제합니다. "
            + "다른 사용자 소유 식사와 존재하지 않는 식사는 구분하지 않습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "식사 삭제 완료. 공통 응답 코드: `MEAL_DELETED`"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "식사 식별자가 양의 정수가 아닙니다. 공통 응답 코드: `INVALID_INPUT`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "유효한 Bearer Access Token이 없습니다. 공통 응답 코드: `E003`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "식사가 없거나 다른 사용자 소유입니다. 공통 응답 코드: `MEAL_NOT_FOUND`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500",
                    description = "예상하지 못한 서버 오류입니다. 공통 응답 코드: `INTERNAL_SERVER_ERROR`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(required = true, description = "삭제할 양의 식사 식별자")
            @PathVariable long mealId) {
        mealService.delete(ownerId(jwt), mealId);
        return ApiResponse.success("MEAL_DELETED", "식사가 삭제되었습니다.", null);
    }

    private long ownerId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
