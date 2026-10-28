# M4 1차 보완·2차 결과

## 범위와 기준
브랜치 `feat/m4-home-board`, 기준 명세는 `57cf2a8:backend/md/15-rest-api-v2-m4.md`와 사용자의 M4 1차 리뷰 추가 결정이다. 1차 보완 후 홈·마감 알림·구 API 제거를 구현했다. 새 라이브러리·V7은 추가하지 않았다. frontend, backend/md, V1~V6, 미추적 .claude와 로컬 개발 DB는 변경하지 않았고 병합·푸시하지 않았다.

## 커밋·테스트·문서

| 커밋 | 작업 | 주요 테스트 | troubleshooting |
|---|---|---|---|
| ac7355e | 실제 UPDATE 후 커밋 전 정지, 두 번째 프로젝트 잠금 대기 증명 | M4DeterministicLockTest (완료·재정렬) | [M4-09](M4-09-deterministic-lock-wait.md), M4-04·06 보강 |
| abb2b60 | MOVED/target 응답, 알림 제목, 검색, 기존 퇴사 멤버, V+C | M4ReviewRegressionTest 6개 | [M4-10](M4-10-review-contract-fixes.md) |
| 1b45159 | 권한·페이지를 먼저 적용한 목록/보드 집계 | M4QueryGrowthTest, M4BoardApiTest | [M4-11](M4-11-scoped-summary.md) |
| 51f753c | 마감 알림 배치, 중복 방지·알림·이력 원자성 | M4OverdueTest 3개 | [M4-13](M4-13-overdue-batch.md) |
| 027b992 | 홈·근태 응답 공유, 위젯 권한, 확장 ANALYZE | M4HomeTest 4개, M4HomeGrowthTest | [M4-12](M4-12-home-and-growth.md) |
| ad3e33f | 구 API·미사용 JPA 서비스·임시 어댑터 제거 | M4RemovedRoutesTest, M1RegressionTest, M3DashboardTest | [M4-14](M4-14-remove-legacy-routes.md) |

## 전체 테스트
JUnit XML 57개 클래스와 Maven 최종 합계를 대조했다.

| 환경 | 실행 | 성공 | 실패 | 오류 | 건너뜀 |
|---|---:|---:|---:|---:|---:|
| 기본 JVM (동일 소스 전체 재실행) | 214 | 214 | 0 | 0 | 0 |
| UTC JVM (-Duser.timezone=UTC) | 214 | 214 | 0 | 0 | 0 |

기본 재실행은 6분 44초였다. UTC의 전체 벽시계 시간에는 아래 환경 중단이 포함되어 처리 성능 자료로 사용하지 않는다. Java 17, Docker, MariaDB 10.11 Testcontainers. UTC는 `/private/tmp`의 별도 복사본에서 실행하며 pom/mvnw/.mvn/src의 262개 파일 SHA-256이 원본과 같다. 전체 검증 중 운영·테스트 소스를 변경하지 않았다.

이전 262개에서 새 테스트 20개를 추가하고 폐기된 구 서비스 테스트 64개(ProjectService 30·TaskService 26·DashboardService 8), 임시 구 쓰기 어댑터 테스트 4개를 제거하여 최종 214개다. @Disabled 추가나 실패 숨김은 없다. 구 서비스 자체가 없으므로 그 테스트는 유지할 대상이 아니며 새 MariaDB 계약 테스트와 구 경로 404 검증으로 대체했다.

### 전체 실행 중 환경 중단
첫 기본/UTC 실행 중 Hikari가 약 1시간 35분의 thread starvation/clock leap를 기록했다. 기본 JVM CoreApiIntegrationTest.approveLeave_shouldReturnApprovedStatus는 로그인과 후속 요청 사이에 이 정지가 끼어 만료된 JWT로 401 INVALID_TOKEN을 받았다. 테스트 한 건의 벽시계 경과가 약 5,700초였다. 이 실패를 성공으로 간주하지 않고 소스 변경 없이 기본 JVM 전체를 다시 실행했다. JWT 만료 정책이나 테스트를 완화하지 않았다. 재실행은 프로세스가 동작하는 동안만 유휴 잠자기를 막고 시스템 설정은 변경하지 않았다.

## 수정 전 증거
- 이력·제목·검색·멤버 회귀: 수정 전 5개 모두 실패 → 수정 후 5개 및 추가 권한 결정 1개 성공.
- 범위 없는 요약: 실제 이전 SQL과 HTTP로 100,000 업무 조회를 먼저 실행. 이후 비교 SQL을 LegacyProjectSummary에 보존하고 같은 결과·계획으로 비교.
- 홈 팀 조회: 최초 SQL이 100배에서 휴가 10,000행을 읽음을 확인 후 순서를 변경. LegacyHomeQueries로 전후 paired 측정.
- 구 경로 제거 뒤 URL 겹침: M4RemovedRoutesTest가 기대 404/실제 500으로 실패한 뒤 삭제 대상 메서드의 404 처리 추가.
- 쓰기 왜곡·순서 중복의 과거 재현은 1차 M4LegacyExperimentTest를 유지한다. **10/10은 barrier로 최악의 순서를 강제한 재현율이며 운영 발생 확률이 아니다.**

## 측정 표
시간은 참고치다. 정합성·행 수·SQL 수를 주요 근거로 사용한다. 서로 다른 실행의 ms를 섞어 속도 배수를 계산하지 않는다.

| 측정 | 이전 | 이후 |
|---|---|---|
| 실제 커밋 전 잠금 대기 증거 | 시작 barrier만 | DB 대기 행 확인 → 첫 커밋 → 두 번째 획득·version 가시성, 최종 DONE |
| 동시 완료 상태 불일치 | 강제 순서 10/10 | 0/10, 결정적 위험 구간 검증 추가 |
| 동시 이동 위치 중복 | 20 | 0, 결정적 대기 검증 추가 |
| 상태 계산 애플리케이션 반환 행 | 1,000 | 2 (DB 내부는 해당 프로젝트 업무 1,000행) |
| 일반 이동 UPDATE | 100 | 1; 정상 재정렬 0, 강제 간격 고갈 재정렬 1 |
| 동시 10개 업무 번호 | 번호 기능 없음 | 중복·빈 번호 0 |
| 목록 요약 업무 읽기, 총 100,000 | 100,000 | 40 (2프로젝트×20업무) |
| 단일 보드 요약 업무 읽기, 총 100,000 | 100,000 | 20 |
| 요약 멤버 읽기 | 전체 28 | 목록 4·보드 2 |
| 목록·보드 데이터 SQL | 2·3 | 2·3 (HTTP 전체 6·7) |
| 팀 휴가 읽기, 휴가 10,000 | 10,000 | 1,000 (내 부서 문서만) |
| 팀 업무 읽기, 업무 100,000 | 4,000 | 2,000 (내 부서 담당만) |
| 홈 SQL, 업무/결재/알림 1·10·100배 | 기존은 같은 홈 계약이 없음 | HTTP 전체 14·14·14 (SELF) |
| 마감 알림 2회 실행 | 신규 기능 | 1건, 마감일 변경 시 누적 2, DONE 추가 0 |
| 동시 마감 배치 2회 | 신규 기능 | 중복 방지·알림 각 1 |
| 알림 실패 | 신규 기능 | 알림·중복 방지·이력 0, 재시도 1 |

단독 기능 측정에서 목록 100,000행의 SQL 중앙값 50.925→0.828ms, 보드 95.371→0.671ms, 팀 SQL 16.956→13.581ms였다. 홈 전체는 1/10/100배에서 23.342/23.403/60.611ms였다. 실제 내 업무·알림 양도 증가하므로 SQL 개수가 일정해도 실행 비용은 증가한다. 자세한 r_rows/r_loops·인덱스·조건은 M4-11·12에 있다.

기존 화면 정적 호출 조사는 관리자 초기 4개(본체 1+공유 레이아웃 3), 사원은 공유 3개이며 본체는 가짜 데이터였다. 새 홈 계약은 1개 요청이다. 프론트는 수정하지 않았고 브라우저 왕복 수를 실측하지 않았으므로 실제 UI가 이미 4→1로 바뀌었다고 주장하지 않는다. 실제 구 dashboard/projects는 프로젝트 5개에 HTTP SQL 13번이었다. 이것은 한 위젯이므로 새 홈 전체 14번과 같은 작업량 비교가 아니다.

## 명세 해석·확정한 결정
- 사용자 확정대로 모든 프로젝트 쓰기는 PROJECT V를 먼저 검사한다. 팀장 예외는 U에만 적용한다. PATCH task는 version 비교 없이 필드 단위 마지막 저장을 적용하고 version은 증가시킨다.
- 이동 응답 `{task, project}`, 409 `error.details.currentTask`, 알림 링크 `/projects/{id}/board?taskId=` 유지.
- teamWeek 전용 메뉴가 없어 휴가는 MY_ATTENDANCE V, 업무는 PROJECT V+프로젝트 범위를 적용했다. 둘 다 없으면 null, 개인 연차/알림은 기존 로그인 규칙이다. 사용자가 항목별 메뉴 권한 적용을 확정했다.
- myTasks.items는 내가 담당인 미완료 업무 중 보기 범위 안의 마감일순 5건이다. 최다 프로젝트 선택은 별도로 하며 동률은 최근 수정·id 역순으로 결정한다.
- 사용자가 구 읽기·쓰기 모두 삭제를 확정하여 임시 구 쓰기도 함께 제거했다. 새 URL과 겹친 옛 메서드는 별도 API를 등록하지 않고 404 예외 처리만 한다.
- 이번 범위에서 추가로 답을 기다리는 결정은 없다. 과거 상태/근무시간 이력 설계는 기존 후속 범위로 남는다.
- 새 인덱스는 추가하지 않았다. 기존 인덱스와 범위 우선 집계로 실험의 전체 스캔을 줄여 V7이 필요하지 않았다.

## 남은 위험·미수정 항목
- 내 부서/본인 업무 자체가 많으면 홈 작업량과 팀 주간 반환량은 증가한다. JOIN 순서는 운영 분포 변화 시 재측정해야 한다.
- 동일 프로젝트 쓰기는 직렬화된다. 마감 배치도 프로젝트 잠금을 사용하므로 큰 배치가 보드 쓰기를 기다리게 할 수 있다.
- 앱 알림은 업무와 함께 커밋하지만 batch_runs 기록 직전 프로세스 장애는 실행 기록 누락을 만들 수 있다. 재실행 알림은 유니크 키로 보호한다.
- 이력 target.name은 현재 사용자 이름이며 과거 이름 스냅샷은 아니다. 기존 문장 접두어가 저장된 알림은 일괄 갱신하지 않았다.
- READ COMMITTED의 여러 위젯 SELECT 사이에 동시 변경이 있으면 시점 차이가 있을 수 있다.
- 기존 remainingAnnualLeave 전 연도 합산과 다른 도메인의 잘못된 HTTP 메서드 500 처리는 유지했다(코드 확인). 캘린더·부서 상태 이력 재설계도 범위 밖이다.
- 사용자 결정대로 새 M4 화면과 함께 병합해야 한다. 여기서는 병합·푸시를 수행하지 않았다.

## 실행 방법
```sh
cd /Users/kimmini/sk-final-deepfake/mini-erp-system/backend
export JAVA_HOME=/Users/kimmini/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home
# 구 클래스가 target에 남은 기존 체크아웃은 한 번 clean
./mvnw clean test
./mvnw -Duser.timezone=UTC test
# 측정만 재실행
./mvnw -Dtest=M4DeterministicLockTest,M4QueryGrowthTest,M4HomeGrowthTest,M4OverdueTest test
```
Docker를 실행한 상태에서 검증한다. DB는 테스트 컨테이너가 생성하며 로컬 개발 DB 이관/repair 명령은 수행하지 않았다.

## 커밋별 바뀐 파일

### ac7355e

```text
A	backend/src/test/java/com/minierp/backend/integration/M4DeterministicLockTest.java
M	backend/src/test/java/com/minierp/backend/support/JdbcProbe.java
M	docs/troubleshooting/M4-04-write-skew.md
M	docs/troubleshooting/M4-06-card-order.md
A	docs/troubleshooting/M4-09-deterministic-lock-wait.md
```

### abb2b60

```text
M	backend/src/main/java/com/minierp/backend/domain/project/controller/ProjectBoardController.java
M	backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java
M	backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java
A	backend/src/test/java/com/minierp/backend/integration/M4ReviewRegressionTest.java
A	docs/troubleshooting/M4-10-review-contract-fixes.md
```

### 1b45159

```text
M	backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java
A	backend/src/main/java/com/minierp/backend/domain/project/service/ProjectSummaryQuery.java
A	backend/src/test/java/com/minierp/backend/integration/M4QueryGrowthTest.java
M	backend/src/test/java/com/minierp/backend/support/JdbcProbe.java
A	backend/src/test/java/com/minierp/backend/support/LegacyProjectSummary.java
A	backend/src/test/java/com/minierp/backend/support/MariaDbPlan.java
A	docs/troubleshooting/M4-11-scoped-summary.md
```

### 51f753c

```text
M	backend/src/main/java/com/minierp/backend/domain/notification/service/NotificationService.java
A	backend/src/main/java/com/minierp/backend/domain/task/service/TaskOverdueScheduler.java
A	backend/src/main/java/com/minierp/backend/domain/task/service/TaskOverdueService.java
A	backend/src/main/java/com/minierp/backend/domain/task/service/TaskOverdueWork.java
A	backend/src/test/java/com/minierp/backend/integration/M4OverdueTest.java
A	docs/troubleshooting/M4-13-overdue-batch.md
```

### 027b992

```text
M	backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java
M	backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceStore.java
A	backend/src/main/java/com/minierp/backend/domain/home/controller/HomeController.java
A	backend/src/main/java/com/minierp/backend/domain/home/service/HomeQueries.java
A	backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java
A	backend/src/test/java/com/minierp/backend/integration/M4HomeGrowthTest.java
A	backend/src/test/java/com/minierp/backend/integration/M4HomeTest.java
A	backend/src/test/java/com/minierp/backend/support/LegacyHomeQueries.java
A	docs/troubleshooting/M4-12-home-and-growth.md
```

### ad3e33f

```text
D	backend/src/main/java/com/minierp/backend/domain/dashboard/controller/DashboardController.java
D	backend/src/main/java/com/minierp/backend/domain/dashboard/service/DashboardService.java
M	backend/src/main/java/com/minierp/backend/domain/project/controller/ProjectBoardController.java
D	backend/src/main/java/com/minierp/backend/domain/project/controller/ProjectController.java
M	backend/src/main/java/com/minierp/backend/domain/project/entity/Project.java
M	backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java
D	backend/src/main/java/com/minierp/backend/domain/project/service/ProjectService.java
M	backend/src/main/java/com/minierp/backend/domain/task/controller/TaskBoardController.java
D	backend/src/main/java/com/minierp/backend/domain/task/controller/TaskController.java
M	backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java
D	backend/src/main/java/com/minierp/backend/domain/task/service/TaskService.java
M	backend/src/main/java/com/minierp/backend/global/exception/GlobalExceptionHandler.java
D	backend/src/test/java/com/minierp/backend/domain/dashboard/service/DashboardServiceTest.java
D	backend/src/test/java/com/minierp/backend/domain/project/service/ProjectServiceTest.java
D	backend/src/test/java/com/minierp/backend/domain/task/service/TaskServiceTest.java
M	backend/src/test/java/com/minierp/backend/integration/M1RegressionTest.java
M	backend/src/test/java/com/minierp/backend/integration/M3DashboardTest.java
D	backend/src/test/java/com/minierp/backend/integration/M4LegacyWriteBridgeTest.java
M	backend/src/test/java/com/minierp/backend/integration/M4QueryGrowthTest.java
A	backend/src/test/java/com/minierp/backend/integration/M4RemovedRoutesTest.java
A	docs/troubleshooting/M4-14-remove-legacy-routes.md
```

## 최종 전체 실행의 측정 출력

아래는 각 JVM 전체 실행에서 추린 측정값이다. 시간 비교는 각 실행의 paired 행을 사용하며, 잠금 대기에는 의도적인 150ms 정지가 포함된다.

### 기본 JVM 재실행

```text
M4_OVERDUE repeat_notifications=1 changed_deadline_total=2 done_additional=0 run_records=4
M4_OVERDUE concurrent_runs=2 claims=1 notifications=1
M4_VERSION stale_http=409 order_changes=0 history_changes=0
M4_QUERY_COUNT list_projects_added=20 data_queries=2 http_before=6 http_after=6 board_cards=1/100 data_queries=3 http_before=7 http_after=7
M4_HOME_FUNCTIONAL http_queries=14
M4_BEFORE_WRITE_SKEW trials=10 inconsistent=10
M4_BEFORE_ORDER cards=100 update_rows=100 concurrent_trials=20 duplicate_positions=20
M4_BEFORE_STATUS tasks=1000 result_rows=1000 queries=3 select_queries=1 warmups=3 samples=9 median_ms=7.656
M4_DASHBOARD_COMPARISON projects=5 task_count_queries=10 core_queries=11; actual_removed_HTTP_before=13
M4_HOME_GROWTH scale=1 tasks=1000 documents=100 notifications=100 queries=14 median_ms=13.357
M4_HOME_GROWTH scale=10 tasks=10000 documents=1000 notifications=1000 queries=14 median_ms=20.916
M4_HOME_GROWTH scale=100 tasks=100000 documents=10000 notifications=10000 queries=14 median_ms=53.984
M4_DETERMINISTIC kind=reorder database_waits=1 wait_ms=196.329 first_commit_visible=true events=[A_TASK_UPDATED_UNCOMMITTED, B_ATTEMPTS_PROJECT_LOCK, DATABASE_CONFIRMS_B_LOCK_WAIT, A_ALLOWED_TO_COMMIT, B_LOCK_ACQUIRED_A_COMMIT_VISIBLE]
M4_DETERMINISTIC kind=completion database_waits=1 wait_ms=181.827 first_commit_visible=true events=[A_TASK_UPDATED_UNCOMMITTED, B_ATTEMPTS_PROJECT_LOCK, DATABASE_CONFIRMS_B_LOCK_WAIT, A_ALLOWED_TO_COMMIT, B_LOCK_ACQUIRED_A_COMMIT_VISIBLE]
M4_AFTER_ORDER cards=100 update_rows=1 concurrent_trials=20 duplicate_positions=0 normal_rebalances=0 forced_rebalances=1 forced_update_rows=100
M4_AFTER_WRITE_SKEW trials=10 inconsistent=0
M4_STATUS_PAIRED tasks=1000 warmups=3 samples=9 before_queries=3 after_queries=4 before_result_rows=1000 after_result_rows=2 before_median_ms=6.337 after_median_ms=3.620
```

### UTC JVM

```text
M4_OVERDUE repeat_notifications=1 changed_deadline_total=2 done_additional=0 run_records=4
M4_OVERDUE concurrent_runs=2 claims=1 notifications=1
M4_VERSION stale_http=409 order_changes=0 history_changes=0
M4_QUERY_COUNT list_projects_added=20 data_queries=2 http_before=6 http_after=6 board_cards=1/100 data_queries=3 http_before=7 http_after=7
M4_HOME_FUNCTIONAL http_queries=14
M4_BEFORE_WRITE_SKEW trials=10 inconsistent=10
M4_BEFORE_ORDER cards=100 update_rows=100 concurrent_trials=20 duplicate_positions=20
M4_BEFORE_STATUS tasks=1000 result_rows=1000 queries=3 select_queries=1 warmups=3 samples=9 median_ms=8.559
M4_DASHBOARD_COMPARISON projects=5 task_count_queries=10 core_queries=11; actual_removed_HTTP_before=13
M4_HOME_GROWTH scale=1 tasks=1000 documents=100 notifications=100 queries=14 median_ms=20.226
M4_HOME_GROWTH scale=10 tasks=10000 documents=1000 notifications=1000 queries=14 median_ms=24.458
M4_HOME_GROWTH scale=100 tasks=100000 documents=10000 notifications=10000 queries=14 median_ms=56.695
M4_DETERMINISTIC kind=reorder database_waits=1 wait_ms=167.697 first_commit_visible=true events=[A_TASK_UPDATED_UNCOMMITTED, B_ATTEMPTS_PROJECT_LOCK, DATABASE_CONFIRMS_B_LOCK_WAIT, A_ALLOWED_TO_COMMIT, B_LOCK_ACQUIRED_A_COMMIT_VISIBLE]
M4_DETERMINISTIC kind=completion database_waits=1 wait_ms=169.579 first_commit_visible=true events=[A_TASK_UPDATED_UNCOMMITTED, B_ATTEMPTS_PROJECT_LOCK, DATABASE_CONFIRMS_B_LOCK_WAIT, A_ALLOWED_TO_COMMIT, B_LOCK_ACQUIRED_A_COMMIT_VISIBLE]
M4_AFTER_ORDER cards=100 update_rows=1 concurrent_trials=20 duplicate_positions=0 normal_rebalances=0 forced_rebalances=1 forced_update_rows=100
M4_AFTER_WRITE_SKEW trials=10 inconsistent=0
M4_STATUS_PAIRED tasks=1000 warmups=3 samples=9 before_queries=3 after_queries=4 before_result_rows=1000 after_result_rows=2 before_median_ms=11.123 after_median_ms=3.337
```
