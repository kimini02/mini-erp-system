# 알려진 이슈

M0 보고서에 기록된 문제 중 M0-4 이후에도 남는 항목이다. 날짜 고정에 따른 연차 테스트 5개·업무 테스트 1개의 실패는 M0-4에서 해결했다. M1에서 KI-02의 지정 경로와 KI-05를 수정했다. M1의 전체 이관 완료 여부는 M1 보고서를 따르며 나머지 문제는 해당 단계에 남긴다.

| ID | 문제 | 처리 단계 | 상태 |
| --- | --- | --- | --- |
| KI-01 | 특근 요청 DTO 필드 불일치와 누락 검증 부재로 500 발생 | M2 — 특근 결재 양식 재구축 | 확인됨, 해당 통합 테스트 1개 임시 비활성화 |
| KI-02 | 연차·특근 전체 조회 및 결재 권한의 부서 조건 부재 | M1 — 데이터 범위 | 부분 해결 — 특근 단건 조회는 M2에서 기존 API 삭제로 해소 (`18d2f69`: 목록·승인·반려) |
| KI-03 | 연차 승인과 연차 차감에 잠금·버전 검증 없음 | M2 — 결재 동시성 | 코드상 위험, 동시 실행 재현 필요 |
| KI-04 | 출퇴근 시각을 요청 본문으로 받고, 본인이 근태 기록을 고칠 수 있음 | M3 — 근태 재구축 | 코드 확인 (M0 검토 중 발견) |
| KI-05 | JPA 자동 기록 시각(created_at·updated_at)이 서버 기본 시간대를 따름 | M1 — 공통 설정 | 해결됨 — `cbf71e0` (M1-7) |
| KI-06 | JDBC 날짜가 UTC 오프셋을 포함한 JSON으로 반환되어 화면 시각이 틀어짐 | M2 선행 수정 — 공통 조회 | 해결됨 — SqlStore 날짜 타입 정규화 ([검증 기록](troubleshooting/M2-0-jdbc-local-date-time.md)) |

## KI-01 — 특근 신청 500 (M2)
- 증상: overtimeDate와 근무 시간을 보내면 정상 신청 대신 500이 반환된다.
- 근거: `OvertimeRequestDto`는 startDate/endDate를 받으며 `OvertimeService.requestOvertime`은 누락된 날짜를 검증하기 전에 역참조한다. 기존 `CoreApiIntegrationTest.approveOvertime_shouldReturnApprovedStatus`에서 재현된다.
- 임시 조치: 해당 테스트에 `@Disabled("M2에서 수정 — docs/known-issues.md 참고")`를 붙였다. 요청 필드와 기대 성공 결과는 남겨 두었다. 운영 DTO·검증 로직은 수정하지 않았다.
- 복구 기준: M2의 특근 결재 양식 계약에 맞춰 화면·요청·서버를 일치시키고, 누락/잘못된 날짜에 400을 반환하는 검증을 추가한다. 테스트의 @Disabled를 제거하고 정상 신청·승인 및 잘못된 입력을 모두 확인한다.
- 캘린더 조회 테스트는 승인된 특근 엔티티를 직접 준비해 계속 실행한다. 특근 신청 버그가 해결되었다는 의미는 아니다.

## KI-02 — 부서별 데이터 범위 없음 (부분 해결)
- 해결 커밋: `18d2f69`. SQL 데이터 범위와 승인·반려의 공통 검사를 추가했다. `M1RegressionTest`에서 타 부서 차단·하위 부서 허용·관리소장 전체 목록을 확인한다. 특근 단건 조회는 아래 잔여 문제를 참고한다.
- 증상: 팀장이 다른 부서의 연차·특근 신청까지 조회하거나 처리할 수 있는 경로가 있다.
- 근거: `ApprovalService.getAllLeaveRequestList`, `OvertimeService.getAllOvertimeRequestList`의 전체 조회 및 `AccessPolicy.validateApprovalHierarchy`의 역할 판정에는 소속 부서 제한이 없다.
- 해결 방향: M1의 조직·역할과 데이터 접근 범위 정책을 정한 뒤 서버 조회와 개별 승인·반려 권한 검증에 함께 반영한다. 화면 필터만으로 제한하지 않는다.
- 완료한 검증: M1RegressionTest가 부서 범위 밖 목록·승인·반려 차단을 확인한다. 남은 특근 단건 조회는 M2에서 기존 API 삭제로 해소한다.

## KI-03 — 연차 승인 동시성 (M2)
- 증상/위험: 같은 신청 또는 같은 사원의 여러 신청을 동시에 승인하면 처리 상태나 잔여 연차 갱신이 충돌할 수 있다.
- 근거: `ApprovalService.approveLeaveRequest`는 신청 상태를 바꾸고 `User.deductAnnualLeave`를 호출하지만 해당 엔티티에 @Version이 없고 조회에도 명시적 잠금이 없다. 실제 동시 실행 결과는 아직 재현하지 않았다.
- 해결 방향: M2에서 실제 MariaDB로 경쟁 상황을 재현한 뒤 결재 상태 전이·연차 차감의 원자성과 중복 방지를 설계한다. 낙관적/비관적 잠금 선택과 재시도 정책을 함께 결정한다.
- 완료 검증: 공통 `MariaDbIntegrationTest` 환경에서 동시 승인 후 처리 횟수와 잔여 연차의 일관성을 검증한다.

## KI-04 — 근태 시각 조작 가능 (M3)
- 증상: 9시 20분에 출근해도 요청에 `"clockInTime": "08:50"`을 보내면 정상 출근으로 기록된다. 지난 날짜(`workDate`)로 출근을 찍을 수도 있다. 또 `PUT /api/v1/attendance?workDate=…`로 본인이 아무 날짜의 출퇴근 시각을 결재 없이 고칠 수 있다.
- 근거: `CheckInRequestDto`(workDate, clockInTime), `CheckOutRequestDto`(clockOutTime)를 `AttendanceService.checkIn`·`checkOut`이 그대로 저장한다. `updateFullAttendance`는 본인 확인 외의 권한·승인 검사가 없다.
- 해결 방향: 출퇴근 시각과 날짜는 서버 시계(`Clock`, Asia/Seoul)로만 정한다. 요청 본문에서 시각·날짜를 받지 않는다. 기록 정정은 본인이 직접 고치지 않고 정정 요청 → 승인 절차로 분리한다(1차 범위 밖이면 정정 API를 막는다).
- 완료 검증: 요청에 시각을 넣어도 무시되고 서버 시각으로 기록되는지, 본인 정정 API가 403인지 통합 테스트한다.

## KI-05 — 자동 기록 시각의 시간대 (M1, 해결됨)
- 해결 커밋: `cbf71e0`. DateTimeProvider에 Clock을 연결하고 중복 시각 생성자를 제거했다. `AuditClockMariaDbTest`가 JVM UTC에서 실제 MariaDB 열 값을 검증한다.
- 증상/위험: 서버(JVM) 시간대가 UTC이면 `created_at`·`updated_at`이 한국 시간보다 9시간 이르게 저장된다. M2부터 결재 이력·알림 시각에 그대로 드러난다.
- 근거: M0-4에서 업무 규칙의 날짜는 `Clock`(Asia/Seoul)로 바꿨지만, `JpaAuditingConfig`의 `@EnableJpaAuditing`은 기본 시간 공급자(시스템 기본 시간대)를 쓴다.
- 해결 방향: JPA 자동 기록도 같은 `Clock`을 쓰도록 `DateTimeProvider`를 등록한다. 운영 실행 옵션에 시간대를 명시하는 것은 보조 수단으로만 쓴다.
- 완료 검증: JVM 시간대를 UTC로 두고, 고정 시계 기준 시각으로 `created_at`이 저장되는지 MariaDB 테스트로 확인한다.

이전 결과는 `troubleshooting/M0-2-mariadb-tests.md`, M0-4 검증은 `troubleshooting/M0-4-clock-dependent-tests.md`를 참고한다.

## M1에서 발견한 추가 문제와 처리 상태

- 특근 단건 조회 `GET /api/v1/overtime/{id}`: 일반 사원에게는 본인 확인을 하지만 팀장에게는 부서 범위를 적용하지 않는다. M1의 목록·승인·반려 범위는 수정했으며, 특근 단건 조회는 M2에서 기존 API 삭제로 해소한다.
- 인증번호 로그 노출은 이번 리뷰에서 해결했다: `9750459`. MailServiceLoggingTest로 메일 비활성 시 인증번호가 로그에 없고 발송기도 호출하지 않음을 확인한다.

## M1 추가 리뷰 수정 (2026-10-15)

| 항목 | 결과 | 커밋 | 검증 문서 |
| --- | --- | --- | --- |
| 권한 있는 계정 이메일·초대 | 대상에게 EMPLOYEE 외 역할이 있으면 ROLE U 필요. 거절 시 DB·메일·토큰 유지 | `ea724bb` | [M1-13](troubleshooting/M1-13-privileged-account-contact.md) |
| 초대 토큰 | 사원당 한 개. 재발송 시 교체, 이메일 변경은 커밋 뒤 폐기. 롤백 시 기존 링크 유지 | `6779ffe` | [M1-14](troubleshooting/M1-14-invitation-after-commit.md) |
| 공개 인증 경로 | /api/v1/auth/**에서 Bearer 검사 제외. 로그인·초대 자격 증명은 그대로 검사 | `58eee3b` | [M1-15](troubleshooting/M1-15-public-auth-filter.md) |
| 부서장 불변식 | 기존 공통 트리 잠금과 사용자·부서장 잠금 읽기를 재검증, 18회 위반 0 | `ee0835e` (검증 문서) | [M1-16](troubleshooting/M1-16-head-lock-review.md) |
| 미등록 부서 이관 | 04 등 모르는 부서는 회사 루트로 보존하고 건수 로그 기록 | `2c482e0` | [M1-17](troubleshooting/M1-17-unknown-department-migration.md) |
| 메일·최초 관리자 | 인증번호 로그 제거, 신규 최초 관리자 부서 id=1 | `9750459` | [M1-18](troubleshooting/M1-18-mail-log-bootstrap-root.md) |

- V2는 배포 전 변경 허용에 따라 수정했으므로 체크섬이 바뀐다. 이미 V2가 적용된 DB는 Flyway validate 불일치가 발생한다. 로컬 MariaDB의 적용 여부는 인증 거절로 미확인이다. DB·이력 repair는 수행하지 않았다.
- DB 이메일 변경과 Redis 폐기는 분산 트랜잭션이 아니다. 커밋 뒤 삭제가 지연되거나 실패해도 토큰의 발급 이메일과 현재 이메일 검증으로 이전 주소의 링크를 거부한다. 이메일 정보가 없는 이전 형식 링크는 재발송해야 한다.
- KI-01(특근 DTO, 기존 비활성 테스트), KI-03(연차 승인 동시성)은 M2에 남긴다. KI-04는 M3에 남긴다. 이번 작업에서 M2를 구현하지 않았다.
- 전체 테스트 및 수정 전 실패 확인 결과: [M1 리뷰 결과](troubleshooting/M1-19-review-results.md).

## KI-06 — JDBC 날짜 응답의 시간대 (M2 선행 수정, 해결됨)
- 증상: DB의 한국 시각 DATETIME을 Timestamp 그대로 응답해 UTC JVM에서 `21:07:29.213+00:00`처럼 반환한다. 화면이 한국 시간으로 바꾸면 9시간 차이가 난다.
- 원인: SqlStore의 queryForList 결과에 남아 있는 JDBC 날짜를 Jackson이 순간 값으로 직렬화한다. 저장 시각을 고친 KI-05와 별개다.
- 해결: SqlStore에서 Timestamp→LocalDateTime, java.sql.Date→LocalDate로 공통 변환한다. NULL과 나머지 값은 유지하며, 시간대 표시 없이 DB 지역 시각과 마이크로초 정밀도를 보존한다.
- 검증: SqlStoreDateTimeMariaDbTest. UTC JVM + MariaDB Testcontainers에서 GET /api/v1/users의 createdAt을 DB DATE_FORMAT 결과와 직접 비교한다. rows·one의 임의 시각 컬럼명, DATE, NULL도 확인한다. 신규 테스트 2개가 수정 전 실패했음을 확인했다.
- M2 적용 기준: submittedAt·completedAt·이력 at·알림 createdAt은 같은 LocalDateTime 계약을 따른다. M2 API 구현 시 실제 응답 검증을 추가한다.
- 수정 및 재현 기록: [M2-0](troubleshooting/M2-0-jdbc-local-date-time.md). 해결 코드·회귀 테스트·이슈 문서를 하나의 독립 커밋으로 묶었다.
