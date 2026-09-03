# small-deployment - Plan Document

> **Revision**: 1.2.0 / `SMALL-DEPLOYMENT-001-R2` — Approved (2026-09-03). 현재 범위는 §10이다. <br>
> §§1–9는 이전 승인 기준과 당시 진행 기록을 보존하며, 충돌하는 첫 배포 완료조건은 §10이 대체한다. 이 승인은 구현·검증·Git 게시·외부 작업을 포함하지 않는다.

> **이후 실행 상태**: 별도 구현·로컬 검증 승인 및 재개 결과는 [runbook §9](../../runbooks/small-deployment.md#9-small-deployment-003-로컬-do-증거-2026-09-03)와 PDCA를 따른다. 아래의 구현 미실행 문장은 문서 승인 당시 기록이다. 현재 작업은 최소 seed/기존 JWT 문맥을 이용한 핵심 도메인 smoke에 한정하며 실제 사용자 인증 흐름은 후속으로 유지한다.

> **Summary**: Render·Aiven 환경에서 seed A/B와 기존 JWT를 이용한 전체 핵심 도메인 smoke 검증
>
> **Version**: 1.2.0 <br>
> **Date**: 2026-09-03 <br>
> **Status**: Approved <br>
> **Decision**: `SMALL-DEPLOYMENT-001`, `SMALL-DEPLOYMENT-001-R1`, `SMALL-DEPLOYMENT-001-R2` <br>
> **Related Issue**: [#22](https://github.com/KBS-guys/Nyam-server/issues/22) <br>
> **Scope Authority**: `FOUNDATION-006`, `FOUNDATION-006-R3`, `FOUNDATION-006-R4`, `FOUNDATION-006-R5`, `LOCAL-LOGIN-002`, `FOOD-002`, `MEAL-002`, `DAILY-SUMMARY-002`

---

## 1. 목적

현재 로컬 인증부터 food, meal, daily-summary까지 구현된 Spring Boot 백엔드를 Render Docker Web Service에 처음 배포한다. 외부 Managed MySQL 8.x와 실제 메일 전송을 연결하고, 개발자 PC에서 최초 food import를 수행한 뒤 curl/Postman으로 핵심 API의 배포 환경 동작을 검증한다.

이 단계는 현재 백엔드 수직 흐름의 배포 가능성을 조기에 증명한다. Vercel 프론트엔드, 브라우저 cookie 동작, Google social-login과 최종 E2E/OpenAPI hardening은 `FOUNDATION-006-R3`의 후속 milestone이며 이 Plan에 포함하지 않는다.

## 2. 현재 기준선

- Java 17과 Spring Boot 3.5.10 애플리케이션이며 Gradle Wrapper로 빌드한다.
- `application.yml`은 `MYSQL_URL`, `MYSQL_USERNAME`, `MYSQL_PASSWORD`를 요구하고 Flyway와 Hibernate `ddl-auto=validate`를 사용한다.
- V1~V7 Flyway Migration이 사용자, 인증 challenge, Refresh Token, Spring Batch metadata, food, meal schema를 구성한다.
- 로컬 `docker-compose.yml`은 MySQL 8.4.5와 Mailpit만 제공한다. Render용 Dockerfile, health endpoint와 Actuator dependency는 아직 없다.
- `NYAM_OPENAPI_ENABLED`의 기본값은 `false`라 Swagger UI와 API docs는 명시적으로 활성화하지 않으면 노출되지 않는다.
- 실제 메일 provider 설정은 추적 파일에 없으며, 현재 메일 발송 코드는 Spring `JavaMailSender`를 사용한다.
- 기존 `foodImport` Gradle task는 승인된 CSV 경로, release date와 checksum을 환경 변수로 받아 수동 실행한다.
- 로컬 로그인은 Access Token을 response body에, Refresh Token을 `Secure`, `HttpOnly`, `SameSite=Strict`, `Path=/api/v1/auth` cookie에 전달하고 인증 응답에 `Cache-Control: no-store`를 사용한다.
- email verification, signup, login/refresh/logout/me, food search/detail, meal create/list/delete와 daily-summary API가 현재 smoke 대상이다.

### 2.1 플랫폼 문서 확인 기준

2026-09-03에 확인한 공식 문서를 이 Plan의 시점 기준으로 사용한다.

- [Render Web Services](https://render.com/docs/web-services): Dockerfile 기반 Web Service, 환경 변수와 HTTP health path, 외부 요청을 받을 port binding을 지원한다.
- [Render Deploys](https://render.com/docs/deploys): auto-deploy를 끄고 특정 commit을 수동 배포할 수 있다.
- [Render Free](https://render.com/docs/free): Free Web Service에는 persistent disk, shell, one-off job이 없고 outbound SMTP 25/465/587이 차단된다.
- [Render Health Checks](https://render.com/docs/health-checks): 지정한 path의 2xx/3xx HTTP 응답으로 새 배포의 health를 판정할 수 있다.

## 3. 목표와 비목표

### 3.1 목표

- 승인된 commit 하나를 Render Docker Web Service에 수동 배포한다.
- 외부 Managed MySQL 8.x에서 전체 Flyway Migration과 Hibernate schema validation을 통과한다.
- 실제 메일 provider를 통해 이메일 인증 메일을 수신한다.
- 최초 food import와 로컬 인증 기반 핵심 API smoke를 재현 가능한 절차로 남긴다.
- health check, secret 비노출, TLS와 최소 네트워크 접근 통제를 적용한다.
- 무료 또는 저비용 개인 배포의 cold start와 운영 한계를 명시한다.

### 3.2 비목표

- Vercel Vue 배포, `/api/*` external rewrite와 실제 browser cookie smoke
- Google social-login과 provider identity schema
- 최종 E2E 또는 OpenAPI hardening
- EC2, Nginx, Certbot과 자체 서버 운영
- PostgreSQL 전환 또는 MySQL 의미 변경
- 자동 배포 pipeline, staging 환경, 고가용성, autoscaling과 무중단 배포 보장
- 종합 monitoring·alerting, backup 자동화, restore rehearsal과 상시 운영 체계
- 복수 mail provider 구현과 일반화된 mail adapter
- production-grade mail deliverability, 반송·재시도 운영, 대량 발송과 지속적인 mail SLA

## 4. 범위

### 4.1 포함

- Render에 연결할 최소 Dockerfile과 Spring Boot `$PORT` 바인딩
- Git 저장소와 연결한 Render Web Service의 auto-deploy 비활성화 및 승인 commit 수동 배포
- Render probe 전용 Actuator health group/path와 내부 상세 비노출; SMTP contributor 제외와 DB contributor 포함 여부의 Design 결정
- secret을 저장소가 아닌 Render 환경 변수로만 주입하는 운영 설정
- 외부 Managed MySQL 8.x provider의 선택 기준과 TLS JDBC 연결
- Flyway 전체 적용, Hibernate validation, `utf8mb4_0900_bin`, FK/CHECK/UNIQUE와 핵심 트랜잭션 의미 확인
- provider가 지원하는 IP allowlist 등 최소 네트워크 접근 통제
- 최초 import 동안에만 개발자 환경/IP를 허용하고 완료 후 제거하는 절차
- 개발자 PC에서 기존 Spring Batch와 승인된 checksum/release identity로 수행하는 최초 food import
- 실제 이메일 수신을 위한 SMTP host/port/auth/TLS/from 외부 설정
- Render Free를 선택할 경우 차단 대상이 아닌 SMTP 2525 지원 provider 우선 검토
- Free와 2525 조합이 불가능할 때 유료 Render SMTP 또는 HTTP mail 전송 방식 중 하나를 Design에서 다시 선택하는 중단 조건
- 공통 OpenAPI 기본값은 비활성으로 유지하되 Render 개인 배포는 `NYAM_OPENAPI_ENABLED=true`로 Swagger UI/OpenAPI와 Try it out을 공개하고, 공식 smoke는 curl/Postman으로 유지
- 수동 배포, 초기 import, smoke, 실패 시 이전 승인 commit 재배포를 설명하는 짧은 runbook

정확한 Managed MySQL과 mail provider, Render plan, Dockerfile, 환경 변수 이름, TLS 옵션, health 공개 범위와 명령은 통합 Design에서 확정한다. 실제 credential이나 secret 형태의 예시는 문서에 넣지 않는다.

### 4.2 제외

- Render shell 또는 one-off job을 전제로 한 food import
- Render persistent disk에 MySQL이나 food 원본을 보관하는 구성
- 개발자 IP를 import 완료 뒤 계속 허용하는 운영
- Swagger 실행 결과로 공식 curl/Postman smoke를 대체하는 구성
- 데이터베이스 schema나 공개 API의 기능 변경
- 성능·부하·장애 주입 시험과 장시간 availability 보장
- custom domain과 프론트엔드 도메인 연결

## 5. 인수 시나리오

### A. 수동 배포와 health

- Render는 auto-deploy가 꺼진 상태에서 승인된 정확한 commit을 Dockerfile로 빌드하고 실행한다.
- 애플리케이션은 Render가 제공한 port에 bind하고 외부 Managed MySQL 연결 뒤 기동한다.
- Render probe는 전용 Actuator health group/path에서 정상 상태에 2xx를 반환하고 component, 환경 변수, credential과 내부 오류 상세를 노출하지 않는다. SMTP는 매 probe에서 제외하며 DB 포함 여부는 readiness 정확성, 검사 비용·timeout과 일시 장애 시 재시작 영향을 비교해 Design에서 결정한다.
- 기존 정상 배포가 존재하는 재배포의 경우, 새 배포가 health check를 통과하지 못하면 기존 정상 배포가 계속 traffic을 처리한다. 첫 배포에는 이 보호가 없으며, runbook은 이전 승인 commit이 존재할 때의 재배포 절차를 구분해 설명한다.

### B. Managed MySQL과 최초 food import

- 빈 Managed MySQL schema에 V1~V7 Migration이 순서대로 성공하고 Hibernate validation이 통과한다.
- MySQL 8.x 고유 collation과 FK/CHECK/UNIQUE 제약이 기존 실제 MySQL 검증과 같은 의미를 유지한다.
- Render 애플리케이션 접속은 허용하고 최초 import에 필요한 개발자 접근만 임시 허용한다.
- 개발자 PC에서 TLS JDBC로 승인된 food import를 완료하고 처리 건수와 checksum/release identity를 확인한다.
- 동일 release 재실행은 중복 적재 없이 기존 Batch 계약에 따라 재개되거나 명시적으로 거부되며, 정확한 기대 결과는 Design에서 고정한다.
- import 확인 뒤 임시 개발자 네트워크 접근을 제거한다.

### C. 실제 메일과 로컬 인증 HTTP 계약

- 실제 수신 가능한 주소로 이메일 인증 메일을 요청하고 provider를 통해 수신한다.
- 메일 credential, 인증 코드, signup proof와 token 원문은 저장소나 애플리케이션 로그에 남지 않는다.
- signup과 login 뒤 Access Token은 response body에서만 확인한다.
- login의 `Set-Cookie`에서 `Secure`, `HttpOnly`, `SameSite=Strict`, `Path=/api/v1/auth`를 확인한다.
- cookie jar에 저장한 Refresh Token으로 refresh하고 rotation된 `Set-Cookie`와 `Cache-Control: no-store`를 확인한다.
- logout은 `Max-Age=0` 또는 동등한 삭제 cookie를 반환한다.

### D. 핵심 API smoke

- 발급받은 Access Token으로 `/me`, food 검색과 상세를 호출한다.
- 선택한 food로 meal을 생성하고 같은 사용자와 날짜로 조회한다.
- meal 삭제 전에 daily-summary를 조회해 `mealItemCount > 0`과 저장 snapshot 기반 non-empty 집계를 확인한다.
- meal을 삭제하고 필요하면 같은 날짜를 다시 조회해 empty summary 계약을 확인한다.
- refresh와 logout까지 통과한 뒤 HTTP status, 주요 안전 header와 비밀 비노출만 증거로 남긴다.
- 실제 browser의 cookie 전송과 Vercel rewrite 동작은 이 smoke의 성공으로 주장하지 않는다.

### E. 공개 Swagger/OpenAPI

- Render에서 Swagger UI와 OpenAPI 문서를 익명으로 열람하고 Try it out을 사용할 수 있다. API 실행에는 기존 인증·소유권·cookie/CSRF 계약이 그대로 적용된다.
- 문서·UI에 실제 secret·개인정보나 공용 계정·token을 미리 넣지 않는다. Swagger의 수동 사용 결과는 위 공식 curl/Postman smoke를 대체하지 않는다.

## 6. Design에서 확정할 항목

- Render service source, plan, region, 연결 branch, Docker build/start와 승인 commit 수동 배포 절차
- Docker image 구성, non-root 실행 여부, `$PORT` 적용과 graceful shutdown의 최소 범위
- Actuator dependency, SecurityFilterChain 허용 범위, Render probe 전용 health group/path와 응답 상세; SMTP contributor는 제외하고 DB contributor 포함 여부는 readiness 정확성, bounded query·timeout과 일시 장애 시 제거·재시작 위험을 비교해 결정
- Managed MySQL provider와 region, MySQL minor baseline, TLS mode, CA 검증, JDBC parameter와 connection limit
- backup/retention 제공 범위와 첫 개인 배포에 허용할 비용·보존 한계
- Render와 개발자 import 환경에 적용할 provider별 최소 네트워크 통제 및 접근 제거 증거
- 기존 `foodImport` task의 원격 DB 실행 명령, full import 성공 판정과 동일 release 재실행 기대 결과
- mail provider, 2525 SMTP 설정과 발신자 검증 또는 HTTP 전송으로 전환할 중단 조건
- 환경 변수 목록과 secret/non-secret 분류, 로그 redaction 및 실패 응답 확인 방법
- curl/Postman cookie jar, Access Token 전달, non-empty daily-summary와 cleanup을 포함한 smoke 절차
- 첫 배포 실패 시 이전 승인 commit 재배포와 migration이 이미 전진한 경우의 처리 경계

provider 이름, 계정 생성, 실제 비용 지출과 credential 입력은 Design 승인과 별도의 외부 작업 승인 없이 실행하지 않는다.

## 7. 성공 및 검증 기준

- Render는 auto-deploy가 꺼져 있고 승인 commit SHA를 수동 배포 대상으로 사용한다.
- Docker Web Service가 환경 변수로 기동하고 SMTP를 매 probe에서 제외한 전용 health check를 통과하며, DB contributor 포함 여부와 근거가 Design에 기록된다.
- Managed MySQL에서 Flyway 전체 migration과 Hibernate validation이 성공한다.
- `utf8mb4_0900_bin`, FK/CHECK/UNIQUE와 관련 트랜잭션의 관찰 가능한 검증이 통과한다.
- 최초 food import 결과와 동일 release 재실행 안전성이 확인되고 임시 개발자 접근이 제거된다.
- 실제 인증 메일을 수신하며 credential, verification 값과 token이 저장소·로그에 노출되지 않는다.
- curl/Postman smoke는 non-empty daily-summary를 meal 삭제 전에 확인하고 HTTP Refresh Token cookie 계약을 증명한다.
- Render의 공개 Swagger UI/OpenAPI와 Try it out이 §5.E를 충족하고, 공통 기본 비활성 설정과 기존 API 보안 계약이 유지된다.
- Java 또는 테스트 변경 후 `.\gradlew.bat test javadoc`, 실제 MySQL 8.4.5 관련 통합 테스트와 `git diff --check`를 통과한다.
- passed, failed, errors, skipped와 unexecuted 결과를 구분하며 Docker 또는 외부 서비스 미실행을 성공으로 보고하지 않는다.
- 짧은 runbook만으로 승인 commit 배포, health 확인, import, smoke와 이전 commit 재배포 절차를 반복할 수 있다.

## 8. 위험과 대응

| 위험 | 영향 | Plan 대응 |
|------|------|-----------|
| Render Free의 restart·cold start 또는 outbound 제한 | smoke 불안정과 메일 실패 | 제한을 Design에서 재확인하고 2525 지원 provider 또는 최소 유료 대안을 명시한다. |
| Managed MySQL이 현재 MySQL 의미와 다름 | migration·검색·제약조건 오류 | MySQL 8.x, binary collation, 전체 Flyway와 실제 제약 검증을 provider 선정 조건으로 둔다. |
| global health가 DB·SMTP를 매 probe마다 검사하거나 내부 정보를 노출 | 외부 장애에 따른 불필요한 제거·재시작 또는 정보 유출 | 전용 health group/path를 두고 SMTP를 제외하며 DB 포함 여부와 상세 비노출을 Design에서 확정한다. |
| 개발자 import 접근이 계속 열림 | 불필요한 DB 공격면 유지 | 임시 허용과 제거를 하나의 완료 절차로 묶어 증거를 남긴다. |
| 삭제 후 daily-summary만 확인 | 실제 snapshot 집계 미검증 | meal 삭제 전에 non-empty summary를 필수로 확인한다. |
| SMTP 또는 provider 계정 문제가 구현 변경으로 번짐 | 범위 확대와 일정 지연 | SMTP 2525를 우선하고 불가능하면 Design 중단 조건으로 대안을 다시 승인받는다. |
| 첫 배포와 Vercel·social-login을 함께 처리 | 원인 격리 실패와 범위 팽창 | #22는 backend HTTP smoke에서 종료하고 후속 milestone을 별도 Issue로 둔다. |

## 9. 진행 경계

| 단계 | 상태 | 다음 조건 |
|------|------|-----------|
| Issue | 기존 범위 승인 완료 | 공개 Swagger 정책에 맞춘 Issue #22 문구 동기화는 별도 승인 |
| Plan | 승인 완료, `SMALL-DEPLOYMENT-001-R1` 정책 개정 | 기존 Design 초안에 개정 정책 반영 |
| Design | 초안 진행 중 | Design 전체 승인과 별도 구현 승인 |
| 구현·외부 설정 | 대기 | Design 전체 승인과 별도 구현·계정·배포 승인 |
| Git 게시 | 대기 | 별도 branch, stage, commit, push와 PR 승인 |

`SMALL-DEPLOYMENT-001`은 `FOUNDATION-006-R3`와 Issue #22를 기준으로 이 Plan의 목적, 범위, 인수 시나리오와 Design 검토 항목을 승인한다. 이 승인은 Plan 단계만 완료하며 PDCA Design 전환, Design 작성, 구현, provider 계정 생성, 비용 지출, credential 입력, 실제 배포, branch, stage, commit, push 또는 Pull Request를 승인하지 않는다.

`SMALL-DEPLOYMENT-001-R1`은 2026-09-03 사용자의 정책 개정 지시에 따라 승인되며, `FOUNDATION-006-R4`에 맞춰 기존 Render Swagger/OpenAPI 비활성화 조건만 공개 열람·Try it out 허용으로 대체한다. 공식 curl/Postman smoke, 나머지 승인된 범위와 보안 계약은 유지한다. 이번 승인은 Foundation·Plan 개정, 기존 Design 초안 정합화와 PDCA 기록에 한정하며 Design 전체 승인, 구현, GitHub Issue 수정, Git 게시나 실제 배포를 포함하지 않는다. 현재 단계는 `docs/.pdca-status.json`을 따른다.

## 10. SMALL-DEPLOYMENT-001-R2 — seed/JWT 전체 도메인 smoke 개정

**Approved (2026-09-03).** 승인된 `FOUNDATION-006-R5`에 맞춘 첫 배포 범위 변경이다. 기존 `SMALL-DEPLOYMENT-001/R1`의 실메일·계정/토큰 발급 smoke 완료조건은 아래 조건으로 대체한다. 기존 승인 결정과 검증 결과는 삭제하거나 소급 수정하지 않는다.

### 10.1 목적과 포함 범위

실제 회원가입/로그인 수단을 먼저 확정하지 않아도, 이미 구현한 food → meal → daily-summary 전체와 기존 JWT 검증·사용자 격리를 Render + Aiven에서 검증한다. 인증·인가를 제거하는 변경이 아니다.

- 기존 Render Docker, Aiven MySQL, TLS, Flyway, 식품 전체 import, health HTTP 200, 공개 Swagger/OpenAPI·Try it out 및 curl/Postman 공식 smoke를 유지한다.
- 수동 실행하는 로컬 seed 도구로 비개인정보 테스트 사용자 A/B를 준비하고 DB가 생성한 user_id를 사용한다. seed는 Flyway·앱 자동 기동과 분리하며 재실행 시 중복·덮어쓰기를 막는다.
- 로컬 전용 단기 JWT 발급 도구가 기존 AccessTokenIssuer 계약을 재사용한다. 공개 토큰 발급 endpoint나 별도 smoke guard를 만들지 않고 기존 JWT/ownership 경계를 통과한다.
- deployment profile의 `/api/v1/auth/**`는 서버에서 명시적으로 차단한다. food·meal·daily-summary는 인증을 유지하고 외부 요청에서 userId를 받지 않는다.
- 해당 배포 모드는 외부 SMTP credential 없이 기동할 수 있어야 한다. 기존 이메일/인증 코드·테이블과 다른 실행 환경의 계약은 유지한다.
- 개발자 DB 접근은 승인된 import·seed·필요한 DB 확인에 한해 임시 허용한 뒤 제거한다. 이후 토큰 발급/HTTP smoke를 이유로 DB 접근을 상시 열어두지 않는다.

### 10.2 제외 및 유지 경계

실메일, SMTP2GO 가입/eligibility/설정, signup/login, refresh/logout, cookie jar 검증은 이번 배포 완료조건에서 제외한다. 기존 인증 코드 삭제, 로그인 방식 교체, schema 변경, Vercel/rewrite/browser cookie smoke, Google 구현, 새 인증 우회 체계는 포함하지 않는다. 후속 인증 방식/최종 완료조건은 별도 결정 대상이며 이번 기술용 seed를 회원가입 완료로 간주하지 않는다.

### 10.3 인수 시나리오

1. ignored 입력이 없는 후보에서 로컬 test/javadoc·실제 MySQL·Docker 검증을 새로 통과하고, SMTP 설정 없이 해당 배포 모드가 기동한다.
2. Render bootstrap과 acceptance를 분리하고, 설정 완료 후 별도로 승인된 정확한 SHA를 수동 배포한다. health는 redirect를 따르지 않은 HTTP 200이며 나머지 Actuator 노출 제한을 유지한다.
3. Aiven TLS identity, Flyway 전체 migration, collation/FK/CHECK/UNIQUE와 snapshot·트랜잭션 검증을 유지한다. 승인된 원본 식품 317,766건과 동일 완료 identity 재실행 거부/건수 유지, A/B seed 재실행 안전성을 확인한다.
4. 정상 JWT A/B로 보호 API를 호출하며, JWT 없음·변조·만료는 401이다. issuer/audience/subject 검증 계약도 유지한다. `/api/v1/auth/**`는 유효 JWT가 있어도 차단되고 메일/계정/토큰 부작용이 없어야 한다.
5. A: food 검색/상세 → meal 생성/날짜 조회 → 삭제 전 non-empty daily-summary의 snapshot 집계 확인.
6. B: 같은 날짜 meal 목록/summary에 A 데이터가 없고, A meal 삭제는 기존 `404 MEAL_NOT_FOUND`이다. 실패 후 A meal이 보존되어야 한다.
7. A가 자신이 만든 meal을 삭제한 뒤 통제된 테스트 날짜의 empty summary를 확인한다. 기존 사용자 데이터나 다른 meal은 정리 대상으로 삼지 않는다.
8. Swagger/OpenAPI 공개·Try it out은 유지하되 공식 결과는 curl/Postman으로 기록한다. seed/JWT 도구는 배포 실행 JAR·이미지에서 제외하고, credential·토큰 산출물은 Git·JAR·이미지·로그에서 제외한다. 비밀값이 없는 개발 도구 소스의 향후 버전 관리는 별도 Git 승인 범위다.

### 10.4 위험과 승인 경계

배포 서명 키를 사용하는 로컬 도구는 민감한 권한이므로 secret 출력/공유와 임의 사용자 토큰 발급을 막고, 승인된 테스트 사용자만 대상으로 한다. seed 충돌은 무조건 upsert하지 않고 중단하며 기존 데이터를 보존한다. 토큰 만료 시 로컬에서 재발급하고 refresh 흐름으로 범위를 확장하지 않는다.

`small-deployment`의 기록상 phase는 `do`를 유지한다. 새 범위는 **Plan/Design 승인 완료 / 구현 미실행 / 새 로컬 검증 미실행 / 외부 배포 미실행**이다. 기존 138/141 테스트와 Docker/MySQL 결과는 기존 결정에 대한 증거이지 새 범위의 통과 증거가 아니다.

현재 승인은 `FOUNDATION-006-R5`, 이 Plan R2, `SMALL-DEPLOYMENT-003`의 문서 결정과 PDCA 승인 기록만 포함한다. 기존 Issue/branch를 유지한다. Issue #22의 승인 상태 문구 동기화, 설정·seed·JWT 도구·테스트 구현 및 로컬 재검증, 코드/DB 수정, 추가 branch/worktree, stage/commit/push/PR/merge, provider 계정·credential·비용·배포는 별도 승인 대상이다.

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.2.0 | 2026-09-03 | `SMALL-DEPLOYMENT-001-R2` 승인: seed A/B·로컬 단기 JWT·전체 domain smoke로 첫 배포 범위를 개정하고 기존 Do 증거 보존; 구현은 미승인 |
| 1.1.0 | 2026-09-03 | `SMALL-DEPLOYMENT-001-R1` 정책 개정: Render Swagger/OpenAPI·Try it out 공개 허용, 공식 curl/Postman smoke와 보안 계약 유지 |
| 1.0.0 | 2026-09-03 | `SMALL-DEPLOYMENT-001`로 Plan 전체 승인; Design 전환·작성과 구현·외부 작업·Git 게시는 미승인 |
| 0.2.0 | 2026-09-03 | health contributor와 재배포 조건을 명확히 하고 mail 운영 경계, 플랫폼 자료와 진행 조건을 정리한 Plan 초안 수정 |
| 0.1.0 | 2026-09-03 | `FOUNDATION-006-R3`와 Issue #22를 기준으로 최초 small-deployment Plan 초안 작성 |
