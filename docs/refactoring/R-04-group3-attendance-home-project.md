# R-04 4단계 묶음 3 — 근태·홈·프로젝트 응답 정리

4단계 마지막 묶음이다. 요청·응답·SQL·트랜잭션·CSV 동작을 보존하면서 응답 record, Java 이름, 순수 변환/집계 메서드를 정리했다. calendar는 이미 타입이 있고 catch 매개변수만 남아 변경하지 않았고, dashboard는 DTO만 있어 변경하지 않았다. 다음 단계·develop으로 병합·푸시는 하지 않았다.

## 기준과 준비

- 시작 `1a47c98`, 명세 11차 병합 `9424ffa2c226e41b46f24b1ab29d35b6a7c6e5da`. 병합 차이는 `backend/md/17-refactoring.md` 하나이며 `backend/src` 차이는 0. 외부 archive 컴파일 통과(10.29초).
- 묶음 2 보완/이번 비교 BASE: `d6938434a965788f29d4aaf15a2cdba84e529598`. 공통 단계 람다 step, 예약 원장 reservation, 문서 id number 통일, 내부 수동 단계 행 map 복원. group2 보고서의 남은 Map·길이·기존 버그도 반영했다.
- 준비 검증: test-compile, 호출 순서/응답/SQL 차이 0, 결재함 문자열 51곳 분류 유지, 트랜잭션 83개 동일, 미사용 import 0. 기록 ON 231/0/0/0, 838.93초, differences=0, 경고 11개(승인된 14개 안). [준비 증거](R-04-evidence/group3-preparation-result.json).
- 검증 도구: `9f04495166e08647e3e971ab37f54dab4f267d2a`. 최종 archive test-compile 통과. 원본/후보 모두 Testcontainers만 사용했다.

## 도구 보완

AttendanceStore.Schedule.view의 원래 출현만 예외로 분류한다. startTime/endTime/lateGraceMin/halfBoundary/source/departmentName 여섯 키는 새 record의 구성요소와 순서까지 대응하며 HH:mm 한 출현은 새 근무시간 record로 이동했다. view 밖 문자열과 추가 문자열은 0개 변화다. 결재함 예외는 이번 BASE 대비 변화가 없다.

| 판별력 | 정상 | 변조 |
|---|---:|---:|
| view 밖 SQL 문자 변경 | 0 | 차이 1 |
| record 키 변경 | 0 | 차이 1 |
| HH:mm 변경 | 0 | 차이 1 |
| 조건부 target 키 변경 | 응답 13종 차이 0 | 차이 1 |
| Page 순수 변환 중 throw 삽입 | 차이 0 | 차이 1 |

응답 도구는 다른 map 뒤 put, 람다의 조건부 put, if/else의 서로 다른 배열 항목, computeIfAbsent, 누적 totals, remove를 대조한다. 다른 빈의 반환은 `rejudge → absence.run → BatchRunRecorder.record` 및 `move → ProjectStateStore.recalculate`로 출처를 수동 연결하고 원래 SELECT/map 키를 검사했다. 중간 빈 호출은 실행 순서 도구로 별도 검사했다. 호출 프레임 310개 차이 0이며, 자동 전개가 멈춘 OrgService.departmentTree 재귀 두 항목은 이번 BASE와 파일 바이트가 같음을 확인했다. 재귀 호출과 효과 횟수를 바꾸지 않았다.

이름 도구는 global과 LocalDate for 변수를 센다. javac BindingPattern도 읽도록 보완해 SqlStore의 b 및 AttendanceStore의 t 두 개를 포함했다. record 구성요소는 데이터이므로 주입된 빈으로 취급하지 않는다. EmployeeService.page 삭제는 기존 PageResponseDto.of의 순수 변환과 정확히 대응시켰다. throw·시계 검사는 유지한다. 재현: `R-04-group3-verify-tools.py --before <컴파일된 BASE archive> --classpath <classpath.txt> --output-dir <새 임시 폴더>`.

## 커밋별 검증

기록 ON/OFF와 추가 비교 테스트는 순차로 실행했다. ON의 기존 동시 요청 순서 고정 정책은 그대로이며, 실제 경합은 해당 훅이 없는 OFF 실행으로 확인했다. 수는 실행/실패/오류/건너뜀이다. 컴파일·정적 검사·원문 보존이 통과한 후보만 해당 커밋으로 적용했다.

| 묶음 | 커밋 | 파일 수 | ON 수 / 초 | differences / 경고 | OFF 수 / 초 | 추가 비교 |
|---|---|---:|---|---|---|---|
| attendance | `02918dc8aea1cdfdd5d2c233b6da3151e99d1e02` | 15 | 231/0/0/0 · 676.66 | 0 / 9 | 231/0/0/0 · 697.9 | 4/0/0/0 · 차이 0 |
| notification | `7b7c0235ffb0dca52db0ebfb7405d64f0838efee` | 4 | 231/0/0/0 · 1067.27 | 0 / 11 | 231/0/0/0 · 929.87 | 4/0/0/0 · 차이 0 |
| project | `6e826bcafc43776cb2c3ff07b4521cf87100b531` | 10 | 231/0/0/0 · 827.89 | 0 / 11 | 231/0/0/0 · 482.14 | 4/0/0/0 · 차이 0 |
| task | `1309e5a6296303a2b5d8bdf9a9d1a73d4f3d398e` | 10 | 231/0/0/0 · 601.92 | 0 / 9 | 231/0/0/0 · 649.6 | 4/0/0/0 · 차이 0 |
| home | `f6df95c04a638ce92026f876a4e5044bd22ffd3e` | 3 | 231/0/0/0 · 905.79 | 0 / 11 | 231/0/0/0 · 1007.47 | 4/0/0/0 · 차이 0 |
| global | `3dbc348e59fa786e9aa58fa67a9e94e69474cb76` | 3 | 231/0/0/0 · 1009.59 | 0 / 11 | 231/0/0/0 · 751.91 | 4/0/0/0 · 차이 0 |

각 커밋의 a~j: (a) src/test 해시 동일, (b) SQL 값·출현·위치 대조 통과, (c) 트랜잭션 83개 동일, (d) 이름 대응 후 호출 순서 차이 0, (e) 응답 키·중첩·순서 대조 통과, (f) 형식 및 미사용 import 검사 통과, (g) 해당 도메인 한 글자 이름 0 및 두 글자 이름 목록, (h) ON 231개/기준선 차이 0/경고 승인 목록 안, (i) OFF 231개/기록 출력 0, (j) 별도 4개 probe 기준선 대비 차이 0. API는 모든 커밋에서 98개였다. 도메인별 `group3-<묶음>-static-final.json`·`-commit.json`·`-probe.json`에 상세 결과와 실제 소스 해시가 있다.

텍스트 블록의 내용과 닫는 줄 전체 변화는 0이다. 기존 예외는 유지했다: ProjectMember의 짧은 @Table 어노테이션 1곳, AttendanceStore의 닫는 줄에 붙은 ErrorCode 분리 및 outResult 키/값 분리. 이를 고치려고 닫는 줄을 바꾸지 않았다. main에서 그 외 120자 초과·여러 문장·여러 선언자·조각 분리·삼항/연산자·어노테이션 분리 등은 0, 문자열 리터럴 길이 예외는 88개, 미사용 import는 main 전체에서 0이다. 테스트는 수정 금지라 기존 형식 측정 결과(키/값 분리 8, 바깥 연산자 1, 삼항 1, 보호된 조각 1, 긴 리터럴 78)를 그대로 보존했다. 테스트 소스 해시가 BASE와 같으며 새 위반을 만들지 않았다.

초기 후보 검증 과정에서 닫는 줄 뒤의 outResult를 옮긴 형식 후보와 대상 밖 ProjectMember를 붙인 형식 후보를 원문으로 복원했다. 복원 전 기록은 완료 증거에서 제외했다. 최초 probe의 공휴일 fixture에는 필수 substitute가 빠져 실패했으며 0을 명시한 원문으로 BASE/후보를 다시 비교했다. 커밋 직전 diff --check가 줄 끝 공백 세 곳을 검출해 정리했고, 최종 소스로 정적 검사·ON/OFF·probe를 다시 실행했다. 이전 후보의 실행은 완료 증거로 사용하지 않았다. 운영 버그 수정은 없었다.

## 프로젝트 첫 기록의 차이와 재검증

첫 프로젝트 전체 ON은 기능 테스트 231/0/0/0이었지만 비교기는 differences=6으로 실패했다. select @@session.tx_isolation 한 번이 concurrentApprovalAndWithdrawalHaveOnlyOneWinner(89→88)에서 concurrentApprovalHasOne409AndNoNotificationFromLosingRequestInTenTrials(811→812)로 이동했다. 전역 SQL별 횟수는 모두 같고 해당 조회 총수도 94회로 같았으며 응답 차이는 0이었다. 실패 기록은 group3-project-initial-drift.json/.txt에 보존했다.

실행 중이던 후속 검증을 중단하고 원인을 확인했다. 관련 approval/leave/overtime 소스와 테스트는 BASE와 바이트가 같았다. 실제 사용한 드라이버를 javap으로 확인하면 MariaDB Connection.getTransactionIsolation은 캐시가 없을 때 해당 SQL을 실행하며, Hikari PoolBase.checkDefaultIsolation도 이를 호출한다. SafetyNetRun.sql은 실행 스레드와 무관하게 당시의 current 프레임으로 귀속한다. 따라서 연결 초기화/첫 사용 시점 차이로 프레임 귀속이 이동한 것으로 판단한다. 직접 스택 추적을 수집한 것은 아니므로 해당 실행의 정확한 호출 스레드까지 증명한 것은 아니다.

수정 없는 BASE의 M2DocumentIntegrationTest 두 번과 후보 한 번을 별도로 실행해 각각 11/0/0/0, 두 프레임 89/811을 확인했다. 실제 비교기로 BASE 1→BASE 2 및 BASE 1→후보를 비교하면 모두 differences=0이었다. 이 세 진단 실행에서는 처음의 이동 현상이 다시 발생하지 않았다. 코드·테스트·정책·기준선·허용 목록은 바꾸지 않고 전체 ON/OFF를 다시 검증했다. 최종 결과는 위 커밋 표에만 반영하며 첫 실패를 숨기지 않는다. 기존 기록의 프레임 귀속이 타이밍에 민감할 수 있다는 한계는 이번 범위에서 고치지 않았다.

## 응답과 타입 근거

[구성요소 대조 269건](R-04-evidence/group3-response-types.md)는 실제 원래 식/열, 새 구성요소, 타입과 원본 위치를 출현마다 기록한다. [API 대조](R-04-evidence/group3-api-map.json), [원래 기준선의 성공·항상 null 경로](R-04-evidence/group3-api-coverage.json)도 함께 본다. 반환 행을 직접 내보내던 곳은 SELECT 별칭 순서로 기록했다. 대조표 재생성은 `R-04-group3-response-types.py --before <BASE archive> --after <후보 archive> --classpath <classpath.txt> --mapping <group3-global-response-map.json> --output-dir <외부 폴더>`로 한다.

| API | 반환 |
|---|---|
| `GET /api/v1/attendance/monthly` | `ApiResponse<AttendanceMonthlyResponseDto>` |
| `GET /api/v1/attendance/records/{userId}/{date}` | `ApiResponse<AttendanceRecordResponseDto>` |
| `GET /api/v1/attendance/monthly/export` | `ResponseEntity<byte[]>` |
| `POST /api/v1/attendance/rejudge` | `ApiResponse<AttendanceBatchRunResponseDto>` |
| `GET /api/v1/attendance/today` | `ApiResponse<AttendanceTodayResponseDto>` |
| `POST /api/v1/attendance/clock-in` | `ApiResponse<AttendanceTodayResponseDto>` |
| `POST /api/v1/attendance/clock-out` | `ApiResponse<AttendanceTodayResponseDto>` |
| `GET /api/v1/attendance/me` | `ApiResponse<AttendanceMyMonthResponseDto>` |
| `GET /api/v1/home` | `ApiResponse<HomeResponseDto>` |
| `GET /api/v1/notifications` | `ApiResponse<PageResponseDto<NotificationItemResponseDto>>` |
| `GET /api/v1/notifications/unread-count` | `ApiResponse<NotificationUnreadCountResponseDto>` |
| `PATCH /api/v1/notifications/{id}/read` | `ApiResponse<Void>` |
| `POST /api/v1/notifications/read-all` | `ApiResponse<Void>` |
| `GET /api/v1/projects` | `ApiResponse<PageResponseDto<ProjectSummaryResponseDto>>` |
| `POST /api/v1/projects` | `ResponseEntity<ApiResponse<ProjectDetailResponseDto>>` |
| `GET /api/v1/projects/{id:[0-9]+}` | `ApiResponse<ProjectDetailResponseDto>` |
| `PATCH /api/v1/projects/{id:[0-9]+}` | `ApiResponse<ProjectDetailResponseDto>` |
| `PUT /api/v1/projects/{id:[0-9]+}/members` | `ApiResponse<ProjectDetailResponseDto>` |
| `GET /api/v1/projects/{id:[0-9]+}/board` | `ApiResponse<TaskBoardResponseDto>` |
| `POST /api/v1/projects/{id:[0-9]+}/tasks` | `ResponseEntity<ApiResponse<TaskDetailResponseDto>>` |
| `GET /api/v1/tasks/{id:[0-9]+}` | `ApiResponse<TaskDetailResponseDto>` |
| `PATCH /api/v1/tasks/{id:[0-9]+}` | `ApiResponse<TaskDetailResponseDto>` |
| `POST /api/v1/tasks/{id:[0-9]+}/move` | `ApiResponse<TaskMoveResponseDto>` |

- 근태 오늘은 14키, 근무시간은 6키/상세 4키이다. 숫자 합계는 원래 int와 double을 유지하고 월 지각·결근 수는 long이다. CSV의 0/0.0, null 이름의 빈 문자열, BOM/CRLF/인용 규칙과 날짜·사용자 순서를 그대로 비교했다.
- 프로젝트 요약은 15키이고 상세 record는 members·permissions를 더한다. 생성/수정에서는 permissions가 없으므로 해당 구성요소만 NON_NULL이다. 원래 키가 있는 상세/멤버 변경에서는 getPermissions가 항상 record를 만들므로 null 값이 키와 함께 있었던 경우는 없다. COALESCE(SUM) overdueCount는 BigDecimal, count/task/progress는 long이다.
- 작업 카드 11키와 상세 15키를 나눈다. MOVED는 action/actor/at만, 다른 이력은 from/to가 null이어도 키를 남긴다. target_id가 있을 때만 target record를 만들고 그 구성요소만 NON_NULL이다. 원래 CASE는 MOVED에 target_id를 만들지 않는다. CAST AS UNSIGNED인 target_id는 BigInteger 직접 캐스트 대신 number를 쓴다.
- 혼합 배열 세 곳은 메서드 없는 중첩 인터페이스와 모양별 record이다: 월간 근태 Day(2키/8키), 업무 History(Moved/Event), 홈 WeekItem(TaskDue/LeaveItem). 전역 JsonInclude 설정은 바꾸지 않았다.
- 홈은 today→approvals→leave→teamWeek→myTasks→notifications 순서다. FormService.shortName은 서비스에 남겼고 DTO는 서비스에 의존하지 않는다. 알림은 rows.get(0)의 unread를 먼저 읽으며 사람 record는 프로젝트·보드·작업 assignees와 같은 세 키로 공유한다.
- 기준선이 빠뜨린 holidayName, clockOutAt, 실제 승인 뒤 fixDocument/leaveDocument, 기록 없는 날, 프로젝트 content·myRole MEMBER/null·leader null, 이력 7종·409 currentTask, 홈 권한별 null·주간 두 종류, 알림 unreadOnly·두 번째 쪽을 probe로 확인했다. 저장소 src/test에는 추가하지 않았다.

## 이름·메서드 나누기

전체 대응은 각 커밋 `group3-*-renames.json`에 있다. 컨트롤러의 HTTP 매개변수는 q만 Java query로 바꾸고 `@RequestParam(name="q")`로 원래 이름을 고정했다. 나머지 요청 매개변수와 요청 record 구성요소는 그대로다.

| 나눈 책임 | 유지한 순서/경계 |
|---|---|
| AttendanceAdminService.monthlyTable + private MonthlyRow | 범위·SQL·Clock은 원래 순서로 서비스에서 수행; TreeMap 날짜, LinkedHashMap 첫 사용자 순서 유지; 집계 클래스는 빈/Clock/throw 없음 |
| AttendanceFixFormHandler.requireCorrectionDate | 기존 날짜 검사와 Clock 읽기를 같은 위치에서 private 호출로 펼침 |
| 응답 record.from 및 작업 historyFrom | 행만 쓰는 식을 옮김. 권한·시계·요청·throw는 서비스에 유지 |
| BoardProjectService.toSummary | myRole의 actor()는 서비스에서 구해 from(row, myRole)에 전달 |
| BoardTaskService.card·taskDetail | card의 DONE 분기 및 canManage와 assigned의 단락 평가, anyMatch 안 actor 위치 유지; raw→assignees→card→canManage→findHistory |
| HomeService의 기존 위젯 메서드 | countTodo→findTodo→findMine, args.put→findTeamWeek, findMySummary→findMyItems 유지 |

메서드 길이 상위 20개 전후와 전체 메서드 길이는 `group3-start-names.json` / `group3-global-names.json`에 있다. 이름 수는 기존 도구와 같이 메서드 매개변수·지역 변수·람다 기준이며 필드와 record 구성요소는 제외한다. 두 글자 이름은 at(시각), id(식별자), in(출근), no(문서번호), pm(ProjectMember), rs(ResultSet), to(종료), ym(YearMonth)이며 위치 목록도 같은 파일에 있다. catch·정수 for 카운터는 측정 제외다.

## 측정 전후

| 범위 | ApiResponse<?> | ResponseEntity<?> | public Map/Object | 비 repository factory 줄 | 한 글자 이름 |
|---|---:|---:|---:|---:|---:|
| attendance | 7 → 0 | 0 → 0 | 10 → 2 | 17 → 2 | 33 → 0 |
| calendar | 0 → 0 | 0 → 0 | 0 → 0 | 0 → 0 | 0 → 0 |
| home | 1 → 0 | 0 → 0 | 1 → 0 | 20 → 1 | 11 → 0 |
| notification | 4 → 0 | 0 → 0 | 2 → 0 | 2 → 0 | 2 → 0 |
| project | 4 → 0 | 1 → 0 | 10 → 2 | 3 → 0 | 21 → 0 |
| task | 4 → 0 | 1 → 0 | 6 → 1 | 9 → 1 | 24 → 0 |
| dashboard | 0 → 0 | 0 → 0 | 0 → 0 | 0 → 0 | 0 → 0 |
| global | 0 → 0 | 0 → 0 | 5 → 5 | 5 → 5 | 9 → 0 |

| 책임 분리 | 전 메서드 줄 수 | 후 메서드 줄 수 |
|---|---:|---|
| `attendance.service.AttendanceAdminService.monthly` | 46 | getMonthlyAttendance: 11, monthlyTable: 34 |
| `attendance.handler.AttendanceFixFormHandler.validate` | 17 | validate: 14, requireCorrectionDate: 6 |
| `project.service.BoardProjectService.view` | 15 | toSummary: 6 |
| `task.service.BoardTaskService.detail` | 4/24 | getTask: 4, taskDetail: 10 |
| `home.service.HomeService.approvals` | 20 | approvals: 6 |

4단계 전체 ApiResponse<?>는 시작 93개 → 이번 묶음 시작 20개 → 최종 0개, API 98개 그대로다. 이번 묶음 domain/global 한 글자 이름은 100 → 0이다. EmployeeService.page 정의·호출은 0이다. 세는 방식은 factory 호출 수가 아니라 원래 요청대로 호출이 있는 서로 다른 줄 수이며 repository를 제외한다. 새 record의 from 매개변수 Map은 반환 Map으로 세지 않는다.

재측정: `R-04-group3-check.py --before <컴파일된 d693843 archive> --after <컴파일된 대상 커밋 archive> --classpath <classpath.txt> --renames <group3-도메인-renames.json> --responses <group3-도메인-response-map.json> --output <외부 결과 폴더>`. `R-04-names.py`, `R-04-response-check.py`, `R-03-sql-location-measure.py`, `R-02-format-measure.py`, `R-03-format-units.py`를 이용한다. 전부 JDK/Python 표준 라이브러리만 쓰며 원본은 읽기 전용이다.

## global 커밋 뒤 전체 main 재측정

최종 코드 커밋 `3dbc348e59fa786e9aa58fa67a9e94e69474cb76`을 별도 git archive로 풀어 test-compile(11.28초) 뒤 전체 검사를 다시 수행했다. 후보 디렉터리의 과거 결과를 재사용하지 않았다. main 257개·test 79개 Java, 전체 src 파일 해시 362개가 검증 후보·작업 트리와 모두 같았다. 전체 main ApiResponse<?>·ResponseEntity<?>·한 글자 이름은 각각 0, API 98개, 트랜잭션 83개, EmployeeService.page 정의·호출 0, 테스트 및 금지 경로 변화 0이다. 상세 수치와 명령 결과는 [커밋 후 측정](R-04-evidence/group3-final-commit-measurement.json)과 group3-final-commit-* 파일에 있다.

## 남긴 것과 판단

- 서비스 Map 반환: AbsenceBatchService.run/runPreviousDay, TaskOverdueService.runToday는 테스트/배치 계약. BoardProjectService.getVisibleProject/lockVisibleProject는 검사 및 SQL 값에 사용하는 행이다.
- 비 repository map: AttendanceService.getHomeAttendance의 내부 휴가 행, AttendanceFixFormHandler.onSubmitted의 저장 JSON(키 순서 유지), HomeService.getHome의 공용 SQL 인자(args.put 포함), BoardTaskService.moveTask의 BusinessException details.currentTask. 응답 factory를 LinkedHashMap/put으로 숨기지 않았다. 묶음 2에서 복원한 수동 결재선 내부 행도 유지한다.
- AttendanceStore의 Schedule·Day, 판정·시각 파싱 및 AttendanceRules 의존과 ProjectStateStore의 잠금 전제·집계·상태 계산·시계는 테스트가 감싸는 기존 빈의 책임으로 남겼다. NotificationService·SqlStore 등 지정 빈의 public 이름/타입/순서도 유지했다.
- global의 Map은 기존 공통 SQL·권한 자료구조이며 이번에는 이름만 바꿨다. 새 응답 record의 구성요소에는 Object/Map<String,Object>/와일드카드가 없다.
- 지우지 않은 옛 DTO: ProjectResponseDto, ProjectMemberResponseDto, ProjectPermissionDto, ProjectProgressResponseDto, LeaderSummaryDto, AssignableMemberDto, AvailableMemberResponseDto, TaskResponseDto, TaskAssignmentResponseDto, RecentAssignmentDto.

## 기존 버그·한계

- 기록기의 타이밍 한계: 프로젝트 첫 ON에서 드라이버 격리 수준 조회 한 번의 테스트 귀속 이동으로 차이 6건이 났다. 진단과 전체 재검증은 통과했지만 이 비결정성을 고친 것은 아니다. 위 프로젝트 첫 기록 절에 실패와 재검증을 함께 남겼다.
- 숫자 쿼리에 문자(page=x)를 넣으면 500: GlobalExceptionHandler의 MethodArgumentTypeMismatchException 처리가 없으며 c531e1f에서도 동일. 이번에는 이름만 바꾸고 고치지 않았다.
- AttendanceService.getMyMonthlyAttendance의 쓰이지 않는 LocalDate.now(clock), 날짜마다 records.day 조회를 유지했다. 지우거나 조회 수를 줄이는 것은 동작/시계·SQL 순서 변경이다.
- 실제 프론트·운영 서버·개발 DB/Redis는 검증하지 않았다. SQL 지문은 파라미터 값을 저장하지 않으므로 소스의 키/값 식·순서와 타입 근거를 함께 확인했다. 모든 데이터 조합을 완전 탐색한 것은 아니며 기준선의 누락/항상 null 경로를 별도 probe로 보완했다.
- 순서 경고는 승인된 14개 목록 안의 프레임뿐이었다. 목록을 넓히거나 경고를 삭제하지 않았다. 합집합은 `group3-observed-warnings.json`에 있다. 기본 기록 정책·비교 기준선 세 파일 해시는 그대로이며 테스트 소스 변화는 0이다.


## 다시 실행하는 명령

작업 트리 밖에 해당 커밋을 git archive로 풀고 그 사본의 backend에서 실행한다. JAVA_HOME은 Java 17 설치 경로로 지정한다. 아래 기록 폴더는 실행마다 새 경로를 사용한다.

```sh
./mvnw -Duser.timezone=UTC -Drefactoring.record=/private/tmp/r04-review-on test
"$JAVA_HOME/bin/java" -cp "$(cat /private/tmp/r04-review-on/classpath.txt)" com.minierp.backend.support.refactoring.SnapshotComparator src/test/resources/refactoring-baseline /private/tmp/r04-review-on
./mvnw -Duser.timezone=UTC test
```

추가 비교는 BASE와 후보의 별도 사본에만 R-04-probe/R04Group3Probe.java를 backend/src/test/java/com/minierp/backend/integration/으로 복사한 뒤 같은 기록 옵션과 -Dtest=R04Group3Probe로 실행한다. 비교기의 첫 인자는 BASE의 추가 기록 폴더이고 둘째 인자는 후보의 추가 기록 폴더다. 저장소 src/test에는 복사하지 않는다.
