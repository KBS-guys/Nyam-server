# auth-scope-reduction Analysis

> **Status**: Complete <br>
> **Date**: 2026-08-27 <br>
> **Plan**: `AUTH-SCOPE-REDUCTION-001` <br>
> **Design**: `AUTH-SCOPE-REDUCTION-002` <br>
> **Related Issue**: [#15](https://github.com/KBS-guys/Nyam-server/issues/15)

---

## 1. 분석 범위

- 이메일 인증번호 발송, 직접 signup, 로컬 로그인 회귀
- V4 forward Migration의 기존 V3 업그레이드와 신규 설치 결과
- signup 원자성, 인증번호 실패 횟수, 발송·가입 경쟁 결과
- Swagger·JavaDoc·민감값 비노출과 변경된 문서의 Superseded Notice
- 실제 MySQL 8.4.5, Mailpit, `test javadoc`, `git diff --check`

## 2. 최초 Check와 Act

Docker가 꺼진 최초 진단 실행에서는 Testcontainers 테스트 14건이 skip되어 완료 근거로 인정하지 않았다. Docker Desktop을 시작하고 실제 MySQL·Mailpit 검증을 다시 수행했다.

실제 MySQL의 challenge 없는 동시 최초 발송에서 경쟁 패자가 승인된 429가 아니라 잠금 획득 예외로 종료되는 P2 한 건을 확인했다. Act에서 이 잠금 경쟁 실패를 `EMAIL_VERIFICATION_SEND_LIMITED`로 변환했다. 패자는 challenge, 재전송 횟수와 메일을 변경하지 않는다. 구체적인 SQL이나 Repository 계약은 추가하지 않았다.

교정 후 대상 MySQL 테스트와 전체 검증을 다시 실행했다.

## 3. PR #16 리뷰 Check와 두 번째 Act

PR 리뷰에서 P2 두 건을 추가로 확인했다.

- 발송 트랜잭션의 challenge 잠금 전 일반 사용자 조회가 REPEATABLE READ snapshot을 만들 수 있었다. 선행 조회를 제거하고 challenge locking read 뒤의 사용자 조회를 첫 consistent read로 만들었다. signup이 잠금을 잡은 채 발송을 대기시킨 실제 MySQL 경쟁에서 signup commit 후 발송은 409 `EMAIL_ALREADY_REGISTERED`, 메일 0건, challenge 0건으로 끝났다.
- proof 제거와 무관하게 유지되어야 할 challenge 범위·시각 CHECK, 사용자 canonical email UNIQUE, 사용자 삭제 시 자격 증명·동의 CASCADE 검증이 축소 과정에서 빠졌다. 네 CHECK와 UNIQUE·두 CASCADE를 하나의 실제 MySQL invariant 테스트로 복원했다.

두 항목은 공개 API나 Migration을 바꾸지 않고 교정했으며 전체 검증을 다시 실행했다.

## 4. 최종 Design 대조

| # | 계약 | 구현·검증 근거 | 결과 |
|---|------|----------------|------|
| 1 | 발송 API 유지, confirm과 proof 공개 계약 제거 | Controller, Security, OpenAPI 구조 검증 | Match |
| 2 | signup이 email과 6자리 code를 직접 검증하고 challenge 표시 이메일 반환 | 요청·서비스·웹·Mailpit 흐름 | Match |
| 3 | 연령·동의·비밀번호 오류 경계와 서버 결정 동의 3건·버전 1.0 유지 | 정책·웹·MySQL 테스트 | Match |
| 4 | V1~V3 보존, V4에서 proof 테이블만 제거하고 기존 CHECK·UNIQUE·CASCADE 유지 | 신규 설치·V3 업그레이드·invariant MySQL 테스트 | Match |
| 5 | 불일치 횟수 커밋, 다섯 번째 429, 성공 시 challenge 일회성 소비 | 서비스·웹·MySQL 테스트 | Match |
| 6 | 사용자·BCrypt 자격 증명·동의·challenge 소비 원자성 | 저장 실패 롤백 MySQL 테스트 | Match |
| 7 | 동일 번호 signup 단일 성공, 발송·signup 및 최초 발송 경쟁 결과 | signup 우선 commit을 포함한 실제 MySQL 동시성 테스트 | Match |
| 8 | 메일 실패 시 challenge 롤백, 승인된 SMTP 모호성 경계 유지 | Mailpit 및 메일 실패 MySQL 테스트 | Match |
| 9 | Access/Refresh Token, 쿠키, CSRF, 회전·폐기와 단일 승자 회귀 없음 | local-login 전체·MySQL 테스트 | Match |
| 10 | 민감값 비노출, 간결한 Swagger·JavaDoc, 필요한 역사 문서 Notice | OpenAPI·로그·JavaDoc·문서 검토 | Match |

최종 일치율은 10/10, 100%다. 남은 P1·P2는 없다.

## 5. 최종 검증 근거

| 검증 | 결과 |
|------|------|
| `.\gradlew.bat test javadoc --rerun-tasks` | `BUILD SUCCESSFUL` |
| 전체 테스트 | 79 passed, 0 failed, 0 errors, 0 skipped |
| V3 → V4 업그레이드 | 1 passed, MySQL 8.4.5 |
| user-registration MySQL | 5 passed, 0 skipped |
| email-verification MySQL | 6 passed, 0 skipped |
| Mailpit 직접 signup | 1 passed, 0 skipped |
| local-login MySQL 회귀 | 3 passed, 0 skipped |
| JavaDoc | 성공, 경고 없음 |
| `git diff --check` | 공백 오류 없음 |

## 6. 문서와 미실행 범위

- 기존 완료 문서 본문은 다시 쓰지 않았다. 효력이 바뀐 user-registration·email-verification 각 4개 문서와 local-login Plan 상단에만 `AUTH-SCOPE-REDUCTION-002` Notice를 추가했다.
- 기존 닫힌 Issue 본문, V1~V3, food 코드·문서와 `stash@{0}`은 변경하지 않았다.
- PWA, production SMTP, 다중 인스턴스와 그 밖의 Design 제외 범위는 실행하지 않았다.
- 별도 후속 Issue가 필요한 남은 P3는 확인되지 않았다.

## 7. 결론

최초 Check의 P2 한 건과 PR 리뷰 P2 두 건은 두 번의 Act에서 교정되었고 재검증을 통과했다. 구현은 승인된 Design과 일치하며 남은 P1·P2는 없다.
