# R-02 — 형식 정리 (리팩토링 2단계)

## 범위와 기준

- 브랜치: `refactor/backend-readability`. 1단계 마지막 `ef6a0e9`에 develop `6058727`을 병합한 `a1df7297dfd52736472c3897d57cd5b275711e29`를 BASE로 사용했습니다.
- 허용된 병합 직후 `git diff ef6a0e9 HEAD -- backend/src`는 비어 있었습니다. 공유 원본 작업 폴더의 브랜치를 바꾸지 않았습니다.
- 운영 Java 180개와 테스트 Java 79개를 형식만 정리했습니다. 이름·순서·로직·import·리터럴·기준선·기록 정책·비교 규칙을 유지했습니다. SQL 위치 이동과 응답 구조 변경은 하지 않았습니다.
- 사용자 추가 결정대로 측정 스크립트는 `docs/refactoring/R-02-format-measure.py`에 두었습니다. 테스트 리소스를 추가하지 않아 출력 폴더 전체 `diff 0` 규칙에 예외가 없습니다.

## 커밋과 검증

| 순서 | 커밋 | 묶음 | 바뀐 Java 파일 | 클래스 / 전체 출력 파일 | 출력 diff |
|---:|---|---|---:|---:|---:|
| 1 | `dec6fd85e1e18de6b67a3c2023bf0776734e0f08` | global + BackendApplication | 32 | 330 / 356 | 0 |
| 2 | `09e39aa80e0e86c96de6032369bd6ed942f18ad3` | org · user · permission | 48 | 330 / 356 | 0 |
| 3 | `c1e62187a0b1eabb0c25657983379d632cbc51e0` | approval · leave · overtime | 34 | 330 / 356 | 0 |
| 4 | `a8379314e0856a55c813e0a6ddb4c93eb9b5d624` | attendance · calendar · home · notification · project · task · dashboard | 66 | 330 / 356 | 0 |
| 5 | `b4985233a721858aa7d85e45d4f5f83420e255a6` | 테스트 domain · global · support · BackendApplicationTests | 29 | 330 / 356 | 0 |
| 6 | `e6ff2a5c13c64ef0083bbfb5b59453bcd8fca4fd` | 테스트 integration | 50 | 330 / 356 | 0 |

각 묶음은 변경 파일만 지정하여 stage한 뒤 UTC 전체 테스트를 기록 ON/OFF 각각 실행했습니다. 검증한 tree와 커밋 tree가 같은지 확인했고, 실제 커밋도 다시 `git archive`하여 컴파일했습니다. BASE와 후보 및 실제 커밋을 각각 깨끗한 임시 폴더에서 같은 Java 17 명령으로 컴파일했습니다.

```bash
./mvnw -q -Duser.timezone=UTC -Dmaven.compiler.debuglevel=none test-compile
diff -r BASE_ARCHIVE/backend/target/classes COMMIT_ARCHIVE/backend/target/classes
diff -r BASE_ARCHIVE/backend/target/test-classes COMMIT_ARCHIVE/backend/target/test-classes
```

운영 출력은 클래스 236개 / 전체 파일 245개, 테스트 출력은 클래스 94개 / 전체 파일 111개입니다. 리소스까지 포함한 두 디렉터리의 diff가 모두 0입니다. BASE의 330개 클래스에는 `LineNumberTable`·`SourceFile` 속성이 없음을 확인했습니다. 작업 트리에서 `mvn clean`을 실행하지 않아 1단계 기록 5개도 보존했습니다.

| 묶음 | 기록 ON 결과 (전체/실패/오류/건너뜀) | ON 시간(s) | differences | 순서 경고 수 | 기록 OFF 결과 | OFF 시간(s) | OFF 기록 출력 |
|---:|---|---:|---:|---:|---|---:|---:|
| 1 | 231 / 0 / 0 / 0 | 456.61 | 0 | 11 | 231 / 0 / 0 / 0 | 500.93 | 0 |
| 2 | 231 / 0 / 0 / 0 | 444.02 | 0 | 11 | 231 / 0 / 0 / 0 | 413.60 | 0 |
| 3 | 231 / 0 / 0 / 0 | 460.66 | 0 | 10 | 231 / 0 / 0 / 0 | 411.50 | 0 |
| 4 | 231 / 0 / 0 / 0 | 390.64 | 0 | 10 | 231 / 0 / 0 / 0 | 379.69 | 0 |
| 5 | 231 / 0 / 0 / 0 | 404.96 | 0 | 11 | 231 / 0 / 0 / 0 | 422.22 | 0 |
| 6 | 231 / 0 / 0 / 0 | 399.78 | 0 | 10 | 231 / 0 / 0 / 0 | 395.10 | 0 |

시간은 각 명령의 실제 경과 시간입니다. 컨테이너 기동·동시성 실험·실행 환경의 영향이 있어 형식 정리의 성능 개선 수치로 해석하지 않습니다. 기록 OFF 실행에는 순서 고정 훅이 설치되지 않으므로 기존 동시성 검증도 그대로 실행했습니다.

실행 명령(backend 폴더에서):

```bash
export JAVA_HOME=/Users/kimmini/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw -Duser.timezone=UTC -Drefactoring.record=/ABSOLUTE/RECORDING_DIRECTORY test
./mvnw -Duser.timezone=UTC test
java -cp "$(cat /ABSOLUTE/RECORDING_DIRECTORY/classpath.txt)" \
  com.minierp.backend.support.refactoring.SnapshotComparator \
  src/test/resources/refactoring-baseline /ABSOLUTE/RECORDING_DIRECTORY
```

검증 원본은 작업 트리의 `backend/target/r02-validation/batch-{1..6}/`에 남겼습니다. `result.json`, `compare.log`, ON/OFF 실행 로그, 후보 및 커밋의 출력 diff 로그로 구성됩니다. 원본 테스트 로그에는 테스트용 값이 포함될 수 있어 저장소에는 커밋하지 않았습니다. 첫 묶음의 초기 줄바꿈 후보 실행은 최종 편집 전 중단했으며 완료 검증 수치에 포함하지 않습니다.

### SQL 순서 경고

합집합은 12개이며 기존 13개 목록 밖의 프레임은 0개입니다. SQL별 횟수·총횟수·workerSql·응답 계약 차이는 모든 묶음에서 0입니다. 아래 순서 경고는 기존 비교 규칙을 그대로 적용한 결과입니다.

| 프레임 | 발생 묶음 | 이유 |
|---|---|---|
| `com.minierp.backend.integration.M1AcceptanceTest#concurrentIdAllocationIsUniqueAndUnsafeMaxPlusOneCollides()` | 1, 2, 3, 4, 5, 6 | 기존 동시성 프레임의 작업 스레드 SQL 섞임; SQL별 실행 횟수와 응답은 같음 |
| `com.minierp.backend.integration.M2DocumentIntegrationTest#concurrentApprovalAndWithdrawalHaveOnlyOneWinner()` | 1, 2, 5 | 기존 동시성 프레임의 작업 스레드 SQL 섞임; SQL별 실행 횟수와 응답은 같음 |
| `com.minierp.backend.integration.M2DocumentIntegrationTest#concurrentApprovalHasOne409AndNoNotificationFromLosingRequestInTenTrials()` | 1, 2, 3, 4, 5, 6 | 기존 동시성 프레임의 작업 스레드 SQL 섞임; SQL별 실행 횟수와 응답은 같음 |
| `com.minierp.backend.integration.M2DocumentIntegrationTest#documentNumbersAreUniqueForEightDifferentDraftersSubmittingConcurrently()` | 1, 2, 3, 4, 5 | 기존 동시성 프레임의 작업 스레드 SQL 섞임; SQL별 실행 횟수와 응답은 같음 |
| `com.minierp.backend.integration.M2LegacyApprovalRaceTest#unconditionalLegacyApprovalAcceptsBothRequestsInTenControlledRaces()` | 1, 2, 3, 4, 5, 6 | 기존 동시성 프레임의 작업 스레드 SQL 섞임; SQL별 실행 횟수와 응답은 같음 |
| `com.minierp.backend.integration.M3AttendanceTest#concurrentClockInHasExactlyOneWinnerInTenTrials()` | 1, 2, 3, 4, 5, 6 | 기존 동시성 프레임의 작업 스레드 SQL 섞임; SQL별 실행 횟수와 응답은 같음 |
| `com.minierp.backend.integration.M3LeaveAdjustmentConcurrencyTest#unlockedModelFailsThenLockedAdjustmentsAndSubmissionsPreserveBalance()` | 1, 2, 3, 4, 5, 6 | 기존 동시성 프레임의 작업 스레드 SQL 섞임; SQL별 실행 횟수와 응답은 같음 |
| `com.minierp.backend.integration.M3LeaveGrantTest#twoConcurrentBatchRunsIssueEachKeyOnlyOnceInTenTrials()` | 1, 2, 3, 4, 5, 6 | 기존 동시성 프레임의 작업 스레드 SQL 섞임; SQL별 실행 횟수와 응답은 같음 |
| `com.minierp.backend.integration.M4BoardConcurrencyTest#gapsUpdateOneRowSerializeTwentyMovesAndRebalanceOnlyWhenExhausted()` | 2, 3, 4, 6 | 기존 동시성 프레임의 작업 스레드 SQL 섞임; SQL별 실행 횟수와 응답은 같음 |
| `com.minierp.backend.integration.M4BoardConcurrencyTest#tenConcurrentCreatesHaveContiguousUniqueNumbers()` | 1, 2, 3, 4, 5, 6 | 기존 동시성 프레임의 작업 스레드 SQL 섞임; SQL별 실행 횟수와 응답은 같음 |
| `com.minierp.backend.integration.M4OverdueTest#concurrentRunsSendOnlyOnce()` | 1, 2, 3, 4, 5, 6 | 기존 동시성 프레임의 작업 스레드 SQL 섞임; SQL별 실행 횟수와 응답은 같음 |
| `com.minierp.backend.integration.M5ProductionTest#<before-all>` | 1, 5, 6 | Map.of 순회에 따른 CREATE USER 두 SQL의 자리 교환만 허용 |

엄격 목록 7개와 M5 CREATE USER 두 줄만 허용하는 예외는 수정하지 않았습니다. 일부 비대칭 요청의 기록 순서는 기존 v2 정책대로이며, 별도 OFF 실행에서 원래 경쟁을 검증했습니다.

## 측정 전후

측정은 UTF-8을 읽어 Unicode 문자 수로 셉니다. 텍스트 블록의 내용과 닫는 구분자 줄은 길이 측정에서 제외합니다. 일반 리터럴 하나와 공백·구두점(`, ; ) ] { }`)만 있는 긴 줄은 예외로 별도 집계합니다. 문자열·주석 내부 세미콜론과 for 헤더 세미콜론을 제외하고 한 줄에 세미콜론이 두 개 이상이면 여러 문장으로 셉니다. 여러 선언자는 타입·이름·초기화식의 괄호와 제네릭을 구분하여, 선언자가 두 개 이상인 선언문의 수로 집계합니다.

| 범위 | 120자 초과(예외 제외) 전 → 후 | 문자열 예외 전 → 후 | 여러 문장 전 → 후 | 여러 선언자 전 → 후 |
|---|---:|---:|---:|---:|
| src/main/java | 567 → 0 | 0 → 88 | 328 → 0 | 14 → 0 |
| src/test/java | 789 → 0 | 6 → 76 | 537 → 0 | 57 → 0 |

BASE의 120자 초과 전체 줄은 main 567, test 795로 제공된 참고값의 합과 같습니다. SQL 키워드 판별을 쓰는 보조 분류는 main 377/190, test 548/247(비SQL/SQL)로 참고값 360/207, 543/252와 다릅니다. 여러 문장 측정은 328/537로 참고값 329/540과 소폭 다릅니다. 본 보고서와 전후 비교는 위의 동일한 렉서 정의를 적용했습니다. 여러 선언자는 JDK 구문 트리에서 별도로 센 14/57과 일치했습니다.

문자열 예외가 늘어난 이유는 이전에 호출문과 같은 줄에 있던 긴 리터럴을 독립된 줄로 옮겨 예외 범위를 명확히 했기 때문입니다. 문자열 자체를 늘리거나 쪼개지 않았습니다.

```bash
python3 docs/refactoring/R-02-format-measure.py --root . --output /tmp/r02-after.json
# BASE를 작업 트리 밖에 git archive a1df7297dfd52736472c3897d57cd5b275711e29로 풀고 같은 스크립트로 측정
python3 docs/refactoring/R-02-format-measure.py --root /ABSOLUTE/BASE_ARCHIVE --output /tmp/r02-before.json
```

측정 스크립트는 Python 표준 라이브러리만 쓰며 파일을 수정하지 않습니다. for 헤더, 여러 문장, 여러 선언자, 제네릭 인자, 문자열·주석 세미콜론, 람다 선언 사례 6개로 집계를 교차 확인했습니다.

### 남은 문자열 리터럴 예외 (파일:줄)

**main: 88줄**

- `backend/src/main/java/com/minierp/backend/domain/approval/general/GeneralFormHandler.java:29`
- `backend/src/main/java/com/minierp/backend/domain/approval/general/GeneralFormHandler.java:41`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/InboxRepository.java:21`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/InboxRepository.java:23`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/InboxRepository.java:25`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/InboxRepository.java:29`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/InboxRepository.java:43`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/InboxRepository.java:49`
- `backend/src/main/java/com/minierp/backend/domain/approval/repository/InboxRepository.java:68`
- `backend/src/main/java/com/minierp/backend/domain/approval/service/ApprovalLineResolver.java:68`
- `backend/src/main/java/com/minierp/backend/domain/approval/service/DocumentService.java:35`
- `backend/src/main/java/com/minierp/backend/domain/approval/service/DocumentService.java:159`
- `backend/src/main/java/com/minierp/backend/domain/approval/service/DocumentService.java:179`
- `backend/src/main/java/com/minierp/backend/domain/approval/service/DocumentService.java:192`
- `backend/src/main/java/com/minierp/backend/domain/approval/service/DocumentService.java:198`
- `backend/src/main/java/com/minierp/backend/domain/approval/service/DocumentService.java:253`
- `backend/src/main/java/com/minierp/backend/domain/approval/service/DocumentService.java:270`
- `backend/src/main/java/com/minierp/backend/domain/approval/service/DocumentService.java:353`
- `backend/src/main/java/com/minierp/backend/domain/approval/service/FormService.java:66`
- `backend/src/main/java/com/minierp/backend/domain/approval/service/FormService.java:83`
- `backend/src/main/java/com/minierp/backend/domain/approval/service/FormService.java:125`
- `backend/src/main/java/com/minierp/backend/domain/attendance/handler/AttendanceFixFormHandler.java:89`
- `backend/src/main/java/com/minierp/backend/domain/attendance/handler/AttendanceFixFormHandler.java:111`
- `backend/src/main/java/com/minierp/backend/domain/attendance/handler/AttendanceFixFormHandler.java:172`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:107`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:31`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:126`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceStore.java:34`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceStore.java:73`
- `backend/src/main/java/com/minierp/backend/domain/home/service/HomeQueries.java:15`
- `backend/src/main/java/com/minierp/backend/domain/home/service/HomeQueries.java:52`
- `backend/src/main/java/com/minierp/backend/domain/leave/entity/LeaveRequest.java:37`
- `backend/src/main/java/com/minierp/backend/domain/leave/entity/LeaveRequest.java:41`
- `backend/src/main/java/com/minierp/backend/domain/leave/entity/LeaveRequest.java:51`
- `backend/src/main/java/com/minierp/backend/domain/leave/entity/LeaveRequest.java:54`
- `backend/src/main/java/com/minierp/backend/domain/leave/handler/LeaveFormHandler.java:68`
- `backend/src/main/java/com/minierp/backend/domain/leave/handler/LeaveFormHandler.java:124`
- `backend/src/main/java/com/minierp/backend/domain/leave/handler/LeaveFormHandler.java:140`
- `backend/src/main/java/com/minierp/backend/domain/leave/service/LeaveAdminService.java:93`
- `backend/src/main/java/com/minierp/backend/domain/leave/service/LeaveAdminService.java:117`
- `backend/src/main/java/com/minierp/backend/domain/leave/service/LeaveGrantWork.java:39`
- `backend/src/main/java/com/minierp/backend/domain/leave/service/LeaveGrantWork.java:52`
- `backend/src/main/java/com/minierp/backend/domain/notification/service/NotificationService.java:19`
- `backend/src/main/java/com/minierp/backend/domain/notification/service/NotificationService.java:27`
- `backend/src/main/java/com/minierp/backend/domain/notification/service/NotificationService.java:34`
- `backend/src/main/java/com/minierp/backend/domain/org/service/OrgService.java:87`
- `backend/src/main/java/com/minierp/backend/domain/org/service/OrgService.java:187`
- `backend/src/main/java/com/minierp/backend/domain/org/service/OrgService.java:224`
- `backend/src/main/java/com/minierp/backend/domain/org/service/OrgService.java:282`
- `backend/src/main/java/com/minierp/backend/domain/overtime/entity/OvertimeRequest.java:25`
- `backend/src/main/java/com/minierp/backend/domain/overtime/entity/OvertimeRequest.java:37`
- `backend/src/main/java/com/minierp/backend/domain/overtime/handler/OvertimeFormHandler.java:42`
- `backend/src/main/java/com/minierp/backend/domain/overtime/handler/OvertimeFormHandler.java:48`
- `backend/src/main/java/com/minierp/backend/domain/permission/service/RoleService.java:26`
- `backend/src/main/java/com/minierp/backend/domain/permission/service/RoleService.java:32`
- `backend/src/main/java/com/minierp/backend/domain/permission/service/RoleService.java:55`
- `backend/src/main/java/com/minierp/backend/domain/permission/service/RoleService.java:79`
- `backend/src/main/java/com/minierp/backend/domain/permission/service/RoleService.java:114`
- `backend/src/main/java/com/minierp/backend/domain/project/repository/ProjectMemberRepository.java:16`
- `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:31`
- `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:123`
- `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:156`
- `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:190`
- `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:226`
- `backend/src/main/java/com/minierp/backend/domain/project/service/ProjectSummaryQuery.java:9`
- `backend/src/main/java/com/minierp/backend/domain/task/repository/TaskRepository.java:49`
- `backend/src/main/java/com/minierp/backend/domain/task/repository/TaskRepository.java:61`
- `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:36`
- `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:118`
- `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:166`
- `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:188`
- `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:253`
- `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:261`
- `backend/src/main/java/com/minierp/backend/domain/task/service/TaskOverdueWork.java:27`
- `backend/src/main/java/com/minierp/backend/domain/task/service/TaskOverdueWork.java:59`
- `backend/src/main/java/com/minierp/backend/domain/user/entity/User.java:45`
- `backend/src/main/java/com/minierp/backend/domain/user/entity/User.java:53`
- `backend/src/main/java/com/minierp/backend/domain/user/entity/User.java:68`
- `backend/src/main/java/com/minierp/backend/domain/user/entity/User.java:71`
- `backend/src/main/java/com/minierp/backend/domain/user/service/EmployeeService.java:44`
- `backend/src/main/java/com/minierp/backend/domain/user/service/EmployeeService.java:60`
- `backend/src/main/java/com/minierp/backend/domain/user/service/EmployeeService.java:77`
- `backend/src/main/java/com/minierp/backend/domain/user/service/EmployeeService.java:193`
- `backend/src/main/java/com/minierp/backend/domain/user/service/EmployeeService.java:246`
- `backend/src/main/java/com/minierp/backend/domain/user/service/EmployeeService.java:272`
- `backend/src/main/java/com/minierp/backend/domain/user/service/UserLookupService.java:31`
- `backend/src/main/java/com/minierp/backend/global/batch/BatchRunRecorder.java:21`
- `backend/src/main/java/com/minierp/backend/global/batch/BatchRunRecorder.java:25`

**test: 76줄**

- `backend/src/test/java/com/minierp/backend/integration/CoreApiIntegrationTest.java:58`
- `backend/src/test/java/com/minierp/backend/integration/CoreApiIntegrationTest.java:62`
- `backend/src/test/java/com/minierp/backend/integration/DepartmentHeadConcurrencyTest.java:64`
- `backend/src/test/java/com/minierp/backend/integration/M1AcceptanceTest.java:290`
- `backend/src/test/java/com/minierp/backend/integration/M1AcceptanceTest.java:298`
- `backend/src/test/java/com/minierp/backend/integration/M1DataMigrationTest.java:46`
- `backend/src/test/java/com/minierp/backend/integration/M1DataMigrationTest.java:68`
- `backend/src/test/java/com/minierp/backend/integration/M2DataMigrationTest.java:77`
- `backend/src/test/java/com/minierp/backend/integration/M2DocumentIntegrationTest.java:64`
- `backend/src/test/java/com/minierp/backend/integration/M2DocumentIntegrationTest.java:66`
- `backend/src/test/java/com/minierp/backend/integration/M2DocumentIntegrationTest.java:262`
- `backend/src/test/java/com/minierp/backend/integration/M2DocumentIntegrationTest.java:352`
- `backend/src/test/java/com/minierp/backend/integration/M2FormApiTest.java:76`
- `backend/src/test/java/com/minierp/backend/integration/M2InboxPerformanceTest.java:31`
- `backend/src/test/java/com/minierp/backend/integration/M2InboxPerformanceTest.java:89`
- `backend/src/test/java/com/minierp/backend/integration/M2InboxTest.java:16`
- `backend/src/test/java/com/minierp/backend/integration/M2ReviewTest.java:14`
- `backend/src/test/java/com/minierp/backend/integration/M2ReviewTest.java:30`
- `backend/src/test/java/com/minierp/backend/integration/M3AttendanceFixTest.java:31`
- `backend/src/test/java/com/minierp/backend/integration/M3AttendanceTest.java:118`
- `backend/src/test/java/com/minierp/backend/integration/M3AttendanceTest.java:125`
- `backend/src/test/java/com/minierp/backend/integration/M3BatchResponseTest.java:27`
- `backend/src/test/java/com/minierp/backend/integration/M3CsvLabelsTest.java:36`
- `backend/src/test/java/com/minierp/backend/integration/M3DataMigrationTest.java:25`
- `backend/src/test/java/com/minierp/backend/integration/M3DataMigrationTest.java:29`
- `backend/src/test/java/com/minierp/backend/integration/M3DataMigrationTest.java:32`
- `backend/src/test/java/com/minierp/backend/integration/M3DataMigrationTest.java:41`
- `backend/src/test/java/com/minierp/backend/integration/M3DataMigrationTest.java:48`
- `backend/src/test/java/com/minierp/backend/integration/M3DataMigrationTest.java:59`
- `backend/src/test/java/com/minierp/backend/integration/M3LeaveAdjustmentConcurrencyTest.java:51`
- `backend/src/test/java/com/minierp/backend/integration/M3LeaveGrantTest.java:164`
- `backend/src/test/java/com/minierp/backend/integration/M3LegacyAttendanceTest.java:31`
- `backend/src/test/java/com/minierp/backend/integration/M3LegacyAttendanceTest.java:43`
- `backend/src/test/java/com/minierp/backend/integration/M3LegacyAttendanceTest.java:78`
- `backend/src/test/java/com/minierp/backend/integration/M3MonthlyTest.java:155`
- `backend/src/test/java/com/minierp/backend/integration/M4BoardApiTest.java:258`
- `backend/src/test/java/com/minierp/backend/integration/M4BoardConcurrencyTest.java:153`
- `backend/src/test/java/com/minierp/backend/integration/M4BoardConcurrencyTest.java:222`
- `backend/src/test/java/com/minierp/backend/integration/M4DataMigrationTest.java:24`
- `backend/src/test/java/com/minierp/backend/integration/M4DataMigrationTest.java:28`
- `backend/src/test/java/com/minierp/backend/integration/M4DataMigrationTest.java:33`
- `backend/src/test/java/com/minierp/backend/integration/M4DeterministicLockTest.java:112`
- `backend/src/test/java/com/minierp/backend/integration/M4HomeGrowthTest.java:26`
- `backend/src/test/java/com/minierp/backend/integration/M4HomeGrowthTest.java:46`
- `backend/src/test/java/com/minierp/backend/integration/M4HomeGrowthTest.java:50`
- `backend/src/test/java/com/minierp/backend/integration/M4HomeGrowthTest.java:65`
- `backend/src/test/java/com/minierp/backend/integration/M4HomeGrowthTest.java:68`
- `backend/src/test/java/com/minierp/backend/integration/M4HomeGrowthTest.java:71`
- `backend/src/test/java/com/minierp/backend/integration/M4HomeTest.java:21`
- `backend/src/test/java/com/minierp/backend/integration/M4HomeTest.java:24`
- `backend/src/test/java/com/minierp/backend/integration/M4HomeTest.java:48`
- `backend/src/test/java/com/minierp/backend/integration/M4HomeTest.java:150`
- `backend/src/test/java/com/minierp/backend/integration/M4LegacyExperimentTest.java:86`
- `backend/src/test/java/com/minierp/backend/integration/M4OverdueTest.java:47`
- `backend/src/test/java/com/minierp/backend/integration/M4OverdueTest.java:86`
- `backend/src/test/java/com/minierp/backend/integration/M4QueryGrowthTest.java:34`
- `backend/src/test/java/com/minierp/backend/integration/M4QueryGrowthTest.java:52`
- `backend/src/test/java/com/minierp/backend/integration/M4QueryGrowthTest.java:104`
- `backend/src/test/java/com/minierp/backend/integration/M5ProductionTest.java:146`
- `backend/src/test/java/com/minierp/backend/integration/M5ProductionTest.java:186`
- `backend/src/test/java/com/minierp/backend/integration/M5ProductionTest.java:208`
- `backend/src/test/java/com/minierp/backend/support/ApprovalApiTestSupport.java:56`
- `backend/src/test/java/com/minierp/backend/support/ApprovalApiTestSupport.java:59`
- `backend/src/test/java/com/minierp/backend/support/ApprovalApiTestSupport.java:66`
- `backend/src/test/java/com/minierp/backend/support/AttendanceTestSupport.java:70`
- `backend/src/test/java/com/minierp/backend/support/AttendanceTestSupport.java:73`
- `backend/src/test/java/com/minierp/backend/support/AttendanceTestSupport.java:85`
- `backend/src/test/java/com/minierp/backend/support/BoardTestSupport.java:11`
- `backend/src/test/java/com/minierp/backend/support/BoardTestSupport.java:25`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:15`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:17`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:19`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:21`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:25`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:27`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:190`

## 보존 확인과 선택한 방법

- 외부 포매터는 쓰지 않았습니다. 기존 JDK 구문 트리로 선언·제어문 경계를 확인하고 임시 토큰 편집 도구로 공백·줄바꿈·중괄호·선언 분리만 적용한 뒤 diff를 검토했습니다. 이 임시 편집 도구는 제품이나 빌드에 추가하지 않았습니다.
- 일반 문자열·문자 리터럴 6,821개, 텍스트 블록 58개(내용 및 닫는 줄 들여쓰기 포함), 주석 단어·구두점 1,405개, import 1,854개의 순서와 내용을 BASE와 대조했습니다. 모두 보존됐습니다.
- BASE에 허용된 중괄호 추가·선언 분리만 적용한 결과와 최종 소스의 토큰 151,465개를 비교했습니다. 공백·주석 배치를 제외하고 연속 > 기호의 분리 표기를 정규화했을 때 순서와 내용이 모두 같았습니다. import는 토큰뿐 아니라 각 줄의 내용도 모두 같습니다.
- formatter 의존성/IDE 일괄 포맷은 문자열과 import를 건드릴 수 있어 제외했습니다. 테스트만 통과하면 충분하다는 방법도 버리고 전체 출력의 바이트 동일성과 기록 ON/OFF 검증을 함께 사용했습니다.
- 측정 스크립트를 테스트 리소스에 두고 예외 diff를 허용하는 방법은 사용자 결정에 따라 버렸습니다. docs에 두어 예외 없이 전체 출력 diff 0을 유지합니다.
- 문자열 분할·텍스트 블록 변환은 금지됐으므로 긴 리터럴은 위치만 정리하고 위 목록에 남겼습니다.

### 기준선 해시

| 파일 | SHA-256 (BASE와 동일) |
|---|---|
| `sql.json` | `8ca96939bfbd887a037156bc523f7e60fdd9961910ec950a25c986e73fca4930` |
| `responses.json` | `2e2acc5d706ea90531d44a7f957938f5e9a67a0d3e049d8fde0fc8b41dddcebc` |
| `masked-fields.txt` | `7c4c8e12a7287aafc91ce4073f0ab047be4086f390cd26bc9901669acee5156e` |

## 발견했지만 고치지 않은 버그

이번 형식 정리와 검증에서 새로 확인한 제품 버그는 없습니다. 기존 알려진 이슈나 동작을 수정하지 않았습니다.

## 확인하지 못한 것과 남은 한계

- 실제 서버·개발 DB·개발 Redis에는 접속하지 않았습니다. DB 검증은 기존 Testcontainers 테스트 범위입니다.
- 측정 스크립트는 현재 저장소 Java 구문을 대상으로 한 렉서입니다. 임의의 모든 Java 문법을 검증하는 컴파일러 대체물이 아닙니다. 컴파일 결과의 동일성 검증을 별도로 사용했습니다.
- 기록 모드는 기존 정책상 일부 경쟁을 순서대로 처리합니다. 이를 일반 실행의 동시성 보장으로 과장하지 않고, 각 묶음의 OFF 전체 실행도 검증했습니다.
- 독립 리뷰어의 재실행은 이 작업 결과에 포함되지 않습니다.
- develop으로의 병합과 푸시는 하지 않았고 3·4단계는 시작하지 않았습니다. 마지막 문서 커밋은 Java 변경 없이 BASE와 전체 출력 diff 0만 다시 확인합니다.
