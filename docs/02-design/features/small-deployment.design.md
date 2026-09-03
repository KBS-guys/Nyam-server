# small-deployment - Design Document

> **Revision**: 2.0.0 / `SMALL-DEPLOYMENT-003` — Approved (2026-09-03). 현재 설계는 §14이다. <br>
> §§1–13는 승인된 `SMALL-DEPLOYMENT-002/R1` 원문을 보존하며, 충돌하는 항목은 §14가 대체한다. 기존 `do` 기록과 이 설계 승인은 새 구현 승인을 뜻하지 않는다.

> **이후 실행 상태**: 별도 구현·로컬 검증 승인 및 재개 결과는 [runbook §9](../../runbooks/small-deployment.md#9-small-deployment-003-로컬-do-증거-2026-09-03)와 PDCA를 따른다. §14의 구현 미실행/미승인 문장은 문서 승인 당시 기록이다. 기존 seed/JWT 도구는 테스트 문맥에만 사용하고 실제 사용자 auth 발급 기능은 추가 구현하지 않는다.

> **Summary**: Render Free·Aiven MySQL 8.4에서 seed A/B와 기존 JWT/ownership을 이용하는 첫 백엔드 개인 배포 설계
>
> **Version**: 2.0.0 <br>
> **Date**: 2026-09-03 <br>
> **Status**: Approved <br>
> **Decision**: `SMALL-DEPLOYMENT-002`, `SMALL-DEPLOYMENT-002-R1`, `SMALL-DEPLOYMENT-003` <br>
> **Related Plan**: `docs/01-plan/features/small-deployment.plan.md` (`SMALL-DEPLOYMENT-001`, `SMALL-DEPLOYMENT-001-R1`, `SMALL-DEPLOYMENT-001-R2`) <br>
> **Related Issue**: [#22](https://github.com/KBS-guys/Nyam-server/issues/22)

---

## 1. 설계 목표와 경계

승인된 한 commit의 Spring Boot API를 Render에 수동 배포하고, 외부 MySQL·실제 인증 메일·기존 핵심 API가 함께 동작함을 HTTP smoke로 증명한다.

- 이 배포는 개인 학습·포트폴리오와 제한된 시험용이며 상용 production SLA를 주장하지 않는다.
- Vercel, browser cookie, social-login, custom domain, CI/CD, HA와 종합 monitoring은 포함하지 않는다.
- 공개 API와 database schema는 변경하지 않는다. 구현 중 migration 필요성이 생기면 Design을 다시 승인받는다.
- provider 계정 생성, 비용 지출, credential 입력과 실제 배포는 이 Design 승인과도 별도다.

## 2. 배포 구성과 provider 결정

```text
curl / Postman
      -> HTTPS -> Render Free Docker Web Service (Singapore)
                     -> TLS JDBC -> Aiven for MySQL 8.4
                     -> STARTTLS 2525 -> SMTP2GO

개발자 PC -> TLS JDBC -> Aiven (최초 food import 동안만 허용)
```

| 역할 | 결정 | 허용 한계와 중단 조건 |
|------|------|-----------------------|
| Backend | Render Free Web Service, Docker runtime, Singapore region | idle spin-down, 약 1분 cold start, 임의 restart와 Free 한계를 수용한다. |
| Database | Aiven for MySQL Free, MySQL 8.4.x | 1GB·single node·리전 선택 불가·무 SLA를 수용한다. 용량 또는 지연 gate 실패 시 멈춘다. |
| Mail | SMTP2GO Free를 1차 선택으로 두며 계정·sender eligibility 확인 후 확정; port 2525와 STARTTLS 필수 | 월 1,000건·일 200건의 서로 다른 초과 동작은 §9를 따른다. 가입·sender·TLS 조건 미충족 시 멈춘다. |

Aiven Developer 이상이나 다른 유료 plan, 다른 provider와 HTTP mail adapter로의 전환은 자동 fallback이 아니다. 비용과 구현 범위를 제시하고 별도 승인을 받은 뒤 Design을 개정한다.

## 3. Render service와 container

- source는 `KBS-guys/Nyam-server`의 `dev` branch로 연결하되 auto-deploy를 끈다.
- 서비스 생성 직전 원격 `dev` HEAD가 승인 SHA인지 확인한다. Create Web Service는 최초 build/deploy를 시작하며 Auto-Deploy Off가 이 bootstrap 시도까지 막는다는 뜻은 아니다. 서비스 생성·bootstrap도 별도 실제 외부 작업 승인 대상이며 공식 acceptance deploy로 세지 않는다.
- Aiven 접근을 닫은 채 Render 서비스를 생성한 뒤 service 화면의 outbound CIDR을 확인해 등록하고 CA·환경 설정을 완료한다. 그 후 승인된 정확한 commit SHA를 수동 deploy 대상으로 선택하고 Deploys/Events에서 SHA를 다시 확인한다. 이 수동 배포부터 공식 acceptance를 판정한다.
- Blueprint와 pre-deploy command는 사용하지 않는다. Flyway와 Hibernate validation은 애플리케이션 기동에서 수행한다.
- multi-stage Docker build는 Gradle Wrapper로 Java 17 `bootJar`를 만들고 Java 17 runtime에서 JAR 하나만 실행한다.
- runtime process는 group `1000`에 속한 non-root 사용자로 실행해 Render secret file을 읽을 수 있어야 한다. source, Gradle cache, 원본 CSV와 `.env`를 image에 넣지 않는다.
- Java 17 runtime image에 `keytool`을 포함하고, entrypoint는 §5의 truststore 준비 성공 후에만 JVM을 실행한다.
- `server.address=0.0.0.0`, `server.port=${PORT:8080}`로 Render의 runtime port를 사용한다.
- graceful shutdown을 켜고 짧은 종료 유예만 둔다. Free instance의 restart나 무중단을 보장한다고 표현하지 않는다.
- secret은 runtime 환경 변수 또는 `/etc/secrets` 파일로만 주입하며 Docker `ARG`, image layer와 build log에서 참조하지 않는다.

## 4. 배포 profile과 환경 변수

배포 전용 추적 설정은 별도 `deployment` Spring profile에 두고 로컬 Mailpit 기본값을 유지한다. Render에는 `SPRING_PROFILES_ACTIVE=deployment`를 설정한다.

공통 OpenAPI default-off와 Flyway/Hibernate 안전 기본값은 ignored `application.yml`·`.env` 없이도 최종 artifact 자체에서 제공한다. profile·환경 변수의 명시적 설정은 우선 적용하며 실제 credential은 공통 기본값에 포함하지 않는다. 기존 ignored 파일과 Git 추적 정책은 변경하지 않는다.

| 이름 | 분류 | 용도 |
|------|------|------|
| `PORT` | Render 제공 | HTTP listen port |
| `MYSQL_URL` | secret 취급 | endpoint와 database를 지정하는 JDBC URL; credential과 TLS·truststore 옵션은 넣지 않는다. |
| `MYSQL_USERNAME`, `MYSQL_PASSWORD` | secret | Aiven application service user |
| `MYSQL_TRUSTSTORE_URL` | 일반 설정 | Render는 `file:/tmp/nyam-mysql/aiven-truststore.p12`; 개발자 PC는 로컬 임시 truststore의 file URL |
| `MYSQL_TRUSTSTORE_PASSWORD` | secret | PKCS12 생성과 Connector/J 로딩에 쓰는 별도 runtime 환경 변수 |
| `NYAM_AUTH_ACCESS_SECRET` | secret | Access Token HS256 key |
| `NYAM_EMAIL_VERIFICATION_HMAC_SECRET` | secret | 이메일 인증번호 verifier key |
| `NYAM_MAIL_HOST`, `NYAM_MAIL_PORT` | 일반 설정 | `mail.smtp2go.com`, `2525` |
| `NYAM_MAIL_USERNAME`, `NYAM_MAIL_PASSWORD` | secret | SMTP credential |
| `NYAM_MAIL_FROM` | 개인 설정 | 검증된 단일 sender 주소 |
| `NYAM_OPENAPI_ENABLED` | 일반 설정 | Render 개인 배포에서 `true`; 공통 기본값 `false` 유지 |

- database pool은 단일 instance 기준 maximum 5, minimum idle 0과 bounded connection timeout으로 제한한다.
- mail은 SMTP auth, STARTTLS enable과 STARTTLS required를 모두 켜고 connection/read/write timeout을 각각 5초로 둔다.
- 현재 Mailpit 전용 `JavaMailSender` 구성은 위 외부 설정을 읽도록 바꾸되, 메일 실패 시 `EMAIL_DELIVERY_UNAVAILABLE`과 challenge transaction rollback 계약은 유지한다.
- 실제 값, token, 인증번호와 credential은 문서·명령 history·응답 증거·애플리케이션 log에 남기지 않는다.

### 4.1 공개 Swagger/OpenAPI 계약

- `FOUNDATION-006-R4`와 `SMALL-DEPLOYMENT-001-R1`에 따라 Render에서 `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`를 익명 공개하고 Try it out을 허용한다. OpenAPI 대상은 기존 `/api/v1/**`로 유지하며 Actuator·내부 관리 기능은 추가하지 않는다.
- Swagger는 같은 Render HTTPS origin의 API를 호출하는 수동 클라이언트다. 기존 Bearer 인증, 사용자 소유권, Refresh Token cookie와 CSRF 계약을 그대로 적용하고, Swagger 동작을 위해 인증 우회나 CORS·cookie 보안 완화를 추가하지 않는다.
- 공용 계정·token을 미리 주입하지 않고 Swagger UI의 Authorization 값 영속 저장을 켜지 않는다. 문서 예시·기본값·설정·로그·검증 증거에 실제 secret과 개인정보를 넣지 않는다.
- Try it out은 실제 DB 변경과 메일 발송을 수행할 수 있음을 안내하고, 메일 quota와 실패 계약은 §9를 따른다. UI의 문서 로딩과 대표 요청 실행 가능 여부는 보조 확인하며, 공식 core smoke와 cookie 증거는 §10의 curl/Postman만 사용한다.

## 5. MySQL TLS와 네트워크 경계

- Aiven에서 MySQL 8.4를 명시 선택하고 애플리케이션 전용 database와 service user를 만든다.
- Aiven CA PEM은 `/etc/secrets/aiven-ca.pem`에 제공한다. non-root entrypoint는 매 cold start/restart에서 해당 CA로 `/tmp/nyam-mysql/aiven-truststore.p12`를 새로 생성하며 이전 파일을 재사용하지 않는다. 디렉터리와 파일은 실행 사용자만 접근하도록 각각 `0700`, `0600`으로 제한한다.
- `keytool`은 암호를 `-storepass:env MYSQL_TRUSTSTORE_PASSWORD`로 읽는다. 도구·CA·암호 누락, 읽기·변환·권한 실패 시 JVM을 시작하지 않고 비민감 오류만 남긴다. shell tracing을 켜거나 암호를 명령 인자 값으로 확장하지 않는다.
- datasource의 Connector/J properties로 `sslMode=VERIFY_IDENTITY`, `fallbackToSystemTrustStore=false`, `trustCertificateKeyStoreType=PKCS12`를 고정하고 `trustCertificateKeyStoreUrl`과 `trustCertificateKeyStorePassword`에는 각각 §4의 환경 변수를 전달한다. URL과 JVM 명령 인자에는 암호를 넣지 않는다.
- 개발자 PC import도 같은 CA에서 저장소 밖에 만든 임시 PKCS12와 위 다섯 Connector/J properties를 적용한다. truststore는 image와 저장소에 포함하지 않고 CA·hostname 검증 실패 시 TLS 수준을 낮추지 않는다.
- Aiven 기본 `0.0.0.0/0`·`::/0` 허용은 제거하고 Render service 화면에 표시되는 전체 shared outbound CIDR만 등록한다.
- import 직전에 현재 개발자 public IP 한 개를 `/32`로 추가하고, import 검증 직후 제거한다. 제거 후 개발자 PC 연결 실패와 Render 연결 유지를 함께 확인한다.
- Render shared CIDR은 전용 IP가 아니므로 같은 region의 다른 tenant와 공유된다. TLS server identity와 database credential이 추가 경계이며, 전용 outbound IP 구매는 이 범위에서 제외한다.

## 6. Migration, 용량과 최초 food import

- 빈 Aiven database에서 V1~V7 Flyway가 순서대로 성공한 뒤 Hibernate `ddl-auto=validate`가 통과해야 Web Service가 기동한다.
- 구현 전 로컬 MySQL 8.4.5의 전체 317,766건 import 후 `information_schema`로 schema의 data+index 크기를 측정한다.
- 용량 gate: 예상 크기 또는 Aiven import 후 사용량이 700MB를 넘으면 1GB Free plan을 사용하지 않는다. Aiven Developer 8GB를 용량 대안으로 제시하되 비용 승인 전 중단한다.
- 지연 gate: cold start와 초기 기동을 마친 뒤 §10의 food 검색·상세, meal 날짜 조회와 daily-summary를 요청별 10초 client timeout으로 확인한다. timeout이면 완료 판정을 중단하고 원인을 확인한다. 이는 부하 시험이나 응답시간 SLA가 아닌 소규모 smoke의 완료 한계다.
- 인증·TLS·quota·일시 장애와 구분해 provider 위치가 지연의 원인으로 확인되면, Aiven Developer의 Asia Pacific geographical area 배치 가능성을 먼저 검토한다. Developer는 넓은 geographical area는 선택할 수 있지만 exact cloud/region은 선택할 수 없으며, APAC 배치만으로 지연 개선을 보장하지 않는다.
- 기존 Free service의 단순 업그레이드가 위치 변경을 보장한다고 가정하지 않고 새 service 생성·데이터 migration 필요성을 확인한다. exact cloud/region이 필요하면 이를 지원하는 Professional 이상 또는 다른 Managed MySQL을 후보로 제시한다. 전환·service 생성·데이터 migration은 Design 재승인과 비용을 포함한 별도 외부 작업 승인 전 실행하지 않는다.
- 개발자 PC에서 기존 `foodImport` Gradle task를 실행하며 원본 경로는 비영속이고 release date와 SHA-256만 Job identity로 저장한다.
- 성공은 Job과 모든 Step `COMPLETED`, read/write 317,766, filter/skip/rollback 0, `foods`와 고유 외부 코드 317,766으로 판정한다.
- 완료된 같은 release date+checksum 재실행은 `JobInstanceAlreadyCompleteException` 계열의 명시적 실패여야 하며 `foods` 수가 바뀌지 않아야 한다.
- 실패 실행은 같은 identity와 동일 checksum 원본으로 checkpoint 재개한다. 임의 삭제·Flyway repair·새 identity 우회는 하지 않는다.

## 7. MySQL 의미 검증

원격 provider에서 다음을 관찰해 로컬 MySQL 의미가 유지됨을 확인한다.

- `SELECT VERSION()`은 8.4.x이며 Flyway V1~V7이 모두 success다.
- `foods.food_name`, `foods.normalized_name`과 meal 이름 snapshot은 `utf8mb4_0900_bin`이다.
- 대표 FK, CHECK와 UNIQUE 위반은 저장되지 않고 transaction이 rollback된다.
- `ABC`와 `abc`, `cafe`와 `café`의 food 변경 감지·검색 구분이 기존 실제 MySQL test와 같다.
- meal snapshot은 원본 food 변경 뒤에도 보존되고 다른 사용자의 meal과 daily-summary는 노출되지 않는다.

검증 query는 credential, 사용자 개인정보, 식품 원문 전체나 내부 오류를 evidence에 복사하지 않는다.

## 8. Render health check

- `spring-boot-starter-actuator`를 추가하고 Render 전용 group을 `GET /actuator/health/render`에 둔다.
- group은 기본 indicator인 `ping`만 포함하고 `livenessState`·`readinessState`와 DB·SMTP contributor는 포함하지 않는다. 별도 availability probe 활성화 없이 애플리케이션의 HTTP 응답 가능 여부만 확인하며, 전체 핵심 기능의 정상 동작은 §10의 smoke로 검증한다.
- startup에서는 Flyway·Hibernate가 DB 연결을 이미 검증한다. runtime 외부 장애로 health가 실패해 Render가 정상 JVM을 반복 제거하는 위험을 피하기 위해 DB를 probe에 넣지 않는다.
- response는 status만 표시하고 component와 detail은 항상 숨긴다. HTTP에는 health만 expose하고 Actuator discovery를 끈다.
- SecurityFilterChain은 위 정확한 GET path만 anonymous 허용하고 나머지 `/actuator/**`는 거부한다.
- Render health path도 `/actuator/health/render`로 설정한다. Render 자체는 5초 이내의 2xx·3xx를 healthy로 판정하고 timeout, 4xx와 5xx는 실패로 본다. Nyamlog 완료 조건은 더 엄격하게 redirect를 따라가지 않은 최초 응답의 HTTP 200으로 고정해 로그인 redirect 등 잘못된 인증 설정을 성공으로 오인하지 않는다.

## 9. 실제 메일 계약

- 별도 외부 작업 승인 후 메일 provider 관련 첫 단계로 가입·sender eligibility를 확인한다. SMTP2GO 가입에는 업무용 또는 소유 도메인의 이메일이 필요하며 Gmail/Outlook/Yahoo 같은 shared-domain 주소는 허용되지 않는다. single sender는 검증 가능 여부와 해당 도메인의 DMARC 제한을 확인한 뒤 확정한다.
- eligibility 충족과 별도 구현 승인 후 SMTP2GO의 검증된 sender와 별도 SMTP username/password를 사용한다. 계정 로그인 암호를 사용하지 않는다.
- port 2525에서 STARTTLS 협상이 되지 않으면 plaintext로 downgrade하지 않고 발송을 실패시킨다.
- 실제 수신함에서 제목·유효기간·6자리 코드 도착을 확인하되 코드 원문은 evidence에 남기지 않는다.
- SMTP 인증·TLS 실패, timeout 또는 provider의 동기적 rejection(월간 hard quota 초과 거부 포함)은 기존 `503 EMAIL_DELIVERY_UNAVAILABLE`로 반환하고 challenge 변경을 rollback한다.
- 일일 quota 초과는 provider가 성공으로 accept한 뒤 queue에서 지연 전송할 수 있으므로 즉시 503이나 rollback을 보장하지 않는다. SMTP 수락과 실제 inbox 수신은 별개이며, 정상 저용량 smoke는 인증번호 유효기간 안의 실제 수신까지 확인한다.
- eligibility를 충족하지 못하면 SMTP2GO 연동 구현 전에 중단하고 provider 재선정과 Design 재승인을 요청한다. 계정 우회·발신자 사칭·도메인 자동 구매를 하지 않는다.

## 10. core HTTP smoke 순서

공식 smoke는 공개 Swagger와 별개로 curl cookie jar 또는 같은 기능의 Postman collection으로 수행한다. Swagger 실행 결과로 아래 순서와 증거를 대체하지 않는다.

1. §8의 health HTTP 200 조건을 curl/Postman의 자동 redirect 추적을 끈 상태로 확인하고, 공개 Swagger UI 진입점과 OpenAPI JSON의 정상 제공을 확인한다.
2. 실제 이메일 수신 → signup → login을 수행한다.
3. Access Token은 response body에만 있고 인증 응답이 `Cache-Control: no-store`인지 확인한다.
4. login cookie의 `Secure`, `HttpOnly`, `SameSite=Strict`, `Path=/api/v1/auth`를 확인한다.
5. Bearer token으로 `/me`, food 검색·상세, meal 생성·날짜 조회를 수행한다.
6. meal 삭제 전에 daily-summary의 `mealItemCount > 0`과 snapshot 합계를 확인한다.
7. meal 삭제 뒤 선택적으로 empty summary를 확인한다.
8. cookie jar로 refresh하고 rotation된 `Set-Cookie`를 확인한 뒤 logout의 `Max-Age=0` 삭제 cookie를 확인한다.

HTTP status, 공개 code, 비민감 header, count와 승인 commit SHA만 증거로 남긴다. Access/Refresh Token, cookie value, verification code, 이메일 주소와 provider credential은 저장하지 않는다. 이 결과로 browser cookie나 Vercel rewrite 동작을 주장하지 않는다.

## 11. 실패와 rollback

- 첫 배포에는 이전 정상 deploy가 없으므로 health 실패 시 public service가 준비되지 않은 상태로 남는 것을 수용한다.
- 이후 배포는 health 통과 전 기존 정상 deploy가 traffic을 유지하는 Render 동작을 이용한다.
- 이번 범위는 새 migration을 만들지 않으므로 이전 승인 commit 재배포가 기본 rollback이다.
- Flyway가 일부 적용됐거나 schema compatibility가 의심되면 commit만 되돌리지 않고 중단한다. destructive SQL, Flyway repair와 수동 row 삭제는 별도 승인이 필요하다.
- DB·mail provider 장애, Free quota와 cold start는 runbook에 구분하고 상용 availability 보장으로 확대하지 않는다.

## 12. 검증과 구현 범위

- 설정 test는 port/profile, `ping-only` health group의 정상 기동과 §8의 HTTP 200 계약, OpenAPI 공통 기본 비활성·Render 명시 활성화 및 Try it out 허용 설정, SMTP TLS/auth binding을 확인한다. 기존 OpenAPI 기본 비활성·민감값 비노출 회귀 test는 유지한다.
- security test는 필터를 적용한 상태에서 §4.1의 문서 익명 접근, 보호 API의 기존 인증·소유권 경계, Actuator 중 정확한 health path만 공개되고 나머지 endpoint가 거부됨을 확인한다.
- mail integration test는 Mailpit 회귀를 유지하며 외부 provider credential을 자동 test에 넣지 않는다.
- container 검증은 non-root/group `1000`의 CA 읽기, `keytool` 존재, 반복 시작 시 truststore 재생성, 입력 누락·권한·변환 실패 시 기동 중단과 다섯 Connector/J property 전달을 확인한다. 검증에는 임시 비운영 CA·설정만 사용한다.
- `test javadoc`, Docker image build·non-root 실행·`$PORT` local smoke와 실제 MySQL 8.4.5 관련 test를 통과한 뒤에만 외부 배포를 요청한다.
- 위 검증은 ignored 파일·기존 build 산출물이 없는 clean checkout 또는 동등한 배포 후보 export에서도 통과해야 한다. commit 전 export는 현재 추적 파일과 승인된 신규 파일의 내용을 복사하고 원본과 일치함을 확인하며, 새 Git branch/worktree를 만들지 않는다. 공통 OpenAPI default-off와 명시적 opt-in을 모두 검증한다.
- 외부 단계에서는 Aiven version/TLS/allowlist/Flyway/constraint/import와 SMTP2GO 실제 수신, 전체 core smoke를 수동 증거로 확인한다.
- 짧은 runbook은 승인 SHA 배포, CA·환경 변수 이름, health, import 접근 추가·제거, smoke와 rollback 순서만 담고 secret 값은 담지 않는다.

구현 대상은 Dockerfile·`.dockerignore`, Gradle dependency, 공통 안전 기본값·deployment profile, mail/health/security 설정과 관련 test, runbook이다. 최초 Design 승인과 이후 실행 승인은 구분하며 현재 승인 범위와 단계는 `docs/.pdca-status.json`을 따른다.

`SMALL-DEPLOYMENT-002`는 2026-09-03 사용자의 전체 승인으로 Design `0.4.0`의 설계 내용을 변경 없이 확정하고 `1.0.0 Approved`로 기록한다. 이번 승인은 Design 단계만 완료하며 Do 전환, 구현, branch, 외부 작업, 실제 배포나 Git 게시를 승인하지 않는다.

`SMALL-DEPLOYMENT-002-R1`은 2026-09-03 사용자의 보완·로컬 재검증 승인으로 기존 §3의 최초 배포 절차만 bootstrap/acceptance 구분으로 대체하고 §4·§12의 clean checkout 재현성 gate를 명시한다. 나머지 계약은 유지한다. 관련 로컬 문서·설정·테스트 수정과 Do 증거 갱신만 승인되었으며 branch/worktree 추가, 추적 정책 변경, Git 게시, provider 계정·실제 credential·외부 배포는 포함하지 않는다.

## 13. 근거 자료

- [Render Free](https://render.com/docs/free), [Web Services](https://render.com/docs/web-services), [Outbound IP Addresses](https://render.com/docs/outbound-ip-addresses), [Secrets](https://render.com/docs/configure-environment-variables), [Docker secret 권한](https://render.com/docs/docker-secrets), [Health 판정](https://render.com/docs/health-checks)
- [Aiven MySQL Free](https://aiven.io/docs/products/mysql/concepts/mysql-free-tier), [IP allowlist](https://aiven.io/docs/platform/howto/restrict-access), [MySQL version](https://aiven.io/docs/products/mysql/howto/manage-mysql-version), [tier별 용량·region 조건](https://aiven.io/pricing/mysql), [Developer geographical area와 exact region 구분](https://aiven.io/developer-tier-pg-mysql)
- [SMTP2GO TLS/STARTTLS](https://support.smtp2go.com/hc/en-gb/articles/223087527-TLS-STARTTLS-and-SSL-Secure-Connections), [Free plan](https://support.smtp2go.com/hc/en-gb/articles/223087947-Free-Plan), [가입 조건](https://support.smtp2go.com/hc/en-gb/articles/12747932085145-Quick-Start-Guide), [sender 검증 조건](https://support.smtp2go.com/hc/en-gb/articles/9150216032537-Verified-Senders-Sender-Domain-vs-Single-Sender-Emails)
- [Spring Boot 3.5 Actuator health groups](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html), [Connector/J TLS properties](https://dev.mysql.com/doc/connectors/en/connector-j-connp-props-security.html), [Java 17 keytool](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html)
- [Swagger UI 실행·인증 정보 저장 설정](https://swagger.io/docs/open-source-tools/swagger-ui/usage/configuration/)

## 14. SMALL-DEPLOYMENT-003 — seed/JWT 기반 전체 도메인 smoke

### 14.1 결정 상태와 제한적 대체 범위

**Approved (2026-09-03).** 선행 범위는 승인된 `FOUNDATION-006-R5`, `SMALL-DEPLOYMENT-001-R2`다. 아래 명시한 변경만 기존 `SMALL-DEPLOYMENT-002/R1`을 대체하며, 기존 결정/승인문/Do 기록은 보존한다. 현재 승인은 세 문서 결정과 PDCA 승인 기록에 한정된다.

| 기존 승인 기준 | `SMALL-DEPLOYMENT-003`에서 적용하는 변경 |
|---|---|
| §§1–2, 4, 9: 실메일·SMTP2GO 전제 | 이번 배포 모드에서 외부 SMTP 설정/발송·eligibility를 제외; 아래 auth 차단과 SMTP 없는 기동 적용 |
| §3: `dev` HEAD를 최초 서비스 생성 기준으로 사용 | 아래 §14.7의 승인된 작업 브랜치 SHA로 pre-merge acceptance 수행하는 순서 제안; bootstrap 경계 유지 |
| §5: 개발자 IP를 import 동안만 허용 | 승인된 import·seed·필수 DB 확인의 임시 접근 창으로 한정하고 종료 후 제거 |
| §10: 실메일·signup/login·refresh/logout·cookie jar smoke | seed A/B + 로컬 JWT + 기존 보호 API/ownership 전체 smoke로 대체 |
| §12: 기존 scope 검증 | 새 scope의 설정/도구/보안/회귀 검증을 별도 수행; 과거 통과 기록을 재사용하여 완료 처리하지 않음 |

Render/Aiven 선택, Docker Java 17·non-root/group 1000·`$PORT`, 공통 OpenAPI default-off/Render opt-in, TLS `VERIFY_IDENTITY`·PKCS12·fallback 차단·실패 시 기동 중단, MySQL 8.4/Flyway/콜레이션/제약조건/트랜잭션/snapshot, food import identity·용량/지연 gate, ping-only health와 redirect 미추적 HTTP 200, Swagger/보호 API 경계, 비파괴 rollback 계약은 그대로 유지한다. 비용/타 provider 변경은 자동 승인하지 않는다.

### 14.2 배포 모드의 인증·메일 경계

- 기존 deployment profile에서 `/api/v1/auth/**`의 모든 메서드를 서버 수준에서 거부한다. 기존 permitAll보다 우선하며 유효 Access JWT도 우회할 수 없다. `/me`를 포함한 이 prefix의 경로는 이번 smoke에서 사용하지 않는다.
- 차단 요청은 기존 보안 오류 응답 형식을 유지한 401 또는 403으로 끝나며, 유효 JWT의 차단 요청은 403이다. 성공 응답, 로그인 redirect, 토큰/refresh cookie 발급, 메일 발송 또는 계정 관련 쓰기가 발생하지 않아야 한다. Swagger에서 감추는 것만으로 대체하지 않는다.
- food 검색/상세, meal, daily-summary는 기존 JWT 인증을 요구한다. Controller가 SecurityContext의 JWT subject에서 userId를 얻고 Service/Repository의 소유자 조건을 유지한다. API 입력에 userId를 추가하거나 고정 사용자로 바꾸지 않는다.
- 실제 메일 provider와 `NYAM_MAIL_*` 없이 이 배포 모드가 기동한다. 사용하지 않는 메일 구성은 외부 SMTP를 요구/호출하지 않도록 분리하되 발송 성공을 위조하지 않는다. 기존 이메일/인증 소스·테이블·다른 실행 환경의 기능과 회귀 검증은 유지한다.
- 실제 Access JWT 서명 키의 필수 입력·유효성 검증은 유지한다. 메일 제거를 명분으로 인증 키 기본값, insecure fallback 또는 토큰 검증 완화를 도입하지 않는다. 그 밖의 기존 내부 보안 설정은 실제 Bean 의존성에 필요한 경우 유지하며 외부 메일 계약과 구분한다.
- Swagger UI/OpenAPI 익명 열람·Try it out과 same-origin 요청은 유지한다. 예시/기본값/자동 주입에 credential·토큰을 넣지 않고 인증 정보 영속 저장을 금지한다. 공식 smoke는 curl/Postman이다.

### 14.3 테스트 사용자 seed 계약

1. 별도 로컬 개발 도구를 명시적으로 실행해 대상 DB/환경을 확인한 뒤 A/B 두 사용자를 준비한다. Flyway migration, 앱 startup initializer, 공개 HTTP endpoint에는 넣지 않는다. 기존 schema는 바꾸지 않는다.
2. `users`의 필수 display_email/canonical_email, birth_date, created_at을 명백한 합성 값으로 채운다. 예약된 테스트용 도메인의 서로 다른 이메일 식별자와 가상의 성인 생년월일을 사용한다. 실제 개인정보·계정·비밀번호를 사용하지 않는다.
3. user_id는 MySQL 생성값을 회수한다. `1` 등의 고정 ID, `local_credentials`, email verification proof, consent 또는 JWT row는 만들지 않는다. 이 fixture는 정상 회원가입/약관 동의가 완료된 사용자라는 제품 증거가 아니다.
4. 두 사용자 준비는 트랜잭션으로 수행한다. 재실행 시 동일한 fixture임을 필수 속성과 인증 관련 데이터 부재로 확인한 경우에만 기존 ID를 재사용한다. 충돌/예상 밖 데이터/부분 실패는 rollback 및 명시적 실패로 처리하고, 기존 사용자·meal을 덮어쓰거나 자동 정리하지 않는다.
5. 대상 환경에 묶인 A/B 생성/검증 결과와 실제 ID를 비밀값 없는 로컬 결과로 넘긴다. JWT 도구는 이 결과의 A/B만 선택한다. DB credential은 외부 보안 입력으로 공급하며 TLS identity 검증을 유지한다.
6. fixture 정리는 기본 실행에 포함하지 않는다. smoke가 생성한 본인 meal만 정상 API로 삭제한다. 사용자/잔여 DB 데이터 제거가 필요하면 정확한 대상과 FK 영향을 확인한 별도 승인을 받는다. truncate/reset/광범위 delete는 금지한다.

### 14.4 로컬 단기 JWT 도구 계약

- 기존 AccessTokenIssuer/encoder의 HS256, `iss=nyamlog`, `aud=nyamlog-api`, `sub=DB 생성 userId`, 발급 시각 및 900초 만료 계약을 재사용한다. 서버 decoder의 서명·만료·issuer/audience·양의 BIGINT subject 검증도 그대로 사용한다.
- 승인된 대상 환경의 seed 결과에서 A 또는 B를 선택해 발급한다. 임의 사용자 ID 발급 기능, 서버 `/dev/token` endpoint, 정적 smoke token, 별도 SmokeGuard 또는 만료 연장은 만들지 않는다. 만료되면 로컬에서 재발급하며 refresh를 호출하지 않는다.
- 배포와 동일한 키 사용은 이후 credential/외부 작업 승인 아래에서만 허용한다. 이번 문서 작업에서는 키를 생성·읽기·입력하지 않는다. 도구의 권한 제한은 실수 방지이며, HS256 키 보유 자체가 임의 서명 권한이라는 점을 숨기지 않는다.
- 서명 키·JWT를 stdout/stderr, 명령 인자, shell history, 애플리케이션 로그, 테스트 보고서, 문서 또는 Git에 출력/저장하지 않는다. 보안 입력을 사용하고 JWT는 사용자만 접근 가능한 임시 파일 또는 메모리로 전달한다. 권한 제한이 불가능하면 중단한다.
- curl/Postman은 제한된 로컬 파일/비공개 세션에서 토큰을 읽고 요청/응답 verbose 로그·공유 workspace·환경 export·cloud sync로 유출하지 않는다. 실행 후 토큰 산출물을 정리하고 증거에는 시나리오/상태 코드/검증 결과만 남긴다.
- 도구 소스는 개발용으로 버전 관리할 수 있지만 최종 실행 JAR/이미지에는 포함하지 않는다. 비밀값/토큰 산출물은 소스와 별개이며 절대 게시하지 않는다. 자동 앱 기동이나 배포 시 토큰 발급도 없다.

### 14.5 네트워크와 준비 순서

Render/Aiven 계정·서비스·credential·비용과 원격 DB 쓰기는 각각 승인된 범위에서만 실행한다. Render outbound CIDR 허용, CA secret/truststore 설정과 TLS 요구는 기존 §§3–5를 따른다. 개발자 접근은 **승인된 food import + A/B seed + 필요한 DB 검증** 동안만 최소 범위로 허용하고, 종료 즉시 제거한다. 그 이후 로컬 JWT 발급과 HTTPS smoke에는 개발자 DB allowlist가 필요하지 않다. 추후 DB 정리를 위한 재개방은 별도 승인이다.

Flyway 완료 → 원본 identity 확인/전체 food import → A/B seed 및 ID 확인 → 필요한 DB 검증 → 개발자 DB 접근 제거 → 로컬 단기 JWT → 공식 HTTP smoke 순서로 준비한다. 원격 적재량/용량과 seed 결과는 직접 확인하며 과거 로컬 측정값으로 갈음하지 않는다.

### 14.6 공식 smoke와 대표 실패 결과

| 순서/대상 | 관찰 가능한 통과 조건 |
|---|---|
| 1. health / 문서 | health GET 최초 응답 HTTP 200/status-only, docs/UI opt-in 공개; 다른 Actuator는 비공개 |
| 2. 인증 경계 | JWT 없음·변조·만료의 보호 API 요청은 401; 잘못된 issuer/audience/sub도 기존 검증으로 거부; 정상 A/B 토큰 통과 |
| 3. auth 차단 | signup/login/email-verifications/refresh/logout 및 auth prefix의 경로는 익명/유효 JWT 모두 차단; 발송·DB 쓰기·토큰/cookie 발급 부작용 없음 |
| 4. A food | `/api/v1/foods/search` 검색과 food 상세를 JWT A로 호출하고 저장된 영양값/NULL 의미 확인 |
| 5. A meal / non-empty summary | 비어 있는 테스트 날짜를 먼저 확인한 뒤 A meal 생성/날짜 조회 → **삭제 전** daily-summary가 생성한 snapshot 합계/개수/complete 규칙과 일치 |
| 6. B ownership | 같은 날짜 B meal 목록/summary에 A 데이터 미포함; B가 A meal 삭제 시 `404 MEAL_NOT_FOUND`, 이후 A 조회에서 원본 보존 확인 |
| 7. A 정리 / empty summary | A가 자신이 이번에 만든 meal을 정상 API로 삭제 → 같은 날짜 mealItemCount=0, 영양 합계=0, complete=true; 다른 데이터 삭제 금지 |

공식 결과에 실제 signup/login·메일·refresh/logout·cookie 동작이 검증됐다고 적지 않는다. 기존 Meal에는 별도 단건 GET을 신설하지 않고 현재 날짜 목록으로 A 조회/B 격리를 확인한다. API 오류/영양 집계의 기존 계약을 바꾸지 않는다.

### 14.7 승인 SHA·bootstrap·merge 순서

기존 §3의 `dev` 선반영 전제는 원격 smoke가 Issue 완료/merge보다 먼저 필요한 흐름과 충돌할 수 있다. 이 결정은 **기존 `codex/small-deployment`에서 별도 게시 승인 후 고정된 commit을 검증 대상으로 삼고, 별도 외부 승인 후 그 정확한 SHA로 acceptance를 수행하는 순서**로 정한다. 새 branch/worktree나 선행 merge를 요구하지 않는다.

서비스 생성 전 연결할 작업 브랜치 HEAD가 승인 SHA인지 확인하고 Auto-Deploy Off를 지정한다. 최초 생성의 bootstrap 시도는 acceptance가 아니다. 서비스 생성 후 outbound CIDR·DB allowlist·CA/secret/env를 완성한 다음 동일 승인 SHA를 수동 배포하고 배포된 SHA를 확인한다. Render는 연결 저장소의 특정 commit 수동 배포를 지원한다. [공식 deploy 문서](https://render.com/docs/deploys#deploying-a-specific-commit)

외부 smoke와 Check 결과를 갖춘 뒤 PR/merge는 별도 승인한다. merge 후 `dev`로 연결/배포 대상을 바꾸는 작업도 별도 승인과 SHA 확인·재검증 대상이다. 지금은 Git 게시·서비스 생성·bootstrap·배포·merge 중 어느 것도 실행/승인된 것이 아니다.

### 14.8 구현 후 검증과 현재 상태

향후 별도 구현 승인 후 설정 + seed + JWT 도구 + 관련 테스트를 변경하고 `test javadoc`, 실제 MySQL 8.4.5, ignored 입력 없는 clean export, Docker build/runtime 검증을 새로 수행한다. seed 최초/반복/충돌 rollback, 정확한 A/B ID와 토큰 계약, SMTP 없는 기동, auth-prefix 차단/부작용 없음, 기존 local-auth 회귀, 도구/secret의 최종 artifact 제외, JWT 실패와 사용자 격리를 대표 검증에 포함한다. Docker 부재에 따른 skip은 MySQL 통과가 아니다.

기존 runbook §7의 138/141 테스트·MySQL·Docker 기록과 `SMALL-DEPLOYMENT-002/R1` 승인은 그대로 보존한다. 현재 기록상 phase는 `do`이고, **003 Plan/Design 승인 완료 / 새 구현 미실행 / 새 로컬 검증 미실행 / 외부 배포 미실행**이다. Check/완료/match-rate를 새로 선언하지 않는다. 다음 허용 가능한 단계는 설정·seed·JWT 도구·테스트 구현과 로컬 재검증에 대한 별도 사용자 승인이다.

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 2.0.0 | 2026-09-03 | `SMALL-DEPLOYMENT-003` 승인: seed A/B·기존 JWT/ownership·SMTP 없는 auth 차단 배포·전체 domain smoke 및 pre-merge 승인 SHA 순서 확정; 기존 증거 보존, 구현은 미승인 |
| 1.1.0 | 2026-09-03 | `SMALL-DEPLOYMENT-002-R1`: Render 생성 bootstrap과 승인 SHA acceptance 분리, 공통 안전 기본값·clean checkout 재현성 gate; 보완·로컬 검증만 승인 |
| 1.0.0 | 2026-09-03 | `SMALL-DEPLOYMENT-002` 전체 승인: 0.4.0 설계 내용 유지, Design 단계 완료; Do 전환·구현·branch·외부 작업·배포·Git 게시는 미승인 |
| 0.4.0 | 2026-09-03 | 승인된 `FOUNDATION-006-R4`·`SMALL-DEPLOYMENT-001-R1` 반영: Render Swagger/OpenAPI·Try it out 공개, 공식 curl/Postman smoke와 기존 API 보안 유지; Design 전체 승인·구현은 미승인 |
| 0.3.0 | 2026-09-03 | Developer APAC 지연 대안과 위치 이전 조건, ping-only health, redirect 미추적 HTTP 200 smoke의 세 항목 수정; Design 승인·구현은 미승인 |
| 0.2.0 | 2026-09-03 | SMTP quota·eligibility, non-root CA 접근·truststore bootstrap, Aiven 용량/지연 대안 분리의 네 항목 수정; Design 승인·구현은 미승인 |
| 0.1.0 | 2026-09-03 | Render Free, Aiven MySQL 8.4, SMTP2GO STARTTLS 2525, health/import/smoke와 중단 조건을 통합한 Design 초안 |
