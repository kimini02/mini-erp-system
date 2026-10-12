# Mini ERP System

React 기반 프론트엔드와 Spring Boot 기반 백엔드를 하나의 저장소로 구성한 팀 ERP 프로젝트입니다.

## 주요 기능

- 회원가입·로그인 및 역할 기반 접근 제어
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

2026-10-12 기준 변경 전 기존 테스트 88개 중 7개가 실패했습니다. M0 범위 밖 원인과 동일 실패 여부는 `docs/troubleshooting/M0-2-mariadb-tests.md`에 기록합니다.

## 최초 관리소장 계정 (M0)

최초 기동 전에 다음 환경변수 네 개를 모두 주입하세요. 실제 비밀번호는 명령문·설정 파일·저장소에 적지 말고, 배포 환경의 비밀값 주입 기능이나 터미널의 숨김 입력을 사용하세요.

| 환경변수 | 저장 항목 |
| --- | --- |
| `INITIAL_ADMIN_LOGIN_ID` | `login_id` |
| `INITIAL_ADMIN_NAME` | `user_name` |
| `INITIAL_ADMIN_EMAIL` | `user_email` |
| `INITIAL_ADMIN_PASSWORD` | 기존 BCrypt로 암호화한 `user_pw` |

앱 시작 시 `user_role=ADMIN`이 없을 때만 생성합니다. 직급은 관리소장, 부서는 현재 `DepartmentCode` 첫 값, 연차 세 수치는 모두 0입니다. 하나라도 누락·빈 값이면 경고만 남기고 계정은 만들지 않습니다. 이미 관리자가 있으면 환경값을 읽거나 계정·비밀번호를 변경하지 않습니다.

관리자가 없는데 입력한 아이디·이메일이 일반 계정과 중복되면 기존 BusinessException 오류로 기동을 중단합니다. 기존 계정을 승격하거나 덮어쓰지 않으므로 사용하지 않는 값을 지정하세요. 최초 설치는 한 앱 인스턴스로 실행하세요. 여러 인스턴스의 동시 최초 생성은 이번 단계에서 보장하지 않습니다. 초기 생성 후에는 주입한 초기 비밀번호 환경변수를 제거할 수 있습니다.

```bash
cd backend
./mvnw -Dtest=InitialAdminInitializerTest,InitialAdminMariaDbTest,MariaDbMigrationTest test
```

첫 테스트는 Docker 없이 실행되며, 나머지는 실제 MariaDB의 생성·로그인·중복 방지와 초기 스키마를 확인합니다.
