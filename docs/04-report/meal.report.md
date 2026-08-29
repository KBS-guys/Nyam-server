# meal Completion Report

> **Status**: Complete <br>
> **Completion Date**: 2026-08-29 <br>
> **Plan**: `MEAL-001` <br>
> **Design**: `MEAL-002` <br>
> **Analysis**: `docs/03-analysis/meal.analysis.md` <br>
> **Related Issue**: [#18](https://github.com/KBS-guys/Nyam-server/issues/18)

---

## 1. 결과

인증 사용자가 공공 food와 섭취량으로 식사를 생성하고, 요청한 `mealDate`별 자기 식사와 item snapshot을 조회하며, 소유한 식사를 삭제하는 서버 수직 흐름을 완성했다.

각 meal item은 기록 시점의 식품명·섭취량·단위와 에너지·탄수화물·단백질·지방을 저장한다. 조회 응답은 현재 food가 아니라 저장된 snapshot을 사용하며, 원천 영양값 `NULL`도 그대로 보존한다.

## 2. 변경 영향

- `POST /api/v1/meals`, `GET /api/v1/meals?date=YYYY-MM-DD`, `DELETE /api/v1/meals/{mealId}`와 한국어 OpenAPI 계약을 추가했다.
- 요청은 `mealDate`, `foodId`, `amount`만 받고 소유자는 SecurityContext의 JWT subject에서 정한다.
- 생성은 모든 food를 일괄 조회한 뒤 한 서비스 transaction에서 meal과 item을 저장하며 한 항목이라도 실패하면 전체 rollback한다.
- V7 Migration은 `meals`와 `meal_items`, 정밀도·단위·중복·범위 제약과 조회 인덱스를 추가한다.
- meal 삭제와 사용자 삭제는 소속 item까지 cascade하지만 food 삭제는 `RESTRICT`하여 과거 snapshot을 보호한다.
- 날짜별 목록은 소유자와 날짜로 제한하고 meal ID 내림차순·item 요청 순서로 반환하며 meal별 반복 조회 없이 nested 응답을 조립한다.
- meal 또는 daily 영양 합계는 중복 저장하지 않았고 후속 `daily-summary`의 책임으로 유지했다.

## 3. Check 결과

승인된 Design 22개 계약을 최초 구현과 대조한 결과 **22/22, 100%**가 일치했다. 이후 PR #19 리뷰에서 `items: [null]`이 DTO 변환 중 예외를 일으켜 승인된 `400 INVALID_INPUT` 대신 500이 될 수 있는 P2를 확인해 두 번째 iteration의 Act를 수행했다.

item container element에 `@NotNull`을 적용하고, null item 요청이 서비스 호출 전에 `INVALID_INPUT`으로 거절되는 웹 회귀 테스트를 추가했다. 재분석 결과는 다시 22/22, 100%이며 남은 P1·P2·P3 gap은 없다.

구현 중 실제 MySQL Hibernate validation이 `item_position SMALLINT`와 Java `int`의 불일치를 발견했다. Java 필드를 `short`로 수정한 뒤 fresh migration과 validation을 재실행해 해결했다.

rollback 테스트는 추가 DB 권한이 필요한 trigger 대신 두 번째 item의 실제 CHECK 위반을 강제했다. 이 방식으로 운영 schema를 확장하지 않고 승인된 전체 transaction rollback을 검증했다.

## 4. 검증

| 항목 | 최종 결과 |
|------|-----------|
| `.\gradlew.bat test javadoc` | 성공 |
| 전체 테스트 | 35 suites, 117 passed, 0 failed, 0 errors, 0 skipped, 0 unexecuted |
| meal 단위·Web·OpenAPI | 15 passed, 0 skipped |
| `MealMySqlIntegrationTest` | 3 passed, MySQL 8.4.5, 0 skipped |
| JavaDoc | 성공 |
| `git diff --check` | 공백 오류 없음 |
| Design 일치율 | 22/22, 100% |

실제 MySQL 검증은 V1부터 V7까지의 fresh migration과 Hibernate validation, snapshot 변경 독립성, 사용자 격리, nested 목록 정렬과 단일 statement, item 실패 전체 rollback, binary 단위 CHECK, meal별 food UNIQUE, meal·user cascade와 food RESTRICT를 포함한다.

## 5. 배운 점

### Keep

- nullable 영양값과 과거 기록 독립성을 API·schema·테스트에서 같은 계약으로 유지한다.
- MySQL 고유 타입·collation·FK 동작은 실제 기준 버전과 Hibernate validation으로 확인한다.
- 사용자 소유 데이터는 요청 식별자가 아니라 인증 principal과 소유자 조건 쿼리로 격리한다.

### Problem

- Java 숫자 타입과 MySQL `SMALLINT`의 차이는 단위 테스트만으로 드러나지 않았다.
- Docker 미가동 시 Testcontainers skip이 발생할 수 있으므로 일반 빌드 성공만 완료 증거로 사용할 수 없었다.
- cascaded `@Valid`만으로는 collection의 null 원소를 거절하지 않으므로 container element 제약이 별도로 필요했다.

### Try

- 새 Migration은 구현 초기에 실제 MySQL fresh migration과 Hibernate validation을 먼저 실행한다.
- 후속 `daily-summary`도 저장된 meal item snapshot만 집계하고 `NULL`을 임의로 0으로 바꾸지 않는 경계를 먼저 확정한다.

## 6. 완료 경계와 다음 단계

meal은 PR 리뷰 Act와 두 번째 재검증을 거쳐 Plan, Design, Do, Check와 Report까지 재완료했다. 상세·수정 API, meal 합계, `daily-summary`, social-login, 프론트엔드와 운영·확장 기능은 이번 완료 범위에 포함하지 않는다.

다음 작업 순서는 별도 승인에 따른 이번 수정 5개 경로의 stage·commit·push로 기존 PR #19를 업데이트하는 것이다. merge와 Issue 종료 뒤에는 다음 기능인 `daily-summary`를 별도 Issue와 PDCA 범위로 시작한다. Archive와 Git 게시 작업은 이 Report 작성에 포함하지 않는다.
