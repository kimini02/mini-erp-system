# M5 1차 구현·검증 결과

## 기준과 작업 위치

- 작업 브랜치: `feat/m5-deploy`, 시작점 `develop=f2c83697869dac8ddc008923f1c41546188573b4`.
- 별도 작업 트리: `/Users/kimmini/sk-final-deepfake/mini-erp-system-m5-deploy`.
- 공유 원본 `/Users/kimmini/sk-final-deepfake/mini-erp-system`의 브랜치는 변경하지 않았고 최종 조회에서 `feat/m4-home-board`입니다.
- 처음 요청한 명세는 3dba704. 작업 중 추가된 2027ebd의 자동 공인 IP·부팅 DuckDNS 방식은 사용자 확인을 받아 적용했습니다. 명세 파일은 읽기만 했습니다.
- 병합·푸시·실제 서버 작업 없음. SSH/AWS CLI/DuckDNS 업데이트 실제 호출 없음. 검증은 Testcontainers·별도 로컬 Docker·가짜 HTTP/전송으로 수행했습니다.

## 커밋

| 커밋 | 작업 | 기록 |
|---|---|---|
| `7c0b849` | 운영 프로필·계정 분리·health·CORS·신규 테스트 | [M5-01-production-profile.md](M5-01-production-profile.md) |
| `f417c28` | 서버 설치·설정·비밀값·부팅 DNS·백업 | [M5-02-server-containers.md](M5-02-server-containers.md) |
| `2fe74e4` | 배포·rollback·로컬 복구·probe·runbook | [M5-03-release-recovery-observability.md](M5-03-release-recovery-observability.md) |
| `4f176bd` | 첫 migration 전 빈 DB 복구 보완 | [M5-03-release-recovery-observability.md](M5-03-release-recovery-observability.md) |
| `0be68a4` | 로컬 복구 익명 볼륨 정리 | [M5-03-release-recovery-observability.md](M5-03-release-recovery-observability.md) |
| `be9838a` | 1차 검증 결과 문서 | 이 문서 초판 |
| `f5ce4fa` | skip-name-resolve와 TCP 계정 호스트 보완 | [M5-05-loopback-db-accounts.md](M5-05-loopback-db-accounts.md) |

이 최종 결과 문서는 별도 문서 커밋입니다. 아래 파일 목록은 각 구현 커밋에 실제 포함된 경로를 기록합니다.

## 백엔드 검증

```bash
cd /Users/kimmini/sk-final-deepfake/mini-erp-system-m5-deploy/backend
JAVA_HOME=/Users/kimmini/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home ./mvnw -Duser.timezone=UTC test
```

```text
[INFO] Tests run: 227, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  28:16 min
[INFO] Finished at: 2026-10-28T05:06:10Z
```

| 범위 | 실행 | 성공 | 실패·오류 | 건너뜀 |
|---|---:|---:|---:|---:|
| 전체 UTC | 227 | 227 | 0 | 0 |
| 신규 M5ProductionTest | 4 | 4 | 0 | 0 |
| 신규 M5ProdSecretsTest | 5 | 5 | 0 | 0 |
| 신규 테스트 합계(전체에 포함) | 9 | 9 | 0 | 0 |

신규 테스트만 따로 실행한 9/9도 통과했습니다. 운영 프로필·라이브러리 추가 뒤 기존 218개도 통과했습니다. 전체 실행 이후 변경은 운영 셸과 문서뿐이며, 복구·계정 셸은 수정 전 재현 후 컨테이너로 다시 확인했습니다. 백엔드 Java/설정/의존성은 전체 통과 이후 변경하지 않았습니다.

- Flyway V1~V6 설치자 `workflow_migrator`, 업무 연결 `workflow_app` 확인.
- 로그인·기안 상신·두 단계 승인·출근·카드 이동·마감 알림 배치 성공.
- app의 DROP/ALTER/CREATE 각각 `1142`, SQLState `42000`.
- 무인증 health 200 UP, DB/Redis 각각 장애 시 503 DOWN, 응답에 상세 없음.
- 앱 actuator/Swagger/api-docs 및 management의 나머지 경로 404. prod CORS 허용 출처 없음.
- 필수 설정 5개 각각 누락 시 외부 연결 전 기동 실패.

## 컨테이너·셸·도구 검증

| 항목 | 결과 | 증거/범위 |
|---|---|---|
| bash -n | 18개, 실패 0 | 모든 deploy .sh, 마지막 복구 수정 뒤에도 확인 |
| 내장 Python | 문법 통과 | shell heredoc compile |
| shellcheck | **못 함** | 설치되어 있지 않아 설치하지 않음 |
| nginx:1.24 | nginx -t 통과 | 임시 자체 서명 인증서, 외부 통신 없는 컨테이너 |
| Nginx HTTP 동작 | 통과 | UP 200, 숨긴 경로 404, 앱 401 보존, auth burst 6회 200 후 429, 장애 화면/API/health 503, 보안 헤더 6종, 쿼리 토큰 로그 0 |
| mariadb:10.11 | 기동·SHOW VARIABLES 통과 | bind=127.0.0.1, pool=536870912, max_connections=40, slow=ON, verbosity=query_plan,explain |
| Ubuntu 24.04 bootstrap | 설치·재실행 통과 | 설정/권한 비교 72개 파일 변경 0, nologin, root 600/700, logrotate 충돌 0 |
| Redis | 설정 확인 | bind 127.0.0.1 -::1, protected yes, maxmemory=67108864 |
| systemd 정적 검사 | 7개 통과 | verify + KST 03:30/09:10 calendar 해석; 실제 구동은 미검증 |
| 비밀값 입력 | 통과 | 가상 터미널 입력 7개 노출 0, 특수문자 보존, 재실행 바이트 변경 0, 초기 비밀번호 제거 |
| DuckDNS | 모킹 3가지 통과 | OK/KO/예외, 외부 요청 0, 토큰/URL 유출 0 |
| 백업/복구 | 행 수·버전 일치 | 시험용 2테이블·3행. 덤프 940 bytes, 서버 복구 elapsed_s=0·로컬 2초(정수 초, 0은 1초 미만인 참고치) |
| 실제 서버용 계정 호스트 | 전 TCP 1045 → 후 성공 | 실제 provision 2회, app/migrator @127.0.0.1, DML 성공·DDL 3종 1142 |
| 빈 DB 복구 | 전 실패 1/1 → 후 성공 | 이력 없음 1146 → 0테이블·버전 0으로 서버/로컬 성공. 버전 6 회귀도 성공 |
| 복구 볼륨 정리 | 전 잔존 1 → 후 0 | 도구가 만든 컨테이너의 익명 볼륨만 삭제 |
| probe | 10회 중 의도한 3실패 정확히 집계 | 0.2초, CSV 10행, 최장 실패 0.600초. 실제 배포 중단 시간이 아님 |
| slow-endpoints | 예상값 일치 | 3표본 p50=20ms, p95/max=30ms, 잘못된 로그 1줄 제외 |

수치·안전한 출력 원문은 M5-01~03과 M5-05에 있습니다. 컨테이너는 개발 머신의 arm64 환경이며 실제 t3.small/x86_64 성능 결과가 아닙니다.

## 명세와 달라지거나 구체화한 곳

1. **IP 방식 변경은 사용자 승인**: 최신 2027ebd에 따라 Elastic IP를 빼고 자동 공인 IP와 DuckDNS 부팅 갱신을 구현했습니다.
2. Nginx 화면에 내부 health subrequest를 추가했습니다. 앱 장애 때 정적 index만 200으로 보이는 것을 막기 위해서이며 방문마다 상태 조회 비용이 있습니다.
3. 사이트 error_log를 비활성화했습니다. Nginx 오류 로그는 쿼리 문자열의 토큰을 지울 수 없어, 상세 요청 오류 대신 쿼리 없는 access JSON과 시작 로그를 사용합니다.
4. 느린 쿼리 로그를 하위 폴더에 배치했습니다. Ubuntu 기본 logrotate wildcard와 겹치지 않게 했습니다.
5. 메일 health를 제외했습니다. 메일을 끈 운영에서 SMTP 연결 때문에 DOWN이 되지 않도록 했습니다.
6. bootstrap의 `--no-systemd`, 초기 비밀번호 제거 도구, 로컬 오프라인 복구 도구, 재실행 가능한 가짜 API/전송 검증 도구를 추가했습니다. 운영 정책을 바꾸는 기능은 아닙니다.
7. 첫 배포 전 빈 DB는 복구 검사에서 버전 0으로 처리했습니다. 익명 복구 볼륨 정리는 자신이 만든 컨테이너로 제한했습니다.

8. **계정 호스트는 @127.0.0.1로 명시**했습니다. 명세의 @localhost + skip-name-resolve + JDBC TCP 조합에서 실제 provision 실행 후 1045가 재현됐습니다. 이름 해석을 켜거나 @%로 넓히는 대신 loopback TCP로 한정했고 root socket은 유지했습니다. [M5-05](M5-05-loopback-db-accounts.md)에 전후 결과가 있습니다.

각 선택의 버린 대안과 이유는 M5-01~03·05에 남겼습니다. 구현을 막는 추가 결정 질문은 없습니다.

## 확인하지 못한 것과 이유

- 실제 서버의 보안 그룹·SSH·IMDS/DuckDNS 갱신·인증서 발급/갱신: 사용자 지시로 실제 서버/외부 갱신을 실행하지 않았습니다.
- systemd PID 1 기반의 서비스 재시작·권한 격리·swap/sysctl 적용·타이머 발화·journald 제한: 일반 Ubuntu 컨테이너로는 확인하지 못했습니다.
- 실제 배포·자동/수동 rollback·SSH 백업 다운로드·EC2 메모리·시작 시간·배포 중단·다음 날 배치: 명세 9장 실측은 사용자 범위입니다.
- 프론트 결합 빌드와 주요 화면 CSP: frontend 수정/실행 범위에 포함하지 않았습니다. 리뷰한 M5 프론트와 함께 배포해야 합니다.
- shellcheck: 미설치. 설치하지 말라는 요청을 따랐습니다.

## 변경 보호와 남은 위험

- frontend/, backend/md/, Flyway V1~V6, .claude/ 변경 **0개**. 새 migration 없음.
- 새 애플리케이션 의존성은 actuator starter 하나. 새 라이브러리·Redis 용도 추가 없음.
- 파일별 git add만 사용. 공유 원본 브랜치 변경·develop 병합·푸시 없음.
- 실제 비밀값을 입력받거나 저장소에 저장하지 않았습니다. 테스트 값은 런타임 생성, 인증서는 임시 자체 서명입니다. 변경 파일의 AWS 키/개인 키/실제 웹후크 URL 패턴 검사 결과 0건이며, 이 검사는 모든 비밀값 형태를 완전 탐지한다는 뜻은 아닙니다.
- 운영 프로세스는 Flyway 비밀값도 갖습니다. DML 계정 분리가 프로세스 침해까지 격리하는 것은 아닙니다.
- 한 대 장애·하루 단위 백업·수동 외부 보관·스키마 비호환 rollback 한계가 남습니다. [운영 문서](../../deploy/README.md)의 순서와 한계를 따릅니다.

## 커밋별 변경 파일

### 7c0b849 — 운영 프로필·계정 분리·health·CORS·신규 테스트

```text
backend/pom.xml
backend/src/main/java/com/minierp/backend/domain/task/service/TaskOverdueWork.java
backend/src/main/java/com/minierp/backend/global/config/ProdSecretsConfig.java
backend/src/main/java/com/minierp/backend/global/config/SecurityConfig.java
backend/src/main/java/com/minierp/backend/global/config/WebConfig.java
backend/src/main/resources/application-prod.properties
backend/src/test/java/com/minierp/backend/integration/M5ProdSecretsTest.java
backend/src/test/java/com/minierp/backend/integration/M5ProductionTest.java
docs/troubleshooting/M4-18-strict-overdue-claim.md
docs/troubleshooting/M5-01-production-profile.md
```

### f417c28 — 서버 설치·설정·비밀값·부팅 DNS·백업

```text
deploy/server/backup.sh
deploy/server/batch-check.service
deploy/server/batch-check.sh
deploy/server/batch-check.timer
deploy/server/bootstrap.sh
deploy/server/clear-initial-password.sh
deploy/server/common.sh
deploy/server/duckdns-update.service
deploy/server/duckdns-update.sh
deploy/server/journald-workflow.conf
deploy/server/logrotate-workflow
deploy/server/maintenance.html
deploy/server/mariadb-60-workflow.cnf
deploy/server/nginx-http-bootstrap.conf
deploy/server/nginx-observability.conf
deploy/server/nginx-snippets/proxy-api.conf
deploy/server/nginx-snippets/security-headers.conf
deploy/server/nginx-workflow.conf
deploy/server/notify-failure@.service
deploy/server/notify.sh
deploy/server/provision-db.sh
deploy/server/redis-workflow.conf
deploy/server/restore-rehearsal.sh
deploy/server/setup-secrets.sh
deploy/server/slow-endpoints.sh
deploy/server/workflow-backup.service
deploy/server/workflow-backup.timer
deploy/server/workflow.service
docs/troubleshooting/M5-02-server-containers.md
```

### 2fe74e4 — 배포·rollback·로컬 복구·probe·runbook

```text
README.md
deploy/README.md
deploy/bin/common.sh
deploy/bin/deploy.sh
deploy/bin/probe.sh
deploy/bin/pull-backup.sh
deploy/bin/restore-local.sh
deploy/bin/rollback.sh
deploy/checks/offline-tools.py
deploy/server/release.sh
docs/troubleshooting/M5-03-release-recovery-observability.md
```

### 4f176bd — 첫 migration 전 빈 DB 복구 보완

```text
deploy/bin/restore-local.sh
deploy/server/common.sh
deploy/server/restore-rehearsal.sh
docs/troubleshooting/M5-03-release-recovery-observability.md
```

### 0be68a4 — 로컬 복구 익명 볼륨 정리

```text
deploy/README.md
deploy/bin/restore-local.sh
docs/troubleshooting/M5-03-release-recovery-observability.md
```

### f5ce4fa — TCP 계정 및 최종 서버 준비 확인

```text
deploy/README.md
deploy/bin/restore-local.sh
deploy/server/provision-db.sh
docs/troubleshooting/M5-05-loopback-db-accounts.md
```

## 다음 리뷰 요청

클로드에게 이 브랜치와 M5-01~05, `deploy/README.md`, 기준 명세 `2027ebd`를 전달해 읽기 리뷰를 요청합니다. 실제 서버 실행·SSH·AWS CLI·DuckDNS 갱신·병합·푸시는 별도 사용자 지시 전까지 하지 않습니다. 특히 비밀값 처리, 부팅 순서, health 실패 시 rollback, 백업 잠금과 스키마 호환성, 공개 Nginx 경로를 검토하면 됩니다.
