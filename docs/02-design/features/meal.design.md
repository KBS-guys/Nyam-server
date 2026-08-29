# meal - Design Document

> **Summary**: 인증 사용자 식사 생성·날짜별 조회·삭제와 불변 meal item 영양 snapshot 설계
>
> **Version**: 1.0.0 <br>
> **Date**: 2026-08-29 <br>
> **Status**: Approved <br>
> **Decision**: `MEAL-002` <br>
> **Related Plan**: `docs/01-plan/features/meal.plan.md` (`MEAL-001`) <br>
> **Related Issue**: [#18](https://github.com/KBS-guys/Nyam-server/issues/18)

---

## 1. 설계 목표와 경계

인증 사용자가 food와 섭취량으로 한 식사를 원자적으로 생성하고, 요청한 `mealDate`별 자기 식사 목록을 조회하며, 자기 식사를 삭제하게 한다. 각 item은 기록 시점의 식품명·섭취량·영양정보를 snapshot으로 보존한다.

- 소유자는 요청값이 아니라 Nyamlog Bearer Token의 양의 `BIGINT` subject로 정한다.
- 생성 요청은 `mealDate`, `foodId`, `amount`만 받는다. 식품명·단위·영양값은 서버가 food에서 읽는다.
- 조회 응답은 저장된 snapshot만 사용하며 현재 food 값으로 대체하지 않는다.
- 원천 영양값 `NULL`은 계산 결과와 응답에서도 `NULL`이다.
- meal 전체 합계는 저장하거나 반환하지 않는다. 집계는 후속 `daily-summary`가 담당한다.
- 수정·상세·페이지네이션·custom food·단위 변환·social-login 구현은 포함하지 않는다.

## 2. 구성과 책임

```text
Bearer 요청
  -> Meal Controller: HTTP 매핑, 입력 검증, JWT subject 추출, 응답 구성
  -> Meal Service: 소유권, food 조회, snapshot 계산, 생성·삭제 transaction
  -> Meal/Food 저장소: 소유자 조건 조회와 영속화
  -> MySQL: Flyway schema와 FK·CHECK 제약
```

- meal 코드는 business domain 단위로 둔다.
- Controller에 계산·소유권·데이터 접근 규칙을 두지 않고 JPA entity를 공개 응답으로 반환하지 않는다.
- 별도 interface, mapper 계층, aggregate 저장소, cache 또는 새 의존성을 추가하지 않는다.
- 생성 시 요청한 모든 food를 한 번에 읽고, 목록은 meal 수에 비례한 반복 조회 없이 meal과 item을 완전하게 조립한다.

## 3. 공개 API 계약

모든 API는 Bearer 인증이 필요하며 공통 `ApiResponse` 봉투를 사용한다.

### 3.1 식사 생성

`POST /api/v1/meals`

```json
{
  "mealDate": "2026-08-29",
  "items": [
    {
      "foodId": 1,
      "amount": 150
    }
  ]
}
```

- `mealDate`는 ISO `YYYY-MM-DD`이며 MySQL `DATE` 범위인 `1000-01-01`부터 `9999-12-31`까지 허용한다.
- 미래 날짜도 허용한다. 이 값은 서버 현재 시각이 아닌 사용자가 지정한 식사 기준 날짜이므로 사용자 시간대 정책을 도입하지 않는다.
- `items`는 1개 이상 20개 이하이며 같은 `foodId`를 한 요청에 중복할 수 없다.
- `foodId`는 양수이고 `amount`는 `0` 초과 `10,000` 이하이며 숫자값을 변경하지 않고 scale 4로 표현할 수 있어야 한다.
- `amount`의 소수 자릿수가 4자리보다 적으면 0을 채워 저장하고, 4자리를 초과하더라도 제거되는 값이 모두 0이면 허용한다. scale 4 변환에 반올림이 필요한 값은 `INVALID_INPUT`이다.
- 클라이언트가 보낸 `userId`, 이름, 단위 또는 영양값은 계약에 없으며 소유권이나 snapshot 생성에 사용하지 않는다.
- 성공은 HTTP `201 Created`, 코드 `MEAL_CREATED`이며 생성된 식사 snapshot 전체를 반환한다.

### 3.2 날짜별 목록 조회

`GET /api/v1/meals?date=YYYY-MM-DD`

- `date`의 형식과 범위는 생성의 `mealDate`와 같다.
- 현재 인증 사용자와 요청 날짜가 모두 일치하는 식사만 반환한다.
- meal은 `mealId` 내림차순, 각 meal의 item은 요청 순서를 보존한 `itemPosition` 오름차순이다.
- 기록이 없으면 빈 목록과 HTTP `200 OK`, 코드 `MEALS_RETRIEVED`를 반환한다.
- 각 item의 이름·섭취량·단위·영양값은 `meal_items` snapshot에서만 읽는다.

### 3.3 식사 삭제

`DELETE /api/v1/meals/{mealId}`

- `mealId`는 양수다.
- 현재 인증 사용자가 소유한 식사와 그 item만 삭제한다.
- 다른 사용자 소유 식사와 존재하지 않는 식사는 모두 HTTP `404 Not Found`, 코드 `MEAL_NOT_FOUND`로 처리한다.
- 성공은 HTTP `200 OK`, 코드 `MEAL_DELETED`, `data: null`이다.

### 3.4 응답 데이터

생성 응답의 `data`와 목록의 각 원소는 다음 형태를 공유한다.

| 필드 | 의미 |
|------|------|
| `mealId` | 식사 식별자 |
| `mealDate` | 요청으로 저장한 식사 기준 날짜 |
| `items[].foodId` | 기록 시 참조한 food 식별자 |
| `items[].name` | 저장된 식품명 snapshot |
| `items[].amount` | 저장된 섭취량 |
| `items[].unit` | food 기준 단위와 같은 `g` 또는 `ml` |
| `items[].energy` | nullable `value`와 `kcal` 단위 |
| `items[].carbohydrate` | nullable `value`와 `g` 단위 |
| `items[].protein` | nullable `value`와 `g` 단위 |
| `items[].fat` | nullable `value`와 `g` 단위 |

영양 객체는 값이 없어도 존재하며 `value: null`과 해당 단위를 반환한다. 영양값과 섭취량의 JSON 숫자 표기에서 후행 0의 존재 여부는 공개 계약으로 고정하지 않는다. JSON 표기와 무관하게 저장값은 승인된 scale 4 정밀도를 유지하며 숫자값이 변경되지 않는다. meal item 식별자와 전체 영양 합계는 공개하지 않는다.

### 3.5 오류와 OpenAPI

| 조건 | HTTP | 공개 코드 |
|------|------|-----------|
| 인증 누락·유효하지 않은 Bearer Token | 401 | `E003` |
| 형식·범위·항목 수·중복 위반 | 400 | `INVALID_INPUT` |
| 하나 이상의 food가 존재하지 않음 | 404 | `FOOD_NOT_FOUND` |
| 식사가 없거나 다른 사용자 소유 | 404 | `MEAL_NOT_FOUND` |
| 예상하지 못한 내부 실패 | 500 | `INTERNAL_SERVER_ERROR` |

한국어 Swagger는 목적, Bearer 요구, 입력 제약, snapshot의 서버 계산·불변성, 성공 상태와 위 오류를 설명한다. 내부 예외·SQL·entity는 노출하지 않는다.

## 4. 데이터 모델

### 4.1 `meals`

| Column | Type | Null | Constraint and meaning |
|--------|------|------|------------------------|
| `meal_id` | `BIGINT` | No | Auto-increment primary key |
| `user_id` | `BIGINT` | No | 인증 소유자, `users.user_id` FK |
| `meal_date` | `DATE` | No | 요청으로 저장한 식사 기준 날짜 |

- `(user_id, meal_date, meal_id)` 인덱스로 소유자·날짜 조회와 결정적 정렬을 지원한다.
- `meals.user_id`는 `users.user_id`를 `ON DELETE CASCADE`로 참조하고, 삭제된 meal의 item은 `meal_items.meal_id` cascade를 통해 함께 삭제된다.

### 4.2 `meal_items`

| Column | Type | Null | Constraint and meaning |
|--------|------|------|------------------------|
| `meal_item_id` | `BIGINT` | No | Auto-increment primary key |
| `meal_id` | `BIGINT` | No | `meals.meal_id` FK, meal 삭제 시 cascade |
| `item_position` | `SMALLINT` | No | 요청 순서, meal 안에서 1부터 시작 |
| `food_id` | `BIGINT` | No | 기록 시 참조한 `foods.food_id` FK |
| `food_name_snapshot` | `VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin` | No | 기록 시 food 이름의 정확한 복사본 |
| `consumed_amount` | `DECIMAL(12,4)` | No | `0` 초과 `10,000` 이하 |
| `consumed_unit` | `VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin` | No | `G` 또는 `ML` |
| `energy_snapshot` | `DECIMAL(16,4)` | Yes | 섭취량 기준 에너지 |
| `energy_unit` | `VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin` | No | `KCAL` |
| `carbohydrate_snapshot` | `DECIMAL(16,4)` | Yes | 섭취량 기준 탄수화물 |
| `carbohydrate_unit` | `VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin` | No | `G` |
| `protein_snapshot` | `DECIMAL(16,4)` | Yes | 섭취량 기준 단백질 |
| `protein_unit` | `VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin` | No | `G` |
| `fat_snapshot` | `DECIMAL(16,4)` | Yes | 섭취량 기준 지방 |
| `fat_unit` | `VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin` | No | `G` |

- `(meal_id, item_position)`은 unique이며 위치는 1부터 20까지다.
- `(meal_id, food_id)`은 unique여서 한 meal 안의 같은 food 중복을 database에서도 방지한다.
- database `CHECK`는 섭취량·위치·단위와 nullable 영양값의 0 이상 조건을 방어한다. 단위 컬럼은 `ascii_bin`에서 `G`, `ML`, `KCAL`의 대소문자를 정확히 제약한다.
- meal 삭제만 item으로 cascade한다. food FK는 `ON DELETE RESTRICT`여서 food 삭제가 과거 snapshot을 지우지 못한다.
- food의 이름·영양값 변경은 `meal_items`를 갱신하지 않는다. 향후 food 삭제 정책도 기존 snapshot 보존을 침해할 수 없다.
- meal 또는 daily 합계 column은 두지 않는다.

## 5. Snapshot 계산과 transaction

### 5.1 계산

각 영양값은 서버에서 다음과 같이 계산한다.

```text
snapshot = food 원천 영양값 × amount ÷ food.basisAmount
```

- food 기준량은 현재 승인된 food 계약의 `100.0000`, 섭취 단위는 기준 단위 `G` 또는 `ML`이다.
- 원천 값이 `NULL`이면 계산하지 않고 snapshot도 `NULL`로 저장한다.
- `BigDecimal`로 계산하고 최종 저장 직전에만 scale 4, `RoundingMode.HALF_UP`을 한 번 적용한다.
- 입력 `amount`는 `setScale(4, RoundingMode.UNNECESSARY)`와 동등한 규칙으로 검증·정규화한다. 숫자값을 바꾸는 반올림이나 범위 위반은 `INVALID_INPUT`이다.
- 계산 결과가 `DECIMAL(16,4)`에 들어가지 않으면 영속화 전에 `INVALID_INPUT`으로 거절한다.
- 식품명·단위도 같은 일괄 조회 결과에서 복사하여 한 기록 안에서 동일한 조회 시점 값을 사용한다.

### 5.2 생성

- 서비스의 한 write transaction에서 중복을 확인하고 모든 food를 일괄 조회한 뒤 누락 여부와 계산 결과를 검증한다.
- 검증이 끝나면 meal과 요청 순서의 item을 저장한다.
- food 누락, 계산·제약 위반 또는 item 저장 실패가 하나라도 있으면 meal과 모든 item을 rollback한다.
- transaction 완료 뒤 food가 변경되어도 저장된 snapshot은 갱신하지 않는다.

### 5.3 조회와 삭제

- 목록은 read-only transaction에서 `userId + mealDate`로 meal을 제한하고 item을 함께 조립한다.
- 조회 쿼리는 현재 food를 join하여 이름이나 영양값을 대체하지 않는다. `food_id`는 응답 식별자일 뿐 snapshot 값의 출처가 아니다.
- 삭제는 write transaction에서 `mealId + userId`로 소유권을 확인한 뒤 삭제한다.
- meal 삭제는 FK로 item에만 전파되며 food 행은 변경하지 않는다.

## 6. 대표 검증

| 구분 | 필수 증거 |
|------|-----------|
| 단위 | amount의 무손실 scale 4 정규화, snapshot 계산식과 단일 반올림, 원천 `NULL`, 범위 경계, 중복 food 거절 |
| Web | JWT subject 소유자, 요청에 사용자 식별자 없음, 생성 201, 빈 날짜 목록, 정렬, 삭제와 공개 오류 |
| 격리 | 날짜 목록에 현재 사용자만 노출되고 교차 사용자 삭제와 없는 식사가 같은 404 |
| Snapshot | 생성 후 food 이름·영양값을 변경해도 목록 응답이 그대로이며 현재 food를 응답값에 사용하지 않음 |
| Transaction | 실제 MySQL에서 item 저장 실패를 강제했을 때 meal과 모든 item이 남지 않음 |
| FK·제약 | meal 삭제 시 item cascade, food 삭제 시 RESTRICT, 사용자 삭제 시 소유 데이터 cascade, CHECK 위반 거절 |
| 조회 | 여러 meal·item의 완전한 nested 응답과 불필요한 meal별 반복 조회가 없음 |
| Migration | 빈 MySQL에서 V1부터 meal Migration까지 전체 적용되고 Hibernate `validate` 통과 |
| OpenAPI | 한국어 설명, Bearer 보안, 필드 제약, 201/200/400/401/404/500과 snapshot 민감 경계 |

- MySQL 관련 증거는 Testcontainers `mysql:8.4.5`에서 실제 실행하고 XML 결과의 실패·오류·skip이 모두 0인지 확인한다.
- Java와 테스트 변경 후 전체 `./gradlew.bat test javadoc`을 통과한다.
- Docker 미가동으로 MySQL 테스트가 skip된 결과는 완료 증거가 아니다.

## 7. 승인 경계

이 문서는 Issue #18과 승인된 Plan `MEAL-001`을 구체화한 **통합 Design 초안**이다. 전체 Design 승인과 구현 승인은 별도다.

- Design 승인 전: 문서 검토·수정만 수행한다.
- Design 승인 후에도 구현, stage, commit, push와 PR은 별도 명시적 승인 전에는 수행하지 않는다.
- 구현 중 공개 API·소유권·snapshot 보존·transaction 또는 schema 경계와 충돌하면 임의 변경하지 않고 재설계를 요청한다.

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2026-08-29 | `MEAL-002` 승인: meal API, snapshot 계산·저장, 소유권, transaction, FK와 검증 계약 확정 |
| 0.2.0 | 2026-08-29 | 단위 binary collation, meal별 food unique, amount 무손실 scale 4 규칙과 JSON·Migration 검증 경계 보완 |
| 0.1.0 | 2026-08-29 | Issue #18과 승인된 Plan을 기준으로 최초 통합 Design 초안 작성 |
