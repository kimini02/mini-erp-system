# R-01 리뷰 보완 — 재현 가능한 기록과 엄격한 SQL 비교

기준 커밋: ba6dca0. 브랜치: refactor/backend-readability. 이 문서는 R-01-safety-net.md의 v1 결과를 보완하는 최신 검증 기록이다. 운영 코드는 수정하지 않는다. 단계 2는 시작하지 않는다.

## 무엇을 왜 바꿨나

v1은 M3 연차 조정과 휴가 상신의 비대칭 경쟁을 놓쳤다. 두 번의 자연 실행이 우연히 같은 승자를 택했다고 해서 다른 실행에서도 같다는 보장이 생기지 않는다. 사용자 리뷰에서는 상신/조정의 승자가 바뀌어 queryCount 349→325 및 계약 차이 21개가 확인됐다. 기록 정책을 ordered-outcome-dependent-http-races-v2로 올리고 해당 경쟁을 포함했다.

SQL 전체 횟수와 집합이 같더라도 SQL별 횟수가 달라지면 이제 실패한다. 원본 순서가 바뀌면 작업 스레드 SQL이 있었던 프레임만 경고로 남긴다. 직렬 프레임은 실패한다. 유일한 이름 기반 순서 예외는 M5ProductionTest#<before-all>이며, Map.of로 구성한 두 계정의 순회 순서 때문이다. 예외도 SQL별 횟수 검사를 우회하지 않는다.

프레임마다 workerSql을 기록한다. SQL 호출 시점의 스레드가 JUnit 테스트 스레드와 다르면 true다. 테스트 풀뿐 아니라 Hikari 연결 추가 스레드도 포함한다. 두 실행 중 어느 쪽에서든 실제 작업 스레드 SQL이 있으면 순서 경고가 가능하다. 바인딩 값과 인증 헤더는 읽거나 저장하지 않는다.

기존 승인대로 정확히 일치하는 INNODB_LOCK_WAITS 진단 폴링문만 비교용 SQL 횟수에서 제외한다. 원본 횟수·순서·SQL 집합은 유지한다. 업무 SQL의 횟수 제외는 없다.

## 동시 응답 테스트 11개 전수 확인

기준선 responses.json에서 concurrent=true인 응답을 가진 메서드를 추출하고, 전체 테스트의 executor/submit/runAsync/supplyAsync/new Thread 호출도 검색했다. 서비스·직접 JDBC 경합은 HTTP 응답 목록에는 없으나 SQL 기록과 횟수 검사는 적용한다.

| 테스트(메서드) | 작업 스레드 응답 수 | 처리 순서 고정 | 이유 |
| --- | ---: | --- | --- |
| M2DocumentIntegrationTest#concurrentApprovalAndWithdrawalHaveOnlyOneWinner | 2 | 필요, 기존 유지 | 승인과 회수는 서로 다른 상태·원장·알림 경로. 승자에 따라 SQL과 200/409의 요청 대응이 달라짐 |
| M2DocumentIntegrationTest#concurrentApprovalHasOne409AndNoNotificationFromLosingRequestInTenTrials | 20 | 불필요 | 각 회차의 같은 문서·결재자·입력인 승인 2회. 성공 1회와 충돌 1회의 응답/SQL 다중집합은 누가 먼저여도 같음 |
| M2DocumentIntegrationTest#concurrentSubmissionsCannotSpendSameRemainingDays | 2 | 필요, 기존 유지 | 서로 다른 문서가 같은 잔액을 소비. 어느 문서가 200/422인지 고정해야 함 |
| M2DocumentIntegrationTest#documentNumbersAreUniqueForEightDifferentDraftersSubmittingConcurrently | 8 | 필요, 기존 유지 | 서로 다른 기안자·문서에 번호가 배정되므로 요청별 docNo 대응이 달라짐 |
| M3AttendanceTest#concurrentClockInHasExactlyOneWinnerInTenTrials | 20 | 불필요 | 각 회차의 같은 사원·날짜·입력인 출근 2회. 고정 시각에서 200 한 건/409 한 건과 SQL 다중집합이 동일 |
| M3LeaveAdjustmentConcurrencyTest#unlockedModelFailsThenLockedAdjustmentsAndSubmissionsPreserveBalance | 22 | 마지막 상신/조정만 필요, 이번 추가 | 앞 10회는 같은 사원·-6일·메모·관리자의 대칭 조정. 마지막은 상신과 조정이 서로 다른 경로로 잔액을 소비하므로 상신 먼저 처리 |
| M4BoardConcurrencyTest#gapsUpdateOneRowSerializeTwentyMovesAndRebalanceOnlyWhenExhausted | 40 | 필요, 기존 유지 | 서로 다른 카드의 위치와 version이 선행 이동에 따라 달라짐 |
| M4BoardConcurrencyTest#projectLockPreventsWriteSkewInTenConcurrentCompletions | 20 | 필요, 기존 유지 | 서로 다른 카드 완료 응답에 포함된 중간 프로젝트 진행률·상태가 달라짐 |
| M4BoardConcurrencyTest#tenConcurrentCreatesHaveContiguousUniqueNumbers | 10 | 불필요 | 같은 프로젝트·담당자·입력으로 등록. 프로젝트 잠금 안에서 번호와 행이 생성되며, 응답 전체는 동일한 연속 번호/행의 집합. 작업 스레드 신원과의 대응은 계약에 없음 |
| M4DeterministicLockTest#secondCompletionWaitsAtProjectLockUntilFirstCommit | 2 | 추가 불필요 | 기존 latch가 A 업무 갱신→B 잠금 시도→A 커밋→B 진행 순서를 강제. 필터에서 다시 묶으면 기존 latch와 교착 가능 |
| M4DeterministicLockTest#secondReorderWaitsAndReadsPositionsAfterFirstCommit | 2 | 추가 불필요 | 위와 같은 결정적 잠금 순서를 이미 검증하며 이동 결과도 그 순서로 고정 |

대칭이라는 판단은 URL만 같은 것으로 내리지 않았다. 위 테스트의 사용자·대상·요청 본문·고정 시각이 같은지 확인했다. 병렬 응답은 경로·상태별 응답 다중집합으로 비교하고, 본문 배열은 정렬하지 않는다. 서로 다른 경로/동작의 경쟁 6개 메서드만 RecordingOrder 대상으로 둔다. M3의 같은 조정 요청은 묶인 요청 경로가 모두 같으면 함께 풀어 자연 경쟁을 유지한다. 직접 JDBC로 구현한 수정 전 재현 실험도 건드리지 않는다.

HTTP 목록 밖의 작업 스레드도 확인했다. DepartmentHeadConcurrencyTest의 서로 다른 사원 변경/부서장 지정은 latch로 두 실행 순서를 이미 강제한다. M1의 아이디 발급은 같은 등록 작업들의 SQL만 기록하고, 초대 링크 설정은 재발송으로 유효한 토큰이 이미 하나로 결정돼 있다. InvitationLifecycleTest의 토큰 교체는 테스트용 토큰 저장소를 직접 호출하며 HTTP 응답이 없다. 연차 부여·마감 알림은 같은 배치 두 번의 대칭 경쟁이다. 나머지 M2/M3/M4 직접 JDBC 실험은 수정 전 구조를 barrier로 재현하는 코드라 그대로 두며 SQL별 횟수를 검증한다. 전체 src/test/java의 Executor·CompletableFuture·new Thread·parallel 호출 검색에서도 추가 비대칭 HTTP 경쟁은 찾지 못했다.

기록 옵션이 없으면 필터를 설치하지 않는다. 기록 모드의 순차 처리로는 실제 경쟁 안전성을 보장할 수 없으므로 기본 전체 UTC 실행을 반드시 별도로 검증한다.

## 비교기 판별력

AuditClockMariaDbTest#<before-all>의 실제 기준선 프레임에서 두 사본을 만들어 기존 비교기와 새 비교기에 같은 입력을 주었다. 전체 쿼리 수는 308로 유지했다.

| 변형 | 기존 비교기 | 새 비교기 |
| --- | --- | --- |
| select database() 1회를 select 1로 이동 | exit 0, differences=0, 경고 1 | exit 1, SQL별 횟수 차이 2 |
| 작업 스레드가 없는 프레임의 인접한 다른 SQL 두 개 순서만 교환 | exit 0, differences=0, 경고 1 | exit 1, sql.order 차이 1 |

기존 a/b/c는 실제 운영 코드를 임시 변경해 얻었던 기록을 최신 비교기에 다시 입력했다. 운영 코드를 이번에 다시 변경한 것으로 보고하지 않는다. 숫자 10→10.0은 차이 8건, null 키 삭제는 6건, SQL 별칭 추가는 4건으로 모두 exit 1이었다. self-check는 기존 8개에 SQL 다중집합, 직렬 순서, 작업 스레드 순서 허용, 작업 스레드라도 다중집합 차이는 실패, 명시적 예외의 순서 허용을 추가해 13개이며 모두 통과했다.

## 바이트 안정성

SafetyNetRun.write에서 직렬화하는 모든 Map.of를 순서가 명시된 LinkedHashMap으로 바꿨다. 테스트 이름과 SQL 집합은 기존 TreeMap/TreeSet 정렬을 유지한다. 같은 입력을 실제 SafetyNetRun에 주는 도구를 새 JVM 5개에서 실행하여 sql.json·responses.json·masked-fields.txt 바이트의 SHA-256이 모두 같은지 검증한다.

옛 기록기를 임시 폴더에만 컴파일해 같은 실험을 했을 때는 5개 JVM에서 sql.json 해시 4종, responses.json 해시 2종으로 달랐다(exit 1). 수정 후에는 세 파일 모두 5/5 동일했다(exit 0).

전체 실제 기록의 SQL 스레드 간 순서와 병렬 응답 도착 순서는 여전히 달라질 수 있고 원본 그대로 남긴다. 서로 다른 원본 실행 순서를 같은 파일로 바꾼다는 의미는 아니다. 동일 내용의 맵 키 순서가 JVM 난수화 때문에 달라지던 문제만 해결한다.

## 안정성 5회 및 기본 실행

검증 완료. 각 기록은 record-1을 기준으로 실제 비교하며, 다른 기준선을 번갈아 사용하지 않는다. 최종 기준선은 record-1을 복사한다. 시간은 Python monotonic으로 측정한 Maven 프로세스 경과 시간이다.

| 실행 | 전체/실패/오류/건너뜀 | 경과 초 | 비교 차이 | 순서 경고 | 기록 출력 |
| --- | --- | ---: | ---: | ---: | ---: |
| record-1 | 231 / 0 / 0 / 0 | 762.19 | 0 | 0 | 1 |
| record-2 | 231 / 0 / 0 / 0 | 482.02 | 0 | 11 | 1 |
| record-3 | 231 / 0 / 0 / 0 | 595.16 | 0 | 12 | 1 |
| record-4 | 231 / 0 / 0 / 0 | 447.58 | 0 | 9 | 1 |
| record-5 | 231 / 0 / 0 / 0 | 452.07 | 0 | 12 | 1 |
| off | 231 / 0 / 0 / 0 | 653.00 | — | — | 0 |

5회 모두 첫 기록을 복사한 새 기준선과 비교해 differences=0이다. record-1 자체 비교를 포함하며, 나머지 4회는 독립 전체 실행이다.

| 실행 | M3 SQL 횟수 | 마지막 상신/조정 상태 |
| --- | ---: | --- |
| record-1 | 349 | 200 / 422 |
| record-2 | 349 | 200 / 422 |
| record-3 | 349 | 200 / 422 |
| record-4 | 349 | 200 / 422 |
| record-5 | 349 | 200 / 422 |

| 실행 | 잠금 진단 원본 횟수: 완료 이동 / 재정렬 |
| --- | --- |
| record-1 | 1 / 1 |
| record-2 | 1 / 1 |
| record-3 | 1 / 1 |
| record-4 | 1 / 1 |
| record-5 | 1 / 1 |


## 순서 경고 프레임의 합집합

| 프레임 | 경고가 나온 실행 | 이유 |
| --- | --- | --- |
| M1AcceptanceTest#concurrentIdAllocationIsUniqueAndUnsafeMaxPlusOneCollides() | 2, 3, 4, 5 | 병렬 사원 등록의 부서·사용자 조회 진입 순서 |
| M2DocumentIntegrationTest#concurrentApprovalAndWithdrawalHaveOnlyOneWinner() | 2, 3, 5 | HTTP 순서는 고정, Hikari 연결 초기화 SELECT @@session.tx_isolation의 실행 위치 |
| M2DocumentIntegrationTest#concurrentApprovalHasOne409AndNoNotificationFromLosingRequestInTenTrials() | 2, 3, 4, 5 | 같은 문서 승인 2회의 인증·권한·잠금 조회가 교차함 |
| M2DocumentIntegrationTest#documentNumbersAreUniqueForEightDifferentDraftersSubmittingConcurrently() | 2, 3, 4, 5 | HTTP 순서는 고정, Hikari 연결 초기화 SELECT @@session.tx_isolation의 실행 위치 |
| M2LegacyApprovalRaceTest#unconditionalLegacyApprovalAcceptsBothRequestsInTenControlledRaces() | 2, 3, 4, 5 | 수정 전 직접 JDBC 재현의 두 승인 트랜잭션이 문서 상태·잔액을 읽는 순서 |
| M3AttendanceTest#concurrentClockInHasExactlyOneWinnerInTenTrials() | 2, 3, 4, 5 | 대칭 출근 경합의 사용자·근태 조회가 교차함 |
| M3LeaveAdjustmentConcurrencyTest#unlockedModelFailsThenLockedAdjustmentsAndSubmissionsPreserveBalance() | 2, 3, 4, 5 | 직접 JDBC 재현 및 앞 10회 대칭 조정의 SQL이 교차함. 마지막 비대칭 HTTP는 고정 |
| M3LeaveGrantTest#twoConcurrentBatchRunsIssueEachKeyOnlyOnceInTenTrials() | 2, 3, 4, 5 | 두 부여 배치의 사용자/기부여 키 조회 순서 |
| M4BoardConcurrencyTest#gapsUpdateOneRowSerializeTwentyMovesAndRebalanceOnlyWhenExhausted() | 3, 5 | HTTP 이동 순서는 고정, Hikari 연결 초기화 SELECT @@session.tx_isolation의 실행 위치 |
| M4BoardConcurrencyTest#tenConcurrentCreatesHaveContiguousUniqueNumbers() | 2, 3, 4, 5 | 같은 업무 등록 10개의 인증·권한 조회가 교차함 |
| M4LegacyExperimentTest#fullRenumberUpdatesOneHundredRowsAndUnlockedMovesDuplicateTwentyTimes() | 3 | 수정 전 직접 JDBC 카드 이동의 두 트랜잭션에서 MIN(position) 조회가 교차함 |
| M4OverdueTest#concurrentRunsSendOnlyOnce() | 2, 3, 4, 5 | 마감 알림 두 배치의 대상 프로젝트 조회·잠금 순서 |
| M5ProductionTest#<before-all> | 2, 5 | Map.of 계정 순회의 CREATE USER 순서. workerSql=false이므로 유일한 명시적 허용 목록 적용 |

합집합은 13개 프레임이다. 모든 경고는 SQL별 횟수가 같으며, 기준선·후보 중 실제 workerSql=true 또는 위 단일 허용 목록으로만 통과한다.


## 재현 명령

backend 폴더에서 JAVA_HOME을 Java 17로 지정한다. record-1부터 record-5까지 출력 경로만 바꿔 실행한다.

```bash
./mvnw -Duser.timezone=UTC -Drefactoring.record=target/refactoring-candidate test
REFACTORING_CLASSPATH=target/refactoring-candidate/classpath.txt \
  bash src/test/resources/refactoring-tools/compare.sh \
  src/test/resources/refactoring-baseline target/refactoring-candidate
python3 src/test/resources/refactoring-tools/self-check.py target/refactoring-candidate/classpath.txt
python3 src/test/resources/refactoring-tools/serialization-check.py target/refactoring-candidate/classpath.txt
./mvnw -Duser.timezone=UTC test
```

## 버린 방법과 이유

- 경쟁 승자에 따라 달라진 업무 SQL·상태 코드를 가림: 실제 계약 변화를 숨기므로 사용하지 않음.
- 모든 경합 요청을 순차 처리: 대칭 경합과 기존 결정적 잠금 실험까지 바꿀 필요가 없어 6개 메서드의 비대칭 HTTP 경쟁으로 한정.
- 작업 스레드가 있다는 이유로 횟수 차이까지 허용: SQL 추가/누락을 놓치므로 다중집합은 항상 실패 조건.
- 직렬 순서 차이를 일괄 경고: 새 지시에 따라 실패하고, 이름·이유가 명시된 M5 계정 순회만 예외.
- 출력 때 원본 SQL/응답 배열을 정렬: 실제 관찰 순서를 잃으므로 맵 키 순서만 고정.

## 변경 파일

- 테스트 지원: `RecordingOrder.java`, `SafetyNetRun.java`, `SnapshotComparator.java` — 비대칭 경쟁 v2, 작업 스레드 여부·고정 맵 순서, SQL 다중집합·직렬 순서 검사.
- 도구: `refactoring-tools/self-check.py`, `refactoring-tools/serialization-check.py` — 비교기 13개 판별 사례, 새 JVM 5개의 실제 기록기 바이트 검사.
- 기준선: `refactoring-baseline/README.md`, `sql.json`, `responses.json`, `manifest.json` — v2 기록과 비교 정책·검증 결과·해시. 가린 필드 규칙과 `masked-fields.txt`는 변경하지 않음.
- 문서: `R-01-safety-net.md`에 최신 보고서 안내를 추가하고, 이 `R-01-review-hardening.md`에 분석과 결과를 기록.

새 기준선은 SQL 801종·411개 프레임·HTTP 응답 921개이며, 작업 스레드 SQL이 있는 프레임은 26개다. 동시 HTTP 응답을 가진 테스트는 11개다. 원본 SQL 실행 273,939회를 압축 기록하며 바인딩 값은 포함하지 않는다.

## 남은 한계와 변경 범위

5회는 우연히 같은 결과가 나올 가능성을 없애는 수학적 보장이 아니다. 11개 경쟁의 구조를 먼저 분류하고 비대칭 경쟁을 고정한 뒤 5회 관찰로 확인한다. workerSql은 프레임 단위이므로 한 프레임 안의 직렬 구간 순서 변화까지 개별적으로 구분하지 않는다. 바인딩 값은 비교하지 않으며 MockMvc 밖 HTTP 응답은 기록 범위 밖이다. 운영 코드·금지 경로·새 의존성·기본 테스트 본문은 변경하지 않는다. 병합·푸시는 하지 않는다.

## 2차 보완

### 커밋과 변경 범위

리뷰 기준은 `c176f10`, 보완 범위는 `c176f10..HEAD`이다. 이 절과 아래 5개 파일을 한 보완 커밋에 포함하며, 최종 해시는 완료 보고에 기재한다. 단계 2는 시작하지 않았다.

- `backend/src/test/java/com/minierp/backend/support/refactoring/SnapshotComparator.java`: 엄격 목록 7개, workerSql 동일성 검사, 기준선 표시만으로 경고 판정, M5 두 계정 생성 교환만 허용.
- `backend/src/test/resources/refactoring-tools/self-check.py`: 기존 13개 사례와 추가 4개 사례, 17개 검사.
- `backend/src/test/resources/refactoring-baseline/README.md`, `manifest.json`: 새 비교 규칙과 재비교 결과. 기준선 원본의 파일 해시는 그대로 유지.
- `docs/refactoring/R-01-review-hardening.md`: 이 2차 보완 절. 앞 절은 c176f10 당시 결과로 보존하며, 그곳의 workerSql OR 판단·프레임 전체 M5 예외 설명은 아래 규칙으로 대체된다.

`src/main`, 기록기(SafetyNetRun·RecordingOrder·JdbcProbe 등), 기록 정책 v2, 기준선 `sql.json`·`responses.json`·`masked-fields.txt`, 테스트 본문은 변경하지 않았다. 새 라이브러리·빌드 플러그인도 없다. 실제 서버와 개발 DB/Redis를 호출하지 않았다.

### workerSql 26개 프레임 분류

분류 기준은 **기록 모드의 SQL 실행 구조**다. 트랜잭션이나 Java 메서드가 동시에 살아 있다는 사실과 SQL 호출이 겹친다는 사실을 구분한다. “한 번에 하나” 및 “SQL 한 흐름” 7개만 엄격 목록에 넣었다. 모두 기존 5회 기록에서 순서 경고가 0회였다. 나머지 19개는 실제 경합 또는 Hikari 연결 스레드의 중첩이 남아 있다. 무경고만으로 엄격 목록에 넣지 않는다.

| 프레임 | SQL 실행 구조 | 경고 실행(기존 5회) | 엄격 목록 | 근거 |
| --- | --- | --- | --- | --- |
| DepartmentHeadConcurrencyTest#employeeChangeAndHeadAssignmentPreserveInvariant()[test-template-invocation:#1] | 중첩 | 없음 | 제외 | 사용자 변경을 UPDATE 직전에 멈춘 동안 부서장 지정이 트리 잠금을 시도한다. 두 트랜잭션이 겹치며, 잠금 획득 뒤의 직렬화만으로 전체 프레임을 직렬이라 단정하지 않는다. 매개변수: MOVE. |
| DepartmentHeadConcurrencyTest#employeeChangeAndHeadAssignmentPreserveInvariant()[test-template-invocation:#2] | 중첩 | 없음 | 제외 | 사용자 변경을 UPDATE 직전에 멈춘 동안 부서장 지정이 트리 잠금을 시도한다. 두 트랜잭션이 겹치며, 잠금 획득 뒤의 직렬화만으로 전체 프레임을 직렬이라 단정하지 않는다. 매개변수: RETIRED. |
| DepartmentHeadConcurrencyTest#employeeChangeAndHeadAssignmentPreserveInvariant()[test-template-invocation:#3] | 중첩 | 없음 | 제외 | 사용자 변경을 UPDATE 직전에 멈춘 동안 부서장 지정이 트리 잠금을 시도한다. 두 트랜잭션이 겹치며, 잠금 획득 뒤의 직렬화만으로 전체 프레임을 직렬이라 단정하지 않는다. 매개변수: ON_LEAVE. |
| DepartmentHeadConcurrencyTest#headCheckUsesCurrentReadEvenWithAnOlderTransactionSnapshot()[test-template-invocation:#1] | 한 번에 하나 | 없음 | 포함 | worker의 사전 SELECT → snapshot 신호 → main의 부서장 지정 완료 → assigned 신호 → worker 변경. 트랜잭션 수명은 겹쳐도 latch 사이의 SQL 실행 단계는 겹치지 않는다. 매개변수: MOVE. |
| DepartmentHeadConcurrencyTest#headCheckUsesCurrentReadEvenWithAnOlderTransactionSnapshot()[test-template-invocation:#2] | 한 번에 하나 | 없음 | 포함 | worker의 사전 SELECT → snapshot 신호 → main의 부서장 지정 완료 → assigned 신호 → worker 변경. 트랜잭션 수명은 겹쳐도 latch 사이의 SQL 실행 단계는 겹치지 않는다. 매개변수: RETIRED. |
| DepartmentHeadConcurrencyTest#headCheckUsesCurrentReadEvenWithAnOlderTransactionSnapshot()[test-template-invocation:#3] | 한 번에 하나 | 없음 | 포함 | worker의 사전 SELECT → snapshot 신호 → main의 부서장 지정 완료 → assigned 신호 → worker 변경. 트랜잭션 수명은 겹쳐도 latch 사이의 SQL 실행 단계는 겹치지 않는다. 매개변수: ON_LEAVE. |
| M1AcceptanceTest#concurrentIdAllocationIsUniqueAndUnsafeMaxPlusOneCollides() | 중첩 | 2, 3, 4, 5 | 제외 | MAX+1 재현 barrier 및 사원 동시 등록. 번호 잠금 앞뒤의 조회가 교차한다. |
| M1AcceptanceTest#concurrentInvitationLinksCannotOverwriteFirstPassword() | SQL 한 흐름 | 없음 | 포함 | 두 setup 호출은 겹치지만 재발송으로 옛 토큰이 먼저 제거된다. 옛 토큰은 테스트용 메모리 owner 조회에서 탈락하고 유효 토큰만 findByIdForUpdate·UPDATE에 도달한다. |
| M2DocumentIntegrationTest#concurrentApprovalAndWithdrawalHaveOnlyOneWinner() | 중첩(연결 스레드) | 2, 3, 5 | 제외 | 기록 모드 HTTP는 순차이나 Hikari의 SELECT @@session.tx_isolation 위치가 달라졌다. 프레임 전체를 엄격 목록에 넣지 않는다. |
| M2DocumentIntegrationTest#concurrentApprovalHasOne409AndNoNotificationFromLosingRequestInTenTrials() | 중첩 | 2, 3, 4, 5 | 제외 | 같은 승인 요청 두 개의 인증·권한·잠금 조회가 동시에 진입한다. |
| M2DocumentIntegrationTest#concurrentSubmissionsCannotSpendSameRemainingDays() | 기록 모드 한 번에 하나 | 없음 | 포함 | RecordingOrder v2가 첫 상신의 필터 체인 완료 후 두 번째를 푼다. 기본 모드의 경쟁은 유지된다. |
| M2DocumentIntegrationTest#documentNumbersAreUniqueForEightDifferentDraftersSubmittingConcurrently() | 중첩(연결 스레드) | 2, 3, 4, 5 | 제외 | 기록 모드 상신은 순차이나 Hikari 연결 초기화 SQL 위치가 달라졌다. |
| M2LegacyApprovalRaceTest#unconditionalLegacyApprovalAcceptsBothRequestsInTenControlledRaces() | 중첩 | 2, 3, 4, 5 | 제외 | 수정 전 승인 두 트랜잭션이 barrier 전후로 상태·잔액을 읽고 갱신한다. |
| M3AttendanceTest#concurrentClockInHasExactlyOneWinnerInTenTrials() | 중첩 | 2, 3, 4, 5 | 제외 | 같은 출근 요청 두 개를 barrier에서 함께 출발시킨다. |
| M3LeaveAdjustmentConcurrencyTest#unlockedModelFailsThenLockedAdjustmentsAndSubmissionsPreserveBalance() | 중첩 | 2, 3, 4, 5 | 제외 | 마지막 상신/조정은 기록 모드에서 순차지만, 앞의 직접 JDBC 재현 및 10회 대칭 조정은 중첩된다. |
| M3LeaveGrantTest#twoConcurrentBatchRunsIssueEachKeyOnlyOnceInTenTrials() | 중첩 | 2, 3, 4, 5 | 제외 | 두 배치가 barrier에서 함께 시작하여 대상·기부여 키를 조회한다. |
| M3LegacyAttendanceTest#legacyConcurrentReadThenInsertLeaksUniqueConstraintInTenTrials() | 중첩 | 없음 | 제외 | 두 SELECT 뒤 barrier를 만나고 INSERT가 경쟁한다. 같은 SQL끼리 묶여 5회 순서가 같았을 뿐 직렬은 아니다. |
| M4BoardConcurrencyTest#gapsUpdateOneRowSerializeTwentyMovesAndRebalanceOnlyWhenExhausted() | 중첩(연결 스레드) | 3, 5 | 제외 | 기록 모드 이동은 순차이나 Hikari 연결 초기화 SQL 위치가 달라졌다. |
| M4BoardConcurrencyTest#projectLockPreventsWriteSkewInTenConcurrentCompletions() | 기록 모드 한 번에 하나 | 없음 | 포함 | 각 회차의 사전 assignment INSERT는 barrier 해제 전에 끝나며, RecordingOrder v2가 이동 필터 체인을 하나씩 완료시킨다. |
| M4BoardConcurrencyTest#tenConcurrentCreatesHaveContiguousUniqueNumbers() | 중첩 | 2, 3, 4, 5 | 제외 | 동일 등록 10개가 동시 진입한다. 프로젝트 잠금 앞의 인증·권한 조회는 교차한다. |
| M4DeterministicLockTest#secondCompletionWaitsAtProjectLockUntilFirstCommit() | 중첩 | 없음 | 제외 | A가 커밋 전 멈춘 동안 B가 인증·프로젝트 잠금에 진입하고 main이 잠금 대기를 폴링한다. latch는 위험 구간을 만들며 전체 SQL을 직렬화하지 않는다. |
| M4DeterministicLockTest#secondReorderWaitsAndReadsPositionsAfterFirstCommit() | 중첩 | 없음 | 제외 | A의 미커밋 이동, B의 잠금 대기, main의 진단 폴링이 공존한다. 무경고여도 엄격 목록에 넣지 않는다. |
| M4LegacyExperimentTest#fullRenumberUpdatesOneHundredRowsAndUnlockedMovesDuplicateTwentyTimes() | 중첩 | 3 | 제외 | 잠금 없는 두 이동이 position과 MIN(position)을 읽고 barrier 뒤 갱신한다. |
| M4LegacyExperimentTest#unlockedWholeListRecalculationReproducesWriteSkewTenTimes() | 중첩 | 없음 | 제외 | 두 UPDATE → barrier → 두 목록 SELECT → barrier → 두 상태 UPDATE. 같은 SQL끼리 단계가 묶인 것이지 단일 실행은 아니다. |
| M4OverdueTest#concurrentRunsSendOnlyOnce() | 중첩 | 2, 3, 4, 5 | 제외 | 마감 알림 배치 두 개가 동시에 프로젝트 대상 조회·잠금에 진입한다. |
| M5ProductionTest#separatedAccountsMigrateAndPerformRepresentativeWritesButRejectDdl() | 한 번에 하나 | 없음 | 포함 | 테스트 스레드가 blocking HttpClient.send로 응답을 받은 후 다음 요청·직접 JDBC·배치를 실행한다. Tomcat worker라는 이유만으로 순서를 허용하지 않는다. |

엄격 목록의 7개 이름과 이유는 비교기 `STRICT_ORDER`에 명시했다. 특정 부서장 테스트는 매개변수 3개를 각각 등록한다. 목록에 있으면 `workerSql=true`라도 순서 변경은 `sql.order` 차이다. 기본 실행의 자연 경합을 바꾸거나 기록 필터를 추가하지 않았다.

범위가 애매한 프레임은 제외했다. `employeeChangeAndHeadAssignment...`는 잠금 이후에는 순차여도 트리 잠금 시도가 겹친다. `M4DeterministicLockTest`는 latch로 커밋 순서를 강제하지만 B의 인증·잠금과 main 진단 조회가 겹친다. `M3LegacyAttendanceTest`, `M4LegacyExperimentTest#unlockedWholeList...`는 두 스레드가 같은 SQL을 같은 단계에서 실행하므로 원본 모양이 같아질 뿐이다. 연결 스레드의 초기화 SQL이 5회 중 실제로 움직인 세 프레임도 업무 HTTP가 순차라는 이유만으로 엄격 목록에 넣지 않았다.

### 비교 규칙

1. 매 프레임의 `workerSql`이 다르면 `key=workerSql`로 실패한다. SQL 순서가 같아도 실패하며, 후보 표시가 true로 바뀌어 경고 허용 범위를 넓힐 수 없다.
2. SQL별 횟수는 기존처럼 항상 실패 조건이다. 횟수가 같고 순서만 다를 때 엄격 목록을 먼저 적용한다. 그 외 작업 스레드 순서 경고는 **기준선의** workerSql만 사용한다.
3. M5 `<before-all>`은 프레임 전체를 허용하지 않는다. 정확한 두 SQL(`create user 'workflow_app'@'%' identified by ?`, `create user 'workflow_migrator'@'%' identified by ?`)이 각각 한 번 실행된 두 항목만 서로 교환되고, 나머지 항목·위치·횟수가 모두 같아야 경고 1건이다. 다른 두 항목 교환이나 이 교환에 다른 변경이 섞이면 실패한다. 비밀번호는 바인딩 자리표시자 `?`이며 값은 읽지 않는다.

버린 방법: 경고가 없던 모든 worker 프레임을 엄격 처리하면 실제 경합을 오탐하므로 구조와 5회 기록을 함께 사용했다. worker 표시를 후보와 OR하면 후보가 검사 수준을 낮출 수 있어 버렸다. M5 계정 생성문을 제거·정렬하는 방식은 이동 범위를 넓힐 수 있어, 두 항목을 교환했을 때 나머지가 원본과 같은지를 검사했다.

### 실제 기준선 사본 재현: 수정 전 → 수정 후

운영 코드는 임시로도 수정하지 않았다. 기준선은 읽기만 하고 임시 폴더의 사본만 변형했다. 수정 전 비교기는 `git show c176f10:.../SnapshotComparator.java`를 임시 폴더에 컴파일하여 사용했고, 수정 후에는 test-compile 결과를 사용했다. 아래 인덱스는 압축된 sql 배열의 0부터 시작하는 인덱스다.

| 사례 | 사본 변경 | 이전 exit / 차이 / 경고 | 이후 exit / 차이 / 경고 | 이후 차이 키 |
| --- | --- | --- | --- | --- |
| strict-worker | M5 순차 HTTP 프레임의 19·20번 업무 SQL 교환 | 0 / 0 / 1 | 1 / 1 / 0 | sql.order |
| worker-flag | AuditClockMariaDbTest#<before-all>의 workerSql false→true 및 0·1번 SQL 교환 | 0 / 0 / 1 | 1 / 2 / 0 | workerSql, sql.order |
| m5-tail | M5#<before-all>의 마지막 두 항목(메타데이터 조회·앱 조회) 교환 | 0 / 0 / 1 | 1 / 1 / 0 | sql.order |
| m5-create-only | M5#<before-all>의 1·2번 CREATE USER만 교환(정상 예외) | 0 / 0 / 1 | 0 / 0 / 1 | 없음 |

### 검증 결과

- Java 17 + UTC `./mvnw -Duser.timezone=UTC test-compile`: BUILD SUCCESS, Maven 4.994초.
- self-check: **17/17 통과**. 추가 (a) 엄격 worker 순서 변경 exit 1, (b) 후보만 worker 변경 및 순서 변경 exit 1(`workerSql`·`sql.order`), (c) M5 계정 생성문만 교환 exit 0/경고 1, (d) M5 다른 두 SQL 교환 exit 1.
- 기존 13개 검사 이름·성공/실패 기대값은 유지했다. 13번째 `allowlisted-sql-order`의 긍정 fixture는 새 예외 범위에 맞게 일반 SELECT 교환에서 두 CREATE USER 교환으로 좁혔다. 임의의 두 SQL 교환을 계속 허용하는 검사는 남기지 않았다.
- serialization-check: **새 JVM 5개 모두 세 출력 파일 바이트 동일**, exit 0. 기록기는 변경하지 않았다.
- c176f10 전체 실행 기록 5개가 모두 남아 있어 새 비교기로 재사용했다. 전체 테스트를 새로 실행한 결과로 보고하지 않는다. 411개 프레임의 workerSql 표시도 각 기록에서 직접 대조했다.

| 기존 기록 | 새 비교 exit | differences | 순서 경고 | workerSql 동일 | 비교 경과 초 |
| --- | ---: | ---: | ---: | --- | ---: |
| record-1 | 0 | 0 | 0 | 411/411 (변화 0) | 1.510 |
| record-2 | 0 | 0 | 11 | 411/411 (변화 0) | 1.350 |
| record-3 | 0 | 0 | 12 | 411/411 (변화 0) | 1.373 |
| record-4 | 0 | 0 | 9 | 411/411 (변화 0) | 1.263 |
| record-5 | 0 | 0 | 12 | 411/411 (변화 0) | 1.284 |

순서 경고 합집합은 여전히 앞 절의 13개이며, 새 경고 프레임은 없다. 엄격 목록 7개는 5개 기록에서 모두 원본 순서가 동일하다. 시간은 병렬로 수행한 비교 도구의 경과 시간이므로 성능 개선 수치로 사용하지 않는다.

기준선 원본 SHA-256은 c176f10과 다음 값으로 일치한다.

| 파일 | SHA-256 |
| --- | --- |
| sql.json | `8ca96939bfbd887a037156bc523f7e60fdd9961910ec950a25c986e73fca4930` |
| responses.json | `2e2acc5d706ea90531d44a7f957938f5e9a67a0d3e049d8fde0fc8b41dddcebc` |
| masked-fields.txt | `7c4c8e12a7287aafc91ce4073f0ab047be4086f390cd26bc9901669acee5156e` |

재검증 명령은 backend에서 Java 17 JAVA_HOME을 지정한 뒤 실행한다. record-1을 record-2~5로 바꾸어 반복한다.

```bash
./mvnw -Duser.timezone=UTC test-compile
REFACTORING_CLASSPATH=target/refactoring-v2-validation/record-1/classpath.txt \
  bash src/test/resources/refactoring-tools/compare.sh \
  src/test/resources/refactoring-baseline target/refactoring-v2-validation/record-1
python3 src/test/resources/refactoring-tools/self-check.py target/refactoring-v2-validation/record-1/classpath.txt
python3 src/test/resources/refactoring-tools/serialization-check.py target/refactoring-v2-validation/record-1/classpath.txt
```

### 확인하지 않은 것과 남은 한계

요청한 검증에서 수행하지 못한 항목은 없다. 기존 기록 5개가 있고 운영 코드·기록기가 바뀌지 않아 이번에는 전체 UTC 테스트 및 기록 OFF 실행을 재실행하지 않았다. 재사용한 전체 테스트 결과는 앞 절의 231/231이다. 이번 작업에서 DB/Redis 또는 실제 서버를 띄우거나 호출하지 않았다.

workerSql 자체는 여전히 “다른 스레드에서 실행됨”을 뜻한다. 엄격 목록 밖에서 순서 차이를 경고로 두는 범위가 남으며, 전체 프레임 안의 각 SQL 구간에 대한 동시성 증명까지 제공하지 않는다. 새 테스트나 스레드 구조가 추가되면 목록과 근거를 다시 검토해야 한다. 후보 workerSql이 변하면 이제 자동으로 실패하므로 조용히 경고 범위를 넓힐 수 없다. 병합·푸시는 하지 않았다.
