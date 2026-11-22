# 배포 · 운영 — M5 (서버 한 대, HTTPS, 백업, 관측)

> 기준: [11-architecture.md](11-architecture.md) 2장·8장(AD-01 서버 한 대 모놀리스, AD-08 jar + systemd 직접 운영).
> 목표는 세 가지입니다. ① 실제 주소로 접속되는 운영 서버를 안전하게 올리고 ② 다음 단계(**운영 규모 데이터로 문제 찾기**)에서 증상을 관찰할 도구를 갖추고 ③ **4주 동안 실제로 운영하며 사건을 감지하고 복구한 기록**을 남깁니다. ③은 포트폴리오의 주요 결과물입니다(10장).
> 이 단계에서 재는 수치는 **작은 데이터에서의 기준값**입니다. 같은 항목을 다음 단계에서 회사 규모 데이터로 다시 재서 비교합니다.

---

## 0. 결정

| 결정 | 이유 · 버린 것 |
|---|---|
| **AWS EC2 t3.small 1대** (2 vCPU, 2 GiB), 서울 리전, Ubuntu 24.04 LTS, gp3 30 GiB | 새 무료 플랜에서 쓸 수 있는 2 GiB 중 가장 흔한 사양. t3.micro(1 GiB)는 MariaDB와 JVM을 함께 올리면 swap에 기대게 됨. 디스크 30 GiB는 다음 단계 데이터 + 최근 7세트 백업 + swap 여유 |
| **새 무료 플랜 계정** (크레딧). 유료 전환 금지, AWS Organizations 가입 금지 | 무료 플랜은 유료로 바꾸지 않는 한 청구하지 않고, 6개월이 지나거나 크레딧을 다 쓰면 계정이 닫힘. Organizations 가입 등은 **자동 유료 전환**이라 하지 않음 |
| 한 대에 **Nginx · Spring Boot(systemd) · MariaDB 10.11 · Redis** | AD-01·AD-08 그대로. RDS는 버림: 크레딧을 더 쓰고, 느린 쿼리 로그·버퍼 풀 같은 DB 설정을 직접 다루는 게 이 프로젝트의 목표 |
| 주소는 **DuckDNS 무료 서브도메인 + Let's Encrypt 90일 인증서** | 도메인 구입은 돈이 듦. IP 인증서는 수명이 6일이라 갱신이 하루만 멈춰도 접속이 막힘. DuckDNS는 서버를 옮겨도 IP만 바꾸면 같은 주소 |
| **고정 IP(Elastic IP) 없이 자동 공인 IP + 부팅 때 DuckDNS 갱신** (2026-10-28 변경) | 실험할 때만 켜고 끄는 운영이라, 꺼져 있는 동안 IP 요금을 내지 않으려고. Elastic IP는 인스턴스를 꺼 둬도 시간당 요금이 붙음. 대가: 켤 때마다 IP가 바뀌므로 부팅 때 DuckDNS를 갱신하는 서비스와 DuckDNS 토큰(비밀값)이 필요. 인증서는 도메인에 붙으므로 IP가 바뀌어도 그대로 |
| **T3 CPU 크레딧은 standard 모드** | t3는 기본이 unlimited라 부하 실험 중 초과 크레딧이 조용히 과금(크레딧 차감)됨. 대신 크레딧이 바닥나면 기준 성능(vCPU당 20%)으로 묶이므로, 측정할 때는 `CPUCreditBalance`를 같이 기록. t3는 **시작 크레딧이 없고**(새 인스턴스는 0에서 시작, t3.small은 시간당 24씩 쌓임) 끈 뒤 **7일이 지나면 쌓인 크레딧이 사라짐** → 부하 실험은 켜고 2~3시간 뒤 잔액을 확인하고 시작 |
| 외부에는 **80·443만**, 22는 내 IP만. 앱 8080·관리 8081·DB 3306·Redis 6379는 **127.0.0.1에만 바인딩** | 보안 그룹을 실수로 열어도 바깥에서 닿지 않게 두 겹으로 막음 |
| **DB 계정 3개로 분리**: Flyway용(DDL) · 앱 실행용(SELECT·INSERT·UPDATE·DELETE만) · 백업은 root unix_socket | 앱의 DB 연결로는(예: SQL 주입) 테이블을 지우거나 바꾸지 못함. 단 Flyway가 같은 앱 프로세스에서 돌아 migrator 비밀번호도 앱 환경에 있으므로 **서버 프로세스를 장악당한 경우까지 막지는 못함**(2026-10-28 리뷰로 범위를 좁힘). 백업은 비밀번호 없이 OS 권한으로 |
| 시간대: **OS · JVM · DB 모두 UTC**, 업무 날짜·시각은 지금처럼 `Clock`(Asia/Seoul) | 테스트 조합(`-Duser.timezone=UTC`)과 같음. 운영 코드 SQL에 `NOW()`·`CURDATE()`·`CURRENT_TIMESTAMP`가 없음을 확인함(2026-10-28 grep, V1~V6 포함) |
| 격리 수준: MariaDB 기본값(REPEATABLE READ) 그대로 | 서비스마다 `@Transactional(isolation = READ_COMMITTED)`를 이미 명시함. 서버 기본값을 바꾸면 테스트와 달라짐 |
| 배포: 로컬 빌드 → 릴리스 폴더 → 링크 교체 → 재시작 → 헬스 체크 → **실패하면 직전 릴리스로 자동 되돌림** | 서버에서 빌드하지 않음(2 GiB에서 Maven·npm 빌드는 운영 프로세스와 메모리를 다툼) |
| **무중단 배포는 이번에 하지 않음 — 먼저 끊기는 시간을 잼** | 사내 시스템이라 업무 시간 외 배포 + 점검 안내로 충분할 수 있음. 두 프로세스를 번갈아 띄우면 2 GiB에 JVM 두 개, 겹치는 동안 배치 중복 실행, 구·신 코드의 스키마 호환 문제가 따라옴. 측정값을 보고 판단 |
| 관측: **Nginx 접근 로그(JSON, 응답 시간 포함) · MariaDB 느린 쿼리 로그 · sysstat 1분 · CloudWatch 기본 지표** | "어느 API가 느린가, 그때 DB·CPU가 바빴나"를 답하기에 충분. Prometheus·Grafana는 버림: 2 GiB를 나눠 써야 하고 지금 질문에 비해 무거움 |
| 백업: 매일 03:30(KST) 전체 덤프, 서버에 **최근 7세트**, **로컬로 내려받는 스크립트** | S3·EBS 스냅샷은 이번엔 버림(짧게 운영하는 데모라 IAM·비용 설정 대비 이득이 작음). 대신 "서버 디스크에만 있다"는 한계를 기록하고, 데이터 투입 전후·종료 전에 로컬로 받음 |
| 메일은 **켬**(`APP_MAIL_ENABLED=true`, SMTP STARTTLS 587) (2026-11-17 변경) | 사원은 초대 메일의 링크로만 첫 비밀번호를 정할 수 있어, 메일이 꺼져 있으면 관리자 말고는 아무도 로그인할 수 없음(로컬 점검에서 확인). 메일 서비스(Gmail 앱 비밀번호, AWS SES 등)는 사용자가 고르고, 값은 `setup-secrets.sh`에서 숨김 입력. 465 전용 SSL은 지원하지 않음(KI-12). 버린 것: 메일 끄고 관리자가 링크를 직접 전달 — 링크 토큰이 메신저·채팅에 남음 |
| 실패 알림: **Discord 웹후크(선택)** | 주소를 넣으면 앱 비정상 종료·백업 실패·배치 미실행을 알림. 주소가 없으면 스크립트는 아무것도 하지 않음 |
| **4주 상시 운영 + 운영 기록** (2026-11-11 추가) | 실제 사용자는 없어도 배포, 야간 배치, 백업, 인증서, 재부팅, 외부 봇 요청은 실제로 일어남. 이 사건과 일부러 일으킨 장애의 감지·복구 시간을 기록해 "운영해 본 경험"을 보여 줌. 실험할 때만 켜는 방식은 이 기간에는 쓰지 않음 |

### 0.1 왜 서버 한 대인가 — 가정한 회사 규모에서 출발

**가정**: 직원 1,000명, 부서 50개, 3년 운영한 회사 하나. 사내 시스템이라 바깥 사용자는 없음.

| 항목 | 가정 | 계산 |
|---|---|---|
| 평소 | 업무 시간 중 동시에 쓰는 사람 최대 300명(30%), 한 사람이 분당 2번 요청 | 초당 약 10건 |
| **가장 바쁜 때** | 08:50~09:05 출근: 1,000명이 출근 버튼·홈·알림 등 1인당 5번 요청 | 15분에 5,000건 = 초당 평균 약 6건, 몰리는 순간을 5배로 잡아 **초당 30건** |
| 데이터 | 출퇴근 약 75만 행, 결재 문서 약 10만 건, 알림 수백만 건, 업무 수만 건 | 다음 단계에서 실제로 넣음 |
| 멈춰도 되는 정도 | 업무 시간 중 장애는 안 되지만 야간 점검은 괜찮음. 출근을 못 찍으면 근태 정정 결재로 고칠 수 있음 | 복구 목표 1시간 |
| 잃어도 되는 데이터 | 매일 백업이라 **최대 하루치** | 한계로 기록 (아래 바꾸는 기준) |

요청 하나에 CPU 10~20ms가 든다고 보면, 초당 30건은 vCPU 0.3~0.6개 수준입니다. 2 vCPU 한 대로 충분하다고 **가정**하고, 다음 단계에서 이 피크를 부하 도구로 재현해 **확인**합니다. t3는 평소 기준 성능이 vCPU당 20%(합계 0.4개)라서, 아침 피크는 쌓아 둔 CPU 크레딧으로 버티는 구조입니다. 이것도 같이 확인합니다.

**배포 방식 비교** (이 규모, 운영자 1명, 예산은 무료 크레딧 안)

| 방식 | 좋은 점 | 이 규모에서 | 판단 |
|---|---|---|---|
| **EC2 한 대에 직접** | 가장 쌈. 서버·DB·Nginx를 직접 다뤄 원인을 끝까지 따라갈 수 있음 | 피크 초당 30건은 한 대 범위(확인 예정) | **채택.** 약점: 서버가 죽으면 전부 멈춤, 데이터는 최대 하루치 손실 |
| EC2 + RDS | DB 백업·패치·장애 조치를 AWS가 함, 시점 복구 | 비용이 붙고, 느린 쿼리 로그·버퍼 풀 같은 DB 설정을 직접 다루기 어려움 | 다음 후보 |
| Elastic Beanstalk | jar만 올리면 서버·배포 자동 | 인스턴스가 바뀔 수 있어 DB를 같이 못 둠 → RDS 필수. 안에서 무엇이 도는지 가려짐 | 버림 |
| ECS Fargate (컨테이너) | 서버 관리 없음, 개수 늘리기 쉬움 | 앱이 하나이고 늘릴 일이 없음. 로드 밸런서·RDS 비용 | 버림 |
| EKS (쿠버네티스) | 여러 서비스·여러 팀을 한 플랫폼에서 | 관리 비용만 월 약 $73. 앱 하나에는 과함 | 버림 |

**바꾸는 기준** — 이 수치가 넘으면 구성을 바꿉니다

| 신호 | 먼저 할 것 | 그래도 안 되면 |
|---|---|---|
| 피크에 API p95가 1초를 넘거나 CPU 크레딧이 바닥남 | 느린 쿼리부터 고침 → 서버 크기 올리기 | 앱 서버 2대 + 로드 밸런서 (이때 배치 실행 잠금·무중단 배포가 필요해짐) |
| 업무 시간 중 멈춤을 허용할 수 없게 됨 | | 앱 2대 + RDS 다중 AZ |
| 하루치 데이터 손실을 허용할 수 없게 됨 | binlog를 서버 밖에 보관 | RDS 시점 복구 |

---

## 1. 역할과 비밀값

| 누가 | 무엇 |
|---|---|
| 사용자 | AWS 계정 생성(배포 직전에), 콘솔 작업(인스턴스·보안 그룹·키 페어·크레딧 모드, 끄고 켜기), DuckDNS 로그인과 주소 발급, 메일 서비스 계정 준비(SMTP 주소·계정·발신 주소), 서버에서 비밀값 입력 스크립트 실행, 첫 관리자 로그인과 비밀번호 변경, 첫 설정(4.1) |
| Codex | 저장소 변경: 2장 앱 설정과 테스트, 3~6장 `deploy/` 스크립트·설정 파일, 운영 문서(runbook), 측정 기록 문서 |
| Claude | 프론트 운영 설정(8장), 리뷰, 사용자 허락을 받고 SSH로 서버 구성·배포 실행, 측정과 기록 |

**비밀값 규칙**
- 비밀값(DB 비밀번호 2개, `JWT_SECRET`, 초기 관리자 정보, SMTP 계정·비밀번호, DuckDNS 토큰, 웹후크 주소)은 **저장소·채팅·명령 인자·로그에 쓰지 않습니다.**
- `JWT_SECRET`과 DB 비밀번호는 서버에서 `openssl rand`로 만들어 **파일에만** 씁니다(화면에 출력하지 않음).
- 초기 관리자 정보와 SMTP 값은 사용자가 서버에서 `setup-secrets.sh`를 직접 실행해 **숨김 입력**합니다. 관리자가 만들어진 뒤에는 `INITIAL_ADMIN_PASSWORD`를 파일에서 지웁니다.
- 초기 관리자 비밀번호는 앱의 비밀번호 정책(8~20자, 대문자·소문자·숫자·정해진 기호 `!@#$%^&*()_+-=[]{};':"\|,.<>/?`)을 따릅니다. 스크립트도 같은 기준으로 검사하고, 운영 프로필은 네 값이 다 있는데 정책에 맞지 않으면 **기동을 실패**시킵니다(KI-14). 전에는 스크립트가 `~`·공백·한글도 받아 주고, 앱은 경고 한 줄만 남긴 채 관리자 없이 떴습니다(2026-11-17 로컬 점검에서 재현).
- 비밀값 파일: `/etc/workflow/env` (root:root, 600). systemd가 root로 읽어 앱 프로세스에 넘깁니다.

---

## 2. 앱 설정 (Codex)

### 2.1 `application-prod.properties` (저장소에 올림, 값은 환경 변수만)

| 설정 | 값 | 이유 |
|---|---|---|
| `server.address` / `server.port` | `127.0.0.1` / `8080` | 바깥은 Nginx만 |
| `server.shutdown`, `spring.lifecycle.timeout-per-shutdown-phase` | `graceful`, `20s` | 재시작 때 처리 중인 요청을 끝내고 내려감 |
| `server.forward-headers-strategy` | `native` | Nginx가 보낸 `X-Forwarded-*`를 신뢰(내부 프록시 127.0.0.1) |
| `spring.datasource.*` | `jdbc:mariadb://127.0.0.1:3306/mini_erp`, `${DB_APP_USERNAME}`, `${DB_APP_PASSWORD}` | 실행용 계정 |
| `spring.flyway.user` / `password` | `${DB_MIGRATOR_USERNAME}` / `${DB_MIGRATOR_PASSWORD}` | Flyway만 DDL 권한 계정으로(스프링이 별도 DataSource를 만듦) |
| `spring.jpa.show-sql` | `false` | |
| `springdoc.api-docs.enabled`, `springdoc.swagger-ui.enabled` | `false` | 운영에서 API 문서 닫음 |
| `management.server.address` / `port` | `127.0.0.1` / `8081` | 헬스 체크를 앱 포트와 분리 |
| `management.endpoints.web.exposure.include` | `health` | 11장 8절: health만 |
| `management.endpoint.health.show-details` | `never` | 상태만 |
| `app.mail.enabled` | `${APP_MAIL_ENABLED:false}` (운영 비밀값 파일에서 `true`) | 켜져 있으면 아래 SMTP 값이 모두 있어야 기동(`ProdSecretsConfig`) |
| `spring.mail.host` / `port` / `username` / `password` | `${SMTP_HOST}` / `${SMTP_PORT:587}` / `${SMTP_USERNAME}` / `${SMTP_PASSWORD}` | STARTTLS 587 |
| `spring.mail.properties.mail.smtp.connectiontimeout` / `timeout` / `writetimeout` | `${SMTP_CONNECTION_TIMEOUT_MS:5000}` / `${SMTP_READ_TIMEOUT_MS:5000}` / `${SMTP_WRITE_TIMEOUT_MS:5000}` | 시간 제한이 없으면 메일 서버가 응답하지 않을 때 요청이 무한 대기(KI-12) |
| `app.mail.from` | `${APP_MAIL_FROM}` | 메일 서비스가 승인한 발신 주소 |
| `app.invitation.setup-url` | `${APP_INVITATION_SETUP_URL}` | `https://<DuckDNS 주소>/password/setup`. https가 아니거나 localhost면 기동 실패 |
| `app.cors.allowed-origins` | 비움 | 같은 출처(Nginx)라 CORS 불필요 |
| 로그 | 콘솔만(journald가 받음) | 파일 로그를 따로 두지 않음 |

### 2.2 변경

1. 의존성 `spring-boot-starter-actuator` 추가 (11장 8절에서 허용, health만 씀).
2. `WebConfig` CORS 허용 출처를 `app.cors.allowed-origins`(기본 `http://localhost:5173`)로. 비어 있으면 매핑하지 않음.
3. 헬스: `127.0.0.1:8081/actuator/health`가 **인증 없이** `200 {"status":"UP"}`. DB나 Redis가 내려가면 `503 DOWN`. 8080 쪽 `/actuator/**`는 404. 보안 설정이 관리 포트를 막지 않는지 테스트로 확인.
4. 정리: `TaskOverdueWork`의 CLIENT_FOUND_ROWS·no-op upsert 옛 주석 삭제, `M4-18` 문서의 "ODKU를 사용하되" 문구를 최종 구현(`NOT EXISTS`)에 맞게.

### 2.3 테스트 (MariaDB Testcontainers, UTC JVM 포함)

| 테스트 | 확인 |
|---|---|
| DB 계정 분리 | migrator 계정으로 Flyway V1~V6 → app 계정(DML만)으로 컨텍스트 기동 → 대표 쓰기(로그인, 결재 상신·승인, 출근, 카드 이동, 마감 알림 배치 1회) 성공 → app 계정의 `DROP`·`ALTER`·`CREATE`는 권한 오류(1142) |
| 헬스 | 8081 health 200 UP(인증 없이), 8080 `/actuator/health` 404, swagger·api-docs 404 |
| 운영 프로필 | `prod` 프로필 로딩 시 비밀값이 없으면 기동 실패(조용히 기본값으로 뜨지 않음) |

---

## 3. 서버 구성 (`deploy/` 아래, Codex 작성 · Claude 실행)

```
deploy/
  README.md                    순서대로 따라 하는 운영 문서 (처음 설치 · 배포 · 되돌리기 · 백업 · 복구 · 종료)
  server/bootstrap.sh          패키지 · 사용자 · 폴더 · swap · 설정 파일 배치 (여러 번 실행해도 같은 결과, 이미 있는 시스템 폴더의 권한은 바꾸지 않음, 백업·배포와 같은 잠금)
  server/setup-secrets.sh      비밀값 파일 만들기 (사용자가 직접, 숨김 입력)
  server/workflow.service
  server/workflow-backup.service, workflow-backup.timer
  server/notify-failure@.service, notify.sh          (선택: 웹후크)
  server/batch-check.service, batch-check.timer       (선택: 배치 미실행 확인)
  server/nginx-workflow.conf, nginx-snippets/
  server/mariadb-60-workflow.cnf
  server/redis-workflow.conf
  server/journald-workflow.conf
  server/duckdns-update.service  부팅 때 현재 공인 IP로 DuckDNS 갱신 (토큰은 /etc/workflow/duckdns, root 600)
  server/restore-rehearsal.sh
  server/slow-endpoints.sh     접근 로그 → API별 횟수 · p50 · p95 · 최대
  bin/deploy.sh                (로컬 실행)
  bin/rollback.sh              (로컬 실행)
  bin/pull-backup.sh           (로컬 실행, 저장소 밖 ~/workflow-backups/ 로)
```

서버 폴더:

```
/opt/workflow/releases/<UTC시각>-<커밋7자리>/app.jar
/opt/workflow/current -> releases/...           (systemd가 실행)
/var/www/workflow/releases/<같은 이름>/         (React 빌드)
/var/www/workflow/current -> releases/...       (Nginx root)
/etc/workflow/env                               (root:root 600)
/var/backups/workflow/                          (root 700)
/var/lib/workflow/                              (힙 덤프)
```

**패키지**: `openjdk-17-jre-headless`, `mariadb-server`(Ubuntu 24.04 기본 10.11), `redis-server`, `nginx`, `certbot`, `sysstat`, `jq`. `unattended-upgrades`는 기본값(보안 업데이트 자동) 유지. **swap 2 GiB** 파일, `vm.swappiness=10`.

### 3.1 메모리 예산 (시작값 — 실제 RSS를 재고 고침)

| 프로세스 | 설정 | 예상 |
|---|---|---|
| MariaDB | `innodb_buffer_pool_size=512M`, `max_connections=40` | 약 700 MB |
| Spring Boot | `-Xms256m -Xmx512m -XX:+ExitOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/lib/workflow/heap.hprof`(파일로 고정해 첫 덤프 하나만 남김 — 폴더로 두면 OOM마다 수백 MB가 쌓이고, 덤프에는 비밀값·개인정보가 들어 있음) | 약 750 MB |
| Redis | `maxmemory 64mb` | 약 10 MB |
| Nginx · OS | | 약 250 MB |

- GC 종류 등 JVM 옵션은 **재기 전에 고치지 않습니다.** 어떤 GC가 골라졌는지만 기록합니다.
- Hikari 풀은 기본 10. DB `max_connections=40`은 관리·백업 접속 여유분.

### 3.2 MariaDB (`60-workflow.cnf`)

`bind-address=127.0.0.1`, `skip-name-resolve`, `character-set-server=utf8mb4`, `collation-server=utf8mb4_unicode_ci`, `innodb_buffer_pool_size=512M`, `max_connections=40`, `slow_query_log=1`, `long_query_time=1`, `log_slow_verbosity=query_plan,explain`. 격리 수준·`time_zone`은 기본값.

계정 (bootstrap이 root unix_socket으로 만듦, 비밀번호는 `/etc/workflow/env`에서 읽음):

| 계정 | 권한 |
|---|---|
| `workflow_migrator@127.0.0.1` | `mini_erp.*` 전체(GRANT OPTION 제외) |
| `workflow_app@127.0.0.1` | `mini_erp.*`에 `SELECT, INSERT, UPDATE, DELETE`만 |
| `root@localhost` | unix_socket (백업·관리, 비밀번호 없음) |

> **2026-10-28 변경: 앱·Flyway 계정 `@localhost` → `@127.0.0.1`.** 앱은 JDBC로 `127.0.0.1`에 TCP 접속합니다. `skip-name-resolve`를 켜면 MariaDB가 접속 주소를 이름으로 바꾸지 않아 TCP 접속이 `@localhost`(unix socket 전용) 계정과 맞지 않고 1045(접근 거부)가 납니다(구현 중 재현, `docs/troubleshooting/M5-05`). 버린 방법: `@'%'`(loopback보다 허용 범위가 넓음), `skip-name-resolve` 끄기(이름 해석에 기대게 됨), 앱을 unix socket으로 연결(JDBC TCP 구성을 바꿔야 함).

### 3.3 Redis

`bind 127.0.0.1 -::1`, `protected-mode yes`, `maxmemory 64mb`. 비밀번호는 두지 않음(로컬 전용, 같은 서버의 다른 프로세스는 우리 것뿐) — 이 한계를 runbook에 적음.

### 3.4 systemd `workflow.service`

- `User=workflow`(로그인 셸 없는 시스템 사용자), `EnvironmentFile=/etc/workflow/env`, `ExecStart=/usr/bin/java $JAVA_OPTS -jar /opt/workflow/current/app.jar --spring.profiles.active=prod`
- `After=`·`Wants=` `mariadb.service redis-server.service`
- `Restart=on-failure`, `RestartSec=5`, `SuccessExitStatus=143`, `TimeoutStopSec=30`
- 보호: `NoNewPrivileges=yes`, `ProtectSystem=strict`, `ProtectHome=yes`, `PrivateTmp=yes`, `ReadWritePaths=/var/lib/workflow`
- (선택) `OnFailure=notify-failure@%n.service`

### 3.5 Nginx

- **80**: `/.well-known/acme-challenge/`만 웹루트에서 응답, 나머지는 443으로 리다이렉트.
- **443** (`http2`, `server_tokens off`, `client_max_body_size 1m` — 파일 업로드 없음):
  - `/` → `/var/www/workflow/current`, `try_files $uri /index.html`. `index.html`은 `Cache-Control: no-cache`, `/assets/`(해시 붙은 파일)는 1년 `immutable`. gzip(js·css·json·svg).
  - `/api/` → `http://127.0.0.1:8080`, `X-Real-IP`·`X-Forwarded-For`·`X-Forwarded-Proto` 전달, `proxy_read_timeout 30s`.
  - `/api/v1/auth/` → 위와 같되 **IP당 분당 10회, burst 5** 제한(로그인·비밀번호 찾기 대입 공격). 넘으면 429 JSON.
  - `/healthz` → `127.0.0.1:8081/actuator/health` (상태만).
  - 앱이 내려가 있을 때(502·503·504): `/api/`는 `503` + `{"success":false,"error":{"code":"SERVICE_UNAVAILABLE","message":"서버를 점검하고 있습니다. 잠시 후 다시 시도해 주세요."}}`, 화면 경로는 `/maintenance.html`.
- **보안 헤더** (모든 응답, `always`): `Strict-Transport-Security: max-age=86400`(확인 뒤 1년으로), `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`, `Permissions-Policy: camera=(), microphone=(), geolocation=()`, CSP:
  ```
  default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net;
  font-src 'self' https://cdn.jsdelivr.net data:; img-src 'self' data:; connect-src 'self';
  frame-ancestors 'none'; base-uri 'self'; form-action 'self'
  ```
  - 토큰을 `localStorage`에 두므로 스크립트 출처를 우리 서버로만 묶는 CSP가 중요합니다. 폰트(Pretendard)는 jsDelivr라 style·font만 허용.
  - `Referrer-Policy`는 `/password/setup?setupToken=…` 화면이 외부 폰트를 불러올 때 **토큰이 담긴 주소가 Referer로 새지 않게** 합니다.
  - `location`에서 `add_header`를 쓰면 서버 블록의 헤더가 상속되지 않으므로 헤더는 snippet으로 만들어 각 `location`에 include.
- **접근 로그는 JSON** (`escape=json`): `time_iso8601`, `remote_addr`, `request_method`, **`$uri`(쿼리 문자열 제외 — 토큰이 로그에 남지 않게)**, `status`, `body_bytes_sent`, `request_time`, `upstream_response_time`, `upstream_status`.

### 3.6 HTTPS

`certbot certonly --webroot`로 발급(설정 파일은 저장소의 템플릿이 관리하므로 certbot이 Nginx 설정을 고치지 않게). 갱신은 패키지의 `certbot.timer`, `deploy-hook`으로 `systemctl reload nginx`. 인증서가 없을 때는 80만 여는 설정으로 먼저 띄우고 발급 뒤 443 설정으로 바꿈(bootstrap이 처리).

### 3.7 접속

AWS 보안 그룹: 22(내 IP/32), 80·443(전체). SSH는 키만(Ubuntu AMI 기본), root 로그인 금지 — `sshd -T`로 확인. 서버 방화벽(ufw)은 두지 않음: 보안 그룹 + 127.0.0.1 바인딩으로 충분하고, 잘못 켜면 SSH가 잠김.

---

## 4. 배포 · 되돌리기

`deploy/bin/deploy.sh <서버주소>` (로컬):

1. 작업 트리가 깨끗한지, 배포할 커밋이 무엇인지 확인. 릴리스 이름 = `<UTC시각>-<커밋7자리>`.
2. 백엔드 `./mvnw -DskipTests clean package`(지난 빌드의 파일, 특히 지우거나 이름을 바꾼 마이그레이션 SQL이 jar에 섞이지 않게), 프론트 `npm ci && npm run build`. **테스트 통과는 배포 전에 리뷰에서 확인**합니다(스크립트는 빌드만).
3. 저장소에 서버의 `flyway_schema_history` 최신 버전보다 큰 `V` 파일이 있으면 **배포 전에 백업 1회**.
4. 릴리스 폴더로 전송, 최근 3개만 남김.
5. 백엔드 링크 교체 → `systemctl restart workflow` → `/healthz` 확인. 기다리는 시간은 **스키마 버전이 올라가는 배포 900초, 그 외 배포와 되돌리기 90초**입니다(Flyway 실행 시간이 기동 시간에 들어가므로. 큰 DDL 도중에 재시작하면 MariaDB DDL은 되돌아가지 않음, 2026-10-28 리뷰). 대기 중 systemd의 `NRestarts`가 늘거나 `ActiveState=failed`가 되면 제한을 채우지 않고 바로 실패로 봅니다. 로그: `HEALTH_WAIT limit_s=… migration=YES|NO result=…`.
   - 성공: 프론트 링크 교체. (백엔드가 먼저 — 새 화면이 옛 API를 부르는 순간을 없앰)
   - 실패: 직전 릴리스로 링크를 되돌리고 재시작, 헬스 확인, 실패 이유(최근 로그 50줄)를 보여 주고 종료 코드 1.
6. 기록: 릴리스 이름, 단계별 걸린 시간, 로그의 `Started BackendApplication in N seconds`.
7. 서버 쪽 활성화·되돌리기는 **SSH 연결이 끊겨도 끝까지 진행**하고 진행 로그와 결과를 서버에 남깁니다. 로컬은 그 로그를 따라 읽고 종료 코드를 받습니다(대기가 최대 15분이라 그사이 노트북 잠자기·네트워크 변경으로 끊기면 링크가 반쯤 바뀐 채 멈출 수 있음, 2026-10-28 재리뷰).

`deploy/bin/rollback.sh <서버주소>`: 직전 릴리스로 교체 + 재시작 + 헬스.

**마이그레이션 규칙**
- 운영에 적용된 `V` 파일은 **절대 고치지 않습니다.** 바꿀 게 생기면 V7부터 새 파일. (`docs/known-issues.md`의 V2 체크섬 변경 같은 일은 운영 DB에서는 허용하지 않음)
- jar를 되돌려도 스키마는 되돌아가지 않습니다. 새 마이그레이션은 **이전 버전 코드와 같이 돌 수 있게**(컬럼 추가 먼저, 삭제는 다음 배포) 만들고, 그게 안 되면 되돌리기 = 배포 직전 백업 복구입니다.
- 큰 테이블의 DDL은 배포 전에 최신 덤프를 로컬 MariaDB 10.11에 복구해 **같은 DDL의 시간을 먼저 잽니다.** 900초를 넘을 것 같으면 배포 스크립트로 하지 않고 점검 시간을 잡아 따로 진행합니다.
- 03:30 백업 시간에는 배포하지 않습니다(`--single-transaction` 덤프 중 DDL이 겹치면 덤프가 실패함).

### 4.1 첫 설정 (처음 설치한 뒤 한 번, 사용자)

2026-11-17 로컬 점검(가상 회사 대표 1·부서 5·직원 15를 실제 API와 Mailpit으로 등록)에서, 이 순서를 지키지 않으면 사원이 아무것도 상신하지 못하거나 로그인부터 막히는 것을 확인했습니다. 코드로 막을 문제가 아니라 설정 순서의 문제라서 여기에 둡니다.

| 순서 | 할 일 | 안 하면 생기는 일 |
|---|---|---|
| 1 | 관리자로 로그인 → 비밀번호 변경. 그 뒤 `clear-initial-password.sh` | 변경 전에는 다른 API가 모두 403 |
| 2 | **시드 이름 정리**: 직급 "관리소장", 부서 "개발팀·유지보수팀·모바일개발팀"은 v1에서 이관한 기본값입니다. 회사에 맞게 이름을 바꾸거나 정리합니다 | 새 설치에도 옛 이름이 그대로 보임 |
| 3 | 직급을 새로 만들었으면 **직급 순서를 다시 저장**(부서 관리 → "직급" 카드, 위에서부터 낮은 직급 → 높은 직급) | 새 직급은 순위 0이라 맨 앞(가장 낮은 직급)에 들어감 |
| 4 | **최상위 부서("회사")에 부서장(대표) 지정**, 그다음 각 부서장 지정 | 휴가·품의의 2단계("기안자의 상위 부서장")를 찾지 못해 **아무도 상신할 수 없음**. 팀장의 특근·근태 정정도 대표에게 올라가므로 상신할 수 없음(13장 1.3, KI-15). 화면 안내는 "관리자에게 문의"뿐이라 원인을 찾기 어려움 |
| 5 | 사원 등록 → 초대 메일 수신 확인(스팸함 포함). 메일에 로그인 아이디가 들어 있음 | 메일이 안 오면 사원이 첫 비밀번호를 정할 수 없음 |
| 6 | **사원 등록을 마친 뒤** 연차 부여를 수동으로 한 번 실행(연차 관리 → 부여 실행) | 자동 부여는 매일 00:30(KST) 배치라, 등록한 날에는 연차가 0. 부여 실행 **뒤에** 등록한 사원도 다음 날 00:30까지 0이므로, 같은 날 추가 등록했다면 다시 실행(이미 부여한 몫은 다시 주지 않음) |
| 7 | 시험 계정으로 휴가 상신 → 결재선 미리보기에 오류가 없는지 확인 | 4번 누락을 상신하는 사원이 처음 발견하게 됨 |

> 2026-11-17: 직급 API(12장 4절)는 있었지만 직급 관리 화면이 없어 2·3번의 직급 부분을 화면에서 할 수 없었습니다. → 2026-11-22: 부서 관리 화면 안 "직급" 카드로 추가(KI-16, `fix/onboarding-and-approval-line`, develop 병합 전). 이 카드는 DEPARTMENT 권한이 있는 사람만 봅니다.
>
> 2026-11-22 확인: 6번은 같은 날 두 번 실행해도 이미 부여된 사원에게 다시 주지 않았습니다(로컬 점검, 두 번째 실행 `processedCount=1`로 새로 등록한 대표만 부여).

---

## 5. 백업 · 복구 리허설

- `workflow-backup.timer`: `OnCalendar=*-*-* 03:30:00 Asia/Seoul`, `Persistent=true`(서버가 꺼져 있었으면 켜진 뒤 실행).
- 백업: `mariadb-dump --single-transaction --quick --routines --triggers --events --hex-blob --default-character-set=utf8mb4 mini_erp | gzip` → 임시 파일 → 성공하면 이름 변경 → sha256·Flyway 버전 기록 → **파일 이름(UTC 시각) 순으로 최근 7세트만 남김**(덤프와 짝 파일 `.sha256`·`.version`·`.counts.tsv`, 배포 전 백업도 같은 세트로 셈). `set -euo pipefail`(덤프가 중간에 실패하면 반쯤 쓴 파일을 남기지 않음).
  - 일수가 아니라 세트 수로 셉니다(2026-10-28 리뷰). 끄고 켜는 운영에서 "7일 지난 파일 삭제"는 7일 넘게 꺼 두었다가 켜는 순간 이전 백업을 모두 지워 한 세대만 남깁니다. 세트 수는 디스크 총량의 상한이 아니고, 배포가 잦으면 오래된 세대가 밀려납니다.
  - `--single-transaction`이라 InnoDB 읽기·쓰기를 막지 않습니다.
- `restore-rehearsal.sh`(서버): 최신 덤프를 `mini_erp_restore`에 복구 → 테이블별 `COUNT(*)`를 원본과 비교 → `flyway_schema_history` 최신 버전 일치 확인 → 걸린 시간 출력 → `mini_erp_restore` 삭제. 쓰기가 없는 시간에 실행하고, 다르면 테이블 이름과 차이를 출력. 성공하면 테이블별 건수를 `.counts.tsv`로 백업 옆에 저장합니다(로컬 복구는 이 파일이 있는 세트만 받으므로, 자동 백업을 내려받기 전에 먼저 리허설).
- **서버가 통째로 사라지는 경우**: `pull-backup.sh`로 받은 파일을 로컬 Docker MariaDB 10.11에 복구해 같은 비교를 1회 기록.

---

## 6. 관측 (다음 단계 준비)

| 질문 | 도구 |
|---|---|
| 어느 API가 느린가 | Nginx JSON 접근 로그 → `slow-endpoints.sh`: 경로의 숫자를 `{id}`로 묶어 API별 횟수·p50·p95·최대 `request_time` |
| 그 API의 어느 쿼리인가 | MariaDB 느린 쿼리 로그(평소 1초, 문제 찾기 단계에서 `SET GLOBAL long_query_time=0.2`로 잠시 낮춤) + `mariadb-dumpslow` |
| 그때 서버가 바빴나 | sysstat 1분 간격(`sar -u -r -d`), CloudWatch 기본 지표(CPU·`CPUCreditBalance`·디스크·네트워크) |
| 앱이 죽었다 살아났나 | `journalctl -u workflow`, journald `SystemMaxUse=300M` |
| 배치가 돌았나 | `batch_runs` 테이블. (선택) `batch-check.timer`가 09:10에 오늘 결근 판정·마감 알림 기록이 `SUCCESS`인지 보고, 없거나 `FAILED`면 웹후크 |

---

## 7. 비용 · 종료 계획

| 항목 | 서울 리전 요금 | 한 달 |
|---|---|---|
| t3.small | $0.026/시간 | 약 $19 |
| 공인 IPv4(자동) | $0.005/시간, 켜 있는 동안만 | 약 $3.6 |
| gp3 30 GiB | 꺼 둬도 붙음 | 약 $3 |
| 합계 (24시간 켜 둘 때) | | **약 $25** |
| 합계 (꺼 둘 때) | 디스크만 | **약 $3** |

- 가입 크레딧 $100이면 24시간 켜 둘 때 약 4개월입니다. 실험할 때만 켜고 끄면 훨씬 오래 갑니다. 부하 실험은 standard 크레딧 모드라 추가 요금이 붙지 않습니다.
- 같은 계정에서 다른 프로젝트도 돌리면 **크레딧을 나눠 씁니다.** 동시에 켜 두면 두 배로 줄어듭니다.
- 10장의 4주 상시 운영은 약 $25입니다. 끝나면 실험할 때만 켜는 방식으로 돌아갑니다.
- **끄기는 stop**(디스크와 데이터 유지). terminate는 마지막에 최종 백업을 로컬로 받은 뒤에만 합니다.
- 무료 플랜은 6개월이 지나거나 크레딧을 다 쓰면 계정이 닫히고, **유료로 바꾸지 않으면 청구되지 않습니다.** 닫힌 뒤 90일이 지나면 데이터가 지워집니다.
- **계정은 배포 직전에 만듭니다**(6개월이 가입일부터 셈).
- **종료 전 체크리스트**: 최종 백업을 로컬로 받기 · 시연 영상과 화면 캡처(주소창 포함) · README에 운영 기간, 지금은 종료됨, `deploy/`로 다시 올리는 방법 기록.
- 다른 서버로 옮길 때: Ubuntu 서버에 `bootstrap.sh` → 비밀값 → 백업 복구 → 배포 → DuckDNS의 IP만 바꿈.

---

## 8. 프론트 운영 설정 (Claude)

- 운영 빌드(`import.meta.env.PROD`)는 `VITE_API_BASE_URL`이 없으면 **`/api/v1`**(Nginx 같은 출처). 저장소 `.gitignore`가 `**/.env.*`를 막으므로 `.env.production` 파일 대신 코드 기본값으로 둡니다.
- `public/maintenance.html`: 외부 리소스 없는 정적 점검 안내.
- 503 `SERVICE_UNAVAILABLE`, 429를 받으면 기존 에러 메시지 경로로 안내가 보이는지 확인.
- 운영 빌드에 가짜 서버(mock)가 들어가지 않았는지 빌드 결과에서 확인.
- CSP 아래에서 주요 화면(로그인·홈·결재·근태·칸반) 콘솔에 위반 0.

---

## 9. 완료 기준 — 측정과 기록 (작은 데이터 기준값)

기록은 `docs/troubleshooting/M5-xx.md`. 수치마다 측정 시각, 릴리스 이름, `CPUCreditBalance`, 서버를 켠 뒤 지난 시간을 같이 적습니다. 껐다 켠 직후에는 DB 버퍼 풀과 OS 캐시가 비어 있으므로, 측정 전에 같은 요청으로 한 번 예열하고 예열 전·후를 구분해 적습니다.

| # | 항목 | 방법 | 남길 것 |
|---|---|---|---|
| 1 | 외부 노출 | 바깥에서 3306·6379·8080·8081 연결, 다른 IP에서 22, `http://` 접속, `/swagger-ui/`·`/v3/api-docs`·`/actuator/health` | 모두 실패·리다이렉트·404인 출력 |
| 2 | 바인딩 | 서버 `ss -ltnp` | 8080·8081·3306·6379가 127.0.0.1만 |
| 3 | 보안 헤더 · CSP | `curl -I`, 주요 화면 콘솔 | 헤더 목록, CSP 위반 수 |
| 4 | 인증서 | `certbot renew --dry-run` | 성공, 만료일 |
| 5 | DB 권한 | 운영 서버에서 `workflow_app`으로 `DROP TABLE` 시도 | 권한 오류 |
| 6 | 기동 시간 | 재시작 3회 | `Started … in N s`, 헬스 UP까지 초 |
| 7 | **배포 중 끊김** | 배포하는 동안 0.2초마다 API 요청(3회) | 실패(503) 응답 수와 구간 길이, 그동안 화면 메시지 |
| 8 | 메모리 | 기동 직후 / 주요 화면을 한 바퀴 쓴 뒤 | java·mariadbd RSS, `free -m`, swap 사용량, 골라진 GC |
| 9 | 백업 · 복구 | 백업 1회, 서버 리허설 1회, 로컬 Docker 복구 1회 | 덤프 크기·시간, 복구 시간, 테이블 행 수 일치 |
| 10 | 자동 되돌림 | 헬스가 실패하는 시험용 빌드를 일부러 배포(예: 없는 환경 변수를 요구하게 바꾼 jar) | 되돌아온 릴리스, 걸린 시간 |
| 11 | 배치 시간대 | 다음 날 `batch_runs` | UTC 서버에서 결근 판정 00:10·마감 알림 09:00(KST)에 `SUCCESS` |
| 12 | 비밀값 | `git log -p`·저장소 검색, `/etc/workflow/env` 권한, `journalctl` 검색 | 비밀값 노출 0, 파일 600 root |
| 13 | 운영 기록 | 10장 | 4주 운영 일지, 장애 훈련 기록, 운영 지표 요약 |
| 14 | 메일 | 초대·아이디 찾기·비밀번호 재설정 메일을 실제 주소로 1통씩 | 도착까지 걸린 시간, 스팸함 여부, 링크가 운영 주소(https)인지. 메일 본문·토큰은 기록하지 않음 |
| 15 | 첫 설정 | 4.1 순서대로 | 단계마다 걸린 시간, 막힌 곳 |

---

## 10. 운영 기록 (포트폴리오 주요 결과물)

실제 사용자는 없습니다. 대신 **운영 중에 실제로 생기는 사건**과 **일부러 일으킨 장애**를 다루고, 감지하고 복구하기까지의 시간을 기록합니다. 실제 사용자가 없다는 점과 합성 트래픽을 썼다는 점은 기록과 포트폴리오에 밝힙니다.

### 10.1 운영 기간과 상시 트래픽

- 1~9장을 마친 뒤 **4주 동안 서버를 끄지 않고** 운영합니다.
- 평일 업무 시간(09:00~18:00 KST)에는 합성 계정 몇 개로 가벼운 요청을 계속 보냅니다(`deploy/loadtest/steady.js`, Codex). 초당 1~2건, 홈·근태·결재함·알림 위주입니다. 장애가 났을 때 "사용자 요청이 몇 건 실패했는가"를 재기 위해서입니다.
- 이 단계의 데이터는 작은 데이터 그대로입니다. 회사 규모 데이터는 명세 18에서 넣습니다.
- 서버 밖에서 가동 여부를 확인하는 무료 외부 서비스는 계정이 필요하므로 사용자가 결정합니다. 쓰지 않으면 합성 트래픽의 성공·실패 기록으로 대신합니다.

### 10.2 실제로 생기는 사건 (자동으로 기록)

| 사건 | 기록할 것 | 출처 |
|---|---|---|
| 배포 | 배포마다 끊긴 시간과 실패 요청 수, 되돌림 여부 | `deploy.sh` 기록, 합성 트래픽 |
| 야간 배치 | 매일 결근 판정·마감 알림의 성공 여부와 걸린 시간 | `batch_runs`, `batch-check` |
| 백업 | 매일 성공 여부, 크기, 걸린 시간 | 백업 로그 |
| 인증서 | 갱신 확인 결과, 남은 기간 | `certbot` |
| 재부팅·IP 변경 | DuckDNS 갱신, 서비스 자동 시작, 첫 응답까지 시간 | journald, 합성 트래픽 |
| 외부 비정상 요청 | 공개 주소라 봇과 스캐너 요청이 실제로 들어옴. 상태 코드별 수, 많이 두드린 경로 | Nginx 접근 로그 |
| 서버 오류 | 500 발생 수와 원인 (KI-11 로그 기준) | journald |
| 자원 추이 | CPU 크레딧, 메모리, swap, 디스크 사용량 | sysstat, CloudWatch |

### 10.3 장애 훈련 (일부러 일으킴)

업무 시간 밖에, 백업을 받은 뒤, 한 번에 하나씩 합니다. 합성 트래픽을 켜 두고 진행합니다.

| 훈련 | 방법 | 기대 동작 | 잴 것 |
|---|---|---|---|
| 앱 강제 종료 | `kill -9`로 Java 프로세스 종료 | systemd가 다시 띄우고 웹후크가 알림 | 감지 시간, 복구 시간, 실패 요청 수 |
| 잘못된 빌드 배포 | 9장 10번과 같음 | 자동으로 직전 릴리스로 되돌림 | 되돌림까지 시간, 실패 요청 수 |
| DB 중지 | `systemctl stop mariadb` 후 다시 시작 | 헬스가 DOWN, 오류가 로그에 남고, DB가 돌아오면 앱이 재시작 없이 회복 | 감지 방법, 회복까지 시간, 재시작이 필요했는지 |
| Redis 중지 | `systemctl stop redis-server` 후 다시 시작 | 영향받는 기능을 확인 | 영향 범위, 회복 방법 |
| 서버 재부팅 | 인스턴스 재부팅 | IP가 바뀌어도 주소가 다시 연결되고 모든 서비스가 자동으로 시작 | 접속 가능해지기까지 시간 |
| 백업으로 복구 | 5장 리허설과 로컬 복구 | 행 수 일치 | 복구 시간을 목표(1시간)와 비교, 잃는 데이터 범위 |

기대와 다르게 동작한 훈련은 숨기지 않고 사건 기록으로 남기고, 고친 뒤 다시 훈련합니다.

### 10.4 기록 형식

- **운영 일지** `docs/troubleshooting/M5-ops-log.md`: 날짜, 사건, 감지 방법, 감지까지 시간, 조치, 복구까지 시간, 사용자 영향(실패 요청 수), 후속 조치를 한 줄씩 적습니다. 아무 일 없던 날도 배치·백업 결과는 남깁니다.
- **사건 기록** `docs/troubleshooting/M5-ops-xx-<주제>.md`: 기대와 다르게 동작했거나 의미 있는 사건만 씁니다. 증상 → 시간순 경과 → 원인 → 조치 → 재발 방지 순서입니다.
- **운영 지표 요약** `docs/troubleshooting/M5-ops-summary.md`: 4주가 끝나면 아래 값을 정리합니다.

| 지표 | 계산 |
|---|---|
| 가동률 | 합성 요청 성공 비율 (업무 시간 기준) |
| 배포 | 횟수, 평균·최대 끊김 시간, 되돌림 횟수 |
| 감지 시간 | 장애 훈련별 발생부터 알림·로그 확인까지 |
| 복구 시간 | 장애 훈련별 발생부터 정상 응답까지, 목표 1시간과 비교 |
| 데이터 손실 한도 | 백업 주기 기준 최대 손실 범위 |
| 외부 비정상 요청 | 전체 요청 중 비율, 4xx·5xx 비율 |

---

## 11. 하지 않는 것

| 하지 않음 | 이유 |
|---|---|
| 무중단 배포 | 0장. 9장 7번 측정 뒤 판단 |
| Docker · 오토스케일링 · 로드 밸런서 | AD-08, 서버 한 대 |
| RDS · S3 · CDN | 0장. 비용·설정 대비 이득이 작음 |
| GitHub Actions 자동 배포 | 배포 키를 외부에 두는 위험 대비, 사람이 확인하고 배포하는 지금 흐름으로 충분 |
| 운영 규모 데이터 넣기 · 부하 실험 | 다음 단계(문제 찾기)에서. 그 전에 이 단계의 기준값을 먼저 남김. 0.1의 가정(피크 초당 30건을 한 대로 버팀)도 그때 부하 도구로 확인 |
