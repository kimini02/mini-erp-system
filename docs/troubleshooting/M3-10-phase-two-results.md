# M3 2차 결과

## 기준·범위
- 작업 브랜치: `feat/m3-attendance`. 1차 `b0dd5fb`에서 계속했다. 병합·푸시하지 않았다.
- 기준: `feat/m3-frontend`의 `d689d15:backend/md/14-rest-api-v2-m3.md`를 읽기 전용으로 참조했다. 로컬 develop에 없는 명세를 임의 복사·병합하지 않았다.
- 2차 월간 관리·근태 정정·연차 자동 부여/관리·구 계약 삭제와 사용자가 확정한 퇴사일 보완을 구현했다.
- frontend/, backend/md/, V1~V4, pom.xml, 미추적 .claude/는 이번 2차에서 수정하지 않았다. 로컬 개발 DB는 연결하지 않았다.

## 작업별 커밋·테스트·기록
| 작업 | 커밋 | 테스트 | troubleshooting |
|---|---|---|---|
| 퇴사일·결근 배치 보완 | `a746ced` | M3RetirementTest, M3BatchTest, MariaDbMigrationTest | [M3-5-retirement-window.md](M3-5-retirement-window.md) |
| 월간 표·상세·CSV·재판정 | `761bd74` | M3MonthlyTest | [M3-6-monthly-query.md](M3-6-monthly-query.md) |
| 근태 정정 결재 | `7aac727` | M3AttendanceFixTest | [M3-7-attendance-correction.md](M3-7-attendance-correction.md) |
| 연차 부여·관리·조정 | `2e6d226` | M3LeaveGrantTest, M3GrantRegistrationTest, M3LeaveAdjustmentConcurrencyTest | [M3-8-leave-grants-and-adjustments.md](M3-8-leave-grants-and-adjustments.md) |
| 구 근태 계약 정리 | `66a52fc` | M3LegacyRoutesTest | [M3-9-legacy-attendance-removal.md](M3-9-legacy-attendance-removal.md) |

### 변경 파일 전체

**a746ced — 퇴사일·결근 배치 보완**

- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AbsenceBatchWork.java`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceStore.java`
- `backend/src/main/java/com/minierp/backend/domain/user/service/EmployeeService.java`
- `backend/src/main/resources/db/migration/V5__user_retirement_date.sql`
- `backend/src/test/java/com/minierp/backend/integration/M3BatchTest.java`
- `backend/src/test/java/com/minierp/backend/integration/M3RetirementTest.java`
- `backend/src/test/java/com/minierp/backend/integration/MariaDbMigrationTest.java`
- `docs/troubleshooting/M3-5-retirement-window.md`

**761bd74 — 월간 표·상세·CSV·재판정**

- `backend/src/main/java/com/minierp/backend/domain/attendance/controller/AttendanceAdminController.java`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceMonthlyQuery.java`
- `backend/src/test/java/com/minierp/backend/integration/M3MonthlyTest.java`
- `docs/troubleshooting/M3-6-monthly-query.md`

**7aac727 — 근태 정정 결재**

- `backend/src/main/java/com/minierp/backend/domain/attendance/handler/AttendanceFixFormHandler.java`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceStore.java`
- `backend/src/test/java/com/minierp/backend/integration/M3AttendanceFixTest.java`
- `backend/src/test/java/com/minierp/backend/support/AttendanceTestSupport.java`
- `docs/troubleshooting/M3-7-attendance-correction.md`

**2e6d226 — 연차 부여·관리·조정**

- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AbsenceBatchService.java`
- `backend/src/main/java/com/minierp/backend/domain/leave/controller/LeaveAdminController.java`
- `backend/src/main/java/com/minierp/backend/domain/leave/service/LeaveAdminService.java`
- `backend/src/main/java/com/minierp/backend/domain/leave/service/LeaveGrantRules.java`
- `backend/src/main/java/com/minierp/backend/domain/leave/service/LeaveGrantScheduler.java`
- `backend/src/main/java/com/minierp/backend/domain/leave/service/LeaveGrantService.java`
- `backend/src/main/java/com/minierp/backend/domain/leave/service/LeaveGrantWork.java`
- `backend/src/main/java/com/minierp/backend/domain/leave/service/LeaveQueryService.java`
- `backend/src/main/java/com/minierp/backend/domain/user/service/EmployeeService.java`
- `backend/src/main/java/com/minierp/backend/global/batch/BatchRunRecorder.java`
- `backend/src/test/java/com/minierp/backend/integration/M3GrantRegistrationTest.java`
- `backend/src/test/java/com/minierp/backend/integration/M3LeaveAdjustmentConcurrencyTest.java`
- `backend/src/test/java/com/minierp/backend/integration/M3LeaveGrantTest.java`
- `docs/troubleshooting/M3-8-leave-grants-and-adjustments.md`

**66a52fc — 구 근태 계약 정리**

- `backend/src/main/java/com/minierp/backend/domain/attendance/dto/AttendanceSummaryDto.java`
- `backend/src/main/java/com/minierp/backend/domain/attendance/dto/AttendanceUpdateRequestDto.java`
- `backend/src/main/java/com/minierp/backend/domain/attendance/dto/AttendanceUpdateResponseDto.java`
- `backend/src/main/java/com/minierp/backend/domain/attendance/dto/CheckInRequestDto.java`
- `backend/src/main/java/com/minierp/backend/domain/attendance/dto/CheckInResponseDto.java`
- `backend/src/main/java/com/minierp/backend/domain/attendance/dto/CheckOutRequestDto.java`
- `backend/src/main/java/com/minierp/backend/domain/attendance/dto/CheckOutResponseDto.java`
- `backend/src/main/java/com/minierp/backend/domain/attendance/entity/Attendance.java`
- `backend/src/main/java/com/minierp/backend/domain/attendance/entity/AttendanceStatus.java`
- `backend/src/test/java/com/minierp/backend/integration/M3LegacyRoutesTest.java`
- `docs/troubleshooting/M3-9-legacy-attendance-removal.md`

## 전체 테스트
최종 UTC JVM: **230 실행 / 230 성공 / 0 실패 / 0 오류 / 0 건너뜀**, 10분 21초. 기본 JVM(Asia/Seoul): **230 실행 / 230 성공 / 0 실패 / 0 오류 / 0 건너뜀**, 10분 33초. 최종 소스 커밋은 `66a52fc`이며 이후에는 보고 문서만 수정한다. 기본 시간대 실행은 이 커밋에서 git archive로 추적된 backend만 추출한 임시 폴더에서 실행하여 원본 UTC 실행의 빌드 디렉터리와 격리한다.

```sh
cd /Users/kimmini/sk-final-deepfake/mini-erp-system/backend
export JAVA_HOME=/Users/kimmini/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home
./mvnw test
./mvnw -Duser.timezone=UTC test
```

검증 로그: `/private/tmp/m3-phase2-final-utc.log`, `/private/tmp/m3-phase2-final-default.log`. 임시 복사본의 추적 파일 내용이 원본과 같은 것을 확인한 뒤 복사본은 삭제했다.

Java 17, Docker, MariaDB 10.11 Testcontainers를 사용했다. 기존 초대/재설정 테스트의 Redis 컨테이너 외 새 Redis 용도는 없다. 신규 라이브러리는 추가하지 않았다.

### 명세 8장 10개 항목 대응
| 항목 | 검증 |
|---|---|
| 1 시각 조작 방지 | M3SecurityTest, M3LegacyAttendanceTest, M3LegacyRoutesTest |
| 2 경계 판정 | M3AttendanceTest.minuteBoundarySnapshotAndRepeatedCheckout, morningAndAfternoonHalfLeaveBoundariesAndFullLeave |
| 3 스냅샷 | M3AttendanceTest.minuteBoundarySnapshotAndRepeatedCheckout |
| 4 동시 출근 | M3AttendanceTest.concurrentClockInHasExactlyOneWinnerInTenTrials |
| 5 결근 배치·고용 경계 | M3BatchTest, M3RetirementTest.absenceUsesHistoricalEmploymentWindowAndCurrentLeaveStatus |
| 6 월간 50×31·쿼리·퇴사 표시 | M3MonthlyTest 3개, M3RetirementTest.retirementRecordsKoreanDateAndReactivationClearsIt |
| 7 근태 정정 결재 | M3AttendanceFixTest 4개 |
| 8 자동 부여 | M3LeaveGrantTest 5개, M3GrantRegistrationTest, M3LeaveAdjustmentConcurrencyTest |
| 9 UTC JVM/한국 날짜 | M3AttendanceTest.seoulMidnightAndNextDayCheckoutUseLocalDate, M3BatchTest 및 전체 UTC 실행 |
| 10 대시보드 CHIEF 전용 | M3DashboardTest, DashboardServiceTest |

## 전후 수치와 측정 범위
| 실험 | 전 | 후 |
|---|---|---|
| 퇴사 응답의 날짜 | 새 회귀 테스트 1/1 실패(필드 없음) | 기록·복직 NULL 통과 |
| 다음 날 퇴근 설명 | 실제 outResult는 정상인데 조퇴 설명 추가, 1/1 실패 | 저장된 outResult로 설명, 1/1 통과 |
| 신규 입사 당일 연차 | 직급 정책 GRANT 14일, 기대 0 테스트 1/1 실패 | 0일, 자동 부여 배치가 맡음 |
| 잠금 없는 연차 조정 모델 2스레드×10 | 잔액 -2일 10/10 | 새 조정 API 음수 0/10, 매회 200·422 |
| 같은 사용자의 상신 대 조정 | 각 요청이 6일을 요구, 시작 잔액 10일 | 하나만 성공, 잔액 4일 |
| 동시 자동 부여 2스레드×10 | 새 기능이므로 기존 배치와 비교하지 않음 | 키별 1건, 중복 0/10 |
| 월 부여 | 1/31 입사 | 2/28 첫 1일, 1~11개월 총 11일 |
| 1주년·회계연도 부여 | 7/1 1주년, 1/1 1주년, 근속 3·21년 | 각각 8·15·16·25일, 1/1 중복 Y 키 없음 |

월간 성능: 50명×31일=1,550칸, 표 데이터 SQL 1회. 별도 선택 부서 존재 검사 1회이며 셀별 조회가 없다. JWT·권한·부서 범위 조회를 포함한 HTTP 요청 전체가 1 SQL이라는 의미는 아니다. EXPLAIN의 근태 a: eq_ref, uk_attendances_user_date, 예상 rows=1.
- 최초 개별 측정(예열 3회·표본 9회 중앙값): 인덱스 제외 349.327ms → 적용 19.291ms.
- 첫 전체 UTC 측정: 394.176ms → 30.999ms.
- 마지막 수정까지 포함한 최종 UTC 측정: **442.613ms → 30.140ms**.
- 최종 기본 JVM 측정: **324.228ms → 17.824ms**.
- 비교군은 같은 SQL에 IGNORE INDEX를 적용한 통제 실험이며, 실제 옛 월간 API가 있었다는 의미가 아니다. 로컬 Docker 시간이며 성능 보장치가 아니다.

1차 KI-04의 실제 구 API 조작 10/10 → 새 API 0/10, 동시 출근, 결근 재실행 결과는 [1차 결과](M3-0-phase-one-results.md)를 따른다.

## 명세와의 차이·판단·결정 필요
1. **퇴사일 결정은 반영 완료.** V5에서 NULL 컬럼을 추가하고 실제 RETIRED 전환일을 Clock으로 기록, RETIRED→ACTIVE에 NULL로 복원한다. 기존 퇴사일은 추측하지 않는다. 퇴사일부터 결근 행을 만들지 않으며 NULL이면 날짜 제한이 없다. 월간 표는 확정된 재직/휴직/퇴사·기록 유무 조건을 따른다.
2. **상태 변경 이력 테이블은 결정 필요.** 휴직은 배치 시점 상태로 판단하고 재입사 시 퇴사일을 지우므로 과거 휴직/퇴사 기간을 복원할 수 없다. 사용자 지시대로 범위 밖이다.
3. **과거 근무시간 이력은 없음.** 정정할 근태 행이 있으면 저장된 스냅샷을 유지한다. 행이 없으면 현재 부서/회사 설정을 해당 날짜의 스냅샷으로 사용한다. ‘그날 기준’을 역사적으로 재현하려면 근무시간 유효기간 설계가 필요하다.
4. **기존 부여분과 새 소급 부여의 정산 기준은 결정 필요.** 명세대로 예정된 모든 키를 계산하며 v1/옛 신규 등록 원장은 보존한다. 같은 연도 기존 GRANT와 자동 부여가 겹칠 수 있다. 원장 삭제·자동 상계·시작 연도 제한을 임의로 만들지 않았다. 운영 정책 전환 전에 정산 기준을 확정해야 한다.
5. 새로 등록하는 사원의 직급별 즉시 GRANT는 M3 4.1과 충돌하는 것을 재현하여 중단했다. 입사일 수정이나 재입사 때 기존 부여 키를 취소/재산정하는 규칙은 명세에 없어서 추가하지 않았다.
6. 정정 본문의 출근·퇴근 중 한 값만 있는 경우 나머지 기존 값은 보존한다. 두 값을 합친 시간 순서가 잘못되면 승인도 400으로 롤백한다. 퇴근만 정정이 허용되므로 승인된 결근 해제는 재판정에서도 보존한다.
7. 구 근태 라우트 삭제는 V4·KI-04 처리 때문에 1차에서 앞당겼고, 2차에서는 남은 DTO·호환 코드를 삭제했다. 배치 공통 기록기는 global.batch로 이동했다.

## 발견했지만 고치지 않은 문제
- 기존 사용자 호환 응답 remainingAnnualLeave 및 User의 연차 Formula는 원장 전체 연도를 합산한다(EmployeeService.views, User). 새 /leave/balance와 /leave/admin/balances는 요청 연도만 합산하지만 옛 응답을 사용하는 화면에는 이월 없는 정책과 다른 수치가 나올 수 있다. 새 API 범위 밖 호환 필드의 의미를 임의로 바꾸지 않았으며 프론트 전환/계약 결정이 필요하다.
- 위의 기존 GRANT와 소급 부여 정산, 상태/근무시간 이력 부재는 확정되지 않은 정책·데이터 한계다. 기존 원장을 파괴하거나 과거 날짜를 추정하는 수정은 하지 않았다.

## 실행·이관 범위
- 새 DB: Flyway V1~V5 전체 실행 및 Hibernate validate를 MariaDbMigrationTest에서 확인한다.
- 이미 V4까지 적용된 DB: 새 V5만 적용된다. V1~V4 체크섬은 바꾸지 않았다. 실제 로컬 개발 DB에 적용하지 않았다.
- app.batches.enabled=true(기본)이면 한국 시각 00:10 결근, 00:30 연차 부여를 실행한다. 테스트에서는 false로 두고 같은 서비스를 직접 호출한다. 기존 데이터로 배치를 가동하기 전 위 정산 결정이 필요하다.
- 배치 이력은 실행마다 남고 업무 결과가 멱등이다. 실패 기록은 별도 트랜잭션이며 DB 자체가 불능이면 이력 저장도 불가능하다.
