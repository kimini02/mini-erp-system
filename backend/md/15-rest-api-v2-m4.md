# REST API v2 — M4 홈 · 프로젝트 보드

> 기준: [10-erd-v2.md](10-erd-v2.md) 4장(그대로 두던 프로젝트·업무 테이블), [11-architecture.md](11-architecture.md), 화면 시안 `Home`·`Kanban`.
> 공통 규칙(응답 모양, 에러, 권한 `@RequirePermission`, 데이터 범위, 시각 `Clock`)은 [12-rest-api-v2-m1.md](12-rest-api-v2-m1.md)를 따릅니다.
> 이 단계의 목표는 기능만이 아니라 **백엔드 문제를 재현하고 고친 기록(전후 수치)** 을 남기는 것입니다. 8장의 측정 항목은 기능 테스트와 같은 무게로 다룹니다.

---

## 0. 결정

| 결정 | 이유 |
|---|---|
| 홈은 **`GET /api/v1/home` 한 번**으로 위젯 데이터를 모두 받음 | 지금 홈은 화면이 API를 여러 번 부르고, 기존 대시보드 API는 프로젝트마다 쿼리를 더 날림(1+2N). 요청 1번 · 쿼리 수 고정으로 바꾸고 전후를 잼 |
| 프로젝트 권한을 v1 역할 3개(`ADMIN`/`TEAM_LEADER`/`USER`)에서 **`PROJECT` 메뉴 권한**으로 | M1에서 메뉴 권한·데이터 범위를 만들었는데 프로젝트만 옛 방식으로 남아 있음 |
| **프로젝트 행 하나가 잠금 단위** — 업무를 만들거나 옮기거나 상태를 바꾸는 쓰기는 먼저 `projects` 행을 `FOR UPDATE` | 프로젝트 상태 재계산(쓰기 왜곡), 카드 순서, 업무 번호가 모두 "같은 프로젝트 안의 여러 행을 보고 쓰는" 작업이라 한 곳에서 줄을 세움 |
| 프로젝트 상태(`READY`/`PROGRESS`/`DONE`)는 저장하되, **잠금 안에서 집계 쿼리로** 다시 계산 | 기존 코드는 업무 엔티티 목록을 통째로 읽어 계산(업무 1,000개면 1,000행) |
| 카드 순서는 **간격을 둔 `position`** (1024 간격, 사이에 끼우면 중간값) | 옮길 때마다 열 전체를 다시 번호 매기면 UPDATE가 카드 수만큼 생김 |
| 오래된 화면에서 옮기면 **409** (`tasks.version` 비교) | 다른 사람이 먼저 옮긴 카드를 모르고 덮어쓰지 않게 |
| 업무 번호는 프로젝트 키 + 순번 (`GW-21`) | 시안의 카드 번호. 순번은 프로젝트 행 잠금 안에서 `last_task_seq + 1` |
| 기존 대시보드·프로젝트·업무·프로젝트별 권한 API는 **새 API로 바꾼 뒤 삭제** | M2·M3처럼 빈 응답이나 우회 경로를 남기지 않음 |
| 캘린더(`/calendar`)는 이번 범위 밖 | 회의 일정 모델이 없음. 홈의 '오늘 일정' 위젯은 빼고 '이번 주 팀'으로 대신함 |

---

## 1. 권한 (`PROJECT` 메뉴, V2에서 이미 만듦)

| 역할 (V2 기본값) | 보기 | 등록 | 수정 | 범위 |
|---|---|---|---|---|
| 관리소장 | O | O | O | ALL |
| 팀장 | O | O | O | DEPT |
| 사원 | O | | | SELF |

- **보기 범위**: `SELF` = 내가 팀장이거나 멤버인 프로젝트 · `DEPT` = SELF + 팀장이 내 부서 사람인 프로젝트 · `DEPT_TREE` = SELF + 팀장이 내 부서와 하위 부서 사람인 프로젝트 · `ALL` = 전체.
  - 범위는 **쿼리 조건**으로 거릅니다(M1-03과 같은 방식). 목록에서 빼고, 상세·보드를 직접 열면 `403 ACCESS_DENIED`.
- **관리(수정)** = `PROJECT U` + 그 프로젝트가 내 보기 범위 안 **또는** 내가 그 프로젝트의 팀장(`projects.leader_id`). 관리할 수 있으면 프로젝트 수정, 멤버 변경, 업무 생성·수정·배정, 모든 카드 이동.
- **카드 이동·상태 변경** = 관리할 수 있는 사람 **또는 그 업무의 담당자**. (시안: "담당자와 프로젝트 담당 팀장만 상태를 바꿀 수 있습니다")
- 프로젝트 등록 = `PROJECT C`. 팀장(`leaderId`)은 재직 중인 사원 아무나 지정할 수 있고, 팀장은 자동으로 멤버가 됩니다.
- 응답의 `permissions`(`canManage`, `canCreateTask`)와 카드의 `canMove`는 화면 표시용이고, 서버가 같은 규칙으로 다시 검사합니다.
- (2026-10-27 확정) **모든 쓰기에도 `PROJECT V`가 먼저 필요**합니다. V가 없는 팀장 예외는 두지 않습니다(기본 역할은 모두 V 보유).

---

## 2. 홈

`GET /api/v1/home` — 로그인한 사람 누구나. 위젯마다 해당 메뉴 권한이 없으면 그 위젯은 `null`.

```json
{
  "today": { "...": "GET /attendance/today 와 같은 모양 (MY_ATTENDANCE V)" },
  "approvals": {
    "todoCount": 3,
    "todo": [{ "id": 151, "docNo": "HR-2026-0144", "form": { "code": "LEAVE", "shortName": "휴가" }, "title": "연차 · 10/19~10/20", "drafter": { "id": 12, "name": "김서연", "title": "사원" }, "submittedAt": "2026-10-12T09:14:00" }],
    "mine": [{ "id": 160, "docNo": "OT-2026-0061", "form": { "code": "OVERTIME", "shortName": "특근" }, "title": "주말 특근 · 10/17", "status": "IN_PROGRESS", "currentApprover": { "id": 8, "name": "박지훈", "title": "팀장" }, "submittedAt": "2026-10-11T17:40:00" }]
  },
  "leave": { "year": 2026, "granted": 17, "used": 6, "reserved": 1, "available": 10 },
  "teamWeek": {
    "department": { "id": 6, "name": "개발1팀" },
    "from": "2026-10-12", "to": "2026-10-16",
    "days": [
      { "date": "2026-10-14", "items": [
        { "type": "HALF_PM", "user": { "id": 11, "name": "오태민" }, "status": "IN_PROGRESS", "documentId": 170 },
        { "type": "TASK_DUE", "task": { "id": 17, "key": "GW-17", "title": "부서 트리 API" }, "project": { "id": 3, "key": "GW" } }
      ] }
    ]
  },
  "myTasks": {
    "project": { "id": 3, "key": "GW", "title": "사내 그룹웨어 개발", "total": 10, "done": 3, "overdue": 1, "progressPct": 30 },
    "items": [{ "id": 18, "key": "GW-18", "title": "사원 등록·아이디 생성", "status": "DOING", "endDate": "2026-10-09", "overdueDays": 3 }]
  },
  "notifications": { "unread": 3, "items": [{ "id": 901, "type": "APPROVAL_REQUESTED", "title": "연차 · 10/19~10/20", "link": "/approvals/151", "read": false, "createdAt": "2026-10-12T09:14:00" }] }
}
```

- `approvals.todo`: 내 결재 차례 문서 최근 3건(M2 결재함 '결재 대기'와 같은 조건), `todoCount`는 전체 건수. `mine`: 내가 올린 진행 중 문서 최근 3건.
- `teamWeek`: **내 부서**(하위 부서 제외) 사원의 이번 주 월~금. 승인됐거나 결재 중인 휴가(`LEAVE`/`HALF_AM`/`HALF_PM`, 결재 중이면 `status: IN_PROGRESS`)와, 내 부서 사람이 담당인 미완료 업무의 마감일(`TASK_DUE`). 휴가 사유는 내보내지 않음.
- `myTasks.project`: 내가 담당인 미완료 업무가 가장 많은 프로젝트(같으면 최근 수정순). `items`: 내 미완료 업무 5건, **마감 지난 것 먼저**, 그다음 마감일 순.
- `notifications`: 안 읽은 수와 최근 5건(M2 알림).
- **쿼리 수가 데이터 양과 상관없이 고정**이어야 합니다(목표 15번 이하, 8장 측정 4).

---

## 3. 프로젝트

| 메서드 · URL | 설명 | 권한 |
|---|---|---|
| `GET /api/v1/projects?status=&q=&page=&size=` | 내 범위의 프로젝트 목록 | PROJECT V (범위) |
| `POST /api/v1/projects` | 등록 | PROJECT C |
| `GET /api/v1/projects/{id}` | 상세 + 멤버 | 보기 범위 |
| `PATCH /api/v1/projects/{id}` | 이름·기간·우선순위·팀장 수정 | 관리 |
| `PUT /api/v1/projects/{id}/members` `{ "userIds": [9, 10, 12] }` | 멤버 바꾸기 (팀장은 항상 포함) | 관리 |

목록 행:
```json
{ "id": 3, "key": "GW", "title": "사내 그룹웨어 개발", "status": "PROGRESS", "priority": "HIGH",
  "startDate": "2026-09-01", "endDate": "2026-12-31",
  "leader": { "id": 8, "name": "박지훈", "title": "팀장" },
  "memberCount": 7, "taskCount": 10, "doneCount": 3, "overdueCount": 1, "progressPct": 30, "myRole": "LEADER" }
```
- **목록 쿼리 수는 프로젝트 수와 상관없이 고정**(집계는 GROUP BY 한 번, 팀장 이름은 조인). 기존 `ProjectResponseDto`처럼 팀장을 지연 로딩으로 하나씩 읽지 않습니다.
- `key`: 영문 대문자·숫자 2~10자, 회사 안에서 유일(`409 PROJECT_KEY_DUPLICATE`). 등록 뒤에는 바꾸지 않습니다(업무 번호가 바뀌면 안 됨).
- 멤버에서 빠지는 사람이 담당인 미완료 업무가 있으면 `409 PROJECT_MEMBER_HAS_TASKS`(먼저 담당자를 바꿈).

---

## 4. 보드 · 업무

| 메서드 · URL | 설명 | 권한 |
|---|---|---|
| `GET /api/v1/projects/{id}/board` | 열 3개와 카드 | 보기 범위 |
| `POST /api/v1/projects/{id}/tasks` | 업무 추가 (빠른 추가: 제목만) | 관리 |
| `GET /api/v1/tasks/{id}` | 상세 + 변경 이력 | 보기 범위 |
| `PATCH /api/v1/tasks/{id}` | 제목·설명·마감일·우선순위·담당자 | 관리 |
| `POST /api/v1/tasks/{id}/move` | 열 이동·순서 바꾸기 | 관리 또는 담당자 |

### 4.1 보드
```json
{
  "project": { "id": 3, "key": "GW", "title": "사내 그룹웨어 개발", "status": "PROGRESS", "startDate": "2026-09-01", "endDate": "2026-12-31",
               "leader": { "id": 8, "name": "박지훈", "title": "팀장" }, "taskCount": 10, "doneCount": 3, "progressPct": 30 },
  "members": [{ "id": 9, "name": "정하은", "title": "과장" }],
  "permissions": { "canManage": true, "canCreateTask": true },
  "columns": [
    { "status": "TODO", "tasks": [
      { "id": 21, "key": "GW-21", "title": "결재선 규칙 해석 API", "priority": "HIGH", "endDate": "2026-10-20", "overdueDays": 0,
        "assignees": [{ "id": 10, "name": "이도윤" }], "position": 1024, "version": 3, "canMove": true }
    ] },
    { "status": "DOING", "tasks": [] },
    { "status": "DONE", "tasks": [] }
  ]
}
```
- 카드는 열 안에서 `position` 오름차순. 보드 전체를 **쿼리 2~3번**으로(업무 1번 + 담당자 1번, 카드마다 추가 조회 없음).
- 필터(담당자, 내 업무만, 검색)는 화면에서 합니다. 한 프로젝트의 업무는 수백 개 이하로 가정.

### 4.2 업무 추가
`{ "title": "새 업무", "status": "TODO", "assigneeIds": [], "endDate": null, "priority": "MEDIUM", "content": "" }`
- 제목만 필수. `endDate`가 없으면 프로젝트 종료일, `status`가 없으면 `TODO`. 담당자는 프로젝트 멤버만(`422 TASK_ASSIGNEE_NOT_MEMBER`).
- 프로젝트 행을 잠근 뒤 `task_no = last_task_seq + 1`, `position = 그 열 맨 끝 + 1024`.
- 담당자에게 `TASK_ASSIGNED` 알림(같은 트랜잭션, M2 알림과 같은 방식).

### 4.3 이동
`POST /api/v1/tasks/{id}/move` `{ "status": "DOING", "afterTaskId": 17, "version": 3 }`
- `afterTaskId`: 옮긴 뒤 바로 위에 올 카드(없으면 `null` = 맨 위).
- 순서: **프로젝트 행 잠금 → 업무 `version` 비교(다르면 `409 TASK_VERSION_CONFLICT`, 최신 카드를 같이 돌려줌) → 위치 계산 → 저장(`version + 1`) → 이력 → 프로젝트 상태 재계산**.
- 위치 계산: 위 카드와 아래 카드 `position`의 중간값. 간격이 1 이하로 좁아지면 **그 열만** 1024 간격으로 다시 번호를 매기고 계산합니다(드물게 일어남, 8장 측정 3에서 횟수 기록).
- `afterTaskId`가 다른 열이거나 다른 프로젝트 카드면 `422 INVALID_TASK_POSITION`.
- 응답(2026-10-27 확정): `{ "task": 카드, "project": { "id", "status", "taskCount", "doneCount", "progressPct" } }`. 열 안 순서만 바뀌면 이력은 `MOVED`, 열이 바뀌면 `STATUS`.
- 409 응답은 공통 에러에 최신 카드를 붙입니다: `{ "error": { "code": "TASK_VERSION_CONFLICT", "message": "…", "details": { "currentTask": 카드 } } }`. 화면은 그 카드로 바꿔 그리고 다시 옮기게 합니다.
- 다시 번호를 매긴 다른 카드의 `version`은 그대로입니다(상대 순서가 같으므로). 직접 옮기거나 고친 카드만 `version + 1`.
- `PATCH /tasks/{id}`는 `version`을 검사하지 않습니다(필드 단위 마지막 저장 우선). 고치면 `version`이 올라가서 다른 화면의 이동은 409가 됩니다.

### 4.4 프로젝트 상태 재계산 (이동·추가·수정 공통)
- 잠금 안에서 `SELECT task_status, COUNT(*) FROM tasks WHERE project_id = ? GROUP BY task_status` 한 번.
- 업무가 없거나 모두 `TODO` → `READY` · 모두 `DONE`(1개 이상) → `DONE` · 그 밖 → `PROGRESS`.
- 업무 엔티티 목록을 메모리로 읽어 계산하지 않습니다(8장 측정 2).

### 4.5 상세와 이력
```json
{ "id": 18, "key": "GW-18", "projectId": 3, "title": "사원 등록·아이디 생성", "content": "입사연도 + 순번으로…",
  "status": "DOING", "priority": "HIGH", "endDate": "2026-10-09", "overdueDays": 3,
  "assignees": [{ "id": 12, "name": "김서연", "title": "사원" }], "version": 5, "canMove": true, "canManage": false,
  "history": [{ "action": "STATUS", "from": "TODO", "to": "DOING", "actor": { "id": 12, "name": "김서연" }, "at": "2026-10-08T10:02:00" }] }
```
- `history`: 최근 50건, 새것부터. `action`: `CREATED`/`STATUS`/`MOVED`/`ASSIGNED`/`UNASSIGNED`/`UPDATED`/`OVERDUE_NOTIFIED`.
  - (2026-10-27 확정) `MOVED`는 `from`/`to` 없이(위치 숫자는 의미가 없음), `ASSIGNED`·`UNASSIGNED`는 `target: { "id", "name" }`을 함께 내보냅니다(멤버에서 빠진 사람도 이름이 보이게). `STATUS`의 `from`/`to`는 상태 코드, `UPDATED`의 `to`는 바뀐 항목 이름(예: "제목, 마감일"), `CREATED`의 `to`는 제목.
- 알림(2026-10-27 확정): `TASK_ASSIGNED`·`TASK_OVERDUE`의 `title`은 **키 + 제목**(`"GW-21 결재선 규칙 해석 API"`), 문장은 화면이 `type`으로 만듭니다(M2 규칙). `link`는 `/projects/{projectId}/board?taskId={taskId}`, `document_id`는 NULL.

---

## 5. 마감 지난 업무 알림 배치

- 매일 09:00 (`Clock` 기준 한국 시각): 마감일이 오늘보다 앞이고 `DONE`이 아닌 업무의 **담당자마다** `TASK_OVERDUE` 알림.
- 같은 업무·같은 담당자·같은 마감일에는 **한 번만**(`task_due_alerts` 유니크). 마감일을 바꾸면 새 마감일 기준으로 다시 한 번.
- M3의 `batch_runs`에 `TASK_OVERDUE` 기록. `app.batches.enabled=false`면 돌지 않음.

---

## 6. 스키마 (Flyway V6)

```sql
ALTER TABLE projects ADD COLUMN project_key VARCHAR(10) NULL, ADD COLUMN last_task_seq INT NOT NULL DEFAULT 0;
-- 기존 프로젝트: project_key = CONCAT('P', id), 그 뒤 NOT NULL + UNIQUE
ALTER TABLE tasks ADD COLUMN task_no INT NULL, ADD COLUMN position BIGINT NOT NULL DEFAULT 0,
                  ADD COLUMN version INT NOT NULL DEFAULT 0, ADD COLUMN created_by BIGINT NULL;
-- 기존 업무: 프로젝트별 id 순으로 task_no = 1..n, (프로젝트, 상태)별 id 순으로 position = 1024 × 순번, last_task_seq = n
-- UNIQUE (project_id, task_no), INDEX (project_id, task_status, position)
CREATE TABLE task_histories (id, task_id, actor_id NULL, action, from_value NULL, to_value NULL, created_at, INDEX(task_id, id));
CREATE TABLE task_due_alerts (id, task_id, user_id, due_date, created_at, UNIQUE(task_id, user_id, due_date));
```
- V1~V5는 고치지 않습니다. 기존 데이터 이관 결과는 마이그레이션 로그에 건수로 남깁니다(M3 V4처럼).
- 홈 '내 업무'·'이번 주 팀'이 쓰는 인덱스는 측정 4의 `EXPLAIN`을 보고 필요한 것만 추가합니다.

---

## 7. 새 에러 코드

| 코드 | 상태 | 메시지 |
|---|---|---|
| `PROJECT_KEY_DUPLICATE` | 409 | 이미 쓰는 프로젝트 키입니다. |
| `PROJECT_MEMBER_HAS_TASKS` | 409 | 담당 중인 미완료 업무가 있어 멤버에서 뺄 수 없습니다. 담당자를 먼저 바꿔 주세요. |
| `TASK_ASSIGNEE_NOT_MEMBER` | 422 | 프로젝트 멤버만 담당자로 지정할 수 있습니다. |
| `TASK_VERSION_CONFLICT` | 409 | 다른 사람이 먼저 이 업무를 옮겼습니다. 보드를 새로 고칩니다. |
| `INVALID_TASK_POSITION` | 422 | 옮길 위치가 올바르지 않습니다. |

---

## 8. 완료 기준 — 재현과 전후 수치 (MariaDB Testcontainers, UTC JVM 포함)

**측정 1~4는 고치기 전 구조(develop의 기존 코드 또는 같은 방식의 비교 구현)로 먼저 재현하고, 같은 조건에서 고친 뒤 수치를 남깁니다.** 기록은 `docs/troubleshooting/M4-*.md`.

1. **프로젝트 상태 쓰기 왜곡**: 업무 2개 남은 프로젝트에서 두 담당자가 **동시에** 완료로 옮김. 기존 구조(잠금 없이 각자 업무 목록을 읽고 계산)에서 "업무는 모두 완료인데 프로젝트는 진행 중"이 N회 중 몇 번인지 → 새 구조 0회. 10회 이상 반복, 실패 횟수와 원인(각 트랜잭션이 상대의 변경을 못 봄)을 기록.
2. **상태 변경 한 번의 비용**: 업무 1,000개 프로젝트에서 상태 변경 1번에 읽는 행 수·쿼리 수·시간. 기존 `updateStatusByTasks`(목록 전체 로딩) → 집계 쿼리.
3. **카드 순서**: (a) 옮길 때마다 열 전체를 다시 번호 매기는 방식과 간격 방식의 이동 1번당 UPDATE 행 수(열에 카드 100개), (b) 같은 열에서 동시 이동 20회 뒤 `position` 중복 수(잠금 없음 vs 프로젝트 행 잠금), (c) 오래된 `version`으로 이동하면 409이고 순서가 그대로인지, (d) 간격이 좁아져 다시 번호를 매긴 횟수.
4. **홈 한 번에**: 기존 홈이 부르는 API 수(화면 기준)와 기존 대시보드 API의 쿼리 수(프로젝트 5개면 1+2×5 + 팀장 지연 로딩) → `GET /home` 1번. 업무·결재·알림 데이터를 10배·100배로 늘려도 `/home` 쿼리 수가 같은지, 중앙값 시간, '내 업무'·'이번 주 팀' 쿼리의 `EXPLAIN`.
5. **권한**: 사원이 멤버가 아닌 프로젝트 보드·상세·이동 → 403, 팀장(DEPT)이 다른 부서 팀장의 프로젝트 → 403, 담당자가 아닌 멤버의 카드 이동 → 403, 프로젝트 팀장은 `PROJECT U`가 없어도 자기 프로젝트 관리 가능.
6. **업무 번호**: 같은 프로젝트에 동시에 업무 10개 추가 → 번호 중복 0, 빈 번호 없음.
7. **마감 알림 배치**: 두 번 돌려도 알림 한 번, 마감일 변경 뒤 다시 한 번, `DONE`은 제외, 실행 기록.
8. **이관**: 기존 프로젝트·업무에 키·번호·위치가 채워지고 보드가 그대로 열림. 기존 대시보드·프로젝트·업무·프로젝트 권한 API는 404.
9. **시간대**: UTC JVM에서도 '이번 주'와 마감 지난 날 수가 한국 날짜 기준.

---

## 9. 화면 ↔ API

| 화면 | 동작 | API |
|---|---|---|
| 홈 | 오늘 근무·결재·연차·이번 주 팀·내 업무·알림 | `GET /home`, 출퇴근은 M3 API |
| 프로젝트 목록 | 범위 안 프로젝트, 등록 | `GET/POST /projects` |
| 프로젝트 보드 | 열·카드, 필터, 빠른 추가, 끌어 옮기기 | `GET /projects/{id}/board`, `POST /projects/{id}/tasks`, `POST /tasks/{id}/move` |
| 업무 상세 (옆 패널) | 상태 변경, 담당자·마감일·설명, 변경 이력 | `GET/PATCH /tasks/{id}`, `POST /tasks/{id}/move` |
| 프로젝트 설정 | 이름·기간·팀장·멤버 | `PATCH /projects/{id}`, `PUT /projects/{id}/members` |
