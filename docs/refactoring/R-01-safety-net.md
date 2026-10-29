# R-01 안전망

기준 develop: `faf7972`. 별도 작업 트리의 `refactor/backend-readability`에서 작업하며 운영 코드의 영구 변경은 없다. 기준선은 테스트 리소스의 refactoring-baseline에 저장한다. 운영 코드 리팩토링은 수행하지 않았다.

## 무엇을 왜 만들었나

리팩토링에서 SQL 집합·테스트별 호출 수와 응답 계약이 바뀌는 것을 찾는 테스트 전용 기록기와 비교기를 추가했다. 기본 실행은 기록하지 않는다. 기존 JdbcProbe의 범위별 측정과 기대값은 유지한다.

## 기록 방식

`-Drefactoring.record=target/refactoring-run-1`을 전체 UTC 테스트에 추가한다. JUnit 전역 확장으로 테스트 경계를 식별하고, 기존 Mockito의 전이 의존성인 Byte Buddy로 MariaDB JDBC 실행 경계만 관찰한다. 새 라이브러리·플러그인은 추가하지 않았다. 준비된 문장은 원래 SQL 템플릿만 읽고 바인딩 값을 읽지 않는다. SQL은 공백 압축과 소문자 변환만 한다. 연속으로 동일한 SQL은 횟수와 함께 저장해 순서를 보존한다.

Boot MockMvcBuilderCustomizer의 alwaysDo에서 요청 메서드·경로·상태·응답을 수집한다. 개별 테스트는 수정하지 않는다. JSON은 스트리밍 토큰으로 읽어 숫자의 원문 표현을 보존한다. 객체 키만 정렬하며 배열·null 키·날짜·숫자 표현은 보존한다. 오류 응답도 기록한다. 직렬 요청의 발생 순서를 비교하고 병렬 요청은 완료 순서 대신 요청별 응답을 비교한다.

사용자 승인에 따라 `INNODB_LOCK_WAITS`의 정확히 일치하는 진단 폴링 SQL 한 종류만 비교용 호출 수에서 제외한다. 원본 SQL·순서·횟수·서로 다른 SQL 집합에서는 제외하지 않는다. 해당 SQL은 SafetyNetRun.MONITOR에 명시했다. 업무 조회나 쓰기는 제외하지 않는다.

비교 실행: backend에서 `bash src/test/resources/refactoring-tools/compare.sh 기준폴더 후보폴더`. classpath는 기록 디렉터리의 classpath.txt를 사용하며 이 로컬 경로 파일은 기준선에 커밋하지 않는다. 차이는 테스트·요청·키 단위로 표시하고 종료 코드 1을 반환한다. SQL 순서 차이는 별도로 출력한다.

## 가린 필드 목록

- 테스트에서 생성한 전체 UUID 문자열: 매 실행 달라지는 fixture 식별자.
- M1Acceptance의 chief/leader/employee/outside 로그인·이메일에 붙은 UUID 8자리.
- BoardTestSupport의 무작위 10자리 대문자 16진수 프로젝트 키와 M4BoardApi의 X 접두어 8자리 키.
- accessToken: 매 실행 달라지는 인증 자격값이며 기준선에 저장하지 않는다.

적용한 테스트·키 경로·이유는 masked-fields.txt로 남긴다. 자동 증가 숫자 id, 문서번호, 상태 코드, 업무 위치·version, 고정 시계 timestamp는 가리지 않았다.

## 안정성 결과

| 실행 | 성공/실패/건너뜀 | 실측 경과 시간 |
| --- | --- | --- |
| 최초 기록 끔 | 231 / 0 / 0 | 672.62초 |
| 기록 켬 1 | 231 / 0 / 0 | 451.41초 |
| 기록 켬 2 | 231 / 0 / 0 | 432.31초 |
| 훅 추가 후 기본 실행 | 231 / 0 / 0 | 458.60초 |
| 순서 고정 기록 켬 1 | 231 / 0 / 0 | Maven 7분 25초 |
| 순서 고정 기록 켬 2 | 231 / 0 / 0 | 417.59초 (Maven 6분 56초) |
| 최종 코드 기록 끔 | 231 / 0 / 0 | 415.21초 (Maven 6분 53초) |

모두 Java 17, UTC JVM, Testcontainers 실행이다. 초 단위 값은 /usr/bin/time의 real이며 첫 순서 고정 실행만 Maven 총 시간을 사용했다. 최종 기본 실행에는 REFACTORING_RECORDED 출력이 0건이고 실제 경합 테스트를 변경 없이 통과했다. 실행별 컨테이너 준비와 호스트 상태가 달라 시간 차이를 최적화 효과로 해석하지 않는다. 자연 경합을 유지한 두 기록은 각각 SQL 종류 801개, HTTP 응답 921개였다. 첫 기록 SQL 실행 수는 273,939회다.

두 기록의 비교는 차이 109개로 실패했다. 승인과 회수의 승자가 바뀌면 SQL 호출 수 89/84 및 각 요청의 200/409가 바뀐다. 병렬 문서번호 발급과 카드 이동의 위치·version, 중간 프로젝트 상태도 순서에 따라 달라진다. 이는 진단 조회 제외로 해결되지 않는다. 이 값을 넓게 가리지 않았다. 사용자가 기록 모드에서만 처리 순서를 고정하도록 승인했다. RecordingOrder는 결과가 순서에 의존하는 M2 경합 3개와 M4 경합 2개에서 작업 스레드 요청이 모두 도착한 뒤 경로순으로 한 요청씩 완료시킨다. 테스트 본문의 직렬 준비 요청은 건드리지 않는다. 기본 실행에는 필터를 설치하지 않는다. 자연 경합 기록에서는 SQL 순서만의 변화 8건도 따로 출력됐다.

승인한 정책으로 전체 실행을 두 번 다시 수행한 최종 결과는 `REFACTORING_COMPARE differences=0 sqlOrderChanges=9`, 종료 코드 0이다. 각 기록은 테스트·수명주기 프레임 411개, SQL 801종, 원본 실행 273,939회, 응답 921건이다. 테스트 식별자는 표시 이름 대신 메서드 이름과 매개변수화 호출 번호를 사용해 같은 표시 이름을 가진 테스트의 병합을 막았다. 진단 SQL은 두 실행 모두 아래 각 테스트에서 원본 1회씩이며 비교 횟수에서만 제외했다.

- M4DeterministicLockTest#secondCompletionWaitsAtProjectLockUntilFirstCommit(): 1 → 1
- M4DeterministicLockTest#secondReorderWaitsAndReadsPositionsAfterFirstCommit(): 1 → 1

응답 상태별 건수: 200=606, 201=124, 400=20, 401=11, 403=49, 404=45, 409=35, 422=28, 500=3. JSON 918건과 빈 본문 1건, CSV 2건을 기록했다. 가림 감사 목록은 테스트·경로·이유의 서로 다른 조합 252개이며 실제 accessToken 문자열 150건은 모두 가렸다.

SQL 순서 차이는 명세 1.1에 따라 원본을 유지하고 이유를 보고한다. 9개 프레임 모두 SQL별 실행 횟수도 동일했다.

| 프레임 | 순서 차이의 이유 | 원본 실행 수(전→후) |
| --- | --- | --- |
| M1AcceptanceTest#concurrentIdAllocationIsUniqueAndUnsafeMaxPlusOneCollides | 병렬 사원 등록 스레드의 조회 순서 | 143→143 |
| M2DocumentIntegrationTest#concurrentApprovalHasOne409AndNoNotificationFromLosingRequestInTenTrials | 동일 승인 요청끼리의 문서 잠금 진입 순서 | 811→811 |
| M2DocumentIntegrationTest#documentNumbersAreUniqueForEightDifferentDraftersSubmittingConcurrently | Hikari 연결 초기화의 SELECT @@session.tx_isolation 위치. 지정 HTTP 처리는 순서 고정됨 | 369→369 |
| M3AttendanceTest#concurrentClockInHasExactlyOneWinnerInTenTrials | 병렬 출근 요청의 조회·잠금 순서 | 230→230 |
| M3LeaveAdjustmentConcurrencyTest#unlockedModelFailsThenLockedAdjustmentsAndSubmissionsPreserveBalance | 직접 실행한 경합 실험과 조회의 스레드 간 순서 | 349→349 |
| M3LeaveGrantTest#twoConcurrentBatchRunsIssueEachKeyOnlyOnceInTenTrials | 두 부여 배치의 조회 순서 | 461→461 |
| M4BoardConcurrencyTest#tenConcurrentCreatesHaveContiguousUniqueNumbers | 병렬 업무 등록의 인증·권한 조회 순서 | 194→194 |
| M4OverdueTest#concurrentRunsSendOnlyOnce | 병렬 마감 알림 배치의 프로젝트 조회·잠금 순서 | 44→44 |
| M5ProductionTest#<before-all> | Map.of로 만든 계정 두 개의 순회 순서가 JVM마다 달라 CREATE USER 두 문장의 순서만 바뀜 | 312→312 |

## 판별력 결과

실제 운영 코드를 한 번에 한 곳만 임시 변경하고 M4HomeTest 5개를 실행했다. 매번 기존 테스트 5개는 통과했지만 비교기는 다음 차이로 종료 코드 1을 반환했다. 각 실험 직후 파일 원문을 복구했고 src/main diff 0을 확인했다.

| 임시 변경 | 검출 경로 | 결과 |
| --- | --- | --- |
| lateGraceMin 10 → 10.0 | $.data.schedule.lateGraceMin | 실패 검출 |
| null holidayName 키 제거 | $.data.holidayName: null / MISSING_KEY | 실패 검출 |
| HomeQueries SQL에 AS id 추가 | 테스트별 SQL 집합 추가·삭제 | 실패 검출 |

독립 비교기 자체 점검도 객체 키 순서만 허용하고 숫자 표현 2종·null 키·배열 순서·날짜 형식·SQL 별칭·쿼리 수 변화를 거부해 8/8을 통과했다.

## 현재 코드 수치

backend에서 `python3 src/test/resources/refactoring-tools/metrics.py`로 재현한다. Java 파일 180개, 160자를 넘는 줄 278개, ApiResponse<?> 93개, SQL 키워드를 포함한 서비스 파일 25개다. 한 줄 여러 문장은 문법 파서가 없는 텍스트 추정치다. 세미콜론 2개 이상인 줄 333개(3개 이상 121개), 문자열·주석·for 헤더를 제외한 추정치는 각각 330개와 116개다. 스크립트에 측정 정의를 보존하며 명세의 예시 수치에 맞추기 위해 정의를 바꾸지 않는다.

## 버린 방법과 이유

- JDBC 연결 반환값 전체 프록시: 드라이버의 공변 반환·브리지 메서드 때문에 실패하거나 기록 누락이 생겨 실행 메서드 관찰로 교체했다.
- 모든 숫자 id·날짜·상태를 일괄 가림: 계약 변화를 숨기므로 사용하지 않았다.
- JSON을 일반 숫자 객체로 읽기: 10/10.0/10.00 구분을 잃을 수 있어 토큰 원문을 보존했다.
- 실제 경합 결과의 차이를 진단 조회로 분류: 업무 SQL과 응답 의미가 달라 승인 범위를 벗어나므로 하지 않았다.

## 남은 한계

기록 모드의 지정한 5개 HTTP 경합은 실제 동시 진행을 검증하지 않는다. 자연 경합 검증은 반드시 기록 옵션을 끈 전체 실행으로 보완해야 한다. SQL 순서 경고는 의도적으로 종료 코드에 반영하지 않고 반드시 검토한다(명세 1.1). 따라서 차이 0은 원본 파일 바이트가 같다는 의미가 아니라 지정한 비교 계약이 같다는 의미다. 기록은 테스트가 실행한 JDBC 경계와 Boot MockMvc 응답의 범위이며 DB 내부 실행 계획이나 MockMvc 외 HTTP 클라이언트 응답을 대신하지 않는다. 테스트 본문은 기본적으로 직렬 실행된다는 현재 JUnit 설정을 전제로 한다. 비동기 작업이 테스트 종료 뒤에도 살아 있으면 귀속 경계가 달라질 수 있다. 기록 옵션을 켤 때만 바이트코드 관찰을 설치하며 Java 17 환경에서 확인했다.


## 재현 명령과 산출물

backend에서 Java 17을 JAVA_HOME에 지정한다. 기록 1·2는 출력 폴더만 다르게 두 번 실행한다.

```bash
./mvnw -Duser.timezone=UTC test
./mvnw -Duser.timezone=UTC -Drefactoring.record=target/refactoring-candidate test
REFACTORING_CLASSPATH=target/refactoring-candidate/classpath.txt \
  bash src/test/resources/refactoring-tools/compare.sh \
  src/test/resources/refactoring-baseline target/refactoring-candidate
python3 src/test/resources/refactoring-tools/self-check.py target/refactoring-candidate/classpath.txt
python3 src/test/resources/refactoring-tools/metrics.py
```

`sql.json`·`responses.json`·`masked-fields.txt`가 기준선이다. manifest.json에는 기준 운영 커밋·기록 정책·검증 결과와 SHA-256을 남겼다. classpath.txt와 실행 로그의 로컬 경로는 기준선에 커밋하지 않는다. scripts의 bash -n과 Python 문법 검사도 통과했다. src/main·frontend·backend/md·pom.xml·V1~V6·.claude는 변경하지 않았고 원본 폴더의 브랜치도 유지했다. 새 버그 수정은 하지 않았으며 병합·푸시는 하지 않는다.

판별력 임시 변경을 모두 복구한 뒤 최종 전체 UTC 실행에서도 231개 성공·실패/오류/건너뜀 0을 확인했다. 단계 2는 시작하지 않았다.
