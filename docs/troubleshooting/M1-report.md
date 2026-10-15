# M1 최종 작업 보고서

완료일: 2026-10-15. 브랜치: `feat/m1-org-permission` (develop `73ebe93`에서 분기).

## 결과

사용자 결정대로 기존 비활성 계정은 RETIRED, 기존 입사일은 NULL로 이관하는 V2를 확정했다. 새 사원은 hireDate가 필수이며, 기존 사원의 입사일은 PATCH로 보정해도 아이디가 바뀌지 않는다. 사원 등록 시 EMPLOYEE 외 역할에는 ROLE U를 추가 검사하고, 재설정 인증번호 생성기를 SecureRandom으로 변경했다. M1 필수 검증 7개를 포함한 전체 테스트를 임시 이관 경로 없이 실행했다.

```bash
cd backend
./mvnw test
```

Java 17.0.16 + 실행 중인 Docker / MariaDB 10.11: **총 146개, 성공 145개, 실패 0, 오류 0, 건너뜀 1**. 건너뜀은 기존 `CoreApiIntegrationTest.approveOvertime_shouldReturnApprovedStatus` 한 개다. Flyway는 저장소의 V1/V2를 사용하고 Hibernate validate를 통과했다.

## 핵심 검증과 재현 전후

| 테스트 | 확인한 결과 |
| --- | --- |
| M1DataMigrationTest | V1 기존 3명 중 비활성 1명 → RETIRED 1명, 입사일 모두 NULL, 기존 아이디·연차·암호값·역할·직급 보존, 이관 로그 `migrated_inactive_to_retired=1` |
| MariaDbMigrationTest | 빈 DB V1 → V2 → validate, 외래키 20개·유니크 제약·재실행 무변경. 빈 DB 이관 로그 0 |
| M1AcceptanceTest.concurrentIdAllocationIsUniqueAndUnsafeMaxPlusOneCollides | 실제 스레드 MAX+1: 성공 1/중복 충돌 1. 행 잠금 적용 후 동시 등록 8건의 중복 0 |
| M1AcceptanceTest.organizationTreeAlwaysUsesTwoBusinessQueries | 실제 MariaDB Com_select 증가량이 부서 4개·16개 모두 2 |
| M1AcceptanceTest의 순환 이동·부서 범위·토큰 차단·마지막 관리소장·권한 즉시 반영 | 명세 9장의 나머지 5개 필수 검증 통과 |
| M1RegressionTest | 타 부서 연차·특근 목록/승인/반려 차단, 하위 부서 승인 허용, 관리소장 전체 목록, 사원 전체 조회 차단 |
| AuditClockMariaDbTest | JVM UTC에서도 실제 MariaDB created_at이 한국 고정 Clock과 같음. 수정 전 실패/수정 후 통과 |
| InitialAdminInitializerTest / InitialAdminMariaDbTest / PasswordPolicyTest | CHIEF·must_change_pw·암호화·길이/문자 정책·누락·재시작 중복 방지 |
| M1AcceptanceTest.employeeCreator* | 수정 전 권한 상승 4건이 201 → 수정 후 403. EMPLOYEE C만으로 EMPLOYEE 등록 201, ROLE U도 있으면 CHIEF 등록 201. 총 6건 |
| AuthVerificationCodeTest | 수정 전 SecureRandom 사용 검증 실패 → 수정 후 통과. 6자리 양 끝 경계 및 저장·메일 전달 확인 |
| M1AcceptanceTest의 hireDate 관련 테스트 | 수정 전 날짜 무시/잘못된 날짜 허용 4건 실패 → 수정 후 통과. 보정 시 아이디·시퀀스 유지, 잘못된 날짜 400, 본인 수정 403, 신규 등록 누락/null 400 |
| 추가 초대 검증 | 커밋 뒤 발송, 끔/실패/롤백, 일회성 링크, 서로 다른 링크의 동시 사용 시 첫 설정만 성공 |

이전 임시 V2 검증은 중간 결과이며, 위 결과가 최종 기본 명령 결과다. 테스트는 실제 개발 DB에 접속하거나 외부 메일을 보내지 않는다.

## 작업별 커밋·바뀐 파일·문서

각 작업은 독립 커밋으로 남겼다. M1-1은 이 보고서를 포함한 스키마 확정 커밋이며 최종 응답에 해당 해시를 제공한다.

### M1-1 — 스키마·이관 확정 (본 보고서 포함 커밋)

문서: [M1-1-schema-migration.md](M1-1-schema-migration.md)

- `README.md`
- `backend/src/main/resources/db/migration/V2__organization_permission.sql`
- `backend/src/main/java/com/minierp/backend/domain/user/entity/LoginIdSequence.java`
- `backend/src/test/java/com/minierp/backend/integration/M1DataMigrationTest.java`
- `backend/src/test/java/com/minierp/backend/integration/MariaDbMigrationTest.java`
- `docs/troubleshooting/M1-1-schema-migration.md`
- `docs/troubleshooting/M1-report.md`
- `backend/src/main/java/com/minierp/backend/domain/org/entity/Department.java`
- `backend/src/main/java/com/minierp/backend/domain/org/entity/Holiday.java`
- `backend/src/main/java/com/minierp/backend/domain/org/entity/Position.java`
- `backend/src/main/java/com/minierp/backend/domain/org/entity/WorkSchedule.java`
- `backend/src/main/java/com/minierp/backend/domain/permission/entity/Menu.java`
- `backend/src/main/java/com/minierp/backend/domain/permission/entity/Role.java`
- `backend/src/main/java/com/minierp/backend/domain/permission/entity/RolePermission.java`

### M1-2 — `1600900`

문서: [M1-2-role-compatibility.md](M1-2-role-compatibility.md)

- `backend/src/main/java/com/minierp/backend/domain/user/dto/UserResponseDto.java`
- `backend/src/main/java/com/minierp/backend/domain/user/entity/User.java`
- `backend/src/main/java/com/minierp/backend/domain/user/entity/UserStatus.java`
- `backend/src/test/java/com/minierp/backend/integration/CoreApiIntegrationTest.java`
- `backend/src/test/resources/application-integration.properties`
- `backend/src/test/resources/data-integration.sql`
- `docs/troubleshooting/M1-2-role-compatibility.md`

### M1-3 — `ff00ebb`

문서: [M1-3-permission-boundary.md](M1-3-permission-boundary.md)

- `backend/src/main/java/com/minierp/backend/global/config/WebConfig.java`
- `backend/src/main/java/com/minierp/backend/global/exception/BusinessException.java`
- `backend/src/main/java/com/minierp/backend/global/exception/ErrorCode.java`
- `backend/src/main/java/com/minierp/backend/global/exception/GlobalExceptionHandler.java`
- `backend/src/main/java/com/minierp/backend/global/permission/Action.java`
- `backend/src/main/java/com/minierp/backend/global/permission/DataScope.java`
- `backend/src/main/java/com/minierp/backend/global/permission/PermissionAccess.java`
- `backend/src/main/java/com/minierp/backend/global/permission/PermissionInterceptor.java`
- `backend/src/main/java/com/minierp/backend/global/permission/RequirePermission.java`
- `backend/src/main/java/com/minierp/backend/global/permission/ScopeFilter.java`
- `backend/src/main/java/com/minierp/backend/global/persistence/SqlStore.java`
- `backend/src/main/java/com/minierp/backend/global/security/JwtAuthenticationFilter.java`
- `backend/src/main/java/com/minierp/backend/global/security/JwtTokenProvider.java`
- `backend/src/main/java/com/minierp/backend/global/security/PasswordPolicy.java`
- `backend/src/test/java/com/minierp/backend/global/security/PasswordPolicyTest.java`
- `docs/troubleshooting/M1-3-permission-boundary.md`

### M1-4 — `e0ad41b`

문서: [M1-4-organization-api.md](M1-4-organization-api.md)

- `README.md`
- `backend/src/main/java/com/minierp/backend/domain/org/controller/OrgController.java`
- `backend/src/main/java/com/minierp/backend/domain/org/dto/OrgRequests.java`
- `backend/src/main/java/com/minierp/backend/domain/org/service/OrgService.java`
- `backend/src/main/java/com/minierp/backend/domain/permission/controller/RoleController.java`
- `backend/src/main/java/com/minierp/backend/domain/permission/dto/RoleRequests.java`
- `backend/src/main/java/com/minierp/backend/domain/permission/service/RoleService.java`
- `backend/src/main/java/com/minierp/backend/domain/user/controller/AuthController.java`
- `backend/src/main/java/com/minierp/backend/domain/user/controller/MeController.java`
- `backend/src/main/java/com/minierp/backend/domain/user/controller/UserController.java`
- `backend/src/main/java/com/minierp/backend/domain/user/dto/EmployeeRequests.java`
- `backend/src/main/java/com/minierp/backend/domain/user/dto/LoginResponseDto.java`
- `backend/src/main/java/com/minierp/backend/domain/user/dto/RegistrationResult.java`
- `backend/src/main/java/com/minierp/backend/domain/user/repository/EmployeeRepository.java`
- `backend/src/main/java/com/minierp/backend/domain/user/repository/UserRepository.java`
- `backend/src/main/java/com/minierp/backend/domain/user/service/AuthService.java`
- `backend/src/main/java/com/minierp/backend/domain/user/service/EmployeeService.java`
- `backend/src/main/java/com/minierp/backend/domain/user/service/InvitationService.java`
- `backend/src/main/java/com/minierp/backend/domain/user/service/UserService.java`
- `backend/src/main/java/com/minierp/backend/global/service/InvitationTokenStore.java`
- `backend/src/main/java/com/minierp/backend/global/service/MailService.java`
- `backend/src/test/java/com/minierp/backend/integration/M1AcceptanceTest.java`
- `docs/known-issues.md`
- `docs/troubleshooting/M1-4-organization-api.md`

### M1-5 — `b96c0ea`

문서: [M1-5-initial-admin.md](M1-5-initial-admin.md)

- `backend/src/main/java/com/minierp/backend/domain/user/service/InitialAdminInitializer.java`
- `backend/src/test/java/com/minierp/backend/domain/user/service/InitialAdminInitializerTest.java`
- `backend/src/test/java/com/minierp/backend/integration/InitialAdminMariaDbTest.java`
- `docs/troubleshooting/M1-5-initial-admin.md`
- `docs/troubleshooting/M1-report.md`

### M1-6 — `18d2f69`

문서: [M1-6-legacy-request-scope.md](M1-6-legacy-request-scope.md)

- `backend/src/main/java/com/minierp/backend/domain/approval/service/ApprovalService.java`
- `backend/src/main/java/com/minierp/backend/domain/overtime/service/OvertimeService.java`
- `backend/src/main/java/com/minierp/backend/global/permission/LegacyRequestScopeRepository.java`
- `backend/src/main/java/com/minierp/backend/global/service/AccessPolicy.java`
- `backend/src/test/java/com/minierp/backend/domain/dashboard/service/DashboardServiceTest.java`
- `backend/src/test/java/com/minierp/backend/domain/project/service/ProjectServiceTest.java`
- `backend/src/test/java/com/minierp/backend/domain/task/service/TaskServiceTest.java`
- `backend/src/test/java/com/minierp/backend/global/config/ClockBehaviorTest.java`
- `backend/src/test/java/com/minierp/backend/integration/M1RegressionTest.java`
- `docs/troubleshooting/M1-6-legacy-request-scope.md`

### M1-7 — `cbf71e0`

문서: [M1-7-auditing-clock.md](M1-7-auditing-clock.md)

- `backend/src/main/java/com/minierp/backend/global/config/JpaAuditingConfig.java`
- `backend/src/main/java/com/minierp/backend/global/entity/BaseEntity.java`
- `backend/src/test/java/com/minierp/backend/integration/AuditClockMariaDbTest.java`
- `docs/troubleshooting/M1-7-auditing-clock.md`

### M1-8 — `8ac7193`

문서: [M1-8-registration-role-guard.md](M1-8-registration-role-guard.md)

- `backend/src/main/java/com/minierp/backend/domain/user/service/EmployeeService.java`
- `backend/src/test/java/com/minierp/backend/integration/M1AcceptanceTest.java`
- `docs/troubleshooting/M1-8-registration-role-guard.md`

### M1-9 — `0ef892c`

문서: [M1-9-secure-verification-code.md](M1-9-secure-verification-code.md)

- `backend/src/main/java/com/minierp/backend/domain/user/service/AuthService.java`
- `backend/src/test/java/com/minierp/backend/domain/user/service/AuthVerificationCodeTest.java`
- `docs/troubleshooting/M1-9-secure-verification-code.md`

### M1-10 — `5ddfa6b`

문서: [M1-10-hire-date-update.md](M1-10-hire-date-update.md)

- `backend/src/main/java/com/minierp/backend/domain/user/dto/EmployeeRequests.java`
- `backend/src/main/java/com/minierp/backend/domain/user/service/EmployeeService.java`
- `backend/src/test/java/com/minierp/backend/integration/M1AcceptanceTest.java`
- `docs/troubleshooting/M1-10-hire-date-update.md`

## 확정한 해석·명세 보완 및 결정 필요

- **사용자 확정(2026-10-15):** is_active=false → RETIRED, 기존 hire_date → NULL. v1의 비활성화는 수동 변경뿐이어서 이유를 알 수 없다. 복귀를 전제로 하는 ON_LEAVE 대신 닫힌 상태로 두고 필요하면 관리자가 복원한다.
- 같은 추가 지시에 따라 PATCH hireDate와 신규 등록 필수 검증, EMPLOYEE 외 역할 등록 시 ROLE U, SecureRandom을 구현했다. 프론트 브랜치가 수정할 12번 명세는 건드리지 않았다.
- 최대 범위는 API 2.4에 맞춰 같은 메뉴의 유효 권한 사이에서 계산한다. 아무 동작도 없는 권한은 범위를 넓히지 않는다.
- 기본 역할 PATCH는 SYSTEM_ROLE_LOCKED로 막는다. ERD가 금지하는 이름 외에 설명만 수정하도록 허용할지는 명세 보완이 필요하다.
- 조직도 인원수에서 제외할 재직 상태가 명시되지 않아 전체 소속 사원을 센다. 퇴사자를 제외할지 결정 필요.
- 결재 양식 규칙에 쓰인 역할 삭제 금지는 M2 테이블이 아직 없어 연결하지 않았다. 근무시간 변경에 대한 과거 근태 판정 스냅샷도 M3에서 구현한다. 이번에 미래 단계 테이블·코드는 만들지 않았다.
- 신규 역할 코드는 형식 미지정으로 서버 발급 R-번호를 사용한다. 기존 결재 역할 계층과 직급별 초기 연차 계산은 유지했다. 입사일 보정으로 연차를 소급 재계산하지 않는다.

## 프론트 호환 필드

로그인·사용자 응답에 `departmentCode`, `departmentName`, `position`, `role`을 남기고 소스에 `M1 프론트 작업 후 제거`를 표시했다. 새 `department`, `roles`, `loginId` 등과 함께 반환한다. 로그인·/me의 roles는 코드 배열, 사용자 목록/상세는 코드·이름 객체 배열이다. position 문자열은 새 명세에도 있으므로 실제 제거 형태는 프론트 단계에서 결정해야 한다.

## 실행 방법

기존 M0 DB는 앱 정지·백업 → V1 성공 이력 확인 → 부서 코드 및 이관 후 사용할 ACTIVE 관리소장 접근 경로 점검 → 복제 DB에서 V2 리허설 → 기존 DB에 앱 시작 순서다. M0 V1과 데이터는 삭제하지 않는다. Flyway INFO 로그의 `M1: migrated_inactive_to_retired=N`을 적용 기록으로 보관한다. MariaDB DDL은 자동 커밋하므로 실패 시 전체 롤백을 가정하지 않는다.

새 DB는 MariaDB 10.11 빈 DB·전용 계정 준비 → Java 17·DB/JWT 환경값·최초 관리자 환경변수 주입 → `cd backend` / `./mvnw spring-boot:run` → V1/V2 및 validate 확인 → 로그인 후 비밀번호 변경 순서다. 기존 ver 없는 토큰은 재로그인해야 한다. Redis는 비밀번호 재설정·초대 링크 저장에만 사용한다.

테스트는 `cd backend` 후 `./mvnw test`이다. Docker가 없으면 MariaDB 테스트가 건너뛰어지므로 완료 기준 검증에는 Docker를 켠다. 자세한 환경값·초대 페이지 URL 설정은 [README](../../README.md)의 M1 절을 따른다.

## 남은 위험·발견했지만 고치지 않은 문제

- KI-01 특근 DTO 불일치/500: M2, 기존 Disabled 한 개 유지.
- KI-03 연차 승인·차감 동시성: M2.
- KI-04 사용자 입력 출퇴근 시각·직접 근태 수정: M3.
- 특근 단건 GET의 팀장 타 부서 조회: 이번 지정 /all·승인·반려 범위 밖. M1 추가 차단 또는 M2 처리 결정 필요.
- 기존 메일 비활성 시 비밀번호 재설정 인증 코드 로그: 별도 보완 필요. 이번에는 난수 생성기만 변경했다.
- 모든 기존 CHIEF가 비활성인 DB는 이관 전에 관리자 접근 복원 절차를 점검해야 한다. 최초 관리자 생성은 기존 CHIEF 계정을 임의 승격/복원하지 않는다.

이번 요청의 이관·보안·입사일 추가 작업은 완료했다. 화면 연결·실제 운영 DB 이관·원격 푸시/병합은 실행하지 않았다. `frontend/`, V1, 설계 명세 10/11/12, 의존성 파일을 수정하지 않았고 기존 `.claude/` 미추적 파일도 유지했다. 새 라이브러리·권한 캐시는 없다. KI-02, KI-05 해결 커밋은 [known-issues](../known-issues.md)에 기록되어 있다.
