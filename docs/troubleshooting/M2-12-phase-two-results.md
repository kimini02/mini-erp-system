# M2 2차 완료 보고 — 2026-10-24

## 결과
`feat/m2-approval`에서 2차 API와 사용자 추가 요청 12개를 반영했다. Java 17.0.16, JVM UTC, Docker + MariaDB 10.11 Testcontainers에서 전체 **199개 성공, 실패 0, 오류 0, 건너뜀 0**, Maven 종료 코드 0 / BUILD SUCCESS. 실행 시간 6분 41초. Surefire XML 합계와 각 실행의 java.version=17.0.16, user.timezone=UTC도 대조했다.

DB가 필요한 검증은 모두 Testcontainers를 사용했다. 로컬 개발 DB에 접속하거나 이관하지 않았다. frontend, backend/md, V1·V2, .claude, pom.xml은 변경하지 않았다. 새 라이브러리·병합·푸시 없음. 기존 미추적 .claude/는 그대로 남긴다.

## 작업별 커밋·테스트·문서

| 커밋 | 작업 | 테스트 | troubleshooting |
| --- | --- | --- | --- |
| `2e735b6` | 1차 리뷰 보완 | M2ReviewTest(5), M2DocumentIntegrationTest(11) | [M2-10-review-contract.md](M2-10-review-contract.md) |
| `6606c56` | 사원 선택 검색 | M2LookupTest(1) | [M2-11-user-lookup.md](M2-11-user-lookup.md) |
| `8acda13` | 알림 목록·읽음 | M2NotificationTest(1), M2DocumentIntegrationTest의 롤백 2방향 | [M2-7-notification-transaction.md](M2-7-notification-transaction.md) |
| `617b7ca` | 구 API 제거·호환 | M1RegressionTest(4), MariaDbMigrationTest(3), M1AcceptanceTest(37), CoreApiIntegrationTest(9), ClockBehaviorTest(3) | [M2-9-legacy-api-removal.md](M2-9-legacy-api-removal.md) |
| `4c6d079` | 연차 요약·원장·미리 계산 | M2LeaveQueryTest(2) | [M2-6-leave-queries.md](M2-6-leave-queries.md) |
| `788ab80` | 양식 관리 | M2FormApiTest(4) | [M2-8-form-management.md](M2-8-form-management.md) |
| `1d371a8` | 결재함·1만 건 성능 | M2InboxTest(1), M2InboxPerformanceTest(1) | [M2-5-inbox-query.md](M2-5-inbox-query.md) |

최종 검증 기록 커밋에는 M2DataMigrationTest의 이관 연도·memo 단언, 알려진 이슈 상태, 확정 정책 반영 및 M0-4 문서의 diff 기호 정리를 포함한다. 실행된 테스트 수를 늘리지 않고 기존 이관 테스트를 강화했다.

## 사용자 추가 요청 반영

1. v1 연차 수치는 이관 실행 연도이며 memo는 `v1 이관`. M2DataMigrationTest가 DB의 한국 현재 연도와 대조한다. V3 SQL을 다시 쓰지 않았다.
2. 연도를 넘는 휴가는 임시 저장할 수 있지만 상신은 422 `LEAVE_CROSS_YEAR`; 미리 계산은 같은 메시지를 error로 반환한다.
3. 역할 담당자는 숫자 users.id 오름차순 첫 ACTIVE. 로그인 아이디가 반대 순서인 fixture로 확인했다.
4. 기존 dashboard 3개와 calendar/events를 새 결재 데이터로 확인했다. 구 연차·특근 API는 삭제하고 leave/policy만 유지한다.
5. 참조 공석은 SKIPPED/`참조할 사람이 없어 건너뜁니다.`로 표시, 상신 성공·참조 0명. 기안자 본인은 참조에서 제외한다.
6. 두 미리보기는 kind, result.department, references를 포함하며 FALLBACK 사유에 출발/대체 부서 이름을 넣는다.
7. 상세 line은 APPROVER만, references는 참조만. SKIPPED의 skipReason은 SELF/DUPLICATE.
8. 병가·경조 차감 0일은 원장에 쓰지 않는다. 기간과 예약 해제 사유를 읽을 수 있는 memo로 기록한다.
9. 양식 목록/상세는 요청한 code/name/shortName/prefix/description 및 옵션/단계 계약을 제공한다. PATCH는 `{ "skipSelf": false }`처럼 바꿀 옵션 필드만 보낸다. 응답은 상세 형태다.
10. 로그인 전용 users/lookup은 ACTIVE의 id/name/title/department/departmentId만, 이름·로그인 아이디 앞부분 검색, 이름순, size 최대 100. 이메일·역할은 제외한다.
11. 결재함에 formCode/짧은 formName/updatedAt 및 요청한 나머지 필드 포함. drafts는 updatedAt DESC, id DESC.
12. notifications/unread-count는 `{ "count": n }`을 공통 ApiResponse.data에 담는다.

## 수정 전 실패 확인

| 회귀 | 수정 전 | 수정 후 |
| --- | --- | --- |
| M2ReviewTest 5개 | 실패 5: 빈 참조 ERROR, 응답 누락, 연도 초과 200, 0일 원장 1행 | 5개 통과 |
| 양식만 참조하는 역할 삭제 | 기대 409, 실제 500 | ROLE_IN_USE 409 |
| 구 읽기·쓰기 경로 삭제 후 상태 | 테스트 2개에서 기대 404, 실제 500 | RESOURCE_NOT_FOUND 404 |

새 API의 최초 검증 중 잘못된 테스트 fixture(공휴일 필수 substitute 누락)와 오류 응답 경로(error.code)도 바로잡았다. 이를 운영 버그의 수정 전 재현 수치에 포함하지 않았다.

## 동시성·성능 수치

| 실험 | 이전 구조/비교군 | 최종 구조 |
| --- | --- | --- |
| 같은 문서 동시 승인, 10회 | 옛 무조건 갱신 구조 10/10 중복 성공 | 0/10 중복, 각 회 200 하나·409 하나, USE 1회 |
| 충돌에서 패한 요청의 추가 알림 | — | 0/10 |
| 승인 대 회수 | — | 하나만 성공 |
| 잔여 2일, 두 건 동시 2일 상신 | — | 하나만 성공 |
| 서로 다른 8명의 같은 양식 상신 | — | 문서번호 중복 0 |
| 결재함 100건 → 1만 건 페이지 조회 | — | 양쪽 모두 SQL 3회 |
| 결재함 1만 건, 3회 예열·9회 중앙값 | 인덱스 제외 비교 SQL 20.726ms | 실제 SQL 6.718ms |

결재함 EXPLAIN: 결재선의 전체 탐색(type ALL, 추정 9,824행)에서 `idx_approval_lines_turn`의 ref 탐색(추정 100행)으로 바뀐다. 생산 SQL에 인덱스 힌트를 강제하지 않았다. 부분 실행 수치는 11.494ms → 5.250ms였으며 환경 부하에 따라 달라진다. 운영 SLA나 실사용 트래픽 성능을 뜻하지 않는다.

동시 승인 수정 전 실험은 두 요청이 같은 상태를 읽도록 장벽을 두어 경쟁을 확실히 만들었다. 수정 후에는 사용자 잠금 때문에 같은 읽기 장벽을 둘 수 없어 요청 시작을 동시에 맞춘다. 입력·동시 요청 2개·10회 반복·중복 성공 판정은 같으며 자연 발생 확률을 비교한 수치는 아니다.

## 명세와의 차이·결정 필요

- 사용자 추가 확정(연도 경계·참조 처리·응답 모양·lookup)을 제외하고 임의로 API를 늘리거나 계약을 바꾸지 않았다. backend/md는 수정하지 않았다.
- 앱 안 알림은 결재/원장과 같은 트랜잭션이다. **명세와 작업 지시가 달라서 명세를 따름.** 사용자 확인대로 AFTER_COMMIT은 외부 메일·Redis에만 해당하며 새 외부 발송은 만들지 않았다.
- 기본 양식의 shortName/description은 고정 메타데이터다. 편집 API가 없는 항목을 저장/수정하는 기능은 만들지 않았다.
- v1에서 결재자가 없었던 진행 중 문서는 명세대로 담당자 없는 상태로 보존한다. 자동 승인하지 않는다. 운영 이관 시 해당 문서의 기안자가 회수·재기안할지 정해야 한다.
- 기존 대시보드 요약의 권한 범위는 아래 KI-07에 남긴다. 새 결재함의 권한과 같다고 간주하면 안 된다.

## 발견했지만 고치지 않은 문제·남은 위험

- **KI-07:** dashboard/admin-summary의 팀장 조회에서도 pendingApprovals/pendingApprovalCount는 전체 휴가 대기다. 새 결재함은 본인 결재선 범위지만 이 기존 경로는 다르다. 사용자 지시에 따라 고치지 않고 M4 권한 계약 결정 대상으로 남겼다.
- **KI-04:** 요청 본문 시각으로 근태 기록·직접 수정이 가능한 기존 문제는 M3 범위로 유지했다.
- 기존 사용자 응답의 연차 호환 숫자는 여러 연도 합계다. 새 leave/balance는 선택 연도만 집계한다. M3에서 초기 부여/자동 부여 정책을 새로 정해야 한다.
- 알림 저장 장애는 결재 전체를 롤백한다. 이는 누락 방지를 위한 의도된 선택이다. 향후 외부 발송의 재시도/누락 처리는 별도 설계다.
- 같은 사원 상신·결재 및 같은 양식/연도 번호 발급은 직렬화된다. 측정 없이 캐시·Redis 잠금·새 라이브러리를 추가하지 않았다.

## 실행 방법

검증 명령(저장소 backend에서, Java 17 + Docker 실행 상태):

```sh
./mvnw -Duser.timezone=UTC test
```

날짜를 바꾸는 기존 검증 지원: `-Dtest.clock.instant=2026-12-31T15:00:00Z`를 추가하면 한국 기준 2027-01-01이다. 이번 전체 결과는 기본 고정 시계로 실행했으며, ClockBehaviorTest는 기본 시각과 2027-01-01 두 시각을 모두 확인한다.

기존 M1 DB 이관 순서(안내만, 실행하지 않음): 앱 정지 → 백업 → V1/V2 성공 이력/체크섬 확인 → 백업 복제본에서 V3 검증 → 기존 환경 설정으로 앱 시작 시 Flyway V3 실행 → ddl-auto=validate 및 사원별 잔여/진행 문서 확인. V1/V2 변경·repair나 DB 초기화를 하지 않는다. 단일 이관이므로 앱/DB의 버전을 함께 전환한다.

새 DB: 빈 MariaDB 10.11 스키마와 사용자 준비 → README의 로컬 설정 예제를 사용해 DB_URL/DB_USERNAME/DB_PASSWORD/JWT_SECRET 및 기존 Redis 설정 주입 → 최초 관리자 환경변수 4개 준비 → 앱 시작 시 V1→V2→V3 및 validate → 관리자 최초 비밀번호 변경·부서장 지정. 부서장 공석은 422이며 자동 승인하지 않는다. 비밀값을 문서/로그/커밋에 넣지 않는다.

화면 연결·로컬 개발 DB 실제 이관·배포·병합·푸시는 범위 밖이거나 금지된 작업으로 실행하지 않았다. 요청한 백엔드 구현·Testcontainers 검증은 남김없이 완료했다.

## 명세 9장 완료 기준 매핑

| 번호 | 검증 |
| --- | --- |
| 1 동시 승인 | M2LegacyApprovalRaceTest + M2DocumentIntegrationTest.concurrentApprovalHasOne409AndNoNotificationFromLosingRequestInTenTrials |
| 2 승인/회수 | M2DocumentIntegrationTest.concurrentApprovalAndWithdrawalHaveOnlyOneWinner |
| 3 잔여 초과 상신 | M2DocumentIntegrationTest.concurrentSubmissionsCannotSpendSameRemainingDays |
| 4 문서번호 | M2DocumentIntegrationTest.documentNumbersAreUniqueForEightDifferentDraftersSubmittingConcurrently |
| 5 결재선 네 경우 | M2DocumentIntegrationTest.lineRulesHandleNormalSelfFallbackAndDuplicateApprovers |
| 6 스냅샷 | M2DocumentIntegrationTest.missingHeadDoesNotAutoApproveAndSnapshotSurvivesHeadReplacement |
| 7 원장 불변식 | M2DocumentIntegrationTest.rejectWithdrawAndResubmitKeepLedgerInvariantAndOriginalDocument |
| 8 1만 건 쿼리 | M2InboxPerformanceTest.tenThousandDocumentsUseTurnIndexAndConstantQueries |
| 9 특근 복구 | CoreApiIntegrationTest.approveOvertime_shouldReturnApprovedStatus |

## 커밋별 변경 파일

### 2e735b6 — 1차 리뷰 보완

```text
M	backend/src/main/java/com/minierp/backend/domain/approval/service/ApprovalLineResolver.java
M	backend/src/main/java/com/minierp/backend/domain/approval/service/DocumentService.java
M	backend/src/main/java/com/minierp/backend/domain/leave/handler/LeaveFormHandler.java
M	backend/src/main/java/com/minierp/backend/global/exception/ErrorCode.java
A	backend/src/test/java/com/minierp/backend/integration/M2ReviewTest.java
A	backend/src/test/java/com/minierp/backend/support/ApprovalApiTestSupport.java
A	docs/troubleshooting/M2-10-review-contract.md
```

### 6606c56 — 사원 선택 검색

```text
M	backend/src/main/java/com/minierp/backend/domain/user/controller/UserController.java
A	backend/src/main/java/com/minierp/backend/domain/user/service/UserLookupService.java
A	backend/src/test/java/com/minierp/backend/integration/M2LookupTest.java
A	docs/troubleshooting/M2-11-user-lookup.md
```

### 8acda13 — 알림 목록·읽음

```text
A	backend/src/main/java/com/minierp/backend/domain/notification/controller/NotificationController.java
A	backend/src/main/java/com/minierp/backend/domain/notification/service/NotificationQueryService.java
A	backend/src/test/java/com/minierp/backend/integration/M2NotificationTest.java
M	docs/troubleshooting/M2-7-notification-transaction.md
```

### 617b7ca — 구 API 제거·호환

```text
D	backend/src/main/java/com/minierp/backend/domain/approval/dto/RejectRequestDto.java
D	backend/src/main/java/com/minierp/backend/domain/leave/controller/ApprovalController.java
A	backend/src/main/java/com/minierp/backend/domain/leave/controller/LeavePolicyController.java
D	backend/src/main/java/com/minierp/backend/domain/leave/dto/LeaveBalanceResponseDto.java
D	backend/src/main/java/com/minierp/backend/domain/leave/dto/LeaveRequestCreateDto.java
D	backend/src/main/java/com/minierp/backend/domain/leave/service/ApprovalService.java
D	backend/src/main/java/com/minierp/backend/domain/overtime/controller/OvertimeController.java
D	backend/src/main/java/com/minierp/backend/domain/overtime/dto/OvertimeRequestDto.java
D	backend/src/main/java/com/minierp/backend/domain/overtime/dto/OvertimeResponseDto.java
D	backend/src/main/java/com/minierp/backend/domain/overtime/service/OvertimeService.java
M	backend/src/main/java/com/minierp/backend/global/exception/GlobalExceptionHandler.java
D	backend/src/main/java/com/minierp/backend/global/permission/LegacyRequestScopeRepository.java
M	backend/src/test/java/com/minierp/backend/global/config/ClockBehaviorTest.java
M	backend/src/test/java/com/minierp/backend/integration/M1AcceptanceTest.java
M	backend/src/test/java/com/minierp/backend/integration/M1RegressionTest.java
M	backend/src/test/java/com/minierp/backend/integration/MariaDbMigrationTest.java
A	docs/troubleshooting/M2-9-legacy-api-removal.md
```

### 4c6d079 — 연차 요약·원장·미리 계산

```text
A	backend/src/main/java/com/minierp/backend/domain/leave/controller/LeaveQueryController.java
A	backend/src/main/java/com/minierp/backend/domain/leave/service/LeaveQueryService.java
A	backend/src/test/java/com/minierp/backend/integration/M2LeaveQueryTest.java
A	docs/troubleshooting/M2-6-leave-queries.md
```

### 788ab80 — 양식 관리

```text
A	backend/src/main/java/com/minierp/backend/domain/approval/controller/FormController.java
A	backend/src/main/java/com/minierp/backend/domain/approval/service/FormService.java
M	backend/src/main/java/com/minierp/backend/domain/permission/service/RoleService.java
A	backend/src/test/java/com/minierp/backend/integration/M2FormApiTest.java
A	docs/troubleshooting/M2-8-form-management.md
```

### 1d371a8 — 결재함·1만 건 성능

```text
A	backend/src/main/java/com/minierp/backend/domain/approval/controller/InboxController.java
A	backend/src/main/java/com/minierp/backend/domain/approval/repository/InboxRepository.java
A	backend/src/main/java/com/minierp/backend/domain/approval/service/InboxService.java
A	backend/src/test/java/com/minierp/backend/integration/M2InboxPerformanceTest.java
A	backend/src/test/java/com/minierp/backend/integration/M2InboxTest.java
A	docs/troubleshooting/M2-5-inbox-query.md
```

### 최종 검증·문서 기록

- backend/src/test/java/com/minierp/backend/integration/M2DataMigrationTest.java
- docs/known-issues.md
- docs/troubleshooting/M0-4-clock-dependent-tests.md
- docs/troubleshooting/M2-2-form-handlers.md
- docs/troubleshooting/M2-3-line-snapshots.md
- docs/troubleshooting/M2-9-legacy-api-removal.md
- docs/troubleshooting/M2-12-phase-two-results.md (이 문서)
