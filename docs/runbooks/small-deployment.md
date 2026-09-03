# Render 개인 배포 runbook

> **현재 승인 기준**: `SMALL-DEPLOYMENT-003` / 2.0.0 (2026-09-03). 실행 계약은 §8, 이후 승인된 구현·로컬 검증 결과는 §9다. 외부 실행과 Git 게시는 미승인이다. §§1–7는 기존 승인 절차와 당시 증거를 원문 보존한 것이다. 특히 §7의 실메일 잔여 과제와 테스트 통과 수치는 새 scope에 대한 완료/미완료 판정으로 소급 해석하지 않는다.

기준: `SMALL-DEPLOYMENT-002`, `SMALL-DEPLOYMENT-002-R1`, `SMALL-DEPLOYMENT-003` / [Issue #22](https://github.com/KBS-guys/Nyam-server/issues/22).
로컬 구현 승인과 provider 계정·비용·실제 credential·배포·Git 게시 승인은 별개다.
이 문서의 외부 절차를 승인 없이 실행하지 않는다. 자동 배포·Vercel·social-login은 제외한다.

## 1. 배포 전 gate

1. ignored 파일·이전 build 산출물이 없는 배포 후보에서 `test javadoc`의 실제 MySQL 8.4.5 테스트가 skip 없이 통과하고 Docker build와 아래 로컬 container 검증이 성공해야 한다.
2. 전체 식품 317,766건 적재 후 `information_schema.TABLES`의 data+index 합계를 확인한다. 통계 캐시를 피하려면 적재 뒤 통계를 갱신하고 `information_schema_stats_expiry=0`으로 조회한다.
3. 2026-09-03 로컬 선행 검증: V1~V7 성공, Job/Step `COMPLETED`, read/write 317,766, filter/skip/rollback 0, 고유 코드 317,766. data+index **109,346,816 bytes (104.28 MiB)**. 완료된 동일 identity 재실행은 명시적 실패이며 건수 유지. 이 수치는 Aiven의 실제 과금/용량 표시나 여유 공간 보장이 아니다.
4. 예상 또는 Aiven 적재 후 사용량이 700MB를 넘으면 Free 사용을 중단하고 Developer 8GB 비용 승인을 요청한다.
5. 별도 외부 승인 후 SMTP2GO 가입용 업무/소유 도메인 주소, sender 검증 및 DMARC 제약부터 확인한다. shared-domain 주소 가입이나 sender 조건이 안 되면 provider 확정·연결 전에 중단한다. 도메인 구매나 다른 provider 선택은 재승인 대상이다.

## 2. 로컬 검증

먼저 깨끗한 검증 디렉터리를 준비한다. commit 전에는 `git -c core.quotePath=false ls-files --cached --others --exclude-standard` 목록을 검토해 현재 추적 파일과 승인된 신규 파일만 복사하고 원본 내용과 일치함을 확인한다. `.git`, ignored `application.yml`/`application.properties`·`.env`, 로컬 agent 자료·원본 CSV, 기존 `.gradle`/`build`는 복사하지 않는다. 이는 미commit 배포 후보 export이며 Git clone·worktree 생성이나 승인 배포 SHA 증거가 아니다.

아래 명령은 그 디렉터리에서 실행한다. 상속된 애플리케이션 환경 변수도 제거하고 테스트가 생성한 비운영 설정만 사용한다. Gradle dependency cache 재사용은 가능하지만 이전 project build/test 결과는 재사용하지 않는다.

```powershell
.\gradlew.bat test javadoc
docker build -t nyam-small-deployment:local .
```

- image는 Java 17, UID/GID 1000이며 `keytool`을 포함한다. 빌드 context allowlist에서 `.env`, 공통 ignored 설정, CSV, 테스트, Git와 agent 자료를 제외한다. tracking 정책은 바꾸지 않는다.
- 공통 안전 기본값 `nyam-defaults.properties`와 초기 로딩 등록은 최종 JAR에 포함한다. profile/환경 설정은 우선 적용하며, `NYAM_OPENAPI_ENABLED` 미지정 시 docs/UI가 닫히고 명시적 `true`에서만 열리는지 검증한다. 테스트에 `false`를 강제로 주입하는 것으로 default-off 검증을 대체하지 않는다.
- 임시 비운영 MySQL/CA/credential만 사용해 container 실행을 검증한다. 기존 개발 DB를 초기화하거나 실제 provider secret을 테스트에 넣지 않는다.
- CA를 group 1000이 읽을 수 있는 파일로 제공하고 아래 runtime 환경 변수 이름으로 주입한다. secret 값은 command 인자 대신 현재 process 환경 또는 승인된 secret 관리 수단을 사용한다.
- `PORT`를 8080 이외 값으로 바꿔 health 최초 응답 200, non-root, truststore 디렉터리 0700/파일 0600을 확인한다.
- restart 뒤 truststore 재생성, CA/암호/keytool 누락·읽기/변환/권한 실패 시 JVM 미기동을 확인한다. 잘못된 CA/hostname이면 JDBC가 거부되어야 한다. 테스트를 이유로 배포 profile의 TLS를 낮추지 않는다.
- 공식 문서의 보안 설정은 [Spring Boot health groups](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html), [Connector/J TLS properties](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-security.html)를 참고하되 실제 의존성·실행 검증을 우선한다.

## 3. 승인된 외부 설정과 수동 배포

1. 별도 외부 승인 후 Aiven MySQL 8.4.x의 전용 database/service user를 준비한다. `0.0.0.0/0`·`::/0` 허용을 제거한다. Render CIDR을 확인하기 전에는 불특정 외부 접근을 열지 않는다. provider에서 실제 접근 차단을 확인할 수 없으면 생성을 계속하지 말고 중단한다.
2. **서비스 생성·bootstrap을 포함한 실제 배포 승인**을 확인하고 생성 직전 원격 `dev` HEAD가 승인 SHA인지 대조한다. 다르면 중단한다. Render Free Docker Web Service, Singapore, repository `KBS-guys/Nyam-server`, source `dev`, **Auto-Deploy Off**로 설정한다. Blueprint/pre-deploy command는 사용하지 않는다.
3. **Create Web Service는 최초 build/deploy를 시작한다.** Auto-Deploy Off가 이 최초 시도를 제거하지 않는다. 설정 미완료 상태의 platform bootstrap은 공식 acceptance deploy가 아니며, 실패를 피하려고 DB 전체 공개나 TLS 완화를 하지 않는다.
4. 생성된 Render service의 **Connect → Outbound**에서 전체 CIDR을 확인해 Aiven에 등록한다. shared CIDR은 tenant 전용 경계가 아니므로 TLS와 DB credential도 필요하다.
5. Aiven CA를 Render secret file `aiven-ca.pem`으로 등록한다. runtime 경로는 `/etc/secrets/aiven-ca.pem`이며 group 1000 읽기 권한이 필요하다.
6. 다음 변수의 실제 값은 Render의 승인된 환경 설정에서만 입력한다. `.env`, Docker build ARG, Dockerfile, JDBC URL의 password, JVM 인자나 문서에 넣지 않는다. 설정 저장 등으로 추가 배포가 발생해도 아래 승인 SHA 수동 배포의 공식 검증을 대체하지 않는다.

| 환경 변수 | 설정 계약 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `deployment` |
| `PORT` | Render 제공 |
| `MYSQL_URL` | JDBC endpoint/database만; credential, TLS/truststore query 옵션 제외 |
| `MYSQL_USERNAME`, `MYSQL_PASSWORD` | DB service credential |
| `MYSQL_TRUSTSTORE_URL` | `file:/tmp/nyam-mysql/aiven-truststore.p12` |
| `MYSQL_TRUSTSTORE_PASSWORD` | 별도 runtime secret; 최소 6자 이상의 충분히 긴 임의값 |
| `NYAM_AUTH_ACCESS_SECRET`, `NYAM_EMAIL_VERIFICATION_HMAC_SECRET` | 기존 Base64 key 계약 유지 |
| `NYAM_MAIL_HOST`, `NYAM_MAIL_PORT` | eligibility 확정 후 `mail.smtp2go.com`, `2525` |
| `NYAM_MAIL_USERNAME`, `NYAM_MAIL_PASSWORD` | SMTP 전용 credential; 계정 로그인 암호 아님 |
| `NYAM_MAIL_FROM` | 검증된 sender |
| `NYAM_OPENAPI_ENABLED` | Render에서 명시적으로 `true`; 기본값은 `false` |

7. CIDR·CA·환경 설정을 완료하고 Health Check Path를 `/actuator/health/render`로 지정한다. **Manual Deploy → Deploy a specific commit**으로 승인된 정확한 SHA를 배포하고 Deploys/Events에서 일치 여부를 확인한다. branch 최신값을 무조건 배포하지 않는다. **이 수동 배포부터 공식 acceptance 대상으로 판정한다.**
8. Flyway V1~V7와 Hibernate validate 성공 후 health를 확인한다. `ping`만 검사하므로 DB/메일의 지속적인 정상 여부를 증명하지 않는다. Render는 3xx도 healthy로 볼 수 있지만 Nyamlog는 **redirect 미추적 최초 HTTP 200**만 통과시킨다.

플랫폼 근거: [최초 생성 배포](https://render.com/docs/web-services), [outbound CIDR 확인](https://render.com/docs/outbound-ip-addresses), [정확한 commit 수동 배포](https://render.com/docs/deploys).

## 4. 개발자 PC 최초 import (별도 외부 승인 후)

1. import 동안에만 현재 개발자 public IP `/32`를 추가한다. 원본 CSV는 저장소에 게시하지 않는다.
2. Aiven CA로 저장소 밖의 임시 PKCS12를 생성한다. Java 17 `keytool -importcert`에 `-storetype PKCS12 -storepass:env MYSQL_TRUSTSTORE_PASSWORD`를 사용한다. 기존 store를 재사용하지 말고 사용자 전용 디렉터리/ACL을 사용한다. CA·암호·권한 오류면 멈춘다.
3. process 환경에 DB/TLS 변수, `SPRING_PROFILES_ACTIVE=deployment`, `NYAM_FOOD_IMPORT_PATH`, `NYAM_FOOD_IMPORT_RELEASE_DATE`, `NYAM_FOOD_IMPORT_CHECKSUM`을 설정한다. `MYSQL_TRUSTSTORE_URL`은 로컬 임시 store의 file URL이다. URL/type/password와 VERIFY_IDENTITY/fallback 차단 다섯 속성을 deployment profile에서 동일하게 적용한다.
4. non-web import에는 메일이 필요 없다. 이 process에만 `SPRING_AUTOCONFIGURE_EXCLUDE=org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration`을 설정하여 SMTP credential 바인딩을 제외하고 `.\gradlew.bat foodImport --console=plain`을 실행한다. 전체 실행 로그를 공유하지 않는다.
5. Job/Step `COMPLETED`, read/write 317,766, filter/skip/rollback 0, `foods`/고유 외부 코드 317,766과 Aiven 실제 사용량을 확인한다. 완료 같은 release+checksum은 거부되어야 하며 건수 불변이다. 실패는 같은 identity/원본으로 checkpoint 재개하고, 삭제·repair·새 identity 우회는 하지 않는다.
6. MySQL 8.4.x, V1~V7 성공, food/meal 이름 snapshot의 `utf8mb4_0900_bin`, FK/CHECK/UNIQUE 거절·rollback, case/accent 구분, snapshot 독립성과 cross-user 차단을 승인된 검증 절차로 확인한다. 실제 데이터에 파괴적 SQL을 실행하지 않는다.
7. import 직후 개발자 IP를 제거하고 **개발자 연결 거부 + Render 연결 유지**를 확인한다. 임시 credential 환경과 truststore/CA는 검증 완료 후 안전하게 폐기한다.

## 5. 공식 curl/Postman smoke

자동 redirect 추적과 요청/응답 원문 저장을 끈다. cookie jar는 저장소 밖 사용자 전용 임시 위치 또는 Postman의 비공유 로컬 세션에서만 사용하고 검사 후 폐기한다. 콘솔 `-v`, shell tracing, 공유 collection/export에 인증 원문을 남기지 않는다.

1. health 최초 HTTP 200/status-only, Swagger 진입점과 `/v3/api-docs` 제공을 확인한다. 문서는 익명이며 Try it out은 실제 DB/메일을 변경한다. Authorization 영속 저장·공용 계정/토큰 미리 주입은 하지 않는다.
2. 실제 이메일을 5분 내 수신 → signup → login. 코드, 이메일, credential 원문은 evidence에 남기지 않는다.
3. Access Token은 body만, 인증 응답 `Cache-Control: no-store`; Refresh cookie는 `Secure`, `HttpOnly`, `SameSite=Strict`, `Path=/api/v1/auth`. cookie 값은 기록하지 않는다.
4. Bearer로 `/api/v1/auth/me`, food 검색/상세, meal 생성/날짜 조회. cold start 완료 뒤 DB 읽기 요청은 각각 10초 client timeout으로 검증한다.
5. **삭제 전에 daily-summary**의 `mealItemCount > 0`과 저장 snapshot 합계를 확인한다. 이후 meal 삭제, 선택적으로 empty summary 재확인.
6. cookie jar와 `X-Nyam-CSRF: 1`로 refresh → rotation된 cookie 교체 → logout의 `Max-Age=0`을 확인한다. 기존 쿠키/CSRF 보안을 낮추지 않는다.

Swagger 실행은 보조 확인일 뿐 공식 smoke를 대체하지 않는다. 이 단계 성공으로 Vercel이나 실제 browser cookie 동작을 주장하지 않는다. 증거는 승인 SHA, status/code, 민감값을 제거한 header 속성, count와 성공/실패 여부만 남긴다.

## 6. 중단·실패·rollback

- SMTP 인증/TLS/timeout/동기 rejection(월간 hard quota 포함): `503 EMAIL_DELIVERY_UNAVAILABLE`, challenge rollback. 일일 quota queue는 SMTP 수락 뒤 지연될 수 있어 즉시 503을 보장하지 않으며 실제 inbox 도착까지 확인한다.
- TLS CA/hostname 실패: store/권한/endpoint를 확인하고 검증을 낮추지 않는다. 외부 오류 상세나 credential을 evidence에 복사하지 않는다.
- DB 읽기 timeout: cold start·인증·TLS·일시 장애를 먼저 구분한다. 위치가 원인이면 Developer APAC 배치 가능성과 새 service/migration 필요성을 검토하고, exact region이 필요하면 상위 tier/타 provider를 재승인받는다. 자동 업그레이드는 없다.
- 첫 배포 실패: 이전 정상 deploy가 없으므로 준비되지 않은 상태로 중단한다. 이후 배포 실패는 이전 승인 SHA 재배포가 기본이지만, Flyway 부분 적용/호환성 의심 시 commit rollback만 하지 말고 멈춘다.
- destructive SQL, Flyway repair, 유료 전환, provider 변경, 새 migration, Git 게시와 실제 배포는 각각 승인 범위를 다시 확인한다. Free를 상용 SLA나 무중단 보장으로 표현하지 않는다.

## 7. 로컬 Do 검증 기록 (2026-09-03)

`codex/small-deployment`의 미commit 작업트리 기준이며 승인 배포 SHA 또는 외부 배포 완료 증거가 아니다.

### 7.1 선행 로컬 검증

아래는 보완 전 실행 기록이다. 138건 통과는 유효한 로컬 증거지만 ignored 설정 없는 clean checkout의 증거로 사용하지 않는다. 전체 식품 적재·TLS negative·entrypoint 실패 7종은 이 선행 실행의 결과이며 아래 clean-export 재검증과 구분한다.

| 검증 | 결과 |
|---|---|
| `.\gradlew.bat test javadoc` | 42 suites, 138 passed, failed/errors/skipped/unexecuted 각 0; 실제 MySQL 8.4.5·Mailpit 포함 |
| JavaDoc | 성공; 기존 문서 경고 78개 유지 |
| 전체 식품 import·동일 입력 거절 | §1의 317,766건·104.28 MiB 선행 gate 통과 |
| Docker build·산출물 | Java 17 multi-stage 성공; 최종 JAR에 로컬 `application.yml`/`.env`/CSV/Java source 0, deployment profile과 V1~V7 포함 |
| 실제 container | 512 MiB/1 CPU, UID/GID 1000, `$PORT=19090`, TLS 강제 MySQL 연결, redirect 미추적 health 200/status-only |
| 문서·보안 | Swagger/OpenAPI 200, same-origin 상대 server `/`, 보호 API·그 외 Actuator 익명 요청 거부; 인증 상태의 Actuator 거부·CSRF 회귀는 자동 테스트 통과 |
| truststore | root:1000 CA 0440 읽기, 디렉터리 0700/store 0600, restart마다 재생성 |
| bootstrap 실패 7종 | CA/암호 누락, malformed CA, 짧은 store 암호, CA 읽기 권한, store 쓰기 권한, keytool 누락 모두 비민감 오류만 출력하고 JVM 미기동 |
| JDBC 검증 | 5개 TLS property 바인딩, 잘못된 hostname·신뢰하지 않는 CA 연결 거부, 평문 downgrade 없음 |
| 로컬 TLS import | 저장소 밖 임시 PKCS12 사용; auth/SMTP credential 없이 완료 identity 거절까지 도달; 건수 불변 |
| 로그 | Flyway endpoint INFO 로그 억제와 회귀 테스트; 최종 image startup의 JDBC URL·주요 secret 원문 비노출 확인 |
| 범위 | 공개 API 기능·DB schema 변경 없음; stage/commit/push/PR/실제 배포 없음 |

### 7.2 `SMALL-DEPLOYMENT-002-R1` clean-export 재검증

현재 추적 파일과 승인된 신규 파일 **201개**를 빈 `build/local-recheck/candidate` 디렉터리에 복사하고 모든 복사 내용의 일치를 확인했다. `.git`, ignored 설정·`.env`, 원본 CSV, 기존 build 산출물 없이 시작했으며 상속된 애플리케이션 환경 변수도 제거했다. Gradle dependency cache만 재사용했다. 새로운 branch/worktree나 commit SHA는 만들지 않았다.

| 재검증 | 결과 |
|---|---|
| `test javadoc --console=plain` | **43 suites, 141 passed, failed/errors/skipped/unexecuted 각 0**; 4분 14초, 정상 종료 |
| 실제 MySQL 8.4.5 | 10 integration suites / 31 tests 통과; V1~V7, FK/CHECK/UNIQUE·트랜잭션·snapshot·소유권·Batch 및 Mailpit 회귀 포함 |
| JavaDoc | 성공, 기존 경고 78개 유지; 이번 공통 설정 로더로 인한 추가 경고 없음 |
| 공통 기본값 | `nyam-defaults.properties`를 초기 Environment 로더로 읽음; ignored 파일 직접 읽기 제거, 테스트의 강제 `false` 제거 후 실제 docs/UI 404 검증; opt-in·외부 설정 우선순위 3건 추가 |
| clean-export Docker build | `nyam-small-deployment:clean-recheck` 빌드 성공; 위 export만 build context로 사용 |
| deployment, OpenAPI 변수 미지정 | localhost HTTP health **200/status-only**, `/v3/api-docs`, `/swagger-ui.html`, `/swagger-ui/index.html` 모두 **404** |
| deployment, `NYAM_OPENAPI_ENABLED=true` | docs/UI **200**, same-origin `/`, Authorization 영속 저장 false, 보호 API·그 외 Actuator **401** |
| deployment profile 없는 실행 JAR | 별도 프로필 파일·OpenAPI flag 없이 공통 default-off 적용: docs/UI **404**, probe **200**; 검증용 port/probe와 동일한 다섯 JDBC TLS 속성만 명시 공급, `VERIFY_IDENTITY`·fallback 차단 유지 |
| 실제 Docker/MySQL 연결 | MySQL **8.4.5**, 성공 migration **7**, 앱의 TLS 세션 관찰; `require_secure_transport=ON`, non-root **1000:1000**, **512 MiB/1 CPU**, container **PORT=19090** |
| truststore·로그 | CA root:1000/0440, 디렉터리 0700/store 0600, restart 후 재생성; startup의 JDBC URL·주요 임시 secret 원문 비노출 검사 통과 |
| 최종 JAR | 공통 defaults, 로더 class, `META-INF/spring.factories`, deployment profile 포함; ignored `application.yml`/`application.properties`·`.env`·CSV·Java source **0** |
| 정리·경계 | 이번 MySQL·앱·artifact 컨테이너, DB/CA/secret 볼륨과 네트워크 제거; 기존 개발 DB 보존. `.gitignore`·기존 ignored 파일·추적 정책, branch/worktree와 Git 게시 상태 변경 없음 |

검증 보조 절차에서는 Docker internal network의 호스트 포트 미게시, restart 후 임시 호스트 포트 재할당, 실행 JAR의 `META-INF` 경로 가정을 바로잡았다. 제품 코드 변경 없이 로컬 bridge·포트 재조회·실제 JAR 경로 검사로 최종 smoke 전체를 재실행해 정상 종료했다. 로그와 테스트 보고서는 위 ignored 검증 디렉터리 상위/하위에만 남기며 secret 원문을 문서로 복사하지 않는다. 전체 원본 식품 재적재와 TLS negative/entrypoint 실패 7종은 이번에 재실행하지 않았고 §7.1 선행 증거를 유지한다.

남은 검증은 실제 provider의 eligibility·TLS/allowlist·사용량·원격 import·실메일·공식 core HTTP smoke와 Render bootstrap/승인 SHA 배포 및 UI 보조 확인이다. 이들은 미실행이며 **Do/feature 완료로 판정하지 않는다**. `pdca`·deployment 스킬은 승인된 보완·로컬 재검증과 증거 분리에만 적용했고, bkit 세션 미초기화로 자동 보조 검사 대신 저장소 authority를 직접 확인했다. API 기능·DB schema 변경과 provider 계정·실제 credential·외부 배포·Git 게시는 없다.

## 8. SMALL-DEPLOYMENT-003 실행 계약 — 외부 실행 미승인

이 절은 승인된 `FOUNDATION-006-R5`, `SMALL-DEPLOYMENT-001-R2`, `SMALL-DEPLOYMENT-003`에 따른 실행 계약이며 §§1, 3–6의 충돌하는 실메일·auth 발급 흐름을 대체한다. §2의 재현성/보안 검증과 §7의 기존 증거는 유지한다. 이후 사용자가 승인한 구현·로컬 검증만 수행했으며 실제 외부 실행은 여전히 별도 승인 대상이다.

### 8.1 상태와 시작 gate

| 항목 | 현재 상태 |
|---|---|
| 기존 `SMALL-DEPLOYMENT-002/R1` 로컬 Do | §7의 검증 기록 보존; 외부 배포나 feature 완료를 의미하지 않음 |
| R5 / Plan R2 / Design 003 | 문서 결정 승인 완료; 구현 승인과 구분 |
| 새 배포 설정·seed·JWT 도구 | 구현됨; 기존 JWT 도구는 테스트 문맥 준비에만 사용, 인증 기능 추가 확장 없음 |
| 새 scope의 로컬 test/javadoc·MySQL·Docker | §9의 실제 결과 확인; 기존 141 passed와 분리 |
| Git 게시·provider 계정/credential·원격 seed/import·Render 배포 | 이번 승인 밖, 미실행 |

사용자는 설정·seed·JWT 도구·관련 테스트 구현과 로컬 재검증을 별도로 승인한 뒤, 작업을 핵심 도메인 smoke와 필요한 최소 테스트 사용자 문맥으로 한정했다. 현재 branch `codex/small-deployment`와 Issue #22를 유지한다. 아래 외부 절차까지 실행하려면 추가로 Git 게시와 provider·비용·credential·정확한 SHA 배포/DB 쓰기에 대한 명시적 승인이 필요하다.

### 8.2 로컬 구현 후 재검증 gate

1. SMTP 변수 없이 deployment profile 기동, auth prefix 차단, 기존 JWT/ownership 유지, seed 재실행 안전성 및 로컬 JWT 도구의 비노출/비패키징 계약을 구현 후 검증한다.
2. ignored 파일·이전 산출물이 없는 후보에서 `test javadoc`와 실제 MySQL 8.4.5를 skip 없이 검증한다. 기존 local-auth/Mailpit 회귀 테스트는 유지한다. Docker build/runtime, TLS negative, 공통 default-off와 Render 명시적 Swagger opt-in도 확인한다.
3. suite/passed/failed/errors/skipped/unexecuted, 실행/미실행 항목, 실제 후보와 환경을 새 Do 증거로 기록한다. 기존 §7 표를 수정하거나 새 scope 결과로 복사하지 않는다.

### 8.3 별도 승인 후 외부 준비 순서

1. 승인된 후보 commit과 정확한 SHA를 확보한다. Design §14.7은 기존 작업 브랜치의 승인 SHA로 pre-merge smoke를 수행하는 안이다. 현재 uncommitted 결과는 배포 승인 SHA가 아니다. 새 branch/worktree나 선행 merge를 임의 수행하지 않는다.
2. Aiven DB/user와 CA를 준비한다. Render CIDR을 모르면 DB 외부 접근을 열지 않는다. SMTP2GO 가입·도메인·mail credential은 이 새 scope의 선행조건이 아니다.
3. 연결할 작업 브랜치 HEAD가 승인 SHA인지 확인한 뒤, 별도 서비스 생성 승인 아래 Render Docker Web Service를 Auto-Deploy Off로 생성한다. 이때 발생하는 최초 bootstrap 시도는 acceptance가 아니다.
4. 생성한 서비스의 outbound CIDR 확인 → Aiven 최소 allowlist 설정 → CA secret file·truststore·필수 env 준비 순서로 진행한다. Access JWT 키의 fail-closed 조건은 유지하되 SMTP 설정은 요구하지 않는다.
5. 모든 설정을 마친 뒤 승인 SHA를 수동 배포하고 실제 배포 SHA를 확인한다. 여기서부터 acceptance 대상으로 삼는다. HTTP health는 redirect를 따르지 않은 200이어야 한다. Swagger opt-in/보호 API·Actuator 경계를 확인한다.
6. 원격 DB 쓰기 승인 범위에서 개발자 IP를 임시 허용한다. TLS JDBC로 Flyway 상태 확인 → 승인 원본 food 전체 import/identity·건수·용량 확인 → 합성 A/B seed → 생성된 ID와 필요한 DB 계약 검증을 수행한다. 잘못된 대상/seed 충돌이면 중단하며 기존 사용자를 수정하지 않는다.
7. import·seed·필요 DB 검증이 끝나면 개발자 IP를 제거하고 Render 접근만 유지한다. 제거 확인을 증거로 남긴다. 이후 JWT 발급/HTTP smoke에는 개발자 DB 접속이 필요하지 않다. 추가 DB 정리/접근 재개는 별도 승인이다.

### 8.4 로컬 JWT와 공식 domain smoke

구현된 개발용 명령은 `.\gradlew.bat smokeSeedUsers --console=plain`과 `.\gradlew.bat smokeIssueAccessToken --console=plain`이다. 전자는 `MYSQL_URL`, `MYSQL_USERNAME`, `MYSQL_PASSWORD`, `MYSQL_TRUSTSTORE_URL`, `MYSQL_TRUSTSTORE_PASSWORD`, `NYAM_SMOKE_TARGET`, `NYAM_SMOKE_SEED_OUTPUT`을 process 환경에서 읽는다. 후자는 같은 target/seed 결과와 `NYAM_SMOKE_USER`(`A` 또는 `B`), `NYAM_AUTH_ACCESS_SECRET`, `NYAM_SMOKE_JWT_OUTPUT`을 읽으며 DB에는 접속하지 않는다. 결과 경로는 저장소 밖 사용자 전용 디렉터리의 **존재하지 않는 절대 파일 경로**여야 한다. 재실행도 새 결과 파일을 지정하며 기존 파일을 덮어쓰지 않는다. 값은 명령 인자·공유 로그에 입력하지 않는다. 이 두 명령은 배포 앱의 회원가입/로그인 또는 실제 사용자 토큰 획득 흐름이 아니다.

1. 대상 환경의 seed 결과에 기록된 실제 A/B user_id로만 900초 Access JWT를 로컬 발급한다. seed는 users row만 저장하며 JWT를 DB에 저장하지 않는다. 서명 키/토큰을 채팅, 명령 인자, stdout, 로그나 문서에 출력하지 않는다.
2. 토큰은 사용자만 접근 가능한 로컬 임시 파일/메모리로 전달하고 curl/Postman의 비공개 로컬 세션에서 사용한다. verbose 요청 로그, 환경 export, 공유/cloud sync는 사용하지 않는다. 만료 시 로컬에서 재발급한다.
3. 보호 API에서 JWT 없음·변조·만료 → 401 및 issuer/audience/sub 검증을 확인한다. 유효 JWT로도 auth prefix는 403이며 익명 요청도 차단된다. 메일 발송/계정 쓰기/token·cookie 발급 부작용이 없어야 한다.
4. A JWT로 food 검색/상세 → 비어 있는 테스트 날짜 확인 → meal 생성/날짜 조회 → **삭제 전 non-empty daily-summary**의 snapshot 합계와 complete 규칙을 확인한다.
5. B JWT로 동일 날짜의 meal/summary에 A 데이터가 없음을 확인한다. B가 A meal 삭제 시 `404 MEAL_NOT_FOUND`이고, A 조회에서 여전히 존재해야 한다.
6. A JWT로 이번 smoke에서 만든 자신의 meal만 삭제한 뒤 해당 날짜의 empty summary를 확인한다. 사용자의 다른 meal이나 seed 사용자를 자동 삭제하지 않는다.
7. 공식 증거에는 SHA, 비밀값 없는 시나리오 식별자, HTTP 상태/집계/격리 결과와 실패 원인만 남긴다. JWT·서명 키·DB credential 원문은 제외한다. 제한된 토큰 임시 산출물을 정리한다.

### 8.5 완료·후속 경계

이 결정의 모든 로컬/외부 acceptance를 실행하기 전에는 Do/feature 완료로 판정하지 않는다. 완료되더라도 실제 회원가입·로그인·이메일·refresh/logout·cookie/browser 동작이나 최종 Nyamlog 인증 완료를 주장하지 않는다. 후속 인증 방식/토큰 발급 경로, Vercel browser smoke와 최종 E2E는 별도 범위다. Check 후 PR/merge와 merge 이후 `dev` 배포 대상 전환도 각각 승인·SHA 확인·필요 재검증을 거친다.

## 9. SMALL-DEPLOYMENT-003 로컬 Do 증거 (2026-09-03)

사용자의 구현·로컬 재검증 승인 후 수행한 결과다. 이후 범위 축소·일시 중단·재개 지시에 따라 **가입·로그인·로그아웃·이메일 인증·refresh·실제 사용자 JWT 발급 흐름은 추가 구현/검증하지 않았다.** 이미 만든 seed A/B와 로컬 단기 JWT 도구는 기존 보호 API를 호출할 최소 테스트 문맥으로만 사용했다. 새 SmokeGuard, 공개 발급 endpoint, 임의 userId 입력이나 schema 변경은 없다.

### 9.1 중단 전 완료된 구현과 검증

| 항목 | 확인된 결과 |
|---|---|
| 배포 Security | deployment에서 auth prefix를 서버 수준으로 차단; food/meal/daily-summary의 기존 JWT와 소유자 조건 유지 |
| SMTP 없는 기동 설정 | deployment profile에서 외부 SMTP 환경 변수 요구 제거; 기존 이메일 코드는 보존하고 성공을 위조하는 sender는 추가하지 않음 |
| 합성 사용자 seed | users A/B만 트랜잭션으로 생성·검증, DB 생성 ID 회수, 반복 시 ID 유지, 충돌 rollback 테스트 통과; 인증 credential/동의/refresh row 생성 안 함 |
| 로컬 JWT 문맥 도구 | seed manifest의 A/B만 선택, 기존 900초 AccessTokenIssuer 사용, private 파일 출력; 배포 JAR에는 도구 미포함 |
| 작업트리 `test javadoc` | **46 suites / 150 passed / failures 0 / errors 0 / skipped 0**; 실제 MySQL 8.4.5 포함 |
| clean export `test javadoc` | **46 suites / 150 passed / failures 0 / errors 0 / skipped 0**; ignored 설정 없이 별도 실행, JavaDoc 성공(기존 경고 78개, 도구 경고 0) |
| 도메인 integration test | A food 검색/상세·meal 생성/조회·삭제 전 summary, B 비노출·삭제 404·A 원본 보존, A 삭제 후 empty 검증 |
| clean-export Docker build | `nyam-small-deployment:003-clean-recheck`, image ID `0e6b84759c21`; 승인 후보 export로 빌드 성공 |

clean export는 저장소 밖 임시 `nyam-small-deployment-003-clean-20260903` 디렉터리에 **209개** 추적/신규 비ignored 파일을 복사한 미commit 후보다. `.git`, ignored `application.yml`/`application.properties`·`.env`, 원본 CSV와 이전 build 산출물 없이 시작했다. 재개 시 현재 209개 파일의 내용 일치와 두 실행의 XML 결과를 다시 읽어 확인했다. 위 150건에는 중단 전의 기존 local-auth 회귀도 포함되지만, 이를 이번 배포의 실제 회원가입·로그인 완료 증거로 해석하지 않는다.

### 9.2 재개 후 추가 완료한 Docker 도메인 검증

제품 Java·설정·테스트 소스는 추가 수정하지 않았으며 전체 `test/javadoc`를 다시 실행하지 않았다. 위와 내용이 같은 이미지에 격리된 MySQL 8.4.5와 임시 로컬 credential/CA만 연결했다. 호스트 포트는 loopback에만 공개하고 기존 개발 DB는 사용하지 않았다.

| 실제 실행 항목 | 결과 |
|---|---|
| SMTP 환경 변수 없이 기동 | health 최초 **200**, 본문 status-only; OpenAPI 변수 미지정 시 docs/UI **404**, 명시적 opt-in 시 docs/UI **200** |
| Docker/MySQL | **512 MiB / 1 CPU / UID:GID 1000:1000 / PORT=19090**, Flyway 성공 **7**, `require_secure_transport=ON`, 앱의 암호화된 TLS 세션 관찰 |
| truststore | CA를 읽어 PKCS12 준비 후 기동; 디렉터리 **0700**, store **0600**; `VERIFY_IDENTITY`와 fallback 차단 유지 |
| seed CLI | 로컬 PC → TLS JDBC → 격리 DB에 A/B 생성; 새 manifest로 반복 실행해 같은 ID 확인 |
| HTTP 도메인 smoke | A food 검색/상세 **200** → meal 생성 **201**/날짜 조회 **200** → 삭제 전 itemCount **1**, energy **180**, complete **true** |
| A/B ownership | B 목록/summary에 A 데이터 없음, B 삭제 **404 MEAL_NOT_FOUND**, A 원본 보존 확인 |
| A 정리 | 자신이 생성한 meal 삭제 **200** → empty summary itemCount **0**, energy **0**, complete **true** |
| DB 잔여 상태 | users **2**, local_credentials/consents/challenges/refresh_tokens/meals 각각 **0**; 정상 회원가입 fixture로 취급하지 않음 |
| 비노출·artifact | 앱 로그에 검사한 임시 key/password/JWT 원문 없음. 최종 JAR의 smoke 도구·ignored 설정·`.env`·CSV·Java source **0**, deployment/defaults/V1–V7 포함 |

이번 HTTP 검증은 로컬 자동 클라이언트로 수행했다. **Render/Aiven 대상 공식 curl/Postman acceptance는 미실행**이다. 중단 전 실패한 로컬 TLS 준비와 seed CLI 시도는 성공 증거에 포함하지 않으며, 이번에는 Linux volume의 명시적 인증서 권한과 비어 있지 않은 임시 DB credential로 전체 경로를 통과했다. 이전 실패 원인을 인증서 형식 하나로 단정하지 않는다.

이번 실행에서 생성한 컨테이너·네트워크·DB/인증서/secret 볼륨과 private JWT/manifest/truststore 임시 파일은 정리했다. 기존 개발 DB 및 기존 검증 산출물은 보존했다. 재개 후 변경 파일은 이 runbook, Plan/Design의 상태 안내, PDCA와 ignored 로컬 검증 보조 스크립트뿐이다.

### 9.3 미실행과 다음 경계

- 원본 식품 **317,766건** 전체 재적재, 잘못된 CA/hostname negative와 bootstrap 실패 7종은 이번 재개에서 재실행하지 않았다. 변경하지 않은 import/TLS bootstrap 구현의 §7 선행 증거와 구분한다. 이번 Docker domain smoke는 합성 food **1건**을 사용했다.
- provider 계정·실제 credential·원격 import/seed·allowlist 제거·Render bootstrap/승인 SHA 배포·원격 용량/지연·공식 curl/Postman smoke는 **미실행**이다. SMTP2GO와 실제 auth 발급 흐름은 잔여 배포 gate가 아니라 **후속 범위**다.
- `small-deployment`는 **Do 유지 / 승인된 로컬 작업 마무리 / 외부 acceptance 대기**다. feature 완료, Check 전환, match-rate를 선언하지 않는다. Git index/branch/worktree, stage/commit/push/PR/merge와 Issue는 이번 재개에서 변경하지 않았다.
