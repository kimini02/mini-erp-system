# REST API v2 — M1 조직·권한

> 상태: 초안 (2026-10-12)
> 공통 규칙(URL, 메서드, 상태 코드, 응답 형식)은 [03-rest-api.md](03-rest-api.md) 2장을 그대로 따릅니다.
> 테이블은 [10-erd-v2.md](10-erd-v2.md) 1장, 권한 처리 방식은 [11-architecture.md](11-architecture.md) 6장.
> 화면: 시안의 부서 관리 · 사원 관리 · 역할·권한, 그리고 모든 화면의 사이드바.

---

## 0. 기존 API에서 바뀌는 것

| 기존 | 처리 |
|---|---|
| `POST /api/v1/auth/signup` | **삭제**. 사원은 관리자가 등록합니다. |
| `POST /api/v1/auth/login` | 요청은 그대로. 응답에 `mustChangePassword`, `user.department`, `user.roles` 추가. 토큰에 `ver`(token_version) 추가 |
| `PATCH /api/v1/users/{userId}/role` | **삭제** → `PUT /api/v1/users/{userId}/roles` (역할 여러 개) |
| `GET /api/v1/users`, `GET/PUT /api/v1/users/{userId}` | 필터·응답 필드 확장, `PUT` → `PATCH`로 변경 |
| 아이디 찾기, 비밀번호 재설정 | 그대로 |

---

## 1. 공통

### 1.1 권한 표기
API마다 **필요 권한 = 메뉴 코드 + 동작(V 보기 / C 등록 / U 수정 / D 삭제)**, **데이터 범위 적용 여부**를 적습니다.
"로그인만"은 로그인한 사람이면 누구나 쓸 수 있다는 뜻입니다.

### 1.2 메뉴 코드 (`menus` 초기 데이터)

| 코드 | 이름 | 그룹 | 쓰는 동작 | 데이터 범위 |
|---|---|---|---|---|
| `APPROVAL_INBOX` | 결재함 | 전자결재 | V | - |
| `APPROVAL_DRAFT` | 기안하기 | 전자결재 | C | - |
| `APPROVAL_FORM` | 결재 양식 관리 | 전자결재 | V, U | - |
| `MY_ATTENDANCE` | 내 근태 | 근태·휴가 | V, C | - |
| `ATTENDANCE_MONTHLY` | 월간 근태표 | 근태·휴가 | V, U | O |
| `LEAVE_ADMIN` | 연차 관리 | 근태·휴가 | V, C, U | O |
| `DEPARTMENT` | 부서 관리 | 조직 | V, C, U, D | - |
| `EMPLOYEE` | 사원 관리 | 조직 | V, C, U, D | O |
| `ROLE` | 역할·권한 | 조직 | V, C, U, D | - |
| `PROJECT` | 프로젝트 | 업무·시스템 | V, C, U, D | O |
| `HOLIDAY` | 공휴일 | 업무·시스템 | V, C, U, D | - |

### 1.3 기본 역할 (`roles` 초기 데이터, `is_system = true`)

| 코드 | 이름 | 기본 권한 |
|---|---|---|
| `CHIEF` | 관리소장 | 모든 메뉴·모든 동작, 범위 ALL. **권한 수정 불가** |
| `LEADER` | 팀장 | 결재함 V, 기안 C, 내 근태 V·C, 월간 근태표 V(DEPT_TREE), 연차 관리 V(DEPT_TREE), 부서 V, 사원 V(DEPT_TREE), 프로젝트 V·C·U(DEPT), 공휴일 V |
| `EMPLOYEE` | 사원 | 결재함 V, 기안 C, 내 근태 V·C, 프로젝트 V(SELF), 공휴일 V |

### 1.4 토큰
- 클레임: `sub` = 사용자 id, `ver` = `users.token_version`. 역할·권한은 토큰에 넣지 않고 **요청마다 DB에서 조회**합니다. 그래서 권한을 바꾸면 다음 요청부터 바로 반영됩니다.
- `token_version`을 올리는 때: **퇴사·휴직 처리, 비밀번호 변경**. 이전 토큰은 `401 TOKEN_REVOKED`.
- `users.status`가 `ACTIVE`인 사람만 로그인할 수 있습니다.
- 공개 API(`/api/v1/auth/**`: 로그인, 초대 링크 설정, 아이디·비밀번호 찾기)는 `Authorization` 헤더를 보지 않습니다. 만료되거나 무효가 된 토큰이 붙어 와도 401로 막지 않고 그대로 처리합니다. 화면도 이 요청에는 토큰을 붙이지 않습니다. (2026-10-15 추가)

### 1.5 기존 데이터 이관 (M1 Flyway)
- `department_code` '01'/'02'/'03' → 최상위 부서 "회사" 아래 부서 3개
- `user_role` → `user_roles` (USER → EMPLOYEE, TEAM_LEADER → LEADER, ADMIN → CHIEF)
- `position_name` → `positions` (같은 이름은 한 행), `is_active` → `status`
- 기존 `login_id`(예: user01)는 그대로 둡니다. 새 규칙(입사연도+순번)은 새로 등록하는 사원부터 적용합니다.
- `is_active = false` → `RETIRED`. v1에는 비활성으로 바꾸는 기능이 없어 DB를 직접 고친 경우뿐이므로, 이유를 모르는 계정은 가장 닫힌 상태로 두고 필요하면 관리자가 되돌립니다. 이관 건수를 마이그레이션 로그에 남깁니다.
- 입사일은 v1에 없으므로 `hire_date = NULL`로 둡니다(계정 생성일로 추정해 채우지 않음). 새로 등록하는 사원은 필수입니다. 관리자가 사원 수정 화면에서 채웁니다. M3 연차 자동 부여는 입사일이 없는 사원을 건너뛰고 목록으로 남깁니다.
- `menus`, 기본 역할 3개와 `role_permissions`, 회사 기본 `work_schedules`(09:00~18:00, 유예 10분)를 초기 데이터로 넣습니다.
- (2026-10-15 추가) `department_code`가 '01'/'02'/'03'이 아닌 행은 최상위 부서 "회사"로 넣고 건수를 로그에 남깁니다(이관이 중간에 실패하지 않게).
- (2026-10-15 추가) **부서장은 이관하지 않습니다.** v1에는 부서장 개념이 없고 팀장이 여러 명일 수 있어서 추정하지 않습니다. 관리자가 부서 관리 화면에서 지정하기 전까지 M2 결재 상신은 `422 APPROVER_NOT_FOUND`이므로, 운영 전에 부서장 지정이 필요합니다.
- (2026-10-15 추가) MariaDB는 DDL을 되돌리지 못하므로, 이관 도중 실패하면 앞 단계 변경이 남습니다. 실행 전에 DB를 백업합니다.
- 환경변수로 만드는 최초 관리자는 최상위 부서 소속, 관리소장(CHIEF) 역할입니다.

---

## 2. 인증·내 정보

### 2.1 로그인 (변경)
`POST /api/v1/auth/login` · 권한: 없음

```json
// Request (기존과 같음)
{ "id": "2026004", "password": "********" }

// Response 200
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOi...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "mustChangePassword": false,
    "user": {
      "id": 31,
      "loginId": "2026004",
      "name": "신동현",
      "email": "dh.shin@company.com",
      "department": { "id": 6, "name": "개발1팀" },
      "position": "사원",
      "roles": ["EMPLOYEE"]
    }
  }
}
```
- 에러: `401 INVALID_CREDENTIALS` (비밀번호를 아직 설정하지 않은 계정도 같은 메시지 — 계정 존재 여부를 알려 주지 않음), `403 ACCOUNT_INACTIVE`

### 2.2 초대 링크로 첫 비밀번호 설정 (신규)
`POST /api/v1/auth/password/setup` · 권한: 없음
```json
{ "setupToken": "…메일 링크에 담긴 값…", "newPassword": "********" }
```
- 토큰은 기존 비밀번호 재설정과 같은 저장소(Redis, 만료 24시간)를 씁니다. 한 번 쓰면 지워집니다.
- **사원마다 유효한 링크는 하나입니다** (2026-10-15 추가). 초대 메일을 다시 보내거나 그 사원의 이메일이 바뀌면 이전 링크는 바로 무효(`401 SETUP_TOKEN_INVALID`)입니다. 잘못된 주소로 간 링크로 다른 사람이 비밀번호를 정하는 것을 막습니다.
- 에러: `401 SETUP_TOKEN_INVALID`, `400 PASSWORD_POLICY_VIOLATION`

### 2.3 내 비밀번호 변경 (신규)
`PATCH /api/v1/me/password` · 권한: 로그인만
```json
{ "currentPassword": "********", "newPassword": "********" }
```
- 성공하면 `token_version + 1`, 응답으로 **새 토큰**을 줍니다. 다른 기기의 로그인은 끊깁니다.
- `mustChangePassword = true`인 사람(환경변수로 만든 최초 관리자)은 이 API와 `GET /api/v1/me` 말고는 `403 PASSWORD_CHANGE_REQUIRED`.

### 2.4 내 정보 · 내 권한 (신규)
- `GET /api/v1/me` · 로그인만 — 로그인 응답의 `user`와 같은 모양
- `GET /api/v1/me/permissions` · 로그인만 — 사이드바 메뉴를 그릴 때 씁니다(숨기기용, 최종 판단은 서버).
```json
{
  "success": true,
  "data": [
    { "menu": "APPROVAL_INBOX", "actions": ["V"], "scope": null },
    { "menu": "EMPLOYEE", "actions": ["V"], "scope": "DEPT_TREE" }
  ]
}
```
- 역할이 여러 개면 동작은 합치고, 범위는 가장 넓은 것을 씁니다.

---

## 3. 부서

| 메서드 · URL | 설명 | 권한 |
|---|---|---|
| `GET /api/v1/departments/tree` | 조직도 전체 | 로그인만 (조직도는 전 직원 공개) |
| `GET /api/v1/departments/{id}` | 부서 상세 | 로그인만 |
| `POST /api/v1/departments` | 부서 추가 | DEPARTMENT C |
| `PATCH /api/v1/departments/{id}` | 이름·정렬 순서 수정 | DEPARTMENT U |
| `PATCH /api/v1/departments/{id}/parent` | 다른 부서 아래로 이동 | DEPARTMENT U |
| `PUT /api/v1/departments/{id}/head` | 부서장 지정 | DEPARTMENT U |
| `DELETE /api/v1/departments/{id}/head` | 부서장 비우기 | DEPARTMENT U |
| `PUT /api/v1/departments/{id}/work-schedule` | 근무시간 지정 | DEPARTMENT U |
| `DELETE /api/v1/departments/{id}` | 부서 삭제 | DEPARTMENT D |
| `GET /api/v1/work-schedules/default` | 회사 기본 근무시간 | 로그인만 |
| `PUT /api/v1/work-schedules/default` | 회사 기본 근무시간 수정 | DEPARTMENT U |

### 3.1 조직도
`GET /api/v1/departments/tree`
```json
{
  "success": true,
  "data": {
    "id": 1, "name": "WorkFlow 주식회사", "code": "D-0001",
    "head": { "id": 1, "name": "윤재원", "position": "관리소장" },
    "directMemberCount": 0, "totalMemberCount": 42,
    "children": [
      {
        "id": 5, "name": "개발본부", "code": "D-0005",
        "head": { "id": 7, "name": "최민수", "position": "본부장" },
        "directMemberCount": 1, "totalMemberCount": 17,
        "children": [
          { "id": 6, "name": "개발1팀", "head": { "id": 12, "name": "박지훈", "position": "팀장" },
            "directMemberCount": 7, "totalMemberCount": 7, "children": [] },
          { "id": 8, "name": "개발2팀", "head": null,
            "directMemberCount": 3, "totalMemberCount": 3, "children": [] }
        ]
      }
    ]
  }
}
```
- `head: null` = 부서장 공석 (화면의 "부서장 공석" 표시).
- 부서 목록 1번 + 부서별 인원수 1번(GROUP BY)으로 가져와서 서버에서 트리를 조립합니다. 부서마다 쿼리를 따로 날리지 않습니다(N+1 금지).

### 3.2 부서 상세
`GET /api/v1/departments/{id}`
```json
{
  "id": 8, "name": "개발2팀", "code": "D-0008",
  "path": [{ "id": 1, "name": "WorkFlow 주식회사" }, { "id": 5, "name": "개발본부" }],
  "parentId": 5,
  "head": null,
  "fallbackHead": { "id": 7, "name": "최민수", "position": "본부장", "departmentName": "개발본부" },
  "workSchedule": { "useDefault": false, "startTime": "10:00", "endTime": "19:00", "lateGraceMin": 10 },
  "directMemberCount": 3, "childCount": 0
}
```
- `fallbackHead`: 부서장이 비어 있을 때 결재를 대신 받는 가장 가까운 상위 부서장 (화면 안내 문구용).
- 소속 사원 목록은 `GET /api/v1/users?departmentId=8`로 따로 부릅니다.

### 3.3 추가 · 수정 · 이동
- `POST /api/v1/departments` `{ "name": "QA팀", "parentId": 5, "sortOrder": 3 }` → `201`, 부서 코드는 서버가 발급(`D-` + 4자리, 바꿀 수 없음)
- `PATCH /api/v1/departments/{id}` `{ "name": "QA1팀", "sortOrder": 2 }`
- `PATCH /api/v1/departments/{id}/parent` `{ "parentId": 3 }`
  - 자기 자신이나 자기 하위 부서 아래로 옮기면 `422 INVALID_DEPARTMENT_MOVE` (재귀 CTE로 하위 부서를 구해 검사)
  - 최상위 부서는 하나만 둡니다. `parentId: null`로 옮기기는 `422`.

### 3.4 부서장
- `PUT /api/v1/departments/{id}/head` `{ "userId": 12 }`
  - 그 부서 소속이 아니면 `422 HEAD_NOT_IN_DEPARTMENT`, 재직 중이 아니면 `422 ACCOUNT_INACTIVE`
- `DELETE /api/v1/departments/{id}/head` → 공석
- 진행 중인 결재 문서의 결재자는 바뀌지 않습니다(상신 때 스냅샷). 새로 상신하는 문서부터 적용됩니다.
- **잠금 순서** (2026-10-15 추가): 부서장 지정, 사원의 부서 이동, 사원을 휴직·퇴사로 바꾸기는 모두 같은 순서(부서 트리 → 그 사원 행)로 잠그고, 부서장 여부는 잠금 읽기로 확인합니다. 동시에 실행돼도 "부서장은 그 부서 소속의 재직자" 조건이 깨지지 않습니다.

### 3.5 근무시간
- `PUT /api/v1/departments/{id}/work-schedule`
  - 회사 기본 따르기: `{ "useDefault": true }`
  - 따로 정하기: `{ "useDefault": false, "startTime": "10:00", "endTime": "19:00", "lateGraceMin": 10 }`
  - `endTime`이 `startTime`보다 늦어야 함(`400 INVALID_WORK_SCHEDULE`), 유예는 0~60분
- 저장은 바로 반영됩니다. 다만 근태 판정은 **출근하는 순간의 기준**을 기록에 같이 저장하므로, 이미 찍힌 출근 기록의 판정은 바뀌지 않습니다.

### 3.6 삭제
`DELETE /api/v1/departments/{id}` — 소속 사원이나 하위 부서가 있으면 `409 DEPARTMENT_NOT_EMPTY`.

---

## 4. 직급

| 메서드 · URL | 설명 | 권한 |
|---|---|---|
| `GET /api/v1/positions` | 직급 목록 (`rankOrder` 순) | 로그인만 |
| `POST /api/v1/positions` | `{ "name": "선임" }` | DEPARTMENT C |
| `PATCH /api/v1/positions/{id}` | 이름 변경 | DEPARTMENT U |
| `PUT /api/v1/positions/order` | `[{ "id": 1, "rankOrder": 1 }, …]` 순서 한 번에 저장 | DEPARTMENT U |
| `DELETE /api/v1/positions/{id}` | 쓰는 사원이 있으면 `409 POSITION_IN_USE` | DEPARTMENT D |

---

## 5. 사원

| 메서드 · URL | 설명 | 권한 | 범위 |
|---|---|---|---|
| `GET /api/v1/users` | 목록·검색 | EMPLOYEE V | O |
| `GET /api/v1/users/{id}` | 상세 | EMPLOYEE V (본인은 로그인만) | O |
| `GET /api/v1/users/login-id-preview` | 등록 화면의 아이디 미리보기 | EMPLOYEE C | - |
| `POST /api/v1/users` | 등록 + 초대 메일 | EMPLOYEE C (사원 외 역할을 함께 주면 ROLE U도) | - |
| `PATCH /api/v1/users/{id}` | 이름·이메일·부서·직급·입사일 수정 | EMPLOYEE U (사원 외 역할을 가진 사람의 이메일을 바꾸면 ROLE U도) | O |
| `PATCH /api/v1/users/{id}/status` | 재직·휴직·퇴사 | EMPLOYEE U | O |
| `PUT /api/v1/users/{id}/roles` | 역할 지정 (전체 교체) | ROLE U | - |
| `POST /api/v1/users/{id}/invitation` | 초대 메일 다시 보내기 | EMPLOYEE C (사원 외 역할을 가진 사람이면 ROLE U도) | - |

### 5.1 목록
`GET /api/v1/users?q=김&departmentId=6&includeSubDepartments=true&status=ACTIVE&page=0&size=20&sort=userName,asc`
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 31, "loginId": "2026003", "name": "신동현", "email": "dh.shin@company.com",
        "department": { "id": 6, "name": "개발1팀" }, "position": "사원",
        "roles": [{ "code": "EMPLOYEE", "name": "사원" }],
        "hireDate": "2026-10-05", "status": "ACTIVE",
        "invitationPending": true
      }
    ],
    "page": { "number": 0, "size": 20, "totalElements": 1, "totalPages": 1, "first": true, "last": true, "numberOfElements": 1 },
    "statusCounts": { "ACTIVE": 38, "ON_LEAVE": 2, "RETIRED": 4 }
  }
}
```
- `q`: 이름 또는 아이디 **앞부분 일치**(`LIKE 'q%'`)라서 인덱스를 탑니다. 중간 글자 검색(`%q%`)은 지원하지 않습니다.
- `statusCounts`: 화면의 재직·휴직·퇴사 탭 숫자. `status`를 뺀 같은 조건으로 GROUP BY 한 번.
- `invitationPending`: 아직 비밀번호를 설정하지 않음 (화면의 "첫 로그인 전").
- **데이터 범위**: 팀장(DEPT_TREE)은 자기 부서와 하위 부서 사원만 나옵니다. 범위 밖 `departmentId`를 넘기면 빈 목록이 아니라 `403 ACCESS_DENIED`.

### 5.2 아이디 미리보기
`GET /api/v1/users/login-id-preview?hireDate=2026-10-19` → `{ "loginId": "2026004" }`
- **미리보기일 뿐**입니다. 다른 관리자가 동시에 등록하면 실제 번호는 달라질 수 있고, 진짜 번호는 등록할 때 잠금을 걸고 확정합니다.

### 5.3 등록
`POST /api/v1/users`
```json
// Request
{
  "name": "홍길동", "email": "gd.hong@company.com", "hireDate": "2026-10-19",
  "departmentId": 6, "positionId": 1, "roleCodes": ["EMPLOYEE"]
}

// Response 201
{ "success": true, "data": { "id": 45, "loginId": "2026004", "invitationSent": true } }
```
- 처리 순서(한 트랜잭션): `login_id_sequences`의 입사연도 행을 `SELECT … FOR UPDATE` → 번호 +1 → 사용자 저장(비밀번호는 비워 둠) → 역할 저장. 행이 없으면 새로 만듭니다.
- 초대 메일은 **커밋 뒤**에 보냅니다. 메일이 꺼져 있거나 실패하면 `invitationSent: false`, 사원은 그대로 등록됩니다(나중에 다시 보내기).
- `users.user_pw`는 NULL을 허용하도록 바꿉니다(초대 전 계정).
- **권한 상승 방지** (2026-10-12 추가): `roleCodes`에 `EMPLOYEE` 말고 다른 역할이 하나라도 있으면 `ROLE U` 권한도 있어야 합니다. 없으면 `403 ACCESS_DENIED`. 사원 관리만 맡은 인사 담당(EMPLOYEE C)이 관리소장 역할을 붙여 계정을 만드는 것을 막습니다. 화면은 역할 권한이 없으면 역할 선택을 숨기고 `["EMPLOYEE"]`로 보냅니다.
- 에러: `409 EMAIL_ALREADY_EXISTS`, `404 DEPARTMENT_NOT_FOUND`, `404 POSITION_NOT_FOUND`, `404 ROLE_NOT_FOUND`, `422 ROLE_REQUIRED`(역할 0개), `403 ACCESS_DENIED`(위 권한 상승)

### 5.4 수정 · 상태 · 역할
- `PATCH /api/v1/users/{id}` `{ "departmentId": 8, "positionId": 3, "hireDate": "2021-06-07" }`
  - `hireDate`는 v1에서 이관한 사원처럼 입사일이 없는(NULL) 사람에게 나중에 채워 넣는 용도입니다. 아이디는 바뀌지 않습니다. (2026-10-15 추가)
  - 부서장인 사람을 다른 부서로 옮기면 `422 HEAD_MUST_BE_REASSIGNED` (먼저 부서장을 바꾸기)
  - **이메일 변경의 권한 상승 방지** (2026-10-15 추가): 대상이 `EMPLOYEE` 말고 다른 역할을 하나라도 가지면 `ROLE U`도 있어야 합니다. 없으면 `403 ACCESS_DENIED`이고 아무것도 바뀌지 않습니다. 비밀번호 재설정과 초대 링크가 이메일로 가기 때문에, 사원 관리 권한만으로 관리소장의 이메일을 자기 주소로 바꾸면 그 계정을 가져갈 수 있습니다. 화면은 이 경우 이메일 칸을 잠급니다.
  - 이메일이 바뀌면 그 사원의 초대 링크는 무효가 됩니다(2.2).
- `PATCH /api/v1/users/{id}/status` `{ "status": "RETIRED" }`
  - `ON_LEAVE`·`RETIRED`로 바꾸면 `token_version + 1` → 그 사람의 기존 토큰은 바로 `401 TOKEN_REVOKED`
  - 부서장이면 `422 HEAD_MUST_BE_REASSIGNED`, 마지막 관리소장이면 `409 LAST_CHIEF`
- `PUT /api/v1/users/{id}/roles` `{ "roleCodes": ["EMPLOYEE", "HR_MANAGER"] }`
  - 0개면 `422 ROLE_REQUIRED`, 마지막 관리소장의 CHIEF를 빼면 `409 LAST_CHIEF`
- `POST /api/v1/users/{id}/invitation` — 이미 비밀번호를 설정했으면 `409 INVITATION_ALREADY_ACCEPTED`
  - 대상이 사원 외 역할을 가지면 `ROLE U`도 필요(위 이메일 변경과 같은 이유). 새 링크를 보내면 이전 링크는 무효.

---

## 6. 역할·권한

| 메서드 · URL | 설명 | 권한 |
|---|---|---|
| `GET /api/v1/menus` | 권한표의 행 목록 (1.2의 표) | ROLE V |
| `GET /api/v1/roles` | 역할 목록 + 인원수 | ROLE V |
| `POST /api/v1/roles` | 역할 만들기 `{ "name": "인사 담당", "description": "…" }` → 코드는 서버 발급 | ROLE C |
| `PATCH /api/v1/roles/{id}` | 이름·설명 수정 | ROLE U |
| `DELETE /api/v1/roles/{id}` | 역할 삭제 | ROLE D |
| `GET /api/v1/roles/{id}/permissions` | 권한표 | ROLE V |
| `PUT /api/v1/roles/{id}/permissions` | 권한표 한 번에 저장 | ROLE U |
| `GET /api/v1/roles/{id}/members` | 이 역할을 가진 사원 (페이지) | ROLE V |
| `POST /api/v1/roles/{id}/members` | `{ "userIds": [12, 13] }` 추가 | ROLE U |
| `DELETE /api/v1/roles/{id}/members/{userId}` | 한 명 빼기 | ROLE U |

### 6.1 권한표 조회
`GET /api/v1/roles/3/permissions`
```json
{
  "success": true,
  "data": {
    "role": { "id": 3, "code": "LEADER", "name": "팀장", "system": true, "locked": false },
    "permissions": [
      { "menu": "APPROVAL_INBOX", "view": true, "create": false, "update": false, "delete": false, "scope": null },
      { "menu": "EMPLOYEE", "view": true, "create": false, "update": false, "delete": false, "scope": "DEPT_TREE" }
    ]
  }
}
```
- 모든 메뉴를 다 내려줍니다(권한이 없는 메뉴는 전부 false).
- `locked: true` = 관리소장(CHIEF). 화면에서 체크박스를 잠급니다.

### 6.2 권한표 저장
`PUT /api/v1/roles/{id}/permissions` — 본문은 6.1의 `permissions` 배열. **한 트랜잭션에서 전체 교체**합니다.
- 그 메뉴에 없는 동작을 켜면(예: 결재함 C) `400 INVALID_PERMISSION`
- 범위가 없는 메뉴에 `scope`를 넣거나, 범위가 있는 메뉴에서 동작을 켜고 `scope`를 비우면 `400 INVALID_PERMISSION`
- 관리소장이면 `403 SYSTEM_ROLE_LOCKED`
- 권한은 요청마다 조회하므로 저장하는 즉시 그 역할의 사원 모두에게 반영됩니다.

### 6.3 삭제 규칙
- 기본 역할(CHIEF, LEADER, EMPLOYEE)은 `403 SYSTEM_ROLE_LOCKED`
- 사원이 있거나 결재 양식 규칙이 쓰고 있으면 `409 ROLE_IN_USE`

---

## 7. 공휴일

ERD 3장의 `holidays` 테이블을 M1에서 먼저 만듭니다(휴가 일수 계산과 근태 판정이 둘 다 씀).

| 메서드 · URL | 설명 | 권한 |
|---|---|---|
| `GET /api/v1/holidays?year=2026` | 그 해 공휴일 | 로그인만 |
| `POST /api/v1/holidays` | `{ "date": "2026-10-05", "name": "대체공휴일", "substitute": true }` | HOLIDAY C |
| `PATCH /api/v1/holidays/{id}` | 이름·대체 여부 | HOLIDAY U |
| `DELETE /api/v1/holidays/{id}` | 삭제 | HOLIDAY D |

- 같은 날짜가 있으면 `409 HOLIDAY_ALREADY_EXISTS`

---

## 8. 새 에러 코드

| 코드 | 상태 | 메시지 |
|---|---|---|
| `TOKEN_REVOKED` | 401 | 로그인이 만료되었습니다. 다시 로그인해 주세요. |
| `SETUP_TOKEN_INVALID` | 401 | 비밀번호 설정 링크가 만료되었거나 이미 사용되었습니다. |
| `ACCOUNT_INACTIVE` | 403 | 재직 중인 계정이 아닙니다. |
| `PASSWORD_CHANGE_REQUIRED` | 403 | 비밀번호를 먼저 변경해 주세요. |
| `SYSTEM_ROLE_LOCKED` | 403 | 기본 역할은 바꾸거나 지울 수 없습니다. |
| `DEPARTMENT_NOT_FOUND` | 404 | 부서를 찾을 수 없습니다. |
| `POSITION_NOT_FOUND` | 404 | 직급을 찾을 수 없습니다. |
| `ROLE_NOT_FOUND` | 404 | 역할을 찾을 수 없습니다. |
| `DEPARTMENT_NOT_EMPTY` | 409 | 소속 사원이나 하위 부서가 있어 삭제할 수 없습니다. |
| `POSITION_IN_USE` | 409 | 이 직급을 쓰는 사원이 있습니다. |
| `ROLE_IN_USE` | 409 | 이 역할을 쓰는 사원이나 결재 규칙이 있습니다. |
| `LAST_CHIEF` | 409 | 관리소장이 한 명 이상 있어야 합니다. |
| `INVITATION_ALREADY_ACCEPTED` | 409 | 이미 비밀번호를 설정한 사원입니다. |
| `HOLIDAY_ALREADY_EXISTS` | 409 | 이미 등록된 날짜입니다. |
| `INVALID_PERMISSION` | 400 | 이 메뉴에서 쓸 수 없는 권한입니다. |
| `INVALID_WORK_SCHEDULE` | 400 | 퇴근 시각은 출근 시각보다 늦어야 합니다. |
| `INVALID_DEPARTMENT_MOVE` | 422 | 자기 자신이나 하위 부서 아래로 옮길 수 없습니다. |
| `HEAD_NOT_IN_DEPARTMENT` | 422 | 그 부서 소속 사원만 부서장이 될 수 있습니다. |
| `HEAD_MUST_BE_REASSIGNED` | 422 | 부서장을 먼저 다른 사람으로 바꿔 주세요. |
| `ROLE_REQUIRED` | 422 | 역할이 하나 이상 있어야 합니다. |

---

## 9. 완료 기준 — 꼭 테스트로 확인할 것

MariaDB Testcontainers 환경(M0)에서 돌립니다.

1. **아이디 동시 발급**: 같은 입사연도로 두 스레드가 동시에 등록해도 아이디가 겹치지 않는다. (잠금 없이 `MAX+1`로 만들면 실패하는 것도 함께 보여 주면 좋음)
2. **부서 순환 이동 차단**: 개발본부를 자기 하위인 개발1팀 아래로 옮기면 422.
3. **데이터 범위**: 팀장은 하위 부서 포함 사원만 보이고, 범위 밖 부서를 지정하면 403. 사원은 사원 목록 403.
4. **즉시 차단**: 퇴사 처리 직후, 그 사람이 쓰던 토큰으로 요청하면 401 `TOKEN_REVOKED`.
5. **마지막 관리소장 보호**: 관리소장이 한 명일 때 퇴사·역할 제거가 409.
6. **권한 즉시 반영**: 팀장 역할에서 사원 관리 V를 끄면, 같은 토큰으로 다음 요청부터 403.
7. **조직도 쿼리 수**: 조직도 조회가 부서 수와 상관없이 쿼리 2번(부서 목록 + 인원수)으로 끝난다.
8. **권한 상승 차단** (2026-10-15 추가): 사원 관리 권한만 가진 사람이 관리 역할을 붙여 등록하거나, 관리소장의 이메일을 바꾸거나, 관리 역할 사원에게 초대 메일을 다시 보내면 403이고 데이터·메일·토큰이 그대로다.
9. **이전 초대 링크 무효** (2026-10-15 추가): 이메일을 바꾸거나 다시 보낸 뒤에는 이전 링크로 비밀번호를 설정할 수 없다.

---

## 10. 화면 ↔ API (시안 기준)

| 화면 | 동작 | API |
|---|---|---|
| 모든 화면 | 사이드바 메뉴 | `GET /me/permissions` |
| 부서 관리 | 조직도 | `GET /departments/tree` |
| | 부서 선택 | `GET /departments/{id}` + `GET /users?departmentId={id}` |
| | 이름·상위 부서 저장 | `PATCH /departments/{id}`, `PATCH /departments/{id}/parent` |
| | 부서장 변경·지정 | `PUT /departments/{id}/head` |
| | 근무시간 | `PUT /departments/{id}/work-schedule` |
| | 하위 부서 추가 · 삭제 | `POST /departments` · `DELETE /departments/{id}` |
| 사원 관리 | 목록·검색·탭 숫자 | `GET /users` (`statusCounts`) |
| | 등록 모달의 아이디 | `GET /users/login-id-preview` |
| | 등록 | `POST /users` |
| | 수정 | `PATCH /users/{id}`, `PATCH /users/{id}/status`, `PUT /users/{id}/roles` |
| 역할·권한 | 역할 목록 | `GET /roles` |
| | 권한표 | `GET /menus` + `GET /roles/{id}/permissions` |
| | 저장 | `PUT /roles/{id}/permissions` |
| | 사원 탭 | `GET /roles/{id}/members` |
| 초대 메일 링크 | 첫 비밀번호 | `POST /auth/password/setup` |

※ 시안의 부서 근무시간 문구 "다음 날 출근부터 적용"은 3.5에 맞춰 "이미 찍힌 출근 기록의 판정은 바뀌지 않습니다"로 고칩니다.
