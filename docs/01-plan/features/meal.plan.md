# meal - Plan Document

> **Summary**: 인증 사용자의 날짜별 식사 생성·조회·삭제와 meal item 영양 snapshot 흐름
>
> **Version**: 1.0.0 <br>
> **Date**: 2026-08-29 <br>
> **Status**: Approved <br>
> **Decision**: `MEAL-001` <br>
> **Related Issue**: [#18](https://github.com/KBS-guys/Nyam-server/issues/18) <br>
> **Scope Authority**: `FOUNDATION-006`, `FOUNDATION-006-R2`, `LOCAL-LOGIN-002`, `FOOD-002`

---

## 1. 목적

인증 사용자가 공공 food와 섭취량으로 식사를 생성하고, 요청한 식사 날짜별로 자기 기록을 조회하며, 개별 식사를 삭제하는 하나의 서버 수직 흐름을 완성한다.

기록 시점의 식품명과 섭취량 기준 주요 영양정보는 meal item에 snapshot으로 저장한다. 이후 원천 food가 변경되어도 과거 응답은 저장된 snapshot을 사용하며, 누락 영양값은 숫자 0으로 바꾸지 않는다.

## 2. 현재 기준선

- Bearer Access Token의 양의 `BIGINT` subject가 보호 API의 내부 사용자 식별자다.
- meal은 local credential이나 로그인 방식에 의존하지 않고 유효한 Nyamlog Bearer principal의 내부 사용자 ID만 사용한다.
- food는 내부 `foodId`, `100 G` 또는 `100 ML` 기준량과 nullable 에너지·탄수화물·단백질·지방을 제공한다.
- food 검색·상세와 실제 MySQL 적재 검증은 완료됐지만 meal 코드, Migration과 테스트는 아직 없다.
- Flyway가 스키마 권한을 가지며 Hibernate는 `ddl-auto=validate`를 사용한다.
- `daily-summary`는 meal item snapshot을 집계하는 다음 기능이며 이번 범위에 포함하지 않는다.

## 3. 목표와 비목표

### 3.1 목표

- 인증 사용자 소유의 식사와 하나 이상의 meal item을 원자적으로 생성한다.
- 클라이언트가 제출한 `mealDate`, `foodId`, `amount`만 입력으로 사용하고 snapshot은 서버가 현재 food에서 계산한다.
- 날짜별 목록에서 meal item과 저장된 snapshot을 반환한다.
- 사용자 소유권과 교차 사용자 격리를 생성·목록·삭제 전체에 적용한다.
- 실제 MySQL에서 schema, transaction rollback과 snapshot 독립성을 검증한다.

### 3.2 비목표

- `daily-summary`와 meal 또는 daily 단위 영양 합계 저장
- 식사 수정·복구·soft delete와 별도 상세 조회
- meal type, 메모, 사진, 태그, 템플릿과 custom food
- 단위 변환, 사용자별 시간대, 영양 권장·진단·치료 기능
- social-login 자체의 구현과 provider별 인증 처리
- 프론트엔드, PWA와 운영·확장 인프라

## 4. 범위

### 4.1 포함

- `POST /api/v1/meals` 후보를 통한 인증 사용자 식사 생성
- 요청의 `mealDate` 저장과 `GET /api/v1/meals?date=YYYY-MM-DD` 후보를 통한 날짜별 목록 조회
- `DELETE /api/v1/meals/{mealId}` 후보를 통한 소유 식사 삭제
- 한 식사에 하나 이상의 `foodId`와 양수 `amount` 연결
- food 기준 단위를 섭취 단위로 사용하고 별도 단위 선택·변환 금지
- 각 meal item의 식품명, 섭취량·단위, 에너지·탄수화물·단백질·지방 값과 단위 snapshot
- `원천 영양값 × 섭취량 ÷ 기준량`의 서버 계산과 원천 `NULL` 보존
- JWT principal 기반 소유권, 날짜별 교차 사용자 비노출과 삭제 존재 은닉
- 서비스 계층 생성 transaction과 한 항목 실패 시 전체 rollback
- Flyway Migration, 한국어 Swagger, 단위·웹·OpenAPI·실제 MySQL 검증

정확한 요청·응답 DTO, 상태 코드와 schema는 meal Design에서 확정한다.

### 4.2 제외

- 현재 food를 다시 읽어 과거 목록 응답의 snapshot 값을 대체하는 동작
- meal 테이블에 식사 전체 영양 합계를 중복 저장하는 동작
- 목록 페이지네이션, 검색·필터·수정 API
- 원천 food의 변경 또는 삭제가 기존 meal과 meal item을 삭제하거나 변경하는 동작
- food 삭제 API와 일반적인 food 삭제 수명주기 구현
- meal item snapshot 외의 이력 관리
- idempotency key, optimistic locking, 동시 삭제 경쟁과 성능 시험
- Redis, cache, event, monitoring과 분산 처리

## 5. 인수 시나리오

### A. 식사 생성

- 인증 사용자가 `mealDate`와 하나 이상의 `foodId`·`amount`를 제출하면 한 meal과 모든 item이 생성된다.
- 요청에는 `userId`, 식품명, 단위 또는 영양값을 받지 않으며 서버가 JWT subject와 현재 food를 사용한다.
- 섭취량 단위는 food의 기준 단위 `G` 또는 `ML`이며 다른 단위로 변환하지 않는다.

### B. Snapshot과 transaction

- 각 item은 섭취량 기준 영양값을 저장하고 원천 영양값이 없으면 해당 snapshot도 `NULL`이다.
- food 부재, 잘못된 amount 또는 계산 불가 항목이 하나라도 있으면 meal과 모든 item 저장이 rollback된다.
- 생성 뒤 food 이름이나 영양값이 바뀌어도 저장된 과거 snapshot과 조회 응답은 변하지 않는다.

### C. 날짜별 조회와 소유권

- 조회 날짜는 서버 현재 날짜나 생성 시각이 아니라 저장된 `mealDate`다.
- 목록에는 현재 인증 사용자 소유 meal만 포함된다.
- 각 meal은 저장된 item의 식품명, 섭취량·단위와 영양 snapshot을 포함한다.
- 동일 날짜 meal 목록과 각 meal 내부 item은 Design에서 승인한 기준과 식별자로 결정적인 순서를 가진다.

### D. 삭제

- 소유자는 개별 meal을 삭제하고 그 item도 함께 제거한다.
- 다른 사용자 소유 ID와 존재하지 않는 ID는 동일한 `MEAL_NOT_FOUND`로 처리한다.
- meal 삭제는 원천 food를 변경하지 않는다.
- meal 삭제는 소속 item에만 전파되며, food에서 meal item 방향의 삭제 cascade는 허용하지 않는다.
- food 참조의 FK·nullability·삭제 제한 방식은 Design에서 확정하되 기존 snapshot 보존을 침해할 수 없다.

## 6. Design에서 확정할 항목

- 최소 meal·meal item schema, user·food FK, meal→item 삭제 cascade, food 삭제 시 기존 snapshot 보존 방식과 필요한 database constraint
- `mealDate` 형식과 허용 범위, 미래 날짜 허용 여부 및 잘못된 날짜의 오류 계약
- 생성 요청 item 수, amount 최대값·decimal scale, snapshot 정밀도·반올림·overflow 처리
- 한 요청에 같은 `foodId`가 중복된 경우의 허용 또는 거절
- API 요청·응답 DTO, 성공 상태·코드, 안전한 오류와 meal 목록·각 meal 내부 item의 결정적 정렬 기준
- snapshot 값·단위의 저장 계약과 현재 food를 조회 응답 값으로 사용하지 않는 경계
- 생성·삭제 transaction 결과, 소유자 조건 조회, meal·item nested 목록을 정확하고 완전하게 조회하는 방식과 불필요한 반복 조회 방지 여부
- 실제 MySQL에서 증명할 대표 성공, rollback, constraint, 독립성·격리 시나리오

클래스명, Bean 구성, 내부 메서드, SQL 문장과 테스트명은 공개 결과를 바꾸지 않는 한 Design에서 고정하지 않는다.

## 7. 성공 및 검증 기준

- 단위 테스트는 snapshot 계산, 반올림 경계, `NULL`, amount·계산 범위를 다룬다.
- 웹 테스트는 인증된 생성·날짜별 조회·삭제, 미인증·대표 입력 오류와 안전한 소유권 응답을 다룬다.
- OpenAPI 테스트는 한국어 목적·입력 제약, Bearer scheme, snapshot·`NULL` 의미와 주요 응답을 확인한다.
- 실제 MySQL 8.4.5 테스트는 fresh Migration, Hibernate validation, 중요 FK·constraint, 성공 transaction, 전체 rollback, food 변경 후 snapshot 불변, 교차 사용자 격리, meal→item 삭제와 food→item 삭제 비전파를 `skipped=0`으로 실행한다.
- Java 또는 테스트 변경 후 `.\gradlew.bat test javadoc`과 `git diff --check`를 통과하고 passed, failed, errors, skipped와 unexecuted 결과를 구분한다.
- 비밀값, 원천 food 행과 내부 예외·database 상세를 문서·로그·응답에 포함하지 않는다.

## 8. 위험과 대응

| 위험 | 영향 | Plan 대응 |
|------|------|-----------|
| 클라이언트가 조작한 이름·영양값을 snapshot으로 저장 | 데이터 무결성 훼손 | 요청은 `mealDate`, `foodId`, `amount`만 받고 서버가 food에서 계산한다. |
| food 변경이 과거 meal 응답을 바꿈 | 이력 훼손 | item snapshot을 저장하고 조회 응답은 현재 food 값으로 대체하지 않는다. |
| food 삭제가 meal item까지 cascade됨 | 과거 식사 손실 | food→item 삭제 전파를 금지하고 구체적인 FK·삭제 제한 방식은 Design에서 확정한다. |
| 누락 영양값이 0으로 합성됨 | 잘못된 영양 기록 | 원천 `NULL`은 계산 결과에서도 `NULL`로 보존한다. |
| 일부 item만 저장됨 | 불완전한 식사 | 생성 전체를 하나의 서비스 transaction으로 묶고 실패 시 rollback한다. |
| 사용자 조건 없는 조회·삭제 | 교차 사용자 노출·변경 | JWT subject를 소유자로 사용하고 모든 사용자 소유 쿼리에 적용한다. |
| meal에서 daily 합계까지 확장 | 기능 경계 확대 | item snapshot까지만 저장하고 집계는 별도 `daily-summary`로 유지한다. |

## 9. 진행 경계

| 단계 | 상태 | 다음 조건 |
|------|------|-----------|
| Plan | 승인 완료 | `MEAL-001` 범위와 인수 시나리오 승인 |
| Design | 대기 | 승인된 Plan을 기준으로 통합 Design 작성 승인 |
| 구현 | 대기 | Design 전체 승인과 별도 구현 승인 |
| Git 게시 | 대기 | 별도 stage·commit·push·PR 승인 |

`MEAL-001`은 Issue #18의 목적, 범위, 인수 시나리오와 Design 검토 항목을 승인한다. 이 승인은 Plan 단계만 완료하며 Design 작성·승인, 구현, stage, commit, push와 Pull Request는 각각 승인된 다음 경계에서 진행한다.

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2026-08-29 | `MEAL-001`로 Meal Plan 전체 승인 |
| 0.2.0 | 2026-08-29 | food 삭제 비전파, mealDate 정책, 로그인 방식 독립성과 nested 목록 조회 경계 보완 |
| 0.1.0 | 2026-08-29 | Issue #18을 기준으로 최초 Meal Plan 초안 작성 |
