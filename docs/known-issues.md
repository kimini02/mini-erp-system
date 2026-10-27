# 알려진 이슈

M0 이후 확인한 문제와 해결 기록이다. M2에서 KI-01·KI-02·KI-03·KI-06을 해결했다. M3에서 KI-04·KI-07을 해결했다. 2차 결과는 [M3 2차 보고서](troubleshooting/M3-10-phase-two-results.md), 보완된 결근·정정·부여 정책은 [M3 3차 보고서](troubleshooting/M3-17-phase-three-results.md)를 따른다. M2 결과는 docs/troubleshooting/M2-12-phase-two-results.md, M3 1차 결과는 [M3 1차 보고서](troubleshooting/M3-0-phase-one-results.md)를 따른다.

| ID | 문제 | 처리 단계 | 상태 |
| --- | --- | --- | --- |
| KI-01 | 특근 요청 DTO 필드 불일치와 누락 검증 부재로 500 발생 | M2 — 특근 결재 양식 재구축 | 해결됨 — `ef2244e` 새 계약·테스트 복구, `617b7ca` 구 API 제거 |
| KI-02 | 연차·특근 전체 조회 및 결재 권한의 부서 조건 부재 | M1 — 데이터 범위 | 해결됨 — `18d2f69` M1 범위 검사, `617b7ca` 구 API·특근 단건 경로 제거 |
| KI-03 | 연차 승인과 연차 차감에 잠금·버전 검증 없음 | M2 — 결재 동시성 | 해결됨 — `0542307`, 재현 10/10 → 수정 후 0/10 중복 성공 |
| KI-04 | 출퇴근 시각을 요청 본문으로 받고, 본인이 근태 기록을 고칠 수 있음 | M3 — 근태 재구축 | 해결됨 — `7ab4a07`, 서버 Clock·구 직접 수정 경로 삭제 |
| KI-05 | JPA 자동 기록 시각(created_at·updated_at)이 서버 기본 시간대를 따름 | M1 — 공통 설정 | 해결됨 — `cbf71e0` (M1-7) |
| KI-06 | JDBC 날짜가 UTC 오프셋을 포함한 JSON으로 반환되어 화면 시각이 틀어짐 | M2 선행 수정 — 공통 조회 | 해결됨 — `6c39c97` SqlStore 날짜 타입 정규화 ([검증 기록](troubleshooting/M2-0-jdbc-local-date-time.md)) |
| KI-07 | 기존 대시보드 관리자 요약의 전체 휴가 대기 노출 | M3 — 관리소장 전용 요약 | 해결됨 — `a2254af`, 관리소장 외 403 |

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

## KI-04 — 근태 시각 조작 가능 (M3 1차, 해결됨)
- 해결 커밋: `7ab4a07`.
- 수정 전 실제 구 출근 API로 과거 날짜·08:50을 보내는 조작 10회 중 10회 성공을 먼저 확인했다. 새 출근 API에서는 같은 조작 입력 10회 모두 무시하고 Asia/Seoul Clock 시각을 기록한다.
- 본인의 직접 PUT 및 구 check-in/check-out/summary 경로를 삭제했다. 정정은 `7aac727`의 ATTENDANCE_FIX 결재 처리기로 연결했다. `66a52fc`에서 구 DTO·호환 코드를 정리하고 구 네 경로 404를 검증했다.
- 사용자 행 잠금으로 동시 출근 10회 모두 200 하나·409 하나, 날짜별 기록 1행을 검증했다.
- 검증: M3SecurityTest, M3AttendanceTest. 상세: [M3-2](troubleshooting/M3-2-server-clock.md).

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
- 위 표는 M1 리뷰 당시 기록이다. 당시 M2로 넘긴 KI-01·KI-03은 위 M2 결과대로 해결됐고, KI-04도 M3 1차에서 해결됐다.
- 전체 테스트 및 수정 전 실패 확인 결과: [M1 리뷰 결과](troubleshooting/M1-19-review-results.md).

## KI-06 — JDBC 날짜 응답의 시간대 (M2 선행 수정, 해결됨)
- 증상: DB의 한국 시각 DATETIME을 Timestamp 그대로 응답해 UTC JVM에서 `21:07:29.213+00:00`처럼 반환한다. 화면이 한국 시간으로 바꾸면 9시간 차이가 난다.
- 원인: SqlStore의 queryForList 결과에 남아 있는 JDBC 날짜를 Jackson이 순간 값으로 직렬화한다. 저장 시각을 고친 KI-05와 별개다.
- 해결: SqlStore에서 Timestamp→LocalDateTime, java.sql.Date→LocalDate로 공통 변환한다. NULL과 나머지 값은 유지하며, 시간대 표시 없이 DB 지역 시각과 마이크로초 정밀도를 보존한다.
- 검증: SqlStoreDateTimeMariaDbTest. UTC JVM + MariaDB Testcontainers에서 GET /api/v1/users의 createdAt을 DB DATE_FORMAT 결과와 직접 비교한다. rows·one의 임의 시각 컬럼명, DATE, NULL도 확인한다. 신규 테스트 2개가 수정 전 실패했음을 확인했다.
- M2 적용: submittedAt·completedAt·이력 at·알림 createdAt 및 결재함 updatedAt은 같은 LocalDateTime 계약을 따른다. UTC JVM의 M2 문서·결재함·알림 API 테스트에서 시간대 없는 문자열을 검증한다.
- 해결 커밋: `6c39c97`. 수정 및 재현 기록: [M2-0](troubleshooting/M2-0-jdbc-local-date-time.md). 해결 코드·회귀 테스트·이슈 문서를 하나의 독립 커밋으로 묶었다.

## KI-07 — 대시보드 관리자 요약의 별도 조회 범위 (M3 1차, 해결됨)
- 해결 커밋: `a2254af`.
- M3 명세가 관리소장 전용으로 확정하여 GET /api/v1/dashboard/admin-summary는 CHIEF가 계산된 ADMIN만 허용한다. 일반 사원·팀장은 403이다.
- 수정 전 팀장 실제 API 회귀 테스트가 기대 403 대신 200으로 실패했고, 수정 후 M3DashboardTest에서 사원·팀장 403, 관리소장 200을 확인한다.
- 다른 dashboard 경로와 응답 재설계는 변경하지 않았다. [M3-4](troubleshooting/M3-4-dashboard-chief-only.md).

## M3에서 확인한 미수정 항목·정책 결정
- 코드 확인: 기존 사용자 호환 필드 remainingAnnualLeave와 User 연차 Formula는 원장 전체 연도를 합산한다. 새 연차 API는 연도별이다. 사용자 지시대로 이번 수정에서는 유지한다. 프론트는 /leave/balance로 전환했다고 전달받았으며, 호환 필드 제거는 별도 작업이다.
- 부여 정산 결정 반영 완료: `f73cc0e`. 실행 연도만 소급하며, 그해 키 없는 GRANT가 있으면 자동 부여를 전부 건너뛰고 다음 해는 규칙대로 계산한다. 기존 원장은 삭제·상계하지 않는다. [M3-14](troubleshooting/M3-14-current-year-legacy-grants.md).
- 결정 필요: 상태 변경 이력과 과거 근무시간 이력. 상태·계정 활성화는 배치 시점 값으로 판단하며 과거 기간을 추정하지 않았다.
- 근거와 영향은 [M3 최종 보고서](troubleshooting/M3-10-phase-two-results.md)의 결정 필요·미수정 항목을 따른다.

## M3 3차 리뷰 수정

| 항목 | 해결 커밋 | 검증 문서 |
| --- | --- | --- |
| 퇴사일 없는 RETIRED·초대 대기 사원의 신규 결근 제외, 기존 행 재판정 유지 | `b130515` | [M3-11](troubleshooting/M3-11-absence-eligibility.md) |
| 현재 기록과 합친 정정 시각을 상신·승인 모두 검사 | `9da9d04` | [M3-12](troubleshooting/M3-12-correction-submit-validation.md) |
| 미래 날짜 연차 부여 실행 400 | `bd3879e` | [M3-13](troubleshooting/M3-13-grant-run-date.md) |
| 현재 연도 소급·옛 GRANT 인정 및 건너뜀 구분 | `f73cc0e` | [M3-14](troubleshooting/M3-14-current-year-legacy-grants.md) |
| 실행 API에서 실제 저장한 batch_runs 한 건 반환 | `6d1d062` | [M3-15](troubleshooting/M3-15-batch-run-response.md) |
| CSV 날짜 칸 한글 표시·비근태 칸 빈칸 | `315fcce` | [M3-16](troubleshooting/M3-16-csv-korean-labels.md) |

이미 잘못 생성된 운영 근태·자동 부여 행을 일괄 삭제하는 작업은 수행하지 않았다. 개발 DB는 사용하지 않았으며, 사용자별 기록 정리는 확인 후 별도 결정이 필요하다. 전체 테스트와 수정 전 실패 근거는 [M3 3차 결과](troubleshooting/M3-17-phase-three-results.md)를 따른다.
