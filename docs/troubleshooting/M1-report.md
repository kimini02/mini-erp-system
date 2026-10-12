# M1 작업 보고서 — 이관 상태 결정 대기

브랜치: `feat/m1-org-permission` (develop `73ebe93`에서 분기).

## 현재 상태와 완료하지 못한 것

M1-2~7 구현과 검증을 진행했지만 **M1 전체 완료가 아니다**. 명세 1.5는 `is_active → status`만 지정하고 false가 휴직/퇴사 중 어느 것인지 정하지 않는다. 이 결정 없이 기존 인사 상태를 바꾸지 않기 위해 M1-1 운영 V2 파일은 확정·커밋하지 않았다. `M1-1-schema-migration.sql.pending`은 실행 대상이 아닌 초안이다.

현재 브랜치는 V2가 빠져 있으므로 **배포·병합 대상이 아니며 일반 `./mvnw test` 완료 기준도 아직 충족하지 않았다**. 비활성 계정 상태를 정하면 초안을 `backend/src/main/resources/db/migration/V2__organization_permission.sql`로 확정하고 비활성 fixture를 추가해 기본 명령 전체 검증을 해야 한다. 기존 입사일은 없는 사실을 만들어 넣지 않고 NULL로 보존한다.

## 검증 결과

Java 17.0.16 + 실행 중인 Docker의 MariaDB 10.11. 활성 기존 데이터만 지원하는 임시 V2를 명시해 다음 명령을 실행했다.

```bash
cd backend
./mvnw -Dspring.flyway.locations=filesystem:/private/tmp/m1-test-migrations test
```

- 총 133개, 성공 132개, 실패 0개, 오류 0개, 건너뜀 1개. 건너뜀은 기존 특근 DTO 테스트 한 개뿐이다.
- 임시 V2는 미결정 비활성 분기를 NULL로 두었다. 활성 계정 이관·빈 DB 검증 결과이며 비활성 이관 완료를 의미하지 않는다.
- 필수 7개: `M1AcceptanceTest`의 동시 아이디/순환 이동/부서 범위/즉시 토큰 차단/마지막 관리소장/다음 요청 권한 반영/조직도 쿼리 수 모두 통과.
- 동시 발급: 잠금 없는 두 스레드 MAX+1은 성공 1/유니크 충돌 1, 안전 발급 8개 스레드는 중복 0. 조직도는 부서 4개와 16개 모두 실제 `Com_select` 증가량 2.
- `M1DataMigrationTest`: M0 V1 기존 세 역할·세 부서·공통 직급·기존 아이디·연차·암호값 보존과 숫자 아이디 시퀀스 초기화.
- `MariaDbMigrationTest`: 빈 DB V1/V2, Hibernate validate, 외래키 20개, 유니크 제약, 재실행 무변경.
- `M1RegressionTest`: 기존 연차·특근 범위 밖 목록/승인/반려 차단, 하위 부서 허용, 관리소장 전체 목록, 사원 전체 조회 차단.
- `AuditClockMariaDbTest`: JVM UTC에서 한국 고정 Clock과 MariaDB created_at 일치. M0 격리 환경에서도 수정 전 실패/수정 후 성공 확인.
- `InitialAdmin*`, `PasswordPolicyTest`: CHIEF·must_change_pw·암호화·정책·누락·재실행 검증.
- 추가 초대 검증: 커밋 후 발송, 실패/꺼짐/롤백, 일회성 링크, 여러 링크 동시 사용 시 최초 설정만 허용.

## 작업별 커밋·파일·문서

M1-1 스키마 작업은 결정 대기 중이며 새 엔티티·이관 테스트·문서와 SQL 초안은 작업 트리에 남겨 두었다. 다른 변경은 아래 작업별 커밋이다. 초기 관리자 커밋은 이 보고서가 포함된 커밋을 가리킨다.

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

### M1-5 — 본 보고서 포함 커밋

문서: [M1-5-initial-admin.md](M1-5-initial-admin.md)

- `backend/src/main/java/com/minierp/backend/domain/user/service/InitialAdminInitializer.java`
- `backend/src/test/java/com/minierp/backend/domain/user/service/InitialAdminInitializerTest.java`
- `backend/src/test/java/com/minierp/backend/integration/InitialAdminMariaDbTest.java`
- `docs/troubleshooting/M1-5-initial-admin.md`
- `docs/troubleshooting/M1-report.md`

## 명세 해석·결정 필요

1. **필수 결정:** 기존 `is_active=false` → `ON_LEAVE` 또는 `RETIRED` 중 어느 것으로 이관할지. 아직 적용하지 않았다.
2. 기존 입사일은 알 수 없으므로 NULL 보존. 이후 자료로 보정해야 한다.
3. 최대 범위는 API 2.4의 메뉴별 권한 합산을 따랐다. 아무 동작도 없는 권한 행은 범위를 넓히지 않는다.
4. 기본 역할의 PATCH는 명세의 SYSTEM_ROLE_LOCKED를 적용해 막는다. ERD가 금지하는 이름 외에 설명만 수정하도록 허용할지는 명세 보완 필요.
5. 조직도 인원수에서 제외할 재직 상태가 없으므로 소속 사원 전체를 센다.
6. 결재 양식 규칙에 쓰인 역할 삭제 금지는 M2 테이블이 아직 없어 연결하지 않았다. 근무시간의 과거 근태 판정 스냅샷도 M3에서 구현한다. 다음 단계 테이블·코드는 만들지 않았다.
7. 신규 역할 코드 형식은 미지정이어서 서버 발급 `R-번호`를 사용한다. 기존 결재 역할 계층과 직급별 초기 연차 계산은 유지했다.

## 프론트 호환 필드

로그인/사용자 응답에 `departmentCode`, `departmentName`, `position`, `role`을 유지하고 소스에 `M1 프론트 작업 후 제거`를 표시했다. 새 `department`, `roles`, `loginId`, `hireDate`, `status`, `invitationPending`과 함께 반환한다. 로그인과 /me의 roles는 코드 배열이며 사용자 목록/상세는 코드·이름 객체 배열로 명세를 따른다. `mustChangePassword`는 로그인 응답 최상위 data에 있다. position 문자열은 새 명세에도 있어 실제 제거 형태를 프론트 단계에서 정해야 한다.

## 실행 순서

최종 V2 확정 후 기존 M0 DB는 백업 → 앱 정지 → 복제 DB에서 V2 리허설 → 동일 DB의 앱 시작(Flyway V2 + validate). M0 V1과 데이터를 삭제하지 않는다. 새 설치는 빈 MariaDB 10.11 → DB/JWT 환경값 및 최초 관리자 환경변수 → Flyway V1/V2 → 로그인 후 비밀번호 변경 순서다. 세부 환경값·테스트 명령은 [README](../../README.md)의 M1 절에 있다.

## 발견했지만 고치지 않은 문제

- KI-01 특근 요청 DTO 불일치/500: M2, 기존 Disabled 한 개 유지.
- KI-03 연차 승인·차감 동시성: M2.
- KI-04 사용자 입력 출퇴근 시각·직접 근태 수정: M3.
- 특근 단건 GET의 팀장 타 부서 조회: 이번 지정 /all·승인·반려 범위 밖. M1 추가 차단 또는 M2 처리 결정 필요.
- 기존 메일 발송 비활성 시 비밀번호 재설정 인증 코드 로그: 별도 보완 필요. 새 초대 토큰/링크는 로그에 남기지 않는다.

`frontend/`, V1, 설계 명세 10/11/12, 의존성 파일은 수정하지 않았다. 기존 `.claude/` 미추적 파일은 건드리지 않았다. 새 라이브러리·권한 캐시는 없다. KI-02, KI-05의 해결 커밋은 [known-issues](../known-issues.md)에 기록했다.
