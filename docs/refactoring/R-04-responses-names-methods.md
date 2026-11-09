# R-04 4단계 묶음 1 — 응답·이름·메서드

범위는 준비 보완과 org·user·permission뿐이다. 공유 원본 체크아웃의 브랜치, 기존 테스트, 기준선, 기록 정책, 비교 규칙을 바꾸지 않았다. 다음 묶음은 시작하지 않는다. 실행 환경은 Java 17.0.16, UTC JVM, 기존 Testcontainers MariaDB/Redis이며 로컬 개발 서비스는 사용하지 않았다.

## 시작 기준과 준비 보완

4단계 시작 커밋: `5b6430af0d814dc1bd60c368e64c5205b3592f65`. develop 수신 병합 `632449c6ef68f654bd5734f284f2110faf8d751b`에서는 `backend/md/17-refactoring.md` 한 파일만 들어왔고 머리말의 9차를 확인했다. 병합 archive의 test-compile은 exit 0, 8.42초였다.

원래 지정된 미사용 import 8곳: HomeService ErrorCode, BoardProjectService ProjectSummaryQuery, NotificationService static SqlStore.map, NotificationQueries java.time.*, NotificationStore ErrorCode·java.util.*, TaskStore ErrorCode, LeaveGrantWork global.exception.*. 추가로 발견하여 사용자 승인 후 제거한 5곳: LeaveRequest JoinColumn, AuthController SignupRequestDto·UserResponseDto·HttpStatus, AuthService SignupRequestDto. 원래 Java 내용과 같은 병합 사본에서 정확히 13곳, 보완 후 main 전체 0곳이다.

AttendanceFixStore의 upsertBody·copyBody, TaskQueries의 findMaxPosition, ProjectQueries의 pattern 이름을 반영했다. R-03의 8차 병합 사실도 보완했다. 3단계 문자열·트랜잭션·호출 지점·닫는 줄·쿼리 위치·형식 검사를 통과했다. 최종 13곳 정리 후 기록 ON은 231/0/0/0, 511.36초, REFACTORING_COMPARE differences=0 sqlOrderChanges=11였다. 앞선 8곳만 제거한 실행은 최종 후보의 완료 근거로 대체하지 않았다.

## 커밋

| 순서 | 해시 | 내용 | 파일 수 |
| --- | --- | --- | --- |
| 0 | 632449c6ef68 | 명세 17 9차 수신 병합 | 1 |
| 1 | 5b6430af0d81 | 묶음 3 보완 / 4단계 시작 기준 | 16 |
| 2 | c26842d74ff5 | 검증 도구 | 8 |
| 3 | 7ce396362b0c | org | 9 |
| 4 | f1ea5da96ea8 | user | 13 |
| 5 | ad49e943467d | permission | 7 |

순서 6은 `docs(refactor): R-04 4단계 묶음 1 기록` 문서 커밋이다.

마지막 문서 커밋은 이 보고서·대응표·검증 요약·임시 비교 테스트 원문만 포함한다. 자기 해시는 보고서에 순환하여 넣지 않고 `git log --oneline`으로 확인한다.

## 검증 도구와 판별력

javac Tree API로 귀속된 심볼과 식을 읽고 Python 표준 라이브러리로 비교한다. 컴파일 단위별로 해당 사본의 컴파일된 클래스를 참조한다. 프로세서 없이 분석하므로 Lombok 생성 생성자·log 등에 관한 진단이 남지만, 별도 Maven 컴파일은 성공했고 이번 사건의 주입 필드·now·ErrorCode는 귀속됨을 확인했다. 대상 컨트롤러/서비스에서 시그니처가 해소되지 않은 필드 호출은 시작·후보 모두 0건이다. 입력 소스는 읽기만 하고 캐시·출력은 지정한 임시 경로에 쓴다.

| 도구 | 확인 범위 |
| --- | --- |
| R-04-call-order-check.py | 수신자→인자→호출, 빈/시계/throw, 같은 클래스 public·private·메서드 참조 펼침, 조건·반복·try·람다 경계 |
| R-04-response-check.py | 원래 map·put/SELECT 별칭→record 키 순서·중첩, JsonProperty, 남은 map·API 성공/항상 null 경로 |
| R-04-sql-literal-check.py | repository·global/persistence·공통 쿼리 3종의 파일별 문자열 값 다중집합 |
| R-03-sql-location-measure.py | @Transactional 소유 메서드의 rename-method 대조 |
| R-04-unused-imports.py | 귀속된 비한정 식별자 사용으로 단일·정적·와일드카드 import 확인 |
| R-04-names.py | 지역·매개·람다 이름, for 머리 인덱스/catch 제외, 메서드 소스 줄 수 |

같은 시작 코드 300개 public 진입점은 차이 0. 작업 트리 밖에서만 바꾼 네 사본을 실제 CLI에 넣었다.

| 돌연변이 | 종료 코드 | 차이 | 검출 메서드 |
| --- | --- | --- | --- |
| sql-order | 1 | 1 | OrgService#tree() |
| condition-boundary | 1 | 1 | OrgService#setHead(long,Long) |
| error-code | 1 | 1 | OrgService#setHead(long,Long) |
| clock-order | 1 | 1 | EmployeeService#register(EmployeeRequests.Create) |

도구 커밋 archive test-compile: exit 0, 8.81초. 새 라이브러리·빌드 플러그인은 없다.

수동 확인은 트리의 재귀 경계 한 곳이다. 원래 aggregate와 새 departmentTree 안에는 빈 호출·Clock·throw가 없다. 두 조회는 여전히 findDepartments → findDepartmentMemberCounts이며, 반환된 부서 순서대로 children을 구성하고 자기 인원과 자식 합계를 long으로 합산한다. null 루트와 빈 children을 유지한다. 도구는 재귀를 무한히 펼치지 않고 manualReview에 남긴다.

## 커밋별 검증

| 묶음 | (a) 테스트 변경 | (b)(c) SQL 차이/Tx 수 | (d) 순서 차이 | (e) 응답 차이 | (f) import | (h) ON 총/실패/오류/skip·시간 | 기준선 비교 | (i) OFF 총/실패/오류/skip·시간 | (j) 추가 비교 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| org | 0 | 0 / 83 | 0 | 0 | 0 | [231, 0, 0, 0] / 551.28s | REFACTORING_COMPARE differences=0 sqlOrderChanges=11 | [231, 0, 0, 0] / 550.63s | REFACTORING_COMPARE differences=0 sqlOrderChanges=0 |
| user | 0 | 0 / 83 | 0 | 0 | 0 | [231, 0, 0, 0] / 592.95s | REFACTORING_COMPARE differences=0 sqlOrderChanges=11 | [231, 0, 0, 0] / 1114.45s | REFACTORING_COMPARE differences=0 sqlOrderChanges=0 |
| permission | 0 | 0 / 83 | 0 | 0 | 0 | [231, 0, 0, 0] / 547.94s | REFACTORING_COMPARE differences=0 sqlOrderChanges=10 | [231, 0, 0, 0] / 562.67s | REFACTORING_COMPARE differences=0 sqlOrderChanges=0 |

org·permission은 ON→OFF 순서로 실행했고, user는 적용 후 같은 Git tree의 별도 archive에서 OFF를 ON과 병행했다. user 동시 실행에서 경과 시간이 늘어 다음 permission은 다시 순차 실행했다. 각 실행은 독립 JVM/Testcontainers/출력 폴더를 사용했고 두 모드 성공 후에만 커밋했다. OFF 사본 tree는 validation-summary.json에 기록했다.

SQL 소유 파일 40개, 리터럴 출현 1,022→1,022, repository 밖 SQL 0. 새 DTO 생성으로 전체 main 리터럴은 달라지므로 SQL 소유 범위의 값·출현 수를 별도로 비교한다. @Transactional은 메서드 이름 대응 후 83→83이다. 사용자 커밋의 RoleService 호출부 한 줄(views→getEmployeeViews)도 실행 순서 비교에 포함했다.

R-02: 새 120자 초과·여러 문장·여러 선언자 0. 기존 문자열 예외 88개와 보호된 AttendanceStore 닫는 줄 관련 예외는 그대로다. 한 줄 단위는 main의 기존 ProjectMember 1곳, test 0. 세 도메인의 한 글자 이름은 0. OFF는 REFACTORING_RECORDED 출력 0을 확인한다. 전체 기록은 응답 921개(그중 오류 응답 191개)를 비교한다. 차이 0일 때 4xx·5xx 응답 차이도 0이다.

### org 순서 경고

기존 13개 허용 합집합 안임을 확인한다. 11개:

- `com.minierp.backend.integration.M1AcceptanceTest#concurrentIdAllocationIsUniqueAndUnsafeMaxPlusOneCollides()`
- `com.minierp.backend.integration.M2DocumentIntegrationTest#concurrentApprovalHasOne409AndNoNotificationFromLosingRequestInTenTrials()`
- `com.minierp.backend.integration.M2DocumentIntegrationTest#documentNumbersAreUniqueForEightDifferentDraftersSubmittingConcurrently()`
- `com.minierp.backend.integration.M2LegacyApprovalRaceTest#unconditionalLegacyApprovalAcceptsBothRequestsInTenControlledRaces()`
- `com.minierp.backend.integration.M3AttendanceTest#concurrentClockInHasExactlyOneWinnerInTenTrials()`
- `com.minierp.backend.integration.M3LeaveAdjustmentConcurrencyTest#unlockedModelFailsThenLockedAdjustmentsAndSubmissionsPreserveBalance()`
- `com.minierp.backend.integration.M3LeaveGrantTest#twoConcurrentBatchRunsIssueEachKeyOnlyOnceInTenTrials()`
- `com.minierp.backend.integration.M4BoardConcurrencyTest#gapsUpdateOneRowSerializeTwentyMovesAndRebalanceOnlyWhenExhausted()`
- `com.minierp.backend.integration.M4BoardConcurrencyTest#tenConcurrentCreatesHaveContiguousUniqueNumbers()`
- `com.minierp.backend.integration.M4OverdueTest#concurrentRunsSendOnlyOnce()`
- `com.minierp.backend.integration.M5ProductionTest#<before-all>`

### user 순서 경고

기존 13개 허용 합집합 안임을 확인한다. 11개:

- `com.minierp.backend.integration.M1AcceptanceTest#concurrentIdAllocationIsUniqueAndUnsafeMaxPlusOneCollides()`
- `com.minierp.backend.integration.M2DocumentIntegrationTest#concurrentApprovalAndWithdrawalHaveOnlyOneWinner()`
- `com.minierp.backend.integration.M2DocumentIntegrationTest#concurrentApprovalHasOne409AndNoNotificationFromLosingRequestInTenTrials()`
- `com.minierp.backend.integration.M2DocumentIntegrationTest#documentNumbersAreUniqueForEightDifferentDraftersSubmittingConcurrently()`
- `com.minierp.backend.integration.M2LegacyApprovalRaceTest#unconditionalLegacyApprovalAcceptsBothRequestsInTenControlledRaces()`
- `com.minierp.backend.integration.M3AttendanceTest#concurrentClockInHasExactlyOneWinnerInTenTrials()`
- `com.minierp.backend.integration.M3LeaveAdjustmentConcurrencyTest#unlockedModelFailsThenLockedAdjustmentsAndSubmissionsPreserveBalance()`
- `com.minierp.backend.integration.M3LeaveGrantTest#twoConcurrentBatchRunsIssueEachKeyOnlyOnceInTenTrials()`
- `com.minierp.backend.integration.M4BoardConcurrencyTest#gapsUpdateOneRowSerializeTwentyMovesAndRebalanceOnlyWhenExhausted()`
- `com.minierp.backend.integration.M4BoardConcurrencyTest#tenConcurrentCreatesHaveContiguousUniqueNumbers()`
- `com.minierp.backend.integration.M4OverdueTest#concurrentRunsSendOnlyOnce()`

### permission 순서 경고

기존 13개 허용 합집합 안임을 확인한다. 10개:

- `com.minierp.backend.integration.M1AcceptanceTest#concurrentIdAllocationIsUniqueAndUnsafeMaxPlusOneCollides()`
- `com.minierp.backend.integration.M2DocumentIntegrationTest#concurrentApprovalHasOne409AndNoNotificationFromLosingRequestInTenTrials()`
- `com.minierp.backend.integration.M2DocumentIntegrationTest#documentNumbersAreUniqueForEightDifferentDraftersSubmittingConcurrently()`
- `com.minierp.backend.integration.M2LegacyApprovalRaceTest#unconditionalLegacyApprovalAcceptsBothRequestsInTenControlledRaces()`
- `com.minierp.backend.integration.M3AttendanceTest#concurrentClockInHasExactlyOneWinnerInTenTrials()`
- `com.minierp.backend.integration.M3LeaveAdjustmentConcurrencyTest#unlockedModelFailsThenLockedAdjustmentsAndSubmissionsPreserveBalance()`
- `com.minierp.backend.integration.M3LeaveGrantTest#twoConcurrentBatchRunsIssueEachKeyOnlyOnceInTenTrials()`
- `com.minierp.backend.integration.M4BoardConcurrencyTest#tenConcurrentCreatesHaveContiguousUniqueNumbers()`
- `com.minierp.backend.integration.M4OverdueTest#concurrentRunsSendOnlyOnce()`
- `com.minierp.backend.integration.M5ProductionTest#<before-all>`

### 사원 첫 실행에서 추가로 관찰한 순서 경고

첫 동시 실행은 ON 231/0/0/0 (1134.59초), OFF 231/0/0/0 (1114.45초), differences=0이었다. 다만 기존 13개 목록에 없는 `M4LegacyExperimentTest#unlockedWholeListRecalculationReproducesWriteSkewTenTimes()` 경고 1개가 나와 자동 커밋을 중단했다.

두 실행 모두 workerSql=true, queryCount=comparedQueryCount=171, diagnosticQueryCount=0이며 SQL별 횟수가 같다. 첫 경합에서 두 작업 스레드의 `SELECT @@session.tx_isolation`과 `UPDATE tasks ...`가 교차한 순서만 달랐다. 테스트의 barrier는 UPDATE 뒤에 있어 각 트랜잭션 시작/격리수준 조회 순서를 고정하지 않는다. 테스트·baseline·기록 정책·비교 규칙을 바꾸지 않았고, 첫 결과도 보존했다. 사원 Java 내용/Git tree를 그대로 둔 단독 ON 전체 실행으로 다시 확인한 결과를 위 표에 적었다.

따라서 최초 13개 목록이 가능한 모든 병렬 순서 경고를 포괄한다고 보장할 수는 없다. 기존 비교기는 이 프레임의 workerSql과 SQL별 횟수 불변을 확인하고 경고로 구분했다. 이번에 목록을 넓히거나 경고를 지우지 않았다. 이 관찰은 후속 검증 기준 리뷰용으로 남긴다. 실제 전후 순서는 R-04-evidence/new-warning-analysis.json에 있다.

## 기준선 사각지대의 추가 비교

동일한 R04ContractProbe.java를 시작/후보의 작업 트리 밖 src/test에 복사했다. 저장소 src/test에는 추가하지 않는다. org·user·permission 각 메서드를 기록 ON으로 실행하고 시작 기록과 기존 SnapshotComparator로 비교한다. 임시 테스트는 app.mail.enabled=false와 Mockito InvitationTokenStore를 써 외부 메일·Redis 토큰 쓰기 없이 초대 응답을 확인한다.

| 영역 | 시작 성공·시간 | 후보 성공·시간 | JSON/SQL 차이 |
| --- | --- | --- | --- |
| org | 1/0/0/0, 24.08s | 1/0/0/0, 29.13s | 0 / 순서 경고 0 |
| user | 1/0/0/0, 28.41s | 1/0/0/0, 32.04s | 0 / 순서 경고 0 |
| permission | 1/0/0/0, 28.01s | 1/0/0/0, 44.1s | 0 / 순서 경고 0 |

첫 조직 probe는 fixture INSERT의 active 필수값 누락(1364)으로 실패했다. 테스트 전용 원문에 active=1을 넣은 뒤 기준 사본부터 다시 실행했다. 운영 코드 오류나 응답 변화로 처리하지 않았다. 일반 부서장/상위 부서장, 역할 description, 사원 PATCH retiredDate가 실제 값을 가지는 경우를 함께 단언했다.

### API → 응답 타입

| API | 반환 타입 | 기존 성공 스냅샷 | 기존 항상 null 경로 |
| --- | --- | --- | --- |
| GET /api/v1/departments/tree | ApiResponse&lt;OrgDepartmentTreeResponseDto&gt; | 없음 → probe | — |
| GET /api/v1/departments/{id} | ApiResponse&lt;OrgDepartmentResponseDto&gt; | 1 | $.data.head |
| POST /api/v1/departments | ApiResponse&lt;OrgDepartmentResponseDto&gt; | 15 | $.data.fallbackHead, $.data.head |
| PATCH /api/v1/departments/{id} | ApiResponse&lt;OrgDepartmentResponseDto&gt; | 1 | $.data.head |
| PATCH /api/v1/departments/{id}/parent | ApiResponse&lt;OrgDepartmentResponseDto&gt; | 없음 → probe | — |
| PUT /api/v1/departments/{id}/head | ApiResponse&lt;OrgDepartmentResponseDto&gt; | 2 | $.data.fallbackHead |
| DELETE /api/v1/departments/{id}/head | ApiResponse&lt;OrgDepartmentResponseDto&gt; | 1 | $.data.fallbackHead, $.data.head |
| PUT /api/v1/departments/{id}/work-schedule | ApiResponse&lt;OrgDepartmentResponseDto&gt; | 2 | $.data.head |
| DELETE /api/v1/departments/{id} | ApiResponse&lt;Void&gt; | 1 | — |
| GET /api/v1/work-schedules/default | ApiResponse&lt;OrgWorkScheduleResponseDto&gt; | 없음 → probe | — |
| PUT /api/v1/work-schedules/default | ApiResponse&lt;OrgWorkScheduleResponseDto&gt; | 없음 → probe | — |
| GET /api/v1/positions | ApiResponse&lt;List&lt;OrgPositionResponseDto&gt;&gt; | 없음 → probe | — |
| POST /api/v1/positions | ApiResponse&lt;OrgPositionCreateResponseDto&gt; | 1 | — |
| PATCH /api/v1/positions/{id} | ApiResponse&lt;OrgPositionUpdateResponseDto&gt; | 1 | — |
| PUT /api/v1/positions/order | ApiResponse&lt;List&lt;OrgPositionResponseDto&gt;&gt; | 1 | — |
| DELETE /api/v1/positions/{id} | ApiResponse&lt;Void&gt; | 1 | — |
| GET /api/v1/holidays | ApiResponse&lt;List&lt;OrgHolidayResponseDto&gt;&gt; | 없음 → probe | — |
| POST /api/v1/holidays | ApiResponse&lt;OrgHolidayResponseDto&gt; | 1 | — |
| PATCH /api/v1/holidays/{id} | ApiResponse&lt;OrgHolidayResponseDto&gt; | 없음 → probe | — |
| DELETE /api/v1/holidays/{id} | ApiResponse&lt;Void&gt; | 1 | — |
| GET /api/v1/menus | ApiResponse&lt;List&lt;RoleMenuResponseDto&gt;&gt; | 없음 → probe | — |
| GET /api/v1/roles | ApiResponse&lt;List&lt;RoleSummaryResponseDto&gt;&gt; | 없음 → probe | — |
| POST /api/v1/roles | ApiResponse&lt;RoleDetailResponseDto&gt; | 3 | $.data.description |
| PATCH /api/v1/roles/{id} | ApiResponse&lt;RoleDetailResponseDto&gt; | 없음 → probe | — |
| DELETE /api/v1/roles/{id} | ApiResponse&lt;Void&gt; | 없음 → probe | — |
| GET /api/v1/roles/{id}/permissions | ApiResponse&lt;RolePermissionsResponseDto&gt; | 없음 → probe | — |
| PUT /api/v1/roles/{id}/permissions | ApiResponse&lt;RolePermissionsResponseDto&gt; | 2 | $.data.role.description |
| GET /api/v1/roles/{id}/members | ApiResponse&lt;PageResponseDto&lt;EmployeeDetailResponseDto&gt;&gt; | 없음 → probe | — |
| POST /api/v1/roles/{id}/members | ApiResponse&lt;Void&gt; | 1 | — |
| DELETE /api/v1/roles/{id}/members/{userId} | ApiResponse&lt;Void&gt; | 없음 → probe | — |
| POST /api/v1/auth/password/setup | ResponseEntity&lt;ApiResponse&lt;Void&gt;&gt; | 6 | — |
| POST /api/v1/auth/login | ResponseEntity&lt;ApiResponse&lt;LoginResponseDto&gt;&gt; | 149 | — |
| POST /api/v1/auth/find-id/request | ResponseEntity&lt;ApiResponse&lt;FindIdResponseDto&gt;&gt; | 없음 / 기존 typed auth 유지 | — |
| POST /api/v1/auth/password/reset/request | ResponseEntity&lt;ApiResponse&lt;PasswordResetRequestResponseDto&gt;&gt; | 없음 / 기존 typed auth 유지 | — |
| POST /api/v1/auth/password/reset/verify | ResponseEntity&lt;ApiResponse&lt;PasswordResetVerifyResponseDto&gt;&gt; | 없음 / 기존 typed auth 유지 | — |
| POST /api/v1/auth/password/reset/confirm | ResponseEntity&lt;ApiResponse&lt;PasswordResetConfirmResponseDto&gt;&gt; | 없음 / 기존 typed auth 유지 | — |
| GET /api/v1/me | ApiResponse&lt;UserResponseDto&gt; | 1 | — |
| GET /api/v1/me/permissions | ApiResponse&lt;List&lt;PermissionAccess.Grant&gt;&gt; | 1 | — |
| PATCH /api/v1/me/password | ApiResponse&lt;LoginResponseDto&gt; | 1 | — |
| GET /api/v1/users | ApiResponse&lt;EmployeeListResponseDto&gt; | 7 | — |
| GET /api/v1/users/lookup | ApiResponse&lt;List&lt;EmployeeLookupResponseDto&gt;&gt; | 4 | — |
| GET /api/v1/users/login-id-preview | ApiResponse&lt;EmployeeLoginIdPreviewResponseDto&gt; | 1 | — |
| GET /api/v1/users/{id} | ApiResponse&lt;EmployeeDetailResponseDto&gt; | 8 | — |
| POST /api/v1/users | ApiResponse&lt;RegistrationResult&gt; | 4 | — |
| PATCH /api/v1/users/{id} | ApiResponse&lt;EmployeeDetailResponseDto&gt; | 12 | $.data.retiredDate |
| PATCH /api/v1/users/{id}/status | ApiResponse&lt;EmployeeDetailResponseDto&gt; | 7 | — |
| PUT /api/v1/users/{id}/roles | ApiResponse&lt;Void&gt; | 없음 → probe | — |
| POST /api/v1/users/{id}/invitation | ApiResponse&lt;RegistrationResult&gt; | 없음 → probe | — |

### record 구성요소 대조

키와 구성요소는 같은 순서이다. 원래 타입이 명확한 식은 그대로 유지했다. Object 행은 DB 열/SELECT 결과에 맞춰 구체화했다. 모든 중첩 대응과 원래 map/SELECT 위치는 R-04-evidence/*-response-map.json 및 validation-summary.json에 있다.

`OrgDepartmentResponseDto`

| JSON 키 | 구성요소 | Java 타입 | 근거 |
| --- | --- | --- | --- |
| id | id | long | departments.id BIGINT NOT NULL → number(row,"id") |
| name | name | String | departments.name VARCHAR NOT NULL |
| code | code | String | departments.code VARCHAR NOT NULL |
| parentId | parentId | Long | departments.parent_id BIGINT NULL; null 또는 Number.longValue() |
| path | path | List&lt;OrgDepartmentResponseDto.Path&gt; | 상위 부서 행을 순서대로 Path.from으로 변환한 List |
| head | head | OrgDepartmentResponseDto.Head | head_user_id가 null이면 null, 아니면 findHead 행의 id/name/position |
| fallbackHead | fallbackHead | OrgDepartmentResponseDto.FallbackHead | 상위 부서장 탐색 결과 행 또는 null; id/name/position/departmentName |
| workSchedule | workSchedule | OrgDepartmentResponseDto.WorkSchedule | findSchedule 행 + work_schedule_id == null 계산값 |
| directMemberCount | directMemberCount | long | countDepartmentMembers(id)의 COUNT(*) → long |
| childCount | childCount | long | countChildDepartments(id)의 COUNT(*) → long |

`OrgDepartmentResponseDto.FallbackHead`

| JSON 키 | 구성요소 | Java 타입 | 근거 |
| --- | --- | --- | --- |
| id | id | long | findHead의 users.id BIGINT NOT NULL → number(row,"id") |
| name | name | String | findHead의 users.user_name VARCHAR(50) NOT NULL AS name |
| position | position | String | findHead의 positions.name VARCHAR(100) NOT NULL AS position |
| departmentName | departmentName | String | departments.name VARCHAR NOT NULL, findHead SELECT 별칭 |

`OrgDepartmentResponseDto.Head`

| JSON 키 | 구성요소 | Java 타입 | 근거 |
| --- | --- | --- | --- |
| id | id | long | users.id BIGINT NOT NULL; 트리에서는 departments.head_user_id BIGINT NULL을 null 검사 후 number |
| name | name | String | users.user_name VARCHAR NOT NULL (head_name 별칭 포함) |
| position | position | String | positions.name VARCHAR NOT NULL (head_position 별칭 포함) |

`OrgDepartmentResponseDto.Path`

| JSON 키 | 구성요소 | Java 타입 | 근거 |
| --- | --- | --- | --- |
| id | id | long | departments.id BIGINT NOT NULL → number(ancestor,"id") |
| name | name | String | departments.name VARCHAR NOT NULL |

`OrgDepartmentResponseDto.WorkSchedule`

| JSON 키 | 구성요소 | Java 타입 | 근거 |
| --- | --- | --- | --- |
| id | id | long | work_schedules.id BIGINT NOT NULL → number(row,"id") |
| name | name | String | work_schedules.name VARCHAR NOT NULL |
| startTime | startTime | String | TIME_FORMAT(start_time,'%H:%i') 문자열 |
| endTime | endTime | String | TIME_FORMAT(end_time,'%H:%i') 문자열 |
| lateGraceMin | lateGraceMin | long | work_schedules.late_grace_min INT NOT NULL → number(row,"lateGraceMin") |
| useDefault | useDefault | boolean | departmentRow.get("work_schedule_id") == null 계산값(boolean) |

`OrgDepartmentTreeResponseDto`

| JSON 키 | 구성요소 | Java 타입 | 근거 |
| --- | --- | --- | --- |
| id | id | long | departments.id BIGINT NOT NULL → number(row,"id") |
| name | name | String | departments.name VARCHAR NOT NULL |
| code | code | String | departments.code VARCHAR NOT NULL |
| head | head | OrgDepartmentResponseDto.Head | 같은 findDepartments 행의 head_user_id/head_name/head_position을 Head.fromDepartment로 변환; head_user_id가 null이면 null (추가 조회 없음) |
| directMemberCount | directMemberCount | Long | members.getOrDefault(id,0L)의 Long |
| totalMemberCount | totalMemberCount | long | 기존 aggregate(id, counts, children)의 long 합계; 현재 long total = directMembers 뒤 descendant.totalMemberCount()를 더한 값 |
| children | children | List&lt;OrgDepartmentTreeResponseDto&gt; | children.getOrDefault(id,List.of())를 재귀 departmentTree로 변환한 List |

`OrgHolidayResponseDto`

| JSON 키 | 구성요소 | Java 타입 | 근거 |
| --- | --- | --- | --- |
| id | id | long | 조회: holidays.id BIGINT NOT NULL → number(row,"id"); 생성: store.insertHoliday 반환 long |
| date | date | LocalDate | 조회: holidays.holiday_date DATE NOT NULL → SqlStore LocalDate; 생성: request.date() LocalDate |
| name | name | String | 조회: holidays.name VARCHAR NOT NULL; 생성: request.name() String |
| substitute | substitute | Boolean | 조회: holidays.substitute BIT NOT NULL → 드라이버 Boolean; 생성: request.substitute() Boolean |

`OrgPositionCreateResponseDto`

| JSON 키 | 구성요소 | Java 타입 | 근거 |
| --- | --- | --- | --- |
| id | id | long | insertPosition 반환 long |
| name | name | String | 서비스 인자 name(String) |
| rankOrder | rankOrder | int | 리터럴 0(int) |

`OrgPositionResponseDto`

| JSON 키 | 구성요소 | Java 타입 | 근거 |
| --- | --- | --- | --- |
| id | id | long | positions.id BIGINT NOT NULL → number(row,"id") |
| name | name | String | positions.name VARCHAR NOT NULL |
| rankOrder | rankOrder | long | positions.rank_order INT NOT NULL → number(row,"rankOrder") |

`OrgPositionUpdateResponseDto`

| JSON 키 | 구성요소 | Java 타입 | 근거 |
| --- | --- | --- | --- |
| id | id | long | 요청 id(long) |
| name | name | String | 서비스 인자 name(String) |

`OrgWorkScheduleResponseDto`

| JSON 키 | 구성요소 | Java 타입 | 근거 |
| --- | --- | --- | --- |
| id | id | long | work_schedules.id BIGINT NOT NULL → number(row,"id") |
| name | name | String | work_schedules.name VARCHAR NOT NULL |
| startTime | startTime | String | TIME_FORMAT(start_time,'%H:%i') 문자열 |
| endTime | endTime | String | TIME_FORMAT(end_time,'%H:%i') 문자열 |
| lateGraceMin | lateGraceMin | long | work_schedules.late_grace_min INT NOT NULL → number(row,"lateGraceMin") |

`RoleDetailResponseDto`

| JSON 키 | 구성요소 | Java 타입 | 근거 |
| --- | --- | --- | --- |
| id | id | long | roles.id BIGINT NOT NULL → number(row,"id") |
| code | code | String | roles.code VARCHAR NOT NULL |
| name | name | String | roles.name VARCHAR NOT NULL |
| description | description | String | roles.description VARCHAR NULL |
| system | system | Boolean | roles.is_system BIT NOT NULL → 드라이버 Boolean |

`RoleMenuResponseDto`

| JSON 키 | 구성요소 | Java 타입 | 근거 |
| --- | --- | --- | --- |
| id | id | long | menus.id BIGINT NOT NULL → number(row,"id") |
| code | code | String | menus.code VARCHAR NOT NULL |
| name | name | String | menus.name VARCHAR NOT NULL |
| menuGroup | menuGroup | String | menus.menu_group VARCHAR NOT NULL |
| sortOrder | sortOrder | long | menus.sort_order INT NOT NULL → number(row,"sortOrder") |
| allowedActions | allowedActions | String | menus.allowed_actions VARCHAR NOT NULL |
| scopeApplicable | scopeApplicable | Boolean | menus.scope_applicable BIT NOT NULL → 드라이버 Boolean |

`RolePermissionsResponseDto`

| JSON 키 | 구성요소 | Java 타입 | 근거 |
| --- | --- | --- | --- |
| role | role | RolePermissionsResponseDto.LockedRole | findRole 행 + "CHIEF".equals(role.get("code")) boolean |
| permissions | permissions | List&lt;RolePermissionsResponseDto.Permission&gt; | findPermissions의 menus.sort_order,id 순 행을 Permission.from으로 변환 |

`RolePermissionsResponseDto.LockedRole`

| JSON 키 | 구성요소 | Java 타입 | 근거 |
| --- | --- | --- | --- |
| id | id | long | roles.id BIGINT NOT NULL → number(row,"id") |
| code | code | String | roles.code VARCHAR NOT NULL |
| name | name | String | roles.name VARCHAR NOT NULL |
| description | description | String | roles.description VARCHAR NULL |
| system | system | Boolean | roles.is_system BIT NOT NULL → 드라이버 Boolean |
| locked | locked | boolean | "CHIEF".equals(role.get("code")) 계산값(boolean) |

`RolePermissionsResponseDto.Permission`

| JSON 키 | 구성요소 | Java 타입 | 근거 |
| --- | --- | --- | --- |
| menu | menu | String | menus.code VARCHAR NOT NULL AS menu |
| view | view | boolean | bool(row,"can_view")의 기본형 boolean; SELECT COALESCE(p.can_view,0) |
| create | create | boolean | bool(row,"can_create")의 기본형 boolean; SELECT COALESCE(p.can_create,0) |
| update | update | boolean | bool(row,"can_update")의 기본형 boolean; SELECT COALESCE(p.can_update,0) |
| delete | delete | boolean | bool(row,"can_delete")의 기본형 boolean; SELECT COALESCE(p.can_delete,0) |
| scope | scope | String | role_permissions.data_scope VARCHAR(20) NULL (LEFT JOIN) |

`RoleSummaryResponseDto`

| JSON 키 | 구성요소 | Java 타입 | 근거 |
| --- | --- | --- | --- |
| id | id | long | roles.id BIGINT NOT NULL → number(row,"id") |
| code | code | String | roles.code VARCHAR NOT NULL |
| name | name | String | roles.name VARCHAR NOT NULL |
| description | description | String | roles.description VARCHAR NULL |
| system | system | Boolean | roles.is_system BIT NOT NULL → 드라이버 Boolean |
| memberCount | memberCount | long | COUNT(ur.user_id) AS memberCount → long |

`EmployeeDetailResponseDto`

| JSON 키 | 구성요소 | Java 타입 | 근거 |
| --- | --- | --- | --- |
| id | id | long | users.id BIGINT NOT NULL → number(row,"id") |
| loginId | loginId | String | users.login_id VARCHAR NOT NULL |
| name | name | String | users.user_name VARCHAR NOT NULL |
| email | email | String | users.user_email VARCHAR NOT NULL |
| department | department | EmployeeDetailResponseDto.Department | users.department_id BIGINT NOT NULL + departments.name VARCHAR INNER JOIN |
| positionId | positionId | long | users.position_id BIGINT NOT NULL → number(row,"position_id") |
| position | position | String | positions.name VARCHAR NOT NULL AS position_name |
| departmentCode | departmentCode | String | legacyDepartmentCode(department_code) String switch |
| departmentName | departmentName | String | departments.name VARCHAR NOT NULL AS department_name |
| role | role | String | legacyRole(userRoles) String: ADMIN / TEAM_LEADER / USER |
| roles | roles | List&lt;EmployeeDetailResponseDto.Role&gt; | findEmployeeRoles의 roles.id 순 행을 Role.from으로 변환한 List |
| hireDate | hireDate | LocalDate | users.hire_date DATE NULL → LocalDate |
| retiredDate | retiredDate | LocalDate | users.retired_date DATE NULL → LocalDate |
| status | status | String | users.status VARCHAR(20) NOT NULL → String |
| invitationPending | invitationPending | boolean | row.get("user_pw") == null(boolean) |
| remainingAnnualLeave | remainingAnnualLeave | BigDecimal | COALESCE(SUM(leave_ledger.days DECIMAL(8,1)),0) → BigDecimal |
| createdAt | createdAt | LocalDateTime | users.created_at DATETIME → SqlStore LocalDateTime |
| updatedAt | updatedAt | LocalDateTime | users.updated_at DATETIME → SqlStore LocalDateTime |

`EmployeeDetailResponseDto.Department`

| JSON 키 | 구성요소 | Java 타입 | 근거 |
| --- | --- | --- | --- |
| id | id | long | users.department_id BIGINT NOT NULL → number(row,"department_id") |
| name | name | String | departments.name VARCHAR NOT NULL AS department_name |

`EmployeeDetailResponseDto.Role`

| JSON 키 | 구성요소 | Java 타입 | 근거 |
| --- | --- | --- | --- |
| code | code | String | roles.code VARCHAR NOT NULL |
| name | name | String | roles.name VARCHAR NOT NULL |

`EmployeeListResponseDto`

| JSON 키 | 구성요소 | Java 타입 | 근거 |
| --- | --- | --- | --- |
| content | content | List&lt;EmployeeDetailResponseDto&gt; | getEmployeeViews(selected.ids())의 List&lt;EmployeeDetailResponseDto&gt; |
| page | page | PageResponseDto.Page | PageResponseDto.Page.of(page,size,selected.total(),content.size()) |
| statusCounts | statusCounts | Map&lt;String,Long&gt; | EmployeeRepository.SearchResult.counts Map&lt;String,Long&gt;; users.status별 COUNT(*) |

`EmployeeLoginIdPreviewResponseDto`

| JSON 키 | 구성요소 | Java 타입 | 근거 |
| --- | --- | --- | --- |
| loginId | loginId | String | String.format("%d%03d", hireDate.getYear(), next) 문자열 (기존 아이디 미리보기 식) |

`EmployeeLookupResponseDto`

| JSON 키 | 구성요소 | Java 타입 | 근거 |
| --- | --- | --- | --- |
| id | id | long | users.id BIGINT NOT NULL → number(row,"id") |
| name | name | String | users.user_name VARCHAR NOT NULL AS name |
| title | title | String | positions.name VARCHAR NOT NULL AS title |
| department | department | String | departments.name VARCHAR NOT NULL AS department |
| departmentId | departmentId | long | departments.id BIGINT NOT NULL AS departmentId → number(row,"departmentId") |

`PageResponseDto`

| JSON 키 | 구성요소 | Java 타입 | 근거 |
| --- | --- | --- | --- |
| content | content | List&lt;T&gt; | of의 List&lt;T&gt; content 인자 그대로 |
| page | page | PageResponseDto.Page | Page.of(page,size,total,content.size()) |

`PageResponseDto.Page`

| JSON 키 | 구성요소 | Java 타입 | 근거 |
| --- | --- | --- | --- |
| number | number | int | page 인자(int) |
| size | size | int | size 인자(int) |
| totalElements | totalElements | long | total 인자(long) |
| totalPages | totalPages | long | long pages = (total + size - 1) / size |
| first | first | boolean | page == 0(boolean) |
| last | last | boolean | page + 1 &gt;= pages(boolean) |
| numberOfElements | numberOfElements | int | content.size()의 int를 numberOfElements로 전달 |


### 조건부 키를 나눈 곳

- head는 id/name/position, fallbackHead는 departmentName이 추가된다. 별도 record로 나누고, 원래 자신의 head에서만 departmentName을 제거하던 효과를 보존했다.
- 부서의 workSchedule만 useDefault가 있다. 회사 기본 근무시간 GET/PUT 응답은 별도 OrgWorkScheduleResponseDto다. useDefault 조건은 서비스가 원래 department 행에서 계산하고 WorkSchedule.from에는 계산된 boolean만 전달한다.
- 역할 권한 화면 role만 locked를 포함한다. 생성·수정·목록 역할과 구분했다.
- 사원 목록의 statusCounts는 user 전용 record에 남기고, 다른 페이지 응답은 PageResponseDto로 만들었다.
- 삭제 응답은 ApiResponse.success(clock, null)로 기존 성공 message와 data 생략 규칙을 유지한다. 전역 JsonInclude와 기존 typed DTO는 바꾸지 않았다.

## 메서드 이름과 분할

| 이전 | 이후 |
| --- | --- |
| OrgController#clearHead(long) | OrgController#deleteDepartmentHead(long) |
| OrgController#create(@jakarta.validation.Valid OrgRequests.DepartmentCreate) | OrgController#createDepartment(@jakarta.validation.Valid OrgRequests.DepartmentCreate) |
| OrgController#department(long) | OrgController#getDepartment(long) |
| OrgController#head(long,@jakarta.validation.Valid OrgRequests.Head) | OrgController#updateDepartmentHead(long,@jakarta.validation.Valid OrgRequests.Head) |
| OrgController#holiday(@jakarta.validation.Valid OrgRequests.HolidayCreate) | OrgController#createHoliday(@jakarta.validation.Valid OrgRequests.HolidayCreate) |
| OrgController#holiday(long,@jakarta.validation.Valid OrgRequests.HolidayPatch) | OrgController#updateHoliday(long,@jakarta.validation.Valid OrgRequests.HolidayPatch) |
| OrgController#holidays(int) | OrgController#getHolidays(int) |
| OrgController#move(long,OrgRequests.Parent) | OrgController#updateDepartmentParent(long,OrgRequests.Parent) |
| OrgController#order(@jakarta.validation.Valid List&lt;@jakarta.validation.Valid OrgRequests.PositionOrder&gt;) | OrgController#updatePositionOrder(@jakarta.validation.Valid List&lt;@jakarta.validation.Valid OrgRequests.PositionOrder&gt;) |
| OrgController#patch(long,@jakarta.validation.Valid OrgRequests.DepartmentPatch) | OrgController#updateDepartment(long,@jakarta.validation.Valid OrgRequests.DepartmentPatch) |
| OrgController#position(@jakarta.validation.Valid OrgRequests.PositionName) | OrgController#createPosition(@jakarta.validation.Valid OrgRequests.PositionName) |
| OrgController#position(long,@jakarta.validation.Valid OrgRequests.PositionName) | OrgController#updatePosition(long,@jakarta.validation.Valid OrgRequests.PositionName) |
| OrgController#positions() | OrgController#getPositions() |
| OrgController#schedule() | OrgController#getDefaultWorkSchedule() |
| OrgController#schedule(OrgRequests.Schedule) | OrgController#updateDefaultWorkSchedule(OrgRequests.Schedule) |
| OrgController#schedule(long,OrgRequests.Schedule) | OrgController#updateDepartmentWorkSchedule(long,OrgRequests.Schedule) |
| OrgController#tree() | OrgController#getDepartmentTree() |
| OrgService#defaultSchedule() | OrgService#getDefaultWorkSchedule() |
| OrgService#defaultSchedule(OrgRequests.Schedule) | OrgService#updateDefaultWorkSchedule(OrgRequests.Schedule) |
| OrgService#departmentSchedule | OrgService#updateDepartmentSchedule |
| OrgService#detail | OrgService#getDepartment |
| OrgService#holidays | OrgService#getHolidays |
| OrgService#move | OrgService#updateDepartmentParent |
| OrgService#orderPositions | OrgService#updatePositionOrder |
| OrgService#patchDepartment | OrgService#updateDepartment |
| OrgService#patchHoliday | OrgService#updateHoliday |
| OrgService#positions | OrgService#getPositions |
| OrgService#renamePosition | OrgService#updatePosition |
| RoleController#create(@jakarta.validation.Valid RoleRequests.Create) | RoleController#createRole(@jakarta.validation.Valid RoleRequests.Create) |
| RoleController#delete(long) | RoleController#deleteRole(long) |
| RoleController#member(long,long) | RoleController#deleteRoleMember(long,long) |
| RoleController#members(long,@jakarta.validation.Valid RoleRequests.Members) | RoleController#createRoleMembers(long,@jakarta.validation.Valid RoleRequests.Members) |
| RoleController#members(long,int,int) | RoleController#getRoleMembers(long,int,int) |
| RoleController#menus() | RoleController#getMenus() |
| RoleController#patch(long,@jakarta.validation.Valid RoleRequests.Patch) | RoleController#updateRole(long,@jakarta.validation.Valid RoleRequests.Patch) |
| RoleController#permissions(long) | RoleController#getRolePermissions(long) |
| RoleController#permissions(long,@jakarta.validation.Valid List&lt;@jakarta.validation.Valid RoleRequests.Permission&gt;) | RoleController#updateRolePermissions(long,@jakarta.validation.Valid List&lt;@jakarta.validation.Valid RoleRequests.Permission&gt;) |
| RoleController#roles() | RoleController#getRoles() |
| RoleService#addMembers | RoleService#createRoleMembers |
| RoleService#create | RoleService#createRole |
| RoleService#delete | RoleService#deleteRole |
| RoleService#members | RoleService#getRoleMembers |
| RoleService#menus | RoleService#getMenus |
| RoleService#patch | RoleService#updateRole |
| RoleService#permissions(long) | RoleService#getRolePermissions(long) |
| RoleService#permissions(long,List&lt;RoleRequests.Permission&gt;) | RoleService#updateRolePermissions(long,List&lt;RoleRequests.Permission&gt;) |
| RoleService#removeMember | RoleService#deleteRoleMember |
| RoleService#roles | RoleService#getRoles |
| AuthController#setup(@jakarta.validation.Valid AuthController.SetupRequest) | AuthController#setupPassword(@jakarta.validation.Valid AuthController.SetupRequest) |
| MeController#me() | MeController#getMe() |
| MeController#password(@jakarta.validation.Valid MeController.PasswordChange) | MeController#updatePassword(@jakarta.validation.Valid MeController.PasswordChange) |
| MeController#permissions() | MeController#getPermissions() |
| UserController#create(@jakarta.validation.Valid EmployeeRequests.Create) | UserController#createEmployee(@jakarta.validation.Valid EmployeeRequests.Create) |
| UserController#detail(long) | UserController#getEmployee(long) |
| UserController#invitation(long) | UserController#createInvitation(long) |
| UserController#list(String,Long,boolean,String,int,int,String) | UserController#getEmployees(String,Long,boolean,String,int,int,String) |
| UserController#lookup(String,Long,int) | UserController#getUserLookup(String,Long,int) |
| UserController#patch(long,@jakarta.validation.Valid EmployeeRequests.Patch) | UserController#updateEmployee(long,@jakarta.validation.Valid EmployeeRequests.Patch) |
| UserController#preview(LocalDate) | UserController#getLoginIdPreview(LocalDate) |
| UserController#roles(long,@jakarta.validation.Valid EmployeeRequests.Roles) | UserController#updateEmployeeRoles(long,@jakarta.validation.Valid EmployeeRequests.Roles) |
| UserController#status(long,@jakarta.validation.Valid EmployeeRequests.Status) | UserController#updateEmployeeStatus(long,@jakarta.validation.Valid EmployeeRequests.Status) |
| EmployeeService#detail | EmployeeService#getEmployee |
| EmployeeService#list | EmployeeService#getEmployees |
| EmployeeService#preview | EmployeeService#getLoginIdPreview |
| EmployeeService#replaceRoles | EmployeeService#updateEmployeeRoles |
| EmployeeService#views | EmployeeService#getEmployeeViews |
| UserLookupService#lookup | UserLookupService#getUsers |

테스트가 부르는 EmployeeService.register/invitation/raw/patch/status, OrgService.setHead/tree, InvitationService.send/setup, AuthService.login은 보존했다. raw와 login의 반환 타입도 그대로다. q 요청 매개변수의 Java 이름을 query로 바꿀 때 @RequestParam(name="q")로 HTTP 이름을 유지했다.

| 원래 메서드·줄 수 | 분리 후·줄 수 | 이유 |
| --- | --- | --- |
| OrgService.tree 34 + aggregate 8 | tree 19 + private departmentTree 16 | 불변 record를 자식부터 만들고 인원 합산; 조회 2개는 tree 안 |
| EmployeeService.views 39 | getEmployeeViews 18 + employeeView 15 + legacyRole 5 + legacyDepartmentCode 8 | 역할·레거시 부서 코드 규칙은 private 서비스 메서드에 유지 |
| RoleService.permissions(long,List) 32 | updateRolePermissions 16 + permissionMenus 7 + validatePermissions 18 | 메뉴 행을 SQL 값과 검사에 그대로 사용; 조회→검사→삭제→삽입 순서 유지 |

줄 수는 선언부터 닫는 중괄호까지이며 annotation은 제외한다. 이름 확장과 타입 명시로 getDepartment는 23→26줄이다. 단순히 줄 수를 줄이려고 로직을 이동하지 않았다. 새 private 메서드에 @Transactional은 없다.

## 측정 전후

| 도메인 | ApiResponse&lt;?&gt; | public Map 반환 | 비 repository map/Map.of | 한 글자 이름 |
| --- | --- | --- | --- | --- |
| org | 21 → 0 | 16 → 0 | 7 → 0 | 23 → 0 |
| user | 13 → 0 | 8 → 2 | 6 → 2 | 21 → 0 |
| permission | 11 → 0 | 7 → 0 | 2 → 0 | 16 → 0 |

user의 map 2개와 public Map 2개는 명시적인 보존 지시에 따른 예외다: EmployeeService.page(쪽+중첩 page map)는 leave/notification/project/InboxRepository가 계속 사용하고, raw는 테스트의 Map 비교와 내부 권한 검사에 사용한다. 이번 세 도메인의 새 HTTP 응답 경로는 record/기존 typed DTO를 반환한다. 이 예외를 0으로 보고하지 않는다.

### org 두 글자 이름

| 이름 | 출현 수 | 이유 |
| --- | --- | --- |
| id | 51 | 도메인 식별자 |

파일·줄·메서드별 전체 위치는 validation-summary.json의 names.twoLetters에 남긴다.

### org 메서드 길이 상위 20개

| 순위 | 시작 메서드 | 줄 | 끝 메서드 | 줄 |
| --- | --- | --- | --- | --- |
| 1 | OrgService#tree() | 34 | OrgService#getDepartment(long) | 26 |
| 2 | OrgService#departmentSchedule(long,OrgRequests.Schedule) | 24 | OrgService#updateDepartmentSchedule(long,OrgRequests.Schedule) | 24 |
| 3 | OrgService#detail(long) | 23 | OrgService#tree() | 19 |
| 4 | OrgService#setHead(long,Long) | 17 | OrgService#setHead(long,Long) | 17 |
| 5 | OrgService#orderPositions(List&lt;OrgRequests.PositionOrder&gt;) | 13 | OrgService#departmentTree(Map&lt;String,Object&gt;,Map&lt;Long,List&lt;Map&lt;String,Object&gt;&gt;&gt;,Map&lt;Long,Long&gt;) | 16 |
| 6 | OrgService#createHoliday(OrgRequests.HolidayCreate) | 12 | OrgService#updatePositionOrder(List&lt;OrgRequests.PositionOrder&gt;) | 13 |
| 7 | OrgService#move(long,Long) | 11 | OrgService#createHoliday(OrgRequests.HolidayCreate) | 12 |
| 8 | OrgService#head(Object,boolean) | 10 | OrgService#updateDepartmentParent(long,Long) | 12 |
| 9 | OrgService#patchDepartment(long,OrgRequests.DepartmentPatch) | 10 | OrgService#updateDepartment(long,OrgRequests.DepartmentPatch) | 10 |
| 10 | OrgService#deleteDepartment(long) | 9 | OrgService#createDepartment(OrgRequests.DepartmentCreate) | 9 |
| 11 | OrgService#aggregate(Map&lt;String,Object&gt;) | 8 | OrgService#deleteDepartment(long) | 9 |
| 12 | OrgService#createDepartment(OrgRequests.DepartmentCreate) | 8 | OrgService#deletePosition(long) | 8 |
| 13 | OrgService#deletePosition(long) | 8 | WorkingDays#count(LocalDate,LocalDate) | 8 |
| 14 | WorkingDays#count(LocalDate,LocalDate) | 8 | OrgController#updateDepartment(long,@jakarta.validation.Valid OrgRequests.DepartmentPatch) | 7 |
| 15 | com.minierp.backend.domain.org.repository.OrgQueries#findDepartments() | 7 | OrgController#updateDepartmentHead(long,@jakarta.validation.Valid OrgRequests.Head) | 7 |
| 16 | OrgController#create(@jakarta.validation.Valid OrgRequests.DepartmentCreate) | 6 | OrgController#updateDepartmentParent(long,OrgRequests.Parent) | 7 |
| 17 | OrgController#deleteDepartment(long) | 6 | OrgController#updateDepartmentWorkSchedule(long,OrgRequests.Schedule) | 7 |
| 18 | OrgController#deleteHoliday(long) | 6 | OrgController#updateHoliday(long,@jakarta.validation.Valid OrgRequests.HolidayPatch) | 7 |
| 19 | OrgController#deletePosition(long) | 6 | OrgController#updatePosition(long,@jakarta.validation.Valid OrgRequests.PositionName) | 7 |
| 20 | OrgController#holiday(@jakarta.validation.Valid OrgRequests.HolidayCreate) | 6 | com.minierp.backend.domain.org.repository.OrgQueries#findDepartments() | 7 |

### user 두 글자 이름

| 이름 | 출현 수 | 이유 |
| --- | --- | --- |
| id | 29 | 도메인 식별자 |

파일·줄·메서드별 전체 위치는 validation-summary.json의 names.twoLetters에 남긴다.

### user 메서드 길이 상위 20개

| 순위 | 시작 메서드 | 줄 | 끝 메서드 | 줄 |
| --- | --- | --- | --- | --- |
| 1 | EmployeeService#list(String,Long,boolean,String,int,int,String) | 39 | EmployeeService#getEmployees(String,Long,boolean,String,int,int,String) | 39 |
| 2 | EmployeeService#views(List&lt;Long&gt;) | 39 | com.minierp.backend.domain.user.repository.EmployeeRepository#search(com.minierp.backend.global.permission.ScopeFilter,String,List&lt;Long&gt;,String,int,int,String) | 38 |
| 3 | com.minierp.backend.domain.user.repository.EmployeeRepository#search(com.minierp.backend.global.permission.ScopeFilter,String,List&lt;Long&gt;,String,int,int,String) | 38 | EmployeeService#patch(long,EmployeeRequests.Patch) | 35 |
| 4 | EmployeeService#patch(long,EmployeeRequests.Patch) | 34 | EmployeeService#register(EmployeeRequests.Create) | 31 |
| 5 | EmployeeService#register(EmployeeRequests.Create) | 31 | InitialAdminInitializer#run(org.springframework.boot.ApplicationArguments) | 30 |
| 6 | InitialAdminInitializer#run(org.springframework.boot.ApplicationArguments) | 30 | InvitationService#send(EmployeeService.InvitationRequested) | 26 |
| 7 | InvitationService#send(EmployeeService.InvitationRequested) | 26 | AuthService#confirmPasswordReset(PasswordResetConfirmDto) | 21 |
| 8 | AuthService#confirmPasswordReset(PasswordResetConfirmDto) | 21 | com.minierp.backend.domain.user.entity.User#updateProfile(String,String,String) | 19 |
| 9 | com.minierp.backend.domain.user.entity.User#updateProfile(String,String,String) | 19 | EmployeeService#status(long,com.minierp.backend.domain.user.entity.UserStatus) | 19 |
| 10 | EmployeeService#status(long,com.minierp.backend.domain.user.entity.UserStatus) | 19 | EmployeeService#getEmployeeViews(List&lt;Long&gt;) | 18 |
| 11 | AuthService#changePassword(Long,String,String) | 17 | AuthService#changePassword(Long,String,String) | 17 |
| 12 | com.minierp.backend.domain.user.repository.EmployeeStore#insertEmployee(String,String,String,Long,Long,LocalDate,LocalDateTime) | 16 | com.minierp.backend.domain.user.repository.EmployeeStore#insertEmployee(String,String,String,Long,Long,LocalDate,LocalDateTime) | 16 |
| 13 | AuthService#verifyPasswordReset(PasswordResetVerifyDto) | 16 | AuthService#verifyPasswordReset(PasswordResetVerifyDto) | 16 |
| 14 | com.minierp.backend.domain.user.repository.EmployeeQueries#findActiveUsers(int,String,Long) | 15 | com.minierp.backend.domain.user.repository.EmployeeQueries#findActiveUsers(int,String,Long) | 15 |
| 15 | com.minierp.backend.domain.user.entity.User#resolveInitialAnnualLeave(String) | 14 | EmployeeService#employeeView(Map&lt;String,Object&gt;,long,List&lt;EmployeeDetailResponseDto.Role&gt;) | 15 |
| 16 | AuthService#login(LoginRequestDto) | 14 | com.minierp.backend.domain.user.entity.User#resolveInitialAnnualLeave(String) | 14 |
| 17 | AuthService#requestPasswordReset(PasswordResetRequestDto) | 14 | AuthService#login(LoginRequestDto) | 14 |
| 18 | InvitationService#setup(String,String) | 14 | AuthService#requestPasswordReset(PasswordResetRequestDto) | 14 |
| 19 | com.minierp.backend.domain.user.repository.EmployeeStore#updateEmployee(long,String,String,Long,Long,LocalDate,LocalDateTime) | 13 | InvitationService#setup(String,String) | 14 |
| 20 | EmployeeService#replaceRoles(long,List&lt;String&gt;) | 13 | UserController#getEmployees(String,Long,boolean,String,int,int,String) | 13 |

### permission 두 글자 이름

| 이름 | 출현 수 | 이유 |
| --- | --- | --- |
| id | 27 | 도메인 식별자 |

파일·줄·메서드별 전체 위치는 validation-summary.json의 names.twoLetters에 남긴다.

### permission 메서드 길이 상위 20개

| 순위 | 시작 메서드 | 줄 | 끝 메서드 | 줄 |
| --- | --- | --- | --- | --- |
| 1 | RoleService#permissions(long,List&lt;RoleRequests.Permission&gt;) | 32 | RoleService#validatePermissions(Map&lt;String,Map&lt;String,Object&gt;&gt;,List&lt;RoleRequests.Permission&gt;) | 18 |
| 2 | RoleService#removeMember(long,long) | 16 | RoleService#deleteRoleMember(long,long) | 16 |
| 3 | RoleService#delete(long) | 13 | RoleService#updateRolePermissions(long,List&lt;RoleRequests.Permission&gt;) | 16 |
| 4 | RoleService#patch(long,RoleRequests.Patch) | 12 | com.minierp.backend.domain.permission.repository.RoleStore#insertPermission(long,Object,boolean,boolean,boolean,boolean,String) | 13 |
| 5 | RoleService#permissions(long) | 12 | RoleService#deleteRole(long) | 13 |
| 6 | RoleService#addMembers(long,List&lt;Long&gt;) | 11 | RoleService#updateRole(long,RoleRequests.Patch) | 12 |
| 7 | RoleController#members(long,int,int) | 8 | RoleService#createRoleMembers(long,List&lt;Long&gt;) | 11 |
| 8 | RoleService#members(long,int,int) | 8 | RoleService#getRolePermissions(long) | 10 |
| 9 | RoleController#create(@jakarta.validation.Valid RoleRequests.Create) | 6 | RoleController#getRoleMembers(long,int,int) | 8 |
| 10 | RoleController#delete(long) | 6 | RoleService#getRoleMembers(long,int,int) | 8 |
| 11 | RoleController#member(long,long) | 6 | RoleController#updateRolePermissions(long,@jakarta.validation.Valid List&lt;@jakarta.validation.Valid RoleRequests.Permission&gt;) | 7 |
| 12 | RoleController#members(long,@jakarta.validation.Valid RoleRequests.Members) | 6 | RoleService#permissionMenus() | 7 |
| 13 | RoleService#create(RoleRequests.Create) | 6 | RoleController#createRole(@jakarta.validation.Valid RoleRequests.Create) | 6 |
| 14 | RoleController#menus() | 5 | RoleController#createRoleMembers(long,@jakarta.validation.Valid RoleRequests.Members) | 6 |
| 15 | RoleController#patch(long,@jakarta.validation.Valid RoleRequests.Patch) | 5 | RoleController#deleteRole(long) | 6 |
| 16 | RoleController#permissions(long) | 5 | RoleController#deleteRoleMember(long,long) | 6 |
| 17 | RoleController#permissions(long,@jakarta.validation.Valid List&lt;@jakarta.validation.Valid RoleRequests.Permission&gt;) | 5 | RoleService#createRole(RoleRequests.Create) | 6 |
| 18 | RoleController#roles() | 5 | RoleController#getMenus() | 5 |
| 19 | com.minierp.backend.domain.permission.repository.RoleQueries#findMenus() | 5 | RoleController#getRolePermissions(long) | 5 |
| 20 | com.minierp.backend.domain.permission.repository.RoleQueries#findPermissions(long) | 5 | RoleController#getRoles() | 5 |

## 남긴 것

MeController의 repository·권한 빈 직접 호출, 사용하지 않는 UserListResponseDto/UserRoleUpdateResponseDto/UserUpdateRequestDto/UserRoleUpdateRequestDto, RegistrationResult 클래스, AttendanceStore.time와 ProjectStateStore.recalculate의 3단계 예외는 그대로 두었다. 응답에 사용한 행을 검사/SQL 인자로 다시 쓰는 곳은 원래 Map과 행 값의 실행 중 타입을 유지한다.

### org 남은 행 Map

| 메서드 | 변수/매개변수 | 남긴 이유 |
| --- | --- | --- |
| OrgDepartmentResponseDto.FallbackHead#from(Map&lt;String,Object&gt;) | row | 행→record 변환 입력 |
| OrgDepartmentResponseDto.Head#from(Map&lt;String,Object&gt;) | row | 행→record 변환 입력 |
| OrgDepartmentResponseDto.Head#fromDepartment(Map&lt;String,Object&gt;) | row | 행→record 변환 입력 |
| OrgDepartmentResponseDto.Path#from(Map&lt;String,Object&gt;) | row | 행→record 변환 입력 |
| OrgDepartmentResponseDto.WorkSchedule#from(Map&lt;String,Object&gt;,boolean) | row | 행→record 변환 입력 |
| OrgHolidayResponseDto#from(Map&lt;String,Object&gt;) | row | 행→record 변환 입력 |
| OrgPositionResponseDto#from(Map&lt;String,Object&gt;) | row | 행→record 변환 입력 |
| OrgWorkScheduleResponseDto#from(Map&lt;String,Object&gt;) | row | 행→record 변환 입력 |
| OrgService#departmentTree(Map&lt;String,Object&gt;,Map&lt;Long,List&lt;Map&lt;String,Object&gt;&gt;&gt;,Map&lt;Long,Long&gt;) | child, children, row | 조회·검사·SQL 값/트리 구성용 내부 행; HTTP 응답 map 아님 |
| OrgService#getDepartment(long) | ancestor, departmentRow, schedule | 조회·검사·SQL 값/트리 구성용 내부 행; HTTP 응답 map 아님 |
| OrgService#setHead(long,Long) | user | 조회·검사·SQL 값/트리 구성용 내부 행; HTTP 응답 map 아님 |
| OrgService#tree() | children, counts, departments, root, row | 조회·검사·SQL 값/트리 구성용 내부 행; HTTP 응답 map 아님 |
| OrgService#updateDepartmentParent(long,Long) | departmentRow | 조회·검사·SQL 값/트리 구성용 내부 행; HTTP 응답 map 아님 |
| OrgService#updateDepartmentSchedule(long,OrgRequests.Schedule) | departmentRow | work_schedule_id로 근무시간 생성/수정 경로와 SQL 값을 정하는 부서 행 |
| WorkingDays#count(LocalDate,LocalDate) | row | 조회·검사·SQL 값/트리 구성용 내부 행; HTTP 응답 map 아님 |

### user 남은 행 Map

| 메서드 | 변수/매개변수 | 남긴 이유 |
| --- | --- | --- |
| EmployeeDetailResponseDto.Department#from(Map&lt;String,Object&gt;) | row | 행→record 변환 입력 |
| EmployeeDetailResponseDto.Role#from(Map&lt;String,Object&gt;) | row | 행→record 변환 입력 |
| EmployeeLookupResponseDto#from(Map&lt;String,Object&gt;) | row | 행→record 변환 입력 |
| EmployeeService#employeeView(Map&lt;String,Object&gt;,long,List&lt;EmployeeDetailResponseDto.Role&gt;) | row | 행→record 변환 입력 |
| EmployeeService#getEmployeeViews(List&lt;Long&gt;) | row, rows | 조회·검사·SQL 값/트리 구성용 내부 행; HTTP 응답 map 아님 |
| EmployeeService#getLoginIdPreview(LocalDate) | rows | 조회·검사·SQL 값/트리 구성용 내부 행; HTTP 응답 map 아님 |
| EmployeeService#invitation(long) | user | 조회·검사·SQL 값/트리 구성용 내부 행; HTTP 응답 map 아님 |
| EmployeeService#patch(long,EmployeeRequests.Patch) | old | 이메일 변경·부서 이동 검사 및 변경 전 SQL 값에 쓰는 사원 행 |
| EmployeeService#protectLastChief(long) | chiefs, row | 조회·검사·SQL 값/트리 구성용 내부 행; HTTP 응답 map 아님 |
| EmployeeService#register(EmployeeRequests.Create) | position | 직급 존재 확인에만 쓰며 응답 변환 입력이 아님 |
| EmployeeService#roleIds(List&lt;String&gt;) | row, rows | 조회·검사·SQL 값/트리 구성용 내부 행; HTTP 응답 map 아님 |
| EmployeeService#status(long,com.minierp.backend.domain.user.entity.UserStatus) | user | 조회·검사·SQL 값/트리 구성용 내부 행; HTTP 응답 map 아님 |

### permission 남은 행 Map

| 메서드 | 변수/매개변수 | 남긴 이유 |
| --- | --- | --- |
| RoleDetailResponseDto#from(Map&lt;String,Object&gt;) | row | 행→record 변환 입력 |
| RoleMenuResponseDto#from(Map&lt;String,Object&gt;) | row | 행→record 변환 입력 |
| RolePermissionsResponseDto.Permission#from(Map&lt;String,Object&gt;) | row | 행→record 변환 입력 |
| RoleSummaryResponseDto#from(Map&lt;String,Object&gt;) | row | 행→record 변환 입력 |
| RoleService#deleteRole(long) | role | 조회·검사·SQL 값/트리 구성용 내부 행; HTTP 응답 map 아님 |
| RoleService#deleteRoleMember(long,long) | role | 조회·검사·SQL 값/트리 구성용 내부 행; HTTP 응답 map 아님 |
| RoleService#getRoleMembers(long,int,int) | row | 조회·검사·SQL 값/트리 구성용 내부 행; HTTP 응답 map 아님 |
| RoleService#getRolePermissions(long) | role, rows | 조회·검사·SQL 값/트리 구성용 내부 행; HTTP 응답 map 아님 |
| RoleService#permissionMenus() | menu, menus | 조회·검사·SQL 값/트리 구성용 내부 행; HTTP 응답 map 아님 |
| RoleService#updateRole(long,RoleRequests.Patch) | role | system 역할 수정 금지 검사에 쓰는 역할 행 |
| RoleService#updateRolePermissions(long,List&lt;RoleRequests.Permission&gt;) | menus, role | system 역할 수정 금지 검사에 쓰는 역할 행 |
| RoleService#validatePermissions(Map&lt;String,Map&lt;String,Object&gt;&gt;,List&lt;RoleRequests.Permission&gt;) | menu, menus | allowedActions·scopeApplicable 검사에 쓰는 메뉴 행 |

## 재실행 명령

작업 트리 밖에 시작/후보 archive를 풀고 각각 test-compile한다. 작업 트리에서 clean은 하지 않는다. 아래 BASE/CAND/OUT/CP는 재실행자가 만든 임시 경로이며 CP는 기록 폴더의 classpath.txt 파일이다.

```bash
export JAVA_HOME=/Users/kimmini/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home
./mvnw -q -Duser.timezone=UTC test-compile
./mvnw -Duser.timezone=UTC -Drefactoring.record="$OUT/recording" test
./mvnw -Duser.timezone=UTC test
"$JAVA_HOME/bin/java" -cp "$(cat "$OUT/recording/classpath.txt")" \
  com.minierp.backend.support.refactoring.SnapshotComparator \
  src/test/resources/refactoring-baseline "$OUT/recording"
```

저장소 루트에서 읽기 전용 측정:

```bash
python3 docs/refactoring/R-04-sql-literal-check.py --before "$BASE" --after "$CAND" --output "$OUT/sql.json"
python3 docs/refactoring/R-04-unused-imports.py --root "$CAND" --classpath "$CP" --output "$OUT/imports.json"
python3 docs/refactoring/R-04-names.py --root "$CAND" --classpath "$CP" --output "$OUT/names.json"
python3 docs/refactoring/R-03-sql-location-measure.py --root "$CAND" --output "$OUT/location.json"
python3 docs/refactoring/R-02-format-measure.py --root "$CAND" --output "$OUT/format.json"
python3 docs/refactoring/R-03-format-units.py --root "$CAND" --output "$OUT/units.json"
python3 docs/refactoring/R-04-verify-tools.py --start "$BASE" --original "$ORIGINAL" --classpath "$CP" --output "$OUT/proof.json"
```

실행 순서는 해당 묶음의 *-renames.json 각 항목을 `--rename old=new`로 넘긴다. 최종 permission 대응표는 org/user 변경도 누적 포함하므로 세 도메인을 모두 선택한다. 응답 대조는 org/user/permission-response-map.json 배열을 합친 임시 JSON을 --mapping으로 주고 세 도메인을 선택한다. @Transactional은 R-03 모듈의 `compare(scan(BASE), scan(CAND), {}, {}, transactionRenames)["sameTransactions"]`로 확인한다. 전체 리터럴 증감 검사와 분리하며 raw signature 대응표는 *-transaction-renames.json에 있다.

임시 probe는 docs/refactoring/R-04-probe/R04ContractProbe.java를 각 archive의 backend/src/test/java/com/minierp/backend/integration/에 복사하고 `-Dtest=R04ContractProbe#orgSuccessAndNonNullShapes`(user/permission 메서드도 각각)와 기록 옵션으로 실행한다. 시작 probe 기록과 후보 probe 기록을 위 비교기로 대조한다.

## 한계·발견했지만 고치지 않은 것

- 새로 재현한 운영 버그는 없다. 이번 작업은 기존 응답과 실행 순서를 보존한다.
- 기존 typed Auth API 네 개의 성공 응답은 기준선에 없고 이번 16개 probe 범위에서도 제외했다. 그 DTO/본문 로직은 그대로이며 제네릭·메서드/매개변수 이름만 다룬다.
- 정적 사건 비교는 업무 조건식 자체의 논리 동등성을 증명하지 않는다. 조건 경계·효과 순서를 비교하고, 실제 SQL/응답 기준선과 별도 API probe를 함께 사용한다. 재귀는 수동 확인을 병행했다.
- DB 제약 밖의 손상된 데이터, 무한 부서 깊이, 실제 운영 성능/외부 메일/실서버는 시험하지 않았다. 이 단계에서 스키마·권한·버그를 바꾸지 않는다.
- 기록 ON은 기존 v2 순서 고정 정책을 그대로 사용한다. 각 커밋 OFF 전체 실행으로 원래 동시성 경로도 확인한다. 시간은 컨테이너 기동과 동시 실행 부하를 포함한 참고치다.


마지막 문서 커밋도 별도 Git archive에서 Java 17/UTC `test-compile`을 확인했다(exit 0). 실행 결과는 작업 트리의 `backend/target/r04-group1-validation/docs/result.json`에 남긴다.

### 묶음 2 준비에서 반영한 리뷰 보완

구성요소 근거를 실제 SELECT 열·스키마 타입·원래 식으로 바로잡았다. LockedRole.from에는 행과 서비스에서 계산한 locked만 넘긴다. OrgService.updateDepartmentSchedule은 updateDepartmentWorkSchedule로, 사용자 조회 두 메서드는 getLookupUsers로 맞췄다. 순서 경고 목록은 승인된 M4LegacyExperimentTest#unlockedWholeListRecalculationReproducesWriteSkewTenTimes()를 더해 14개로 적용한다. barrier가 UPDATE 뒤에 있어 격리수준 조회와 UPDATE의 순서가 섞이는 근거는 기존 new-warning-analysis.json에 있다.
