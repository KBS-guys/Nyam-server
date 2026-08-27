# auth-scope-reduction - Design Document

> **Summary**: 이메일 인증 challenge를 회원가입에서 직접 검증·소비하고, 기존 로그인 보안 계약은 유지한다. <br>
> **Project**: Nyamlog <br>
> **Version**: 1.0.0 <br>
> **Author**: Project decision record <br>
> **Date**: 2026-08-27 <br>
> **Status**: Approved <br>
> **Plan**: `AUTH-SCOPE-REDUCTION-001` <br>
> **Decision**: `AUTH-SCOPE-REDUCTION-002` <br>
> **Related Issue**: [#15](https://github.com/KBS-guys/Nyam-server/issues/15)

---

## 1. 설계 목표와 권위

인증번호 발송 뒤 별도 confirm과 `verificationProof` 전달 없이 signup이 현재 challenge를 직접 검증한다. 성공 시 인증 상태 소비와 사용자·자격 증명·동의 저장을 하나의 원자적 결과로 만든다.

이 문서는 승인되면 변경 대상의 목표 권위가 된다. 현재 구현 상태는 코드·Migration·설정·테스트로 판단하며, 실제 차이와 검증 결과는 구현 후 Analysis와 Report에 기록한다.

## 2. 변경 경계

- 이메일 인증번호 발송 API와 정책은 유지한다.
- confirm API와 proof 발급·저장·전달·소비 계약은 제거한다.
- signup API의 입력만 직접 인증 방식으로 바꾸고 성공 응답은 유지한다.
- Issue #12의 Access/Refresh Token, 쿠키, CSRF, 회전, 로그아웃, 동시 단일 승자 계약은 변경하지 않는다.
- 기존 V1~V3와 완료 PDCA 본문은 수정하지 않는다.

## 3. 공개 API

### 3.1 인증번호 발송

| 항목 | 계약 |
|------|------|
| Method / Path | `POST /api/v1/auth/email-verifications` |
| Request | `email` |
| Success | HTTP 200, `EMAIL_VERIFICATION_CODE_SENT`, 표시 이메일과 만료·재전송 가능 시각 |
| Failure | 400 입력 오류, 409 가입된 이메일, 429 발송 제한, 503 메일 전달 실패 |

이메일은 바깥 공백 제거 후 254자 이하의 출력 가능한 ASCII와 기본 형식을 검증하고, `Locale.ROOT` 소문자 값을 canonical identity로 사용한다. 6자리 인증번호, 5분 만료, 60초 재전송 대기, 세션당 재전송 3회, 불일치 5회 제한은 유지한다.

### 3.2 회원가입

| 항목 | 계약 |
|------|------|
| Method / Path | `POST /api/v1/auth/signup` |
| Success | HTTP 201, `SIGNUP_COMPLETED`, 표시 이메일 |
| Side effect | 성공 후 자동 로그인이나 토큰 발급 없음 |

요청 필드는 다음과 같다.

| 필드 | 제약 |
|------|------|
| `email` | 발송 요청과 같은 이메일 정규화 계약, 필수 |
| `verificationCode` | 정확히 6자리 ASCII 숫자, 필수·요청 전용 민감값 |
| `password` | 8자 이상, UTF-8 72바이트 이하, 필수·요청 전용 민감값 |
| `birthDate` | 가입일 기준 만 19세 이상, 미래 날짜 불가 |
| `termsAgreed` | `true` 필수 |
| `personalInformationAgreed` | `true` 필수 |
| `healthInformationAgreed` | `true` 필수 |

signup의 `email`은 canonical identity를 계산하여 challenge를 찾는 데 사용한다. 인증 성공 후 `users.display_email`에 저장하고 성공 응답으로 반환하는 표시 이메일은 signup 요청값이 아니라 challenge에 저장된 `display_email`을 사용한다.

동의 종류와 버전은 클라이언트가 제출하지 않는다. 서버는 세 boolean이 모두 `true`일 때 `TERMS`, `PERSONAL_INFORMATION`, `HEALTH_INFORMATION`과 현재 버전 `1.0`을 저장한다.

### 3.3 회원가입 실패 결과

| 상태 | 공개 코드와 조건 |
|------|------------------|
| 400 | `INVALID_INPUT`: 필수 필드, JSON 또는 기본 형식 오류 |
| 409 | `EMAIL_ALREADY_REGISTERED`: canonical email이 이미 가입됨 |
| 422 | `EMAIL_VERIFICATION_INVALID`: challenge 없음·만료·번호 불일치 |
| 422 | `UNDERAGE_NOT_ALLOWED`: 만 19세 미만 또는 승인된 연령 정책 위반 |
| 422 | `REQUIRED_CONSENT_MISSING`: 하나 이상의 필수 동의가 `false` |
| 422 | `PASSWORD_POLICY_VIOLATION`: 승인된 비밀번호 정책 위반 |
| 429 | `EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED`: 다섯 번째 불일치 또는 이미 한도 도달 |
| 500 | `INTERNAL_SERVER_ERROR`: 내부 상세를 숨긴 예상하지 못한 실패 |

필드 누락, JSON 타입과 기본 형식 검증 실패는 400 `INVALID_INPUT`이며, 연령·필수 동의·비밀번호의 비즈니스 정책 실패는 위의 기존 422 application code를 유지한다.

`POST /api/v1/auth/email-verifications/confirm`과 proof 응답은 라우팅 및 OpenAPI 계약에서 제거한다. 이전 클라이언트와의 하위 호환 계층은 두지 않는다.

## 4. 데이터와 Forward Migration

- V2의 `email_verification_challenges`를 signup 직전까지의 유일한 인증 상태로 사용한다.
- V1의 `email_verification_proofs`는 다음 순번의 forward Migration에서 삭제한다.
- 기존 proof 데이터는 이전 계약과 함께 폐기하며 다른 상태로 변환하지 않는다.
- Migration 시점에 남아 있는 미사용 `verificationProof`는 사용할 수 없으며, 해당 사용자는 인증번호를 다시 발급받아 새 signup 흐름을 사용한다.
- 기존 challenge는 유지하므로 배포 전에 발송된 아직 유효한 번호도 새 signup 계약으로 검증할 수 있다.
- V1~V3 파일은 수정하지 않아 신규 설치와 기존 설치가 동일한 최종 스키마에 도달하게 한다.
- 사용자 canonical email UNIQUE와 사용자·자격 증명·동의의 기존 제약은 최종 무결성 방어로 유지한다.

## 5. 트랜잭션과 동시성

### 5.1 signup 최종 결과

1. 이메일·인증번호 형식, 연령, 동의, 비밀번호 정책을 먼저 검증한다. 이 단계의 실패는 challenge 실패 횟수를 바꾸지 않는다.
2. canonical email의 challenge를 쓰기 잠금으로 직렬화하고 한 시각을 기준으로 만료와 실패 횟수를 판단한다.
3. challenge가 없거나 만료되면 422를 반환하고 실패 횟수는 올리지 않는다.
4. 번호가 다르면 실패 횟수를 커밋한다. 첫 번째부터 네 번째 불일치는 422, 다섯 번째와 이후는 429다.
5. 번호가 맞으면 canonical email 중복을 확인하고 데이터베이스 UNIQUE 제약으로 가입 경쟁을 최종 방어한다.
6. 사용자, BCrypt 자격 증명, 서버 결정 동의 세 건을 저장하고 challenge를 삭제한다.
7. 6번의 모든 변경은 하나의 데이터베이스 트랜잭션으로 커밋한다. 어느 저장 단계든 실패하면 계정 데이터와 challenge 소비를 모두 롤백한다.

불일치 횟수는 오류 응답 전에 커밋되어야 하며, 롤백을 유발하는 예외로 먼저 종료해서는 안 된다. 성공 응답은 트랜잭션 커밋이 확정된 뒤에만 반환한다.

### 5.2 경쟁 결과

- 같은 challenge와 번호로 동시에 signup하면 challenge 잠금으로 직렬화하고 한 요청만 계정을 생성한다. 패자는 계정 일부를 남기지 않고 422 `EMAIL_VERIFICATION_INVALID`를 받는다.
- 발송과 signup은 같은 canonical email의 challenge를 먼저 직렬화한 뒤 가입 여부를 현재 데이터베이스 상태로 다시 확인한다.
- signup이 먼저 커밋되면 기다리던 발송은 409를 반환하고 새 challenge나 메일을 남기지 않는다.
- 재전송이 먼저 커밋되면 기존 번호는 즉시 대체되며, 그 번호를 제출한 signup은 불일치 규칙을 따른다.
- challenge가 없는 동일 canonical email의 동시 최초 발송에서는 한 요청만 신규 challenge를 커밋하고 메일을 전송한다.
- 경쟁에서 진 요청은 커밋된 challenge를 덮어쓰거나 재전송 횟수를 변경하거나 추가 메일을 전송하지 않는다.
- 경쟁에서 진 요청은 커밋된 challenge의 재전송 가능 시각을 기준으로 429 `EMAIL_VERIFICATION_SEND_LIMITED`를 반환한다.
- 이 결과를 만드는 구체적인 SQL, 잠금 또는 Repository 충돌 처리 방식은 구현에서 결정한다.

### 5.3 challenge 저장과 메일 실패

발송은 challenge 변경을 반영한 뒤 같은 트랜잭션 안에서 Mailpit에 동기 전송한다. 연결 실패나 명시적 전송 실패면 503을 반환하고 신규·재전송 상태 변경을 롤백한다.

SMTP가 메일을 수락한 뒤 응답 유실 또는 데이터베이스 커밋 실패가 발생하면 사용자가 사용할 수 없는 번호를 받을 수 있다. 이 모호한 결과는 소규모 로컬 범위에서 허용하며 사용자는 제한에 따라 다시 발송한다. outbox나 분산 트랜잭션은 도입하지 않는다.

## 6. 보안 경계

- 인증번호는 안전한 난수로 생성하고 원문을 저장하거나 로그·오류·Swagger 예시에 노출하지 않는다.
- challenge에는 기존 버전 구분 HMAC-SHA-256 검증값만 저장하고 canonical email을 입력에 결합하며 상수 시간 비교를 유지한다.
- HMAC 키는 최소 32바이트의 안정적인 외부 비밀값이며 누락·형식 오류 시 시작을 거부한다.
- 비밀번호는 BCrypt로만 저장하고 평문·해시·인증번호·토큰·키·내부 예외를 공개하지 않는다.
- Issue #12의 짧은 Access Token, Refresh Token 해시 저장·회전·폐기, Secure·HttpOnly·SameSite 쿠키, same-origin CSRF, 패자 응답의 쿠키 비변경을 그대로 유지한다.
- 보호 API는 SecurityContext에서 사용자를 식별하고 소유권과 cross-user 데이터 격리를 유지한다.

## 7. Swagger와 JavaDoc

- Swagger는 각 API의 목적, 필수 입력 제약, 주요 상태 코드와 민감 필드 경계만 설명한다.
- confirm 경로와 proof 필드를 제거하고 `verificationCode`와 `password`를 `writeOnly`로 표시하며 예시·기본값을 두지 않는다.
- 같은 정책 문장을 Controller·DTO·테스트에 반복하지 않는다.
- JavaDoc은 공개 타입, 주요 서비스 책임, 공개 계약과 비직관적인 보안·트랜잭션 규칙에 집중한다.
- OpenAPI 검증은 정확한 문구가 아니라 경로, 요청·응답 필드, 상태 코드, 보안 scheme과 민감값 비노출을 확인한다.

## 8. 대표 검증

- Mailpit 발송 번호로 직접 signup하고 계정·자격 증명·서버 결정 동의와 challenge 소비를 확인한다.
- challenge 없음·만료·불일치·다섯 번째 실패·재전송 교체·성공 후 재사용이 부분 계정을 만들지 않는지 확인한다.
- 잘못된 연령·동의·비밀번호가 실패 횟수를 소비하지 않고, 저장 실패가 계정과 challenge를 함께 롤백하는지 확인한다.
- 기존 설치 업그레이드와 신규 설치에서 V1~V3 변경 없이 proof 테이블만 최종 제거되는지 실제 MySQL 8.4.5로 확인한다.
- 실제 MySQL에서 동일 번호 동시 signup 단일 성공과 발송·signup 경쟁의 최종 결과를 확인한다.
- 기존 로그인·보호 API·refresh 회전·logout 회귀와 동일 이전 Refresh Token 동시 단일 승자를 유지한다.
- 전체 `test javadoc`, 실제 MySQL 필수 테스트 `skipped=0`, OpenAPI 민감값 비노출과 `git diff --check`를 확인한다.

## 9. 문서 Supersession

구현과 최종 검증 전에는 기존 완료 문서를 변경하지 않는다. 완료 후 효력이 바뀐 문서 상단에만 `AUTH-SCOPE-REDUCTION-002`, 아래 대체 범위, 역사 기록 유지 사실을 적은 짧은 Notice를 추가한다.

- Issue #1 문서: signup의 `verificationProof` 소비 계약만 대체
- Issue #8 문서: confirm과 `verificationProof` 발급 계약만 대체
- Issue #12 문서: 공개·보안·트랜잭션 계약은 유지하고 중복 설명 정리만 기록

새 Analysis와 Report는 구현 차이, 실행한 검증, 미실행 항목과 남은 P3만 기록하며 이 문서의 결정을 반복하지 않는다. 기존 닫힌 Issue 본문은 수정하지 않는다.

## 10. 제외 범위

PWA·프론트엔드, production SMTP, Redis, 분산 rate limit, 다중 인스턴스 상태, Access Token blacklist, 다중 기기 세션, 비밀번호 재설정, social-login, account-deletion, food stash는 변경하지 않는다.

## 11. 승인 기준과 다음 경계

이 초안에서 승인 차단 대상으로 보는 사항은 공개 API, forward Migration, 인증번호 실패 커밋, signup 원자성, 발송·가입 경쟁, 메일 실패, 로그인 보안 회귀와 실제 MySQL 완료 검증이다. 명명·표현·내부 구조·선택적 최적화 P3는 제안할 수 있지만 승인 조건으로 삼지 않는다.

현재 확인된 미해결 P1·P2는 없다. 전체 Design과 구현 착수는 각각 승인되었으며, 이후 승인 범위 안의 가역적인 내부 선택은 다시 승인받지 않는다.

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 1.0.0 | 2026-08-27 | P2 검토 반영 후 `AUTH-SCOPE-REDUCTION-002` 전체 Design 승인 | Project decision record |
| 0.1.0 | 2026-08-27 | `AUTH-SCOPE-REDUCTION-002` 통합 Design 초안 | Project decision record |
