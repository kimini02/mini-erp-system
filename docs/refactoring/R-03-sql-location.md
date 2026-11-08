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

## 묶음 1 리뷰 보완과 측정 도구 보완 (2026-11-07)

위 묶음 1 본문은 당시 기록으로 유지한다. 아래는 이번 리뷰 지시와 추가 타입 판단에 따른 보완이며, 다음 절의 묶음 2와 함께 검증했다.

- WorkingDays·RoleService·EmployeeService의 짧아진 문장 3곳을 한 줄로 합쳤다. EmployeeQueries의 텍스트 블록 닫는 줄은 원래 `""", map("ids", ids));`로 복구했다.
- SQL 없는 정렬 보조 메서드는 `sortColumns()`·`orderBy(...)`로 바꾸고, WorkingDays·UserLookupService에서 쓰지 않는 import를 제거했다.
- 사용자 추가 지시에 따라 `OrgStore.lockHeadCandidate(Long id)`와 `updateWorkSchedule(..., Long id)`로 맞췄다. 원래 map/put 값 `Long`을 기본형으로 받던 두 곳의 불필요한 언박싱을 제거했다.
- `RoleStore.insertPermission`의 scope는 String을 유지한다. `map(Object...)` 문맥에서 조건식 전체가 Object로 귀속되더라도 두 분기의 독립적인 타입은 String이다. 최신 사용자 결정에 따라 참조 넓히기는 차이가 아니며, 박싱·언박싱·기본형 변환만 금지한다. 도구는 javac로 분기 타입과 참조 상위 타입을 얻는다. 서로 다른 분기 타입의 일반적인 LUB/제네릭 추론까지 대신 계산하지 않으며, 간접 흐름은 수동 목록으로 남긴다.

### 재실행 가능한 도구

- `R-03-sql-location-measure.py`: javac 문자열 값·트랜잭션 목록 검사 유지. ScopeFilter 허용, 정확한 파일/값 오탐 목록, repository의 now/actor/scope/BodyValues/NotificationService/LeaveGrantRules 호출 검사를 추가했다. 인스턴스 NotificationService 호출도 선언된 수신자 타입으로 확인한다.
- `R-03-format-units.py`: 명세에 열거한 문장·선언·제어문/메서드 머리·어노테이션의 javac 범위를 읽는다. 텍스트 블록·주석·블록 람다·switch 식·익명 클래스가 포함된 단위를 제외한다. 줄바꿈을 공백 하나로 줄이고 지정된 구두점 공백을 정리해 120자 이내인 여러 줄 단위를 센다. 원본은 수정하지 않는다.
- `R-03-call-site-check.py`: 별도 컴파일 사본과 기록의 classpath.txt를 사용해 원래 SQL 실행을 새 repository 호출과 연결한다. SQL 식 원문 토큰열, map 키/순서, 호출 인자 식/순서, standalone 타입을 대조한다. map 뒤 put의 값과 새 쿼리 매개변수도 검사한다. 짝이 없거나 간접 map/helper 흐름인 항목은 자동 통과로 위장하지 않고 manualReview/putManualReview로 출력한다. 클래스패스의 프로젝트 classes는 각 사본의 것으로 치환하며 실제 DB에 접속하지 않는다.

| 대조 | 결과 |
|---|---|
| 한 줄 단위: 952a64e | main 1(ProjectMember:14–15), test 0 |
| 한 줄 단위: c6270e0 | main 4, test 0 |
| 한 줄 단위: 준비 보완 뒤 | main 1(동일 기존 예외), test 0 |
| 952a64e → c6270e0, standalone/put 보완 도구 | 호출 87, 자동 84, 간접 흐름 3; 타입 차이 2, exit 1 |
| 952a64e → 준비 보완 후보 | 호출 87, 자동 84, 간접 흐름 3; 차이 0, exit 0 |
| put 3곳 | scheduleId Long→Long, q String→String, departmentId Long→Long; 타입 차이 0 |

간접 흐름 3곳은 OrgService.departmentSchedule INSERT/UPDATE와 UserLookupService.lookup이다. 근무시간 값은 startTime→endTime→lateGraceMin→이름 순서로 서비스에서 보관하고, repository map도 start/end/grace/name 순서이며 UPDATE에만 Long id를 마지막에 붙인다. 검색은 원래 이스케이프 식을 서비스의 prefix에 그대로 보관한다. size→q→dept 바인딩 순서와 검색/부서 조건 조립 순서를 유지했다. put q의 식→지역 변수 흐름은 도구에도 수동 확인으로 표시된다.

준비 보완 최종 검증: `a843fc1`, UTC 기록 ON 231/0/0/0, 588.24초, `REFACTORING_COMPARE differences=0 sqlOrderChanges=10`. (a) 문자열 집합 동일, (b) 트랜잭션 83개 동일, (c) repository 금지 호출 0, (f) 기존 예외만 남았다. OFF는 지시대로 생략했다.
타입 두 곳을 반영하기 전 예비 실행은 231/0/0/0·694.27초·differences=0이었지만 최종 후보의 완료 증거로 쓰지 않았다. 위 최종 실행으로 대체했다.

## 묶음 2 — approval · leave · overtime

### 범위와 커밋

develop `26226bd2392ff8b6840e10a3fa918b16286679c5`의 7차 머리말과 merge-tree를 확인하고 병합했다. 실제 병합 차이는 backend/md/17-refactoring.md 한 파일이다. 모든 누적 비교는 952a64e 기준이다. 묶음 3·4단계, 다른 기능/버그 수정, develop으로의 병합·푸시는 하지 않는다.

| 커밋 | 묶음 | 변경 파일 수 | 검증 |
|---|---|---:|---|
| `f9aa570` | 명세 17 7차 병합 | 1 | archive test-compile exit 0, 9.43초 |
| `a843fc1` | 묶음 1 보완 | 6 | 정적 검사 + ON 전체 |
| `649f4e9` | 읽기 전용 도구 3개 | 3 | archive test-compile exit 0, 9.87초 |
| `e839fce` | approval SQL 이동 | 10 | 적용 전 (a)(b)(c)(g), 적용 뒤 (d)(e)(f) |
| `03ed3a0` | leave SQL 이동 | 6 | 적용 전 (a)(b)(c)(g), 적용 뒤 (d)(e)(f) |
| `7a28fcc` | overtime SQL 이동 | 2 | 적용 전 (a)(b)(c)(g), 적용 뒤 (d)(e)(f) |
| 이 문서 커밋 | 묶음 2 기록 | 1 | Java 변경 없음, archive test-compile exit 0 |

### (a)·(b)·(c)·(g) 적용 전 검사

각 도메인 후보를 작업 트리 밖에 만들고 컴파일했다. 정적 검사를 통과한 후보의 파일 SHA-256을 보관한 뒤 동일한 파일만 적용했다. 각 커밋 직전에 staged tree와 파일 해시를 다시 확인했다.

| 후보 | 문자열 출현/서로 다른 값 | 새 값/사라진 값 | 트랜잭션 | repository 위반 | 호출 자동/수동 | 자동 차이 |
|---|---|---|---:|---:|---|---:|
| approval 누적 | 3216/1314 | 0/0 | 83개 동일 | 0 | 40/1 | 0 |
| leave 누적 | 3216/1314 | 0/0 | 83개 동일 | 0 | 20/4 | 0 |
| overtime 누적 | 3216/1314 | 0/0 | 83개 동일 | 0 | 5/0 | 0 |

추가 중복 제거는 DocumentService의 동일한 DELETE approval_lines 3곳→DocumentStore.deleteLines 1곳뿐이다. SQL 리터럴 2개와 `id` map 키 2개 출현 감소를 묶음 1 허용 목록에 더했다. 전체 감소는 23개이며 그 외 감소/증가가 없다. 도메인을 넘는 동일 SQL은 합치지 않았다.

누적 감소 허용 목록(아래 JSON을 임시 파일로 저장해 --allow-count-reductions에 전달):

```json
{
  "id": 10,
  "SELECT COUNT(*) FROM users WHERE department_id=:id": 1,
  "SELECT COUNT(*) FROM departments WHERE parent_id=:id": 1,
  "SELECT id FROM departments WHERE id=:id": 2,
  "SELECT * FROM users WHERE id=:id FOR UPDATE": 2,
  "SELECT COUNT(*) FROM user_roles WHERE role_id=:id": 1,
  "DELETE FROM role_permissions WHERE role_id=:id": 1,
  "SELECT COUNT(*) FROM user_roles WHERE user_id=:user AND role_id=:role": 1,
  "user": 1,
  "role": 1,
  "DELETE FROM approval_lines WHERE document_id=:id": 2
}
```

### 수동 확인과 설계 판단

- FormService.steps: 첫 반복의 역할/사원 조회·검증과 값 계산을 유지한다. 기존 map의 form(Object), kind(String), type(String), role(Long), user(Long), order(int)를 같은 순서로 private StepValues에 보관한다. 양식 잠금→기존 단계 삭제→등록 반복 순서는 그대로다. INSERT 메서드에서 form/kind/type/role/user/order 순서로 map을 만든다. 응답 record는 도입하지 않았다. 쿼리 클래스에서 검증까지 실행하는 대안은 예외 변환과 트랜잭션 경계가 달라져 제외했다.
- LeaveAdminService.balances COUNT/목록(수동 2곳): year 검증→offset 계산→access.scope SQL→부서 범위 검사→검색어 가공→COUNT→목록 순서다. ScopeFilter.predicate는 DB 호출 없이 map에 범위 값만 넣으므로 부서 검사 뒤로 옮겨도 조회 결과·거절 시 부작용이 같다(사용자 지시). 두 쿼리는 같은 balanceParameters/balanceWhere helper로 year(int), size(int), offset(long), scopeUser/scopeDepartments, dept(Long), q(String) 순서의 동일 map/WHERE를 각각 만든다. 결과의 stream 가공은 서비스에 남겼다. ScopeFilter 또는 권한 SQL 자체를 복제하는 대안은 공통 권한 규칙이 갈라져 제외했다.
- LeaveAdminService.adjust(수동 1곳): 사용자 FOR UPDATE→id(long)/year(int) 잔여 조회→잔여 검증→INSERT 순서를 유지한다. INSERT 값에서 access.actor()를 먼저, now(clock)를 다음에 평가한다. 잔여 BigDecimal 변환과 계산은 서비스에 남겼다.
- LeaveQueryService.ledger(수동 1곳): 유형 검증→year 계산→비어 있지 않은 type만 전달한다. repository의 map은 user(long)/year(int), 필요할 때 type(String) 순서이며 기존 WHERE 조각과 window-query 식 원문이 같다. 잔여 계산 뒤 type 필터가 붙는 구조도 유지했다.
- DocumentService: 클래스 수준 1개와 메서드 수준 7개의 트랜잭션 선언 및 격리 수준을 유지한다. transition의 completed 시각과 now 시각은 원래처럼 두 번 읽는다. nextNumber INSERT→FOR UPDATE→UPDATE 및 lockDocument의 잠금 조회→raw 재조회도 유지한다. NotificationService.store는 서비스 안에 있어 기존 주입 실패 500/전체 롤백 테스트 경로를 유지한다.
- ApprovalLineResolver: 쿼리 클래스에 @Transactional을 두지 않는다. APPROVER_NOT_FOUND를 잡아 참조 건너뛰기/ResolutionFailure로 바꾸는 기존 처리에서 rollback-only 부작용을 추가하지 않는다. person의 텍스트 블록 닫는 줄과 public 메서드 시그니처를 유지했다.
- LeaveGrantWork: now는 반복 전에 한 번, 사원별 잠금→옛 부여 COUNT→기존 grant_key 조회→INSERT 순서를 유지한다. grant_key stream과 LeaveGrantRules 계산은 서비스에 남겼다.
- General/Leave/Overtime handler의 BodyValues.date/time/optionalLong, JsonNode 읽기, Clock 값과 비교·형변환·응답 가공은 기존 handler에서 평가한다. public raw/available/days 및 ApprovalFormHandler 인터페이스는 그대로다.

repository에 옮긴 SQL 조립 코드는 LeaveQueries.balanceParameters/balanceWhere 두 private helper와 findLedger의 선택적 type 필터다. 그 외는 파라미터 map 구성·SQL 실행·반환이며 throw, @Transactional, valueOf/parse, 값 계산/NotificationService 호출은 없다. GeneralRequestStore와 OvertimeStore는 요청한 소규모 읽기/쓰기 통합 클래스로 만들었다. 기존 JPA repository는 수정하지 않았다.

### (d)·(e) 전체 UTC 실행

Java 17·MariaDB 10.11 Testcontainers, 기록 ON/OFF를 도메인별로 순차 실행했다. 시간은 Maven 전체 wall time(컴파일·컨테이너 시작 포함)이며 성능 개선 지표가 아니다. 테스트와 기대값, 기준선·기록 정책·비교 규칙은 수정하지 않았다.

| 묶음 | ON 성공/실패/오류/건너뜀 | ON 초 | 기록 비교 | OFF 성공/실패/오류/건너뜀 | OFF 초 | OFF 기록 출력 |
|---|---|---:|---|---|---:|---:|
| approval | 231/0/0/0 | 689.23 | REFACTORING_COMPARE differences=0 sqlOrderChanges=12 | 231/0/0/0 | 544.89 | 0 |
| leave | 231/0/0/0 | 549.63 | REFACTORING_COMPARE differences=0 sqlOrderChanges=12 | 231/0/0/0 | 655.82 | 0 |
| overtime | 231/0/0/0 | 917.52 | REFACTORING_COMPARE differences=0 sqlOrderChanges=12 | 231/0/0/0 | 687.45 | 0 |

묶음 2의 기록 ON 세 실행에서 오류 응답 각각 191건도 별도 다중집합 대조 결과 차이 0이며 masked-fields.txt도 동일했다. 로그/기록은 `backend/target/r03-group2-validation/{preparation-final,approval,leave,overtime}/`에 보존했다. target을 clean하지 않았다.

순서 경고 합집합은 13개, 기존 13개 밖의 프레임은 0개다. 아래 프레임은 기존 worker-thread 경쟁 또는 M5 CREATE USER 순서 예외이며 비교 규칙을 넓히지 않았다.

| 프레임 | 경고 발생 실행 |
|---|---|
| `M1AcceptanceTest#concurrentIdAllocationIsUniqueAndUnsafeMaxPlusOneCollides()` | 준비 보완, approval, leave, overtime |
| `M2DocumentIntegrationTest#concurrentApprovalAndWithdrawalHaveOnlyOneWinner()` | 준비 보완, approval, leave, overtime |
| `M2DocumentIntegrationTest#concurrentApprovalHasOne409AndNoNotificationFromLosingRequestInTenTrials()` | 준비 보완, approval, leave, overtime |
| `M2DocumentIntegrationTest#documentNumbersAreUniqueForEightDifferentDraftersSubmittingConcurrently()` | 준비 보완, approval, leave, overtime |
| `M2LegacyApprovalRaceTest#unconditionalLegacyApprovalAcceptsBothRequestsInTenControlledRaces()` | 준비 보완, approval, leave, overtime |
| `M3AttendanceTest#concurrentClockInHasExactlyOneWinnerInTenTrials()` | 준비 보완, approval, leave, overtime |
| `M3LeaveAdjustmentConcurrencyTest#unlockedModelFailsThenLockedAdjustmentsAndSubmissionsPreserveBalance()` | 준비 보완, approval, leave, overtime |
| `M3LeaveGrantTest#twoConcurrentBatchRunsIssueEachKeyOnlyOnceInTenTrials()` | 준비 보완, approval, leave, overtime |
| `M4BoardConcurrencyTest#gapsUpdateOneRowSerializeTwentyMovesAndRebalanceOnlyWhenExhausted()` | approval, leave |
| `M4BoardConcurrencyTest#tenConcurrentCreatesHaveContiguousUniqueNumbers()` | 준비 보완, approval, leave, overtime |
| `M4LegacyExperimentTest#fullRenumberUpdatesOneHundredRowsAndUnlockedMovesDuplicateTwentyTimes()` | approval, overtime |
| `M4OverdueTest#concurrentRunsSendOnlyOnce()` | 준비 보완, approval, leave, overtime |
| `M5ProductionTest#<before-all>` | leave, overtime |

### (f) 형식과 SQL 위치 전후

main/test의 일반 120자 초과·여러 문장·여러 선언자·짧은 호출 분할·조각 분할·분리 어노테이션·angle line ends는 모두 0이다. 새 한 줄 단위는 main의 ProjectMember:14–15 기존 예외 1곳, test 0곳이다. DocumentService.nextNumber의 number(...) + 1, LeaveGrantWork의 향상된 for 머리 등 짧아진 단위는 한 줄로 합쳤다.

| 도메인 | 준비 뒤 SQL 값/파일 | 묶음 2 뒤 SQL 값/파일 |
|---|---:|---:|
| org | 0/0 | 0/0 |
| user | 0/0 | 0/0 |
| permission | 0/0 | 0/0 |
| approval | 41/4 | 0/0 |
| leave | 29/4 | 0/0 |
| overtime | 5/1 | 0/0 |
| attendance | 24/6 | 24/6 |
| calendar | 0/0 | 0/0 |
| home | 16/2 | 16/2 |
| notification | 11/2 | 11/2 |
| project | 26/3 | 26/3 |
| task | 25/2 | 25/2 |
| dashboard | 0/0 | 0/0 |
| global | 0/0 | 0/0 |
| 전체 | 177/24 | 102/15 |

오탐 1개: global/config/WebConfig.java의 정확한 문자열 값 `DELETE`는 HTTP 메서드다. ScopeFilter는 명세상 허용 위치로 별도 제외한다. SQL 키워드 없는 조각은 이 카운트만으로 찾지 못하므로 호출 지점 원문 대조를 함께 사용한다.

predicate 호출 위치(선언 제외):

- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:29` — `String where = scope.predicate("u", args);`
- `backend/src/main/java/com/minierp/backend/domain/leave/repository/LeaveQueries.java:136` — `String where = scope.predicate("u", args);`
- `backend/src/main/java/com/minierp/backend/domain/user/repository/EmployeeRepository.java:26` — `String where = scope.predicate("u", args);`
- `backend/src/main/java/com/minierp/backend/global/permission/PermissionAccess.java:109` — `String where = scope(menu, actorId).predicate("u", args);`

서비스에 남은 호출은 묶음 3 대상 AttendanceAdminService 한 곳이다.

### 만든 클래스·메서드와 원래 SQL 위치

원래 위치는 952a64e, 경로는 backend/src/main/java/com/minierp/backend/domain/ 아래다. 같은 메서드로 합친 호출도 원래 위치별로 표시한다.

| 새 메서드 | 원래 파일:줄 | SQL 요약 | 반환 타입 |
|---|---|---|---|
| `DocumentQueries.findDocument` | `approval/service/DocumentService.java:36` | SELECT d.*,f.code AS form_code,f.name AS form_name,f.doc_prefix FROM approval_documents … | `Map<String, Object>` |
| `DocumentQueries.countReadableDocument` | `approval/service/DocumentService.java:59` | SELECT COUNT(*) FROM approval_documents d WHERE d.id=:id AND (d.drafter_id=:actor OR EXI… | `long` |
| `DocumentStore.lockUser` | `approval/service/DocumentService.java:67` | SELECT id FROM users WHERE id=:id FOR UPDATE | `Map<String, Object>` |
| `DocumentStore.lockDocument` | `approval/service/DocumentService.java:71` | SELECT id FROM approval_documents WHERE id=:id FOR UPDATE | `Map<String, Object>` |
| `DocumentQueries.findLines` | `approval/service/DocumentService.java:77` | SELECT * FROM approval_lines WHERE document_id=:id ORDER BY step_order | `List<Map<String, Object>>` |
| `DocumentStore.insertLine` | `approval/service/DocumentService.java:105` | INSERT INTO approval_lines(document_id,step_order,line_kind,approver_id,approver_name,ap… | `int` |
| `DocumentStore.insertDraft` | `approval/service/DocumentService.java:121` | INSERT INTO approval_documents(form_id,drafter_id,drafter_dept_id,title,status,version,m… | `long` |
| `DocumentStore.deleteLines` | `approval/service/DocumentService.java:167` | DELETE FROM approval_lines WHERE document_id=:id | `int` |
| `DocumentStore.updateDraft` | `approval/service/DocumentService.java:169` | UPDATE approval_documents SET title=:title,manual_line=:manual,version=version+1,updated… | `int` |
| `DocumentStore.deleteLines` | `approval/service/DocumentService.java:184` | DELETE FROM approval_lines WHERE document_id=:id | `int` |
| `DocumentStore.deleteDocument` | `approval/service/DocumentService.java:185` | DELETE FROM approval_documents WHERE id=:id | `int` |
| `DocumentStore.insertNumberSequence` | `approval/service/DocumentService.java:189` | INSERT INTO doc_no_sequences(prefix,doc_year,last_seq) VALUES(:prefix,:year,0) ON DUPLIC… | `int` |
| `DocumentStore.lockNumberSequence` | `approval/service/DocumentService.java:193` | SELECT last_seq FROM doc_no_sequences WHERE prefix=:prefix AND doc_year=:year FOR UPDATE | `Map<String, Object>` |
| `DocumentStore.updateNumberSequence` | `approval/service/DocumentService.java:195` | UPDATE doc_no_sequences SET last_seq=:seq WHERE prefix=:prefix AND doc_year=:year | `int` |
| `DocumentStore.insertHistory` | `approval/service/DocumentService.java:201` | INSERT INTO approval_histories(document_id,actor_id,action,comment,created_at) VALUES(:i… | `int` |
| `DocumentStore.updateTransition` | `approval/service/DocumentService.java:207` | UPDATE approval_documents SET status=:status,current_step=:step,completed_at=:completed,… | `int` |
| `DocumentStore.deleteLines` | `approval/service/DocumentService.java:231` | DELETE FROM approval_lines WHERE document_id=:id | `int` |
| `DocumentStore.updateSubmission` | `approval/service/DocumentService.java:234` | UPDATE approval_documents SET doc_no=:no,submitted_at=:now WHERE id=:id | `int` |
| `DocumentQueries.countApprover` | `approval/service/DocumentService.java:262` | SELECT COUNT(*) FROM approval_lines WHERE document_id=:id AND approver_id=:actor AND lin… | `long` |
| `DocumentStore.updateAction` | `approval/service/DocumentService.java:281` | UPDATE approval_lines SET status=:status,acted_at=:now,comment=:comment WHERE id=:id AND… | `int` |
| `DocumentStore.updatePendingLine` | `approval/service/DocumentService.java:292` | UPDATE approval_lines SET status='PENDING' WHERE id=:id AND status='WAITING' | `int` |
| `DocumentQueries.findHistory` | `approval/service/DocumentService.java:369` | SELECT h.action,h.actor_id,u.user_name,h.comment,h.created_at FROM approval_histories h … | `List<Map<String, Object>>` |
| `ApprovalLineQueries.findForm` | `approval/service/ApprovalLineResolver.java:16` | SELECT * FROM approval_forms WHERE code=:code | `Map<String, Object>` |
| `ApprovalLineQueries.findPerson` | `approval/service/ApprovalLineResolver.java:20` | SELECT u.id,u.user_name AS name,p.name AS title,d.name AS department,u.department_id,u.s… | `Map<String, Object>` |
| `ApprovalLineQueries.findSteps` | `approval/service/ApprovalLineResolver.java:61` | SELECT * FROM approval_form_steps WHERE form_id=:id ORDER BY step_order | `List<Map<String, Object>>` |
| `ApprovalLineQueries.findRoleHolder` | `approval/service/ApprovalLineResolver.java:79` | SELECT u.id FROM users u JOIN user_roles ur ON ur.user_id=u.id WHERE ur.role_id=:role AN… | `List<Map<String, Object>>` |
| `ApprovalLineQueries.findDepartmentName` | `approval/service/ApprovalLineResolver.java:94` | SELECT name FROM departments WHERE id=:id | `Map<String, Object>` |
| `ApprovalLineQueries.findDepartmentHead` | `approval/service/ApprovalLineResolver.java:99` | SELECT parent_id,head_user_id FROM departments WHERE id=:id | `Map<String, Object>` |
| `ApprovalLineQueries.findParent` | `approval/service/ApprovalLineResolver.java:165` | SELECT parent_id FROM departments WHERE id=:id | `Map<String, Object>` |
| `FormQueries.findForms` | `approval/service/FormService.java:46` | SELECT f.*,COUNT(CASE WHEN s.line_kind='APPROVER' THEN 1 END) AS approvers, COUNT(CASE W… | `List<Map<String, Object>>` |
| `FormQueries.findSteps` | `approval/service/FormService.java:66` | SELECT s.*,r.code AS role_code,r.name AS role_name,u.user_name FROM approval_form_steps … | `List<Map<String, Object>>` |
| `FormStore.updateOptions` | `approval/service/FormService.java:85` | UPDATE approval_forms SET dedupe_approver=COALESCE(:dedupe,dedupe_approver),fallback_to_… | `int` |
| `FormQueries.findRole` | `approval/service/FormService.java:112` | SELECT id FROM roles WHERE code=:code | `Map<String, Object>` |
| `FormQueries.findUser` | `approval/service/FormService.java:120` | SELECT id FROM users WHERE id=:id | `Map<String, Object>` |
| `FormStore.lockForm` | `approval/service/FormService.java:127` | SELECT id FROM approval_forms WHERE id=:id FOR UPDATE | `Map<String, Object>` |
| `FormStore.deleteSteps` | `approval/service/FormService.java:129` | DELETE FROM approval_form_steps WHERE form_id=:id | `int` |
| `GeneralRequestStore.upsertBody` | `approval/general/GeneralFormHandler.java:28` | INSERT INTO general_requests(document_id,content,amount) VALUES(:id,:content,:amount) ON… | `int` |
| `GeneralRequestStore.findBody` | `approval/general/GeneralFormHandler.java:35` | SELECT content,amount FROM general_requests WHERE document_id=:id | `Map<String, Object>` |
| `GeneralRequestStore.copyBody` | `approval/general/GeneralFormHandler.java:40` | INSERT INTO general_requests(document_id,content,amount) SELECT :target,content,amount F… | `int` |
| `GeneralRequestStore.deleteBody` | `approval/general/GeneralFormHandler.java:46` | DELETE FROM general_requests WHERE document_id=:id | `int` |
| `FormStore.insertStep` | `approval/service/FormService.java:131` | INSERT INTO approval_form_steps(form_id,step_order,line_kind,approver_type,role_id,user_… | `int` |
| `LeaveStore.lockUser` | `leave/service/LeaveAdminService.java:88` | SELECT id FROM users WHERE id=:id FOR UPDATE | `Map<String, Object>` |
| `LeaveStore.insertAdjustment` | `leave/service/LeaveAdminService.java:97` | INSERT INTO leave_ledger(user_id,leave_year,entry_type,days,memo,created_by,created_at) … | `long` |
| `LeaveQueries.findGrantRuns` | `leave/service/LeaveAdminService.java:121` | SELECT id,job_name AS jobName,target_date AS targetDate,status,processed_count AS proces… | `List<Map<String, Object>>` |
| `LeaveQueries.findBalance` | `leave/service/LeaveQueryService.java:38` | SELECT COALESCE(SUM(CASE WHEN entry_type='GRANT' THEN days ELSE 0 END),0) AS granted, -C… | `Map<String, Object>` |
| `LeaveQueries.findGrantCandidates` | `leave/service/LeaveGrantWork.java:27` | SELECT id FROM users WHERE status IN ('ACTIVE','ON_LEAVE') ORDER BY id | `List<Map<String, Object>>` |
| `LeaveStore.lockGrantUser` | `leave/service/LeaveGrantWork.java:29` | SELECT id,status,hire_date FROM users WHERE id=:id FOR UPDATE | `Map<String, Object>` |
| `LeaveQueries.countLegacyGrants` | `leave/service/LeaveGrantWork.java:38` | SELECT COUNT(*) FROM leave_ledger WHERE user_id=:id AND leave_year=:year AND entry_type=… | `long` |
| `LeaveQueries.findGrantKeys` | `leave/service/LeaveGrantWork.java:45` | SELECT grant_key FROM leave_ledger WHERE user_id=:id AND grant_key IS NOT NULL | `List<Map<String, Object>>` |
| `LeaveStore.insertGrant` | `leave/service/LeaveGrantWork.java:54` | INSERT INTO leave_ledger(user_id,leave_year,entry_type,days,grant_key,memo,created_at) V… | `int` |
| `LeaveQueries.findHandoverUser` | `leave/handler/LeaveFormHandler.java:40` | SELECT id FROM users WHERE id=:id | `Map<String, Object>` |
| `LeaveStore.upsertBody` | `leave/handler/LeaveFormHandler.java:42` | INSERT INTO leave_requests(document_id,leave_type,start_date,end_date,used_days,handover… | `int` |
| `LeaveQueries.findRequest` | `leave/handler/LeaveFormHandler.java:51` | SELECT * FROM leave_requests WHERE document_id=:id | `Map<String, Object>` |
| `LeaveQueries.findHandoverName` | `leave/handler/LeaveFormHandler.java:59` | SELECT id,user_name AS name FROM users WHERE id=:id | `Map<String, Object>` |
| `LeaveStore.copyBody` | `leave/handler/LeaveFormHandler.java:67` | INSERT INTO leave_requests(document_id,leave_type,start_date,end_date,used_days,handover… | `int` |
| `LeaveStore.deleteBody` | `leave/handler/LeaveFormHandler.java:73` | DELETE FROM leave_requests WHERE document_id=:id | `int` |
| `LeaveQueries.findAvailable` | `leave/handler/LeaveFormHandler.java:77` | SELECT COALESCE(SUM(days),0) AS available FROM leave_ledger WHERE user_id=:user AND leav… | `Map<String, Object>` |
| `LeaveQueries.countOverlap` | `leave/handler/LeaveFormHandler.java:112` | SELECT COUNT(*) FROM leave_requests l JOIN approval_documents d ON d.id=l.document_id WH… | `long` |
| `LeaveStore.insertLedger` | `leave/handler/LeaveFormHandler.java:124` | INSERT INTO leave_ledger(user_id,leave_year,entry_type,days,document_id,memo,created_at)… | `int` |
| `LeaveStore.updateUsedDays` | `leave/handler/LeaveFormHandler.java:134` | UPDATE leave_requests SET used_days=:days WHERE document_id=:id | `int` |
| `LeaveQueries.findReservations` | `leave/handler/LeaveFormHandler.java:140` | SELECT leave_year,-SUM(days) AS days FROM leave_ledger WHERE document_id=:id AND entry_t… | `List<Map<String, Object>>` |
| `LeaveQueries.countBalances` | `leave/service/LeaveAdminService.java:51` | SELECT COUNT(*) FROM users u WHERE " + where | `long` |
| `LeaveQueries.findBalances` | `leave/service/LeaveAdminService.java:52` | SELECT u.id,u.user_name,p.name AS title,d.name AS department,u.hire_date, COALESCE(l.gra… | `List<Map<String, Object>>` |
| `LeaveQueries.findAdjustmentBalance` | `leave/service/LeaveAdminService.java:90` | SELECT COALESCE(SUM(days),0) AS balance FROM leave_ledger WHERE user_id=:id AND leave_ye… | `Map<String, Object>` |
| `LeaveQueries.findLedger` | `leave/service/LeaveQueryService.java:68` | SELECT l.*,d.doc_no,d.status FROM ( SELECT x.*,SUM(x.days) OVER(ORDER BY x.created_at,x.… | `List<Map<String, Object>>` |
| `OvertimeStore.upsertBody` | `overtime/handler/OvertimeFormHandler.java:32` | INSERT INTO overtime_requests(document_id,overtime_date,start_time,end_time,reason,creat… | `int` |
| `OvertimeStore.findBody` | `overtime/handler/OvertimeFormHandler.java:41` | SELECT overtime_date AS overtimeDate,TIME_FORMAT(start_time,'%H:%i:%s') AS startTime,TIM… | `Map<String, Object>` |
| `OvertimeStore.copyBody` | `overtime/handler/OvertimeFormHandler.java:47` | INSERT INTO overtime_requests(document_id,overtime_date,start_time,end_time,reason,creat… | `int` |
| `OvertimeStore.deleteBody` | `overtime/handler/OvertimeFormHandler.java:53` | DELETE FROM overtime_requests WHERE document_id=:id | `int` |
| `OvertimeStore.countValidTime` | `overtime/handler/OvertimeFormHandler.java:57` | SELECT COUNT(*) FROM overtime_requests WHERE document_id=:id AND end_time>start_time | `long` |

### 재현 명령

```bash
# 먼저 952a64e를 작업 트리 밖에 git archive하고, 그 사본에서 UTC test-compile한다.
# JAVA_HOME은 설치된 Java 17로 지정한다. root에는 저장소 최상위 경로를 넘긴다.
python3 docs/refactoring/R-03-sql-location-measure.py --root "$PWD" \
  --baseline "$BASE_DIR" --allow-count-reductions /tmp/r03-reductions.json \
  --output /tmp/r03-location.json
python3 docs/refactoring/R-03-format-units.py --root "$PWD" --output /tmp/r03-units.json
python3 docs/refactoring/R-02-format-measure.py --root "$PWD" --output /tmp/r03-format.json
# --file을 해당 묶음의 원래 서비스/handler별로 반복한다.
python3 docs/refactoring/R-03-call-site-check.py --before "$BASE_DIR" --after "$PWD" \
  --classpath backend/target/r03-group2-validation/approval/recording/classpath.txt \
  --file backend/src/main/java/com/minierp/backend/domain/approval/service/DocumentService.java \
  --output /tmp/r03-call-sites.json
# backend에서 실행(각 도메인 커밋마다):
./mvnw -Duser.timezone=UTC -Drefactoring.record=target/r03-review-recording test
./mvnw -Duser.timezone=UTC test
```

repository 검사에는 새 파일 경로마다 `--check-repository`를 추가한다. 기록 비교는 기존 SnapshotComparator와 recording/classpath.txt를 그대로 사용한다. 그룹1 전체 호출은 OrgService·WorkingDays·EmployeeService·UserLookupService·RoleService 5개 파일을 --file로 지정하면 87개다. 클래스패스 대상 사본은 먼저 test-compile해야 한다.

### 남은 예외 위치와 한계

**main 기존 형식 예외(최종 위치)**

literal_exceptions: 88개

- `backend/src/main/java/com/minierp/backend/domain/approval/repository/ApprovalLineQueries.java:32`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/DocumentQueries.java:17`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/DocumentQueries.java:36`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/DocumentQueries.java:42`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/DocumentStore.java:67`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/DocumentStore.java:77`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/DocumentStore.java:93`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/DocumentStore.java:105`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/DocumentStore.java:116`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/FormQueries.java:26`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/FormStore.java:17`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/FormStore.java:31`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/GeneralRequestStore.java:17`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/GeneralRequestStore.java:28`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/InboxRepository.java:21`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/InboxRepository.java:23`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/InboxRepository.java:25`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/InboxRepository.java:29`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/InboxRepository.java:43`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/InboxRepository.java:49`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/InboxRepository.java:69`
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
- `backend/src/main/java/com/minierp/backend/domain/leave/repository/LeaveQueries.java:19`
- `backend/src/main/java/com/minierp/backend/domain/leave/repository/LeaveQueries.java:39`
- `backend/src/main/java/com/minierp/backend/domain/leave/repository/LeaveQueries.java:76`
- `backend/src/main/java/com/minierp/backend/domain/leave/repository/LeaveStore.java:23`
- `backend/src/main/java/com/minierp/backend/domain/leave/repository/LeaveStore.java:34`
- `backend/src/main/java/com/minierp/backend/domain/leave/repository/LeaveStore.java:56`
- `backend/src/main/java/com/minierp/backend/domain/leave/repository/LeaveStore.java:73`
- `backend/src/main/java/com/minierp/backend/domain/notification/service/NotificationService.java:19`
- `backend/src/main/java/com/minierp/backend/domain/notification/service/NotificationService.java:27`
- `backend/src/main/java/com/minierp/backend/domain/notification/service/NotificationService.java:34`
- `backend/src/main/java/com/minierp/backend/domain/org/repository/OrgQueries.java:36`
- `backend/src/main/java/com/minierp/backend/domain/org/repository/OrgQueries.java:50`
- `backend/src/main/java/com/minierp/backend/domain/org/repository/OrgQueries.java:72`
- `backend/src/main/java/com/minierp/backend/domain/org/repository/OrgStore.java:102`
- `backend/src/main/java/com/minierp/backend/domain/overtime/entity/OvertimeRequest.java:25`
- `backend/src/main/java/com/minierp/backend/domain/overtime/entity/OvertimeRequest.java:37`
- `backend/src/main/java/com/minierp/backend/domain/overtime/repository/OvertimeStore.java:26`
- `backend/src/main/java/com/minierp/backend/domain/overtime/repository/OvertimeStore.java:32`
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
- `backend/src/main/java/com/minierp/backend/domain/user/repository/EmployeeQueries.java:35`
- `backend/src/main/java/com/minierp/backend/domain/user/repository/EmployeeQueries.java:84`
- `backend/src/main/java/com/minierp/backend/domain/user/repository/EmployeeStore.java:22`
- `backend/src/main/java/com/minierp/backend/domain/user/repository/EmployeeStore.java:32`
- `backend/src/main/java/com/minierp/backend/domain/user/repository/EmployeeStore.java:81`
- `backend/src/main/java/com/minierp/backend/domain/user/repository/EmployeeStore.java:88`
- `backend/src/main/java/com/minierp/backend/global/batch/BatchRunRecorder.java:27`
- `backend/src/main/java/com/minierp/backend/global/batch/BatchRunRecorder.java:31`

non_chain_breaks: 1개

- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceStore.java:102`

key_value_splits: 2개

- `backend/src/main/java/com/minierp/backend/domain/attendance/handler/AttendanceFixFormHandler.java:60`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceStore.java:140`

protected_fragment_splits: 3개

- `backend/src/main/java/com/minierp/backend/domain/attendance/handler/AttendanceFixFormHandler.java:60`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceStore.java:102`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceStore.java:140`

**test 기존 형식 예외(최종 위치)**

literal_exceptions: 78개

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

key_value_splits: 8개

- `backend/src/test/java/com/minierp/backend/support/refactoring/RecordingOrder.java:17`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:14`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:16`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:18`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:20`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:22`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:24`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:26`

outer_operator_unsplit: 1개

- `backend/src/test/java/com/minierp/backend/integration/M4BoardConcurrencyTest.java:164`

protected_fragment_splits: 1개

- `backend/src/test/java/com/minierp/backend/integration/M1DataMigrationTest.java:35`

ternary_layout: 1개

- `backend/src/test/java/com/minierp/backend/integration/M1DataMigrationTest.java:35`

- 새 기능 버그는 확인하지 않았다. 묶음 1의 타입 문제 두 곳은 사용자 추가 지시 범위에서만 보완했다. 기존 알려진 이슈와 묶음 3의 SQL은 그대로다.
- 안전망은 기존 테스트가 실행한 경로를 확인한다. 호출 도구는 소스 내 간접 map·지역 변수·record 전달의 일반적인 데이터 흐름 증명기가 아니므로 위 5곳 및 묶음 1의 3곳을 수동 대조했다. 정적 검사만으로 운영 성능·락 대기 시간을 보장하지 않는다.
- 개발 DB/Redis·실제 서버 검증은 하지 않았다(금지 범위). frontend, 마이그레이션 V1~V6, .claude, 기준선·정책·비교기는 변경하지 않았다. backend/md는 허용된 develop 병합 한 파일 외에 변경하지 않았다.
- 묶음 2까지 마쳤으며 묶음 3은 리뷰 후 별도 지시를 기다린다.


## 묶음 2 리뷰 보완

시작은 `64b5555`이며 당시에는 develop 병합을 하지 않았다. 이후 `3fb2902`에서 명세 17 8차를 병합했고, `632449c`에서 9차를 먼저 병합했다. 이어진 이 리뷰 보완 커밋에서 미사용 import 13곳(리뷰 목록 8곳과 추가 발견 5곳)과 쿼리 메서드·매개변수 이름을 정리했다. 이 보완 커밋을 4단계 시작 기준으로 삼으며 해시는 R-04 보고서에 기록한다. 준비 보완 `5584474`에서 DocumentQueries·FormQueries·LeaveQueries의 닫는 줄 4곳을 복원하고, LeaveAdminService/LeaveQueries의 `prefix`를 `pattern`, LeaveQueryService의 `filter`를 `entryType`으로 바꿨다. 값 이름이 달라진 기존 7곳은 그대로 뒀다. 닫는 줄 검사: 원본 64b5555 **4건 → 준비 후보 0건**.

기록 ON: **231/0/0/0**, 578.23초, `REFACTORING_COMPARE differences=0 sqlOrderChanges=12`. 순서 경고는 모두 기존 13개 목록 안이다. 준비에는 지시대로 OFF를 추가 실행하지 않았다. 문자열 집합 1,314개·@Transactional 83개·허용 목록·형식·호출 지점은 모두 통과했다.

### 도구 보완

- `298be1b`: `R-03-call-site-check.py`는 map 이후 인자 토큰열(ErrorCode 포함)도 대조하고, `--moved 옛경로=새경로`로 같은 메서드의 직접 SQL 호출을 짝짓는다. SQL 식·키·값 순서·standalone 타입 및 기존 put 대조는 유지했다.
- 정상 묶음 1 **87개(자동 84·수동 3), 차이 0**, 묶음 2 **70개(자동 65·수동 5), 차이 0**. DocumentStore.lockDocument의 ErrorCode만 USER_NOT_FOUND로 바꾼 작업 트리 밖 사본은 **차이 1, 종료 1**이었다. 사본은 저장소에 적용하지 않았다.
- `R-03-text-block-check.py`: 952a64e의 SQL 호출과 옮긴 텍스트 블록을 연결하고, 닫는 줄 뒤 인자 토큰이 같은 경우 물리적인 닫는 줄을 비교한다. SQL 호출 밖의 `> 0`·`.stream()`은 서비스에 남아도 위반으로 세지 않는다. 인자 이름이 달라진 경우는 별도 목록으로 남긴다.
- `R-03-package-move-check.py`: 옮긴 클래스는 package/import 줄만, 바뀐 테스트는 import 줄만 제외하고 나머지 원문을 대조한다. 빈 줄도 비교한다.
- `R-03-sql-location-measure.py`: `--rename-class`로 ProjectStateStore의 트랜잭션 소유자만 대응한다. `--preserve-repository`는 원본에도 존재하는 지정된 기존 위반 3개만 별도 출력한다. 새로운 위반은 허용하지 않는다. 키워드 없는 파라미터·비교식 조각도 별도 수집한다.
- 프로젝트 후보 검사 중 `ScopeFilter.scope()` getter가 권한을 계산하는 `access.scope(...)`처럼 집계되는 오탐 2건을 발견했다. 명시된 ScopeFilter 변수의 무인자 scope 접근자만 구분하도록 프로젝트 커밋에서 도구를 추가 보완했다. `access.scope("PROJECT")` 등 권한 조회는 여전히 금지 호출로 탐지한다. 기록기·비교기·기준선은 바꾸지 않았다.
- `HH:mm`의 콜론은 SQL 바인딩이 아니다. 앞 문자가 식별자 문자인 콜론을 제외한다. 시작점에서는 지시에 적힌 4개 출현 외에 NotificationQueryService의 `user_id=:user` 1개도 발견했다. 모두 이동했다.
- 도구 전용 커밋은 별도 git archive에서 test-compile 종료 0, 11.05초였다.

## 묶음 3

### 범위와 커밋

`notification · attendance · project · task · home`의 SQL 이동까지다. calendar·dashboard는 SQL이 없어 변경하지 않았다. 묶음 3을 마치고 리뷰를 기다리며 4단계는 시작하지 않는다.

|커밋|묶음|추가·변경·삭제 경로 수|
|---|---|---|
|5584474|묶음 2 Java 리뷰 보완|5|
|298be1b|읽기 전용 측정 도구|4|
|d26988f|moved|23|
|fdf3fb6|notification|4|
|52cc02c|attendance|7|
|41ecb91|project|4|
|d6b2354|task|4|
|292f6fd|home|2|
|이 문서 커밋|R-03 보고서|1|

패키지 이동의 수는 삭제·새 경로를 각각 센다. Git rename 표시에서는 5쌍이 이동으로 표시된다.

### 커밋별 (a)~(g) 검증

후보는 `/private/tmp/r03-group3/`에서 만들었다. 정적 검사를 통과한 파일의 SHA-256을 고정하고 적용했다. 각 코드 커밋은 ON과 OFF 전체 UTC 테스트가 끝난 뒤 만들었다. 비교 기준은 누적 `952a64e`다. Java 17, Testcontainers MariaDB만 사용했다.

|후보|문자열 출현|서로 다른 값|새 값/사라진 값 (a)|트랜잭션 (b)|신규 위반 (c)|호출 자동/수동/차이 (g)|120자 일반 초과/기존 한 줄 예외/닫는 줄 차이 (f)|
|---|---|---|---|---|---|---|---|
|moved|3216|1314|0 / 0|83 동일|0 + 기존 3|11 / 0 / 0|0 / 1 / 0|
|notification|3216|1314|0 / 0|83 동일|0 + 기존 3|7 / 2 / 0|0 / 1 / 0|
|attendance|3216|1314|0 / 0|83 동일|0 + 기존 3|12 / 0 / 0|0 / 1 / 0|
|project|3214|1314|0 / 0|83 동일|0 + 기존 3|15 / 3 / 0|0 / 1 / 0|
|task|3226|1314|0 / 0|83 동일|0 + 기존 3|18 / 7 / 0|0 / 1 / 0|
|home|3226|1314|0 / 0|83 동일|0 + 기존 3|0 / 8 / 0|0 / 1 / 0|

근태 표의 호출 수는 서비스·handler 12개다. 기존 AttendanceMonthlyQuery.fetch → findMonthly의 간접 SQL 호출 1개는 아래에서 추가 수동 대조했다. 패키지 이동 커밋의 직접 호출 11개에는 기존 fetch가 포함된다. 모든 후보는 축소 허용 목록의 누락·초과 0이다.

|커밋|후보|ON 전체/실패/오류/건너뜀|ON 초|기록 비교 (d)|오류 응답 수/차이|OFF 전체/실패/오류/건너뜀|OFF 초 (e)|OFF 기록 출력|
|---|---|---|---|---|---|---|---|---|
|d26988f|moved|231/0/0/0|784.62|REFACTORING_COMPARE differences=0 sqlOrderChanges=11|191 / 0|231/0/0/0|656.17|0|
|fdf3fb6|notification|231/0/0/0|569.24|REFACTORING_COMPARE differences=0 sqlOrderChanges=11|191 / 0|231/0/0/0|517.23|0|
|52cc02c|attendance|231/0/0/0|552.36|REFACTORING_COMPARE differences=0 sqlOrderChanges=10|191 / 0|231/0/0/0|530.95|0|
|41ecb91|project|231/0/0/0|492.81|REFACTORING_COMPARE differences=0 sqlOrderChanges=10|191 / 0|231/0/0/0|497.95|0|
|d6b2354|task|231/0/0/0|524.09|REFACTORING_COMPARE differences=0 sqlOrderChanges=12|191 / 0|231/0/0/0|465.35|0|
|292f6fd|home|231/0/0/0|488.08|REFACTORING_COMPARE differences=0 sqlOrderChanges=10|191 / 0|231/0/0/0|460.6|0|

로그와 정적 결과: `backend/target/r03-group3-validation/{preparation,moved,notification,attendance,project,task,home}/`. target은 커밋하지 않는다. 시간은 해당 실행 환경의 관측값이다.

#### 순서 경고 프레임

모든 실행에서 기존 13개 범위를 벗어난 경고는 0이다. SQL별 횟수·응답·직렬 순서 규칙은 기존 비교기 그대로 적용했다.

- preparation: `M1AcceptanceTest#concurrentIdAllocationIsUniqueAndUnsafeMaxPlusOneCollides()`, `M2DocumentIntegrationTest#concurrentApprovalAndWithdrawalHaveOnlyOneWinner()`, `M2DocumentIntegrationTest#concurrentApprovalHasOne409AndNoNotificationFromLosingRequestInTenTrials()`, `M2DocumentIntegrationTest#documentNumbersAreUniqueForEightDifferentDraftersSubmittingConcurrently()`, `M2LegacyApprovalRaceTest#unconditionalLegacyApprovalAcceptsBothRequestsInTenControlledRaces()`, `M3AttendanceTest#concurrentClockInHasExactlyOneWinnerInTenTrials()`, `M3LeaveAdjustmentConcurrencyTest#unlockedModelFailsThenLockedAdjustmentsAndSubmissionsPreserveBalance()`, `M3LeaveGrantTest#twoConcurrentBatchRunsIssueEachKeyOnlyOnceInTenTrials()`, `M4BoardConcurrencyTest#gapsUpdateOneRowSerializeTwentyMovesAndRebalanceOnlyWhenExhausted()`, `M4BoardConcurrencyTest#tenConcurrentCreatesHaveContiguousUniqueNumbers()`, `M4OverdueTest#concurrentRunsSendOnlyOnce()`, `M5ProductionTest#<before-all>`

- moved: `M1AcceptanceTest#concurrentIdAllocationIsUniqueAndUnsafeMaxPlusOneCollides()`, `M2DocumentIntegrationTest#concurrentApprovalAndWithdrawalHaveOnlyOneWinner()`, `M2DocumentIntegrationTest#concurrentApprovalHasOne409AndNoNotificationFromLosingRequestInTenTrials()`, `M2DocumentIntegrationTest#documentNumbersAreUniqueForEightDifferentDraftersSubmittingConcurrently()`, `M2LegacyApprovalRaceTest#unconditionalLegacyApprovalAcceptsBothRequestsInTenControlledRaces()`, `M3AttendanceTest#concurrentClockInHasExactlyOneWinnerInTenTrials()`, `M3LeaveAdjustmentConcurrencyTest#unlockedModelFailsThenLockedAdjustmentsAndSubmissionsPreserveBalance()`, `M3LeaveGrantTest#twoConcurrentBatchRunsIssueEachKeyOnlyOnceInTenTrials()`, `M4BoardConcurrencyTest#tenConcurrentCreatesHaveContiguousUniqueNumbers()`, `M4LegacyExperimentTest#fullRenumberUpdatesOneHundredRowsAndUnlockedMovesDuplicateTwentyTimes()`, `M4OverdueTest#concurrentRunsSendOnlyOnce()`

- notification: `M1AcceptanceTest#concurrentIdAllocationIsUniqueAndUnsafeMaxPlusOneCollides()`, `M2DocumentIntegrationTest#concurrentApprovalAndWithdrawalHaveOnlyOneWinner()`, `M2DocumentIntegrationTest#concurrentApprovalHasOne409AndNoNotificationFromLosingRequestInTenTrials()`, `M2DocumentIntegrationTest#documentNumbersAreUniqueForEightDifferentDraftersSubmittingConcurrently()`, `M2LegacyApprovalRaceTest#unconditionalLegacyApprovalAcceptsBothRequestsInTenControlledRaces()`, `M3AttendanceTest#concurrentClockInHasExactlyOneWinnerInTenTrials()`, `M3LeaveAdjustmentConcurrencyTest#unlockedModelFailsThenLockedAdjustmentsAndSubmissionsPreserveBalance()`, `M3LeaveGrantTest#twoConcurrentBatchRunsIssueEachKeyOnlyOnceInTenTrials()`, `M4BoardConcurrencyTest#tenConcurrentCreatesHaveContiguousUniqueNumbers()`, `M4OverdueTest#concurrentRunsSendOnlyOnce()`, `M5ProductionTest#<before-all>`

- attendance: `M1AcceptanceTest#concurrentIdAllocationIsUniqueAndUnsafeMaxPlusOneCollides()`, `M2DocumentIntegrationTest#concurrentApprovalHasOne409AndNoNotificationFromLosingRequestInTenTrials()`, `M2DocumentIntegrationTest#documentNumbersAreUniqueForEightDifferentDraftersSubmittingConcurrently()`, `M2LegacyApprovalRaceTest#unconditionalLegacyApprovalAcceptsBothRequestsInTenControlledRaces()`, `M3AttendanceTest#concurrentClockInHasExactlyOneWinnerInTenTrials()`, `M3LeaveAdjustmentConcurrencyTest#unlockedModelFailsThenLockedAdjustmentsAndSubmissionsPreserveBalance()`, `M3LeaveGrantTest#twoConcurrentBatchRunsIssueEachKeyOnlyOnceInTenTrials()`, `M4BoardConcurrencyTest#gapsUpdateOneRowSerializeTwentyMovesAndRebalanceOnlyWhenExhausted()`, `M4BoardConcurrencyTest#tenConcurrentCreatesHaveContiguousUniqueNumbers()`, `M4OverdueTest#concurrentRunsSendOnlyOnce()`

- project: `M1AcceptanceTest#concurrentIdAllocationIsUniqueAndUnsafeMaxPlusOneCollides()`, `M2DocumentIntegrationTest#concurrentApprovalHasOne409AndNoNotificationFromLosingRequestInTenTrials()`, `M2DocumentIntegrationTest#documentNumbersAreUniqueForEightDifferentDraftersSubmittingConcurrently()`, `M2LegacyApprovalRaceTest#unconditionalLegacyApprovalAcceptsBothRequestsInTenControlledRaces()`, `M3AttendanceTest#concurrentClockInHasExactlyOneWinnerInTenTrials()`, `M3LeaveAdjustmentConcurrencyTest#unlockedModelFailsThenLockedAdjustmentsAndSubmissionsPreserveBalance()`, `M3LeaveGrantTest#twoConcurrentBatchRunsIssueEachKeyOnlyOnceInTenTrials()`, `M4BoardConcurrencyTest#tenConcurrentCreatesHaveContiguousUniqueNumbers()`, `M4OverdueTest#concurrentRunsSendOnlyOnce()`, `M5ProductionTest#<before-all>`

- task: `M1AcceptanceTest#concurrentIdAllocationIsUniqueAndUnsafeMaxPlusOneCollides()`, `M2DocumentIntegrationTest#concurrentApprovalAndWithdrawalHaveOnlyOneWinner()`, `M2DocumentIntegrationTest#concurrentApprovalHasOne409AndNoNotificationFromLosingRequestInTenTrials()`, `M2DocumentIntegrationTest#documentNumbersAreUniqueForEightDifferentDraftersSubmittingConcurrently()`, `M2LegacyApprovalRaceTest#unconditionalLegacyApprovalAcceptsBothRequestsInTenControlledRaces()`, `M3AttendanceTest#concurrentClockInHasExactlyOneWinnerInTenTrials()`, `M3LeaveAdjustmentConcurrencyTest#unlockedModelFailsThenLockedAdjustmentsAndSubmissionsPreserveBalance()`, `M3LeaveGrantTest#twoConcurrentBatchRunsIssueEachKeyOnlyOnceInTenTrials()`, `M4BoardConcurrencyTest#gapsUpdateOneRowSerializeTwentyMovesAndRebalanceOnlyWhenExhausted()`, `M4BoardConcurrencyTest#tenConcurrentCreatesHaveContiguousUniqueNumbers()`, `M4LegacyExperimentTest#fullRenumberUpdatesOneHundredRowsAndUnlockedMovesDuplicateTwentyTimes()`, `M4OverdueTest#concurrentRunsSendOnlyOnce()`

- home: `M1AcceptanceTest#concurrentIdAllocationIsUniqueAndUnsafeMaxPlusOneCollides()`, `M2DocumentIntegrationTest#concurrentApprovalHasOne409AndNoNotificationFromLosingRequestInTenTrials()`, `M2DocumentIntegrationTest#documentNumbersAreUniqueForEightDifferentDraftersSubmittingConcurrently()`, `M2LegacyApprovalRaceTest#unconditionalLegacyApprovalAcceptsBothRequestsInTenControlledRaces()`, `M3AttendanceTest#concurrentClockInHasExactlyOneWinnerInTenTrials()`, `M3LeaveAdjustmentConcurrencyTest#unlockedModelFailsThenLockedAdjustmentsAndSubmissionsPreserveBalance()`, `M3LeaveGrantTest#twoConcurrentBatchRunsIssueEachKeyOnlyOnceInTenTrials()`, `M4BoardConcurrencyTest#gapsUpdateOneRowSerializeTwentyMovesAndRebalanceOnlyWhenExhausted()`, `M4BoardConcurrencyTest#tenConcurrentCreatesHaveContiguousUniqueNumbers()`, `M4OverdueTest#concurrentRunsSendOnlyOnce()`

### 패키지 이동과 보존한 코드

패키지 이동 검사는 5개 모두 차이 0, 테스트 본문 검사도 차이 0이다. 최종 후보에서 AttendanceStore·ProjectStateStore·ProjectSummaryQuery는 package/import를 제외한 본문이 원문과 같다. HomeQueries의 기존 public static 멤버 토큰열도 전부 같다. 테스트 79개 파일 본문과 비 Java 테스트·메인 리소스 26개가 보존됐다.

|클래스|새 패키지|이번에 남긴 SQL 외 코드|
|---|---|---|
|AttendanceStore|attendance.repository|Schedule.view(), time()의 LocalTime.parse, day 판정, AttendanceRules.judge 호출, 날짜·시각 결과 처리|
|AttendanceMonthlyQuery|attendance.repository|sql(String) 원문·public 시그니처 유지; fetch만 findMonthly로 교체하고 범위·map 조립을 이동|
|HomeQueries|home.repository|public static SQL 멤버 유지; final/private 생성자만 제거하고 @Repository·@RequiredArgsConstructor·실행 메서드 추가|
|ProjectStateStore|project.repository|상태 집계 후 계산, Clock 조회, @Transactional(MANDATORY) 유지|
|ProjectSummaryQuery|project.repository|기존 정적 SQL 조립과 public static 멤버 유지|

기존 repository 예외: AttendanceStore.time의 `LocalTime.parse`(P), ProjectStateStore.recalculate의 `@Transactional(MANDATORY)`(T), 같은 메서드의 `LocalDateTime.now`(V). 세 예외 밖의 X/T/P/V 위반은 0이다. 이 코드들의 추가 분리는 다음 단계 후보이며 이번에 변경하지 않았다.

테스트 import: M3MonthlyTest(AttendanceMonthlyQuery), M4BoardConcurrencyTest(ProjectStateStore), M5HomeCountersTest(AttendanceStore), M4HomeGrowthTest(HomeQueries·ProjectSummaryQuery), M4QueryGrowthTest(ProjectSummaryQuery). 지시 목록에 없던 M3BatchTest는 기존 `attendance.service.*` 때문에 AttendanceStore의 명시적 import 1줄이 추가로 필요했다. 총 6개 파일·7개 import이며 본문은 그대로다.

### 새 실행 메서드와 원래 위치

아래 원래 위치는 64b5555의 소스다. 같은 SQL의 복수 호출은 모두 표시한다. SQL 본문은 전체 복사하지 않고 식의 앞부분만 요약한다. 반환 타입과 전체 map 대조는 methods/calls JSON에도 남아 있다.

|쿼리 메서드|원래 파일:줄|SQL 요약|반환 타입|
|---|---|---|---|
|NotificationStore.insertDocumentNotification|NotificationService.java:18|`"INSERT INTO notifications(user_id,document_id,type,title,link,created_at) VALUES(:user,:doc,:type,:title,:lin…`|int|
|NotificationStore.insertTaskNotification|NotificationService.java:26|`"INSERT INTO notifications(user_id,document_id,type,title,link,created_at) VALUES(:user,NULL,'TASK_ASSIGNED',:…`|int|
|NotificationStore.insertOverdueNotification|NotificationService.java:33|`"INSERT INTO notifications(user_id,document_id,type,title,link,created_at) VALUES(:user,NULL,'TASK_OVERDUE',:t…`|int|
|NotificationQueries.countUnread|NotificationQueryService.java:40|`"SELECT COUNT(*) FROM notifications WHERE user_id=:user AND read_at IS NULL"`|long|
|NotificationQueries.findOwner|NotificationQueryService.java:46|`"SELECT user_id FROM notifications WHERE id=:id"`|Map&lt;String, Object&gt;|
|NotificationStore.updateRead|NotificationQueryService.java:50|`"UPDATE notifications SET read_at=COALESCE(read_at,:now) WHERE id=:id AND user_id=:user"`|int|
|NotificationStore.updateAllRead|NotificationQueryService.java:56|`"UPDATE notifications SET read_at=:now WHERE user_id=:user AND read_at IS NULL"`|int|
|NotificationQueries.countNotifications|NotificationQueryService.java:27|`"SELECT COUNT(*) FROM notifications WHERE " + where`|long|
|NotificationQueries.findNotifications|NotificationQueryService.java:28|`"SELECT id,type,title,link,read_at,created_at FROM notifications WHERE " + where + " ORDER BY id DESC LIMIT :s…`|List&lt;Map&lt;String, Object&gt;&gt;|
|AttendanceQueries.findMonthlyCounts|AttendanceService.java:30|`"SELECT COALESCE(SUM(in_result='LATE'),0) AS late_count,COALESCE(SUM(absent=1),0) AS absent_count FROM attenda…`|Map&lt;String, Object&gt;|
|AttendanceQueries.findMyMonth|AttendanceService.java:134|`"SELECT a.*,d.doc_no AS fix_doc_no FROM attendances a LEFT JOIN approval_documents d ON d.id=a.fix_document_id…`|List&lt;Map&lt;String, Object&gt;&gt;|
|AttendanceQueries.findDepartment|AttendanceAdminService.java:34|`"SELECT id FROM departments WHERE id=:id"`|Map&lt;String, Object&gt;|
|AttendanceQueries.findUser|AttendanceAdminService.java:108|`"SELECT id,user_name,COALESCE(hire_date,DATE(created_at)) AS employed_from,retired_date FROM users WHERE id=:i…`|Map&lt;String, Object&gt;|
|AttendanceQueries.findAbsenceCandidates|AbsenceBatchWork.java:24|`""" SELECT u.id FROM users u WHERE (u.status<>'ON_LEAVE' AND COALESCE(u.hire_date,DATE(u.created_at))<=:date A…`|List&lt;Map&lt;String, Object&gt;&gt;|
|AttendanceFixStore.updateBody|AttendanceFixFormHandler.java:57|`""" INSERT INTO attendance_fix_requests(document_id,work_date,clock_in,clock_out,reason) VALUES(:id,:date,:in,…`|int|
|AttendanceFixStore.findBody|AttendanceFixFormHandler.java:65|`"SELECT * FROM attendance_fix_requests WHERE document_id=:id"`|Map&lt;String, Object&gt;|
|AttendanceFixStore.insertBodyCopy|AttendanceFixFormHandler.java:88|`"INSERT INTO attendance_fix_requests(document_id,work_date,clock_in,clock_out,reason) SELECT :target,work_date…`|int|
|AttendanceFixStore.deleteBody|AttendanceFixFormHandler.java:94|`"DELETE FROM attendance_fix_requests WHERE document_id=:id"`|int|
|AttendanceFixStore.countOtherPending|AttendanceFixFormHandler.java:110|`"SELECT COUNT(*) FROM attendance_fix_requests f JOIN approval_documents d ON d.id=f.document_id WHERE d.drafte…`|long|
|AttendanceFixStore.updateBeforeSnapshot|AttendanceFixFormHandler.java:131|`"UPDATE attendance_fix_requests SET before_json=:json WHERE document_id=:id"`|int|
|AttendanceFixStore.updateAttendanceFix|AttendanceFixFormHandler.java:174|`"UPDATE attendances SET absent=0,fix_document_id=:doc,judged_at=:now,updated_at=:now WHERE user_id=:user AND w…`|int|
|ProjectQueries.countProject|BoardProjectService.java:55|`"SELECT COUNT(*) FROM projects WHERE id=:id"`|long|
|ProjectStore.findLockedProject|BoardProjectService.java:66|`"SELECT id FROM projects WHERE id=:id FOR UPDATE"`|Map&lt;String, Object&gt;|
|ProjectQueries.findMembers|BoardProjectService.java:127|`"SELECT u.id,u.user_name AS name,pos.name AS title FROM project_members m JOIN users u ON u.id=m.user_id LEFT …`|List&lt;Map&lt;String, Object&gt;&gt;|
|ProjectQueries.countActiveUser|BoardProjectService.java:141|`"SELECT COUNT(*) FROM users WHERE id=:id AND status='ACTIVE'"`|long|
|ProjectStore.insertProject|BoardProjectService.java:160|`"INSERT INTO projects(project_key,title,content,start_date,end_date,priority,status,leader_id,created_at) VALU…`|long|
|ProjectStore.insertLeaderMember|BoardProjectService.java:168|`"INSERT INTO project_members(project_id,user_id,created_at) VALUES(:p,:u,:now)"`|int|
|ProjectQueries.findSummary|BoardProjectService.java:172, BoardProjectService.java:201|`ProjectSummaryQuery.sql("p.id=:id", false)`|Map&lt;String, Object&gt;|
|ProjectStore.insertLeaderIfMissing|BoardProjectService.java:191|`"INSERT IGNORE INTO project_members(project_id,user_id,created_at) VALUES(:p,:u,:now)"`|int|
|ProjectStore.updateProject|BoardProjectService.java:194|`"UPDATE projects SET title=COALESCE(:title,title),content=COALESCE(:content,content),start_date=:start,end_dat…`|int|
|ProjectQueries.countUnfinishedForMembers|BoardProjectService.java:231|`"SELECT COUNT(*) FROM tasks t JOIN task_assignments a ON a.task_id=t.id WHERE t.project_id=:id AND t.task_stat…`|long|
|ProjectQueries.findMemberIds|BoardProjectService.java:240|`"SELECT user_id FROM project_members WHERE project_id=:id"`|List&lt;Map&lt;String, Object&gt;&gt;|
|ProjectStore.deleteMembers|BoardProjectService.java:250|`"DELETE FROM project_members WHERE project_id=:id AND user_id IN (:removed)"`|int|
|ProjectStore.insertMember|BoardProjectService.java:255|`"INSERT INTO project_members(project_id,user_id,created_at) VALUES(:p,:u,:now)"`|int|
|ProjectStore.updateChangedAt|BoardProjectService.java:259|`"UPDATE projects SET updated_at=:now WHERE id=:id"`|int|
|ProjectQueries.findVisible|BoardProjectService.java:53|`select`|List&lt;Map&lt;String, Object&gt;&gt;|
|ProjectQueries.countProjects|BoardProjectService.java:120|`"SELECT COUNT(*) FROM projects p LEFT JOIN users u ON u.id=p.leader_id WHERE " + where`|long|
|ProjectQueries.findProjects|BoardProjectService.java:122|`ProjectSummaryQuery.sql(where, true)`|List&lt;Map&lt;String, Object&gt;&gt;|
|TaskQueries.findTask|BoardTaskService.java:26|`"SELECT * FROM tasks WHERE id=:id"`|Map&lt;String, Object&gt;|
|TaskQueries.findTaskProject|BoardTaskService.java:30|`"SELECT project_id FROM tasks WHERE id=:id"`|Map&lt;String, Object&gt;|
|TaskQueries.findAssignees|BoardTaskService.java:35|`"SELECT u.id,u.user_name AS name,p.name AS title FROM task_assignments a JOIN users u ON u.id=a.user_id LEFT J…`|List&lt;Map&lt;String, Object&gt;&gt;|
|TaskQueries.findBoardTasks|BoardTaskService.java:60|`"SELECT * FROM tasks WHERE project_id=:id ORDER BY position,id"`|List&lt;Map&lt;String, Object&gt;&gt;|
|TaskQueries.findBoardPeople|BoardTaskService.java:62|`""" SELECT NULL AS task_id,u.id,u.user_name AS name,pos.name AS title FROM project_members m JOIN users u ON u…`|List&lt;Map&lt;String, Object&gt;&gt;|
|TaskQueries.findHistory|BoardTaskService.java:100|`""" SELECT h.*,u.user_name AS actor_name,target.user_name AS target_name, CASE h.action WHEN 'ASSIGNED' THEN C…`|List&lt;Map&lt;String, Object&gt;&gt;|
|TaskStore.insertHistory|BoardTaskService.java:126|`"INSERT INTO task_histories(task_id,actor_id,action,from_value,to_value,created_at) VALUES(:id,:actor,:action,…`|int|
|TaskQueries.countMembers|BoardTaskService.java:135|`"SELECT COUNT(*) FROM project_members WHERE project_id=:p AND user_id IN (:ids)"`|long|
|TaskQueries.findAssigneeIds|BoardTaskService.java:143|`"SELECT user_id FROM task_assignments WHERE task_id=:id"`|List&lt;Map&lt;String, Object&gt;&gt;|
|TaskStore.deleteAssignment|BoardTaskService.java:149|`"DELETE FROM task_assignments WHERE task_id=:id AND user_id=:user"`|int|
|TaskStore.insertAssignment|BoardTaskService.java:156|`"INSERT INTO task_assignments(task_id,user_id,created_at) VALUES(:id,:user,:now)"`|int|
|TaskStore.insertTask|BoardTaskService.java:176|`"INSERT INTO tasks(project_id,task_no,position,version,created_by,task_title,task_content,end_date,priority,ta…`|long|
|TaskStore.updateLastSequence|BoardTaskService.java:182|`"UPDATE projects SET last_task_seq=:seq WHERE id=:id"`|int|
|TaskStore.updateTask|BoardTaskService.java:199|`"UPDATE tasks SET task_title=COALESCE(:title,task_title),task_content=COALESCE(:content,task_content),end_date…`|int|
|TaskStore.updatePosition|BoardTaskService.java:243|`"UPDATE tasks SET task_status=:status,position=:pos,version=version+1,updated_at=:now WHERE id=:id"`|int|
|TaskQueries.findOverdueProjects|TaskOverdueWork.java:25|`"SELECT DISTINCT project_id FROM tasks WHERE end_date<:today AND task_status<>'DONE' ORDER BY project_id"`|List&lt;Map&lt;String, Object&gt;&gt;|
|TaskStore.findLockedProject|TaskOverdueWork.java:30|`"SELECT id FROM projects WHERE id=:id FOR UPDATE"`|List&lt;Map&lt;String, Object&gt;&gt;|
|TaskQueries.findOverdueTasks|TaskOverdueWork.java:34|`""" SELECT t.id,t.task_no,t.task_title,t.end_date,p.project_key,a.user_id FROM tasks t JOIN projects p ON p.id…`|List&lt;Map&lt;String, Object&gt;&gt;|
|TaskQueries.countValidPredecessor|BoardTaskService.java:259|`"SELECT COUNT(*) FROM tasks WHERE id=:after AND project_id=:p AND task_status=:status"`|long|
|TaskQueries.countMaxPosition|BoardTaskService.java:267|`"SELECT COALESCE(MAX(position),0) FROM tasks WHERE project_id=:p AND task_status=:status AND id<>:moving"`|long|
|TaskQueries.findPredecessorPosition|BoardTaskService.java:272|`"SELECT position FROM tasks WHERE id=:after"`|Map&lt;String, Object&gt;|
|TaskQueries.findNextPosition|BoardTaskService.java:278|`"SELECT position FROM tasks WHERE project_id=:p AND task_status=:status AND id<>:moving AND position>:lower OR…`|List&lt;Map&lt;String, Object&gt;&gt;|
|TaskStore.updateRebalancedPositions|BoardTaskService.java:290|`""" UPDATE tasks t JOIN (SELECT id,ROW_NUMBER() OVER(ORDER BY position,id)*1024 AS new_position FROM tasks WHE…`|int|
|TaskStore.insertDueClaim|TaskOverdueWork.java:44|`""" INSERT INTO task_due_alerts(task_id,user_id,due_date,created_at) SELECT :task,:user,:due,:now FROM DUAL WH…`|int|
|TaskStore.insertOverdueHistory|TaskOverdueWork.java:57|`"INSERT INTO task_histories(task_id,actor_id,action,to_value,created_at) VALUES(:task,NULL,'OVERDUE_NOTIFIED',…`|int|
|HomeQueries.countTodo|HomeService.java:56|`"SELECT COUNT(*) FROM approval_documents d WHERE " + HomeQueries.TODO`|long|
|HomeQueries.findTodo|HomeService.java:57|`HomeQueries.TODO_ROWS`|List&lt;Map&lt;String, Object&gt;&gt;|
|HomeQueries.findMine|HomeService.java:63|`HomeQueries.MINE`|List&lt;Map&lt;String, Object&gt;&gt;|
|HomeQueries.findDepartment|HomeService.java:77|`"SELECT d.id,d.name FROM users u JOIN departments d ON d.id=u.department_id WHERE u.id=:actor"`|Map&lt;String, Object&gt;|
|HomeQueries.findTeamWeek|HomeService.java:87|`HomeQueries.teamWeek(leave, tasks, scope)`|List&lt;Map&lt;String, Object&gt;&gt;|
|HomeQueries.findMySummary|HomeService.java:116|`HomeQueries.mySummary(scope)`|List&lt;Map&lt;String, Object&gt;&gt;|
|HomeQueries.findMyItems|HomeService.java:126|`HomeQueries.myItems(scope)`|List&lt;Map&lt;String, Object&gt;&gt;|
|HomeQueries.findNotifications|HomeService.java:138|`HomeQueries.NOTIFICATIONS`|List&lt;Map&lt;String, Object&gt;&gt;|
|AttendanceMonthlyQuery.findMonthly|AttendanceMonthlyQuery.java:39 / AttendanceAdminService.java:27,49|기존 sql(String) + 범위·부서 조립|List&lt;Map&lt;String,Object&gt;&gt;|

### 합친 SQL과 값 출현 횟수

Project create·patch의 `ProjectSummaryQuery.sql("p.id=:id", false)` 요약 재조회를 findSummary 한 메서드로 합쳤다. 문자열 `p.id=:id`, 키 id/today/actor는 각각 1회 줄었다. 두 멤버 INSERT는 SQL이 같지만 leaderId는 Long, applyMembers의 user는 long이므로 타입을 유지하려고 각각의 메서드로 뒀다. 도메인을 넘는 병합은 없다.

PROJECT 권한 문자열은 visible/list에서 각각 ScopeFilter를 구하도록 경계를 옮기면서 기존 값 출현이 2회 늘었다. task 공유 map 분리로 user +1, status +3, now +1, task +1, after +1, p +3, moving +2가 늘었다. 새 문자열 값은 모두 0이다. 기존 묶음의 user -1과 이번 +1은 상쇄되므로 최종 누적 축소 허용 목록에서는 user 항목이 빠진다. 범용 허용이나 차이 무시는 쓰지 않았다.

|최종 누적 감소 값|감소 횟수|
|---|---|
|`id`|11|
|`actor`|1|
|`role`|1|
|`DELETE FROM approval_lines WHERE document_id=:id`|2|
|`SELECT id FROM departments WHERE id=:id`|2|
|`today`|1|
|`SELECT COUNT(*) FROM users WHERE department_id=:id`|1|
|`SELECT COUNT(*) FROM departments WHERE parent_id=:id`|1|
|`SELECT COUNT(*) FROM user_roles WHERE role_id=:id`|1|
|`DELETE FROM role_permissions WHERE role_id=:id`|1|
|`SELECT COUNT(*) FROM user_roles WHERE user_id=:user AND role_id=:role`|1|
|`p.id=:id`|1|
|`SELECT * FROM users WHERE id=:id FOR UPDATE`|2|

### 간접 흐름 수동 대조와 이번 판단

- notification.list: 쪽 검사 후 actor(long) → offset(long)을 서비스에서 구한다. count/find가 같은 parameters(user,size,offset)·where(unreadOnly)를 사용한다. map 순서 user(long), size(int), offset(long), 조건식 전체가 같다. read의 소유자 오류 RESOURCE_NOT_FOUND와 actor 호출 2회, NotificationService의 MANDATORY 3개, 링크·시각 계산 순서를 보존했다.
- attendance.monthly: 서비스에서 start/end 계산 → access.scope → includesDepartment 검사 → 부서 존재 SQL(DEPARTMENT_NOT_FOUND) → descendants SQL → findMonthly. predicate와 부서 조건 문자열만 query로 옮겼다. map 키는 start/end/범위 파라미터/departments. predicate가 부서 확인 뒤로 가도 입력 ScopeFilter·map 값만 조립하고 SQL/외부 상태를 바꾸지 않으므로 조회·오류 순서는 같다. 기존 fetch의 `db.rows(sql(scope), args)`와 새 `db.rows(sql(where), args)`가 같은 sql(String)과 같은 조건/키를 받는 것을 수동 대조했다.
- AttendanceFix handler: BodyValues/optionalTime 순서, copyBody(id,target), mapper 쓰기와 UPDATE의 동일 try, onApproved의 단일 now 공유를 유지했다. 월 집계의 YearMonth와 배치 now도 서비스에서 계산한다.
- project.visible: require → today → access.scope → actor → findVisible → 비었을 때 count/throw. map id/today/actor/[departments], summary 삼항 SQL 식은 통째로 이동했다. list는 today/offset/size/actor/[departments]/[status]/[q] 순서, status.name와 검색 이스케이프는 서비스에 남기고 count → rows를 유지했다. helper는 SQL을 실행하지 않는다. 범위 getter와 map 조립을 COUNT와 목록에서 각각 수행해도 권한 조회 SQL은 한 번만 실행된다.
- TaskOverdueWork: 프로젝트 잠금 rows → 빈 결과 continue를 유지했다. t.get의 Object 타입을 좁히지 않았고, now는 한 번만 읽는다. inserted != 1, 알림 제목·링크, 알림 호출 위치는 두 INSERT 사이 그대로다.
- HomeService의 args는 승인된 예외다. actor/today/from/to를 같은 위치에서 만들고 project helper의 actor/departments 및 teamWeek의 department 추가도 같은 map에 반영한다. 쿼리별로 나누면 프로젝트 공개 범위 API까지 바꿔야 하므로 이번 범위에서 버렸다. 8개 실행 메서드 모두 동일 map 객체를 받으며 ErrorCode.USER_NOT_FOUND와 실행 순서를 유지한다.

|분리한 map|키 순서|값·standalone 타입|사용하지 않는 키|
|---|---|---|---|
|position 선행 카드 확인|p, status, after|project:long, status:String, after:Long|moving 제거|
|position MAX|p, status, moving|project:long, status:String, moving:long|after/lower 제거|
|position 선행 위치|after|after:Long|p/status/moving 제거|
|position 다음 위치|p, status, moving, lower|project:long, status:String, moving:long, lower:long|after 제거|
|position 재배치|p, status, moving|project:long, status:String, moving:long|after/lower 제거|
|마감 대상 INSERT|task, user, due, now|t.get(id):Object, t.get(user_id):Object, t.get(end_date):Object, now:LocalDateTime|그대로|
|마감 이력 INSERT|task, user, now|위와 같은 task/user/now 변수|due 제거|

반복 2회·단락 평가·position 계산·마지막 IllegalStateException은 서비스에 그대로 있다. NamedParameterJdbcTemplate가 읽는 이름과 바인딩 값은 같으며 사용하지 않는 키만 제거했다.

### SQL 위치·호출 위치 측정

|도메인|시작 SQL 값/파일|패키지 이동 뒤|최종|
|---|---|---|---|
|notification|11 / 2|11 / 2|0 / 0|
|attendance|24 / 6|13 / 4|0 / 0|
|project|26 / 3|20 / 1|0 / 0|
|task|25 / 2|25 / 2|0 / 0|
|home|16 / 2|2 / 1|0 / 0|
|calendar|0 / 0|0 / 0|0 / 0|
|dashboard|0 / 0|0 / 0|0 / 0|
|합계|102 / 15|71 / 10|0 / 0|

SQL 키워드 오탐은 WebConfig의 HTTP 메서드 문자열 `DELETE` 1개다. 키워드 없는 실제 SQL 조각은 시작 **5출현 → 최종 0**: 0=1, 1=1, p.id=:id 두 번, user_id=:user. HH:mm 두 곳은 시간 형식이므로 제외한다.

ScopeFilter.predicate 호출은 서비스 0, repository 3(EmployeeRepository·LeaveQueries·AttendanceMonthlyQuery), PermissionAccess 1이다. 프로젝트 공개 범위 helper 호출은 BoardProjectService.visibilityPredicate → ProjectQueries.visibility, HomeService.home → BoardProjectService.visibilityPredicate다. ProjectQueries 내부 findVisible/where도 같은 visibility를 사용한다.

### 형식과 보호 파일

최종 main 일반 120자 초과 0·복수 문장 0·복수 선언자 0·한 줄 단위 기존 ProjectMember 1, test는 같은 항목 모두 0이다. 문자열 리터럴 예외는 main 88/test 78이며, 기존 보호된 name/member·key/value·text block 모양은 다음 위치에만 남는다. 테스트 형식은 바꾸지 않았다.

#### main 잔여 예외 위치

literal_exceptions: 88개

- `backend/src/main/java/com/minierp/backend/domain/approval/repository/ApprovalLineQueries.java:32`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/DocumentQueries.java:17`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/DocumentQueries.java:35`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/DocumentQueries.java:41`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/DocumentStore.java:67`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/DocumentStore.java:77`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/DocumentStore.java:93`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/DocumentStore.java:105`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/DocumentStore.java:116`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/FormQueries.java:25`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/FormStore.java:17`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/FormStore.java:31`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/GeneralRequestStore.java:17`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/GeneralRequestStore.java:28`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/InboxRepository.java:21`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/InboxRepository.java:23`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/InboxRepository.java:25`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/InboxRepository.java:29`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/InboxRepository.java:43`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/InboxRepository.java:49`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/InboxRepository.java:69`
- `backend/src/main/java/com/minierp/backend/domain/attendance/repository/AttendanceFixStore.java:31`
- `backend/src/main/java/com/minierp/backend/domain/attendance/repository/AttendanceFixStore.java:41`
- `backend/src/main/java/com/minierp/backend/domain/attendance/repository/AttendanceFixStore.java:52`
- `backend/src/main/java/com/minierp/backend/domain/attendance/repository/AttendanceQueries.java:18`
- `backend/src/main/java/com/minierp/backend/domain/attendance/repository/AttendanceQueries.java:24`
- `backend/src/main/java/com/minierp/backend/domain/attendance/repository/AttendanceQueries.java:34`
- `backend/src/main/java/com/minierp/backend/domain/attendance/repository/AttendanceStore.java:36`
- `backend/src/main/java/com/minierp/backend/domain/attendance/repository/AttendanceStore.java:79`
- `backend/src/main/java/com/minierp/backend/domain/home/repository/HomeQueries.java:23`
- `backend/src/main/java/com/minierp/backend/domain/home/repository/HomeQueries.java:60`
- `backend/src/main/java/com/minierp/backend/domain/leave/entity/LeaveRequest.java:37`
- `backend/src/main/java/com/minierp/backend/domain/leave/entity/LeaveRequest.java:41`
- `backend/src/main/java/com/minierp/backend/domain/leave/entity/LeaveRequest.java:51`
- `backend/src/main/java/com/minierp/backend/domain/leave/entity/LeaveRequest.java:54`
- `backend/src/main/java/com/minierp/backend/domain/leave/repository/LeaveQueries.java:19`
- `backend/src/main/java/com/minierp/backend/domain/leave/repository/LeaveQueries.java:39`
- `backend/src/main/java/com/minierp/backend/domain/leave/repository/LeaveQueries.java:76`
- `backend/src/main/java/com/minierp/backend/domain/leave/repository/LeaveStore.java:23`
- `backend/src/main/java/com/minierp/backend/domain/leave/repository/LeaveStore.java:34`
- `backend/src/main/java/com/minierp/backend/domain/leave/repository/LeaveStore.java:56`
- `backend/src/main/java/com/minierp/backend/domain/leave/repository/LeaveStore.java:73`
- `backend/src/main/java/com/minierp/backend/domain/notification/repository/NotificationStore.java:24`
- `backend/src/main/java/com/minierp/backend/domain/notification/repository/NotificationStore.java:30`
- `backend/src/main/java/com/minierp/backend/domain/notification/repository/NotificationStore.java:36`
- `backend/src/main/java/com/minierp/backend/domain/org/repository/OrgQueries.java:36`
- `backend/src/main/java/com/minierp/backend/domain/org/repository/OrgQueries.java:50`
- `backend/src/main/java/com/minierp/backend/domain/org/repository/OrgQueries.java:72`
- `backend/src/main/java/com/minierp/backend/domain/org/repository/OrgStore.java:102`
- `backend/src/main/java/com/minierp/backend/domain/overtime/entity/OvertimeRequest.java:25`
- `backend/src/main/java/com/minierp/backend/domain/overtime/entity/OvertimeRequest.java:37`
- `backend/src/main/java/com/minierp/backend/domain/overtime/repository/OvertimeStore.java:26`
- `backend/src/main/java/com/minierp/backend/domain/overtime/repository/OvertimeStore.java:32`
- `backend/src/main/java/com/minierp/backend/domain/permission/repository/RoleQueries.java:22`
- `backend/src/main/java/com/minierp/backend/domain/permission/repository/RoleQueries.java:28`
- `backend/src/main/java/com/minierp/backend/domain/permission/repository/RoleQueries.java:42`
- `backend/src/main/java/com/minierp/backend/domain/permission/repository/RoleStore.java:25`
- `backend/src/main/java/com/minierp/backend/domain/permission/repository/RoleStore.java:43`
- `backend/src/main/java/com/minierp/backend/domain/project/repository/ProjectMemberRepository.java:16`
- `backend/src/main/java/com/minierp/backend/domain/project/repository/ProjectQueries.java:23`
- `backend/src/main/java/com/minierp/backend/domain/project/repository/ProjectQueries.java:38`
- `backend/src/main/java/com/minierp/backend/domain/project/repository/ProjectQueries.java:89`
- `backend/src/main/java/com/minierp/backend/domain/project/repository/ProjectStore.java:30`
- `backend/src/main/java/com/minierp/backend/domain/project/repository/ProjectStore.java:55`
- `backend/src/main/java/com/minierp/backend/domain/project/repository/ProjectSummaryQuery.java:9`
- `backend/src/main/java/com/minierp/backend/domain/task/repository/TaskQueries.java:26`
- `backend/src/main/java/com/minierp/backend/domain/task/repository/TaskQueries.java:63`
- `backend/src/main/java/com/minierp/backend/domain/task/repository/TaskQueries.java:82`
- `backend/src/main/java/com/minierp/backend/domain/task/repository/TaskQueries.java:93`
- `backend/src/main/java/com/minierp/backend/domain/task/repository/TaskRepository.java:37`
- `backend/src/main/java/com/minierp/backend/domain/task/repository/TaskRepository.java:45`
- `backend/src/main/java/com/minierp/backend/domain/task/repository/TaskStore.java:18`
- `backend/src/main/java/com/minierp/backend/domain/task/repository/TaskStore.java:44`
- `backend/src/main/java/com/minierp/backend/domain/task/repository/TaskStore.java:55`
- `backend/src/main/java/com/minierp/backend/domain/task/repository/TaskStore.java:88`
- `backend/src/main/java/com/minierp/backend/domain/user/entity/User.java:45`
- `backend/src/main/java/com/minierp/backend/domain/user/entity/User.java:53`
- `backend/src/main/java/com/minierp/backend/domain/user/entity/User.java:64`
- `backend/src/main/java/com/minierp/backend/domain/user/entity/User.java:67`
- `backend/src/main/java/com/minierp/backend/domain/user/repository/EmployeeQueries.java:21`
- `backend/src/main/java/com/minierp/backend/domain/user/repository/EmployeeQueries.java:35`
- `backend/src/main/java/com/minierp/backend/domain/user/repository/EmployeeQueries.java:84`
- `backend/src/main/java/com/minierp/backend/domain/user/repository/EmployeeStore.java:22`
- `backend/src/main/java/com/minierp/backend/domain/user/repository/EmployeeStore.java:32`
- `backend/src/main/java/com/minierp/backend/domain/user/repository/EmployeeStore.java:81`
- `backend/src/main/java/com/minierp/backend/domain/user/repository/EmployeeStore.java:88`
- `backend/src/main/java/com/minierp/backend/global/batch/BatchRunRecorder.java:27`
- `backend/src/main/java/com/minierp/backend/global/batch/BatchRunRecorder.java:31`

name_member_breaks: 1개

- `backend/src/main/java/com/minierp/backend/domain/attendance/repository/AttendanceStore.java:103`

non_chain_breaks: 1개

- `backend/src/main/java/com/minierp/backend/domain/attendance/repository/AttendanceStore.java:103`

key_value_splits: 1개

- `backend/src/main/java/com/minierp/backend/domain/attendance/repository/AttendanceStore.java:141`

protected_fragment_splits: 2개

- `backend/src/main/java/com/minierp/backend/domain/attendance/repository/AttendanceStore.java:103`
- `backend/src/main/java/com/minierp/backend/domain/attendance/repository/AttendanceStore.java:141`

#### test 잔여 예외 위치

literal_exceptions: 78개

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

key_value_splits: 8개

- `backend/src/test/java/com/minierp/backend/support/refactoring/RecordingOrder.java:17`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:14`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:16`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:18`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:20`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:22`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:24`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:26`

outer_operator_unsplit: 1개

- `backend/src/test/java/com/minierp/backend/integration/M4BoardConcurrencyTest.java:164`

protected_fragment_splits: 1개

- `backend/src/test/java/com/minierp/backend/integration/M1DataMigrationTest.java:35`

ternary_layout: 1개

- `backend/src/test/java/com/minierp/backend/integration/M1DataMigrationTest.java:35`

### 재현 명령과 남은 한계

```sh
# 후보/기준은 작업 트리 밖 archive. 기존 target 기록을 지우지 않는다.
JAVA_HOME=/Users/kimmini/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home
export JAVA_HOME
python3 docs/refactoring/R-03-sql-location-measure.py --root "$CANDIDATE" --baseline "$BASE" \
  --allow-count-reductions "$REDUCTIONS" \
  --rename-class com.minierp.backend.domain.project.service.ProjectStateStore=com.minierp.backend.domain.project.repository.ProjectStateStore \
  --check-repository "$REPOSITORY_FILE" --preserve-repository "$OLD_FILE=$NEW_FILE" --output "$SAFETY_JSON"
python3 docs/refactoring/R-03-call-site-check.py --before "$BASE" --after "$CANDIDATE" \
  --classpath "$CLASSPATH_TXT" --file "$ORIGINAL_FILE" --moved "$OLD_FILE=$NEW_FILE" --output "$CALLS_JSON"
python3 docs/refactoring/R-03-text-block-check.py --before "$BASE" --after "$CANDIDATE" --output "$CLOSING_JSON"
python3 docs/refactoring/R-03-package-move-check.py --before "$BEFORE_MOVE" --after "$AFTER_MOVE" \
  --moved "$OLD_FILE=$NEW_FILE" --output "$PACKAGE_JSON"
python3 docs/refactoring/R-02-format-measure.py --root "$CANDIDATE" --output "$FORMAT_JSON"
python3 docs/refactoring/R-03-format-units.py --root "$CANDIDATE" --output "$UNITS_JSON"
# --file/--moved/--check-repository/--preserve-repository는 대상별 반복한다.
cd backend
./mvnw -Duser.timezone=UTC -Drefactoring.record="$RECORDING" test
"$JAVA_HOME/bin/java" -cp "$(cat "$RECORDING/classpath.txt")" \
  com.minierp.backend.support.refactoring.SnapshotComparator src/test/resources/refactoring-baseline "$RECORDING"
./mvnw -Duser.timezone=UTC test
```

- SQL 합치기로 타입을 통일하는 대안은 버렸다(Long/long 변환 금지). 기존 쿼리 클래스의 판정·시계 코드를 같이 정리하는 대안도 범위 밖이라 하지 않았다.
- 이번 작업에서 새 기능 버그는 확인하지 않았다. 기존 클래스의 SQL 외 코드는 위 목록대로 후속 검토 대상으로 남겼다.
- 비교 도구는 간접 map의 일반적인 데이터 흐름 증명기가 아니므로 위 표의 키·값·타입·실행 순서를 직접 대조했다. 기록 모드는 기존 정책대로 경합 순서를 고정하는 한계가 있으며 이번에 정책을 변경하지 않았다.
- 실제 서버·개발 DB/Redis·운영 부하 실험은 수행하지 않았다(금지 범위). frontend/backend/md/V1~V6/.claude/기준선/기록기/비교기는 변경하지 않았다. 병합·푸시는 하지 않았다.

- 이 보고서 커밋의 별도 git archive 컴파일: test-compile 종료 0, 9.49초. Java 변경은 없으며 보고서만 추가했다.
