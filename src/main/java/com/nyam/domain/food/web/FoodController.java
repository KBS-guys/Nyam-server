package com.nyam.domain.food.web;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nyam.domain.food.service.FoodQueryService;
import com.nyam.global.common.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 인증된 식품명 검색과 영양정보 상세 조회 HTTP 계약을 제공합니다.
 */
@RestController
@RequestMapping(path = "/api/v1/foods", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "식품", description = "적재된 공공 식품을 이름 접두사로 검색하고 주요 영양정보를 조회합니다.")
@SecurityRequirement(name = "bearerAuth")
public class FoodController {

    private final FoodQueryService foodQueryService;

    /**
     * 식품 읽기 유스케이스를 수행할 서비스를 주입받습니다.
     *
     * @param foodQueryService 식품 검색 및 상세 조회 서비스
     */
    public FoodController(FoodQueryService foodQueryService) {
        this.foodQueryService = foodQueryService;
    }

    /**
     * 정규화된 식품명 접두사와 일치하는 식품을 최대 20개 반환합니다.
     *
     * @param query 1자 이상 100자 이하의 식품명 접두사
     * @return 공통 성공 봉투에 담긴 결정적 식품 검색 결과
     */
    @GetMapping("/search")
    @Operation(
            summary = "식품명 접두사 검색",
            description = "검색어를 NFKC·공백·소문자 규칙으로 정규화하고 wildcard를 리터럴로 처리한 뒤 최대 20개를 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "검색 완료. 공통 응답 코드: `S000`"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "검색어가 없거나 정규화 후 1~100자 범위를 벗어났습니다. 공통 응답 코드: `INVALID_INPUT`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "유효한 Bearer Access Token이 없습니다. 공통 응답 코드: `E003`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "예상하지 못한 서버 오류입니다. 공통 응답 코드: `INTERNAL_SERVER_ERROR`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ApiResponse<List<FoodSearchResponse>> search(
            @Parameter(required = true, description = "정규화 후 1자 이상 100자 이하인 식품명 접두사")
            @RequestParam String query) {
        List<FoodSearchResponse> foods = foodQueryService.search(query).stream()
                .map(FoodSearchResponse::from)
                .toList();
        return ApiResponse.success(foods);
    }

    /**
     * 한 식품의 영양 기준과 주요 영양값을 반환합니다.
     *
     * @param foodId 양의 내부 식품 식별자
     * @return 공통 성공 봉투에 담긴 식품 상세
     */
    @GetMapping("/{foodId}")
    @Operation(
            summary = "식품 영양정보 상세 조회",
            description = "식품의 기준량·기준 단위와 에너지, 탄수화물, 단백질, 지방 값을 명시적 단위와 함께 반환합니다. 원천에 없는 값은 0이 아니라 null입니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "상세 조회 완료. 공통 응답 코드: `S000`"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "식품 식별자가 양의 정수가 아닙니다. 공통 응답 코드: `INVALID_INPUT`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "유효한 Bearer Access Token이 없습니다. 공통 응답 코드: `E003`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "식품이 존재하지 않습니다. 공통 응답 코드: `FOOD_NOT_FOUND`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500", description = "예상하지 못한 서버 오류입니다. 공통 응답 코드: `INTERNAL_SERVER_ERROR`",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ApiResponse<FoodDetailResponse> detail(
            @Parameter(required = true, description = "검색 결과에서 받은 양의 식품 식별자")
            @PathVariable Long foodId) {
        return ApiResponse.success(FoodDetailResponse.from(foodQueryService.get(foodId)));
    }
}
