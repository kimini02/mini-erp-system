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
