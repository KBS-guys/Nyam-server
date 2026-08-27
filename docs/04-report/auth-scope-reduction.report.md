# auth-scope-reduction Completion Report

> **Status**: Complete <br>
> **Completion Date**: 2026-08-27 <br>
> **Plan**: `AUTH-SCOPE-REDUCTION-001` <br>
> **Design**: `AUTH-SCOPE-REDUCTION-002` <br>
> **Related Issue**: [#15](https://github.com/KBS-guys/Nyam-server/issues/15)

---

## 1. 결과

이메일 인증 흐름을 `인증번호 발송 → email과 verificationCode를 포함한 signup`으로 단순화했다. 별도 confirm, `verificationProof` 발급·저장·전달·소비 상태는 제거했다.

signup 성공 시 challenge의 표시 이메일로 사용자 계정을 만들고 BCrypt 자격 증명과 서버가 정한 동의 3건을 저장한 뒤 challenge를 같은 트랜잭션에서 소비한다. 실패 횟수, 롤백과 실제 MySQL 동시성 결과는 승인된 계약대로 유지된다.

## 2. 변경 영향

- `POST /api/v1/auth/email-verifications`는 유지하고 `/confirm`은 제거했다.
- `POST /api/v1/auth/signup`은 `email`, `verificationCode`, 가입 정보와 동의 boolean을 받는다.
- 성공 응답의 이메일은 signup 입력 표기가 아니라 인증 challenge의 표시 이메일이다.
- V4는 기존 `email_verification_proofs` 테이블을 제거한다. V1~V3는 수정하지 않았다.
- Refresh Token 해시 저장·회전·로그아웃 폐기, 쿠키·CSRF, 동시 단일 승자와 SecurityContext 사용자 격리는 변경하지 않았다.
- Swagger·JavaDoc·OpenAPI 검증은 공개 계약과 민감값 경계에 집중하도록 정리했다.

## 3. Check와 Act

실제 MySQL 최초 동시 발송에서 경쟁 패자가 429 대신 잠금 예외로 종료되는 P2 한 건을 발견했다. Act에서 이를 발송 제한 오류로 변환했고, 패자가 상태나 메일을 변경하지 않는 결과를 다시 검증했다.

최종 Design 대조는 10/10, 100%이며 남은 P1·P2와 후속 관리가 필요한 P3는 없다.

## 4. 검증

| 항목 | 최종 결과 |
|------|-----------|
| `\.\gradlew.bat test javadoc --rerun-tasks` | 성공 |
| 전체 테스트 | 77 passed, 0 failed, 0 errors, 0 skipped |
| 실제 데이터베이스 | MySQL 8.4.5, 신규 설치·V3 업그레이드·트랜잭션·동시성 통과 |
| Mailpit | 발송 번호를 사용한 직접 signup 통과 |
| local-login MySQL | 3 passed, 0 skipped |
| JavaDoc | 성공, 경고 없음 |
| `git diff --check` | 공백 오류 없음 |

## 5. 문서 정합성

새 통합 Plan·Design·Analysis·Report를 변경 범위의 현재 권위 문서로 사용한다. 기존 완료 PDCA 본문은 당시 구현의 역사 기록으로 유지하고, 효력이 바뀐 문서 상단에만 최소 Superseded Notice를 추가했다. 기존 닫힌 Issue는 수정하지 않았다.

## 6. 완료 경계

`auth-scope-reduction`은 한 번의 Act 교정을 거쳐 Report까지 완료되었다. 이 완료에는 stage, commit, push, Pull Request 생성·병합이나 food stash 접근이 포함되지 않는다.
