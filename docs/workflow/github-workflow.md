# Nyam-server GitHub 작업 절차

이 문서는 Nyam-server의 코드를 GitHub Issue와 Pull Request(PR)를 통해 변경하는 기본 절차를 설명합니다. 기능 개발, 버그 수정, 리팩터링은 특별한 합의가 없는 한 이 절차를 따릅니다.

## 기본 원칙

- 기준 브랜치는 `dev`입니다.
- 하나의 Issue와 PR에는 하나의 논리적인 변경 범위를 담습니다.
- 실제 `.env`, DB 접속 정보, 비밀번호, 토큰, 키 등의 비밀값은 commit하지 않습니다.
- 기존 작업 트리에 다른 변경이 있으면 의도한 파일만 stage합니다.
- 저장소에는 별도의 공식 브랜치 이름 또는 commit 메시지 규칙이 없습니다. 아래 이름과 메시지는 권장 예시이며 강제 규칙이 아닙니다.
- 코드와 기능 변경은 Issue와 PR을 거치는 것을 기본으로 합니다. `dev` 직접 commit은 사전에 범위를 명확히 합의한 단순 정리 작업에만 예외적으로 사용합니다.

## 문서와 승인 단위

- 기능 하나는 사용자가 실제로 확인할 수 있는 하나의 수직 흐름을 기본 작업 단위로 삼습니다.
- 하나의 기능에는 간결한 Plan 하나와 전체 흐름을 다루는 통합 Design 하나를 우선합니다.
- Design에서는 공개 API, 스키마, 보안, 데이터 무결성, 트랜잭션, 동시성, 외부 연동 실패와 같이 변경 비용이 큰 결정을 확정합니다.
- 정책 숫자, 클래스·메서드명, SQL 문장, 패키지 경로, 테스트명처럼 되돌리기 쉬운 세부사항은 일반적으로 별도 Issue나 반복 승인 대상으로 만들지 않습니다. 다만 그 선택이 보안, 데이터 무결성, 공개 API 또는 스키마 결과를 실질적으로 바꾸면 통합 Design에서 함께 검토합니다.
- Analysis는 실제 설계 불일치와 검증 근거에 집중하고, Report는 완료 결과와 남은 제한을 간결하게 기록합니다. 각 단계에서 같은 설명을 반복하지 않습니다.
- Design 승인과 구현 승인은 별도 단계로 유지합니다. 두 범위가 승인되면 그 안의 세부 구현은 계속 진행하고, 설계 충돌·보안 또는 데이터 무결성 문제·구현 불가능한 제약·범위 확대가 발견될 때만 다시 결정합니다.

## 1. Issue 작성

작업을 시작하기 전에 변경 목적과 완료 조건을 Issue로 정의합니다.

| 작업 유형 | 템플릿 | 기본 제목 |
| --- | --- | --- |
| 기능 추가 | [Feature](../../.github/ISSUE_TEMPLATE/✨-feature.md) | `[Feat] 기능 이름` |
| 버그 수정 | [Bug](../../.github/ISSUE_TEMPLATE/🐛-bug.md) | `[Bug] 문제 요약` |
| 리팩터링 | [Refactor](../../.github/ISSUE_TEMPLATE/♻️-refactor.md) | `[Refactor] 대상 요약` |

Issue에는 다음 내용을 작성합니다.

- 작업 목적과 배경
- 이번 작업에 포함되는 범위와 포함되지 않는 범위
- 완료 여부를 확인할 수 있는 To-do
- 필요한 테스트와 문서 변경
- 관련 자료 또는 후속 작업

Feature Issue는 API 하나나 정책 값 하나가 아니라 사용자 요청부터 데이터 변경과 응답 확인까지 함께 작동해야 의미가 있는 전체 흐름을 한 범위로 묶습니다. 별도로 배포하거나 검증할 수 있는 독립 기능만 새 Issue로 분리합니다.

## 2. 작업 브랜치 생성

Issue 범위를 확정한 뒤 원격 상태를 갱신하고 최신 `origin/dev`에서 별도 작업 브랜치와 worktree를 생성합니다.

```bash
git fetch origin --prune
git worktree add -b email-verification ../Nyam-server-email-verification origin/dev
```

새 worktree는 갱신된 `origin/dev`와 같은 commit에서 시작하므로 기존 dirty worktree를 switch하거나 pull할 필요가 없습니다. 브랜치 이름은 작업 내용을 알아볼 수 있도록 간결하게 작성합니다. 저장소에 강제 규칙은 없으므로 협업자가 이해할 수 있는 이름이면 됩니다.

## 3. 구현과 검증

Issue의 To-do를 기준으로 코드, Migration, 설정, 테스트, 문서를 변경합니다.

작업 중에는 다음 사항을 확인합니다.

- 트랜잭션 경계와 실패 시 롤백이 명확한가
- Flyway Migration과 Hibernate 검증이 일치하는가
- MySQL 전용 동작은 실제 MySQL 환경에서 검증했는가
- API 계약과 Swagger 설명이 구현과 일치하는가
- 비밀값 또는 불필요한 로컬 파일이 포함되지 않았는가
- Issue 범위를 벗어난 변경이 섞이지 않았는가

대표적인 검증 명령은 다음과 같습니다.

```bash
./gradlew test javadoc
git diff --check
git status --short
```

Windows PowerShell에서는 `./gradlew` 대신 `.\gradlew.bat`을 사용할 수 있습니다.

## 4. 의도한 파일만 commit하고 push

dirty worktree에서는 전체 파일을 한꺼번에 stage하지 않고, Issue에 포함되는 경로만 명시적으로 선택합니다.

```bash
git status --short
git diff -- path/to/file
git add path/to/file path/to/test
git commit -m "feat: 이메일 인증 구현"
git push -u origin email-verification
```

commit 메시지는 변경 목적을 짧고 명확하게 설명합니다. 위 메시지 형식은 예시이며 현재 저장소의 강제 규칙은 아닙니다.

## 5. PR 작성

원격 작업 브랜치를 push한 다음 `dev`를 대상으로 PR을 생성합니다. [PR 템플릿](../../.github/PULL_REQUEST_TEMPLATE.md)의 항목을 빠짐없이 확인합니다.

PR 본문에는 다음 내용을 포함합니다.

- PR 유형과 변경 개요
- 관련 Issue를 닫는 `Close #이슈번호`
- 주요 코드와 데이터 변경 사항
- 수행한 테스트와 결과
- 리뷰어가 알아야 할 제한 사항과 후속 범위
- 템플릿 체크리스트와 적절한 라벨

검토 준비가 끝나지 않았다면 Draft PR로 만들고, 최신 `dev` 반영과 최종 검증을 마친 후 Ready for review로 전환합니다.

## 6. 최신 `dev` 반영

PR을 병합하기 전에 원격 `dev`의 최신 변경을 작업 브랜치에 반영합니다.

```bash
git fetch origin
git merge origin/dev
```

충돌이 발생하면 각 파일의 최종 의도를 확인하여 해결합니다. 동기화 후에는 전체 테스트와 문서 생성을 다시 실행하고 결과를 PR 본문에 기록합니다.

공유 중인 작업 브랜치를 rebase하려면 commit SHA가 변경되므로 협업자와 먼저 합의합니다.

## 7. 리뷰와 병합

다음 조건을 만족하면 PR을 병합합니다.

- Issue의 완료 조건을 충족함
- PR의 변경 파일과 범위가 의도와 일치함
- 필수 테스트가 성공함
- 최신 `dev`와 충돌하지 않음
- PR 체크리스트와 라벨을 확인함

하나의 논리적 변경을 담은 PR은 `Squash and merge`를 권장합니다. 기능 commit과 동기화용 commit을 하나의 명확한 기준 commit으로 남길 수 있기 때문입니다. 여러 commit을 각각 보존해야 할 이유가 있다면 merge 방식을 별도로 합의합니다.

병합 후에는 다음을 확인합니다.

- PR이 merged 상태인가
- `Close #이슈번호`로 Issue가 닫혔는가
- `dev`가 병합 commit을 가리키는가
- 병합된 원격 작업 브랜치를 삭제했는가

## 실제 적용 사례

아래 내용은 당시 완료 상태를 기록한 사례이며 현재 브랜치의 검증 결과를 대신하지 않습니다.

project-foundation과 user-registration 기준선은 다음 과정으로 반영했습니다.

1. [Issue #1](https://github.com/KBS-guys/Nyam-server/issues/1)에서 범위와 To-do를 정의했습니다.
2. `project-foundation-user-registration` 브랜치에서 구현하고 원격에 push했습니다.
3. [PR #3](https://github.com/KBS-guys/Nyam-server/pull/3)을 `dev` 대상으로 작성하고 본문에 `Close #1`을 연결했습니다.
4. 최신 `dev`를 병합한 뒤 전체 38개 테스트, 실제 MySQL 8.4.5 통합 테스트 6개, JavaDoc 생성을 확인했습니다.
5. PR을 Ready for review로 전환하고 `Squash and merge`했습니다.
6. squash commit `4a931680b6502a3398521176a25da2a290c2053b`이 `dev`에 반영됐고 Issue #1이 자동으로 닫혔습니다.
7. 병합된 원격 작업 브랜치를 삭제했습니다.

이 흐름을 후속 기능에서도 기본 작업 단위로 사용합니다.
