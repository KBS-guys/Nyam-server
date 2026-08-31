# daily-summary - Analysis

> **Status**: Complete <br>
> **Date**: 2026-08-31 <br>
> **Plan**: `DAILY-SUMMARY-001` <br>
> **Design**: `DAILY-SUMMARY-002` <br>
> **Related Issue**: [#20](https://github.com/KBS-guys/Nyam-server/issues/20) <br>
> **Match Rate**: 22/22 (100%)

---

## 1. 분석 범위

- 인증 사용자의 요청 날짜별 daily summary API와 공개 오류 계약
- 저장된 meal item snapshot 기반 네 영양소 합계와 strict-null 판정
- JWT subject 소유권, 날짜 조건과 교차 사용자 격리
- group 없는 단일 aggregate query, nullable `SUM`과 projection 불변식
- 공통 meal 날짜 정책, read-only transaction과 Migration 경계
- 단위·Web·OpenAPI·실제 MySQL 8.4.5·전체 회귀 검증

## 2. Design 대조

| # | Design 계약 | 구현·검증 근거 | 결과 |
|---|-------------|----------------|------|
| 1 | `GET /api/v1/daily-summaries?date=YYYY-MM-DD` 단일 조회 API | `DailySummaryController`, Web·OpenAPI 테스트 | Match |
| 2 | 필수 ISO date, `1000-01-01`~`9999-12-31`, 미래 날짜 허용 | `@RequestParam`, `MealDatePolicy`, 단위·Web 테스트 | Match |
| 3 | JWT subject만 소유자로 사용하고 요청·응답에 `userId` 비노출 | `@AuthenticationPrincipal Jwt`, Web·OpenAPI 테스트 | Match |
| 4 | HTTP 200, `DAILY_SUMMARY_RETRIEVED`와 공통 응답 봉투 | `DailySummaryController`, Web·OpenAPI 테스트 | Match |
| 5 | 요청 date, `mealItemCount`와 네 영양소 객체를 항상 반환 | `DailySummaryResponse`, Web·OpenAPI 테스트 | Match |
| 6 | 에너지 `kcal`, 탄수화물·단백질·지방 `g` 단위 | `DailySummaryResponse.from`, Web 테스트 | Match |
| 7 | 빈 날짜는 item 0, 네 합계 0, `complete: true`인 성공 결과 | `DailySummaryService`, 단위·Web·MySQL 테스트 | Match |
| 8 | item이 있는 실제 0 합계는 양수 `mealItemCount`로 빈 날짜와 구분 | 단위·MySQL 0 합계 테스트 | Match |
| 9 | 영양소별 하나라도 `NULL`이면 부분합 대신 null·false | `DailySummaryService.nutrient`, 단위·MySQL 테스트 | Match |
| 10 | 한 영양소의 불완전성이 다른 영양소에 전파되지 않음 | 영양소별 count·sum 변환, 단위·MySQL 테스트 | Match |
| 11 | 현재 food를 join하지 않고 저장 snapshot만 집계 | `MealRepository` JPQL, food 변경 후 MySQL 테스트 | Match |
| 12 | Controller→Service→MealRepository·meal projection 의존 방향 | daily-summary web/service와 meal repository 패키지 구성 | Match |
| 13 | 기존 `MealRepository`에 작은 조회를 두고 공개 DTO와 projection 분리 | `MealRepository`, `DailyNutritionAggregate`, `DailySummaryResult` | Match |
| 14 | 소유자·날짜·meal-item 관계를 query 자체에서 제한 | `aggregateDailyNutrition`의 userId·mealDate 조건과 item join | Match |
| 15 | group 없는 한 query로 item 수, 영양소별 non-null 수와 합계를 조회 | aggregate JPQL과 Hibernate Statistics 1 statement 검증 | Match |
| 16 | `COALESCE` 없이 database `SUM NULL`을 nullable `BigDecimal`로 보존 | JPQL, projection, 빈 날짜·부분합 MySQL 테스트 | Match |
| 17 | projection null, count 범위와 count-sum 불변식 위반을 내부 오류 처리 | `DailySummaryService` 방어 로직과 단위 테스트 | Match |
| 18 | 추가 반올림·scale 축소 없이 완전한 database 합계를 반환 | 서비스 직접 반환과 scale 4 합계 테스트 | Match |
| 19 | meal과 daily summary가 작은 공통 MySQL DATE 정책을 재사용 | `MealDatePolicy`, `MealService`, `DailySummaryService` | Match |
| 20 | read-only service transaction, 단일 statement, 별도 lock·격리 강화 없음 | `@Transactional(readOnly = true)`, Repository·MySQL 테스트 | Match |
| 21 | 새 Entity·table·Migration·저장형 summary·Security matcher·의존성 없음 | Git 변경 범위와 기존 V7·Security 설정 대조 | Match |
| 22 | 한국어 Swagger·JavaDoc과 단위·Web·OpenAPI·실제 MySQL·전체 검증 | daily-summary 12개 테스트와 전체 129개 테스트 증거 | Match |

최종 일치율은 **22/22, 100%**다. 구현 누락, 승인되지 않은 기능 확장, 공개 계약 변경 또는 P1·P2 gap은 확인되지 않았다.

## 3. 자동 검증 증거

| 검증 | 결과 |
|------|------|
| `.\gradlew.bat test javadoc --rerun-tasks` | 성공 |
| 전체 테스트 | 39 suites, 129 passed, 0 failed, 0 errors, 0 skipped, 0 unexecuted |
| daily-summary 단위·Web·OpenAPI | 10 passed, 0 skipped |
| `DailySummaryMySqlIntegrationTest` | 2 passed, MySQL 8.4.5, 0 skipped |
| JavaDoc | 생성 성공, warning 78건, 오류 없음 |
| `git diff --check` | 공백 오류 없음 |
| staged 변경 | 없음 |

위 결과는 2026-08-31 Check에서 Docker Engine 29.6.2의 정상 응답을 확인한 뒤 새로 실행한 결과다. Docker 미가동으로 skip된 과거 중간 실행은 완료 증거에서 제외했으며, 전체 XML과 daily-summary 실제 MySQL XML이 모두 `skipped=0`임을 다시 확인했다.

실제 MySQL 검증은 사용자·날짜 격리, 현재 food 변경 독립성, 영양소별 `SUM NULL` 보존, 빈 날짜와 기록된 0의 구분 및 한 aggregate statement를 포함한다.

## 4. Gap과 관찰

- P1, P2, P3 구현 gap은 확인되지 않았다.
- 새 Migration이나 schema 변경이 없어 V7을 그대로 사용한다.
- 기간 통계, 차트, 추천·진단·치료, cache, social-login과 프론트엔드는 승인된 비범위로 유지됐다.
- JavaDoc warning은 기존 코드와 자명한 생성자·accessor를 포함하며 생성 실패나 승인 계약 누락은 아니다.
- Check 재검증에 사용한 Docker Desktop은 종료하지 않았다.

## 5. Check 결론

`DAILY-SUMMARY-002` 구현 일치율은 첫 Check에서 22/22, 100%다. 수정이 필요한 gap이 없어 Act는 필요하지 않으며 다음 단계는 별도 승인 후 Report 작성이다. 구현, stage, commit, push와 Pull Request 작업은 Check에서 수행하지 않았다.
