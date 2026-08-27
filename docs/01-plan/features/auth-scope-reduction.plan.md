# auth-scope-reduction - Plan Document

> **Summary**: 이메일 인증번호 발송부터 회원가입과 반복 로그인까지의 보안 경계는 유지하면서 `verificationProof` 중간 단계를 제거하고 인증 문서와 공개 계약을 간결하게 정리한다. <br>
> **Project**: Nyamlog <br>
> **Version**: 1.0.0 <br>
> **Author**: Project decision record <br>
> **Date**: 2026-08-27 <br>
> **Status**: Approved <br>
> **Decision**: `AUTH-SCOPE-REDUCTION-001` <br>
> **Related Issue**: [#15](https://github.com/KBS-guys/Nyam-server/issues/15)

---

## 1. 목적

현재 로컬 인증 흐름은 이메일 인증번호 확인 후 `verificationProof`를 발급하고, 별도 회원가입 요청이 그 proof를 소비한다. 이 구조는 안전하게 동작하지만 사용자에게 중간 값을 전달하고 동일한 계약을 코드·Swagger·JavaDoc·테스트·PDCA 문서에서 반복한다.

이 작업은 다음 사용자 흐름을 한 명의 신입 백엔드 개발자가 설명하고 검증할 수 있는 범위로 단순화한다.

```text
인증번호 발송
  -> email과 verificationCode를 포함한 회원가입
  -> 원자적 계정 생성
  -> 로그인
  -> 보호 API 사용
  -> Refresh Token 회전
  -> 로그아웃과 서버 측 폐기
```

줄 수나 파일 수 감축은 완료 목표가 아니다. 중간 상태와 반복 계약을 제거하면서 사용자 경험, 보안, 데이터 정합성, 반복 PWA 로그인을 유지하는 것이 목표다.

## 2. 현재 기준선

- `user-registration`은 `verificationProof`에서 이메일을 복원해 사용자, 로컬 자격 증명, 동의를 한 트랜잭션으로 저장한다.
- `email-verification`은 Mailpit으로 인증번호를 발송하고 별도 confirm 요청에서 proof를 발급한다.
- V1은 사용자·자격 증명·동의·proof 테이블, V2는 인증 challenge 테이블, V3는 Refresh Token 테이블을 생성한다.
- `local-login`은 Access/Refresh Token, 쿠키, CSRF, 회전, 로그아웃, 동일 이전 Refresh Token의 동시 단일 승자 계약까지 완료되어 있다.
- 세 완료 기능의 문서와 공개 설명에는 당시 구현에 필요했던 proof 및 세부 계약이 각각 기록되어 있다.

## 3. 범위

### 3.1 포함

- 인증번호 발송 후 signup이 `email`과 `verificationCode`를 직접 검증하는 흐름
- 별도 인증번호 confirm API와 `verificationProof` 발급·전달·소비 상태 제거
- proof 전용 영속 상태와 사용되지 않는 관련 코드 제거
- 필수 동의를 boolean 입력으로 단순화하고 동의 종류와 버전을 서버가 결정하는 계약
- 인증번호 확인, 계정·자격 증명·동의 저장, 인증 상태 소비의 원자적 결과
- 기존 V1~V3를 수정하지 않는 forward Flyway Migration
- Swagger·JavaDoc은 승인된 AGENTS.md 기준으로 정리하고, OpenAPI 테스트는 경로·필드·상태 코드·보안 scheme·민감값 비노출을 검증
- 제거된 동작의 테스트만 정리하고 보안·데이터 정합성·로그인 회귀 검증은 유지
- 실제 MySQL 8.4.5 기반 스키마·트랜잭션·동시성 검증
- 최종 검증 후 변경된 기존 PDCA 문서에 최소 Superseded Notice 추가

### 3.2 제외

- Issue #12의 Access/Refresh Token, 쿠키, CSRF, 회전, 로그아웃 및 동시 단일 승자 계약 변경
- PWA·프론트엔드 구현
- production SMTP, Redis, 분산 rate limit, 다중 인스턴스 인증 상태
- Access Token blacklist, 다중 기기 세션, 토큰 계보·탈취 관제
- 비밀번호 재설정, social-login, account-deletion
- food·meal 기능과 관련 문서 또는 food stash 변경
- 기존 닫힌 Issue 본문 소급 수정
- 기존 완료 PDCA 본문 축약 또는 재작성
- V1~V3 Migration 수정

## 4. 유지할 안전 경계

- canonical email 유일성과 회원가입 저장·롤백 원자성
- 인증번호 원문 비저장·비로그, 짧은 만료, 오입력 제한과 일회성 성공 소비
- 비밀번호·인증번호·토큰·해시·키·내부 오류의 비노출
- BCrypt 비밀번호 저장과 UTF-8 72바이트 경계
- Refresh Token 발급, 해시 저장, 회전, 로그아웃 폐기
- Secure·HttpOnly·SameSite 쿠키와 same-origin CSRF 경계
- 동일 이전 Refresh Token 동시 회전의 단일 승자와 패자 `Set-Cookie` 부재
- SecurityContext 기반 사용자 식별, 소유권 검증과 사용자 데이터 격리
- 로그아웃 전 Access Token이 짧은 만료까지 남을 수 있다는 기존 한계
- MySQL 제약·트랜잭션·잠금 결과를 실제 MySQL 8.4.5에서 검증하는 원칙

## 5. 대표 완료 시나리오

1. Mailpit으로 받은 인증번호와 email을 signup에 제출하면 계정·자격 증명·서버 결정 동의가 함께 저장되고 인증 상태는 재사용할 수 없다.
2. 인증 상태 없음, 만료, 오입력 또는 성공 후 재사용은 계정 데이터를 만들지 않고 안전한 공개 오류를 반환한다.
3. 중복 canonical email과 가입 저장 실패는 부분 사용자·자격 증명·동의 또는 의도하지 않은 인증 상태 소비를 남기지 않는다.
4. 필수 동의 boolean이 누락되거나 false이면 가입되지 않으며, 성공 시 서버가 정한 현재 동의 종류와 버전이 저장된다.
5. 가입된 사용자는 기존 계약대로 로그인하고 SecurityContext 기반 보호 API를 호출할 수 있다.
6. Refresh Token 회전은 이전 토큰을 거절하고, 실제 MySQL 동시 요청에서 한 요청만 성공하며 패자 응답은 쿠키를 변경하지 않는다.
7. 로그아웃은 Refresh Token을 폐기하고 쿠키를 삭제하며 이후 refresh를 거절한다.
8. Swagger·OpenAPI·JavaDoc·로그와 오류 응답은 민감값이나 내부 구현 상세를 노출하지 않는다.

## 6. 문서와 기존 Issue 관계

- Issue #1에서는 `user-registration`의 `verificationProof` 소비 계약만 대체한다.
- Issue #8에서는 별도 인증번호 confirm과 `verificationProof` 발급 계약을 대체한다.
- Issue #12의 핵심 공개·보안·트랜잭션 계약은 유지하고 문서·Swagger·JavaDoc·테스트 중복만 정리한다.
- 기존 닫힌 Issue 본문은 수정하지 않는다.
- 기존 `user-registration`, `email-verification`, `local-login` Plan·Design·Analysis·Report 본문은 당시 구현의 역사 기록으로 유지한다.
- 구현과 검증이 완료된 후 효력이 변경된 문서 상단에만 새 결정 ID, 대체된 계약 범위, 역사 기록 유지 사실을 담은 짧은 Superseded Notice를 추가한다.
- 승인된 새 통합 Plan과 Design은 변경 대상 범위의 목표 권위 문서가 된다.
- 현재 구현 상태는 코드·Migration·설정·테스트로 판단하며, 구현 완료 후 Analysis와 Report에 실제 차이와 검증 결과를 기록한다.

## 7. 주요 위험과 대응

| 위험 | 대응 방향 |
|------|-----------|
| signup에 인증번호 소비와 계정 저장을 합치면서 부분 상태가 남음 | Design에서 하나의 명확한 DB 트랜잭션 결과를 정하고 실제 MySQL 롤백으로 검증한다. |
| 발송·재전송과 signup이 경쟁해 오래된 인증번호가 사용됨 | Design에서 잠금, 조건부 변경 또는 제약의 최종 결과를 정하고 대표 경쟁 시나리오를 검증한다. |
| forward Migration이 기존 설치와 신규 설치에서 다르게 동작함 | V1~V3는 보존하고 업그레이드 및 fresh-schema 검증을 모두 수행한다. |
| 인증 단순화가 로그인 보안 계약을 우발적으로 약화함 | Issue #12 계약을 변경 금지 기준으로 두고 기존 웹·MySQL 회귀 검증을 유지한다. |
| 문서 정리가 역사 기록 삭제나 반복 설명으로 바뀜 | 기존 본문은 유지하고 최소 Notice와 새 통합 문서만 현재 권위로 사용한다. |
| 범위가 운영 규모나 미래 확장성으로 커짐 | 현재 사용자 흐름과 P1·P2 문제만 완료 차단 대상으로 삼고 P3는 메모 또는 후속 Issue로 관리한다. |

## 8. Design에서 확정할 사항

- 변경 후 signup과 이메일 인증 공개 API, 필드, 성공·실패 결과
- challenge 상태의 검증·소비·재전송 경쟁 결과와 메일 실패 경계
- proof 영속 상태 제거를 위한 forward Migration과 업그레이드 결과
- boolean 동의 필드와 서버가 결정할 동의 종류·버전
- 인증번호 발송 시 challenge 저장과 메일 전송 실패의 최종 결과
- 대표 단위·웹·OpenAPI·실제 MySQL 검증 범위
- Superseded Notice에 사용할 최종 결정 ID와 문서별 대체 계약 범위

클래스명, Bean 구성, 라이브러리 API, 내부 메서드, SQL 문장, 패키지 경로와 테스트 이름은 공개 계약을 바꾸지 않는 한 Design에서 고정하지 않는다.

## 9. 완료 조건

- [x] 이 Plan과 하나의 통합 Design이 각각 승인된다.
- [x] Design 승인과 별도로 구현 범위가 승인된다.
- [x] 포함 범위와 대표 완료 시나리오가 구현·검증된다.
- [x] 기존 V1~V3는 변경되지 않고 forward Migration만 추가된다.
- [x] Issue #12 로그인 계약의 회귀가 없다.
- [x] 실제 MySQL 8.4.5 필수 테스트가 skip 없이 실행된다.
- [x] `.\gradlew.bat test javadoc`과 `git diff --check`가 통과한다.
- [x] Gap Analysis와 Report에 실제 결과, 미실행 항목과 남은 P3가 간결하게 기록된다.
- [x] 최종 검증 후 필요한 기존 문서에만 승인된 Superseded Notice가 추가된다.

## 10. 다음 승인 경계

Plan·Design·구현·Check·Report는 완료되었다. 다음 경계는 승인된 경로의 stage, commit, push와 Pull Request 생성 범위이며, 별도 승인 전에는 해당 Git 게시 작업을 수행하지 않는다.

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 1.0.1 | 2026-08-27 | 구현·검증·Analysis·Report 완료 조건 확인 | Project decision record |
| 1.0.0 | 2026-08-27 | `AUTH-SCOPE-REDUCTION-001` Plan 승인과 다음 Design 초안 경계 기록 | Project decision record |
| 0.1.0 | 2026-08-27 | Issue #15와 기존 인증 3개 흐름을 통합한 최초 Plan 초안 | Project decision record |
