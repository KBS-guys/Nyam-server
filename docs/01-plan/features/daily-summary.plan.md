# daily-summary - Plan Document

> **Summary**: 인증 사용자의 요청 날짜별 meal item snapshot 주요 영양 합계와 불완전성 조회
>
> **Version**: 1.0.0 <br>
> **Date**: 2026-08-30 <br>
> **Status**: Approved <br>
> **Decision**: `DAILY-SUMMARY-001` <br>
> **Related Issue**: [#20](https://github.com/KBS-guys/Nyam-server/issues/20) <br>
> **Scope Authority**: `FOUNDATION-006`, `FOUNDATION-006-R2`, `LOCAL-LOGIN-002`, `FOOD-002`, `MEAL-002`

---

## 1. 목적

인증 사용자가 요청한 식사 기준 날짜의 저장된 meal item snapshot을 바탕으로 에너지·탄수화물·단백질·지방의 일별 합계와 영양소별 불완전성을 조회하는 하나의 서버 수직 흐름을 완성한다.

집계는 현재 food를 다시 읽거나 별도 합계 데이터를 저장하지 않는다. 누락된 영양값은 0 또는 알려진 값만의 부분합으로 표현하지 않으며, 기록이 없는 날짜와 실제 영양 합계가 0인 날짜는 `mealItemCount`로 구분한다.

## 2. 현재 기준선

- Bearer Access Token의 양의 `BIGINT` subject가 보호 API의 내부 사용자 식별자다.
- `meals.meal_date`는 서버 현재 날짜가 아니라 사용자가 지정한 MySQL `DATE` 범위의 식사 기준 날짜다.
- `meal_items`는 기록 시점의 에너지·탄수화물·단백질·지방을 nullable `DECIMAL(16,4)` snapshot으로 보존한다.
- meal 목록은 현재 food가 아닌 저장 snapshot을 사용하며 사용자와 날짜로 격리된다.
- V7은 사용자·날짜 조회 인덱스와 meal→item 관계를 이미 제공한다.
- daily summary 코드, 별도 schema, Design과 테스트는 아직 없다.

## 3. 목표와 비목표

### 3.1 목표

- 인증 사용자와 요청 날짜에 속한 meal item만 집계한다.
- 네 영양소 합계, 단위와 영양소별 `complete`를 반환한다.
- 집계 대상 item 수를 `mealItemCount`로 반환한다.
- 저장 snapshot의 `NULL`을 영양소별 strict-null 규칙으로 공개한다.
- 기록이 없는 날짜도 결정적인 성공 응답으로 반환한다.
- 실제 MySQL과 한국어 Swagger를 포함한 대표 검증으로 결과를 증명한다.

### 3.2 비목표

- `daily_summaries` 테이블, meal·daily 합계 column과 집계값 중복 저장
- 현재 food 값으로 과거 합계를 재계산하는 동작
- 알려진 값만 더한 부분합, meal·food별 breakdown과 누락 item 상세
- 기간별·주간·월간 통계, 차트와 목표 대비 분석
- 영양 권장·진단·치료 기능
- social-login, 프론트엔드와 PWA
- cache, 비동기·batch 사전 집계, monitoring, benchmark와 운영·확장 기능

## 4. 범위

### 4.1 포함

- `GET /api/v1/daily-summaries?date=YYYY-MM-DD` 후보의 조회 API 1개
- 기존 meal 계약과 같은 날짜 형식·범위 및 미래 날짜 허용
- SecurityContext의 JWT subject만 사용하는 소유자 결정
- `userId + mealDate`로 제한된 저장 meal item snapshot 집계
- 에너지 `kcal`, 탄수화물·단백질·지방 `g` 단위
- 영양소별 합계와 `complete`, 최상위 `mealItemCount`
- 빈 날짜의 `200 DAILY_SUMMARY_RETRIEVED` 후보 성공 결과
- 안전한 입력·인증 오류와 공통 `ApiResponse` 봉투
- 한국어 Swagger와 단위 테스트·웹 테스트·OpenAPI 테스트·실제 MySQL 검증

정확한 공개 API 필드, 성공 코드, 집계 query와 transaction은 통합 Design에서 확정한다.

### 4.2 제외

- current food join 또는 meal item snapshot 갱신
- 새 집계 Entity, table, Migration과 집계 결과를 저장하는 별도 persistence model
- 날짜 범위 조회, pagination, 정렬 옵션과 비교 통계
- 영양소 전체를 한 번에 불완전 처리하는 정책
- JSON 숫자의 후행 0 또는 고정 소수점 문자열 계약
- idempotency, locking, 동시 요청 순서 보장과 성능 시험
- Redis, event, scheduler와 분산 처리

## 5. 인수 시나리오

### A. 완전한 일별 합계

- 인증 사용자의 요청 날짜에 속한 모든 meal item을 집계한다.
- 모든 item에 특정 영양소 snapshot이 있으면 해당 영양소의 정확한 합계와 `complete: true`를 반환한다.
- 합산은 저장 snapshot의 정밀도를 훼손하는 추가 반올림을 하지 않는다.

### B. 영양소별 불완전성

- 하나 이상의 item에서 특정 영양소 snapshot이 `NULL`이면 그 영양소는 `value: null`, `complete: false`다.
- 알려진 값만의 부분합을 `value`로 반환하지 않는다.
- 한 영양소의 누락은 다른 영양소의 합계와 `complete`에 영향을 주지 않는다.
- `complete`는 모든 집계 item에 해당 snapshot이 존재한다는 뜻이며 원본 데이터의 정확성이나 영양학적 완전성을 뜻하지 않는다.

### C. 기록이 없는 날짜

- 집계 대상 item이 없으면 HTTP 200과 `mealItemCount: 0`을 반환한다.
- 각 영양소는 빈 집합의 합인 `value: 0`, `complete: true`로 표현한다.
- item이 존재하고 특정 영양소 snapshot이 모두 0이면 `mealItemCount`는 0보다 크고, 해당 영양소는 `value: 0`, `complete: true`로 반환한다.
- 별도 not-found 오류나 빈 summary row 저장은 사용하지 않는다.

### D. 날짜와 사용자 격리

- 요청 날짜는 서버 오늘·생성 시각·사용자 시간대가 아니라 저장된 `mealDate`와 비교한다.
- 같은 날짜의 다른 사용자 item과 다른 날짜의 현재 사용자 item은 제외한다.
- 다른 사용자에게 기록이 있더라도 현재 사용자에게 item이 없으면 빈 날짜 결과만 반환한다.
- food 이름이나 영양값이 나중에 변경돼도 저장 snapshot 기반 결과는 바뀌지 않는다.

## 6. Design에서 확정할 항목

- 조회 API의 최종 path, query parameter, 공통 봉투와 성공 코드
- `date`, `mealItemCount`와 네 영양소 응답 구조 및 OpenAPI 설명
- MySQL `DATE` 범위 검증과 안전한 입력 오류 계약
- 전체 item 수, 영양소별 non-null item 수와 합계를 이용한 strict-null 판정
- 소유자·날짜 제한 집계 query, projection과 Repository 책임 및 불필요한 반복 조회 방지
- read-only transaction 적용 여부와 집계 query의 서비스 경계
- 빈 날짜와 nullable 합계의 MySQL 결과를 공개 계약으로 변환하는 방식
- 실제 MySQL에서 증명할 집계·격리·snapshot 독립성 시나리오

SQL 문장, projection·DTO 클래스명, 내부 메서드와 테스트명은 공개 결과를 바꾸지 않는 한 Design에서 고정하지 않는다.

## 7. 성공 및 검증 기준

- 단위 테스트는 완전 합계, 영양소별 strict-null, 빈 날짜와 추가 반올림 금지를 다룬다.
- 웹 테스트는 JWT subject, 요청 날짜, 빈 날짜, 대표 입력 오류와 미인증 응답을 다룬다.
- OpenAPI 테스트는 한국어 목적·Bearer 보안·날짜 제약·`mealItemCount`와 `complete` 의미를 확인한다.
- 실제 MySQL 8.4.5 테스트는 사용자·날짜 격리, nullable snapshot 집계, 빈 날짜와 food 변경 후 독립성을 `skipped=0`으로 실행한다.
- Java 또는 테스트 변경 후 `.\gradlew.bat test javadoc`과 `git diff --check`를 통과한다.
- passed, failed, errors, skipped와 unexecuted 결과를 구분해 보고한다.
- 비밀값, 원천 food 행, 내부 예외와 database 상세를 문서·로그·응답에 포함하지 않는다.

## 8. 위험과 대응

| 위험 | 영향 | Plan 대응 |
|------|------|-----------|
| SQL `SUM`이 `NULL`을 무시해 부분합을 완전한 합계로 반환 | 잘못된 영양 정보 | 전체 item 수와 영양소별 값 존재 수를 함께 판정한다. |
| `COUNT(*)`가 실제 item 수와 다른 결과를 만듦 | 빈 날짜 의미 왜곡 | 집계 대상 item 식별자를 기준으로 `mealItemCount`를 계산한다. |
| 현재 food join으로 과거 합계가 변경 | 기록 이력 훼손 | 저장된 meal item snapshot만 집계한다. |
| 사용자 조건 누락 | 교차 사용자 데이터 노출 | JWT subject와 날짜를 모든 집계 조건에 적용한다. |
| 빈 날짜와 실제 0 합계 혼동 | API 의미 불명확 | 최상위 `mealItemCount`를 반환한다. |
| summary 저장·기간 통계로 범위 확장 | 구현 복잡도 증가 | 요청 시 단일 날짜 집계만 유지한다. |

## 9. 진행 경계

| 단계 | 상태 | 다음 조건 |
|------|------|-----------|
| Issue | 완료 | Issue #20 생성 및 범위 확인 |
| Plan | 승인 완료 | `DAILY-SUMMARY-001` 승인 |
| Design | 대기 | 승인된 Plan 기준 통합 Design 작성 승인 |
| 구현 | 대기 | Design 전체 승인과 별도 구현 승인 |
| Git 게시 | 대기 | 별도 stage·commit·push·PR 승인 |

`DAILY-SUMMARY-001`은 Issue #20의 목적, 범위, 인수 시나리오와 Design 검토 항목을 승인한다. 이 승인은 Plan 단계만 완료하며 Design 작성·PDCA Design 전환·구현·stage·commit·push와 Pull Request를 승인하지 않는다.

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2026-08-30 | `DAILY-SUMMARY-001`로 Plan 전체 승인; Design 전환과 구현·Git 게시는 미승인 |
| 0.2.0 | 2026-08-30 | Repository 선택을 Design에 남기고 실제 0 합계와 단순한 query·transaction 경계를 보완 |
| 0.1.0 | 2026-08-30 | Issue #20을 기준으로 최초 daily-summary Plan 초안 작성 |
