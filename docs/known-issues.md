# 알려진 이슈

M0 이후 확인한 문제와 해결 기록이다. M2에서 KI-01·KI-02·KI-03·KI-06을 해결했다. 기존 대시보드의 별도 권한 경로는 KI-07로 남긴다. 단계별 검증과 최종 전체 결과는 docs/troubleshooting/M2-12-phase-two-results.md를 따른다.

| ID | 문제 | 처리 단계 | 상태 |
| --- | --- | --- | --- |
| KI-01 | 특근 요청 DTO 필드 불일치와 누락 검증 부재로 500 발생 | M2 — 특근 결재 양식 재구축 | 해결됨 — `ef2244e` 새 계약·테스트 복구, `617b7ca` 구 API 제거 |
| KI-02 | 연차·특근 전체 조회 및 결재 권한의 부서 조건 부재 | M1 — 데이터 범위 | 해결됨 — `18d2f69` M1 범위 검사, `617b7ca` 구 API·특근 단건 경로 제거 |
| KI-03 | 연차 승인과 연차 차감에 잠금·버전 검증 없음 | M2 — 결재 동시성 | 해결됨 — `0542307`, 재현 10/10 → 수정 후 0/10 중복 성공 |
| KI-04 | 출퇴근 시각을 요청 본문으로 받고, 본인이 근태 기록을 고칠 수 있음 | M3 — 근태 재구축 | 코드 확인 (M0 검토 중 발견) |
| KI-05 | JPA 자동 기록 시각(created_at·updated_at)이 서버 기본 시간대를 따름 | M1 — 공통 설정 | 해결됨 — `cbf71e0` (M1-7) |
| KI-06 | JDBC 날짜가 UTC 오프셋을 포함한 JSON으로 반환되어 화면 시각이 틀어짐 | M2 선행 수정 — 공통 조회 | 해결됨 — `6c39c97` SqlStore 날짜 타입 정규화 ([검증 기록](troubleshooting/M2-0-jdbc-local-date-time.md)) |
| KI-07 | 기존 대시보드 관리자 요약의 전체 휴가 대기 노출 | M4 — 대시보드 권한 계약 결정 | 미해결, M2 명세 밖 경로로 보고만 함 |

## KI-01 — 특근 신청 500 (M2, 해결됨)
- 해결 커밋: `ef2244e` (하루 단위 처리기·기존 비활성 테스트 복구), `617b7ca` (구 API 제거).
- 원인: v1 화면의 overtimeDate와 요청 DTO의 startDate/endDate 불일치, 누락 날짜 역참조.
- 해결: 새 결재 양식 본문은 overtimeDate/startTime/endTime/reason으로 통일한다. 형식·필수값은 400, 종료 시각 검사는 상신 때 처리한다.
- 검증: CoreApiIntegrationTest.approveOvertime_shouldReturnApprovedStatus, M2DocumentIntegrationTest.overtimeUsesOneDateAndTimesAndApproves. @Disabled는 제거됐으며 Testcontainers의 새 문서 API로 신청·승인한다. 캘린더도 새 API로 승인한 데이터로 검증한다.

## KI-02 — 기존 연차·특근 조회/결재 범위 (M2, 해결됨)
- 해결 커밋: `18d2f69` (M1 목록·승인·반려 부서 범위), `617b7ca` (M2 구 API·특근 단건 경로 제거).
- 구 /leave/all·my·신청·승인·반려·취소 및 /overtime/*는 더 이상 호출할 수 없다. /leave/policy만 유지하고 새 문서 상세·결정은 기안자/결재선/참조자/관리소장 계약에 따른다.
- 검증: M1RegressionTest의 구 경로 404, 타 부서 팀장 문서 조회/승인/반려 403 및 쓰기 흔적 없음. M2InboxTest는 본인 결재선·참조 기준 목록/건수를 확인한다.
- 기존 대시보드 요약이라는 별도 경로는 KI-07이다. 이 이슈 해결이 전체 앱의 모든 조회 경로를 안전하게 만들었다는 뜻은 아니다.

## KI-03 — 연차 승인 동시성 (M2, 해결됨)
- 해결 커밋: `0542307`.
- 실제 MariaDB에서 옛 무조건 저장 구조를 읽기 장벽으로 재현: 10회 중 10회 중복 성공. 새 문서 API의 동시 승인: 10회 중 0회 중복 성공, 매회 200 하나와 409 하나, 원장 USE 1회.
- 사용자 행 → 번호 행(상신) → 문서 행 잠금, 결재선 조건부 갱신, 문서 version으로 상태 전이를 보호한다. 결재·원장·앱 안 알림은 같은 트랜잭션이다.
- 승인 대 회수, 잔여 초과 동시 상신, 문서번호 동시 발급, 알림 저장 실패 롤백도 검증한다. 수치와 통제 조건은 [M2-4](troubleshooting/M2-4-document-concurrency.md), [M2-7](troubleshooting/M2-7-notification-transaction.md)를 따른다.

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

- 당시 남긴 특근 단건 조회 `GET /api/v1/overtime/{id}` 문제는 M2 `617b7ca`에서 경로를 삭제하여 해소했다.
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
- 위 표는 M1 리뷰 당시 기록이다. 당시 M2로 넘긴 KI-01·KI-03은 위 M2 결과대로 해결됐고, KI-04는 M3에 남긴다.
- 전체 테스트 및 수정 전 실패 확인 결과: [M1 리뷰 결과](troubleshooting/M1-19-review-results.md).

## KI-06 — JDBC 날짜 응답의 시간대 (M2 선행 수정, 해결됨)
- 증상: DB의 한국 시각 DATETIME을 Timestamp 그대로 응답해 UTC JVM에서 `21:07:29.213+00:00`처럼 반환한다. 화면이 한국 시간으로 바꾸면 9시간 차이가 난다.
- 원인: SqlStore의 queryForList 결과에 남아 있는 JDBC 날짜를 Jackson이 순간 값으로 직렬화한다. 저장 시각을 고친 KI-05와 별개다.
- 해결: SqlStore에서 Timestamp→LocalDateTime, java.sql.Date→LocalDate로 공통 변환한다. NULL과 나머지 값은 유지하며, 시간대 표시 없이 DB 지역 시각과 마이크로초 정밀도를 보존한다.
- 검증: SqlStoreDateTimeMariaDbTest. UTC JVM + MariaDB Testcontainers에서 GET /api/v1/users의 createdAt을 DB DATE_FORMAT 결과와 직접 비교한다. rows·one의 임의 시각 컬럼명, DATE, NULL도 확인한다. 신규 테스트 2개가 수정 전 실패했음을 확인했다.
- M2 적용: submittedAt·completedAt·이력 at·알림 createdAt 및 결재함 updatedAt은 같은 LocalDateTime 계약을 따른다. UTC JVM의 M2 문서·결재함·알림 API 테스트에서 시간대 없는 문자열을 검증한다.
- 해결 커밋: `6c39c97`. 수정 및 재현 기록: [M2-0](troubleshooting/M2-0-jdbc-local-date-time.md). 해결 코드·회귀 테스트·이슈 문서를 하나의 독립 커밋으로 묶었다.

## KI-07 — 대시보드 관리자 요약의 별도 조회 범위 (보고만 함)
- GET /api/v1/dashboard/admin-summary는 관리자/팀장 자격을 검사하지만, DashboardService.getAdminSummary의 pendingApprovalCount와 pendingApprovals는 전체 휴가 대기를 조회한다. 팀장 부서나 새 결재선 멤버십으로 제한하지 않는다.
- 사용자 지시에 따라 기존 대시보드가 새 테이블로 계속 동작하도록 보존했으며, 명세 밖 권한 우회 경로를 임의 수정하지 않았다. 허용 범위와 기존 휴가 전용 응답의 개편은 M4 전에 결정해야 한다.
- 이는 코드 확인 사항이다. 새 결재함에는 이 경로를 재사용하지 않으며 SQL에서 본인 결재선/기안/참조 조건을 적용한다.
