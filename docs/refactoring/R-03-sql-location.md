# R-03 SQL 위치 이동 — 묶음 1

## 범위와 기준

- 브랜치: `refactor/backend-readability`, 별도 작업 트리에서 진행. 묶음 1 `org · user · permission`까지만이며 묶음 2·3 및 응답 record 도입은 미착수.
- 모든 정적 비교 기준: `952a64e2fabaeb49590426d594b2f900669ba8cf`.
- develop `c550f893753dc0ae7da083a3e2527d3c5aa79fb8` 및 명세 17 12행의 6차 개정을 확인했다. merge-tree 사전 검사와 실제 병합 차이 모두 `backend/md/17-refactoring.md` 1개.
- 새 클래스는 `@Repository`·`@RequiredArgsConstructor`. 잠금 조회는 Store, 값 계산·권한·유효성 검사·결과 가공·트랜잭션 경계는 기존 서비스에 둔다.
- SQL 값·파라미터 이름·동적 연결 순서, API·오류 응답·스키마·격리 수준·잠금 순서는 유지한다. 테스트 및 안전망 파일은 수정하지 않았다.

## 커밋과 검증

| 커밋 | 범위 | 변경 파일 | 검증 |
|---|---|---:|---|
| `5dc7396` | 명세 17 develop 병합 | 1 | 별도 archive test-compile 0, 8.70초 |
| `f67f42e` | R-02 남은 모양 기록 | 1 | 별도 archive test-compile 0, 7.45초 |
| `8938288` | org SQL 이동 | 4 | 아래 (a)~(f) |
| `ccd31f0` | user SQL 이동 | 4 | 아래 (a)~(f) |
| `08bfc66` | permission SQL 이동 | 3 | 아래 (a)~(f) |
| 이 문서 커밋 | R-03 보고서·읽기 전용 측정 스크립트 | 2 | Java 변경 없음, 별도 archive test-compile 종료 0 |

### (a)~(c) 적용 전 정적 검사

후보는 작업 트리 밖 `/private/tmp/r03-group1/`에서 만들었다. javac `LiteralTree.getValue()`로 문자열·텍스트 블록의 처리된 값을 읽었다. SQL 지문처럼 공백을 압축하지 않는다. 트랜잭션은 클래스 수준과 메서드 수준을 함께 검사했다.

| 후보 | 문자열 출현 수 | 서로 다른 값 | 새 값 / 사라진 값 | 트랜잭션 목록 | 새 repository throw / Transactional / valueOf·parse |
|---|---:|---:|---|---|---|
| 시작 | 3239 | 1314 | — | 83 | — |
| org 누적 | 3235 | 1314 | 0 / 0 | 83개 동일 | 0 / 0 / 0 |
| user 누적 | 3227 | 1314 | 0 / 0 | 83개 동일 | 0 / 0 / 0 |
| permission 누적 | 3220 | 1314 | 0 / 0 | 83개 동일 | 0 / 0 / 0 |

출현 감소 19개는 아래 9개 중복 SQL 제거 및 해당 map 키 10개 제거와 정확히 대응한다. 증가한 출현은 0개다. SQL 원문 식 87곳은 리터럴 원문을 포함한 토큰열도 대조했으며 모두 같았다. 일반 호출 84곳은 서비스에 남은 값 표현식과 평가 순서도 원래 map 인자 순서와 같다. 나머지는 근무시간 공용 파라미터 2곳과 접두어 검색 1곳이며 아래에서 따로 설명한다.

### (d)·(e) 전체 UTC 실행

Java 17, MariaDB 10.11 Testcontainers. 로컬 개발 DB·Redis와 실제 서버는 사용하지 않았다. 기존 231개 테스트와 기대값을 그대로 사용했다. org는 ON/OFF를 순차 실행했다. user/permission은 같은 staged tree의 별도 archive에서 OFF를, 작업 트리에서 ON을 서로 다른 JVM·Testcontainers로 동시에 실행했다. JUnit 테스트 자체의 병렬 설정은 바꾸지 않았고 target/컨테이너/포트를 공유하지 않는다. 초 단위 시간은 전체 Maven 프로세스의 wall time이며 동시 실행의 자원 경합이 포함될 수 있어 성능 개선 수치가 아니다.

| 커밋 묶음 | 기록 ON 성공/실패/오류/건너뜀 | ON 시간 | 기준선 비교 | 기록 OFF 성공/실패/오류/건너뜀 | OFF 시간 | OFF 기록 출력 |
|---|---|---:|---|---|---:|---:|
| org | 231/0/0/0 | 528.5초 | REFACTORING_COMPARE differences=0 sqlOrderChanges=9 | 231/0/0/0 | 491.12초 | 0 |
| user | 231/0/0/0 | 998.86초 | REFACTORING_COMPARE differences=0 sqlOrderChanges=10 | 231/0/0/0 | 1005.23초 | 0 |
| permission | 231/0/0/0 | 1068.3초 | REFACTORING_COMPARE differences=0 sqlOrderChanges=12 | 231/0/0/0 | 1079.03초 | 0 |

각 비교는 SQL별 횟수·workerSql 표시·순서 규칙 및 응답 JSON을 함께 비교했다. 응답 921건 중 4xx·5xx 191건(400/401/403/404/409/422/500)도 별도 다중집합 대조했으며 차이는 모두 0이다. 기준선/정책/비교기와 마스킹을 수정하지 않았다. 실행 증거는 로컬 `backend/target/r03-group1-validation/{org,user,permission}/`의 `result.json`, `on.log`, `off.log`, `compare.log`, `recording/`에 있다.

순서 경고 프레임(기존 13개 허용 범위와 대조):

경고 합집합 12개이며 기존 13개 밖의 새 프레임은 0개다.

- org: 9개
  - `M1AcceptanceTest#concurrentIdAllocationIsUniqueAndUnsafeMaxPlusOneCollides()`
  - `M2DocumentIntegrationTest#concurrentApprovalHasOne409AndNoNotificationFromLosingRequestInTenTrials()`
  - `M2DocumentIntegrationTest#documentNumbersAreUniqueForEightDifferentDraftersSubmittingConcurrently()`
  - `M2LegacyApprovalRaceTest#unconditionalLegacyApprovalAcceptsBothRequestsInTenControlledRaces()`
  - `M3AttendanceTest#concurrentClockInHasExactlyOneWinnerInTenTrials()`
  - `M3LeaveAdjustmentConcurrencyTest#unlockedModelFailsThenLockedAdjustmentsAndSubmissionsPreserveBalance()`
  - `M3LeaveGrantTest#twoConcurrentBatchRunsIssueEachKeyOnlyOnceInTenTrials()`
  - `M4BoardConcurrencyTest#tenConcurrentCreatesHaveContiguousUniqueNumbers()`
  - `M4OverdueTest#concurrentRunsSendOnlyOnce()`
- user: 10개
  - `M1AcceptanceTest#concurrentIdAllocationIsUniqueAndUnsafeMaxPlusOneCollides()`
  - `M2DocumentIntegrationTest#concurrentApprovalAndWithdrawalHaveOnlyOneWinner()`
  - `M2DocumentIntegrationTest#concurrentApprovalHasOne409AndNoNotificationFromLosingRequestInTenTrials()`
  - `M2DocumentIntegrationTest#documentNumbersAreUniqueForEightDifferentDraftersSubmittingConcurrently()`
  - `M2LegacyApprovalRaceTest#unconditionalLegacyApprovalAcceptsBothRequestsInTenControlledRaces()`
  - `M3AttendanceTest#concurrentClockInHasExactlyOneWinnerInTenTrials()`
  - `M3LeaveAdjustmentConcurrencyTest#unlockedModelFailsThenLockedAdjustmentsAndSubmissionsPreserveBalance()`
  - `M3LeaveGrantTest#twoConcurrentBatchRunsIssueEachKeyOnlyOnceInTenTrials()`
  - `M4BoardConcurrencyTest#tenConcurrentCreatesHaveContiguousUniqueNumbers()`
  - `M4OverdueTest#concurrentRunsSendOnlyOnce()`
- permission: 12개
  - `M1AcceptanceTest#concurrentIdAllocationIsUniqueAndUnsafeMaxPlusOneCollides()`
  - `M2DocumentIntegrationTest#concurrentApprovalAndWithdrawalHaveOnlyOneWinner()`
  - `M2DocumentIntegrationTest#concurrentApprovalHasOne409AndNoNotificationFromLosingRequestInTenTrials()`
  - `M2DocumentIntegrationTest#documentNumbersAreUniqueForEightDifferentDraftersSubmittingConcurrently()`
  - `M2LegacyApprovalRaceTest#unconditionalLegacyApprovalAcceptsBothRequestsInTenControlledRaces()`
  - `M3AttendanceTest#concurrentClockInHasExactlyOneWinnerInTenTrials()`
  - `M3LeaveAdjustmentConcurrencyTest#unlockedModelFailsThenLockedAdjustmentsAndSubmissionsPreserveBalance()`
  - `M3LeaveGrantTest#twoConcurrentBatchRunsIssueEachKeyOnlyOnceInTenTrials()`
  - `M4BoardConcurrencyTest#gapsUpdateOneRowSerializeTwentyMovesAndRebalanceOnlyWhenExhausted()`
  - `M4BoardConcurrencyTest#tenConcurrentCreatesHaveContiguousUniqueNumbers()`
  - `M4OverdueTest#concurrentRunsSendOnlyOnce()`
  - `M5ProductionTest#<before-all>`

조직 예비 실행은 새 Store의 직급 정렬 ID가 `Long`에서 `long`으로 불필요하게 언박싱된 것을 검토 중 발견해 중단했다. `Long`을 유지하도록 적용 전 후보 검사부터 다시 했고, 중단한 실행을 완료 증거로 쓰지 않았다. 표에는 보완 후 전체 실행만 포함한다.

### (f) 형식

R-02 측정기로 매 후보를 검사했다. 120자 초과(리터럴 예외 제외)·여러 문장·여러 선언자·짧은 호출 분할·조각 분할·분리된 어노테이션·angle line ends는 main/test 모두 0이다.

| 항목(main/test) | 시작 | org | user | permission |
|---|---|---|---|
| 리터럴 예외 | 88/78 | 88/78 | 88/78 | 88/78 |
| 이름·멤버 끊김(보호 예외) | 1/0 | 1/0 | 1/0 | 1/0 |
| 키·값 분리(기존 예외) | 4/8 | 4/8 | 3/8 | 3/8 |
| 바깥 연산자(기존 예외) | 2/1 | 2/1 | 1/1 | 1/1 |
| 보호된 텍스트 블록 조각 | 5/1 | 5/1 | 4/1 | 4/1 |
| 삼항 배치(기존 예외) | 0/1 | 0/1 | 0/1 | 0/1 |

새 형식 예외를 추가하지 않았다. 사원 INSERT 파라미터 이동으로 기존 텍스트 블록 닫는 줄에 묶였던 예외가 줄었다. 아래 부록에 최종 위치를 갱신한다.

## 만든 클래스와 메서드

원래 위치는 모두 `952a64e` 기준이며, `backend/src/main/java/com/minierp/backend/domain/` 아래 경로다. SQL 호출은 반환 타입을 바꾸지 않았다. `rows`의 stream·number·응답 map 처리는 서비스에 남아 있다.

### org/repository/OrgQueries.java

| 메서드 | 원래 위치 | SQL 요약 | 반환 타입 |
|---|---|---|---|
| `findDepartment` | org/service/OrgService.java:24 | 부서 단건 존재/상세 | `Map<String, Object>` |
| `findDepartments` | org/service/OrgService.java:33 | 부서·부서장·직급 트리 행 | `List<Map<String, Object>>` |
| `findDepartmentMemberCounts` | org/service/OrgService.java:38 | 부서별 사원 수 | `List<Map<String, Object>>` |
| `findHead` | org/service/OrgService.java:88 | 부서장 표시 정보 | `Map<String, Object>` |
| `countDepartmentMembers` | org/service/OrgService.java:117, org/service/OrgService.java:180 | 부서 소속 사원 수 | `long` |
| `countChildDepartments` | org/service/OrgService.java:118, org/service/OrgService.java:181 | 직속 하위 부서 수 | `long` |
| `findSchedule` | org/service/OrgService.java:188 | 근무시간 표시 정보 | `Map<String, Object>` |
| `findDefaultSchedule` | org/service/OrgService.java:196 | 기본 근무시간 ID | `Map<String, Object>` |
| `findPositions` | org/service/OrgService.java:244 | 직급 순서 목록 | `List<Map<String, Object>>` |
| `findPosition` | org/service/OrgService.java:255 | 직급 존재 확인 | `Map<String, Object>` |
| `countPositionMembers` | org/service/OrgService.java:278 | 해당 직급 사원 수 | `long` |
| `findHolidays` | org/service/OrgService.java:285 | 연도 구간 공휴일 목록 | `List<Map<String, Object>>` |
| `countHolidaysOnDate` | org/service/OrgService.java:292 | 같은 날짜 공휴일 수 | `long` |
| `findHolidayIdentity` | org/service/OrgService.java:306 | 공휴일 존재 확인 | `Map<String, Object>` |
| `findHoliday` | org/service/OrgService.java:309 | 공휴일 상세 | `Map<String, Object>` |
| `findHolidayDates` | org/service/WorkingDays.java:16 | 휴가 일수 계산 구간 공휴일 | `List<Map<String, Object>>` |

### org/repository/OrgStore.java

| 메서드 | 원래 위치 | SQL 요약 | 반환 타입 |
|---|---|---|---|
| `insertDepartment` | org/service/OrgService.java:125 | 부서 등록 | `long` |
| `updateDepartmentCode` | org/service/OrgService.java:129 | 생성 부서 코드 확정 | `int` |
| `updateDepartment` | org/service/OrgService.java:140 | 부서 이름·순서 수정 | `int` |
| `updateDepartmentParent` | org/service/OrgService.java:153 | 상위 부서 변경 | `int` |
| `lockHeadCandidate` | org/service/OrgService.java:162 | 부서장 후보 사원 잠금 | `Map<String, Object>` |
| `updateDepartmentHead` | org/service/OrgService.java:172 | 부서장 지정 | `int` |
| `deleteDepartment` | org/service/OrgService.java:184 | 부서 삭제 | `int` |
| `updateDefaultSchedule` | org/service/OrgService.java:210 | 기본 근무시간 수정 | `int` |
| `updateDepartmentToDefaultSchedule` | org/service/OrgService.java:220 | 부서별 근무시간 연결 해제 | `int` |
| `updateDepartmentSchedule` | org/service/OrgService.java:237 | 부서의 근무시간 연결 | `int` |
| `insertPosition` | org/service/OrgService.java:249 | 직급 등록 | `long` |
| `updatePositionName` | org/service/OrgService.java:256 | 직급명 수정 | `int` |
| `updatePositionOrder` | org/service/OrgService.java:267 | 직급 순서 수정 | `int` |
| `lockPosition` | org/service/OrgService.java:277 | 삭제 대상 직급 잠금 | `Map<String, Object>` |
| `deletePosition` | org/service/OrgService.java:281 | 직급 삭제 | `int` |
| `insertHoliday` | org/service/OrgService.java:296 | 공휴일 등록 | `long` |
| `updateHoliday` | org/service/OrgService.java:307 | 공휴일 이름·대체 여부 수정 | `int` |
| `deleteHoliday` | org/service/OrgService.java:315 | 공휴일 삭제 | `int` |
| `insertWorkSchedule` | org/service/OrgService.java:228 | 부서별 근무시간 등록 | `long` |
| `updateWorkSchedule` | org/service/OrgService.java:233 | 부서별 근무시간 수정 | `int` |

### user/repository/EmployeeQueries.java

| 메서드 | 원래 위치 | SQL 요약 | 반환 타입 |
|---|---|---|---|
| `findUser` | user/service/EmployeeService.java:35 | 사원 원본 행 | `Map<String, Object>` |
| `countPrivilegedRoles` | user/service/EmployeeService.java:58 | EMPLOYEE 외 역할 수 | `long` |
| `findEmployeeViews` | user/service/EmployeeService.java:69 | 사원 표시 정보·연차 합계 | `List<Map<String, Object>>` |
| `findEmployeeRoles` | user/service/EmployeeService.java:75 | 사원별 역할 목록 | `List<Map<String, Object>>` |
| `findDepartment` | user/service/EmployeeService.java:142, user/service/EmployeeService.java:194, user/service/EmployeeService.java:246 | 부서 단건 존재/상세 | `Map<String, Object>` |
| `findLoginSequence` | user/service/EmployeeService.java:169 | 입사연도 번호 미리보기 | `List<Map<String, Object>>` |
| `findRolesByCodes` | user/service/EmployeeService.java:180 | 역할 코드별 ID | `List<Map<String, Object>>` |
| `findPositionName` | user/service/EmployeeService.java:195 | 직급명 확인 | `Map<String, Object>` |
| `countEmail` | user/service/EmployeeService.java:197 | 같은 이메일 계정 수 | `long` |
| `findPosition` | user/service/EmployeeService.java:250 | 직급 존재 확인 | `Map<String, Object>` |
| `findActiveUsers` | user/service/UserLookupService.java:30 | ACTIVE 사원 접두어·부서 검색 | `List<Map<String, Object>>` |

### user/repository/EmployeeStore.java

| 메서드 | 원래 위치 | SQL 요약 | 반환 타입 |
|---|---|---|---|
| `lockChief` | user/service/EmployeeService.java:39 | CHIEF 역할 행 잠금 | `List<Map<String, Object>>` |
| `lockActiveChiefs` | user/service/EmployeeService.java:43 | ACTIVE CHIEF 사원 잠금 | `List<Map<String, Object>>` |
| `lockHeadDepartments` | user/service/EmployeeService.java:52 | 사원이 부서장인 부서 잠금 | `List<Map<String, Object>>` |
| `insertLoginSequence` | user/service/EmployeeService.java:201 | 입사연도 발급 행 생성 | `int` |
| `lockLoginSequence` | user/service/EmployeeService.java:205 | 입사연도 발급 행 잠금 | `Map<String, Object>` |
| `updateLoginSequence` | user/service/EmployeeService.java:207 | 마지막 발급 번호 갱신 | `int` |
| `insertEmployee` | user/service/EmployeeService.java:212 | 초대 대기 사원 등록 | `long` |
| `insertUserRole` | user/service/EmployeeService.java:222 | 사원 역할 등록(:user) | `int` |
| `lockUser` | user/service/EmployeeService.java:238, user/service/EmployeeService.java:276, user/service/EmployeeService.java:307 | 사원 변경 대상 잠금 | `Map<String, Object>` |
| `updateEmployee` | user/service/EmployeeService.java:256 | 사원 정보 수정 | `int` |
| `updateStatus` | user/service/EmployeeService.java:282 | 상태·퇴사일·토큰 버전 수정 | `int` |
| `deleteUserRoles` | user/service/EmployeeService.java:299 | 사원 역할 삭제 | `int` |
| `insertAssignedRole` | user/service/EmployeeService.java:301 | 역할 교체 중 등록(:id) | `int` |

### permission/repository/RoleQueries.java

| 메서드 | 원래 위치 | SQL 요약 | 반환 타입 |
|---|---|---|---|
| `findRole` | permission/service/RoleService.java:20 | 역할 상세 | `Map<String, Object>` |
| `findMenus` | permission/service/RoleService.java:25 | 메뉴·허용 동작 목록 | `List<Map<String, Object>>` |
| `findRoles` | permission/service/RoleService.java:31 | 역할별 인원 포함 목록 | `List<Map<String, Object>>` |
| `countRoleMembers` | permission/service/RoleService.java:66, permission/service/RoleService.java:137 | 역할 보유 사원 수 | `long` |
| `countRoleApprovalSteps` | permission/service/RoleService.java:67 | 역할을 쓰는 결재 단계 수 | `long` |
| `findPermissions` | permission/service/RoleService.java:77 | 역할별 메뉴 권한 | `List<Map<String, Object>>` |
| `findMemberIds` | permission/service/RoleService.java:130 | 역할 사원 ID 페이지 | `List<Map<String, Object>>` |
| `countMembership` | permission/service/RoleService.java:146, permission/service/RoleService.java:158 | 사원·역할 연결 수 | `long` |
| `countUserRoles` | permission/service/RoleService.java:165 | 사원의 역할 수 | `long` |

### permission/repository/RoleStore.java

| 메서드 | 원래 위치 | SQL 요약 | 반환 타입 |
|---|---|---|---|
| `insertRole` | permission/service/RoleService.java:38 | 역할 등록 | `long` |
| `updateRoleCode` | permission/service/RoleService.java:40 | 생성 역할 코드 확정 | `int` |
| `updateRole` | permission/service/RoleService.java:53 | 역할 이름·설명 수정 | `int` |
| `lockRole` | permission/service/RoleService.java:65 | 삭제 대상 역할 잠금 | `List<Map<String, Object>>` |
| `deleteRolePermissions` | permission/service/RoleService.java:70, permission/service/RoleService.java:114 | 역할 권한 삭제 | `int` |
| `deleteRole` | permission/service/RoleService.java:71 | 역할 삭제 | `int` |
| `insertPermission` | permission/service/RoleService.java:116 | 메뉴별 동작·범위 저장 | `int` |
| `insertMembership` | permission/service/RoleService.java:148 | 사원·역할 연결 등록 | `int` |
| `deleteMembership` | permission/service/RoleService.java:168 | 사원·역할 연결 삭제 | `int` |

SQL 실행 메서드는 78개(기존 호출 지점 87곳). SQL 외 보조 메서드는 `OrgStore.workScheduleParameters`(map 생성, 원래 OrgService:226), `EmployeeQueries.findSortColumns`(Map<String,String>, 원래 EmployeeService:146), `findSortOrder`(String, 원래 EmployeeService:152) 3개다.

## 합친 SQL과 남긴 경계

| 클래스·메서드 | 원래 출현 → 메서드 수 | 줄어든 SQL / map 키 출현 |
|---|---|---|
| OrgQueries.countDepartmentMembers | 2 → 1 | SQL 1, id 1 |
| OrgQueries.countChildDepartments | 2 → 1 | SQL 1, id 1 |
| EmployeeQueries.findDepartment | 3 → 1 | SQL 2, id 2 |
| EmployeeStore.lockUser | 3 → 1 | SQL 2, id 2 |
| RoleQueries.countRoleMembers | 2 → 1 | SQL 1, id 1 |
| RoleQueries.countMembership | 2 → 1 | SQL 1, user 1, role 1 |
| RoleStore.deleteRolePermissions | 2 → 1 | SQL 1, id 1 |

쿼리 구조가 같아 보여도 파라미터 이름이 다른 `insertUserRole(:user)`와 `insertAssignedRole(:id)`는 합치지 않았다. 역할 도메인의 같은 SQL도 도메인 간 공통 클래스로 추상화하지 않았다.

SQL 실행 외 repository 코드와 이유:

- `OrgStore.workScheduleParameters`: 기존에 INSERT/UPDATE가 공유하던 map 구성. getter 평가 순서(start, end, grace, name)는 서비스의 기존 분기 앞에서 유지했다. UPDATE에서만 id를 추가한다. 쓰이지 않던 UPDATE의 name 바인딩도 그대로다.
- `EmployeeQueries.findSortColumns/findSortOrder`: 정렬 컬럼 표와 SQL ORDER BY 조각을 함께 이동했다. sort.split, 허용 컬럼·방향 검사, throw는 기존 서비스에 있다. 연결 피연산자 및 순서는 그대로다. SQL 실행이 없는 메타데이터 메서드이므로 원래 서비스 로컬 값 타입(Map<String,String>, String)을 유지한다.
- `EmployeeQueries.findActiveUsers`: 선택적 WHERE 조각 조합과 map만 둔다. q의 공백 검사와 와일드카드 이스케이프는 서비스에서 prefix로 계산한다. null prefix이면 기존 q 없음과 같은 분기다. 바인딩 구성 순서는 size, q(있을 때), dept(있을 때)로 그대로다. 결과 가공은 추가하지 않았다.

서비스에 남긴 판단:

- `UserStatus.valueOf(status)`와 IllegalArgumentException→BusinessException 변환은 서비스 그대로다. Repository의 JPA 예외 변환으로 오류 응답이 달라지지 않게 했다.
- `db.one(..., ErrorCode)`는 SQL 메서드로 이동하되 반환이 없을 때 기존 BusinessException을 그대로 사용한다.
- `LocalDate.now(clock)`, `LocalDateTime.now(clock)`, UUID·코드 생성, 요청 getter, 페이지 offset, 역할 scope.name()는 호출 인자로 서비스에서 계산한다.
- `if`의 &&/|| 단락 평가 안에 있던 조회는 같은 조건식 자리에 남았다. 부서 트리→사용자 행, CHIEF→사용자/역할 행, 번호 발급 행의 기존 순서도 유지했다.
- 사용자 상태·부서장 불변식·마지막 관리소장 보호·권한 상승 방지·초대 이벤트/토큰 호출은 이동하지 않았다.
- 기존 EmployeeRepository와 DepartmentTreeLock은 이름·내용 그대로다. SqlStore 및 테스트가 감싸는 다른 빈의 public 메서드를 바꾸지 않았다. 조직 트리의 두 JdbcTemplate 호출도 원래 실행 경로를 유지했다.

## SQL 위치 측정

| 도메인 | 밖의 SQL 값(전 → 후) | 파일 수(전 → 후) |
|---|---:|---:|
| approval | 41 → 41 | 4 → 4 |
| attendance | 24 → 24 | 6 → 6 |
| global | 2 → 2 | 2 → 2 |
| home | 16 → 16 | 2 → 2 |
| leave | 29 → 29 | 4 → 4 |
| notification | 11 → 11 | 2 → 2 |
| org | 38 → 0 | 2 → 0 |
| overtime | 5 → 5 | 1 → 1 |
| permission | 21 → 0 | 1 → 0 |
| project | 26 → 26 | 3 → 3 |
| task | 25 → 25 | 2 → 2 |
| user | 31 → 0 | 2 → 0 |
| 전체(명시적 예외 3개 적용) | 269 → 179 | 31 → 26 |
| domain만 | 267 → 177 | 29 → 24 |

이번 대상은 org 38 + user 31 + permission 21 = 90개 SQL 값 출현을 repository 안으로 이동했다. 실행 호출 87곳과 값 출현 수가 다른 이유는 UserLookupService의 SELECT/ORDER BY/선택적 AND 조각을 따로 세기 때문이다. SQL 키워드 검색은 단어 단위·대문자이며 행 수가 아닌 javac 리터럴 출현 수다.

global 2개는 의도적으로 별도 보고한다. `WebConfig`의 HTTP 메서드 문자열 `DELETE`는 규칙상 오탐이며 SQL이 아니다. `ScopeFilter`의 IN 조건 조각은 이번 명세의 공통 예외 3개 이름에 들지 않는다. 이번 묶음 밖이므로 바꾸지 않았다. 이를 제외한 domain 파일 29개는 리뷰어 집계와 같다.

키워드 없는 조각 수동 확인: `EmployeeService`의 `u.user_name`, `u.login_id`, `u.hire_date`, `u.status` 컬럼 표 및 기본 ` ASC`, 공백 연결을 EmployeeQueries로 이동했다. `UserLookupService`의 `u.status='ACTIVE'`도 함께 이동했다. 그 밖의 도메인·global은 이번 범위 밖이다.

### 측정 재실행

```bash
R03_BASE=$(mktemp -d)
git archive 952a64e | tar -x -C "$R03_BASE"
cat > /tmp/r03-merged-counts.json <<'JSON'
{
  "id": 8,
  "SELECT COUNT(*) FROM users WHERE department_id=:id": 1,
  "SELECT COUNT(*) FROM departments WHERE parent_id=:id": 1,
  "SELECT id FROM departments WHERE id=:id": 2,
  "SELECT * FROM users WHERE id=:id FOR UPDATE": 2,
  "SELECT COUNT(*) FROM user_roles WHERE role_id=:id": 1,
  "DELETE FROM role_permissions WHERE role_id=:id": 1,
  "SELECT COUNT(*) FROM user_roles WHERE user_id=:user AND role_id=:role": 1,
  "user": 1,
  "role": 1
}
JSON
JAVA_HOME=/Users/kimmini/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home \
python3 docs/refactoring/R-03-sql-location-measure.py \
  --root . --baseline "$R03_BASE" \
  --allow-count-reductions /tmp/r03-merged-counts.json \
  --check-repository backend/src/main/java/com/minierp/backend/domain/org/repository/OrgQueries.java \
  --check-repository backend/src/main/java/com/minierp/backend/domain/org/repository/OrgStore.java \
  --check-repository backend/src/main/java/com/minierp/backend/domain/user/repository/EmployeeQueries.java \
  --check-repository backend/src/main/java/com/minierp/backend/domain/user/repository/EmployeeStore.java \
  --check-repository backend/src/main/java/com/minierp/backend/domain/permission/repository/RoleQueries.java \
  --check-repository backend/src/main/java/com/minierp/backend/domain/permission/repository/RoleStore.java \
  --output /tmp/r03-final.json
python3 docs/refactoring/R-02-format-measure.py --root . --output /tmp/r03-format.json
```

측정기는 Python 표준 라이브러리와 설치된 JDK 파서만 쓰며 파일이나 DB를 수정하지 않는다. 승인하지 않은 출현 감소, 값 집합/트랜잭션 차이, 지정 repository의 throw/Transactional/valueOf/parse 호출이면 종료 코드 1이다. 지정 repository가 없으면 인자 오류로 실패한다. 설치·빌드 플러그인은 없다. 임시 fixture 검사 7개(일반/텍스트 블록 값, 키워드 수, 같은 코드, SQL 공백 변화 검출, 트랜잭션 속성 변화 검출, 정확한 중복 감소 승인, repository 금지 항목 검출)가 통과했다.

## 버그·결정 필요·한계

- 이번 검토 범위에서 추가로 재현한 기존 기능 버그는 없다. R-02에 적은 남은 공백 모양은 대상 SQL 이동에 걸리는 부분 외에는 수정하지 않았다.
- `global/permission/ScopeFilter`를 명시적 공통 쿼리 예외에 넣을지는 다음 묶음 시작 전에 명세 담당자의 결정이 필요하다. 이번 측정에서 숨기지 않았다.
- 안전망은 실행된 경로만 확인한다. javac 값 집합/횟수·트랜잭션 검사와 원문 SQL·인자 표현식 대조로 보완했지만 모든 가능한 입력에 대한 수학적 동치 증명은 아니다.
- 기록 모드의 기존 v2 동시 요청 순서 고정 및 진단 조회 제외 한계는 R-01 그대로다. 이를 바꾸거나 차이를 가리지 않았다.
- 로컬 개발 DB·실제 운영 서버는 검증하지 않았다. 테스트는 Testcontainers만 사용했다. 새 성능 개선은 측정하거나 주장하지 않는다.
- 묶음 1 리뷰를 기다린다. 묶음 2·3 및 4단계는 시작하지 않았다.

## 부록: 최종 형식 예외 위치

R-02 도구가 출력한 위치를 그대로 갱신했다. 비대상 코드의 예외는 R-02의 승인된 사유가 그대로 적용된다.

### main

literal_exceptions (88개):

- `backend/src/main/java/com/minierp/backend/domain/approval/general/GeneralFormHandler.java:29`
- `backend/src/main/java/com/minierp/backend/domain/approval/general/GeneralFormHandler.java:41`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/InboxRepository.java:21`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/InboxRepository.java:23`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/InboxRepository.java:25`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/InboxRepository.java:29`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/InboxRepository.java:43`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/InboxRepository.java:49`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/InboxRepository.java:69`
- `backend/src/main/java/com/minierp/backend/domain/approval/service/ApprovalLineResolver.java:80`
- `backend/src/main/java/com/minierp/backend/domain/approval/service/DocumentService.java:37`
- `backend/src/main/java/com/minierp/backend/domain/approval/service/DocumentService.java:170`
- `backend/src/main/java/com/minierp/backend/domain/approval/service/DocumentService.java:190`
- `backend/src/main/java/com/minierp/backend/domain/approval/service/DocumentService.java:202`
- `backend/src/main/java/com/minierp/backend/domain/approval/service/DocumentService.java:208`
- `backend/src/main/java/com/minierp/backend/domain/approval/service/DocumentService.java:263`
- `backend/src/main/java/com/minierp/backend/domain/approval/service/DocumentService.java:282`
- `backend/src/main/java/com/minierp/backend/domain/approval/service/DocumentService.java:370`
- `backend/src/main/java/com/minierp/backend/domain/approval/service/FormService.java:67`
- `backend/src/main/java/com/minierp/backend/domain/approval/service/FormService.java:86`
- `backend/src/main/java/com/minierp/backend/domain/approval/service/FormService.java:132`
- `backend/src/main/java/com/minierp/backend/domain/attendance/handler/AttendanceFixFormHandler.java:89`
- `backend/src/main/java/com/minierp/backend/domain/attendance/handler/AttendanceFixFormHandler.java:111`
- `backend/src/main/java/com/minierp/backend/domain/attendance/handler/AttendanceFixFormHandler.java:175`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:109`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:31`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:135`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceStore.java:35`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceStore.java:78`
- `backend/src/main/java/com/minierp/backend/domain/home/service/HomeQueries.java:15`
- `backend/src/main/java/com/minierp/backend/domain/home/service/HomeQueries.java:52`
- `backend/src/main/java/com/minierp/backend/domain/leave/entity/LeaveRequest.java:37`
- `backend/src/main/java/com/minierp/backend/domain/leave/entity/LeaveRequest.java:41`
- `backend/src/main/java/com/minierp/backend/domain/leave/entity/LeaveRequest.java:51`
- `backend/src/main/java/com/minierp/backend/domain/leave/entity/LeaveRequest.java:54`
- `backend/src/main/java/com/minierp/backend/domain/leave/handler/LeaveFormHandler.java:68`
- `backend/src/main/java/com/minierp/backend/domain/leave/handler/LeaveFormHandler.java:125`
- `backend/src/main/java/com/minierp/backend/domain/leave/handler/LeaveFormHandler.java:141`
- `backend/src/main/java/com/minierp/backend/domain/leave/service/LeaveAdminService.java:98`
- `backend/src/main/java/com/minierp/backend/domain/leave/service/LeaveAdminService.java:122`
- `backend/src/main/java/com/minierp/backend/domain/leave/service/LeaveGrantWork.java:39`
- `backend/src/main/java/com/minierp/backend/domain/leave/service/LeaveGrantWork.java:55`
- `backend/src/main/java/com/minierp/backend/domain/notification/service/NotificationService.java:19`
- `backend/src/main/java/com/minierp/backend/domain/notification/service/NotificationService.java:27`
- `backend/src/main/java/com/minierp/backend/domain/notification/service/NotificationService.java:34`
- `backend/src/main/java/com/minierp/backend/domain/org/repository/OrgQueries.java:36`
- `backend/src/main/java/com/minierp/backend/domain/org/repository/OrgQueries.java:50`
- `backend/src/main/java/com/minierp/backend/domain/org/repository/OrgQueries.java:72`
- `backend/src/main/java/com/minierp/backend/domain/org/repository/OrgStore.java:102`
- `backend/src/main/java/com/minierp/backend/domain/overtime/entity/OvertimeRequest.java:25`
- `backend/src/main/java/com/minierp/backend/domain/overtime/entity/OvertimeRequest.java:37`
- `backend/src/main/java/com/minierp/backend/domain/overtime/handler/OvertimeFormHandler.java:42`
- `backend/src/main/java/com/minierp/backend/domain/overtime/handler/OvertimeFormHandler.java:48`
- `backend/src/main/java/com/minierp/backend/domain/permission/repository/RoleQueries.java:22`
- `backend/src/main/java/com/minierp/backend/domain/permission/repository/RoleQueries.java:28`
- `backend/src/main/java/com/minierp/backend/domain/permission/repository/RoleQueries.java:42`
- `backend/src/main/java/com/minierp/backend/domain/permission/repository/RoleStore.java:25`
- `backend/src/main/java/com/minierp/backend/domain/permission/repository/RoleStore.java:43`
- `backend/src/main/java/com/minierp/backend/domain/project/repository/ProjectMemberRepository.java:16`
- `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:31`
- `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:128`
- `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:161`
- `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:195`
- `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:232`
- `backend/src/main/java/com/minierp/backend/domain/project/service/ProjectSummaryQuery.java:9`
- `backend/src/main/java/com/minierp/backend/domain/task/repository/TaskRepository.java:37`
- `backend/src/main/java/com/minierp/backend/domain/task/repository/TaskRepository.java:45`
- `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:36`
- `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:127`
- `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:177`
- `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:200`
- `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:268`
- `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:279`
- `backend/src/main/java/com/minierp/backend/domain/task/service/TaskOverdueWork.java:26`
- `backend/src/main/java/com/minierp/backend/domain/task/service/TaskOverdueWork.java:58`
- `backend/src/main/java/com/minierp/backend/domain/user/entity/User.java:45`
- `backend/src/main/java/com/minierp/backend/domain/user/entity/User.java:53`
- `backend/src/main/java/com/minierp/backend/domain/user/entity/User.java:64`
- `backend/src/main/java/com/minierp/backend/domain/user/entity/User.java:67`
- `backend/src/main/java/com/minierp/backend/domain/user/repository/EmployeeQueries.java:21`
- `backend/src/main/java/com/minierp/backend/domain/user/repository/EmployeeQueries.java:36`
- `backend/src/main/java/com/minierp/backend/domain/user/repository/EmployeeQueries.java:85`
- `backend/src/main/java/com/minierp/backend/domain/user/repository/EmployeeStore.java:22`
- `backend/src/main/java/com/minierp/backend/domain/user/repository/EmployeeStore.java:32`
- `backend/src/main/java/com/minierp/backend/domain/user/repository/EmployeeStore.java:81`
- `backend/src/main/java/com/minierp/backend/domain/user/repository/EmployeeStore.java:88`
- `backend/src/main/java/com/minierp/backend/global/batch/BatchRunRecorder.java:27`
- `backend/src/main/java/com/minierp/backend/global/batch/BatchRunRecorder.java:31`

name_member_breaks (1개):

- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceStore.java:102`

key_value_splits (3개):

- `backend/src/main/java/com/minierp/backend/domain/approval/service/DocumentService.java:124`
- `backend/src/main/java/com/minierp/backend/domain/attendance/handler/AttendanceFixFormHandler.java:60`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceStore.java:140`

outer_operator_unsplit (1개):

- `backend/src/main/java/com/minierp/backend/domain/approval/service/DocumentService.java:192`

protected_fragment_splits (4개):

- `backend/src/main/java/com/minierp/backend/domain/approval/service/DocumentService.java:124`
- `backend/src/main/java/com/minierp/backend/domain/attendance/handler/AttendanceFixFormHandler.java:60`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceStore.java:102`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceStore.java:140`

### test

literal_exceptions (78개):

- `backend/src/test/java/com/minierp/backend/integration/CoreApiIntegrationTest.java:57`
- `backend/src/test/java/com/minierp/backend/integration/CoreApiIntegrationTest.java:61`
- `backend/src/test/java/com/minierp/backend/integration/DepartmentHeadConcurrencyTest.java:65`
- `backend/src/test/java/com/minierp/backend/integration/M1AcceptanceTest.java:330`
- `backend/src/test/java/com/minierp/backend/integration/M1AcceptanceTest.java:338`
- `backend/src/test/java/com/minierp/backend/integration/M1DataMigrationTest.java:46`
- `backend/src/test/java/com/minierp/backend/integration/M1DataMigrationTest.java:71`
- `backend/src/test/java/com/minierp/backend/integration/M2DataMigrationTest.java:81`
- `backend/src/test/java/com/minierp/backend/integration/M2DataMigrationTest.java:87`
- `backend/src/test/java/com/minierp/backend/integration/M2DocumentIntegrationTest.java:63`
- `backend/src/test/java/com/minierp/backend/integration/M2DocumentIntegrationTest.java:65`
- `backend/src/test/java/com/minierp/backend/integration/M2DocumentIntegrationTest.java:293`
- `backend/src/test/java/com/minierp/backend/integration/M2DocumentIntegrationTest.java:399`
- `backend/src/test/java/com/minierp/backend/integration/M2FormApiTest.java:103`
- `backend/src/test/java/com/minierp/backend/integration/M2InboxPerformanceTest.java:29`
- `backend/src/test/java/com/minierp/backend/integration/M2InboxPerformanceTest.java:92`
- `backend/src/test/java/com/minierp/backend/integration/M2InboxTest.java:16`
- `backend/src/test/java/com/minierp/backend/integration/M2ReviewTest.java:14`
- `backend/src/test/java/com/minierp/backend/integration/M2ReviewTest.java:31`
- `backend/src/test/java/com/minierp/backend/integration/M3AttendanceFixTest.java:31`
- `backend/src/test/java/com/minierp/backend/integration/M3AttendanceTest.java:117`
- `backend/src/test/java/com/minierp/backend/integration/M3AttendanceTest.java:126`
- `backend/src/test/java/com/minierp/backend/integration/M3BatchResponseTest.java:28`
- `backend/src/test/java/com/minierp/backend/integration/M3CsvLabelsTest.java:32`
- `backend/src/test/java/com/minierp/backend/integration/M3DataMigrationTest.java:25`
- `backend/src/test/java/com/minierp/backend/integration/M3DataMigrationTest.java:29`
- `backend/src/test/java/com/minierp/backend/integration/M3DataMigrationTest.java:31`
- `backend/src/test/java/com/minierp/backend/integration/M3DataMigrationTest.java:36`
- `backend/src/test/java/com/minierp/backend/integration/M3DataMigrationTest.java:42`
- `backend/src/test/java/com/minierp/backend/integration/M3DataMigrationTest.java:53`
- `backend/src/test/java/com/minierp/backend/integration/M3LeaveAdjustmentConcurrencyTest.java:51`
- `backend/src/test/java/com/minierp/backend/integration/M3LeaveGrantTest.java:181`
- `backend/src/test/java/com/minierp/backend/integration/M3LegacyAttendanceTest.java:31`
- `backend/src/test/java/com/minierp/backend/integration/M3LegacyAttendanceTest.java:43`
- `backend/src/test/java/com/minierp/backend/integration/M3LegacyAttendanceTest.java:77`
- `backend/src/test/java/com/minierp/backend/integration/M3MonthlyTest.java:170`
- `backend/src/test/java/com/minierp/backend/integration/M4BoardApiTest.java:200`
- `backend/src/test/java/com/minierp/backend/integration/M4BoardApiTest.java:281`
- `backend/src/test/java/com/minierp/backend/integration/M4BoardConcurrencyTest.java:159`
- `backend/src/test/java/com/minierp/backend/integration/M4BoardConcurrencyTest.java:231`
- `backend/src/test/java/com/minierp/backend/integration/M4DataMigrationTest.java:24`
- `backend/src/test/java/com/minierp/backend/integration/M4DataMigrationTest.java:28`
- `backend/src/test/java/com/minierp/backend/integration/M4DataMigrationTest.java:33`
- `backend/src/test/java/com/minierp/backend/integration/M4DeterministicLockTest.java:114`
- `backend/src/test/java/com/minierp/backend/integration/M4HomeGrowthTest.java:26`
- `backend/src/test/java/com/minierp/backend/integration/M4HomeGrowthTest.java:39`
- `backend/src/test/java/com/minierp/backend/integration/M4HomeGrowthTest.java:43`
- `backend/src/test/java/com/minierp/backend/integration/M4HomeGrowthTest.java:55`
- `backend/src/test/java/com/minierp/backend/integration/M4HomeGrowthTest.java:58`
- `backend/src/test/java/com/minierp/backend/integration/M4HomeGrowthTest.java:61`
- `backend/src/test/java/com/minierp/backend/integration/M4HomeTest.java:21`
- `backend/src/test/java/com/minierp/backend/integration/M4HomeTest.java:24`
- `backend/src/test/java/com/minierp/backend/integration/M4HomeTest.java:48`
- `backend/src/test/java/com/minierp/backend/integration/M4HomeTest.java:150`
- `backend/src/test/java/com/minierp/backend/integration/M4LegacyExperimentTest.java:88`
- `backend/src/test/java/com/minierp/backend/integration/M4OverdueTest.java:47`
- `backend/src/test/java/com/minierp/backend/integration/M4OverdueTest.java:90`
- `backend/src/test/java/com/minierp/backend/integration/M4QueryGrowthTest.java:34`
- `backend/src/test/java/com/minierp/backend/integration/M4QueryGrowthTest.java:48`
- `backend/src/test/java/com/minierp/backend/integration/M4QueryGrowthTest.java:103`
- `backend/src/test/java/com/minierp/backend/integration/M5ProductionTest.java:157`
- `backend/src/test/java/com/minierp/backend/integration/M5ProductionTest.java:207`
- `backend/src/test/java/com/minierp/backend/integration/M5ProductionTest.java:231`
- `backend/src/test/java/com/minierp/backend/support/ApprovalApiTestSupport.java:55`
- `backend/src/test/java/com/minierp/backend/support/ApprovalApiTestSupport.java:58`
- `backend/src/test/java/com/minierp/backend/support/ApprovalApiTestSupport.java:65`
- `backend/src/test/java/com/minierp/backend/support/AttendanceTestSupport.java:69`
- `backend/src/test/java/com/minierp/backend/support/AttendanceTestSupport.java:72`
- `backend/src/test/java/com/minierp/backend/support/AttendanceTestSupport.java:83`
- `backend/src/test/java/com/minierp/backend/support/BoardTestSupport.java:11`
- `backend/src/test/java/com/minierp/backend/support/BoardTestSupport.java:24`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:14`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:16`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:18`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:20`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:24`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:26`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:188`

key_value_splits (8개):

- `backend/src/test/java/com/minierp/backend/support/refactoring/RecordingOrder.java:17`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:14`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:16`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:18`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:20`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:22`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:24`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:26`

outer_operator_unsplit (1개):

- `backend/src/test/java/com/minierp/backend/integration/M4BoardConcurrencyTest.java:164`

protected_fragment_splits (1개):

- `backend/src/test/java/com/minierp/backend/integration/M1DataMigrationTest.java:35`

ternary_layout (1개):

- `backend/src/test/java/com/minierp/backend/integration/M1DataMigrationTest.java:35`
