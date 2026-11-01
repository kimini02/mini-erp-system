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
