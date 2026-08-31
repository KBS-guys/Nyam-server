# daily-summary Completion Report

> **Status**: Complete <br>
> **Completion Date**: 2026-08-31 <br>
> **Plan**: `DAILY-SUMMARY-001` <br>
> **Design**: `DAILY-SUMMARY-002` <br>
> **Analysis**: `docs/03-analysis/daily-summary.analysis.md` <br>
> **Related Issue**: [#20](https://github.com/KBS-guys/Nyam-server/issues/20)

---

## 1. 결과

인증 사용자가 요청한 식사 기준 날짜의 저장된 meal item snapshot을 바탕으로 에너지·탄수화물·단백질·지방의 일별 합계와 영양소별 불완전성을 조회하는 서버 수직 흐름을 완성했다.

누락 영양값은 0이나 알려진 값만의 부분합으로 바꾸지 않는다. 특정 영양소가 하나라도 누락되면 그 영양소만 `value: null`, `complete: false`이며, 기록이 없는 날짜는 `mealItemCount: 0`, 네 합계 0과 `complete: true`인 결정적 성공 결과로 반환한다.

## 2. 변경 영향

- `GET /api/v1/daily-summaries?date=YYYY-MM-DD`와 한국어 OpenAPI 계약을 추가했다.
- 소유자는 요청값이 아니라 기존 Bearer JWT의 양의 `BIGINT` subject에서 정한다.
- 응답은 요청 date, `mealItemCount`와 에너지·탄수화물·단백질·지방의 `value`, `unit`, `complete`를 제공한다.
- 기존 `MealRepository`의 group 없는 aggregate query 하나가 사용자·날짜 조건으로 `meal_items` snapshot만 집계한다.
- 전체 item 수, 영양소별 non-null 수와 nullable `SUM`을 함께 사용하여 database `NULL`과 부분합을 서비스에서 구분한다.
- projection의 null·count·sum 불변식 위반은 공개값으로 보정하지 않고 내부 오류로 처리한다.
- meal과 daily-summary가 같은 MySQL `DATE` 범위 정책을 사용하며, 조회 서비스에는 read-only transaction을 적용했다.
- 현재 food를 join하거나 과거 snapshot을 갱신하지 않으며, 집계 결과를 저장하는 Entity·table·Migration을 추가하지 않았다.
- 기간 통계, 차트, 권장·진단·치료, cache, social-login과 프론트엔드는 범위 밖으로 유지했다.

## 3. Check 결과

승인된 `DAILY-SUMMARY-002`의 공개 API, strict-null, 집계 query, 사용자 격리, 날짜 정책, transaction, 문서와 테스트 계약 22개를 구현과 대조한 결과 **22/22, 100%**가 일치했다.

남은 P1·P2·P3 gap은 없으며 수정 iteration이 필요하지 않아 Act는 수행하지 않았다. 단일 aggregate statement, 현재 food 변경 독립성, 사용자·날짜 격리, 빈 날짜와 기록된 0의 구분 및 영양소별 database `SUM NULL` 보존을 실제 MySQL에서 확인했다.

## 4. 검증

| 항목 | 최종 결과 |
|------|-----------|
| Docker Engine | 29.6.2 정상 응답 |
| `.\gradlew.bat test javadoc --rerun-tasks` | 성공 |
| 전체 테스트 | 39 suites, 129 passed, 0 failed, 0 errors, 0 skipped, 0 unexecuted |
| daily-summary 단위·Web·OpenAPI | 10 passed, 0 skipped |
| `DailySummaryMySqlIntegrationTest` | 2 passed, MySQL 8.4.5, 0 skipped |
| JavaDoc | 생성 성공, warning 78건, 오류 없음 |
| `git diff --check` | 공백 오류 없음 |
| Design 일치율 | 22/22, 100% |

Docker 미가동으로 skip된 과거 중간 결과는 완료 증거에서 제외했다. 최종 Check는 Docker Engine 준비를 확인한 뒤 전체 작업을 새로 실행했으며, 전체 XML과 daily-summary 실제 MySQL XML 모두 `skipped=0`이다.

## 5. 배운 점

### Keep

- 과거 영양 기록은 현재 food가 아니라 저장된 item snapshot을 source of truth로 유지한다.
- nullable 집계는 전체 item 수, 값 존재 수와 database 합계를 함께 사용해 부분합 노출을 방지한다.
- 사용자 소유 데이터는 JWT principal과 repository query 조건에서 함께 격리한다.
- Testcontainers 완료는 Build 성공 문구가 아니라 실제 XML의 `skipped=0`으로 확인한다.

### Problem

- Docker Desktop이 꺼진 상태에서는 Testcontainers가 skip되어도 Gradle 자체는 성공할 수 있었다.
- 초기 OpenAPI 문서에서 내부 JWT principal이 query parameter로 노출됐고, 이를 숨긴 뒤 계약 테스트로 고정했다.
- OpenAPI 3.1의 nullable 숫자는 `nullable: true`가 아니라 `type: ["number", "null"]`로 표현되어 버전에 맞는 검증이 필요했다.

### Try

- 실제 MySQL 검증 전 Docker Engine의 정상 응답을 먼저 확인하고 XML 합계를 항상 함께 기록한다.
- 후속 API도 framework 내부 parameter와 nullable 공개 필드가 생성된 OpenAPI에 정확히 반영되는지 직접 검증한다.
- 다음 기능은 Foundation 순서에 따라 social-login을 별도 Issue와 PDCA 범위로 시작한다.

## 6. 완료 경계와 다음 단계

daily-summary는 Plan, Design, Do, Check와 Report를 완료했고 match rate는 100%다. Act는 필요하지 않았으며 Archive는 수행하지 않는다.

다음 Git 단계는 별도 승인에 따른 승인 경로의 stage, commit, push와 Issue #20을 연결하는 Pull Request 생성이다. Pull Request merge, Issue 종료, 브랜치 삭제와 이후 social-login 시작도 각각 승인 경계를 유지한다.
