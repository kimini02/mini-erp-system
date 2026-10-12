# Mini ERP System

React 기반 프론트엔드와 Spring Boot 기반 백엔드를 하나의 저장소로 구성한 팀 ERP 프로젝트입니다.

## 주요 기능

- 관리자 사원 등록·초대·로그인 및 역할 기반 접근 제어
- 사용자·권한 관리
- 출퇴근 및 근태 현황 관리
- 연차·특근 신청과 승인
- 프로젝트·업무·일정·대시보드 관리

## Tech Stack

### Frontend

`React` `Vite` `JavaScript` `Tailwind CSS` `Zustand` `Axios`

### Backend

`Java 17` `Spring Boot` `Spring Security` `Spring Data JPA` `MariaDB` `Redis` `Flyway`

## Repository Structure

```text
mini-erp-system/
├── frontend/   # React + Vite
└── backend/    # Spring Boot + Maven
```

## 실행 방법

### Frontend

```bash
cd frontend
cp .env.example .env
npm install
npm run dev
```

### Backend

```bash
cd backend
cp src/main/resources/application-local.properties.example \
   src/main/resources/application-local.properties
export JWT_SECRET="replace-with-a-base64-encoded-secret"
./mvnw spring-boot:run
```

MariaDB와 Redis가 로컬에서 실행 중이어야 하며, DB·메일 관련 값은 환경변수로 설정할 수 있습니다.

## Source Repositories

- Frontend: [JangSeonguk1011/mini-erp-system_frontend](https://github.com/JangSeonguk1011/mini-erp-system_frontend)
- Backend: [kimini02/mini-erp-system-backend](https://github.com/kimini02/mini-erp-system-backend)


## M0: 빈 MariaDB에서 처음 실행하기

> 이 절의 삭제·재생성은 M0 이전 DB 전용입니다. 이미 M0 V1을 적용한 DB의 M1 이관에는 실행하지 마세요. 아래 M1 절을 참고하세요.

MariaDB **10.11**, Java **17**을 사용합니다. 스키마는 Flyway `V1__init.sql`이 만들고 Hibernate는 `validate`만 수행합니다.

**기존 로컬 DB는 지우고 다시 만들기:** 과거 V1~V5를 V1 하나로 통합했으므로 기존 스키마와 Flyway 이력을 그대로 사용할 수 없습니다. 필요한 데이터는 먼저 백업하고, 삭제 대상이 개발용 로컬 DB인지 확인하세요. 운영 DB에 이 초기화 절차를 적용하지 마세요.

1. MariaDB 관리자 계정으로 개발용 DB를 삭제·재생성합니다. 신규 설치는 CREATE만 실행합니다.
   ```sql
   DROP DATABASE IF EXISTS mini_erp;
   CREATE DATABASE mini_erp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```
   애플리케이션용 DB 계정에 이 DB의 테이블 생성·변경 및 데이터 읽기·쓰기 권한을 부여합니다.
2. 위 Backend 절차로 로컬 설정 예제를 복사하고 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`을 환경에 주입합니다. 기존 로컬 설정을 유지한다면 반드시 `spring.jpa.hibernate.ddl-auto=validate`, `spring.flyway.baseline-on-migrate=false`로 변경합니다. 실제 비밀번호와 JWT 비밀키는 저장소에 기록하지 않습니다.
3. Redis를 실행합니다(기존 비밀번호 재설정 기능용). 메일을 사용하지 않는 개발 환경에서는 `APP_MAIL_ENABLED=false`로 실행할 수 있습니다.
4. `cd backend` 후 `./mvnw spring-boot:run`을 실행합니다. Flyway V1 적용, EntityManagerFactory 초기화, `Started BackendApplication` 로그를 확인합니다.

빈 스키마만 지원하며, `baseline-on-migrate` 또는 `ddl-auto=update`로 검증 오류를 우회하지 않습니다. 이후 스키마 변경은 V2부터 새 마이그레이션으로 추가합니다.

## 백엔드 테스트 (M0)

`backend/`에서 Java 17로 실행합니다.

```bash
./mvnw test                                  # 기존 H2·단위 테스트 + MariaDB 테스트
./mvnw -Dtest=MariaDbMigrationTest test       # 빈 DB → Flyway → validate
./mvnw -Dtest=CoreApiIntegrationTest test     # 기존 H2 통합 테스트
```

MariaDB 테스트는 Docker가 실행 중일 때 `mariadb:10.11` 임시 컨테이너를 사용하며 개발용 DB에 접속하지 않습니다. 처음 실행할 때는 이미지와 테스트 의존성을 내려받을 수 있어야 합니다. Docker를 사용할 수 없으면 해당 테스트만 **skipped**로 표시됩니다. Docker를 찾은 뒤 이미지 다운로드·마이그레이션·기동에 실패하면 테스트 실패로 처리합니다.

새 MariaDB 테스트는 `com.minierp.backend.support.MariaDbIntegrationTest`를 상속하고 이름을 `*Test`로 끝내세요. 각 테스트 클래스는 빈 DB에서 시작하며, 클래스 종료 시 Spring 컨텍스트와 컨테이너를 정리합니다. 클래스 내 테스트 간 데이터 정리가 필요하면 `@Transactional` 롤백 또는 명시적 정리를 사용하세요. 이 공통 베이스를 쓰는 테스트 클래스는 병렬 실행하지 마세요. 기존 `integration` 프로필의 H2 설정은 유지합니다.

M0-4에서 날짜에 의존하던 실패를 해결했습니다. 알려진 특근 요청 버그 테스트 1개만 이유를 명시해 비활성화하며, 처리 단계와 복구 조건은 `docs/known-issues.md`를 참고하세요. 이전 실패 기록은 `docs/troubleshooting/M0-2-mariadb-tests.md`에 보존합니다.

## 최초 관리소장 계정 (M0)

최초 기동 전에 다음 환경변수 네 개를 모두 주입하세요. 실제 비밀번호는 명령문·설정 파일·저장소에 적지 말고, 배포 환경의 비밀값 주입 기능이나 터미널의 숨김 입력을 사용하세요.

| 환경변수 | 저장 항목 |
| --- | --- |
| `INITIAL_ADMIN_LOGIN_ID` | `login_id` |
| `INITIAL_ADMIN_NAME` | `user_name` |
| `INITIAL_ADMIN_EMAIL` | `user_email` |
| `INITIAL_ADMIN_PASSWORD` | 기존 BCrypt로 암호화한 `user_pw` |

M1에서는 앱 시작 시 `CHIEF` 역할 사용자가 없을 때만 생성합니다. 초기 비밀번호는 기존 정책(8~20자, 대문자·소문자·숫자·특수문자)을 만족해야 하며, 미충족이면 경고만 남깁니다. 생성된 계정은 `must_change_pw=true`이므로 첫 로그인 뒤 `GET /api/v1/me`와 `PATCH /api/v1/me/password`만 사용할 수 있습니다. 직급은 관리소장, 부서는 현재 `DepartmentCode` 첫 값, 연차 세 수치는 모두 0입니다. 하나라도 누락·빈 값이면 경고만 남기고 계정은 만들지 않습니다. 이미 관리자가 있으면 환경값을 읽거나 계정·비밀번호를 변경하지 않습니다.

관리자가 없는데 입력한 아이디·이메일이 일반 계정과 중복되면 기존 BusinessException 오류로 기동을 중단합니다. 기존 계정을 승격하거나 덮어쓰지 않으므로 사용하지 않는 값을 지정하세요. 최초 설치는 한 앱 인스턴스로 실행하세요. 여러 인스턴스의 동시 최초 생성은 이번 단계에서 보장하지 않습니다. 초기 생성 후에는 주입한 초기 비밀번호 환경변수를 제거할 수 있습니다.

```bash
cd backend
./mvnw -Dtest=InitialAdminInitializerTest,InitialAdminMariaDbTest,MariaDbMigrationTest test
```

첫 테스트는 Docker 없이 실행되며, 나머지는 실제 MariaDB의 생성·로그인·중복 방지와 초기 스키마를 확인합니다.

### 테스트에서 시계를 고정·변경하는 방법 (M0-4)

운영 `ClockConfig`는 서버 기본 시간대와 관계없이 `Asia/Seoul` 시스템 시계를 제공합니다. 날짜가 필요한 서비스에는 `Clock`을 생성자로 주입하고 `LocalDate.now(clock)` 등을 사용하세요. 엔티티에는 `Task.isOverdue(today)`처럼 기준 날짜를 인자로 전달합니다.

Spring 테스트에는 `@Import(FixedClockTestConfiguration.class)`를 붙이면 `@Primary`인 `Clock.fixed`가 사용됩니다. 순수 단위 테스트는 `TestClocks.fixed()` / `TestClocks.today()`를 사용하고 날짜 데이터는 기준일에 일수를 더하거나 빼서 만드세요. 기본 고정 시각은 `TestClocks` 한 곳에 정의되어 있습니다. 새로운 지각·결근 테스트는 고정 시계나 `Clock.offset(fixedClock, duration)`을 직접 주입해 경계를 검증할 수 있습니다.

```bash
cd backend
./mvnw test
# 한국 날짜 2027-01-01 00:00, JVM 기본 시간대 UTC에서도 동일 검증
./mvnw -Dtest.clock.instant=2026-12-31T15:00:00Z -Duser.timezone=UTC test
```

`test.clock.instant`는 테스트 전용 속성이며 운영 시계를 고정하지 않습니다. 공휴일 정책을 검증하는 테스트는 의도한 근무일·공휴일에 맞는 데이터를 선택해야 합니다. JWT 유효기간은 기존 동작을 유지합니다. M1부터 JPA 자동 생성·수정 시각도 같은 Clock의 DateTimeProvider로 제어하며, JVM 기본 시간대가 UTC인 MariaDB 테스트로 검증합니다.

## M1: 조직·권한 이관 및 실행

**현재 작업 상태:** 비활성 기존 계정(`is_active=false`)을 휴직/퇴사 중 어디로 이관할지 결정 대기 중입니다. 운영 V2 파일은 아직 확정하지 않았습니다. 현재 브랜치를 배포하거나 아래 최종 실행 절차를 적용하지 마세요. 활성 계정만 넣은 임시 V2로 기능 검증을 진행했습니다. `docs/troubleshooting/M1-1-schema-migration.md`를 참고하세요.

이관 결정을 반영하고 최종 V2 검증을 마친 후의 순서:

1. **기존 M0 DB:** 앱을 정지하고 DB를 백업합니다. `flyway_schema_history`의 V1 성공 및 체크섬을 확인합니다. 기존 부서 코드가 01/02/03이고 비활성 계정 처리에 합의했는지 확인한 뒤 복제 DB에서 먼저 V2 이관을 검증합니다. 기존 DB와 V1 이력은 지우지 않습니다.
2. **새 DB:** MariaDB 10.11에 빈 DB와 앱 전용 계정을 준비합니다. Java 17을 사용하고 DB/JWT 환경값을 주입합니다. 새 DB는 Flyway V1 → V2 순서로 적용됩니다.
3. 공통으로 Hibernate는 `validate`, Flyway는 `baseline-on-migrate=false`를 유지합니다. `backend/`에서 `./mvnw spring-boot:run`을 실행해 V2와 validate 완료를 확인합니다. 과거 토큰에는 ver가 없으므로 다시 로그인해야 합니다.
4. 최초 관리자 환경변수 네 개는 위 절과 같습니다. 첫 로그인 후 비밀번호를 변경하고 새 토큰을 사용합니다. 이미 CHIEF가 있으면 추가 생성하지 않습니다.
5. 초대를 쓰려면 Redis와 메일 설정을 준비하고 `APP_MAIL_ENABLED=true`, `APP_INVITATION_SETUP_URL`을 프론트의 비밀번호 설정 페이지 주소로 설정합니다. 링크의 쿼리 키는 `setupToken`입니다. 화면은 별도 작업이므로 연결 전에는 메일을 꺼 둘 수 있습니다. 메일이 꺼져 있거나 발송 실패 시 사원은 등록되고 `invitationSent=false`를 받습니다.

최종 V2 확정 후 테스트 명령:

```bash
cd backend
./mvnw test
./mvnw -Dtest=M1DataMigrationTest,MariaDbMigrationTest test
./mvnw -Dtest=M1AcceptanceTest,M1RegressionTest,AuditClockMariaDbTest test
```

Docker가 켜져 있어야 MariaDB 테스트가 실행됩니다. 테스트 메일·초대 토큰 저장소는 가짜를 사용하므로 외부 메일을 보내지 않습니다. API 명세 9장의 일곱 검증은 `M1AcceptanceTest`에 있으며, 조직도는 실제 MariaDB의 `Com_select` 증가량이 부서 수와 상관없이 2인지 확인합니다.
