# Small Deployment Runbook

> **Plan**: `SMALL-DEPLOYMENT-001-R3`
>
> **Design**: `SMALL-DEPLOYMENT-004`
>
> **Current phase**: Do — local implementation; remote acceptance not run

## 1. 실행 경계

이 runbook은 PR #23을 리뷰하고 별도 승인으로 `dev`에 병합한 뒤, 병합된 SHA를 Render + Aiven에 배포해 핵심 도메인을 검증하는 절차다.

이번 실행에서 `/api/v1/auth/**`, 이메일, SMTP, signup/login, refresh/logout, cookie/CSRF를 사용하지 않는다. 인증 방식이나 schema를 변경하지 않는다. provider 계정 생성, 비용 발생, credential 입력, DB 쓰기, PR merge는 각각 승인된 시점에만 수행한다.

## 2. 로컬 PR gate

1. branch가 `codex/small-deployment`이고 PR #23의 head와 일치하는지 확인한다.
2. `auth-endpoints-enabled` 참조가 저장소에 없고 deployment 통합 테스트가 auth prefix 차단을 증명하는지 확인한다.
3. manifest 실패 통합 테스트가 명령 실패, A/B rollback, 부분 파일 제거를 증명하는지 확인한다.
4. `./gradlew test javadoc --console=plain`을 실행한다. actual MySQL 8.4.5 suite의 skip은 성공으로 세지 않는다.
5. clean export에서 동일 명령과 Docker build를 확인한다. ignored 설정과 이전 build 산출물은 입력으로 사용하지 않는다.
6. 결과를 PR에 기록하고 리뷰한다. 이 단계에서 병합하거나 외부 배포하지 않는다.

## 3. 병합 후 acceptance SHA

1. 사용자의 별도 merge 승인을 확인한다.
2. PR #23을 `dev`에 병합한다.
3. remote `dev`의 정확한 SHA를 기록하고 이후 모든 배포·증거에서 같은 SHA를 사용한다.
4. SHA가 달라지면 자동으로 최신 branch를 배포하지 말고 다시 승인 범위를 확인한다.

## 4. Aiven 준비

1. MySQL 8.4 service, database, 전용 application user와 CA를 준비한다.
2. service/database/user 이름과 endpoint는 운영 메모에만 두고 Git/Issue/PR에 원문을 남기지 않는다.
3. Render service의 outbound CIDR을 알기 전에는 불필요하게 외부 접근을 열지 않는다.
4. import/seed 때만 개발자 IP를 임시 허용하고 완료 후 제거한다.
5. TLS `VERIFY_IDENTITY`를 만족할 hostname과 CA를 사용한다. TLS mode나 hostname 검증을 낮추지 않는다.

## 5. Render 생성과 수동 배포

1. Docker Web Service를 Auto-Deploy Off로 만든다.
2. 최초 service creation 중 발생하는 bootstrap deploy는 acceptance가 아니다.
3. 생성된 service의 outbound CIDR을 Aiven 최소 allowlist에 추가한다.
4. Aiven CA를 `/etc/secrets/aiven-ca.pem`으로 제공한다.
5. 다음 필수 설정을 secret-safe하게 입력한다.

| 이름 | 계약 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `deployment` |
| `MYSQL_URL` | credential/TLS query가 없는 JDBC URL |
| `MYSQL_USERNAME`, `MYSQL_PASSWORD` | 전용 DB credential |
| `MYSQL_TRUSTSTORE_URL` | `file:/tmp/nyam-mysql/aiven-truststore.p12` |
| `MYSQL_TRUSTSTORE_PASSWORD` | 별도 runtime secret |
| `NYAM_AUTH_ACCESS_SECRET` | 기존 JWT 검증과 로컬 발급이 공유하는 Base64 HS256 secret |
| `NYAM_EMAIL_VERIFICATION_HMAC_SECRET` | 기존 bean 기동에 필요한 별도 secret; 기능은 사용하지 않음 |
| `NYAM_OPENAPI_ENABLED` | Render에서 문서를 공개할 때만 `true` |

SMTP 환경 변수는 설정하지 않는다. auth endpoint는 deployment profile에서 항상 차단된다.

6. Health Check Path를 `/actuator/health/render`로 설정한다.
7. 병합된 acceptance SHA를 수동 배포한다.
8. entrypoint의 truststore bootstrap, Flyway V1~V7, Hibernate validate와 redirect 없는 health 200을 확인한다.

## 6. 원격 food import

1. 저장소 밖 사용자 전용 위치에 CA와 임시 PKCS12를 준비한다.
2. process 환경에 DB/TLS 변수와 아래 import 변수를 설정한다.

```text
SPRING_PROFILES_ACTIVE=deployment
NYAM_FOOD_IMPORT_PATH=<approved source>
NYAM_FOOD_IMPORT_RELEASE_DATE=<approved release date>
NYAM_FOOD_IMPORT_CHECKSUM=<approved checksum>
```

3. 이 non-web process에만 `SPRING_AUTOCONFIGURE_EXCLUDE=org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration`을 설정한다.
4. `./gradlew foodImport --console=plain`을 실행한다.
5. job 성공, identity/checksum과 food 317,766건을 비밀값 없이 확인한다.

## 7. Synthetic A/B와 JWT

`smokeSeedUsers`는 DB에 users A/B만 준비하고 private manifest를 만든다. manifest 실패 시 신규 행이 rollback돼야 한다.

```text
MYSQL_URL
MYSQL_USERNAME
MYSQL_PASSWORD
MYSQL_TRUSTSTORE_URL
MYSQL_TRUSTSTORE_PASSWORD
NYAM_SMOKE_TARGET
NYAM_SMOKE_SEED_OUTPUT
```

위 환경을 사용자 전용 local process에만 둔 뒤 `./gradlew smokeSeedUsers --console=plain`을 실행한다. 출력은 저장소 밖의 존재하지 않는 절대 경로여야 한다. 기존 파일을 덮어쓰지 않는다.

각 사용자에 대해 `NYAM_SMOKE_USER=A` 또는 `B`, `NYAM_AUTH_ACCESS_SECRET`, `NYAM_SMOKE_JWT_OUTPUT`을 설정하고 `./gradlew smokeIssueAccessToken --console=plain`을 실행한다. JWT는 900초이고 private 파일에만 기록된다. 만료되면 refresh가 아니라 로컬 도구로 새 파일에 재발급한다.

## 8. 공식 HTTP smoke

curl/Postman의 비공개 local session에서 수행한다. verbose 출력과 공유 로그를 사용하지 않는다.

1. health → redirect 없는 200
2. OpenAPI opt-in → UI/spec 열람, 보호 API 우회 없음
3. JWT 없음·변조·만료 → food API 401
4. 익명 auth prefix → 401, 유효 A JWT auth prefix → 403, 부작용 없음
5. A JWT → food 검색 200 → 결과 ID의 상세 200
6. 빈 테스트 날짜 확인 → A meal 생성 201 → A 날짜 목록 200
7. 삭제 전 A daily-summary → non-empty, snapshot 합계와 `complete` 확인
8. B 날짜 목록/summary → A 데이터 없음
9. B가 A meal 삭제 → `404 MEAL_NOT_FOUND`
10. A 날짜 목록 → 원본 meal이 남아 있음
11. A가 자신의 meal 삭제 → 성공
12. A daily-summary → empty 결과

smoke가 끝나면 개발자 DB 접근을 제거하고 JWT/manifest/truststore 임시 산출물을 안전하게 정리한다. seed 사용자 자동 삭제나 다른 데이터 정리는 별도 승인 없이는 하지 않는다.

## 9. 증거 기록

다음만 Issue #22, PR, PDCA에 기록한다.

- 실제 배포 SHA
- Render health와 OpenAPI 결과
- Aiven TLS, Flyway V1~V7, Hibernate validate
- food import identity와 317,766건
- A/B seed 성공과 DB 생성 ID 사용 여부(실제 ID 값 제외 가능)
- JWT 실패 401, auth prefix 차단, domain/ownership smoke의 status·공개 code·집계
- 개발자 DB 접근 제거
- 성공/실패/미실행과 남은 작업

JWT, signing secret, DB password, provider credential, CA 내용, private endpoint, cookie나 Authorization header는 기록하지 않는다.

## 10. 보존된 로컬 Do 증거

2026-09-03의 `SMALL-DEPLOYMENT-003` 검증은 이력으로 보존한다.

- clean export: 46 suites / 150 passed / failures 0 / errors 0 / skipped 0
- 실제 MySQL 8.4.5 도메인/ownership smoke 통과
- Docker non-root, custom `$PORT`, TLS/truststore, Flyway V1~V7, health/OpenAPI 검증 통과
- 전체 food import 선행 증거: 317,766건

이 증거는 이번 revision의 새 auth-toggle 제거와 manifest 실패 rollback 테스트를 대신하지 않는다. 새 결과는 아래에 별도 기록한다.

## 11. SMALL-DEPLOYMENT-004 로컬 Do 증거 (2026-09-04)

- 구현: runtime auth toggle 제거, deployment profile의 auth prefix 고정 차단, manifest-before-commit 및 실패 rollback/부분 파일 제거
- 전체 test/JavaDoc: **47 suites / 151 passed / failures 0 / errors 0 / skipped 0**; JavaDoc 성공, 기존 경고 78개와 신규 smokeTools 경고 0
- actual MySQL 8.4.5: **13 suites / 39 tests 포함, skipped 0**; 새 manifest 실패 rollback 테스트 1건 통과
- Docker build: `nyam-small-deployment:pr23-review` 성공, image `sha256:5fcbdc62fc7bd1b400c2a09175e1e6b9dddfbb86b326456aa3ac11627bd57134`, `User=1000:1000`, entrypoint `/app/entrypoint.sh`
- clean export: 이 revision에서는 별도 전체 재실행하지 않음. Docker의 allowlist `.dockerignore` context와 2026-09-03 clean-export 150건 증거를 유지하되, 병합 전 필요하면 새 candidate clean export gate를 별도로 수행한다.
- Render/Aiven remote acceptance: 미실행

현재 revision의 전체 회귀, 실제 MySQL과 Docker build는 통과했다. 외부 acceptance가 Plan의 16개 조건을 모두 충족하기 전에는 feature 완료나 Check 전환을 선언하지 않는다.
