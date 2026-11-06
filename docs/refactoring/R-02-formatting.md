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

### 남은 문자열 리터럴 예외 (6db1ce8 기준, 파일:줄)

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

## 줄 바꿈 보완

### 발견한 문제와 바꾼 규칙

6db1ce8의 형식 정리는 바이트코드와 동작을 보존했지만, 줄 길이를 먼저 맞추면서 수신자·메서드·상수·제네릭 이름을 끊고 매개변수 어노테이션을 선언 어노테이션처럼 분리했습니다. 이번에는 인자/매개변수 경계 → 실제 호출 체인 → 연산자 순으로 나누고, 짧은 중첩 호출·키/값 쌍·한정된 이름을 함께 유지했습니다. 매개변수 목록이 길면 한 줄에 한 매개변수를 둡니다. 중첩 호출·체인의 이어지는 줄은 해당 호출을 시작한 줄보다 8칸 더 들여씁니다.

어노테이션은 클래스·메서드·필드 선언에서만 한 줄씩 둡니다. 매개변수·record 구성요소·타입 인자의 어노테이션과 대상은 붙였습니다. 완전 수식 이름 때문에 어노테이션 자체가 긴 AttendanceAdminController의 DateTimeFormat과 EmployeeRequests의 JsonFormat은 어노테이션 인자 내부만 나누고 닫는 괄호와 대상 타입은 같은 줄에 뒀습니다.

추가 지시에 따라 텍스트 블록의 내용과 닫는 구분자뿐 아니라 **닫는 줄 전체를 후속 코드까지 원문 그대로 보존**했습니다. 줄 끝의 공백도 포함합니다. 이 규칙 때문에 AttendanceStore의 ErrorCode / .USER_NOT_FOUND 한 곳은 보호 예외로 남깁니다. Java는 공백·줄바꿈만 변경했으며 새 중괄호·선언 분리·import·주석 변경은 없습니다.

### 중단 원인 재현과 재발 방지

이전 중단에서 막힌 파일은 `backend/src/main/java/com/minierp/backend/domain/leave/service/LeaveAdminService.java:62`입니다. 52행에서 시작하는 `var rows = db.rows(...)...toList();` 문장 하나만 추출하여 당시 임시 도구로 다시 처리했습니다. 공백 제거 비교가 실패했고 다음 토큰 범위가 누락됐습니다(아래는 원문 표기).

```java
""" + where + " ORDER BY u.user_name,u.id LIMIT :size OFFSET :offset", args).stream().map(
```

렉서는 텍스트 블록을 토큰 하나로 읽었지만 출력 단계가 이를 여는 줄·내용·닫는 줄로 나눈 뒤, 닫는 줄의 후속 호출을 일반 줄 바꿈 경로에 넣었습니다. 마지막 인자를 다시 배치하면서 `out[:-lastlen]`으로 출력 줄을 지웠는데, 별도로 계산한 인자의 줄 수가 실제로 차지한 줄 수보다 컸습니다. 그 결과 인자 앞의 닫는 구분자와 호출 시작까지 지웠습니다. 프로젝트 코드나 Java 문법 문제가 아니라 임시 편집 도구의 버그였습니다. 이 후보는 작업 트리에 적용하지 않았습니다.

수정은 두 부분입니다. 마지막 인자의 접미 토큰을 **배치 전에** 인자에 붙여 출력 줄을 역으로 삭제하지 않게 했습니다. 동시에 텍스트 블록 닫는 줄은 별도 보호 영역으로 복사하고 그다음 물리적 줄부터만 재배치합니다. 닫는 줄에서 다른 텍스트 블록을 여는 2곳도 내용이 빠지지 않도록 별도로 처리했습니다. 렉서는 역슬래시 뒤 문자 하나를 건너뛰어 `\"""`, 줄 끝 역슬래시, 이중 역슬래시를 구별합니다.

| 적용 전 검사 | 결과 |
|---|---|
| 실패한 문장만 이전 도구에 입력 | 누락 재현, 공백 제거 결과 불일치 |
| 텍스트 블록을 포함하는 문장별 검사 | 58/58 통과; 서로 다른 문장은 56개(한 문장에 블록이 2개인 경우 포함) |
| 이스케이프된 구분자·줄 끝 역슬래시·이중 역슬래시 fixture | 3/3 통과, 블록 전체를 토큰 하나로 보존 |
| 전체 Java 파일 검사(변경 없는 파일 포함) | 259/259 통과 |
| 일반 문자열·문자 리터럴 / 텍스트 블록 | 6,821 / 58개, 원문·순서 불일치 0 |
| 닫는 줄 전체와 주석 원문 | 58개 닫는 줄 보존, 불일치 0 |

후속 Java 코드가 있는 닫는 줄 42곳과 같은 줄에서 다음 블록을 여는 2곳을 포함하여, 닫는 줄 58개 모두를 비교했습니다. 평범한 `);` 등 닫는 구두점만 있는 경우도 빠뜨리지 않았습니다. 검사는 저장소 밖 임시 후보에서 먼저 통과시킨 뒤 main/test 묶음으로 적용했습니다.

추가 측정 중 record 헤더 범위를 찾는 임시 코드가 정규식 리터럴 `[A-Z0-9]{2,10}`의 `{`를 본문 시작으로 오인해 구성요소 5개를 놓친 것도 발견했습니다. JDK 구문 트리의 record 인스턴스 필드 목록으로 집계 범위를 고쳤고 BoardProjectRequests.Create는 직접 정리했습니다. 이때 이미 시작한 main 기록 ON 실행은 중단하여 `preliminary-batch-1`로 보존했으며 완료 증거에서 제외했습니다. 이후 `<`로 끝나는 줄 검사보다 넓은 JDK 제네릭 타입 범위 검사를 추가해 MariaDbPlan과 ExactJson의 타입 인자 중간 줄 바꿈 2곳을 발견했습니다. throws 때문에 길어진 선언도 전체 매개변수 단위로 재점검하여 JwtAuthenticationFilter와 M5ProductionTest를 함께 정리했습니다. 최종 후보의 제네릭 타입 내부 줄 바꿈과 여러 줄 매개변수 목록에서 같은 줄을 공유하는 매개변수는 모두 0입니다. 앞서 통과했던 main 후보도 최종 후보와 다르므로 `superseded-batch-1`로 분리하고 커밋을 갱신했습니다. 진행 중이던 test ON은 `preliminary-batch-2`로 분리했습니다. 아래 결과는 이 모든 보완 후 최종 후보로 처음부터 다시 실행한 결과입니다.

### 커밋별 새 검증

| 커밋 | 묶음 | 바뀐 Java 파일 | 직전 커밋과 공백 제거·리터럴 비교 | BASE와 클래스 / 전체 출력 | diff |
|---|---|---:|---|---|---:|
| `4d5ecf20bcfab809d64c73f3048dee01664f696d` | main | 101 | 101개, 불일치 0 | 330 / 356 | 0 |
| `8749538b21dd84b0bc0c1dc60975ee4417d86e56` | test | 67 | 67개, 불일치 0 | 330 / 356 | 0 |
| 이 절을 추가한 `docs(refactor): R-02 줄 바꿈 보완 기록` 커밋 | 문서·측정·blame 3개 파일 | 0 | Java 변경 없음 | 330 / 356 | 0 |

첫 비교 대상은 6db1ce8, 두 번째는 main 줄 바꿈 커밋입니다. 커밋마다 BASE `a1df7297dfd52736472c3897d57cd5b275711e29`와 후보/실제 커밋을 각각 새 임시 폴더에 `git archive`했고 아래 명령으로 컴파일했습니다. 클래스뿐 아니라 리소스 포함 두 출력 폴더 전체를 비교했습니다. 작업 트리에서 `mvn clean`은 실행하지 않았습니다.

```bash
./mvnw -q -Duser.timezone=UTC -Dmaven.compiler.debuglevel=none test-compile
diff -r BASE/backend/target/classes COMMIT/backend/target/classes
diff -r BASE/backend/target/test-classes COMMIT/backend/target/test-classes
```

| 묶음 | 기록 ON: 전체/실패/오류/건너뜀 | 시간(s) | differences | 순서 경고 | 기록 OFF: 전체/실패/오류/건너뜀 | 시간(s) | OFF 기록 출력 |
|---|---|---:|---:|---:|---|---:|---:|
| main | 231 / 0 / 0 / 0 | 534.25 | 0 | 11 | 231 / 0 / 0 / 0 | 459.91 | 0 |
| test | 231 / 0 / 0 / 0 | 517.03 | 0 | 10 | 231 / 0 / 0 / 0 | 474.3 | 0 |

시간은 컨테이너 준비를 포함한 벽시계 시간이며 성능 개선 수치가 아닙니다. 기존 1단계·2단계 검증을 이번 후보의 통과 증거로 사용하지 않았습니다. 기록 OFF에는 기존 순서 고정 훅이 설치되지 않습니다.

| SQL 순서 경고 프레임 | 발생 묶음 | 이유 |
|---|---|---|
| `com.minierp.backend.integration.M1AcceptanceTest#concurrentIdAllocationIsUniqueAndUnsafeMaxPlusOneCollides()` | main, test | 기존 동시 실행 프레임의 작업 스레드 SQL 순서 차이; SQL별 횟수·응답은 동일 |
| `com.minierp.backend.integration.M2DocumentIntegrationTest#concurrentApprovalAndWithdrawalHaveOnlyOneWinner()` | test | 기존 동시 실행 프레임의 작업 스레드 SQL 순서 차이; SQL별 횟수·응답은 동일 |
| `com.minierp.backend.integration.M2DocumentIntegrationTest#concurrentApprovalHasOne409AndNoNotificationFromLosingRequestInTenTrials()` | main, test | 기존 동시 실행 프레임의 작업 스레드 SQL 순서 차이; SQL별 횟수·응답은 동일 |
| `com.minierp.backend.integration.M2DocumentIntegrationTest#documentNumbersAreUniqueForEightDifferentDraftersSubmittingConcurrently()` | main, test | 기존 동시 실행 프레임의 작업 스레드 SQL 순서 차이; SQL별 횟수·응답은 동일 |
| `com.minierp.backend.integration.M2LegacyApprovalRaceTest#unconditionalLegacyApprovalAcceptsBothRequestsInTenControlledRaces()` | main, test | 기존 동시 실행 프레임의 작업 스레드 SQL 순서 차이; SQL별 횟수·응답은 동일 |
| `com.minierp.backend.integration.M3AttendanceTest#concurrentClockInHasExactlyOneWinnerInTenTrials()` | main, test | 기존 동시 실행 프레임의 작업 스레드 SQL 순서 차이; SQL별 횟수·응답은 동일 |
| `com.minierp.backend.integration.M3LeaveAdjustmentConcurrencyTest#unlockedModelFailsThenLockedAdjustmentsAndSubmissionsPreserveBalance()` | main, test | 기존 동시 실행 프레임의 작업 스레드 SQL 순서 차이; SQL별 횟수·응답은 동일 |
| `com.minierp.backend.integration.M3LeaveGrantTest#twoConcurrentBatchRunsIssueEachKeyOnlyOnceInTenTrials()` | main, test | 기존 동시 실행 프레임의 작업 스레드 SQL 순서 차이; SQL별 횟수·응답은 동일 |
| `com.minierp.backend.integration.M4BoardConcurrencyTest#gapsUpdateOneRowSerializeTwentyMovesAndRebalanceOnlyWhenExhausted()` | main | 기존 동시 실행 프레임의 작업 스레드 SQL 순서 차이; SQL별 횟수·응답은 동일 |
| `com.minierp.backend.integration.M4BoardConcurrencyTest#tenConcurrentCreatesHaveContiguousUniqueNumbers()` | main, test | 기존 동시 실행 프레임의 작업 스레드 SQL 순서 차이; SQL별 횟수·응답은 동일 |
| `com.minierp.backend.integration.M4OverdueTest#concurrentRunsSendOnlyOnce()` | main, test | 기존 동시 실행 프레임의 작업 스레드 SQL 순서 차이; SQL별 횟수·응답은 동일 |
| `com.minierp.backend.integration.M5ProductionTest#<before-all>` | main | Map.of 순회의 CREATE USER 두 줄 교환만 허용 |

경고 합집합 12개, 기존 13개 목록 밖 0개입니다. 엄격 목록·허용 예외·기록 정책·비교 규칙은 바꾸지 않았습니다.

### 측정 전후

측정 스크립트는 `docs/refactoring/R-02-format-measure.py`입니다. Python 표준 라이브러리와 설치된 JDK 파서만 사용하며, 소스를 수정하거나 애너테이션 프로세서를 실행하지 않습니다. 어노테이션의 끝과 대상 타입의 시작 줄을 비교하므로, 긴 어노테이션 인자를 나누는 경우와 어노테이션/대상 사이를 끊는 경우를 구별합니다. 메서드·생성자 매개변수는 163개이며 record 구성요소 65개를 별도로 집계합니다. 타입 인자는 10개입니다. 처음의 60개 집계는 위 정규식 중괄호 문제로 5개를 누락한 수치였습니다.

이름·멤버 끊김과 비체인 판별은 요청한 물리적 줄 기준을 그대로 사용합니다. 문자열·주석 내부는 제외하고 닫는 텍스트 블록 뒤 Java 코드는 포함합니다. 비체인은 바로 그다음(세 번째) 줄이 점으로 시작하지 않는 경우입니다. 새 측정 규칙은 주석·일반 문자열·텍스트 블록·닫는 줄의 코드 등을 포함한 fixture 7개도 통과했습니다.

| 범위 | 이름·멤버 끊김 | 그중 비체인 | `<`로 끝나는 줄 | 분리된 매개변수 어노테이션 | 분리된 record 어노테이션 | 분리된 타입 인자 어노테이션 |
|---|---:|---:|---:|---:|---:|---:|
| main | 148 → 1 | 127 → 1 | 10 → 0 | 160 → 0 | 65 → 0 | 10 → 0 |
| test | 173 → 0 | 148 → 0 | 0 → 0 | 3 → 0 | 0 → 0 | 0 → 0 |

| 범위 | 120자 초과(예외 제외) | 한 줄 여러 문장 | 여러 선언자 | 문자열 리터럴 예외 |
|---|---:|---:|---:|---:|
| main | 0 → 0 | 0 → 0 | 0 → 0 | 88 → 88 |
| test | 0 → 0 | 0 → 0 | 0 → 0 | 76 → 78 |

남은 비체인 1개는 `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceStore.java:98`입니다(6db1ce8의 AttendanceStore:97). ErrorCode가 보호된 닫는 줄 끝에 있으므로 .USER_NOT_FOUND를 올리지 않았습니다. 수정 가능한 비체인은 0개입니다.

일반 리터럴 예외는 164 → 166개입니다. M2DataMigrationTest의 information_schema.columns 조회와 M4BoardApiTest의 TASK_ASSIGNED 알림 개수 조회 리터럴이 중첩 인자의 8칸 들여쓰기를 적용하면서 새로 예외가 됐습니다. 문자열 내용이 길어진 것이 아니며 원문은 같습니다. 기존 예외 목록 164개는 위에 **6db1ce8 기준**으로 보존했습니다.

측정 명령(BASE 전후는 각각 git archive한 경로를 전달):
```bash
JAVA_HOME=/path/to/jdk17 python3 docs/refactoring/R-02-format-measure.py \
  --root /path/to/archive --output /tmp/r02-measure.json
```

### 줄 바꿈 전후 예시

InboxRepository: 수신자와 호출·키/값 쌍을 유지하고 안쪽 map의 인자를 구분합니다.

```java
// 6db1ce8
"formName", FormService
        .shortName((String) d.get("code"))
"department", d
        .get("department_name")

// 보완 후 (각각의 호출 부분)
"formName", FormService.shortName((String) d.get("code"))
"department", d.get("department_name")
```

DocumentService: 수신자를 끊지 않고 바깥 인자부터 나누며, 안쪽 map의 키/값을 붙입니다.

```java
// 6db1ce8
db
        .update(
        "UPDATE approval_documents SET title=:title,manual_line=:manual,version=version+1,updated_at=:now WHERE id=:id",
        map("id", id, "title", request.title(), "manual", request.line() != null, "now", LocalDateTime
        .now(clock)));

// 보완 후
db.update(
        "UPDATE approval_documents SET title=:title,manual_line=:manual,version=version+1,updated_at=:now WHERE id=:id",
        map("id", id, "title", request.title(), "manual", request.line() != null,
                "now", LocalDateTime.now(clock)));
```

DocumentController: 매개변수 어노테이션을 대상과 붙입니다.

```java
// 6db1ce8
public ApiResponse<?> delete(
        @PathVariable
        long id) {

// 보완 후
public ApiResponse<?> delete(@PathVariable long id) {
```

### 직접 정리한 위치

도구가 안전하게 배치하지 못한 긴 제네릭 필드·열린 블록 람다·record 헤더 등은 임시 후보에서 직접 정리하고 같은 공백 제거/리터럴 검사를 통과시켰습니다. 아래 줄은 최종 Java 기준입니다.

| 파일:줄 | 직접 정리한 부분 |
|---|---|
| `backend/src/main/java/com/minierp/backend/global/security/JwtAuthenticationFilter.java:31` | throws가 붙은 긴 선언의 매개변수 한 줄씩 배치 |
| `backend/src/test/java/com/minierp/backend/support/MariaDbPlan.java:14` | 제네릭 타입 내부를 붙이고 매개변수 경계에서 줄 바꿈 |
| `backend/src/test/java/com/minierp/backend/support/refactoring/ExactJson.java:28` | 제네릭 타입 내부를 붙이고 매개변수 경계에서 줄 바꿈 |
| `backend/src/test/java/com/minierp/backend/integration/M5ProductionTest.java:102` | throws가 붙은 긴 선언의 매개변수 한 줄씩 배치 |
| `backend/src/main/java/com/minierp/backend/domain/project/dto/ProjectPermissionUpdateRequestDto.java:13` | 긴 제네릭 타입과 변수 이름 경계 |
| `backend/src/main/java/com/minierp/backend/domain/project/dto/BoardProjectRequests.java:12` | 정규식 어노테이션이 있는 record 헤더 |
| `backend/src/main/java/com/minierp/backend/global/permission/PermissionAccess.java:54` | 보호된 닫는 줄 다음 람다 본문 들여쓰기 |
| `backend/src/test/java/com/minierp/backend/integration/DepartmentHeadConcurrencyTest.java:95` | 중첩 블록 람다의 인자 경계·본문 및 assert 인자 |
| `backend/src/test/java/com/minierp/backend/integration/M2DocumentIntegrationTest.java:159` | 블록 람다 뒤 when/store 호출 |
| `backend/src/test/java/com/minierp/backend/integration/M3LegacyAttendanceTest.java:69` | try 자원 선언의 인자와 다음 insert의 여는 중괄호 |
| `backend/src/test/java/com/minierp/backend/integration/M5ProdSecretsTest.java:24` | builder 체인과 블록 람다 들여쓰기 |
| `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:188` | 긴 기존 설명 리터럴 격리 |
| `backend/src/test/java/com/minierp/backend/integration/M2LegacyApprovalRaceTest.java:41` | 체인 안 람다 본문 들여쓰기 |
| `backend/src/test/java/com/minierp/backend/integration/M4QueryGrowthTest.java:101` | 체인 안 람다 본문 들여쓰기 |
| `backend/src/test/java/com/minierp/backend/integration/M3LeaveAdjustmentConcurrencyTest.java:51` | 기존 호출 닫는 괄호 뒤 여는 중괄호 같은 줄 배치 |

### 갱신한 문자열 리터럴 예외 (c742eea 기준)


main: 88개

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
- `backend/src/main/java/com/minierp/backend/domain/approval/service/DocumentService.java:371`
- `backend/src/main/java/com/minierp/backend/domain/approval/service/FormService.java:66`
- `backend/src/main/java/com/minierp/backend/domain/approval/service/FormService.java:82`
- `backend/src/main/java/com/minierp/backend/domain/approval/service/FormService.java:128`
- `backend/src/main/java/com/minierp/backend/domain/attendance/handler/AttendanceFixFormHandler.java:89`
- `backend/src/main/java/com/minierp/backend/domain/attendance/handler/AttendanceFixFormHandler.java:111`
- `backend/src/main/java/com/minierp/backend/domain/attendance/handler/AttendanceFixFormHandler.java:171`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:108`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:31`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:133`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceStore.java:34`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceStore.java:74`
- `backend/src/main/java/com/minierp/backend/domain/home/service/HomeQueries.java:15`
- `backend/src/main/java/com/minierp/backend/domain/home/service/HomeQueries.java:52`
- `backend/src/main/java/com/minierp/backend/domain/leave/entity/LeaveRequest.java:37`
- `backend/src/main/java/com/minierp/backend/domain/leave/entity/LeaveRequest.java:41`
- `backend/src/main/java/com/minierp/backend/domain/leave/entity/LeaveRequest.java:51`
- `backend/src/main/java/com/minierp/backend/domain/leave/entity/LeaveRequest.java:54`
- `backend/src/main/java/com/minierp/backend/domain/leave/handler/LeaveFormHandler.java:68`
- `backend/src/main/java/com/minierp/backend/domain/leave/handler/LeaveFormHandler.java:124`
- `backend/src/main/java/com/minierp/backend/domain/leave/handler/LeaveFormHandler.java:140`
- `backend/src/main/java/com/minierp/backend/domain/leave/service/LeaveAdminService.java:98`
- `backend/src/main/java/com/minierp/backend/domain/leave/service/LeaveAdminService.java:122`
- `backend/src/main/java/com/minierp/backend/domain/leave/service/LeaveGrantWork.java:39`
- `backend/src/main/java/com/minierp/backend/domain/leave/service/LeaveGrantWork.java:55`
- `backend/src/main/java/com/minierp/backend/domain/notification/service/NotificationService.java:19`
- `backend/src/main/java/com/minierp/backend/domain/notification/service/NotificationService.java:27`
- `backend/src/main/java/com/minierp/backend/domain/notification/service/NotificationService.java:34`
- `backend/src/main/java/com/minierp/backend/domain/org/service/OrgService.java:87`
- `backend/src/main/java/com/minierp/backend/domain/org/service/OrgService.java:186`
- `backend/src/main/java/com/minierp/backend/domain/org/service/OrgService.java:225`
- `backend/src/main/java/com/minierp/backend/domain/org/service/OrgService.java:282`
- `backend/src/main/java/com/minierp/backend/domain/overtime/entity/OvertimeRequest.java:25`
- `backend/src/main/java/com/minierp/backend/domain/overtime/entity/OvertimeRequest.java:37`
- `backend/src/main/java/com/minierp/backend/domain/overtime/handler/OvertimeFormHandler.java:42`
- `backend/src/main/java/com/minierp/backend/domain/overtime/handler/OvertimeFormHandler.java:48`
- `backend/src/main/java/com/minierp/backend/domain/permission/service/RoleService.java:26`
- `backend/src/main/java/com/minierp/backend/domain/permission/service/RoleService.java:32`
- `backend/src/main/java/com/minierp/backend/domain/permission/service/RoleService.java:54`
- `backend/src/main/java/com/minierp/backend/domain/permission/service/RoleService.java:78`
- `backend/src/main/java/com/minierp/backend/domain/permission/service/RoleService.java:117`
- `backend/src/main/java/com/minierp/backend/domain/project/repository/ProjectMemberRepository.java:16`
- `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:31`
- `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:125`
- `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:158`
- `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:192`
- `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:228`
- `backend/src/main/java/com/minierp/backend/domain/project/service/ProjectSummaryQuery.java:9`
- `backend/src/main/java/com/minierp/backend/domain/task/repository/TaskRepository.java:43`
- `backend/src/main/java/com/minierp/backend/domain/task/repository/TaskRepository.java:51`
- `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:36`
- `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:124`
- `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:174`
- `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:197`
- `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:265`
- `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:274`
- `backend/src/main/java/com/minierp/backend/domain/task/service/TaskOverdueWork.java:26`
- `backend/src/main/java/com/minierp/backend/domain/task/service/TaskOverdueWork.java:58`
- `backend/src/main/java/com/minierp/backend/domain/user/entity/User.java:45`
- `backend/src/main/java/com/minierp/backend/domain/user/entity/User.java:53`
- `backend/src/main/java/com/minierp/backend/domain/user/entity/User.java:66`
- `backend/src/main/java/com/minierp/backend/domain/user/entity/User.java:69`
- `backend/src/main/java/com/minierp/backend/domain/user/service/EmployeeService.java:44`
- `backend/src/main/java/com/minierp/backend/domain/user/service/EmployeeService.java:59`
- `backend/src/main/java/com/minierp/backend/domain/user/service/EmployeeService.java:76`
- `backend/src/main/java/com/minierp/backend/domain/user/service/EmployeeService.java:202`
- `backend/src/main/java/com/minierp/backend/domain/user/service/EmployeeService.java:256`
- `backend/src/main/java/com/minierp/backend/domain/user/service/EmployeeService.java:282`
- `backend/src/main/java/com/minierp/backend/domain/user/service/UserLookupService.java:31`
- `backend/src/main/java/com/minierp/backend/global/batch/BatchRunRecorder.java:27`
- `backend/src/main/java/com/minierp/backend/global/batch/BatchRunRecorder.java:31`

test: 78개

- `backend/src/test/java/com/minierp/backend/integration/CoreApiIntegrationTest.java:57`
- `backend/src/test/java/com/minierp/backend/integration/CoreApiIntegrationTest.java:61`
- `backend/src/test/java/com/minierp/backend/integration/DepartmentHeadConcurrencyTest.java:65`
- `backend/src/test/java/com/minierp/backend/integration/M1AcceptanceTest.java:336`
- `backend/src/test/java/com/minierp/backend/integration/M1AcceptanceTest.java:344`
- `backend/src/test/java/com/minierp/backend/integration/M1DataMigrationTest.java:48`
- `backend/src/test/java/com/minierp/backend/integration/M1DataMigrationTest.java:73`
- `backend/src/test/java/com/minierp/backend/integration/M2DataMigrationTest.java:83`
- `backend/src/test/java/com/minierp/backend/integration/M2DataMigrationTest.java:89`
- `backend/src/test/java/com/minierp/backend/integration/M2DocumentIntegrationTest.java:63`
- `backend/src/test/java/com/minierp/backend/integration/M2DocumentIntegrationTest.java:65`
- `backend/src/test/java/com/minierp/backend/integration/M2DocumentIntegrationTest.java:293`
- `backend/src/test/java/com/minierp/backend/integration/M2DocumentIntegrationTest.java:399`
- `backend/src/test/java/com/minierp/backend/integration/M2FormApiTest.java:103`
- `backend/src/test/java/com/minierp/backend/integration/M2InboxPerformanceTest.java:31`
- `backend/src/test/java/com/minierp/backend/integration/M2InboxPerformanceTest.java:94`
- `backend/src/test/java/com/minierp/backend/integration/M2InboxTest.java:16`
- `backend/src/test/java/com/minierp/backend/integration/M2ReviewTest.java:14`
- `backend/src/test/java/com/minierp/backend/integration/M2ReviewTest.java:31`
- `backend/src/test/java/com/minierp/backend/integration/M3AttendanceFixTest.java:31`
- `backend/src/test/java/com/minierp/backend/integration/M3AttendanceTest.java:117`
- `backend/src/test/java/com/minierp/backend/integration/M3AttendanceTest.java:126`
- `backend/src/test/java/com/minierp/backend/integration/M3BatchResponseTest.java:28`
- `backend/src/test/java/com/minierp/backend/integration/M3CsvLabelsTest.java:36`
- `backend/src/test/java/com/minierp/backend/integration/M3DataMigrationTest.java:25`
- `backend/src/test/java/com/minierp/backend/integration/M3DataMigrationTest.java:29`
- `backend/src/test/java/com/minierp/backend/integration/M3DataMigrationTest.java:31`
- `backend/src/test/java/com/minierp/backend/integration/M3DataMigrationTest.java:40`
- `backend/src/test/java/com/minierp/backend/integration/M3DataMigrationTest.java:46`
- `backend/src/test/java/com/minierp/backend/integration/M3DataMigrationTest.java:57`
- `backend/src/test/java/com/minierp/backend/integration/M3LeaveAdjustmentConcurrencyTest.java:51`
- `backend/src/test/java/com/minierp/backend/integration/M3LeaveGrantTest.java:181`
- `backend/src/test/java/com/minierp/backend/integration/M3LegacyAttendanceTest.java:31`
- `backend/src/test/java/com/minierp/backend/integration/M3LegacyAttendanceTest.java:43`
- `backend/src/test/java/com/minierp/backend/integration/M3LegacyAttendanceTest.java:77`
- `backend/src/test/java/com/minierp/backend/integration/M3MonthlyTest.java:174`
- `backend/src/test/java/com/minierp/backend/integration/M4BoardApiTest.java:200`
- `backend/src/test/java/com/minierp/backend/integration/M4BoardApiTest.java:281`
- `backend/src/test/java/com/minierp/backend/integration/M4BoardConcurrencyTest.java:159`
- `backend/src/test/java/com/minierp/backend/integration/M4BoardConcurrencyTest.java:231`
- `backend/src/test/java/com/minierp/backend/integration/M4DataMigrationTest.java:24`
- `backend/src/test/java/com/minierp/backend/integration/M4DataMigrationTest.java:28`
- `backend/src/test/java/com/minierp/backend/integration/M4DataMigrationTest.java:33`
- `backend/src/test/java/com/minierp/backend/integration/M4DeterministicLockTest.java:114`
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
- `backend/src/test/java/com/minierp/backend/integration/M4LegacyExperimentTest.java:88`
- `backend/src/test/java/com/minierp/backend/integration/M4OverdueTest.java:47`
- `backend/src/test/java/com/minierp/backend/integration/M4OverdueTest.java:90`
- `backend/src/test/java/com/minierp/backend/integration/M4QueryGrowthTest.java:34`
- `backend/src/test/java/com/minierp/backend/integration/M4QueryGrowthTest.java:52`
- `backend/src/test/java/com/minierp/backend/integration/M4QueryGrowthTest.java:107`
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
- `backend/src/test/java/com/minierp/backend/support/BoardTestSupport.java:26`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:14`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:16`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:18`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:20`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:24`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:26`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:189`

### 확인하지 못한 것과 남은 한계

- 실제 서버·개발 DB·개발 Redis는 호출하지 않았습니다. 기능 검증은 기존 Testcontainers 전체 테스트로 한정했습니다.
- 처리 시간은 환경에 따라 달라지며 성능 효과로 주장하지 않습니다.
- 이름·멤버의 비체인 측정은 요청한 물리적 줄 휴리스틱입니다. 어노테이션은 JDK 구문 트리로 확인했습니다.
- 보호 예외 AttendanceStore 한 곳과 긴 리터럴 166줄은 의도적으로 남깁니다.
- 수정한 것은 형식과 측정/문서뿐이며 이번 작업에서 별도의 제품 버그를 확정하거나 수정하지 않았습니다. 3·4단계는 시작하지 않았습니다.

검증 원본: `backend/target/r02-wrap-validation/batch-{1,2,3}/`의 result.json, 컴파일 로그, 출력 diff 로그, ON/OFF 로그와 기록/비교 결과입니다. 중단하거나 교체된 후보는 `preliminary-batch-1/`, `superseded-batch-1/`, `preliminary-batch-2/`로 분리해 통과 증거에서 제외했습니다. 텍스트 블록 문장별 검사·전체 소스 비교·측정 결과와 구문 범위 검사도 같은 검증 폴더에 보존했습니다. 원본 로그는 커밋하지 않았습니다. 기존 `target/refactoring-v2-validation/record-{1..5}`는 보존했습니다. 임시 편집 도구도 저장소에 넣지 않았습니다.


## 줄 바꿈 보완 2

### 문제·원인·규칙

c742eea에서는 120자를 맞출 때 바깥 연산식보다 안쪽 호출의 인자 목록을 먼저 나눴습니다. 그 결과 짧은 `value.matches(...)`, `old.get(...)`, `startDate.equals(endDate)`가 갈라졌고, 논리식의 같은 단계 연산자에도 서로 다른 들여쓰기가 생겼습니다. SQL·응답·동작 문제는 아니며 이번 변경은 공백과 줄바꿈으로만 한정했습니다.

이번에는 **넘치는 식의 가장 바깥 구조**를 먼저 봅니다. 연산식은 `||` → `&&` → `? :` → `+` 순으로 연산자 앞에서 나누고, 같은 단계 조각은 120자 안에서 채웁니다. 괄호로 묶은 조각과 한 줄에 들어가는 호출은 붙입니다. 나눈 조각도 넘칠 때만 그 조각의 시작 줄보다 8칸 더 들여써 다시 나눕니다. 인자 목록은 쉼표 뒤, 긴 호출 체인은 점 앞에서 나눕니다. 키/값 쌍은 함께 옮기며 한 쌍 자체가 넘칠 때만 값을 8칸 더 들여씁니다. 비교 연산자 앞은 나누지 않았습니다.

외부 포매터를 쓰지 않고, 저장소 밖 c742eea 아카이브의 23개 파일(main 16, test 7), 24개 범위를 직접 정리했습니다. 첫 임시 후보에서는 FormService의 치환 끝 범위를 한 줄 크게 지정하여 `return out;`이 빠졌습니다. 적용 전 전체 검사에서 공백 제거·토큰 비교가 이를 막았고, 치환 범위를 고친 뒤 259개 모두 다시 통과했습니다. 이 실패 후보를 저장소에 적용하거나 완료 검증에 사용하지 않았습니다. 임시 편집/검사 도구는 저장소에 넣지 않았습니다.

### 커밋과 검증

| 커밋 | 내용 | 파일 수 | BASE 대비 전체 출력 |
|---|---|---:|---|
| `77bc39a2865a593df766abad56993ddbb2da8000` | style(backend): 줄 바꿈 보완 2 — 연산식·키값 | Java 23 | 클래스 330개 포함 356개 파일, diff 0 |
| 이 절을 추가한 `docs(refactor): R-02 줄 바꿈 보완 2 기록` | 보고서·측정 스크립트·blame | 3 | 클래스 330개 포함 356개 파일, diff 0 |

| 적용 전 검사(c742eea 대비, 변경 없는 파일 포함) | 결과 |
|---|---|
| Java 259개 공백 제거·토큰 순서 | 불일치 0 |
| 일반 문자열/문자 리터럴 | 6,821개, 원문·순서 불일치 0 |
| 텍스트 블록 | 58개, 원문·순서 불일치 0 |
| 텍스트 블록 닫는 물리적 줄 전체 | 58개, 들여쓰기·뒤 코드·줄 끝 공백 불일치 0 |
| 주석과 import | 원문·순서 불일치 0 |
| 새 측정 fixture | 12/12 통과 |

두 커밋 각각 BASE `a1df7297dfd52736472c3897d57cd5b275711e29`와 비교 대상을 작업 트리 밖 새 폴더에 `git archive`하여 컴파일했습니다. `target/classes`는 236개 클래스/245개 전체 파일, `target/test-classes`는 94개 클래스/111개 전체 파일입니다. 둘 다 디렉터리 전체 `diff -r` 종료 코드 0입니다. staged tree를 검증하고 실제 커밋 tree가 같은지도 확인했습니다. 작업 트리에서 `mvn clean`은 실행하지 않았습니다.

```bash
./mvnw -q -Duser.timezone=UTC -Dmaven.compiler.debuglevel=none test-compile
diff -r BASE/backend/target/classes CANDIDATE/backend/target/classes
diff -r BASE/backend/target/test-classes CANDIDATE/backend/target/test-classes
```

아래는 이번 최종 Java 후보의 **새 실행** 결과입니다. 이전 보완의 결과를 재사용하지 않았습니다. 문서 커밋은 Java·테스트 리소스 변경이 없어 지시대로 전체 테스트를 반복하지 않고 출력 비교만 했습니다.

| 모드 | 전체 | 실패 | 오류 | 건너뜀 | 벽시계 시간(s) | 기록/비교 |
|---|---:|---:|---:|---:|---:|---|
| UTC 기록 ON | 231 | 0 | 0 | 0 | 519.4 | REFACTORING_COMPARE differences=0 sqlOrderChanges=10 |
| UTC 기록 OFF | 231 | 0 | 0 | 0 | 549.27 | REFACTORING_RECORDED 0건 |

시간은 Testcontainers 기동을 포함한 검증 소요 시간이며 성능 개선 근거가 아닙니다.

| 이번 ON 순서 경고 프레임 | 기존 사유 |
|---|---|
| `M1AcceptanceTest#concurrentIdAllocationIsUniqueAndUnsafeMaxPlusOneCollides()` | 병렬 사원 등록의 부서·사용자 조회 진입 순서 |
| `M2DocumentIntegrationTest#concurrentApprovalHasOne409AndNoNotificationFromLosingRequestInTenTrials()` | 같은 문서 승인 2회의 인증·권한·잠금 조회가 교차함 |
| `M2DocumentIntegrationTest#documentNumbersAreUniqueForEightDifferentDraftersSubmittingConcurrently()` | HTTP 순서는 고정, Hikari 연결 초기화 SELECT @@session.tx_isolation의 실행 위치 |
| `M2LegacyApprovalRaceTest#unconditionalLegacyApprovalAcceptsBothRequestsInTenControlledRaces()` | 수정 전 직접 JDBC 재현의 두 승인 트랜잭션이 문서 상태·잔액을 읽는 순서 |
| `M3AttendanceTest#concurrentClockInHasExactlyOneWinnerInTenTrials()` | 대칭 출근 경합의 사용자·근태 조회가 교차함 |
| `M3LeaveAdjustmentConcurrencyTest#unlockedModelFailsThenLockedAdjustmentsAndSubmissionsPreserveBalance()` | 직접 JDBC 재현 및 앞 10회 대칭 조정의 SQL이 교차함. 마지막 비대칭 HTTP는 고정 |
| `M3LeaveGrantTest#twoConcurrentBatchRunsIssueEachKeyOnlyOnceInTenTrials()` | 두 부여 배치의 사용자/기부여 키 조회 순서 |
| `M4BoardConcurrencyTest#gapsUpdateOneRowSerializeTwentyMovesAndRebalanceOnlyWhenExhausted()` | HTTP 이동 순서는 고정, Hikari 연결 초기화 SELECT @@session.tx_isolation의 실행 위치 |
| `M4BoardConcurrencyTest#tenConcurrentCreatesHaveContiguousUniqueNumbers()` | 같은 업무 등록 10개의 인증·권한 조회가 교차함 |
| `M4OverdueTest#concurrentRunsSendOnlyOnce()` | 마감 알림 두 배치의 대상 프로젝트 조회·잠금 순서 |

경고 10개가 모두 기존 13개 목록 안입니다. SQL별 횟수·workerSql·응답 차이는 0이며 엄격 목록, M5의 두 CREATE USER 교환 예외, 기록 정책, 비교 규칙은 그대로입니다.

### 측정 전후와 목표 수치의 차이

`docs/refactoring/R-02-format-measure.py`에 JDK 구문 트리 기반 두 항목을 추가했습니다. 전체 메서드 호출 및 `new` 범위의 공백을 압축하여 60자 이하이고 인자 목록에 줄바꿈이 있으면 짧은 호출 분리로 셉니다. 첫 인자가 형변환·괄호 식이어도 검사합니다. 텍스트 블록·람다·주석이 든 호출은 제외합니다. 키/값 분리는 `map(...)`·`SqlStore.map(...)`·`Map.of(...)` 인자 쌍에서 키의 끝과 값의 시작 사이 줄바꿈을 셉니다. JDK의 UTF-16 위치를 Python 문자열 위치로 변환하므로 보조 평면 문자 뒤에서도 같은 범위를 봅니다.

fixture 12개는 일반 호출, 괄호로 시작하는 형변환, new, 텍스트 블록 제외, 람다 제외, 주석 제외, 60자 초과 제외, Map.of 분리, SqlStore.map 쌍 경계, 정적 map 분리, 문자열 안 주석 모양, 보조 평면 문자입니다. 측정은 읽기 전용이고 설치된 JDK 파서와 Python 표준 라이브러리만 사용합니다.

| 항목 | main: c742eea → 최종 | test: c742eea → 최종 |
|---|---:|---:|
| 짧은 호출 분리 | 19 → 0 | 5 → 0 |
| 키·값 분리 | 5 → 4 | 10 → 8 |
| 120자 초과(리터럴 예외/블록 제외) | 0 → 0 | 0 → 0 |
| 한 줄 여러 문장 | 0 → 0 | 0 → 0 |
| 여러 선언자 | 0 → 0 | 0 → 0 |
| `<`로 끝나는 줄 | 0 → 0 | 0 → 0 |
| 이름·멤버 끊김 | 1 → 1 | 0 → 0 |
| 그중 비체인 | 1 → 1 | 0 → 0 |
| 떨어진 매개변수 어노테이션 | 0 → 0 | 0 → 0 |
| 떨어진 record 어노테이션 | 0 → 0 | 0 → 0 |
| 떨어진 타입 인자 어노테이션 | 0 → 0 | 0 → 0 |
| 문자열 리터럴 예외 | 88 → 88 | 78 → 78 |

**키·값 분리의 실제 결과는 15 → 12이며, 지시의 표에 적힌 11과 1개 차이가 있습니다.** SnapshotComparator 7개와 보호된 닫는 줄 4개 외에 RecordingOrder 1개가 남습니다. 해당 M3 테스트 이름 키는 12칸 들여쓰기와 쉼표까지 118자이며 같은 줄에 ` 2,`를 붙이면 **121자**입니다. 따라서 120자 규칙과 지시의 RecordingOrder 예시대로 값을 다음 줄에 8칸 더 들여쓰고, 다음 쌍은 새 줄에서 시작했습니다. 수치를 11로 맞추기 위해 들여쓰기·리터럴·측정 정의를 바꾸지 않았습니다. 이 1개는 텍스트 블록 보호 예외가 아니라 긴 키/값 쌍의 일반 예외입니다.

문자열 리터럴 예외는 166개(main 88, test 78)로 동일합니다. 예외에 해당하는 리터럴 집합도 같습니다. 일부 파일의 줄 번호가 바뀌었으므로 이전 목록은 **c742eea 기준**으로 보존하고 아래에 최종 위치 166개를 다시 적었습니다.

```bash
JAVA_HOME=/path/to/jdk17 python3 docs/refactoring/R-02-format-measure.py \
  --root /path/to/c742eea-archive --output /tmp/r02-wrap2-before.json
JAVA_HOME=/path/to/jdk17 python3 docs/refactoring/R-02-format-measure.py \
  --root /path/to/current-worktree --output /tmp/r02-wrap2-after.json
```

### 전후 예시

PasswordPolicy — c742eea:

```java
        return value != null && value.length() >= 8 && value.length() <= 20 && value.matches(".*[A-Z].*")
                && value.matches(
                ".*[a-z].*") && value.matches(".*[0-9].*") && value.matches(
                        ".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*");
```

보완 후:

```java
        return value != null && value.length() >= 8 && value.length() <= 20 && value.matches(".*[A-Z].*")
                && value.matches(".*[a-z].*") && value.matches(".*[0-9].*")
                && value.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*");
```

AbsenceBatchWork — c742eea:

```java
                if ("ON_LEAVE".equals(user.get("status")) || eligible.isAfter(date) || ("RETIRED".equals(
                        user.get("status")) && (user.get("retired_date") == null || !date.isBefore(
                                (LocalDate) user.get("retired_date")))) || !bool(user, "activated")
                                        || day.type().equals("HOLIDAY")) {
```

보완 후:

```java
                if ("ON_LEAVE".equals(user.get("status")) || eligible.isAfter(date)
                        || ("RETIRED".equals(user.get("status"))
                                && (user.get("retired_date") == null
                                        || !date.isBefore((LocalDate) user.get("retired_date"))))
                        || !bool(user, "activated") || day.type().equals("HOLIDAY")) {
```

RecordingOrder — c742eea:

```java
            "M2DocumentIntegrationTest#documentNumbersAreUniqueForEightDifferentDraftersSubmittingConcurrently()", 8,
            "M3LeaveAdjustmentConcurrencyTest#unlockedModelFailsThenLockedAdjustmentsAndSubmissionsPreserveBalance()",
                    2, "M4BoardConcurrencyTest#projectLockPreventsWriteSkewInTenConcurrentCompletions()", 2,
            "M4BoardConcurrencyTest#gapsUpdateOneRowSerializeTwentyMovesAndRebalanceOnlyWhenExhausted()", 2);
```

보완 후:

```java
            "M2DocumentIntegrationTest#documentNumbersAreUniqueForEightDifferentDraftersSubmittingConcurrently()", 8,
            "M3LeaveAdjustmentConcurrencyTest#unlockedModelFailsThenLockedAdjustmentsAndSubmissionsPreserveBalance()",
                    2,
            "M4BoardConcurrencyTest#projectLockPreventsWriteSkewInTenConcurrentCompletions()", 2,
            "M4BoardConcurrencyTest#gapsUpdateOneRowSerializeTwentyMovesAndRebalanceOnlyWhenExhausted()", 2);
```

### 닫는 줄 보호 예외 7곳

58개 닫는 줄은 모두 보존했습니다. 다음 7곳은 그 보호 규칙 때문에 일반 줄 바꿈 규칙을 완전히 적용할 수 없습니다. 마지막 열은 이번 최종 Java의 줄 번호입니다.

| 파일 | 예외 | c742eea → 최종 줄 |
|---|---|---|
| `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceStore.java` | 닫는 줄이 ErrorCode로 끝나고 다음 줄 .USER_NOT_FOUND | 98 → 99 |
| `backend/src/main/java/com/minierp/backend/domain/approval/service/DocumentService.java` | 닫는 줄 끝 original 키와 다음 줄 값 분리 | 124 → 124 |
| `backend/src/main/java/com/minierp/backend/domain/attendance/handler/AttendanceFixFormHandler.java` | 닫는 줄 끝 out 키와 다음 줄 값 분리 | 60 → 60 |
| `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceStore.java` | 닫는 줄 끝 outResult 키와 다음 줄 값 분리 | 136 → 137 |
| `backend/src/main/java/com/minierp/backend/domain/user/service/EmployeeService.java` | 닫는 줄 끝 position 키와 다음 줄 값 분리 | 216 → 216 |
| `backend/src/main/java/com/minierp/backend/domain/leave/service/LeaveAdminService.java` | 닫는 줄의 .stream() 호출 유지 | 62 → 62 |
| `backend/src/main/java/com/minierp/backend/domain/leave/service/LeaveQueryService.java` | 닫는 줄의 .stream() 호출 유지 | 73 → 73 |

### 직접 고친 파일:줄

아래는 최종 파일 기준 변경 블록의 시작 줄입니다. 하나의 블록 안에서 이어지는 줄도 함께 정리했습니다. 모든 변경은 같은 259개 전체 보존 검사를 통과했습니다.

| 파일 | 최종 변경 블록 시작 줄 |
|---|---|
| `backend/src/main/java/com/minierp/backend/domain/approval/repository/InboxRepository.java` | 50 |
| `backend/src/main/java/com/minierp/backend/domain/approval/service/DocumentService.java` | 375 |
| `backend/src/main/java/com/minierp/backend/domain/approval/service/FormService.java` | 65, 95 |
| `backend/src/main/java/com/minierp/backend/domain/attendance/handler/AttendanceFixFormHandler.java` | 143 |
| `backend/src/main/java/com/minierp/backend/domain/attendance/service/AbsenceBatchWork.java` | 36 |
| `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java` | 107 |
| `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceStore.java` | 28 |
| `backend/src/main/java/com/minierp/backend/domain/leave/entity/LeaveRequest.java` | 138 |
| `backend/src/main/java/com/minierp/backend/domain/leave/service/LeaveAdminService.java` | 84 |
| `backend/src/main/java/com/minierp/backend/domain/org/service/OrgService.java` | 108 |
| `backend/src/main/java/com/minierp/backend/domain/permission/service/RoleService.java` | 107 |
| `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java` | 93 |
| `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java` | 108 |
| `backend/src/main/java/com/minierp/backend/domain/user/service/EmployeeService.java` | 148 |
| `backend/src/main/java/com/minierp/backend/global/exception/GlobalExceptionHandler.java` | 53 |
| `backend/src/main/java/com/minierp/backend/global/security/PasswordPolicy.java` | 11 |
| `backend/src/test/java/com/minierp/backend/integration/CoreApiIntegrationTest.java` | 92 |
| `backend/src/test/java/com/minierp/backend/integration/M1AcceptanceTest.java` | 584 |
| `backend/src/test/java/com/minierp/backend/integration/M2DocumentIntegrationTest.java` | 259 |
| `backend/src/test/java/com/minierp/backend/support/JdbcProbe.java` | 57 |
| `backend/src/test/java/com/minierp/backend/support/refactoring/RecordingOrder.java` | 18 |
| `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java` | 214 |
| `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotNormalizer.java` | 28 |

### 문자열 리터럴 예외 — 보완 2 최종 기준

main: 88개

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
- `backend/src/main/java/com/minierp/backend/domain/approval/service/DocumentService.java:371`
- `backend/src/main/java/com/minierp/backend/domain/approval/service/FormService.java:67`
- `backend/src/main/java/com/minierp/backend/domain/approval/service/FormService.java:86`
- `backend/src/main/java/com/minierp/backend/domain/approval/service/FormService.java:132`
- `backend/src/main/java/com/minierp/backend/domain/attendance/handler/AttendanceFixFormHandler.java:89`
- `backend/src/main/java/com/minierp/backend/domain/attendance/handler/AttendanceFixFormHandler.java:111`
- `backend/src/main/java/com/minierp/backend/domain/attendance/handler/AttendanceFixFormHandler.java:173`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:108`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:31`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:133`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceStore.java:35`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceStore.java:75`
- `backend/src/main/java/com/minierp/backend/domain/home/service/HomeQueries.java:15`
- `backend/src/main/java/com/minierp/backend/domain/home/service/HomeQueries.java:52`
- `backend/src/main/java/com/minierp/backend/domain/leave/entity/LeaveRequest.java:37`
- `backend/src/main/java/com/minierp/backend/domain/leave/entity/LeaveRequest.java:41`
- `backend/src/main/java/com/minierp/backend/domain/leave/entity/LeaveRequest.java:51`
- `backend/src/main/java/com/minierp/backend/domain/leave/entity/LeaveRequest.java:54`
- `backend/src/main/java/com/minierp/backend/domain/leave/handler/LeaveFormHandler.java:68`
- `backend/src/main/java/com/minierp/backend/domain/leave/handler/LeaveFormHandler.java:124`
- `backend/src/main/java/com/minierp/backend/domain/leave/handler/LeaveFormHandler.java:140`
- `backend/src/main/java/com/minierp/backend/domain/leave/service/LeaveAdminService.java:98`
- `backend/src/main/java/com/minierp/backend/domain/leave/service/LeaveAdminService.java:122`
- `backend/src/main/java/com/minierp/backend/domain/leave/service/LeaveGrantWork.java:39`
- `backend/src/main/java/com/minierp/backend/domain/leave/service/LeaveGrantWork.java:55`
- `backend/src/main/java/com/minierp/backend/domain/notification/service/NotificationService.java:19`
- `backend/src/main/java/com/minierp/backend/domain/notification/service/NotificationService.java:27`
- `backend/src/main/java/com/minierp/backend/domain/notification/service/NotificationService.java:34`
- `backend/src/main/java/com/minierp/backend/domain/org/service/OrgService.java:87`
- `backend/src/main/java/com/minierp/backend/domain/org/service/OrgService.java:187`
- `backend/src/main/java/com/minierp/backend/domain/org/service/OrgService.java:226`
- `backend/src/main/java/com/minierp/backend/domain/org/service/OrgService.java:283`
- `backend/src/main/java/com/minierp/backend/domain/overtime/entity/OvertimeRequest.java:25`
- `backend/src/main/java/com/minierp/backend/domain/overtime/entity/OvertimeRequest.java:37`
- `backend/src/main/java/com/minierp/backend/domain/overtime/handler/OvertimeFormHandler.java:42`
- `backend/src/main/java/com/minierp/backend/domain/overtime/handler/OvertimeFormHandler.java:48`
- `backend/src/main/java/com/minierp/backend/domain/permission/service/RoleService.java:26`
- `backend/src/main/java/com/minierp/backend/domain/permission/service/RoleService.java:32`
- `backend/src/main/java/com/minierp/backend/domain/permission/service/RoleService.java:54`
- `backend/src/main/java/com/minierp/backend/domain/permission/service/RoleService.java:78`
- `backend/src/main/java/com/minierp/backend/domain/permission/service/RoleService.java:117`
- `backend/src/main/java/com/minierp/backend/domain/project/repository/ProjectMemberRepository.java:16`
- `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:31`
- `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:126`
- `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:159`
- `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:193`
- `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:229`
- `backend/src/main/java/com/minierp/backend/domain/project/service/ProjectSummaryQuery.java:9`
- `backend/src/main/java/com/minierp/backend/domain/task/repository/TaskRepository.java:43`
- `backend/src/main/java/com/minierp/backend/domain/task/repository/TaskRepository.java:51`
- `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:36`
- `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:126`
- `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:176`
- `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:199`
- `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:267`
- `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:276`
- `backend/src/main/java/com/minierp/backend/domain/task/service/TaskOverdueWork.java:26`
- `backend/src/main/java/com/minierp/backend/domain/task/service/TaskOverdueWork.java:58`
- `backend/src/main/java/com/minierp/backend/domain/user/entity/User.java:45`
- `backend/src/main/java/com/minierp/backend/domain/user/entity/User.java:53`
- `backend/src/main/java/com/minierp/backend/domain/user/entity/User.java:66`
- `backend/src/main/java/com/minierp/backend/domain/user/entity/User.java:69`
- `backend/src/main/java/com/minierp/backend/domain/user/service/EmployeeService.java:44`
- `backend/src/main/java/com/minierp/backend/domain/user/service/EmployeeService.java:59`
- `backend/src/main/java/com/minierp/backend/domain/user/service/EmployeeService.java:76`
- `backend/src/main/java/com/minierp/backend/domain/user/service/EmployeeService.java:202`
- `backend/src/main/java/com/minierp/backend/domain/user/service/EmployeeService.java:256`
- `backend/src/main/java/com/minierp/backend/domain/user/service/EmployeeService.java:282`
- `backend/src/main/java/com/minierp/backend/domain/user/service/UserLookupService.java:31`
- `backend/src/main/java/com/minierp/backend/global/batch/BatchRunRecorder.java:27`
- `backend/src/main/java/com/minierp/backend/global/batch/BatchRunRecorder.java:31`

test: 78개

- `backend/src/test/java/com/minierp/backend/integration/CoreApiIntegrationTest.java:57`
- `backend/src/test/java/com/minierp/backend/integration/CoreApiIntegrationTest.java:61`
- `backend/src/test/java/com/minierp/backend/integration/DepartmentHeadConcurrencyTest.java:65`
- `backend/src/test/java/com/minierp/backend/integration/M1AcceptanceTest.java:336`
- `backend/src/test/java/com/minierp/backend/integration/M1AcceptanceTest.java:344`
- `backend/src/test/java/com/minierp/backend/integration/M1DataMigrationTest.java:48`
- `backend/src/test/java/com/minierp/backend/integration/M1DataMigrationTest.java:73`
- `backend/src/test/java/com/minierp/backend/integration/M2DataMigrationTest.java:83`
- `backend/src/test/java/com/minierp/backend/integration/M2DataMigrationTest.java:89`
- `backend/src/test/java/com/minierp/backend/integration/M2DocumentIntegrationTest.java:63`
- `backend/src/test/java/com/minierp/backend/integration/M2DocumentIntegrationTest.java:65`
- `backend/src/test/java/com/minierp/backend/integration/M2DocumentIntegrationTest.java:293`
- `backend/src/test/java/com/minierp/backend/integration/M2DocumentIntegrationTest.java:399`
- `backend/src/test/java/com/minierp/backend/integration/M2FormApiTest.java:103`
- `backend/src/test/java/com/minierp/backend/integration/M2InboxPerformanceTest.java:31`
- `backend/src/test/java/com/minierp/backend/integration/M2InboxPerformanceTest.java:94`
- `backend/src/test/java/com/minierp/backend/integration/M2InboxTest.java:16`
- `backend/src/test/java/com/minierp/backend/integration/M2ReviewTest.java:14`
- `backend/src/test/java/com/minierp/backend/integration/M2ReviewTest.java:31`
- `backend/src/test/java/com/minierp/backend/integration/M3AttendanceFixTest.java:31`
- `backend/src/test/java/com/minierp/backend/integration/M3AttendanceTest.java:117`
- `backend/src/test/java/com/minierp/backend/integration/M3AttendanceTest.java:126`
- `backend/src/test/java/com/minierp/backend/integration/M3BatchResponseTest.java:28`
- `backend/src/test/java/com/minierp/backend/integration/M3CsvLabelsTest.java:36`
- `backend/src/test/java/com/minierp/backend/integration/M3DataMigrationTest.java:25`
- `backend/src/test/java/com/minierp/backend/integration/M3DataMigrationTest.java:29`
- `backend/src/test/java/com/minierp/backend/integration/M3DataMigrationTest.java:31`
- `backend/src/test/java/com/minierp/backend/integration/M3DataMigrationTest.java:40`
- `backend/src/test/java/com/minierp/backend/integration/M3DataMigrationTest.java:46`
- `backend/src/test/java/com/minierp/backend/integration/M3DataMigrationTest.java:57`
- `backend/src/test/java/com/minierp/backend/integration/M3LeaveAdjustmentConcurrencyTest.java:51`
- `backend/src/test/java/com/minierp/backend/integration/M3LeaveGrantTest.java:181`
- `backend/src/test/java/com/minierp/backend/integration/M3LegacyAttendanceTest.java:31`
- `backend/src/test/java/com/minierp/backend/integration/M3LegacyAttendanceTest.java:43`
- `backend/src/test/java/com/minierp/backend/integration/M3LegacyAttendanceTest.java:77`
- `backend/src/test/java/com/minierp/backend/integration/M3MonthlyTest.java:174`
- `backend/src/test/java/com/minierp/backend/integration/M4BoardApiTest.java:200`
- `backend/src/test/java/com/minierp/backend/integration/M4BoardApiTest.java:281`
- `backend/src/test/java/com/minierp/backend/integration/M4BoardConcurrencyTest.java:159`
- `backend/src/test/java/com/minierp/backend/integration/M4BoardConcurrencyTest.java:231`
- `backend/src/test/java/com/minierp/backend/integration/M4DataMigrationTest.java:24`
- `backend/src/test/java/com/minierp/backend/integration/M4DataMigrationTest.java:28`
- `backend/src/test/java/com/minierp/backend/integration/M4DataMigrationTest.java:33`
- `backend/src/test/java/com/minierp/backend/integration/M4DeterministicLockTest.java:114`
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
- `backend/src/test/java/com/minierp/backend/integration/M4LegacyExperimentTest.java:88`
- `backend/src/test/java/com/minierp/backend/integration/M4OverdueTest.java:47`
- `backend/src/test/java/com/minierp/backend/integration/M4OverdueTest.java:90`
- `backend/src/test/java/com/minierp/backend/integration/M4QueryGrowthTest.java:34`
- `backend/src/test/java/com/minierp/backend/integration/M4QueryGrowthTest.java:52`
- `backend/src/test/java/com/minierp/backend/integration/M4QueryGrowthTest.java:107`
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
- `backend/src/test/java/com/minierp/backend/support/BoardTestSupport.java:26`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:14`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:16`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:18`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:20`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:24`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:26`
- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java:189`

### 확인하지 못한 것·남은 한계

- 실제 서버, 개발 DB/Redis는 호출하지 않았습니다. UTC 전체 테스트는 기존 Testcontainers 구성으로만 실행했습니다.
- 키/값 11개라는 목표는 120자 제한 및 제시된 RecordingOrder 예시와 동시에 충족할 수 없어 12개로 보고했습니다. 그 외 측정 목표를 충족했습니다.
- 닫는 줄 보호 예외 7곳과 긴 리터럴 166개는 의도적으로 남깁니다. 보호 예외를 줄이기 위해 닫는 줄을 바꾸지 않았습니다.
- 별도의 제품 버그를 확정하거나 고치지 않았습니다. 3·4단계를 시작하지 않았고 병합·푸시하지 않았습니다.
- 기준선 3개 파일, 기록 정책·비교 규칙, frontend, backend/md, 마이그레이션, .claude는 변경하지 않았습니다. 기록기·비교기 Java의 수정도 공백만입니다.

이번 검증 원본은 `backend/target/r02-wrap2-validation/`의 `style/`·`docs/`에 보존했습니다. result.json, BASE/후보 컴파일 로그, 두 출력 폴더 diff, 기록 ON/OFF 로그, 기록과 compare.log를 포함합니다. 전체 안전 검사·측정 전후·fixture 결과도 같은 폴더에 보존했습니다. 이전 검증 폴더와 `target/refactoring-v2-validation/record-{1..5}`를 지우지 않았습니다. 원본 로그와 임시 편집 도구는 커밋하지 않았습니다.
