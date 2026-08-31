# daily-summary - Design Document

> **Summary**: 인증 사용자의 요청 날짜별 meal item snapshot 주요 영양 합계와 불완전성 조회 설계
>
> **Version**: 1.0.0 <br>
> **Date**: 2026-08-30 <br>
> **Status**: Approved <br>
> **Decision**: `DAILY-SUMMARY-002` <br>
> **Related Plan**: `docs/01-plan/features/daily-summary.plan.md` (`DAILY-SUMMARY-001`) <br>
> **Related Issue**: [#20](https://github.com/KBS-guys/Nyam-server/issues/20)

---

## 1. 설계 목표와 경계

인증 사용자가 요청한 식사 기준 날짜의 저장된 `meal_items` snapshot을 집계해 에너지·탄수화물·단백질·지방의 일별 합계와 영양소별 불완전성을 조회한다.

- 소유자는 요청값이 아니라 Bearer Access Token의 양의 `BIGINT` subject로 정한다.
- 날짜는 서버 현재 시각이나 생성 시각이 아니라 저장된 `meals.meal_date`와 비교한다.
- 현재 `foods`를 조회하거나 join하지 않는다.
- 집계 결과를 별도 table이나 column에 저장하지 않는다.
- 누락된 영양값을 0이나 알려진 값만의 부분합으로 바꾸지 않는다.
- 단일 날짜 조회 API 하나만 구현한다.
- 기간 통계, 차트, 권장량·진단·치료, cache와 사전 집계는 포함하지 않는다.

## 2. 구성과 책임

```text
Bearer 요청
  -> Daily Summary Controller: HTTP 매핑, date 변환, JWT subject 추출, 응답 구성
  -> Daily Summary Service: 사용자·날짜 검증, strict-null 판정, 공개 결과 변환
  -> Meal Repository: 소유자·날짜 조건의 단일 aggregate query
  -> MySQL: meals + meal_items snapshot 집계
```

- daily-summary는 독립된 HTTP·서비스 책임을 갖되 별도 Entity나 저장 model은 만들지 않는다.
- 기존 `MealRepository`에 조회 전용 aggregate query를 추가한다.
- 집계값 전달에는 전체 item 수, 영양소별 non-null 수와 합계만 담는 작은 projection을 사용한다.
- projection은 meal repository 패키지가 소유하고 daily-summary 서비스가 이를 소비한다.
- projection은 database 집계 결과를 전달하는 내부 조회 계약이며 공개 API 응답 타입으로 사용하지 않는다.
- 별도 `DailySummaryRepository` 구현체나 범용 집계 abstraction은 추가하지 않는다.
- Controller에는 집계 또는 strict-null 판정 로직을 두지 않는다.
- JPA Entity는 공개 응답으로 반환하지 않는다.

Daily Summary Controller는 Daily Summary Service에 의존하고, Daily Summary Service는 `MealRepository`와 meal repository 소유 projection에 의존한다. `MealRepository`는 daily-summary 공개 응답 DTO를 참조하지 않는다.

## 3. 공개 API 계약

### 3.1 요청

`GET /api/v1/daily-summaries?date=YYYY-MM-DD`

- Bearer 인증이 필요하다.
- `date`는 필수 query parameter다.
- ISO `YYYY-MM-DD` 형식을 사용한다.
- 허용 범위는 기존 meal 계약과 같은 `1000-01-01`부터 `9999-12-31`까지다.
- 미래 날짜를 허용한다.
- `userId`, 시간대, 정렬, 기간 또는 pagination parameter는 받지 않는다.

### 3.2 성공 응답

성공은 HTTP `200 OK`, 코드 `DAILY_SUMMARY_RETRIEVED`다.

```json
{
  "success": true,
  "code": "DAILY_SUMMARY_RETRIEVED",
  "message": "일별 영양 요약을 조회했습니다.",
  "data": {
    "date": "2026-08-29",
    "mealItemCount": 3,
    "energy": {"value": 780.5000, "unit": "kcal", "complete": true},
    "carbohydrate": {"value": null, "unit": "g", "complete": false},
    "protein": {"value": 31.2500, "unit": "g", "complete": true},
    "fat": {"value": 18.1250, "unit": "g", "complete": true}
  }
}
```

| 필드 | 계약 |
|------|------|
| `date` | 요청한 식사 기준 날짜 |
| `mealItemCount` | 현재 사용자와 날짜에 포함된 `meal_items` 수 |
| `energy` | 저장된 `energy_snapshot` 합계, 단위 `kcal` |
| `carbohydrate` | 저장된 `carbohydrate_snapshot` 합계, 단위 `g` |
| `protein` | 저장된 `protein_snapshot` 합계, 단위 `g` |
| `fat` | 저장된 `fat_snapshot` 합계, 단위 `g` |
| `value` | 완전한 합계 또는 불완전한 경우 `null` |
| `complete` | 모든 집계 item에 해당 영양 snapshot이 존재하는지 여부 |

영양소 객체는 값이 불완전해도 항상 존재한다. 숫자의 후행 0이나 고정 문자열 표기는 공개 계약으로 고정하지 않는다.

`complete: true`는 모든 item에 값이 존재한다는 뜻이다. 원천 데이터의 정확성, 영양학적 완전성 또는 건강 적합성을 의미하지 않는다.

### 3.3 빈 날짜 응답

집계 대상 item이 없으면 not-found가 아니라 요청한 `date`, 같은 성공 코드와 `mealItemCount: 0`을 반환한다. 네 영양소는 모두 `value: 0`, `complete: true`다. item이 존재하지만 실제 snapshot 합계가 0이면 `mealItemCount`는 0보다 크므로 빈 날짜와 구분된다.

### 3.4 오류 계약

| 조건 | HTTP | 공개 코드 |
|------|------|-----------|
| `date` 누락·형식 오류·MySQL 범위 초과 | 400 | `INVALID_INPUT` |
| 인증 누락 또는 유효하지 않은 Bearer Token | 401 | `E003` |
| 예상하지 못한 집계 또는 내부 오류 | 500 | `INTERNAL_SERVER_ERROR` |

빈 날짜에는 404를 사용하지 않는다. 내부 예외, SQL과 database 상세는 응답에 포함하지 않는다.

## 4. 집계 query와 strict-null 정책

### 4.1 query 입력과 범위

단일 aggregate query는 다음 조건을 모두 database query에 적용한다.

- `meals.user_id = authenticatedUserId`
- `meals.meal_date = requestedDate`
- `meal_items.meal_id = meals.meal_id`

현재 food와는 join하지 않는다. 다른 사용자 또는 다른 날짜의 item을 조회한 뒤 애플리케이션에서 걸러내는 방식은 사용하지 않는다.

### 4.2 aggregate projection과 database `NULL` 보존

query는 group 없이 정확히 한 aggregate 결과를 반환한다.

- `mealItemCount`: `meal_item_id`의 개수
- 영양소별 non-null snapshot 수
- 영양소별 `SUM(snapshot)`

`meal_item_id`를 세어 빈 날짜나 잠재적인 join 행이 item으로 계산되지 않게 한다. item Entity 목록을 메모리에 적재하여 합산하지 않는다.

database의 `SUM`은 집계할 non-null 값이 없으면 `NULL`을 반환한다. query에서는 `COALESCE`, `IFNULL`이나 `CASE`로 이 값을 0으로 바꾸지 않으며, projection의 합계도 nullable `BigDecimal`로 받아 database `NULL`을 서비스까지 보존한다.

서비스만 전체 item 수와 non-null 수를 함께 판정한다. 따라서 빈 날짜의 정상적인 database `NULL`과 값이 누락된 날짜의 database `NULL`을 구분할 수 있고, 알려진 값만의 부분합을 완전한 합계로 노출하지 않는다.

### 4.3 projection 불변식

전체 item 수를 `N`, 특정 영양소의 non-null 수를 `C`, database 합계를 `S`라고 한다. aggregate projection은 다음을 만족해야 한다.

- aggregate projection 자체가 null이면 내부 오류로 처리한다.
- `N`과 각 영양소의 `C`는 null이 아니며 `N >= 0`이다.
- 각 영양소에서 `0 <= C <= N`이다.
- `C == 0`이면 `S`는 database `NULL`이다.
- `C > 0`이면 `S`는 null이 아니며 0 이상이다.
- 따라서 `N == 0`이면 모든 영양소에서 `C == 0`이고 `S`는 database `NULL`이다.
- 불변식을 위반한 결과는 0이나 부분합으로 보정하지 않고 내부 오류로 처리한다.

### 4.4 영양소별 공개 판정

| 조건 | `value` | `complete` |
|------|---------|------------|
| `N == 0` | `0` | `true` |
| `N > 0`이고 `C < N` | `null` | `false` |
| `N > 0`이고 `C == N` | `S` | `true` |

- `N == 0`일 때만 정상적인 빈 집합의 database `NULL`을 공개값 0으로 변환한다.
- `N > 0`이고 `C < N`이면 `S`에 알려진 값의 부분합이 있어도 공개하지 않는다.
- 한 영양소의 `NULL`은 다른 영양소에 영향을 주지 않는다.
- query 결과에 추가 반올림이나 scale 축소를 하지 않는다.
- 단위는 V7 제약과 공개 계약에 따라 에너지 `kcal`, 나머지 영양소 `g`로 구성한다.

## 5. 날짜 정책 재사용

기존 `MealService`의 private MySQL `DATE` 범위 검증을 meal 날짜라는 동일한 도메인 규칙을 표현하는 작은 공통 정책으로 추출한다.

- Meal Service와 Daily Summary Service가 같은 날짜 정책을 사용한다.
- 정책은 null 여부와 `1000-01-01`부터 `9999-12-31` 범위만 검증한다.
- 미래 날짜 허용 계약을 바꾸지 않는다.
- 범용 날짜 validation framework나 global policy 계층은 만들지 않는다.
- HTTP 형식 오류는 기존 Spring MVC 변환과 공통 예외 처리에 맡기고, 서비스 정책은 변환된 `LocalDate`의 도메인 범위를 방어한다.

## 6. 데이터 모델과 Migration

새 Entity, table, column, index 또는 Flyway Migration을 추가하지 않는다. V7의 `(user_id, meal_date, meal_id)` 인덱스, meal-item 관계, nullable `DECIMAL(16,4)` snapshot, 값·단위 제약과 삭제 방향을 그대로 사용한다. 기존 meal Entity 관계에 조회 query만 추가하고 집계 결과는 영속화하지 않는다.

## 7. 서비스와 transaction

- 서비스 조회 경계에 read-only transaction을 적용한다.
- 한 요청은 한 aggregate query만 실행한다.
- 서비스는 양의 사용자 식별자와 공통 meal 날짜 정책을 방어적으로 검증한다.
- query 결과의 불변식을 확인한 뒤 영양소별 strict-null 공개 결과로 변환한다.
- 별도 locking이나 transaction isolation 강화는 적용하지 않는다.
- 동시 meal 생성·삭제와 summary 조회 사이의 순서 보장은 공개 계약으로 제공하지 않는다.
- 한 요청의 결과는 해당 aggregate statement가 관찰한 database 상태를 따른다.

## 8. 인증과 사용자 격리

- Controller는 `@AuthenticationPrincipal Jwt`의 subject만 소유자 식별자로 사용한다.
- JWT 서명·만료·issuer·audience와 양의 `BIGINT` subject 검증은 기존 Security 설정을 그대로 사용한다.
- 요청값이나 응답에 `userId`를 추가하지 않는다.
- 소유자 조건은 query 자체에 포함한다.
- 다른 사용자에게 같은 날짜의 meal이 있더라도 현재 사용자에게 item이 없으면 빈 날짜 응답을 반환한다.
- 인증 오류 시 서비스와 repository를 호출하지 않는다.
- 새 endpoint를 위한 별도 Security matcher는 추가하지 않는다.

## 9. Swagger와 JavaDoc

한국어 Swagger는 자기 snapshot 집계, 날짜 제약, `mealItemCount`, strict-null, 빈 날짜와 실제 0 합계의 차이 및 200·400·401·500 결과를 설명한다. 현재 food 변경이 과거 결과를 바꾸지 않는다는 점도 명시한다.

주요 Controller·Service·응답 타입과 projection에는 공개 계약이나 strict-null 이유를 설명하는 한국어 JavaDoc을 작성한다. 자명한 생성자·접근자와 변환 helper에는 반복하지 않는다.

## 10. 검증 설계

| 계층 | 대표 검증 |
|------|-----------|
| 단위 | 완전 합계, 영양소별 strict-null, 빈 날짜, 실제 0 합계, 추가 반올림 금지, 날짜·사용자 검증과 projection 불변식 실패 |
| 웹 | JWT subject와 날짜 전달, 성공·빈 날짜 응답, 날짜 오류 `INVALID_INPUT`, 미인증 `E003`, 사용자·food 정보 비노출 |
| OpenAPI | GET path와 필수 date, `bearerAuth`, 응답 필드, nullable value, strict-null 설명과 200·400·401·500 결과 |
| MySQL 8.4.5 | 합계, 사용자·날짜 격리, `SUM NULL` 보존, 빈 날짜·실제 0 구분, food 변경 독립성, 단일 statement와 `skipped=0` |

Java 또는 테스트 변경 후 다음을 통과해야 한다.

```powershell
.\gradlew.bat test javadoc
git diff --check
```

passed, failed, errors, skipped와 unexecuted 결과를 구분해 보고한다.

## 11. 구현 범위와 승인 경계

구현 순서는 다음을 권장한다.

1. meal 날짜 공통 정책 추출과 기존 Meal Service 적용
2. meal repository 소유 projection과 aggregate query 추가
3. strict-null 변환을 담당하는 Daily Summary Service 추가
4. 공개 응답 구조와 Controller 추가
5. 단위·웹·OpenAPI·실제 MySQL 테스트 추가
6. 전체 테스트·JavaDoc·diff 검사

새 Migration, 저장형 summary, 기간 조회, cache, mapper 계층과 새 의존성은 추가하지 않는다.

`DAILY-SUMMARY-002`는 이 Design 전체를 승인한다. 이번 결정은 Design 승인만 완료하며 구현·stage·commit·push와 Pull Request를 승인하지 않는다.

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2026-08-30 | `DAILY-SUMMARY-002`로 Design 전체 승인; 구현과 Git 게시는 미승인 |
| 0.2.0 | 2026-08-30 | meal 날짜 정책 재사용, projection 소유·의존 방향, 전체 불변식, database `SUM NULL` 보존과 빈 날짜의 요청 date 반환을 보완 |
| 0.1.0 | 2026-08-30 | API, 영양소별 strict-null, 단일 aggregate query, transaction·Migration 경계와 대표 검증을 포함한 최초 Design 초안 |
