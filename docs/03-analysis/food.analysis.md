# food - Analysis

> **Status**: Complete <br>
> **Date**: 2026-08-27 <br>
> **Plan**: `FOOD-001` <br>
> **Design**: `FOOD-002` <br>
> **Related Issue**: [#14](https://github.com/KBS-guys/Nyam-server/issues/14) <br>
> **Match Rate**: 20/20 (100%)

---

## 1. 분석 범위

- 수동 Spring Batch 실행 경계와 일반 API 시작 격리
- Flyway 기반 Batch 메타데이터 및 `foods` 스키마
- CSV preflight, streaming Reader, Processor, JDBC batch upsert
- chunk rollback, Job identity, checkpoint restart와 rerun
- 인증된 식품명 접두사 검색과 상세 조회
- 단위·웹·OpenAPI·실제 MySQL·전체 원본 CSV 검증

## 2. Design 대조

| # | Design 계약 | 구현·검증 근거 | 결과 |
|---|-------------|----------------|------|
| 1 | 전체 원본을 수동 Job으로 적재하고 일반 API 시작과 분리 | `foodImport` Gradle task, `FoodImportApplication`, `food-import` profile, 일반 시작 시 Job 미실행 MySQL 테스트 | Match |
| 2 | UTF-8 BOM과 정확한 45개 헤더·필드 매핑 | `FoodCsvSchema`, `FoodCsvFileSupport`, `FoodCsvRow`, CSV 계약 테스트 | Match |
| 3 | 입력 경로·release date·checksum을 요구하되 경로는 비영속 처리 | `FoodImportRunner`, `FoodImportInput`, 정확히 두 식별 Parameter 검증 | Match |
| 4 | 영속 JDBC JobRepository와 지정 JDBC Batch transaction manager 사용 | `FoodBatchInfrastructureConfiguration`, Job·Step 구성, 실제 MySQL의 영속 Repository 검증 | Match |
| 5 | Flyway가 Batch 메타데이터를 만들고 Batch 자동 초기화를 금지 | V5 Migration, `spring.batch.jdbc.initialize-schema=never`, Hibernate validation | Match |
| 6 | 최소 `foods` 스키마와 외부 코드·유형·기준량·단위·음수 방지 제약 | V6 Migration, `Food` JPA mapping, 실제 MySQL 제약 검증 | Match |
| 7 | `BigDecimal` scale 4, 무반올림, 공란 `NULL`, 잘못된 숫자 fail-fast | `FoodImportProcessor`, `FoodCsvContractTest`, MySQL nullable·정밀도 검증 | Match |
| 8 | 쓰기 전 파일·checksum·헤더·release date preflight | `FoodImportPreflightTasklet`, 매 실행 preflight Step | Match |
| 9 | 전체 행을 보유하지 않는 streaming Reader와 구조 오류 실패 | `FoodCsvItemReader`, `FoodCsvParser`, quoted field·multiline·field-count 테스트 | Match |
| 10 | 외부 코드·유형·식품명·기준량 검증과 검색명 정규화 | `FoodImportProcessor`, `FoodNameNormalizer`, 단위 테스트 | Match |
| 11 | JDBC batch upsert, null-safe 비교, 변경 시에만 `updated_at` 갱신 | `FoodImportJobConfiguration` Writer SQL과 실제 MySQL no-op upsert 검증 | Match |
| 12 | chunk 500, 실패 chunk rollback, 앞선 commit 유지 | chunk Step 구성과 강제 Writer 실패 MySQL 테스트 | Match |
| 13 | `skipLimit=0`, 정책상 filter 없음, 실행 count와 상태 관찰 | 기본 fail-fast Step, `read/write/filter/skip/commit/rollback` 검증 | Match |
| 14 | release date와 checksum으로 Job Instance 식별 | 식별 Job Parameters와 동일·상이 입력 MySQL 테스트 | Match |
| 15 | 동일 실패 입력의 persisted checkpoint restart | `ExecutionContext` Reader 위치 저장·복원, 두 번째 chunk 실패·재시작 직접 검증 | Match |
| 16 | 완료 동일 입력 거절, 새 release·checksum 실행, 외부 코드 upsert | 완료 입력 거절과 새 release/새 checksum MySQL 테스트 | Match |
| 17 | 검색·상세 API에 기존 Bearer 인증 적용 | `FoodController` 보안 계약과 unauthorized 웹 테스트 | Match |
| 18 | 정규화 prefix 검색, 최대 20건, 결정적 정렬, 안전한 상세·오류·단위 | `FoodQueryService`, `FoodRepository`, 응답 DTO, Controller 테스트 | Match |
| 19 | 한국어 OpenAPI와 대표 단위·웹·실제 MySQL 자동 검증 | food 테스트 17건과 전체 96건, 실패·오류·skip 없음 | Match |
| 20 | 317,766건 전체 원본 수동 실행과 비밀·원문·로컬 경로 비보존 | 전체 Job·Step 완료, DB 행·외부 코드 유일성·nullable count 확인, Git 변경 경로 점검 | Match |

최종 일치율은 **20/20, 100%**다. 구현 누락, 승인되지 않은 기능 확장 또는 Design과 다른 공개 계약은 확인되지 않았다.

## 3. 자동 검증 결과

| 검증 | 결과 |
|------|------|
| `.\gradlew.bat test javadoc --rerun-tasks` | 성공 |
| 전체 테스트 | 31 suites, 96 passed, 0 failed, 0 errors, 0 skipped, 0 unexecuted |
| food 단위·서비스·웹·OpenAPI | 14 passed, 0 skipped |
| `FoodBatchMySqlIntegrationTest` | 3 passed, MySQL 8.4.5, 0 skipped |
| 다른 실제 MySQL·Mailpit 회귀 | 16 passed, 0 skipped |
| JavaDoc | 성공 |
| `git diff --check` | 공백 오류 없음 |

실제 MySQL 검증은 V1부터 V6까지의 fresh migration, Hibernate validation, Batch metadata 영속화, nullable·UNIQUE·CHECK 제약, chunk rollback, checkpoint restart와 완료 입력 거절을 포함한다.

## 4. 전체 원본 수동 실행

- 승인된 2026-06-26 원본 87,197,361 bytes를 임시 MySQL 8.4.5에서 실행했다.
- validation Step과 chunk Step, 최종 Job은 모두 `COMPLETED`였다.
- `readCount=317,766`, `writeCount=317,766`, `filterCount=0`, 모든 skip count는 0이었다.
- validation Step은 commit 1·rollback 0, chunk Step은 commit 636·rollback 0이었다.
- 최종 `foods`와 고유 `source_food_code`는 각각 317,766건이었다.
- `energy` NULL 0건, `carbohydrate` NULL 12,758건, `protein` NULL 0건, `fat` NULL 13,300건으로 공란과 숫자 0의 의미가 분리됐다.
- 실행 시간은 약 7분 28초였다.
- release date와 checksum 일치, 영속 JobExecution 및 StepExecution 상태를 확인했다. 원본 행, 실제 checksum 값과 로컬 경로는 이 문서에 복사하지 않았다.
- 검증용 컨테이너는 제거했고 Docker Desktop은 검증 전 상태로 되돌렸다.

## 5. Gap과 비차단 관찰

- 남은 P1·P2 구현 gap은 없다.
- 승인된 Design은 현재 문서 작성 지침보다 구현·테스트 방법을 상세히 고정하지만, 이는 동작 불일치가 아닌 P3 문서 비례성 관찰이다. 승인된 역사 문서를 재작성하거나 별도 Design 개정을 만들지 않는다.
- Flyway는 MySQL 8.4가 당시 공식 시험 상한 8.1보다 새 버전이라는 경고를 출력했다. 실제 기준 버전 MySQL 8.4.5의 migration과 모든 적용 테스트는 통과했으므로 차단 gap으로 보지 않는다.
- 정확한 최대 메모리 계측은 승인된 완료 조건이 아니며 수행하지 않았다. streaming 구조와 전체 원본 완료 결과로 메모리 전체 적재 금지를 검증했다.

## 6. Check 결론

`FOOD-002`의 구현 일치율은 100%이고 Act가 필요한 gap은 없다. food Check는 완료되었으며 다음 단계는 별도 승인 후 Report 작성이다. stage, commit, push, Pull Request와 stash 변경은 이 Check 범위에 포함하지 않는다.
