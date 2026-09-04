# Small Deployment Design

> **Current decision**: `SMALL-DEPLOYMENT-004` — Approved scope direction (2026-09-04)
>
> **Supersedes for this milestone**: `SMALL-DEPLOYMENT-003`의 pre-merge 배포 순서와 runtime auth toggle
>
> **Related Plan**: `SMALL-DEPLOYMENT-001-R3`

## 1. 설계 목표와 경계

첫 배포는 Render + Aiven에서 기존 핵심 도메인과 사용자 소유권을 검증한다. 실제 계정 생성과 Access Token 획득은 구현하지 않는다. 기존 JWT decoder, controller principal, service의 `userId`, repository ownership 조건은 변경하지 않는다.

```text
개발자 PC                         Render
─────────                         ──────
food import ── TLS ─┐             Docker / deployment profile
synthetic A/B seed ─┼─ Aiven ←── TLS + VERIFY_IDENTITY
local 900s JWT ─────┘                │
                                     └─ food → meal → daily-summary
```

## 2. 배포 artifact

- Java 17 multi-stage Docker build를 사용한다.
- runtime은 UID/GID 1000의 non-root 사용자로 실행한다.
- Spring Boot는 `0.0.0.0:${PORT}`에 bind한다.
- 최종 JAR에는 main/deployment 설정과 Flyway V1~V7만 포함하고 smoke CLI source, ignored local 설정, `.env`, 원본 CSV와 secret을 포함하지 않는다.
- Render에서는 `SPRING_PROFILES_ACTIVE=deployment`를 필수로 설정한다.

## 3. deployment 보안 경계

별도의 `nyam.deployment.auth-endpoints-enabled` property는 두지 않는다. 활성 profile이 `deployment`이면 SecurityFilterChain이 `/api/v1/auth/**`를 무조건 거부한다.

```text
deployment
/api/v1/auth/**             denyAll
/api/v1/foods/**            authenticated
/api/v1/meals/**            authenticated
/api/v1/daily-summaries/**  authenticated
```

- 익명 auth 요청은 기존 JSON 401, 유효 JWT를 포함한 auth 요청은 JSON 403으로 끝난다.
- redirect, 계정/verification/refresh DB 쓰기, 메일 발송, token/cookie 발급이 없어야 한다.
- 일반 profile에서는 기존 signup/login/refresh/logout/email-verification 공개 계약을 유지한다.
- deployment에서는 login/refresh/logout도 Bearer를 정상 해석해 유효 토큰 요청이 auth 경로 차단을 우회하지 못하게 한다.
- 새 SmokeGuard, 고정 userId, 공개 token endpoint를 만들지 않는다.

## 4. Health와 OpenAPI

- Render health path는 `GET /actuator/health/render`이며 `ping`만 포함한다.
- Render 자체의 3xx 허용 여부와 무관하게 Nyamlog acceptance는 redirect 없는 HTTP 200과 status-only 응답을 요구한다.
- 다른 Actuator 경로와 method는 거부한다.
- OpenAPI/Swagger는 공통 기본값이 off다. Render에서만 `NYAM_OPENAPI_ENABLED=true`로 opt-in한다.
- Swagger UI와 `/v3/api-docs/**`는 익명 열람과 Try it out을 허용하지만 보호 API의 JWT/소유권을 우회하지 않는다.
- 공식 smoke 증거는 curl/Postman의 HTTP 결과이며 Swagger 실행으로 대체하지 않는다.

## 5. Aiven MySQL과 TLS

- MySQL 8.4를 사용하고 schema 자동 생성은 금지한다.
- startup은 Flyway V1~V7 적용 후 Hibernate `ddl-auto=validate`를 통과해야 한다.
- JDBC는 `sslMode=VERIFY_IDENTITY`, `fallbackToSystemTrustStore=false`를 유지한다.
- Aiven CA는 Render secret file로 제공하고 entrypoint가 매 기동마다 private PKCS12 truststore를 새로 만든다.
- URL/type/password가 일치하지 않거나 CA·권한·keytool 처리에 실패하면 JVM을 시작하지 않는다.
- Render 접근과 승인된 import/seed 시간의 개발자 접근만 provider가 제공하는 최소 네트워크 정책으로 허용한다. 작업 후 개발자 접근을 제거한다.
- 용량, 위치, 비용 문제가 생기면 보안을 완화하거나 임의 유료 전환하지 않고 중단해 선택을 요청한다.

## 6. Food import

- 기존 non-web Spring Batch `foodImport`를 개발자 PC에서 TLS JDBC로 실행한다.
- 승인된 원본 경로, release date, checksum을 명시한다.
- 기존 identity/checksum과 재실행 안전 계약을 변경하지 않는다.
- 성공 기준은 317,766건, Flyway schema, 예상 identity와 오류 없는 job 완료다.
- import에 메일은 필요하지 않으므로 해당 로컬 process에서만 MailSender auto-configuration을 제외할 수 있다.

## 7. Synthetic A/B seed 원자성

`smokeSeedUsers`는 예약된 `example.invalid` 이메일의 users 행 두 개만 준비한다.

- DB가 생성한 서로 다른 양의 `user_id`를 사용한다.
- 기존 정확한 fixture는 검증 후 같은 ID로 재사용한다.
- 필수 속성 충돌이나 credential, consent, verification challenge, refresh token이 있으면 수정하지 않고 실패한다.
- 새 인증 데이터나 migration을 만들지 않는다.
- CLI가 transaction을 열고 A/B를 준비한 뒤 private manifest를 먼저 기록한다. manifest 기록이 성공한 경우에만 DB를 commit한다.
- manifest 기록 또는 DB commit이 실패하면 transaction을 rollback하고 이번 실행이 새로 만든 불완전한 manifest를 제거한다. 이미 존재하던 출력 파일은 덮어쓰거나 삭제하지 않는다.
- 통합 테스트는 manifest 기록 실패 시 명령 실패, 신규 A/B 0건, 부분 파일 제거를 실제 MySQL 8.4.5에서 확인한다.

## 8. 로컬 JWT 도구

- `smokeIssueAccessToken`은 seed manifest의 A/B 중 하나만 선택한다.
- 기존 `AccessTokenIssuer`를 사용해 `sub=userId`, `iss=nyamlog`, `aud=nyamlog-api`, HS256, 900초 Access JWT를 발급한다.
- 임의 userId 입력, 만료 연장, refresh 사용, 서버 발급 endpoint를 제공하지 않는다.
- JWT와 signing secret은 환경 변수와 저장소 밖 신규 private 파일을 통해서만 전달하고 stdout·로그·문서·Git에 남기지 않는다.

## 9. 배포 순서

1. PR #23에서 전체 test/JavaDoc, 실제 MySQL, Docker 검증을 완료하고 리뷰한다.
2. 별도 승인으로 PR을 `dev`에 병합한다.
3. 병합된 정확한 `dev` SHA를 acceptance SHA로 기록한다.
4. Aiven service/database/user/CA를 준비한다. 비용이나 provider 변경은 별도 승인 없이는 하지 않는다.
5. Render service를 Auto-Deploy Off로 생성한다. 최초 bootstrap deploy는 acceptance로 세지 않는다.
6. Render outbound CIDR, Aiven 접근, CA secret file과 필수 env를 구성한다.
7. acceptance SHA를 수동 배포하고 health 200, Flyway V1~V7, Hibernate validate를 확인한다.
8. 개발자 IP를 임시 허용해 full food import와 A/B seed를 실행한 뒤 접근을 제거한다.
9. A/B JWT를 로컬에서 발급해 공식 domain/ownership smoke를 수행한다.
10. 실제 결과만 Issue #22, runbook, PDCA에 기록한다.

## 10. 공식 smoke

1. health 200과 OpenAPI opt-in을 확인한다.
2. JWT 없음·변조·만료로 보호 API가 각각 401인지 확인한다.
3. auth prefix가 익명/유효 JWT 모두 차단되고 부작용이 없는지 확인한다.
4. A JWT로 food 검색·상세를 수행한다.
5. 비어 있는 날짜에 A meal을 생성하고 목록에서 확인한다.
6. 삭제 전에 A daily-summary의 non-empty snapshot 집계를 확인한다.
7. B meal 목록/summary에 A 데이터가 없는지 확인한다.
8. B가 A meal 삭제를 시도해 `404 MEAL_NOT_FOUND`이고 A 데이터가 남는지 확인한다.
9. A가 자신의 meal을 삭제한다.
10. A daily-summary의 empty 결과를 확인한다.

각 요청은 비밀값 없는 시나리오 ID, HTTP status, 공개 error code, 필요한 집계 결과만 기록한다. request header, JWT, DB credential, signing secret, CA 원문은 저장하지 않는다.

## 11. 완료와 후속

Plan의 16개 완료조건이 실제 Render/Aiven에서 모두 관찰되기 전에는 `small-deployment`를 완료로 판정하지 않는다. 이번 결과는 이메일, signup/login, refresh/logout, cookie/CSRF, 실제 사용자 token acquisition 또는 최종 인증 E2E의 성공 증거가 아니다.

## 12. 결정 이력

- `SMALL-DEPLOYMENT-002/R1`, `SMALL-DEPLOYMENT-003`의 승인 내용과 과거 Do 증거는 Git/PDCA 이력으로 보존한다.
- `SMALL-DEPLOYMENT-004`는 runtime auth toggle을 제거하고 seed/manifest 실패 원자성을 명시하며, pre-merge 배포를 `dev` 병합 후 acceptance로 대체한다.
- 현재 PDCA phase는 Do이며 PR merge와 외부 provider 작업은 각각 별도 승인 경계다.
