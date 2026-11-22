# KI-17 — 기안자 자기 승인 차단·결재자 0명 규칙

## 출처·증상

개발 중 발견한 문제다. 리뷰에서 규칙별 본인 제외가 직접 지정·ROLE·USER에도 같은 안전성을 보장하는지 추론한 뒤 재현했다. 운영 사고나 운영 데이터 수정 기록이 아니다.

일반 사원이 GENERAL의 직접 지정 결재선에 본인만 넣으면 SKIPPED(SELF) 한 줄만 생기고 문서는 즉시 APPROVED가 됐다. ROLE의 유일한 담당자가 본인이거나 USER가 본인을 가리켜도 같은 결과였다. 본인과 다른 사람을 함께 직접 지정하면 본인은 빠지고 나머지 결재가 진행됐지만, 직접 지정 입구에서 본인을 거절한다는 계약이 없었다.

시작점은 develop `fbcebd4a936d2357b75de6fe05f889fee5126e0a`, 작업 브랜치는 `fix/self-approval-invariant`다. 실행은 작업 트리 밖 archive와 MariaDB·Redis Testcontainers만 사용했다. 실제 개발 DB·Redis·서버·SMTP에는 접속하지 않았다.

## 원인·처음 설계가 단계별로 판단한 이유

기존 해석기는 USER·ROLE·DEPT_HEAD·UPPER_DEPT_HEAD마다 사람을 찾고, skip_self·dedupe_approver를 적용해 결재선을 만든다. 부서장 공석·상향·참조 누락은 그 단계의 맥락을 알아야 설명할 수 있어 단계별 처리가 자연스러웠다. KI-15도 부서장 단계의 자기 승인과 공석을 고쳤다.

그러나 DocumentService는 해석 후 저장된 PENDING이 없으면 곧바로 승인 완료와 양식 후처리를 실행했다. 단계별 SELF 처리가 정상이라는 것과, 문서 전체에서 승인자가 없어도 된다는 것은 다른 조건이다. 직접 지정·ROLE·USER는 KI-15의 부서장 상향 분기에 들어가지 않아 전역 조건을 우회했다.

## A·B·C안 비교와 선택

| 안 | 효과 | 부족한 점·선택 이유 |
|---|---|---|
| A: 직접 지정 입구만 검사 | 본인 한 명·본인+타인, 과거 저장한 직접 지정선을 400으로 거절 | ROLE·USER 등 규칙으로 0명이 되는 경로를 막지 못해 단독 적용은 버림 |
| B: 해석 후 0명만 검사 | 규칙 종류와 무관하게 조직상 즉시 승인 자격을 확인 | 본인+타인 직접 지정에는 다른 결재자가 남아 A의 입력 계약을 보장하지 못함. 대표의 직접 본인 지정도 400이어야 하므로 단독 적용은 버림 |
| **C: A+B** | 직접 지정 계약과 문서 전체의 즉시 승인 조건을 각각 보장 | 두 경계가 서로 다른 오류를 막음. 변이 검사로 독립적인 판별력 확인. 선택 |

ROLE·USER마다 별도 예외를 덧붙이는 방법은 새 규칙이 생길 때 같은 누락이 반복될 수 있어 버렸다. 양식 단계를 늘리거나 모든 0명을 금지하는 방법은 대표 기안·기존 정상 경로를 바꾸므로 선택하지 않았다. 라이브러리·오류 코드·양식 시드·마이그레이션은 추가하지 않았다.

## 구현·검사 위치

- `ApprovalLineResolver.validateManualApprovers`: 직접 지정의 APPROVER가 기안자면 기존 INVALID_APPROVAL_STEPS와 `본인은 결재자로 지정할 수 없습니다.`로 400. 구조 검증과 기존 권한·allow_line_edit 검사는 유지한다. REFERENCE의 본인 제외도 유지한다.
- `ApprovalLineResolver.validateRemainingApprovers`: 건너뛰지 않은 APPROVER가 있으면 기존 처리. 없으면 getPerson으로 **현재 기안자 부서·재직 상태를 다시 읽고**, 그 부서부터 부모 부서들을 순회한다. 모든 부서장이 재직 기안자 자신이어야 통과한다. 타인·공석·비재직·부서 없음·순환은 즉시 승인을 허용하지 않는다. 동일한 사람이 모든 부서의 부서장인지 확인하므로 그 사람의 ACTIVE 검사는 한 번이면 된다. 역할 코드나 부서 id=1 같은 지름길은 사용하지 않는다.
- 위 최종 판단 함수는 resolveLine 끝에서 한 번만 호출한다. 문서·양식 미리보기, 상신, 재상신이 모두 이 경로를 사용한다. 단계별 KI-15 공석 검사도 유지한다.
- 최종 검사 실패는 기존 ResolutionFailure로 전달한다. 미리보기에는 `최종 결재자 확인` ERROR 줄과 error를 반환하고 finalLine은 비운다. 상신·재상신은 기존 APPROVER_NOT_FOUND(422)로 끝난다. 새 오류 코드는 없다.
- 재상신은 이 서비스에서 **반려/회수 원본을 새 DRAFT로 복사**하는 동작이다. 규칙선도 복사 전에 검사하도록 최소 변경했다. 실패 시 원본은 REJECTED이고 새 DRAFT는 만들지 않는다. 성공한 규칙선의 저장 시점은 여전히 다음 상신 시점이며, 새 임시 문서에 규칙 스냅샷을 미리 저장하지 않는다.
- 검사는 문서번호 발급·결재선 저장·문서 상태 변경·알림·연차 후처리 전에 끝난다. 기존 사용자 행 잠금과 트랜잭션 경계는 유지한다.
- `skip_self=false`인 **규칙선**에서는 본인이 PENDING으로 남으므로 B가 차단하지 않는다. **직접 지정선**의 본인 금지는 skip_self와 별개로 A가 적용한다.

## 0명이 되는 경로·입구 표

`SelfApprovalInvariantTest`의 매개변수 테스트가 아래 입구와 경로를 표 형태로 실행한다. 기존 `ApprovalSelfEscalationTest` 20개로 KI-15 정상·오류 흐름도 확인한다.

| 경로 | 수정 전 | 수정 후 | 검증 |
|---|---|---|---|
| 직접 지정 본인만 | SELF로 제외 후 즉시 승인 가능 | 모든 입구 400 INVALID_APPROVAL_STEPS | A 입구 7개 × 본인만 |
| 직접 지정 본인+타인 | 본인을 제외하고 진행 | 모든 입구 400 | A 입구 7개 × 본인+타인 |
| ROLE 유일 담당자=기안자 | 미리보기 정상·상신 즉시 승인 | 미리보기 ERROR·상신 422·DRAFT·새 결재선 0 | NEW / RESUBMIT / REVISED_SUBMIT |
| USER 지정 사원=기안자 | ROLE과 같음 | ROLE과 같음 | NEW / RESUBMIT / REVISED_SUBMIT |
| 본인 참조+다른 결재자 | 본인 참조 제외 | 그대로, 타인 결재 진행 | ownReferenceIsSilentlyOmitted |
| 대표 규칙 기안 LEAVE·GENERAL·OVERTIME·ATTENDANCE_FIX | 즉시 승인 | 그대로, 대표 휴가 USE -1·잔여 9 유지 | 기존 회사 계층 12개 행 중 대표 4개 |
| 일반 사원·팀장 규칙 기안 | 팀장/대표 순차 결재 | 그대로 | 기존 회사 계층 12개 행 중 8개 |
| KI-15 중간 공석→더 위의 재직 부서장 | 상향 진행 | 그대로 | 기존 KI-15 회귀 |
| KI-15 공석·휴직 끝까지 결재자 없음 | 422 | 그대로 | 기존 KI-15 회귀 |
| skip_self=false, USER/ROLE 본인 | 본인 PENDING | 그대로 | 2개 매개변수 행 |
| skip_self=false, 직접 지정 본인 | 허용 | 400(A 계약) | 1개 |
| 대표의 USER/ROLE 본인 규칙 | 즉시 승인 | 현재 조직 조건 충족 시 그대로 | 2개 |
| USER 본인 규칙, 상위 공석/휴직/타인/순환 | 조직을 보지 않고 즉시 승인 | 미리보기 ERROR·422·DRAFT·결재선 0 | 4개 |
| 임시 저장 후 기안자 부서 변경 | 저장한 부서·USER 규칙만 보고 즉시 승인 | 현재 기안자 부서로 B 재확인·422 | 1개 |
| 같은 재직자가 하위~최상위 부서장을 모두 겸함 | 즉시 승인 | 역할명/최상위 소속 여부와 무관하게 B 조건대로 허용 | 1개 |

A의 7개 입구: CREATE(POST 임시 저장), UPDATE(PUT 임시 수정), PREVIEW, SUBMIT, RESUBMIT, REVISED_UPDATE(반려 후 새 임시본 수정), REVISED_SUBMIT(반려 후 새 임시본 상신). 각각 본인만/본인+타인을 검사해 14개다. 과거 직접 지정 자기 결재선은 기존 정상 문서 생성·반려 후 테스트 DB의 해당 선만 바꿔 재현한다.

B의 USER·ROLE 각각에서 NEW, 반려 원본 RESUBMIT, 재기안 임시본 REVISED_SUBMIT을 검사한다. 실패 요청 앞뒤 문서·결재선·이력·알림·연차 원장 행 수가 같다. A는 기존 선 내용까지 같음을 확인한다. 재기안 임시본은 DRAFT, 반려 원본의 재상신 거절은 REJECTED 그대로이며 새 문서는 0개다.

## 수정 전 실패·수정 후 검증

처음 30개 테스트를 수정 전 코드에 넣었을 때 25개 실패했다. 현재 부서 재확인·전체 경로 동일인 허용·대사 회귀를 더한 **최종 동일 테스트 33개**도 수정 전 코드에서 다시 실행해 아래 결과를 확인했다. 새 운영 클래스에 의존하지 않아 수정 전 코드에서도 컴파일됐다.

```text
Tests run: 33, Failures: 26, Errors: 0, Skipped: 0
Status expected:<400> but was:<201>    (직접 지정 임시 저장)
Status expected:<400> but was:<200>    (수정·미리보기·상신)
expected: 422                 but was: 200
expected: "APPROVER_NOT_FOUND" but was: ""
expected: "DRAFT"             but was: "APPROVED"
Expecting ["SKIPPED"] to contain ["ERROR"]
BUILD FAILURE / Maven 19.395 s
```

26개 실패 = A 입구 14개 + skip_self=false 직접 지정 1개 + USER/ROLE 6개 + 잘못된 상위 조직 4개 + 현재 부서 재확인 1개다. 테스트 자체의 오류나 건너뜀은 없다.

수정 후 최종 33개 회귀는 33/0/0/0, Maven 20.616 s. 기존 KI-15 20개도 20/0/0/0이다. 최종 전체 실행 결과는 아래 실행 표에 기록한다.

### A·B 변이 검사

최종 후보의 외부 사본에서 호출 한 줄만 주석 처리했다. 운영에 검사 비활성 옵션을 추가하거나 변이 소스를 커밋하지 않았다.

| 외부 사본 | 끈 호출 | 테스트/실패/오류/건너뜀 | 실패한 테스트 | Maven 시간 |
|---|---|---|---|---|
| A만 OFF | validateManualApprovers(manual, drafter) | 33/15/0/0 | everyManualEntranceRejectsSelfApprover 14개 + manualSelfIsRejectedEvenWhenSkipSelfIsDisabled | 21.262 s |
| B만 OFF | validateRemainingApprovers(resolved, drafter) | 33/11/0/0 | ruleSelfWithoutPendingIsRejectedAtPreviewAndSubmission 6개 + zeroApproverChecksActualHierarchyDespiteUserRule 4개 + finalCheckUsesCurrentDepartmentRatherThanDraftSnapshot | 20.688 s |

A만 끈 본인 단독 경로는 B가 422로 막아도 요구한 400·입력 메시지와 다르므로 실패한다. 본인+타인 경로는 B의 대상이 아니어서 A가 필요하다. B만 끄면 ROLE·USER의 즉시 승인 누락이 다시 발생한다. 이 두 결과는 A와 B가 서로를 대신하지 못한다는 증거다.

## 읽기 전용 데이터 대사

### 조회의 의미와 한계

현재 스키마에는 결재선별 “과거 한 번이라도 PENDING이었는지”를 직접 저장한 필드와 부서장 변경 이력이 없다. 그래서 아래 쿼리는 APPROVED 문서 중 **현재 모든 APPROVER가 SKIPPED이고 acted_at이 없으며 APPROVE/REJECT 이력도 없는 문서**를 고른 뒤, **현재** 조직의 B 조건과 대조한다. 정상 API의 보존된 선·이력으로 자동 승인 후보를 좁히는 방식이다.

조직이 승인 후 바뀌었다면 과거의 위반 여부를 단정할 수 없다. 이력이나 선을 수동 변경한 경우도 완전하게 판별하지 못한다. 결과는 사람이 확인할 후보 목록이며, 자동 수정·삭제의 근거로 사용하지 않는다. 본 쿼리·검증에서 운영 DB를 읽지 않았다.

원본은 `backend/src/test/resources/approval/self-approval-audit.sql`이다. 새 마이그레이션이 아니라 테스트 리소스인 SELECT이며 쓰기 구문은 없다.

```sql
-- Read-only candidates: current line/history evidence and CURRENT organization, not historical proof.
WITH RECURSIVE candidates AS (
    SELECT d.id, d.doc_no, d.drafter_id, u.department_id
    FROM approval_documents d
    JOIN users u ON u.id = d.drafter_id
    WHERE d.status = 'APPROVED'
      AND NOT EXISTS (
          SELECT 1 FROM approval_lines l
          WHERE l.document_id = d.id AND l.line_kind = 'APPROVER'
            AND (l.status IS NULL OR l.status <> 'SKIPPED' OR l.acted_at IS NOT NULL)
      )
      AND NOT EXISTS (
          SELECT 1 FROM approval_histories h
          WHERE h.document_id = d.id AND h.action IN ('APPROVE', 'REJECT')
      )
), department_chain AS (
    SELECT c.id AS document_id, c.drafter_id, dep.id AS department_id,
           dep.parent_id, dep.head_user_id, head.status AS head_status,
           CAST(CONCAT(',', dep.id, ',') AS CHAR(4096)) AS visited, 0 AS cycle
    FROM candidates c
    LEFT JOIN departments dep ON dep.id = c.department_id
    LEFT JOIN users head ON head.id = dep.head_user_id
    UNION ALL
    SELECT chain.document_id, chain.drafter_id, dep.id,
           dep.parent_id, dep.head_user_id, head.status,
           CONCAT(chain.visited, dep.id, ','),
           CASE WHEN LOCATE(CONCAT(',', dep.id, ','), chain.visited) > 0 THEN 1 ELSE 0 END
    FROM department_chain chain
    JOIN departments dep ON dep.id = chain.parent_id
    LEFT JOIN users head ON head.id = dep.head_user_id
    WHERE chain.parent_id IS NOT NULL AND chain.cycle = 0
)
SELECT c.id, c.doc_no, c.drafter_id
FROM candidates c
WHERE EXISTS (
    SELECT 1 FROM department_chain chain
    WHERE chain.document_id = c.id
      AND (chain.department_id IS NULL OR chain.head_user_id IS NULL
           OR chain.head_user_id <> chain.drafter_id
           OR COALESCE(chain.head_status, '') <> 'ACTIVE' OR chain.cycle = 1)
)
OR NOT EXISTS (
    SELECT 1 FROM department_chain chain
    WHERE chain.document_id = c.id AND chain.department_id IS NOT NULL AND chain.parent_id IS NULL
)
ORDER BY c.id
```

### 수정 전 API로 검증

`fbcebd4` 외부 사본의 일회용 MariaDB에서 임시 테스트 `auditOldApiAutoApprovals`를 실행했다. 실제 POST 임시 저장→POST 상신으로 직접 지정 본인·USER 본인·ROLE 본인 문서 3개를 만들었고, 각각 APPROVED인지 단언했다. 비교용으로 타인이 실제 승인한 문서 1개, 최상위 대표의 즉시 승인 특근 1개도 만들었다. 위 SELECT를 그대로 읽어 실행했다.

```text
KI17_OLD_API_AUDIT unsafe_created=3 unsafe_found=3 safe_created=2 safe_excluded=2
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

취약 동작을 성공으로 기대하는 임시 테스트는 수정 전 외부 사본에서만 썼다. 커밋한 `auditSelectFindsLegacyUnsafeSnapshotAndExcludesReviewedAndRepresentativeDocuments`는 테스트 DB에 과거 선의 형태를 만들고, 실제 API로 만든 정상 타인 승인·대표 승인 문서는 제외되는지 확인한다. 기존 문서를 일괄 변경하는 마이그레이션은 없다.

## 화면 확인

`ApprovalLinePanel`의 기존 후보 필터가 `p.id !== me?.id`로 본인을 이미 제외한다. 외부 사본에서 실제 컴포넌트를 React로 렌더링해 기안자 option은 없고 타인 option은 있는지 단언했다. 따라서 화면 수정·화면 커밋은 없다. 브라우저 수동 조작이나 실제 서버 접속을 했다는 뜻은 아니다.

```text
KI17_UI self_candidate=ABSENT other_candidate=PRESENT actual_panel_render=PASS
npm run build: exit 0 (Vite 2.55 s)
npm run lint: exit 0
```

### 기존 응답 기준선에 대한 영향

기존 기록기·가림 정책·비교기를 수정하지 않았다. 외부 사본에서 M2DocumentIntegrationTest·M2ReviewTest·M3BatchTest를 각각 수정 전/후로 기록했다. 두 실행 모두 20/0/0/0, 29프레임·응답 137건이며 Maven 시간은 전 1:01 min, 후 1:16 min이다.

기존 SnapshotComparator의 `responses(Path, Path)`만 외부 보조 프로그램에서 호출해 응답 부분을 그대로 비교했다. SQL 비교를 통과한 것으로 표현하지 않는다. **이번 KI-17 전후 응답 차이는 0**이다. JSON 숫자 원문·날짜·null·배열 순서를 보존하는 기존 검사이며, 기록 완료 순서만 기존 규칙대로 취급한다.

유지 중인 refactoring-baseline과 수정 후 기록 사이에는 이미 KI-15에서 승인한 다음 차이만 있었다.

| 메서드 | 응답 필드 차이 수 | 이유 |
|---|---:|---|
| M2DocumentIntegrationTest#lineRulesHandleNormalSelfFallbackAndDuplicateApprovers | 17 | KI-15 본인 부서장 상향 |
| M2ReviewTest#fallbackExplainsSourceAndDestinationAndSkippedLineHasReason | 8 | KI-15 상향·SKIPPED 설명 |
| M3BatchTest#batchFinalizesAbsenceLeaveMissingAndLateHalfApprovalIdempotently | 9 | KI-15로 팀장 휴가를 대표가 승인하는 흐름 |

이번 신규 33개 테스트는 기존 응답 기준선에 없는 보안 계약이다. 실패 시 400/422·ERROR 응답은 테스트로 검증했고 기준선에 덮어쓰지 않았다. 기준선 세 파일의 SHA-256은 시작 커밋과 같다.

```text
sql.json          8ca96939bfbd887a037156bc523f7e60fdd9961910ec950a25c986e73fca4930
responses.json    2e2acc5d706ea90531d44a7f957938f5e9a67a0d3e049d8fde0fc8b41dddcebc
masked-fields.txt 7c4c8e12a7287aafc91ce4073f0ab047be4086f390cd26bc9901669acee5156e
```

SQL은 동작 변경으로 증가할 수 있다. `rejectWithdrawAndResubmitKeepLedgerInvariantAndOriginalDocument`는 272→276회로, 재상신에서 규칙을 확인하는 양식 단계·부서명·부서장·사원 조회가 각 1회 추가됐다. 별도의 동시 문서번호 테스트에서 `select @@session.tx_isolation`만 6→5회로 달랐다(총 369→368). 해당 연결 초기화/트랜잭션 조회의 실행 시점 차이는 이번 기록으로 원인을 확정하지 않았으며, 쿼리를 제거하거나 비교 규칙을 느슨하게 바꾸지 않았다. 기존 응답 137건의 차이 0과 전체 SQL 동일성은 구별한다.

## 전체 실행·기준선·커밋

| 검증 | 결과 |
|---|---|
| 전체 UTC, Java 17, Testcontainers, 기록 OFF | **406/0/0/0**, 68개 클래스, Maven **09:27 min**, BUILD SUCCESS |
| 구성 | 기존 373개 + 신규 SelfApprovalInvariantTest 33개 |
| 수정 전 최종 33개 | 33/26/0/0 (기대된 실패), Maven 19.395 s |
| 수정 후 개별 33개 | 33/0/0/0, Maven 20.616 s |
| 기존 KI-15 | 20/0/0/0, 이후 전체 실행에서도 통과 |
| 기록 전/후 기존 3개 클래스 | 각각 20/0/0/0; 응답 137건 전후 차이 0 |
| 프론트 | build·lint exit 0; 실제 결재선 컴포넌트 렌더링 후보 검사 통과 |

서버 커밋: `2f09adce99a2c950c4ebc78a693634f028756c63` — `fix(api): 직접 지정 본인 결재 차단과 결재자 0명 검증`.

변경 파일 4개: ApprovalLineResolver.java, DocumentService.java, 신규 SelfApprovalInvariantTest.java, 신규 테스트 리소스 approval/self-approval-audit.sql. 이 보고서는 별도 `docs: KI-17 자기 승인 재현과 검증 기록` 커밋이다. 화면은 기존 필터가 충족해 변경 커밋이 없다.

전체 테스트 실행 사본과 작업 트리의 backend/src 파일 내용을 대조해 차이 0을 확인했다. 서버 커밋 이후에는 이 문서만 추가했다.

재현 명령(작업 트리 밖에 backend와 docs/refactoring을 함께 archive한 폴더에서 실행):

```bash
# backend
JAVA_HOME=/Users/kimmini/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home \
  ./mvnw -Duser.timezone=UTC -Dtest=SelfApprovalInvariantTest,ApprovalSelfEscalationTest test
JAVA_HOME=/Users/kimmini/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home \
  ./mvnw -Duser.timezone=UTC test
# frontend (동일 lockfile의 기존 의존성)
npm run build
npm run lint
```

외부 실행 폴더는 `/private/tmp/ki17-self-approval-20261122`다. `before-final.log`(수정 전), `targeted-final.log`(수정 후 33개), `mutant-a.log`, `mutant-b.log`, `audit-before.log`, `full.log`, `frontend-build.log`, `frontend-lint.log`, `frontend-candidates.log`를 사용했다. 전체 원시 로그에는 테스트용 요청이 포함될 수 있어 저장소에 복사하지 않고 실패 요약만 위에 기록했다.

## 남은 한계·미수행

- 이번 변경은 모든 자기 결재를 금지하는 정책이 아니다. 관리자 설정 skip_self=false의 규칙선은 본인 PENDING을 유지하며, B의 조직 조건을 만족한 기안은 즉시 승인한다.
- 현재 조직을 다시 읽지만 기존 조직 변경과 결재선 확정 사이에 새 트리 잠금이나 이력 테이블을 추가하지 않았다. 동시 조직 변경을 직렬화하는 별도 정책은 이번 범위에서 바꾸지 않았다.
- 과거 PENDING·과거 조직 조건을 정확히 재구성할 데이터가 없어 대사 결과의 역사적 위반 확정은 불가능하다. 후보를 확인한 뒤 별도 결정을 해야 한다. 기존 데이터는 고치지 않았다.
- 실제 사용자 화면·운영 서버·실제 SMTP 검증, 배포, 병합·푸시는 수행하지 않았다. 포트폴리오 재현 항목·양식 시드·마이그레이션·backend/md·기준선·기록 정책·.claude는 바꾸지 않았다. 기존 미커밋 KI-10 문서는 이번 커밋에서 제외했다.
