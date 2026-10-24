# M0-4 시간에 의존하는 테스트와 알려진 실패 정리

## 증상
M0 종료 시 전체 104개 중 7개가 실패했다. 연차 통합 테스트 5개는 고정된 2026-04 날짜와 실제 오늘 검증이 충돌했고, TaskTest 1개는 어제 날짜의 업무와 이미 끝난 고정 프로젝트 기간이 충돌했다. 나머지 특근 신청 테스트는 실제 요청 필드 불일치로 500이 발생했다.

## 재현 방법
변경 전 `backend/`에서 Java 17로 `./mvnw test`를 실행한다. 실행 날짜가 고정된 테스트 데이터 이후이면 연차 5개·업무 지연 1개가 실패한다. 특근은 overtimeDate만 전송하면 startDate/endDate가 null이 되어 500을 반환한다.

변경 후 재검증 명령:
```bash
cd backend
./mvnw test
./mvnw -Dtest.clock.instant=2026-12-31T15:00:00Z -Duser.timezone=UTC test
```
Docker를 실행해야 MariaDB 테스트가 실제 수행된다. 두 번째 명령의 한국 날짜는 2027-01-01 00:00이다.

## 원인
업무 서비스·엔티티·응답 객체가 시스템 기본 시간대를 쓰는 now()를 직접 호출했다. 테스트는 고정 달력 날짜와 실제 오늘을 혼용해 재현 가능한 기준 시각이 없었다. 캘린더 테스트도 잘못된 특근 신청 경로로 데이터를 준비하고 있어, 연차 날짜 실패를 해결하면 같은 특근 500이 드러난다.

## 검토했지만 버린 방법과 이유
- 테스트 날짜를 더 먼 미래로 교체: 시간이 지나면 다시 실패한다.
- 테스트에서 시스템 시계나 JVM 기본 시간대를 변경하는 것에 의존: 서버가 UTC인 경우 한국 날짜를 보장하지 못한다.
- 전역 static 가변 Clock: 테스트·요청 간 시계가 섞일 수 있다.
- 엔티티에 Spring 빈 주입: 도메인 객체가 실행 환경에 의존하게 된다.
- 캘린더 테스트까지 비활성화: 알려진 신청 오류 때문에 정상 조회 기능의 회귀 검증도 잃게 된다.
- 특근 운영 DTO를 지금 수정: M2에서 결재 양식으로 재구축하기로 한 범위를 벗어난다.

## 선택한 방법
1. `ClockConfig`에 `Clock.system(ZoneId.of("Asia/Seoul"))` 빈을 등록했다. 서비스와 응답을 만드는 호출자는 생성자로 Clock을 주입받는다. static 응답 팩토리는 Clock을 명시적 인자로 받으며 응답 구조는 유지했다.
2. `Task.isOverdue(LocalDate today)`로 엔티티에 기준 날짜를 전달한다. 테스트는 fixed Clock에서 기준일을 계산하고 프로젝트·업무 날짜는 그 기준일의 상대 날짜로 만든다. 기존 프로젝트·업무 관련 다른 테스트의 고정 날짜도 상대 날짜로 정리해 날짜 데이터에 달력 날짜가 남지 않게 했다.
3. `FixedClockTestConfiguration`의 @Primary Clock과 `TestClocks`를 제공했다. 통합 테스트와 MariaDB 공통 베이스에서 가져오며 `test.clock.instant`로 JVM을 다시 실행할 때 기준 시각을 바꿀 수 있다.
4. 캘린더 테스트는 승인된 특근 엔티티를 저장해 조회 준비 데이터를 만든다. 연차 신청·승인 경로는 그대로 검증하고 특근 조회도 유지한다. 월 경계에서 빠지지 않도록 각 일정의 연·월로 조회한다.
5. 특근 신청 테스트 하나에 요청된 @Disabled 사유를 지정했다. docs/known-issues.md에 남은 특근 DTO 불일치(M2), 부서 데이터 범위(M1), 연차 승인 동시성(M2)의 근거·복구 조건을 기록했다. 운영 특근 버그는 수정하지 않았다.

### now() 변경 위치
| 위치 (backend/src/main/java/com/minierp/backend 기준) | 변경 |
| --- | --- |
| domain/approval/service/ApprovalService.java — createLeaveRequest | LocalDate.now(clock) |
| domain/overtime/service/OvertimeService.java — requestOvertime | LocalDate.now(clock) |
| domain/task/entity/Task.java — isOverdue | today 메서드 인자 |
| global/response/ApiResponse.java — success 두 오버로드, successMessage | LocalDateTime.now(clock), 총 3곳 |
| global/response/ErrorResponse.java — of 두 오버로드 | LocalDateTime.now(clock), 총 2곳 |

총 8곳을 변경했다. 기존 LocalTime.now() 직접 호출은 없었다. 이 Clock을 전달하기 위해 9개 컨트롤러(Approval, Attendance, Calendar, Dashboard, Overtime, Project, Task, Auth, User)와 GlobalExceptionHandler에 생성자 주입을 추가했다.

## 부작용·남은 위험
- 응답 timestamp가 서버 기본 시간대 대신 한국 시간으로 일관되게 생성된다. JSON 필드와 형식은 동일하다.
- 응답 팩토리 및 Task.isOverdue의 Java 호출 인자가 달라졌다. 저장소 내 호출자는 모두 갱신했다.
- JWT 발급·만료의 기존 Date 및 JPA의 자동 생성·수정 시각은 직접 LocalDate/LocalDateTime/LocalTime.now() 호출 대상이 아니므로 변경하지 않았다. 업무용 Clock으로 이를 제어할 수는 없다.
- 기존 공휴일 정책은 변경하지 않았다. 임의 고정 시각으로 공휴일 정책을 검증할 때는 의도한 근무일/공휴일 데이터를 선택해야 한다.
- 다음 단계 설계 문서 10/11/12와 frontend는 수정하거나 커밋하지 않는다. 새 라이브러리·Redis 사용을 추가하지 않았다.

## 결과
1번(시계) 작업 검증: Java 17 + Docker에서 총 **107개, 성공 106개, 실패 1개, 오류 0개, 건너뜀 0개**. 남은 실패는 approveOvertime_shouldReturnApprovedStatus 하나뿐이다.

추가된 ClockBehaviorTest는 3회 실행된다. 운영 시계의 Asia/Seoul 시간대와 UTC 날짜 경계를 검증하고, changingFixedDatePreservesPastRequestAndOverdueRules를 두 고정 시각에서 실행해 연차·특근 과거 신청 거부 및 업무 지연/완료 판정이 유지됨을 확인했다. 고정 시각 리터럴은 Clock의 기준점을 지정하는 곳에만 남겼으며 업무 날짜 데이터는 기준일에서 계산한다. CoreApiIntegrationTest는 성공·오류 응답의 timestamp도 주입된 Clock과 일치하는지 검사한다.


### 최종 결과 (2번 작업 포함)
Java 17.0.16 + Docker 실행 상태에서 두 번 모두 Maven 종료 코드 0:

| 실행 조건 | 총 | 성공 | 실패 | 오류 | 건너뜀 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 기본 Clock.fixed (2026-04-06T00:00:00Z) | 107 | 106 | 0 | 0 | 1 |
| Clock.fixed=2026-12-31T15:00:00Z, JVM 시간대 UTC | 107 | 106 | 0 | 0 | 1 |

두 번째 실행의 한국 날짜는 2027-01-01이다. Surefire 실행 속성에서 java.version=17.0.16, user.timezone=UTC, test.clock.instant 값을 확인했다. 두 실행 모두 유일한 건너뜀은 `CoreApiIntegrationTest.approveOvertime_shouldReturnApprovedStatus`이며 사유는 `M2에서 수정 — docs/known-issues.md 참고`다. MariaDbMigrationTest 3개와 InitialAdminMariaDbTest 1개는 실제 컨테이너에서 실행됐다.

새 라이브러리 없이 변경 전 104개에 ClockBehaviorTest의 3회 실행을 추가했다. 운영 코드의 인자 없는 LocalDate.now()/LocalDateTime.now()/LocalTime.now() 및 인자 없는 isOverdue() 호출이 남지 않았음을 검색으로 확인했다. 금지된 frontend 및 다음 단계 설계 문서 10/11/12는 변경·커밋하지 않았다.
