# REST API v2 — M3 근태·휴가

> 상태: 초안 (2026-10-24)
> 공통 규칙은 [03-rest-api.md](03-rest-api.md) 2장, 권한 표기·메뉴 코드는 [12-rest-api-v2-m1.md](12-rest-api-v2-m1.md) 1장, 결재 구조는 [13-rest-api-v2-m2.md](13-rest-api-v2-m2.md)를 따릅니다.
> 테이블은 [10-erd-v2.md](10-erd-v2.md) 3장. 시각은 모두 **한국 시각, 시간대 표시 없음**(`2026-10-12T09:03:12`), 날짜는 `2026-10-12`.
> 화면: 시안의 월간 근태표, 홈의 '오늘 근무' 위젯, 내 근태, 연차 관리(새로), 기안하기의 근태 정정 양식.

---

## 0. 이번 단계에서 정한 것

| 결정 | 이유 |
|---|---|
| **출퇴근 시각은 서버 시계로만** 기록. 요청 본문에 날짜·시각을 받지 않음 (KI-04) | 9시 20분에 출근하고 `"clockInTime": "08:50"`을 보내면 정상 출근이 되던 문제. 근태는 급여·평가 근거라 본인이 값을 정하면 안 됨 |
| **본인이 근태를 직접 고치는 API는 없음.** 정정은 결재 양식 **근태 정정(`ATTENDANCE_FIX`, 접두어 `AT`)** 으로만 | M2의 양식 처리기 구조에 양식 하나를 더 꽂아 해결. 누가·왜·누구 승인으로 바뀌었는지가 문서로 남음 |
| **판정 기준을 출근 기록에 함께 저장**(스냅샷) | 부서 근무시간이 나중에 바뀌어도 지난 판정이 바뀌지 않음 (ERD 3장) |
| 결근·퇴근 누락은 **다음 날 새벽 배치**가 확정 | 출근하지 않은 사실은 그날이 끝나야 알 수 있음 |
| 연차 부여는 **회계연도(1월 1일) 기준** + 입사 첫해 월 단위 | 아래 4장. 회사 규정으로 가정한 규칙이며, 실제 법정 계산과 다를 수 있는 부분은 문서로 밝힘 |
| 배치는 `@Scheduled` + `Clock`, **두 번 돌려도 결과가 같게**(유니크 제약) | 서버 한 대 기준. 새 라이브러리(분산 잠금 등)는 쓰지 않음 |
| 기존 근태 API(`/api/v1/attendance` check-in·check-out·PUT·summary)는 **삭제** | 새 API로 대체 |
| KI-07: 기존 `GET /api/v1/dashboard/admin-summary`는 **관리소장만** | 팀장이 다른 부서 휴가 대기를 보던 경로. 대시보드는 M4에서 다시 만듦 |

---

## 1. 판정 규칙

### 1.1 근무일
- **근무일** = 주말(토·일)이 아니고 `holidays`에 없는 날.
- `day_type`
  - `WORKDAY`: 근무일
  - `HOLIDAY`: 주말·공휴일 (출근하면 기록은 남기되 지각·조퇴 판정 없음 = 휴일 근무)
  - `LEAVE`: 승인된 종일 휴가(연차·병가·경조)
  - `HALF_AM` / `HALF_PM`: 승인된 오전·오후 반차

### 1.2 기준 근무시간 (스냅샷)
- 출근하는 순간, 그 사원 **부서의 근무시간**(없으면 회사 기본)을 기록에 복사: `sched_start`, `sched_end`, `sched_grace_min`.
- **반차 경계** = `sched_start + 5시간` (09:00 → 14:00, 점심 1시간 포함 4시간 근무).
  - 오전 반차: 출근 기준이 반차 경계(14:00), 퇴근 기준은 `sched_end`
  - 오후 반차: 출근 기준은 `sched_start`, 퇴근 기준이 반차 경계(14:00)

### 1.3 출근 판정 `in_result`
- `ON_TIME`: 출근 시각 ≤ 출근 기준 + 유예(분) — **경계 포함**(09:10:59까지 정상, 09:11:00부터 지각; 초는 버리고 분까지 비교)
- `LATE`: 그 이후
- `NONE`: 휴일·종일 휴가

### 1.4 퇴근 판정 `out_result`
- `ON_TIME`: 퇴근 시각 ≥ 퇴근 기준
- `EARLY`: 그 이전 (조퇴)
- `MISSING`: 출근만 하고 퇴근을 안 찍음 (다음 날 배치가 확정)
- `NONE`: 휴일·종일 휴가

### 1.5 결근 `absent`
- 근무일에 출근 기록이 없고 승인된 종일 휴가도 없으면, 다음 날 배치가 `absent = true` 행을 만듭니다.
- 반차인 날 출근 기록이 없으면 결근(반차는 반나절만 쉼).

### 1.6 휴가와의 관계
- 출근할 때 그날의 **승인된 휴가**를 보고 `day_type`을 정합니다.
- 출근 뒤에 반차가 승인되는 경우가 있으므로, **다음 날 배치가 전날 기록의 `day_type`을 승인된 휴가로 다시 확정하고 판정을 다시 계산**합니다(조퇴 → 정상 등).
- 승인된 종일 휴가인 날 출근하면 `409 ATTENDANCE_ON_LEAVE` ("오늘은 휴가입니다. 휴가를 회수한 뒤 출근하세요.").

---

## 2. 내 근태

| 메서드 · URL | 설명 | 권한 |
|---|---|---|
| `GET /api/v1/attendance/today` | 오늘 내 근무 (홈 위젯) | MY_ATTENDANCE V |
| `POST /api/v1/attendance/clock-in` | 출근 | MY_ATTENDANCE C |
| `POST /api/v1/attendance/clock-out` | 퇴근 | MY_ATTENDANCE C |
| `GET /api/v1/attendance/me?month=2026-10` | 내 한 달 기록 | MY_ATTENDANCE V |

### 2.1 오늘 / 출근 / 퇴근
요청 본문 없음. 세 API 모두 같은 모양을 돌려줍니다.
```json
{
  "workDate": "2026-10-12", "dayType": "WORKDAY", "holidayName": null,
  "schedule": { "startTime": "09:00", "endTime": "18:00", "lateGraceMin": 10, "halfBoundary": "14:00", "source": "DEPARTMENT", "departmentName": "개발1팀" },
  "clockInAt": "2026-10-12T09:03:12", "clockOutAt": null,
  "inResult": "ON_TIME", "outResult": null,
  "leave": null,
  "canClockIn": false, "canClockOut": true,
  "earlyIfNow": true,
  "monthLateCount": 1
}
```
- `schedule`: 출근 전이면 지금 기준(부서·회사), 출근 뒤에는 **기록에 저장된 스냅샷**. `source`: `DEPARTMENT` / `COMPANY`
- `leave`: 오늘 승인된 휴가 `{ "type": "HALF_PM", "documentId": 142, "docNo": "HR-2026-0142" }`
- `earlyIfNow`: 지금 퇴근하면 조퇴로 기록되는지 (화면의 "그래도 퇴근" 확인용)
- 출근: 같은 날 두 번이면 `409 ALREADY_CLOCKED_IN`. 동시에 두 번 눌러도 기록은 하나(`UK(user_id, work_date)`)
- 퇴근: 출근 기록이 없으면 `409 NOT_CLOCKED_IN`. 여러 번 누르면 **마지막 시각**으로 바뀌고 판정도 다시 계산
- 날짜가 바뀌는 야간 근무(자정 넘김)는 1차 범위 밖 — 출근한 날의 기록에 퇴근 시각이 다음 날로 찍히는 것만 허용

### 2.2 내 한 달
`GET /api/v1/attendance/me?month=2026-10`
```json
{
  "month": "2026-10",
  "summary": { "workDays": 21, "present": 15, "late": 1, "early": 0, "absent": 0, "missing": 0, "leaveDays": 2.5 },
  "days": [
    { "date": "2026-10-01", "dayType": "WORKDAY", "clockInAt": "2026-10-01T08:55:40", "clockOutAt": "2026-10-01T18:04:11", "inResult": "ON_TIME", "outResult": "ON_TIME", "absent": false, "fixDocument": null }
  ]
}
```
- 그 달의 모든 날짜를 돌려줍니다(기록이 없는 미래 날짜는 `dayType`만).
- `fixDocument`: 근태 정정으로 바뀐 날이면 `{ "id", "docNo" }`

---

## 3. 월간 근태표 (관리)

| 메서드 · URL | 설명 | 권한 | 범위 |
|---|---|---|---|
| `GET /api/v1/attendance/monthly?month=2026-10&departmentId=6&includeSubDepartments=true` | 사원 × 날짜 표 | ATTENDANCE_MONTHLY V | O |
| `GET /api/v1/attendance/records/{userId}/{date}` | 한 칸의 기록과 판정 근거 | ATTENDANCE_MONTHLY V | O |
| `GET /api/v1/attendance/monthly/export?month=…&departmentId=…` | CSV 내려받기 (UTF-8 BOM, 엑셀에서 바로 열림) | ATTENDANCE_MONTHLY V | O |
| `POST /api/v1/attendance/rejudge` `{ "date": "2026-10-05" }` | 그 날짜 다시 판정 (공휴일을 늦게 등록한 경우 등) | ATTENDANCE_MONTHLY U | 범위 ALL만 |

### 3.1 표
```json
{
  "month": "2026-10",
  "days": [{ "date": "2026-10-01", "weekday": "THU", "dayType": "WORKDAY", "holidayName": null }],
  "rows": [
    {
      "user": { "id": 12, "name": "김서연", "title": "사원", "department": "개발1팀" },
      "cells": [{ "date": "2026-10-01", "code": "ON_TIME" }, { "date": "2026-10-02", "code": "LATE" }],
      "totals": { "late": 1, "early": 0, "absent": 0, "missing": 0, "leaveDays": 2.0 }
    }
  ],
  "legend": ["ON_TIME", "LATE", "EARLY", "MISSING", "ABSENT", "LEAVE", "HALF_AM", "HALF_PM", "HOLIDAY", "HOLIDAY_WORK", "NONE", "FUTURE"]
}
```
- 셀 `code`는 서버가 하나로 정해 줍니다(화면은 색만 칠함). 우선순위: `ABSENT` > `LEAVE` > `MISSING` > `LATE`(+조퇴면 `LATE` 유지, 상세에서 둘 다) > `EARLY` > `HALF_*` > `ON_TIME` > `HOLIDAY_WORK` > `HOLIDAY` > `FUTURE` > `NONE`
- **쿼리 하나**: 날짜 목록(재귀 CTE `WITH RECURSIVE`) × 대상 사원 `LEFT JOIN attendances`. `EXPLAIN`에서 `UK(user_id, work_date)`를 타야 합니다.
- 데이터 범위: 팀장(DEPT_TREE)은 자기 부서와 하위 부서만. 범위 밖 `departmentId`는 `403 ACCESS_DENIED`. 퇴사자는 그 달에 재직한 날이 있을 때만 포함.

### 3.2 칸 상세
```json
{
  "user": { "id": 12, "name": "김서연" }, "date": "2026-10-02", "code": "LATE",
  "clockInAt": "2026-10-02T09:14:02", "clockOutAt": "2026-10-02T18:01:30",
  "schedule": { "startTime": "09:00", "endTime": "18:00", "lateGraceMin": 10, "halfBoundary": "14:00" },
  "reason": "출근 09:14 > 기준 09:00 + 유예 10분",
  "leaveDocument": null, "fixDocument": null, "judgedAt": "2026-10-03T00:10:02"
}
```
- `reason`: 판정 근거 한 줄(시안의 "판정 근거"). 서버가 만들어 줍니다.

---

## 4. 연차 자동 부여 · 연차 관리

### 4.1 부여 규칙 (회사 규정으로 가정)
| 대상 | 언제 | 얼마 | `grant_key` |
|---|---|---|---|
| 근속 1년 미만 | 입사일로부터 k개월이 되는 날 (k = 1~11). 그달에 같은 날이 없으면 그달 마지막 날 (1/31 입사 → 2/28) | 1일 | `M-{userId}-{k}` |
| 입사 1주년 | 1주년이 되는 날 | 15일 × (그날부터 그해 말까지 날 수 / 그해 날 수), **0.5일 단위로 올림** (1월 1일이 1주년이면 15일) | `A-{userId}` |
| 근속 1년 이상 | 매년 1월 1일 | 15 + ⌊(근속연수 − 1) / 2⌋, **최대 25일**. 근속연수 = 그해 1월 1일 기준 만 연수. **1주년이 그해 1월 1일 이후(같은 날 포함)인 사원은 제외** — 그해 몫은 1주년 비례 부여로 받음 | `Y-{userId}-{year}` |

- 대상: 상태가 `ACTIVE`·`ON_LEAVE`이고 **입사일이 있는** 사원. 입사일이 없는 사원은 건너뛰고 건수·목록을 남깁니다(4.3).
- 부여 연도(`leave_year`) = 부여한 날의 연도. **전년도 남은 연차는 넘어오지 않습니다**(연도별 원장이라 따로 소멸 행은 쓰지 않음).
- `grant_key` 유니크 제약으로 **두 번 실행해도 한 번만** 부여됩니다.
- 배치: 매일 00:30 (`Clock` 기준 한국 시각). 하루를 건너뛰었어도 다음 실행에서 놓친 날을 채우도록, 실행 시점까지 받아야 할 부여를 모두 계산합니다(키가 있으면 건너뜀).
- 실제 근로기준법의 회계연도 기준 정산(입사 다음 해 비례 부여 등)과 다를 수 있습니다. 1차는 위 규칙으로 고정하고 다르게 하려면 규칙 표만 바꿉니다.

### 4.2 결근 배치
- 매일 00:10, **전날**에 대해:
  1. 근무일이면, 재직 사원 중 기록이 없는 사람 → 승인된 종일 휴가가 있으면 `day_type = LEAVE` 행, 없으면 `absent = true` 행
  2. 전날 기록 중 출근만 있는 행 → `out_result = MISSING`
  3. 전날 기록의 `day_type`을 승인된 휴가로 다시 확정하고 판정을 다시 계산
- 입사일 이전·퇴사 이후 날짜는 만들지 않습니다(입사일이 없으면 계정 생성일 이후).
- 두 번 실행해도 같은 결과(`UK(user_id, work_date)`, 이미 확정된 값은 같은 값으로 덮음).

### 4.3 배치 실행 기록
- `batch_runs`(id, job_name, target_date, status `SUCCESS`/`FAILED`, processed_count, skipped_count, message, started_at, finished_at)
- 연차 부여의 건너뛴 사원(입사일 없음)은 message에 사원 id 목록을 남기고, 연차 관리 화면에서 `hireDateMissing`으로 보여 줍니다.

### 4.4 연차 관리 API

| 메서드 · URL | 설명 | 권한 | 범위 |
|---|---|---|---|
| `GET /api/v1/leave/admin/balances?year=2026&departmentId=&q=&page=&size=` | 사원별 부여·사용·예약·사용 가능 | LEAVE_ADMIN V | O |
| `GET /api/v1/leave/admin/ledger/{userId}?year=2026` | 한 사원의 원장 | LEAVE_ADMIN V | O |
| `POST /api/v1/leave/admin/adjustments` `{ "userId": 12, "year": 2026, "days": -1.0, "memo": "근태 정정 반영" }` | 수동 조정 (`ADJUST` 행) | LEAVE_ADMIN C | O |
| `GET /api/v1/leave/admin/grant-runs?limit=10` | 최근 부여 배치 기록 | LEAVE_ADMIN U | 범위 ALL만 |
| `POST /api/v1/leave/admin/grant-runs` `{ "date": "2026-10-24" }` | 그 날짜 기준 부여를 지금 실행 (배치와 같은 코드) | LEAVE_ADMIN U | 범위 ALL만 |

- 잔액 행: `{ "user": {…}, "hireDate": "2021-01-04", "hireDateMissing": false, "granted": 15.0, "used": 4.0, "reserved": 2.0, "adjusted": 0.0, "available": 9.0 }`
- 조정: `days`는 0이 아니고 ±0.5 단위, `memo` 필수. 결과로 사용 가능이 음수가 되면 `422 LEAVE_BALANCE_NEGATIVE`. 같은 사람 행을 잠그고 계산(상신과 같은 잠금).
- 조정 행은 `created_by`에 처리한 사람을 남깁니다.

---

## 5. 근태 정정 결재 양식 (`ATTENDANCE_FIX`)

- M2의 `ApprovalFormHandler`를 하나 더 구현합니다. 결재 화면·결재함·알림은 그대로 씁니다.
- 기본 결재선: 부서장(`DEPT_HEAD`), 옵션은 휴가와 같음(`fallback`·`skip_self`·`dedupe` = true, `allow_line_edit` = false). 문서번호 `AT-2026-0001`.
- 본문: `{ "workDate": "2026-10-02", "clockIn": "09:02", "clockOut": "18:10", "reason": "출입카드 오류로 출근을 늦게 찍음" }`
  - `clockIn`·`clockOut` 중 하나 이상, `HH:mm`. 둘 다 있으면 퇴근 > 출근. `reason` 필수.
- 상신 검사(`validate`): `workDate`가 **오늘보다 이전**이고 **최근 30일 이내**, 근무일이거나 그날 기록이 있음, 같은 날짜로 진행 중인 정정 문서가 없음(`409 ATTENDANCE_FIX_DUPLICATE`)
- 승인(`onApproved`, 같은 트랜잭션): 그날 기록을 정정값으로 바꾸고(기록이 없으면 그날 기준 근무시간 스냅샷으로 새로 만듦) **판정을 다시 계산**, `fix_document_id`에 근거 문서를 남김. 결근이었으면 `absent = false`.
- 반려·회수: 근태 기록은 그대로.
- 정정 전 값은 문서 본문에 `before`로 함께 저장해서 상세 화면이 "09:14 → 09:02"처럼 보여 줍니다: `{ "before": { "clockIn": "09:14", "clockOut": "18:01", "code": "LATE" } }` (상신 때 서버가 채움)

---

## 6. 스키마 (Flyway V4)

- `attendances` 재구성 (ERD 3장)
  - 추가: `clock_in_at`, `clock_out_at`(DATETIME), `sched_start`, `sched_end`(TIME), `sched_grace_min`, `day_type`, `in_result`, `out_result`, `absent`, `judged_at`, `fix_document_id`(FK approval_documents)
  - 이관: `work_date + clock_in_time` → `clock_in_at` (퇴근도 같음). 기준 근무시간은 **회사 기본 근무시간**으로 채우고 판정을 다시 계산. v1 `att_status`의 `ABSENT`→`absent`, `LEAVE`→`day_type = LEAVE`. 이관 건수를 로그로 남김
  - 삭제: `clock_in_time`, `clock_out_time`, `att_status`
  - 유지: `UK(user_id, work_date)`
- `approval_forms`에 `ATTENDANCE_FIX` 추가 + 기본 단계, `attendance_fix_requests`(document_id PK·FK, work_date, clock_in TIME NULL, clock_out TIME NULL, reason, before_json)
- `batch_runs`
- `leave_ledger.entry_type`에 `ADJUST` 사용 시작 (컬럼 그대로)

---

## 7. 새 에러 코드

| 코드 | 상태 | 메시지 |
|---|---|---|
| `ALREADY_CLOCKED_IN` | 409 | 오늘은 이미 출근했습니다. |
| `NOT_CLOCKED_IN` | 409 | 출근 기록이 없습니다. 출근부터 해 주세요. |
| `ATTENDANCE_ON_LEAVE` | 409 | 오늘은 휴가입니다. 휴가를 회수한 뒤 출근하세요. |
| `ATTENDANCE_FIX_OUT_OF_RANGE` | 422 | 최근 30일 안의 지난 날짜만 정정할 수 있습니다. |
| `ATTENDANCE_FIX_DUPLICATE` | 409 | 같은 날짜로 진행 중인 정정 문서가 있습니다. |
| `INVALID_ATTENDANCE_TIME` | 400 | 퇴근 시각은 출근 시각보다 늦어야 합니다. |
| `LEAVE_BALANCE_NEGATIVE` | 422 | 조정하면 사용 가능 연차가 0보다 작아집니다. |

---

## 8. 완료 기준 — 꼭 테스트로 확인할 것 (MariaDB Testcontainers, UTC JVM)

1. **시각 조작 불가 (KI-04)**: 출근 요청 본문에 시각·날짜를 넣어도 무시되고 서버 시각으로 기록. 기존 본인 수정 API는 없음(404/405). **고치기 전 구조로 조작이 되는 것을 먼저 재현**하고 전후를 기록.
2. **판정 경계**: 09:10:59 정상 / 09:11:00 지각, 17:59 퇴근 조퇴 / 18:00 정상, 오전·오후 반차 경계(14:00).
3. **스냅샷**: 출근 뒤 부서 근무시간을 10:00~19:00으로 바꿔도 그날 판정과 기준은 그대로.
4. **동시 출근**: 같은 사람이 동시에 두 번 출근 → 기록 하나, 한쪽 409.
5. **결근 배치**: 근무일 기록 없음 → 결근 / 종일 휴가 → LEAVE / 주말·공휴일 → 행 없음 / 퇴근 누락 → MISSING / 출근 뒤 승인된 오후 반차 → 조퇴가 정상으로 바뀜 / 두 번 실행해도 같은 결과.
6. **월간 근태표**: 쿼리 1번, `EXPLAIN`이 `UK(user_id, work_date)` 사용, 범위 밖 부서 403, 50명 × 31일 실행 시간 기록.
7. **근태 정정 결재**: 승인 → 기록·판정 갱신 + `fix_document_id` / 반려·회수 → 그대로 / 30일 밖·미래 날짜 422 / 같은 날짜 진행 중 409.
8. **연차 부여 배치**: 고정 `Clock`으로 월 부여(1~11개월, 1/31 입사의 2월)·1주년 비례(7/1 입사 → 이듬해 7/1에 8일)·1월 1일 부여(근속 1·3·21년 = 15·16·25일) 계산, 1주년이 1월 1일인 사원은 한 번만(15일), 같은 날 두 번 실행해도 한 번, 입사일 없는 사원 건너뜀 기록, 하루 건너뛴 뒤 다음 실행에서 채움.
9. **시간대**: JVM이 UTC여도 한국 자정 직후(00:05) 출근은 그날 날짜로 기록, 배치의 '전날'도 한국 날짜 기준.
10. **KI-07**: `GET /api/v1/dashboard/admin-summary`는 관리소장 외 403.

---

## 9. 화면 ↔ API (시안 기준)

| 화면 | 동작 | API |
|---|---|---|
| 홈 '오늘 근무' 위젯 / 내 근태 | 오늘 상태, 출근·퇴근, 조퇴 확인 | `GET /attendance/today`, `POST …/clock-in`, `POST …/clock-out` |
| 내 근태 | 한 달 달력·요약, 정정 신청 바로가기 | `GET /attendance/me`, 기안하기(`ATTENDANCE_FIX`) |
| 월간 근태표 | 사원 × 날짜, 칸 상세, CSV, 다시 판정 | `GET /attendance/monthly`, `GET …/records/{userId}/{date}`, `GET …/monthly/export`, `POST …/rejudge` |
| 연차 관리 (새로) | 사원별 잔액, 원장, 수동 조정, 부여 실행 기록 | `GET /leave/admin/balances`, `…/ledger/{userId}`, `POST …/adjustments`, `GET/POST …/grant-runs` |
| 기안하기 · 결재 문서 | 근태 정정 양식 (전·후 비교) | M2 문서 API 그대로 |
