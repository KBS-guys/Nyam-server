# food Completion Report

> **Status**: Complete <br>
> **Completion Date**: 2026-08-29 <br>
> **Plan**: `FOOD-001` <br>
> **Design**: `FOOD-002` <br>
> **Analysis**: `docs/03-analysis/food.analysis.md` <br>
> **Related Issue**: [#14](https://github.com/KBS-guys/Nyam-server/issues/14)

---

## 1. 결과

승인된 공공 식품 CSV를 일반 API 시작과 분리된 수동 Spring Batch Job으로 적재하는 흐름을 완성했다. Reader는 파일을 streaming하고, Processor는 외부 식품 코드·이름·기준량·주요 영양값을 검증하며, JDBC batch Writer는 500건 chunk로 MySQL에 upsert한다.

적재된 데이터는 기존 Bearer 인증을 요구하는 식품명 prefix 검색과 상세 조회에 연결된다. 누락된 영양값은 숫자 0과 구분되는 `NULL`로 보존되고 API에서도 명시적인 단위와 함께 반환된다.

## 2. 변경 영향

- Spring Batch 의존성과 수동 `foodImport` 실행 task를 추가했다.
- V5는 Spring Batch 5.2.4의 MySQL 메타데이터를, V6는 `foods` 테이블·제약·검색 인덱스와 이름 컬럼의 exact binary collation을 생성한다.
- 추적되는 안전 기본 설정으로 일반 애플리케이션의 Batch Job 자동 실행과 Batch 스키마 자동 초기화를 금지하고, 스키마는 Flyway만 관리한다.
- preflight는 checksum뿐 아니라 전체 파일을 strict UTF-8로 끝까지 검사해 지원하지 않는 인코딩을 food 쓰기 전에 거절한다.
- release date와 checksum으로 Job Instance를 식별하고, 실패 실행은 영속 checkpoint에서 restart하며 완료 동일 입력은 거절한다.
- `source_food_code` 기준 null-safe upsert는 case·accent 차이를 포함한 실제 값이 변경된 경우에만 `updated_at`을 갱신한다.
- 이름 검색은 애플리케이션의 NFKC·공백·소문자 정규화 결과를 binary로 비교해, 별도로 제거하지 않은 accent 차이를 임의로 동일시하지 않는다.
- `GET /api/v1/foods/search`와 `GET /api/v1/foods/{foodId}`를 추가하고 한국어 OpenAPI·안전한 오류 응답을 제공한다.
- source CSV, 원본 행, 로컬 경로와 비밀값은 Git 또는 공개 문서에 포함하지 않았다.

## 3. Check와 Act

초기 완료 뒤 PR #17 리뷰에서 전체 파일 UTF-8 preflight P2와 경로 로그·추적 설정 P3 두 건을 확인해 두 번째 iteration의 Act를 수행했다. 전체 strict UTF-8 preflight, malformed byte 실제 MySQL 회귀 테스트, I/O cause 경로 비노출과 추적 가능한 Batch 기본 설정을 반영했다.

후속 재검토에서는 MySQL 기본 collation 때문에 case·accent-only 이름 차이가 변경 감지와 검색에서 사라질 수 있는 P2를 확인해 세 번째 iteration의 Act를 수행했다. 두 이름 컬럼을 `utf8mb4_0900_bin`으로 고정하고 실제 MySQL에서 collation, `ABC`→`abc` 변경 시 `updated_at` 증가와 `cafe`/`café` 검색 분리를 검증했다.

Act 후 승인된 Design 20개 계약을 다시 대조한 결과 20/20, 100%이며 남은 P1·P2는 없다.

Design이 현재 문서 지침보다 구현·테스트 방법을 세밀하게 기록한 점은 P3 문서 비례성 관찰로 남겼다. 승인된 역사 문서를 재작성하거나 별도 Design 개정을 만들지는 않았다.

## 4. 검증

| 항목 | 최종 결과 |
|------|-----------|
| `.\gradlew.bat test javadoc --rerun-tasks` | 성공 |
| 전체 테스트 | 31 suites, 99 passed, 0 failed, 0 errors, 0 skipped, 0 unexecuted |
| food 자동 테스트 | 20 passed, 0 skipped |
| `FoodBatchMySqlIntegrationTest` | 5 passed, MySQL 8.4.5, 0 skipped |
| 다른 실제 MySQL·Mailpit 회귀 | 16 passed, 0 skipped |
| JavaDoc | 성공 |
| `git diff --check` | 공백 오류 없음 |
| Design 일치율 | 20/20, 100% |

Flyway는 MySQL 8.4가 당시 시험된 상한 8.1보다 새 버전이라는 경고를 출력했지만, 기준 버전 MySQL 8.4.5의 fresh migration, Hibernate validation과 모든 적용 테스트가 통과했다.

## 5. 전체 원본 실행

- 승인된 2026-06-26 원본 317,766건을 임시 MySQL 8.4.5에 적재했다.
- validation Step, chunk Step과 최종 Job은 모두 `COMPLETED`였다.
- `readCount=317,766`, `writeCount=317,766`, `filterCount=0`, 모든 skip count는 0이었다.
- chunk Step은 commit 636·rollback 0이었고 최종 `foods` 및 고유 외부 식품 코드는 각각 317,766건이었다.
- 누락 영양값 분포를 확인해 공란이 `NULL`로 보존되는 것을 검증했다.
- 실행 시간은 약 7분 28초였으며 검증용 컨테이너는 제거했다.

## 6. 완료 경계

food는 두 차례의 PR 리뷰 Act와 세 번째 iteration 재검증을 거쳐 Plan, Design, Do, Check와 Report까지 다시 완료되었다. Archive, meal 기능, stage, commit, push, Pull Request 업데이트와 stash 변경은 이 완료 범위에 포함하지 않는다.
