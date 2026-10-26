# M3 1차 결과

## 범위와 기준
- 브랜치: `feat/m3-attendance`, `develop`의 `5f85a6f`에서 생성.
- develop에는 M3 명세가 없어서 `feat/m3-frontend`의 `dc2e63c:backend/md/14-rest-api-v2-m3.md`를 읽기 전용으로 참조했다. 명세 파일을 복사·수정하거나 프론트 브랜치를 병합하지 않았다.
- 완료 범위는 1차 1~4번이다. 2차 월간 관리·근태 정정 처리기·연차 부여/관리·구 DTO 정리는 아직 구현하지 않았다. V4의 정정 양식은 2차 처리기 연결 전이므로 이 중간 결과를 M3 배포 완료본으로 사용하지 않는다.

## 작업별 결과
| 작업 | 커밋 | 검증 | 문제 기록 |
|---|---|---|---|
| V4 스키마·v1 이관 | `1d8ec33` | M3DataMigrationTest, MariaDbMigrationTest, M2FormApiTest | [M3-1-schema-migration.md](M3-1-schema-migration.md) |
| 서버 시각·출퇴근·내 한 달·KI-04 | `7ab4a07` | M3SecurityTest, M3AttendanceTest, M3LegacyAttendanceTest, CoreApiIntegrationTest | [M3-2-server-clock.md](M3-2-server-clock.md) |
| 결근 배치·실행 기록 | `fee8e13` | M3BatchTest | [M3-3-absence-batch.md](M3-3-absence-batch.md) |
| KI-07 관리소장 전용 요약 | `a2254af` | M3DashboardTest, DashboardServiceTest | [M3-4-dashboard-chief-only.md](M3-4-dashboard-chief-only.md) |

### 변경 파일

**1d8ec33 — V4 스키마·v1 이관**

- `backend/src/main/java/com/minierp/backend/domain/approval/service/FormService.java`
- `backend/src/main/resources/db/migration/V4__attendance_and_batch_runs.sql`
- `backend/src/test/java/com/minierp/backend/integration/M2FormApiTest.java`
- `backend/src/test/java/com/minierp/backend/integration/M3DataMigrationTest.java`
- `backend/src/test/java/com/minierp/backend/integration/MariaDbMigrationTest.java`
- `docs/troubleshooting/M3-1-schema-migration.md`

**7ab4a07 — 서버 시각·출퇴근·내 한 달·KI-04**

- `backend/src/main/java/com/minierp/backend/domain/attendance/controller/AttendanceController.java`
- `backend/src/main/java/com/minierp/backend/domain/attendance/entity/Attendance.java`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceRules.java`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceStore.java`
- `backend/src/main/java/com/minierp/backend/global/exception/ErrorCode.java`
- `backend/src/test/java/com/minierp/backend/integration/CoreApiIntegrationTest.java`
- `backend/src/test/java/com/minierp/backend/integration/M3AttendanceTest.java`
- `backend/src/test/java/com/minierp/backend/integration/M3LegacyAttendanceTest.java`
- `backend/src/test/java/com/minierp/backend/integration/M3SecurityTest.java`
- `backend/src/test/java/com/minierp/backend/support/AttendanceTestSupport.java`
- `docs/troubleshooting/M3-2-server-clock.md`

**fee8e13 — 결근 배치·실행 기록**

- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AbsenceBatchService.java`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AbsenceBatchWork.java`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceScheduler.java`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/BatchRunRecorder.java`
- `backend/src/main/java/com/minierp/backend/global/config/BatchSchedulingConfig.java`
- `backend/src/test/java/com/minierp/backend/integration/M3BatchTest.java`
- `backend/src/test/java/com/minierp/backend/support/MariaDbIntegrationTest.java`
- `docs/troubleshooting/M3-3-absence-batch.md`

**a2254af — KI-07 관리소장 전용 요약**

- `backend/src/main/java/com/minierp/backend/domain/dashboard/service/DashboardService.java`
- `backend/src/test/java/com/minierp/backend/domain/dashboard/service/DashboardServiceTest.java`
- `backend/src/test/java/com/minierp/backend/integration/M3DashboardTest.java`
- `docs/troubleshooting/M3-4-dashboard-chief-only.md`

## 테스트 결과
- Java 17 + Docker + UTC JVM: **213 실행 / 213 성공 / 0 실패 / 0 오류 / 0 건너뜀**.
- Java 17 + Docker + 기본 JVM(Asia/Seoul): **213 실행 / 213 성공 / 0 실패 / 0 오류 / 0 건너뜀**.
- 실행 시간: UTC 3분 17초, 기본 시간대 3분 16초. 로그: `/private/tmp/m3-phase1-utc.log`, `/private/tmp/m3-phase1-default.log` (로컬 검증 산출물, 커밋 대상 아님).
- 첫 전체 실행에서는 실패 주입 예외가 Spring에 의해 변환되는 점을 테스트가 반영하지 않아 1개 실패했다. 예외 유형과 원인 검증을 수정한 뒤 M3BatchTest 4개와 전체 UTC 재실행이 통과했다. 운영 코드의 롤백 동작은 테스트에서 확인했다.

```sh
cd /Users/kimmini/sk-final-deepfake/mini-erp-system/backend
export JAVA_HOME=/Users/kimmini/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home
./mvnw test
./mvnw -Duser.timezone=UTC test
```
MariaDB 10.11·Redis(기존 초대/인증 테스트 용도)는 Testcontainers가 관리한다. 로컬 개발 DB는 연결하지 않았다. 테스트에서는 app.batches.enabled=false이며 고정 Clock을 변경하고 같은 배치 서비스를 직접 호출한다.

## 재현 전후 수치
| 실험 | 수정 전 | 수정 후 |
|---|---|---|
| 과거 날짜+08:50 조작, 실제 HTTP, 10회 | 조작 10/10 | 조작 0/10, 서버 시각 10/10 |
| 동시 출근, 2스레드×10회 | V3 읽기 후 INSERT 구조에서 유니크 예외 10/10, 행 중복 0 | 새 API는 200·409 10/10, 행 중복 0 |
| 팀장의 관리자 요약 조회 | 기대 403 대신 200으로 회귀 테스트 실패 | 팀장·사원 403, 관리소장 200 |
| V3→V4 근태 이관 | 정상·지각·결근·휴가 4행 | 4행 보존, 재실행 마이그레이션 0개 |
| 결근 배치 반복 실행 | 실행 전 근태/사후 반차 사례 구성 | 2회 후 전체 행·기록 시각 동일 |

시각 조작은 실제 구 API를 운영 코드 수정 전에 호출해 확인했다. 동시성의 구 구조는 테스트용 SQL 모델이고 새 구조는 실제 API이다. 둘을 동일 HTTP 엔드포인트의 전후 측정이라고 주장하지 않는다. 세부 실험은 M3-2를 따른다.

## 명세와의 차이·결정 필요
- V4가 옛 컬럼을 지우고 KI-04를 1차에서 차단해야 하므로 구 쓰기·summary 라우트 삭제를 2차에서 1차로 앞당겼다. 최종 API 계약은 명세와 같다.
- 스냅샷 응답의 source/departmentName 보존을 위해 sched_source/sched_department_name을 추가했다. 출근 뒤 부서·근무시간 변경이 과거 응답을 바꾸지 않게 하기 위한 저장 정보다.
- Flyway SQL은 Clock 빈을 주입받지 못하므로 이관 순간의 날짜만 UTC_TIMESTAMP+9시간을 쓴다. 업무와 배치 시각은 주입 Clock을 사용한다.
- **결정 필요: 퇴사일이 없다.** 현재 RETIRED인 사람의 새 결근은 만들지 않지만 과거 특정 날짜의 재직 여부와 ‘해당 월에 재직한 퇴사자’ 조회를 정확히 복원할 수 없다. retired_date 추가·상태 변경 시 기록·기존 값 NULL 보존안을 질문했고 답변 전에는 사원 스키마/API를 바꾸지 않았다.

## 발견했지만 고치지 않은 사항·남은 위험
- EmployeeService.register에는 직급 기준 ‘기존 신규 사원 부여 정책’ GRANT가 남아 있다. M3의 입사일 기반 자동 부여와 함께 적용하면 추가 부여될 수 있다. 현재는 코드 확인이며 실제 이중 부여 재현 전이다. 2차 연차 정책 전환에서 기존 부여를 어떻게 취급할지 기록해야 한다.
- 배치 이력은 실행마다 쌓이며, 멱등 보장 대상은 근태 결과다. DB 자체가 불능이면 FAILED 이력도 저장할 수 없다.
- 관리소장 외 화면에서 admin-summary를 호출하면 403이다. 화면 수정은 하지 않았다.
- 2차의 월간 50×31 쿼리/EXPLAIN, 근태 정정 결재, 연차 부여 규칙 수치는 이번 1차 검증에 포함하지 않는다.
- frontend/, backend/md/, V1~V3, .claude/를 수정하지 않았고 병합·푸시하지 않았다.

> 후속 확정(2026-10-26): 퇴사일과 배치 대상 규칙은 사용자 답변·d689d15 명세대로 [M3-5](M3-5-retirement-window.md)에 반영했다. 2차 구현과 최종 검증은 [M3-10](M3-10-phase-two-results.md)을 따른다.
