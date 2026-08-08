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

