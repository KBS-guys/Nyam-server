# food Completion Report

> **Status**: Complete <br>
> **Completion Date**: 2026-08-27 <br>
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
- V5는 Spring Batch 5.2.4의 MySQL 메타데이터를, V6는 `foods` 테이블·제약·검색 인덱스를 생성한다.
- 일반 애플리케이션 시작에서는 Batch Job을 자동 실행하지 않으며 Batch 스키마는 Flyway만 관리한다.
- release date와 checksum으로 Job Instance를 식별하고, 실패 실행은 영속 checkpoint에서 restart하며 완료 동일 입력은 거절한다.
- `source_food_code` 기준 null-safe upsert는 실제 값이 변경된 경우에만 `updated_at`을 갱신한다.
- `GET /api/v1/foods/search`와 `GET /api/v1/foods/{foodId}`를 추가하고 한국어 OpenAPI·안전한 오류 응답을 제공한다.
- source CSV, 원본 행, 로컬 경로와 비밀값은 Git 또는 공개 문서에 포함하지 않았다.

## 3. Check와 Act

승인된 Design을 20개 계약으로 대조한 결과 20/20, 100% 일치했다. 구현 누락, 승인되지 않은 범위 확장과 남은 P1·P2가 없어 Act는 수행하지 않았다.

Design이 현재 문서 지침보다 구현·테스트 방법을 세밀하게 기록한 점은 P3 문서 비례성 관찰로 남겼다. 승인된 역사 문서를 재작성하거나 별도 Design 개정을 만들지는 않았다.

## 4. 검증

| 항목 | 최종 결과 |
|------|-----------|
| `.\gradlew.bat test javadoc --rerun-tasks` | 성공 |
| 전체 테스트 | 31 suites, 96 passed, 0 failed, 0 errors, 0 skipped, 0 unexecuted |
| food 자동 테스트 | 17 passed, 0 skipped |
| `FoodBatchMySqlIntegrationTest` | 3 passed, MySQL 8.4.5, 0 skipped |
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

food는 Plan, Design, Do, Check와 Report까지 완료되었다. Archive, meal 기능, stage, commit, push, Pull Request와 stash 변경은 이 완료 범위에 포함하지 않는다.
