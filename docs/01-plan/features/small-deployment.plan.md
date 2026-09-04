# Small Deployment Plan

> **Current decision**: `SMALL-DEPLOYMENT-001-R3` — Approved scope direction (2026-09-04)
>
> **PDCA phase**: Do
>
> **Related**: Issue #22, PR #23, `FOUNDATION-006-R5`

## 1. 목적

Nyamlog의 첫 배포에서 인증 가입·토큰 획득 흐름을 완성하려 하지 않는다. 현재 구현된 JWT 검증과 사용자 소유권 경계를 그대로 사용해 Render와 Aiven MySQL에서 `food → meal → daily-summary` 핵심 도메인이 수직으로 동작하는지를 검증한다.

이번 단계는 작은 개인 배포의 기술 위험을 확인하는 작업이다. 실제 사용자 인증 완성이나 공개 서비스 운영 준비를 뜻하지 않는다.

## 2. 진행 순서

```text
PR #23 로컬 검증
→ 코드 리뷰
→ 별도 승인 후 dev 병합
→ 병합된 dev SHA를 Render에 수동 배포
→ Aiven import / synthetic A·B seed
→ 원격 domain·ownership smoke
```

작업 브랜치를 Render에 먼저 배포하는 pre-merge acceptance는 현재 범위에서 사용하지 않는다. Render 최초 서비스 생성 중 발생할 수 있는 bootstrap 시도는 공식 acceptance가 아니며, DB·secret·환경 구성을 마친 뒤 병합된 정확한 `dev` SHA를 수동 배포한 시점부터 판정한다.

## 3. 포함 범위

- Java 17 multi-stage Docker image, non-root 실행, Render의 `$PORT`
- Render Web Service와 `/actuator/health/render`
- Aiven MySQL 8.4, TLS `VERIFY_IDENTITY`, CA/PKCS12 truststore
- Flyway V1~V7와 Hibernate `validate`
- 승인된 식품 원본 317,766건 전체 import와 identity/checksum 확인
- 인증 데이터가 없는 synthetic 사용자 A/B seed
- 기존 `AccessTokenIssuer`를 재사용하는 로컬 전용 900초 Access JWT 발급
- JWT가 필요한 food 검색·상세, meal 생성·조회·삭제, daily-summary 집계
- A/B 사용자 소유권과 교차 사용자 비노출
- OpenAPI 기본 off, Render 명시적 opt-in 공개와 Try it out
- 실제 결과에 맞춘 Issue #22, PR #23, runbook, PDCA 상태 기록

## 4. 제외·후속 범위

- 이메일 인증과 실제 이메일 수신, SMTP provider
- signup, login, refresh, logout
- Refresh Token cookie, cookie jar, CSRF browser 검증
- 실제 사용자가 Access Token을 획득하는 흐름
- Google/social login
- Vercel rewrite와 browser cookie smoke
- 최종 인증·인가 E2E
- CI/CD, custom domain, HA, 종합 monitoring

기존 인증 코드는 삭제하지 않는다. 일반 환경의 기존 signup/login 계약도 유지한다. 다만 `deployment` profile에서는 `/api/v1/auth/**`를 서버 수준에서 항상 차단하고 이를 다시 여는 별도 runtime property를 두지 않는다.

## 5. 완료 조건

1. Docker image가 성공적으로 빌드된다.
2. Render `/actuator/health/render`가 redirect 없이 HTTP 200을 반환한다.
3. 애플리케이션과 로컬 import/seed 도구가 Aiven에 TLS `VERIFY_IDENTITY`로 연결한다.
4. Flyway V1~V7가 성공하고 Hibernate validation이 통과한다.
5. food 317,766건이 승인된 identity/checksum으로 적재된다.
6. DB 생성 ID를 사용하는 synthetic 사용자 A/B가 준비된다.
7. A/B의 900초 JWT가 로컬 비공개 파일로 발급된다.
8. A JWT로 food 검색과 상세 조회가 성공한다.
9. A JWT로 meal 생성과 날짜 조회가 성공한다.
10. meal 삭제 전에 A daily-summary가 non-empty snapshot 집계를 반환한다.
11. B의 meal 목록과 daily-summary에 A 데이터가 보이지 않는다.
12. B가 A meal 삭제를 시도하면 `404 MEAL_NOT_FOUND`이며 A 데이터는 남아 있다.
13. A가 자신의 meal을 삭제한다.
14. 삭제 후 A daily-summary가 empty 결과를 반환한다.
15. JWT 없음·변조·만료 요청은 각각 401이다.
16. `deployment`의 `/api/v1/auth/**`는 익명·유효 JWT 요청 모두 차단되고 계정·메일·토큰·cookie 부작용이 없다.

## 6. 위험과 중단 조건

| 위험 | 대응 |
|---|---|
| TLS나 CA 문제를 우회하고 싶어짐 | `VERIFY_IDENTITY`와 system truststore fallback 차단을 유지하고 원인을 해결한다. |
| seed 일부만 DB에 남음 | manifest 기록 전에는 DB를 commit하지 않으며 실패 시 신규 A/B를 rollback한다. |
| 기존 사용자를 smoke 사용자로 덮어씀 | 예약된 합성 이메일의 정확한 fixture만 재사용하고 충돌 시 중단한다. |
| JWT·DB secret 노출 | 인자/stdout/문서/PR에 값을 남기지 않고 저장소 밖 private 파일만 사용한다. |
| 배포 범위가 인증 기능으로 확대됨 | `/api/v1/auth/**`는 사용하지 않으며 실제 token acquisition은 후속 Issue로 둔다. |
| 무료/저용량 DB가 전체 import를 수용하지 못함 | 유료 전환을 임의 수행하지 않고 측정 결과와 대안을 보고한 뒤 중단한다. |

## 7. 검증 및 증거 원칙

- Java 변경 후 `./gradlew test javadoc` 결과에서 suites, passed, failures, errors, skipped를 기록한다.
- MySQL 전용 테스트는 실제 MySQL 8.4.5에서 skip 없이 실행돼야 완료 증거가 된다.
- Docker build/runtime, TLS, import, seed, HTTP smoke는 실행 결과와 미실행 항목을 구분한다.
- JWT, signing secret, DB password, CA 내용과 endpoint가 포함된 원문 로그는 남기지 않는다.
- 외부 acceptance가 모두 끝나기 전에는 feature 완료나 Check 전환을 선언하지 않는다.

## 8. 결정 이력

- `SMALL-DEPLOYMENT-001/R1/R2`와 그 당시의 승인·Do 증거는 Git/PDCA 이력으로 보존한다.
- `SMALL-DEPLOYMENT-001-R3`는 현재 완료조건을 핵심 도메인 배포로 통일하고, SMTP·가입·로그인·refresh/logout·cookie를 후속 범위로 분리한다.
- 이 문서의 진행 순서는 merge 권한을 포함하지 않는다. PR #23 병합은 별도 사용자 승인 대상이다.
