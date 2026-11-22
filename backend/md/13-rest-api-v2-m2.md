# REST API v2 — M2 전자결재

> 상태: 초안 (2026-10-15)
> 공통 규칙은 [03-rest-api.md](03-rest-api.md) 2장, 권한 표기·메뉴 코드는 [12-rest-api-v2-m1.md](12-rest-api-v2-m1.md) 1장을 따릅니다.
> 테이블은 [10-erd-v2.md](10-erd-v2.md) 2장(전자결재)과 3장의 `leave_ledger`, 처리 구조는 [11-architecture.md](11-architecture.md) 4·5장.
> 화면: 시안의 결재함 · 결재 문서 · 기안하기 · 결재 양식 · 내 연차, 홈의 결재 위젯과 알림.

---

## 0. 이번 단계에서 정한 것

| 결정 | 이유 |
|---|---|
| **연차 원장(`leave_ledger`)을 M3에서 M2로 앞당긴다** | 휴가 처리기(상신 때 예약, 승인 때 차감)를 M2에서 옛 숫자 칸으로 만들었다가 M3에서 다시 만들면 두 번 일. 연차 자동 부여 배치만 M3에 남긴다 |
| 알림은 **앱 안 알림만** (`notifications`) | 범위를 줄인다. 메일 발송은 나중에(아웃박스 방식 검토) |
| v1 연차·특근 데이터는 **한 번에 이관** | 빠르게 간다. 무중단 이관(확장 → 이중 기록 → 전환 → 축소)은 개선 과제로 남긴다 |
| 기존 연차·특근 API는 **삭제** | 새 결재 문서 API로 대체. 기존 화면(연차 신청·결재 승인)은 M2 화면 작업에서 함께 교체 |

### 없어지는 기존 API
`/api/v1/leave` (신청·승인·반려·취소·all·my), `/api/v1/overtime/*` 전부. 대신 `/approval-documents`, `/approval-inbox`, `/leave/*`(조회·미리보기)를 씁니다.
`GET /api/v1/leave/policy`는 M3 연차 부여 규칙 때 다시 정합니다(그때까지 유지).

---

## 1. 상태와 흐름

### 1.1 문서 상태 (`approval_documents.status`)
```
DRAFT(임시 저장) ──상신──▶ IN_PROGRESS(진행 중) ──마지막 승인──▶ APPROVED
                                 │──반려──▶ REJECTED
                                 └──회수(아무도 승인 전)──▶ WITHDRAWN
REJECTED / WITHDRAWN ──재기안──▶ 새 DRAFT (resubmitted_from_id = 원문서)
```

### 1.2 결재선 줄 상태 (`approval_lines.status`, 결재자만)
- 상신하면 첫 결재자 `PENDING`, 나머지 `WAITING`. 건너뛴 줄은 `SKIPPED`(+ `skip_reason`: `SELF` / `DUPLICATE`)
- 승인하면 내 줄 `APPROVED`, 다음 `WAITING` 하나가 `PENDING`. 다음이 없으면 문서 `APPROVED`
- 반려하면 내 줄 `REJECTED`, 문서 `REJECTED`, 남은 `WAITING`은 그대로 둔다(진행이 끝났다는 것은 문서 상태로 판단)
- 참조(`REFERENCE`) 줄은 상태 없이 `PENDING`으로 두지 않는다 — 열람만

### 1.3 결재선 규칙 해석 (상신 순간에 한 번)
양식의 단계(`approval_form_steps`)를 순서대로 풀어 사람으로 고정하고, 이름·직급·부서를 스냅샷으로 저장합니다.

| 단계 종류 | 푸는 법 |
|---|---|
| `DEPT_HEAD` | 기안 시점 기안자 부서의 부서장 |
| `UPPER_DEPT_HEAD` | 기안자 부서의 상위 부서의 부서장 |
| `ROLE` | 그 역할을 가진 재직 사원 (여러 명이면 **사용자 번호(`users.id`) 오름차순 첫 사람** — 1차 규칙. 2026-10-24 확정: 로그인 아이디는 v1 형식(user01)과 섞여 있어 순서 기준으로 쓰지 않음) |
| `USER` | 지정된 사원 (재직이 아니면 오류) |

예외 처리 (양식 옵션):
- `fallback_to_upper` — 부서장이 비어 있으면 더 위 부서장. 끝까지 없으면 `422 APPROVER_NOT_FOUND`
  - v1에서 이관한 부서는 부서장이 모두 비어 있습니다(M1 1.5). 관리자가 지정하기 전까지는 상신이 이 오류로 막히고, 결재선 미리보기는 그 단계를 `ERROR`로 보여 줍니다. 화면은 "부서장이 지정되지 않아 결재선을 만들 수 없습니다. 관리자에게 문의하세요."로 안내합니다. 비어 있다고 자동 승인하지 않습니다. (2026-10-15 추가)
- `skip_self` — 결재자가 기안자 본인이면 `SKIPPED(SELF)`
  - **(2026-11-17 변경, KI-15)** `DEPT_HEAD`·`UPPER_DEPT_HEAD` 단계의 결과가 기안자 본인이면 바로 건너뛰지 않고 **그 위 부서장**을 찾습니다(`fallback_to_upper` 설정과 관계없이 위로 올라갑니다). 찾으면 그 사람으로 고정하고 `FALLBACK`, note "플랫폼개발팀 부서장이 기안자 본인이어서 상위 회사 부서장에게 넘어갑니다."
  - **(2026-11-22 정정)** 위로 올라가다 지나친 부서 중 부서장이 비었거나 재직 중이 아닌 곳이 있고 끝내 결재자를 찾지 못하면 `422 APPROVER_NOT_FOUND`입니다. 본인 단계라도 비어 있다고 자동 승인하지 않습니다. `SKIPPED(SELF)`는 **지나친 모든 부서의 부서장이 기안자 본인일 때만**입니다(최상위 부서장 = 대표의 기안).
    - 정정 이유: 처음 문구("위에 재직 중인 부서장이 없을 때만 `SKIPPED(SELF)`")대로 구현하면, 대표 자리가 비었거나 대표가 휴직한 동안 팀장의 특근·근태 정정이 상신 즉시 승인됩니다(2026-11-17 리뷰에서 실제 API로 재현). 위 `fallback_to_upper`의 "비어 있다고 자동 승인하지 않습니다"와 충돌하고, 이번 변경으로 막으려던 자기 승인이 다시 생깁니다.
  - 이유: 특근·근태 정정은 "기안자의 부서장" 한 단계뿐이라, 팀장이 기안하면 본인 단계가 빠져 결재자 0명 → 상신 즉시 승인이었습니다. **팀장이 자기 특근·근태를 혼자 승인**하는 셈입니다(2026-11-17 로컬 점검에서 확인). 버린 방법: 두 양식에 "상위 부서장" 단계를 추가 — 일반 사원 신청까지 모두 대표에게 가고, 대표는 상신할 수 없게 됩니다.
- `UPPER_DEPT_HEAD`에서 기안자 부서에 **상위 부서 자체가 없으면**(최상위 부서의 기안자) 오류 대신 건너뜁니다 (2026-11-17 추가, KI-15). 미리보기는 `SKIPPED`, note "상위 부서가 없어 건너뜁니다." 결재자가 없는 줄이라 **결재선에 저장하지 않습니다**(참조 단계의 대상 없음과 같은 처리). 전에는 대표가 휴가·품의를 기안하면 `APPROVER_NOT_FOUND`로 상신할 수 없었습니다.
  - 상위 부서는 있는데 부서장이 끝까지 비어 있는 경우는 지금처럼 `422 APPROVER_NOT_FOUND`입니다(위의 "비어 있다고 자동 승인하지 않습니다").
- `dedupe_approver` — 앞 단계와 같은 사람이면 `SKIPPED(DUPLICATE)`
- 결재자가 **한 명도 남지 않으면** 상신하는 순간 바로 `APPROVED` (처리기의 `onApproved`까지 같은 트랜잭션). **(2026-11-22 정정, KI-17)** 이 즉시 승인은 **기안자 부서부터 최상위 부서까지 만나는 부서장이 모두 기안자 본인이고 공석·휴직 부서장이 없을 때만**(= 대표 본인 기안) 허용합니다. 그 외에 결재자가 0명이 되면 미리보기 `ERROR`, 상신 `422 APPROVER_NOT_FOUND`, 문서는 `DRAFT`로 남습니다.
  - 이 판단은 결재선 계산 결과와 별도로 조직 정보로 **상신 시점 한 곳에서** 다시 확인합니다(미리보기와 상신이 같은 판단을 씀). 반려·회수 문서의 재기안(`POST /{id}/resubmit`, 새 `DRAFT`로 복사)은 임시 저장과 같아서 **규칙 결재선은 복사할 때 해석하지 않고**, 복사본을 상신할 때 이 판단을 받습니다. 직접 지정 결재선을 복사할 때는 임시 저장과 같은 검사를 받습니다. 결재자가 0명이 되는 경로가 여러 개라 단계별 판단만으로는 막을 수 없기 때문입니다.
  - 0명이 되는 경로: 규칙 단계의 본인(KI-15), 결재선 직접 지정의 본인, `ROLE` 단계의 유일한 보유자 = 기안자, `USER` 단계의 지정 사원 = 기안자.
  - 정정 이유: 처음 문장("위 규칙 이후로는 최상위 부서장만 해당")은 규칙 단계만 보고 쓴 것이라 틀렸습니다. 일반 품의(시드 기본값 `allow_line_edit = true`)에서 결재선을 본인만으로 지정하면 일반 사원도 상신 즉시 `APPROVED`가 됐습니다(2026-11-22 리뷰 중 Testcontainers로 재현).
  - 관리자가 `skip_self`를 끈 양식은 해당하지 않습니다(기안자가 `PENDING` 결재자로 남음).

결과 예 (회사 → 팀, 팀장·대표 지정, 기본 양식)

| 기안자 | 특근 · 근태 정정 (`DEPT_HEAD`) | 휴가 · 품의 (`DEPT_HEAD` → `UPPER_DEPT_HEAD`) |
|---|---|---|
| 사원 | 팀장 | 팀장 → 대표 |
| 팀장 | 대표 (`FALLBACK`) | 대표 (1단계 `FALLBACK`, 2단계 `DUPLICATE`) |
| 대표 | 즉시 승인 (`SKIPPED(SELF)`) | 즉시 승인 (`SKIPPED(SELF)`, 2단계 상위 부서 없음) |
| 팀장 (대표 자리 비었거나 휴직) | `422 APPROVER_NOT_FOUND` | `422 APPROVER_NOT_FOUND` |

- **참조(`REFERENCE`) 단계는 대상이 없으면 오류 없이 건너뜁니다** (2026-10-24 추가). 역할 보유자가 없거나 퇴사해서 참조할 사람이 없을 때 상신 자체가 막히면 안 됩니다(예: 인사 담당이 한 명뿐인데 퇴사 → 모든 휴가 상신 불가). 미리보기는 그 줄을 `SKIPPED`, note "참조할 사람이 없어 건너뜁니다."로 보여 줍니다. 기안자 본인이 참조 대상이면 넣지 않습니다.

기안자가 결재선을 바꾼 경우(`allow_line_edit = true`인 양식만): 규칙 결과 대신 기안자가 보낸 목록을 쓰되, 위 예외 처리(본인·중복)는 똑같이 적용합니다.
- **(2026-11-22 추가, KI-17)** 보낸 목록에 기안자 본인이 `APPROVER`로 있으면 `400 INVALID_APPROVAL_STEPS` "본인은 결재자로 지정할 수 없습니다." 결재선을 받는 모든 입구(임시 저장·수정·미리보기·상신, 직접 지정 결재선을 복사하는 재기안)에서 같습니다. 본인을 `REFERENCE`로 넣으면 지금처럼 조용히 제외합니다. 화면의 결재자 후보에서도 본인을 뺍니다.

---

## 2. 양식 (관리자)

| 메서드 · URL | 설명 | 권한 |
|---|---|---|
| `GET /api/v1/approval-forms` | 양식 목록 (기안 화면에서도 씀) | 로그인만 |
| `GET /api/v1/approval-forms/{code}` | 양식 상세 + 단계 + 옵션 | APPROVAL_FORM V |
| `PATCH /api/v1/approval-forms/{code}` | 옵션 4개 수정 | APPROVAL_FORM U |
| `PUT /api/v1/approval-forms/{code}/steps` | 단계 전체 교체 | APPROVAL_FORM U |
| `POST /api/v1/approval-forms/{code}/line-preview` | 특정 기안자 기준 결재선 미리보기 | APPROVAL_FORM V |

- 양식은 1차에서 3개 고정: `LEAVE`(HR), `OVERTIME`(OT), `GENERAL`(GN). 추가·삭제 API 없음.
- 응답 모양 (2026-10-24 추가)
  - 목록: `[{ "code": "LEAVE", "name": "휴가 신청", "shortName": "휴가", "prefix": "HR", "description": "연차 · 반차 · 병가 · 경조 휴가", "allowLineEdit": false, "approverStepCount": 2, "referenceStepCount": 1 }]`
  - 상세: `{ "code", "name", "shortName", "prefix", "description", "options": { "dedupeApprover": true, "fallbackToUpper": true, "skipSelf": true, "allowLineEdit": false }, "steps": [{ "order": 1, "lineKind": "APPROVER", "approverType": "DEPT_HEAD", "roleCode": null, "userId": null, "label": "기안자의 부서장" }] }`
  - `PATCH` 본문: `options`의 네 값 중 바꿀 것만. 응답은 상세와 같은 모양.
- 단계 저장: `[{ "lineKind": "APPROVER", "approverType": "DEPT_HEAD" }, { "lineKind": "APPROVER", "approverType": "ROLE", "roleCode": "CUSTOM_6" }]`
  - `ROLE`이면 `roleCode`, `USER`면 `userId` 필수. 결재자(APPROVER) 단계가 0개면 `400 INVALID_APPROVAL_STEPS`
  - 저장해도 **이미 상신된 문서는 바뀌지 않습니다**(스냅샷).
- 미리보기 `{ "drafterId": 12 }` →
```json
{
  "rows": [
    { "order": 1, "rule": "기안자의 부서장", "result": { "id": 7, "name": "최민수", "title": "본부장" }, "status": "FALLBACK", "note": "개발1팀 부서장이 기안자 본인이어서 상위 개발본부 부서장에게 넘어갑니다." },
    { "order": 2, "rule": "기안자의 상위 부서장", "result": { "id": 7, "name": "최민수", "title": "본부장" }, "status": "SKIPPED", "note": "앞 단계와 같은 결재자라 건너뜁니다." }
  ],
  "finalLine": [{ "id": 7, "name": "최민수", "title": "본부장" }],
  "error": null
}
```
  - `status`: `OK` / `FALLBACK`(공석이거나 기안자 본인이라 위로 넘김) / `SKIPPED` / `ERROR`(결재자 없음 — 이때 `error`에 메시지)
  - 시안의 '결재 양식' 화면 아래쪽 미리보기가 이 응답을 그대로 그립니다.
  - (2026-10-24 추가) 각 줄에 `"kind": "APPROVER" | "REFERENCE"`를 넣고, 참조 대상은 `"references": [{ "id", "name", "title", "department" }]`로 따로 줍니다. `result`에도 `department`를 넣습니다. `FALLBACK` 줄의 `note`는 "개발2팀 부서장이 비어 있어 개발본부 부서장에게 넘어갑니다."처럼 이유를 적습니다.
  - 기안 화면의 `POST /approval-documents/line-preview` `{ "formCode": "GENERAL", "line": null }`도 **같은 모양**으로 응답합니다(기안자 = 로그인한 사람).

### 2.1 사원 찾기 (선택 목록용, 2026-10-24 추가)
`GET /api/v1/users/lookup?q=김&departmentId=6&size=20` · 권한: 로그인만
- 업무 인수자 고르기, 일반 품의 결재선 편집, 양식의 '특정 사원' 단계처럼 **사원을 고르는 칸**에 씁니다. `GET /users`는 사원 관리 권한(EMPLOYEE V)이 있어야 해서 일반 사원은 쓸 수 없습니다.
- 재직(`ACTIVE`) 사원만, 최소 정보만: `[{ "id": 10, "name": "이도윤", "title": "대리", "department": "개발1팀", "departmentId": 6 }]`
- `q`는 이름 또는 로그인 아이디 앞부분 일치, 이름순, `size` 최대 100. 이메일·입사일·역할은 주지 않습니다.

---

## 3. 결재 문서

| 메서드 · URL | 설명 | 권한 |
|---|---|---|
| `POST /api/v1/approval-documents/line-preview` | 기안 화면: 내 결재선 미리보기 | APPROVAL_DRAFT C |
| `POST /api/v1/approval-documents` | 임시 저장 (DRAFT 생성) | APPROVAL_DRAFT C |
| `PUT /api/v1/approval-documents/{id}` | 임시 저장 수정 | 기안자 본인, DRAFT만 |
| `DELETE /api/v1/approval-documents/{id}` | 임시 저장 삭제 | 기안자 본인, DRAFT만 |
| `POST /api/v1/approval-documents/{id}/submit` | 상신 | 기안자 본인 |
| `GET /api/v1/approval-documents/{id}` | 문서 상세 | 기안자 · 결재선·참조에 있는 사람 · 관리소장 |
| `POST /api/v1/approval-documents/{id}/approve` | 승인 `{ "comment": "…" }` (선택) | 지금 차례인 결재자 |
| `POST /api/v1/approval-documents/{id}/reject` | 반려 `{ "comment": "…" }` (필수) | 지금 차례인 결재자 |
| `POST /api/v1/approval-documents/{id}/withdraw` | 회수 | 기안자, 아무도 승인 전 |
| `POST /api/v1/approval-documents/{id}/resubmit` | 재기안: 내용을 복사한 새 DRAFT | 기안자, REJECTED·WITHDRAWN만 |

### 3.1 임시 저장
```json
// POST /api/v1/approval-documents
{
  "formCode": "LEAVE",
  "title": "연차 · 10/19~10/20",
  "body": { "leaveType": "ANNUAL", "startDate": "2026-10-19", "endDate": "2026-10-20", "handoverUserId": 10, "reason": "가족 행사" },
  "line": null
}
// Response 201
{ "id": 142, "status": "DRAFT" }
```
- `body`는 양식마다 다릅니다:
  - `LEAVE`: `leaveType`(ANNUAL / HALF_AM / HALF_PM / SICK / FAMILY), `startDate`, `endDate`(반차는 시작일과 같게), `handoverUserId`(선택), `reason`
  - `OVERTIME`: `overtimeDate`, `startTime`, `endTime`, `reason` — **하루 단위로 통일 (KI-01 해결)**
  - `GENERAL`: `content`, `amount`(선택)
- `line`: 기안자가 바꾼 결재선. `null`이면 규칙대로. `[{ "userId": 12, "lineKind": "APPROVER" }, …]` 순서대로. `allow_line_edit = false` 양식에 보내면 `403 LINE_EDIT_NOT_ALLOWED`
- 임시 저장 단계에서는 형식만 검사합니다(필수값, 날짜 형식). 업무 규칙 검사는 상신 때.

### 3.2 상신 — 한 트랜잭션 (아키텍처 5장)
`POST /api/v1/approval-documents/{id}/submit`
1. 기안자 사용자 행 잠금 (`SELECT … FOR UPDATE`) — 같은 사람의 동시 상신을 한 줄로 세움
2. 처리기 `validate` — 휴가: 차감 일수 계산(주말·공휴일 제외), 잔여 초과, 같은 기간 진행 중·승인된 휴가와 겹침 / 특근: 종료 > 시작
3. 문서번호 발급 — `doc_no_sequences`(양식 접두어·연도) 행 잠금 → `HR-2026-0142`
4. 결재선 규칙 해석 + 스냅샷 저장 (1.3)
5. 처리기 `onSubmitted` — 휴가: 원장 `RESERVE(−일수)`
6. 이력 `SUBMIT`, `LINE_GENERATED` / 알림: 첫 결재자, 참조자
- 응답 200: 문서 상세(3.3)와 같은 모양
- 에러: `422 LEAVE_BALANCE_EXCEEDED`, `409 LEAVE_PERIOD_OVERLAP`, `422 LEAVE_NO_WORKING_DAY`(기간이 모두 휴일), `422 APPROVER_NOT_FOUND`, `400 INVALID_OVERTIME_TIME`, `409 DOCUMENT_STATE_CONFLICT`(이미 상신됨)

### 3.3 문서 상세
```json
{
  "id": 142, "docNo": "HR-2026-0142", "form": { "code": "LEAVE", "name": "휴가 신청" },
  "title": "연차 · 10/19~10/20", "status": "IN_PROGRESS",
  "drafter": { "id": 12, "name": "김서연", "title": "사원", "department": "개발1팀" },
  "submittedAt": "2026-10-12T09:14:00", "completedAt": null,
  "body": { "leaveType": "ANNUAL", "startDate": "2026-10-19", "endDate": "2026-10-20", "usedDays": 2.0, "handover": { "id": 10, "name": "이도윤" }, "reason": "가족 행사" },
  "line": [
    { "order": 1, "kind": "APPROVER", "approver": { "id": 8, "name": "박지훈", "title": "팀장", "department": "개발1팀" }, "source": "RULE", "rule": "기안자의 부서장", "status": "PENDING", "actedAt": null, "comment": null },
    { "order": 2, "kind": "APPROVER", "approver": { "id": 7, "name": "최민수", "title": "본부장", "department": "개발본부" }, "source": "RULE", "rule": "기안자의 상위 부서장", "status": "WAITING", "actedAt": null, "comment": null }
  ],
  "references": [{ "id": 3, "name": "이수아", "title": "대리", "department": "인사팀" }],
  "history": [
    { "action": "SUBMIT", "actor": { "id": 12, "name": "김서연" }, "comment": null, "at": "2026-10-12T09:14:00" },
    { "action": "LINE_GENERATED", "actor": null, "comment": "'휴가 기본' 규칙", "at": "2026-10-12T09:14:00" }
  ],
  "myActions": { "canApprove": true, "canReject": true, "canWithdraw": false, "canResubmit": false },
  "resubmittedFromId": null
}
```
- 결재선의 이름·직급·부서는 **스냅샷 값**입니다(상신 뒤 조직이 바뀌어도 그대로).
- (2026-10-24 추가) `line`에는 결재자(`APPROVER`) 줄만 넣고 참조는 `references`에만 둡니다. 건너뛴 줄은 `"status": "SKIPPED", "skipReason": "SELF" | "DUPLICATE"`.
- `myActions`는 화면 버튼을 그리는 용도. 서버는 요청 때 다시 검사합니다.

### 3.4 승인 · 반려 · 회수 — 동시 처리 (KI-03)
- 승인: `UPDATE approval_lines SET status='APPROVED', acted_at=?, comment=? WHERE id=? AND status='PENDING'` → **영향 행이 1일 때만 성공.** 문서는 `version`(낙관적 잠금)으로 한 번 더 확인.
  - 늦게 온 요청(이미 처리됨, 내 차례 아님): `409 DOCUMENT_STATE_CONFLICT`
  - 마지막 결재자면 문서 `APPROVED` + 처리기 `onApproved` (휴가: 원장 `USE(−)` + `RELEASE(+)`, 같은 문서 근거) — **같은 트랜잭션**
- 반려: `comment` 없으면 `400 REJECT_REASON_REQUIRED`. 처리기 `onRejected` (휴가: `RELEASE(+)`)
- 회수: 결재선에 `APPROVED`가 하나라도 있으면 `409 WITHDRAW_NOT_ALLOWED`. 처리기 `onWithdrawn` (휴가: `RELEASE(+)`)
- 승인과 회수가 동시에 오면 둘 중 하나만 성공해야 합니다(문서 `version`).
- 알림: 중간 승인 → 다음 결재자 / 마지막 승인 → 기안자 / 반려 → 기안자 / 회수 → 지금 차례였던 결재자

### 3.5 재기안
`POST /api/v1/approval-documents/{id}/resubmit` → `201 { "id": 151, "status": "DRAFT" }`. 제목·본문·(기안자가 바꿨다면) 결재선을 복사하고 `resubmittedFromId = 142`. 원문서는 그대로 기록으로 남습니다.

---

## 4. 결재함

| 메서드 · URL | 설명 | 권한 |
|---|---|---|
| `GET /api/v1/approval-inbox` | 탭별 목록 | APPROVAL_INBOX V |
| `GET /api/v1/approval-inbox/counts` | 탭별 건수 (사이드바 배지, 홈 위젯) | APPROVAL_INBOX V |

`GET /api/v1/approval-inbox?box=todo&formCode=LEAVE&q=연차&page=0&size=20`

| `box` | 뜻 | 조건 |
|---|---|---|
| `todo` | 결재 대기 (내 차례) | 내 줄이 `PENDING` |
| `in-progress` | 결재 진행 | 내가 승인했고 문서가 아직 `IN_PROGRESS` |
| `done` | 결재 완료 | 내가 결재선에 있고 문서가 `APPROVED`·`REJECTED` |
| `mine` | 내 기안 | 내가 기안, `DRAFT` 제외 |
| `drafts` | 임시 보관 | 내가 기안, `DRAFT` |
| `reference` | 참조 | 내가 참조자 |

```json
{
  "content": [
    {
      "id": 142, "docNo": "HR-2026-0142", "formName": "휴가", "title": "연차 · 10/19~10/20",
      "drafter": { "name": "김서연", "title": "사원", "department": "개발1팀" },
      "submittedAt": "2026-10-12T09:14:00", "status": "IN_PROGRESS",
      "steps": ["DONE", "CURRENT", "TODO"],
      "currentApprover": { "name": "박지훈", "title": "팀장" }
    }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 3, "totalPages": 1, "first": true, "last": true, "numberOfElements": 3 }
}
```
- `steps`: 기안을 포함한 진행 점(시안의 ●─●─○). `DONE` / `CURRENT` / `TODO` / `REJECTED` / `WITHDRAWN` / `SKIPPED`
- `counts` → `{ "todo": 3, "in-progress": 2, "done": 3, "mine": 3, "drafts": 2, "reference": 1 }`
- (2026-10-24 추가) 행에는 `formCode`와 `updatedAt`도 넣습니다. `formName`은 짧은 이름(휴가/특근/품의). 임시 보관(`drafts`)은 `docNo`·`submittedAt`이 없으므로 `updatedAt` 내림차순, 나머지 탭은 `submittedAt` 내림차순.
- **"내 차례" 조회는 인덱스 `approval_lines(approver_id, status, document_id)`를 타야 합니다.** 목록 한 페이지를 가져오는 데 문서 수와 상관없이 쿼리 수가 일정해야 합니다(N+1 금지 — 결재선 진행 점은 한 번에 모아서).

---

## 5. 연차 (원장 — M2로 앞당김)

| 메서드 · URL | 설명 | 권한 |
|---|---|---|
| `GET /api/v1/leave/balance?year=2026` | 내 연차 요약 | 로그인만 (본인) |
| `GET /api/v1/leave/ledger?year=2026&type=` | 내 원장 | 로그인만 (본인) |
| `POST /api/v1/leave/days-preview` | 기안 화면: 차감 일수 미리 계산 | APPROVAL_DRAFT C |

- 요약: `{ "year": 2026, "granted": 15.0, "used": 4.0, "reserved": 2.0, "available": 9.0 }`
  - `available = SUM(days)` (원장 합계), `reserved` = 아직 `RELEASE`되지 않은 `RESERVE` 합계의 절댓값
- 원장: `[{ "date": "2026-10-12", "type": "RESERVE", "days": -2.0, "balance": 9.0, "memo": "연차 10/19~10/20", "document": { "id": 142, "docNo": "HR-2026-0142", "status": "IN_PROGRESS" } }]` — 최신순, `balance`는 그 줄까지의 누적
- 미리 계산 `{ "leaveType": "ANNUAL", "startDate": "2026-10-19", "endDate": "2026-10-20" }` → `{ "days": 2.0, "offDays": 0, "available": 11.0, "after": 9.0, "error": null }`
  - 반차 0.5, 병가·경조는 0(연차에서 빼지 않음). 공휴일은 `holidays` 테이블 기준
  - (2026-10-24 추가) **연도가 바뀌는 휴가는 받지 않습니다**: 시작일과 종료일의 연도가 다르면 `422 LEAVE_CROSS_YEAR`. 미리 계산도 같은 내용을 `error`로 돌려줍니다. 연차는 연도별로 부여·소멸하므로 한 문서가 두 해의 원장을 건드리지 않게 합니다(연도별로 나눠 신청).
  - (2026-10-24 추가) 차감 일수가 0인 휴가(병가·경조)는 원장 줄을 쓰지 않습니다.
  - 원장 `memo`는 사람이 읽는 문구로: `"연차 10/19~10/20"`, `"예약 해제 (반려)"`, `"v1 이관"`
- 원장 쓰기 API는 없습니다. 원장은 처리기와 (M3) 배치·연차 관리 화면만 씁니다.

### 원장 규칙 (ERD 3장 + 결정 1)
| 사건 | 원장 줄 |
|---|---|
| 상신 | `RESERVE(−일수)` |
| 최종 승인 | `USE(−일수)` + `RELEASE(+일수)` |
| 반려 · 회수 | `RELEASE(+일수)` |
| 승인 후 취소 | M3 (근태 정정과 함께) |

- **불변식**: 어떤 순서로 처리해도 `SUM(days)` = 부여 − 승인된 사용 − 진행 중 예약. 테스트로 확인합니다.

---

## 6. 알림 (앱 안)

| 메서드 · URL | 설명 | 권한 |
|---|---|---|
| `GET /api/v1/notifications?unreadOnly=false&page=0&size=20` | 내 알림 | 로그인만 |
| `GET /api/v1/notifications/unread-count` | 안 읽은 수 | 로그인만 |
| `PATCH /api/v1/notifications/{id}/read` | 하나 읽음 | 본인 것만 |
| `POST /api/v1/notifications/read-all` | 모두 읽음 | 로그인만 |

- 알림 행은 결재 처리와 **같은 트랜잭션**에서 저장합니다. 결재가 롤백되면 알림도 없습니다.
- 항목: `{ "id", "type": "APPROVAL_REQUESTED|APPROVED|REJECTED|WITHDRAWN|REFERENCED", "title", "link": "/approvals/142", "read": false, "createdAt" }`
- 안 읽은 수: `{ "count": 3 }`. 목록은 결재함과 같은 페이지 모양(`content`, `page`).

---

## 7. v1 데이터 이관 (Flyway)

1. 양식 3개와 기본 단계 초기 데이터
   - `LEAVE`: 부서장 → 상위 부서장, 참조 '인사 담당' 역할(있으면) / `OVERTIME`: 부서장 / `GENERAL`: 부서장 → 상위 부서장
   - 옵션: 세 양식 모두 `dedupe`·`fallback`·`skip_self` = true, `allow_line_edit`는 `GENERAL`만 true
2. `leave_requests` · `overtime_requests` 각 행 → `approval_documents` 1건 + 기존 `approver_id`로 결재선 1줄(있으면). 상태: PENDING → IN_PROGRESS(줄 PENDING), APPROVED → APPROVED, REJECTED → REJECTED, CANCELLED → WITHDRAWN. 문서번호는 생성일 순서로 발급.
3. 두 테이블에 `document_id` 추가 후 `approver_id`, 상태 컬럼, `requester_id` 삭제(기안자는 문서에)
4. 연차: `users.total_annual_leave` → `GRANT`, `used_annual_leave` → `USE`(memo "v1 이관"), 진행 중 연차 신청 → `RESERVE`. 이관 뒤 세 숫자 컬럼 삭제
5. 이관 결과를 검증하는 테스트: 이관 전후 사원별 잔여 연차가 같다.

---

## 8. 새 에러 코드

| 코드 | 상태 | 메시지 |
|---|---|---|
| `DOCUMENT_NOT_FOUND` | 404 | 결재 문서를 찾을 수 없습니다. |
| `DOCUMENT_STATE_CONFLICT` | 409 | 이미 처리되었거나 지금 처리할 수 없는 문서입니다. 새로고침해 주세요. |
| `WITHDRAW_NOT_ALLOWED` | 409 | 이미 승인한 사람이 있어 회수할 수 없습니다. |
| `LEAVE_PERIOD_OVERLAP` | 409 | 같은 기간에 이미 신청한 휴가가 있습니다. |
| `LEAVE_BALANCE_EXCEEDED` | 422 | 사용 가능한 연차보다 많이 신청할 수 없습니다. |
| `LEAVE_NO_WORKING_DAY` | 422 | 선택한 기간이 모두 휴일입니다. |
| `LEAVE_CROSS_YEAR` | 422 | 연도가 바뀌는 휴가는 연도별로 나눠 신청해 주세요. |
| `APPROVER_NOT_FOUND` | 422 | 결재자를 찾을 수 없습니다. 결재 양식이나 부서장 설정을 확인해 주세요. |
| `LINE_EDIT_NOT_ALLOWED` | 403 | 이 양식은 결재선을 바꿀 수 없습니다. |
| `INVALID_APPROVAL_STEPS` | 400 | 결재자 단계가 하나 이상 있어야 합니다. |
| `FORM_NOT_FOUND` | 404 | 결재 양식을 찾을 수 없습니다. |

---

## 9. 완료 기준 — 꼭 테스트로 확인할 것 (MariaDB Testcontainers)

1. **동시 승인 (KI-03)**: 같은 줄을 두 스레드가 동시에 승인 → 한쪽만 성공, 한쪽 409, 원장 `USE`는 한 번. **고치기 전 구조(조건 없이 저장)로 같은 실험을 해서 N번 중 몇 번 깨지는지 먼저 기록**하고 troubleshooting 문서에 전후 수치를 남깁니다.
2. **승인 vs 회수 동시**: 둘 중 하나만 성공.
3. **잔여 초과 동시 상신**: 잔여 2일인 사람이 2일짜리 두 건을 동시에 상신 → 한 건만 성공.
4. **문서번호**: 같은 양식 동시 상신 N건 → 번호 중복 없음.
5. **결재선 해석 4가지**: 보통 / 기안자가 부서장(본인 건너뜀) / 부서장 공석(위로 넘김) / 같은 사람 두 단계(한 번만).
6. **스냅샷**: 상신 후 부서장을 바꿔도 진행 중 문서의 결재자는 그대로.
7. **원장 불변식**: 상신·승인·반려·회수를 섞어 돌린 뒤 잔여 = 원장 합계.
8. **결재함 쿼리**: 문서 1만 건 더미에서 `todo` 목록 EXPLAIN이 인덱스를 타고, 페이지 하나의 쿼리 수가 문서 수와 상관없음. 실행 시간을 기록.
9. **특근 (KI-01)**: `@Disabled` 걸어 둔 특근 테스트를 새 API 기준으로 되살려 통과.

---

## 10. 화면 ↔ API (시안 기준)

| 화면 | 동작 | API |
|---|---|---|
| 결재함 | 탭·건수·목록 | `GET /approval-inbox`, `GET /approval-inbox/counts` |
| 결재 문서 | 상세·결재선·이력 | `GET /approval-documents/{id}` |
| | 승인 · 반려 · 회수 · 재기안 | `POST …/approve`, `…/reject`, `…/withdraw`, `…/resubmit` |
| 기안하기 | 양식 고르기 | `GET /approval-forms` |
| | 결재선 미리보기·편집 | `POST /approval-documents/line-preview` |
| | 휴가 일수·잔여 | `POST /leave/days-preview` |
| | 임시 저장 · 상신 | `POST /approval-documents` → `POST …/{id}/submit` |
| 결재 양식 | 단계·옵션·미리보기 | `GET/PATCH /approval-forms/{code}`, `PUT …/steps`, `POST …/line-preview` |
| 내 연차 | 요약·원장 | `GET /leave/balance`, `GET /leave/ledger` |
| 상단·홈 | 알림·결재 대기 수 | `GET /notifications`, `…/unread-count`, `GET /approval-inbox/counts` |
