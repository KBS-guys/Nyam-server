# meal - Analysis

> **Status**: Complete <br>
> **Date**: 2026-08-29 <br>
> **Plan**: `MEAL-001` <br>
> **Design**: `MEAL-002` <br>
> **Related Issue**: [#18](https://github.com/KBS-guys/Nyam-server/issues/18) <br>
> **Match Rate**: 22/22 (100%)

---

## 1. 분석 범위

- 인증 사용자의 식사 생성·날짜별 목록·삭제 API와 공개 오류 계약
- JWT subject 소유권, 교차 사용자 조회·삭제 격리
- meal item 단위의 불변 영양 snapshot 계산·저장·응답
- Flyway schema, FK 방향, CHECK·UNIQUE·collation 제약
- 서비스 transaction, 전체 rollback, nested 목록 조회 방식
- 단위·Web·OpenAPI·실제 MySQL 8.4.5·전체 회귀 검증

## 2. Design 대조

| # | Design 계약 | 구현·검증 근거 | 결과 |
|---|-------------|----------------|------|
| 1 | `POST /api/v1/meals`, 201과 `MEAL_CREATED`, 생성 snapshot 반환 | `MealController`, `MealControllerTest` | Match |
| 2 | `GET /api/v1/meals?date=`, 200과 `MEALS_RETRIEVED`, 빈 목록 허용 | `MealController`, `MealService`, Web 테스트 | Match |
| 3 | `DELETE /api/v1/meals/{mealId}`, 200과 `MEAL_DELETED`, `data: null` | `MealController`, Web·MySQL 테스트 | Match |
| 4 | 입력·food 누락·소유 식사 부재의 승인된 공개 오류 계약 | `MealService`, `ErrorCode`, 단위·Web 테스트 | Match |
| 5 | 요청값이 아닌 JWT subject를 식사 소유자로 사용 | `MealController`, JWT principal Web 테스트 | Match |
| 6 | 목록의 사용자 격리와 타 사용자 삭제의 존재 은닉 | 소유자 조건 Repository, Web·MySQL 격리 테스트 | Match |
| 7 | MySQL `DATE` 범위의 요청 날짜 저장·조회와 미래 날짜 허용 | `MealService` 날짜 검증, `Meal.mealDate`, 단위·Web 테스트 | Match |
| 8 | item 1~20개, 양수 food ID, 같은 food 중복 거절 | `MealService.normalizeItems`, 단위 테스트, DB UNIQUE | Match |
| 9 | amount `(0, 10000]`, 값 변경 없는 scale 4 정규화 | `setScale(4, UNNECESSARY)`, 단위 경계 테스트, DB CHECK | Match |
| 10 | 생성 입력은 날짜·food ID·amount뿐이며 서버가 snapshot 생성 | `CreateMealRequest`, `CreateMealCommand`, Web 신뢰 경계 테스트 | Match |
| 11 | 모든 food 일괄 조회와 하나라도 없을 때 `FOOD_NOT_FOUND` | `FoodRepository.findAllById`, `MealServiceTest` | Match |
| 12 | 원천 영양값 × amount ÷ basis, 최종 scale 4 `HALF_UP`, 범위 방어 | `MealService.calculateSnapshot`, 단위·MySQL 테스트 | Match |
| 13 | 원천 `NULL`을 snapshot과 응답에서도 `NULL`로 보존 | `MealService`, `MealResponse`, 단위·MySQL 테스트 | Match |
| 14 | 조회 응답은 저장 snapshot만 사용하고 현재 food 값으로 대체하지 않음 | `MealResponse.from`, food 변경 후 MySQL 조회 테스트 | Match |
| 15 | `meals`의 사용자·날짜 모델, 조회 인덱스와 사용자 cascade | V7 Migration, `Meal` mapping, MySQL FK 테스트 | Match |
| 16 | `meal_items`의 정밀도·binary 단위·위치·food 중복·영양 CHECK | V7 Migration, `MealItem` mapping, 실제 MySQL 제약 테스트 | Match |
| 17 | meal→item cascade, food→item RESTRICT, food 행 불변 | V7 Migration과 실제 MySQL cascade·RESTRICT 테스트 | Match |
| 18 | 생성·삭제 서비스 transaction과 item 실패 시 전체 rollback | `@Transactional`, `saveAndFlush`·`flush`, 실제 MySQL rollback 테스트 | Match |
| 19 | meal ID 내림차순, item 위치 오름차순, 반복 조회 없는 nested 응답 | `MealRepository` EntityGraph, `@OrderBy`, MySQL 1 statement·정렬 검증 | Match |
| 20 | 한국어 Swagger, Bearer, 입력·snapshot·주요 결과와 내부 정보 비노출 | `MealController`, DTO schema, `MealOpenApiContractTest` | Match |
| 21 | 대표 단위·Web·OpenAPI 자동 검증 | meal 범위 14개 테스트 모두 통과 | Match |
| 22 | V1~V7 fresh migration, Hibernate validate, 실제 MySQL과 전체 회귀 무skip | Foundation·Meal MySQL 테스트와 전체 116개 테스트 | Match |

최종 일치율은 **22/22, 100%**다. 구현 누락, 승인되지 않은 기능 확장 또는 Design과 다른 공개 계약은 확인되지 않았다.

## 3. 자동 검증 결과

| 검증 | 결과 |
|------|------|
| `.\gradlew.bat test javadoc` | 성공 |
| 전체 테스트 | 35 suites, 117 passed, 0 failed, 0 errors, 0 skipped, 0 unexecuted |
| meal 단위·Web·OpenAPI | 15 passed, 0 skipped |
| `MealMySqlIntegrationTest` | 3 passed, MySQL 8.4.5, 0 skipped |
| JavaDoc | 성공 |
| `git diff --check` | 공백 오류 없음 |

실제 MySQL 검증은 V1부터 V7까지의 fresh migration과 Hibernate validation, snapshot 변경 독립성, 소유권 격리, 결정적 nested 정렬, 단일 목록 statement, item 실패 전체 rollback, binary 단위 CHECK, meal별 food UNIQUE, meal·user cascade와 food RESTRICT를 포함한다.

## 4. 발견·해결 사항

| 심각도 | 발견 사항 | 해결 및 최종 증거 |
|--------|-----------|-------------------|
| P2 | 최초 실제 MySQL Hibernate validation에서 `item_position SMALLINT`와 Java `int` 매핑 불일치 | `MealItem.itemPosition`을 `short`로 맞추고 fresh migration·Hibernate validation을 재실행하여 통과 |
| P2 | PR #19 리뷰에서 `items: [null]`이 DTO 변환 중 예외를 일으켜 `500 INTERNAL_SERVER_ERROR`가 될 수 있음 | item container element에 `@NotNull`을 적용하고 서비스 미호출·`400 INVALID_INPUT` 웹 회귀 테스트 추가 |

rollback 검증은 권한 의존적인 DB trigger 대신 두 번째 item의 실제 CHECK 위반을 강제하는 방식으로 구성했다. 이는 승인된 transaction 결과를 실제 MySQL에서 검증하면서 운영 권한이나 새 schema 객체를 요구하지 않는다.

## 5. Gap과 비차단 관찰

- 두 번째 iteration의 PR 리뷰 Act 후 남은 P1·P2·P3 구현 gap은 없다.
- meal 전체 영양 합계, `daily-summary`, 상세·수정 API, social-login 구현과 운영·확장 기능은 승인 범위 밖이며 추가하지 않았다.
- Docker 미가동 시 skip되었던 중간 결과는 완료 증거에서 제외했다. 최종 실제 MySQL XML은 3 passed, 0 skipped다.

## 6. Check 결론

두 번째 iteration의 PR 리뷰 Act와 재분석 결과 `MEAL-002` 구현 일치율은 다시 22/22, 100%다. null item도 서비스 호출 전에 승인된 `400 INVALID_INPUT`으로 처리되며 meal은 Report까지 재완료했다. stage, commit, push와 Pull Request 업데이트는 수행하지 않았다.
