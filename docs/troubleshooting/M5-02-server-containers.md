# M5-02 — 한 대 서버의 설정·비밀값·부팅 DNS

## 증상

배포할 서버의 패키지·권한·포트·메모리·로그 회전 설정이 코드로 재현되지 않았습니다. 중지 후 공인 IP가 바뀌는 운영에서는 DNS 갱신도 부팅 절차에 필요합니다.

## 재현 조건과 방법

2026-10-28, 로컬 Docker의 `ubuntu:24.04`(arm64), `nginx:1.24`, `mariadb:10.11`에서 확인했습니다. 실제 EC2는 x86_64이므로 이 검증을 EC2 성능 결과로 해석하지 않습니다. 호스트 개발 DB/Redis 포트를 사용하지 않았습니다.

- Ubuntu 컨테이너에 `deploy/server`를 읽기 전용 마운트하고 `bash /work/server/bootstrap.sh --no-systemd --domain workflow.example.test` 실행.
- 재실행 전후 설치한 스크립트·설정·유닛 72개 파일의 SHA-256·uid/gid·mode 비교(해시 자체 및 비밀값 출력 안 함).
- Nginx 이미지에 렌더링한 설정과 임시 자체 서명 인증서를 마운트하고 `nginx -t`. 추가로 Ubuntu의 Nginx 1.24에서 localhost 가짜 8080/8081 upstream으로 HTTP 상태·헤더 확인.
- MariaDB 이미지에 `60-workflow.cnf`를 마운트하고 느린 로그 폴더를 mysql 소유로 준비, 호스트 포트 없이 기동. root socket으로 SHOW VARIABLES 확인.
- DuckDNS는 네트워크 전송과 파일 읽기를 모킹. `python3 deploy/checks/offline-tools.py`로 성공/거절/예외를 재실행할 수 있습니다. 실제 IMDS·DuckDNS 업데이트 요청은 0회입니다.
- Ubuntu의 가상 터미널에서 임의 생성 입력값 7개로 setup-secrets를 실행. 특수문자 보존·재실행 파일 동일·초기 비밀번호 제거를 확인. 토큰/비밀번호 원문을 출력하지 않았습니다.

## 원인

배포를 수동 명령 모음으로만 남기면 폴더 권한·로그 설정·재실행 결과가 달라질 수 있습니다. Nginx의 header 상속과 Ubuntu 패키지의 logrotate wildcard도 실제 설치 환경에서 확인해야 합니다.

## 검토했지만 버린 방법과 이유

| 결정 | 버린 대안과 이유 |
|---|---|
| 자동 공인 IP + 부팅 DuckDNS | Elastic IP: 사용자가 최신 명세 2027ebd 적용을 선택해 중지 중 고정 IP 비용을 피함 |
| IMDSv2 + HTTPS 갱신 | 외부 IP 조회 서비스·IMDSv1: 불필요한 외부 의존 또는 토큰 없는 메타데이터 요청을 피함 |
| root 600 파일·숨김 입력 | CLI 옵션·환경값 출력: 프로세스 목록/로그/채팅에 비밀값을 남길 수 있어 제외 |
| 기존 env 파일 그대로 보존 | 읽고 다시 인용해 재작성: 특수문자 재해석 위험이 있어 재실행 시 바이트 그대로 유지 |
| root 소유 릴리스, workflow nologin | 앱 사용자에게 릴리스 수정 권한: 실행 파일 변조 권한이 불필요하므로 제외 |
| 메모리 시작값 512M/512M/64M | 측정 전 GC·풀 추가 튜닝: 근거가 없으므로 제외 |
| Nginx 페이지 health subrequest | 장애 중 정적 index만 200: 사용자가 장애를 늦게 알아차리므로 제외. 페이지마다 추가 health 비용 수용 |
| 보안 헤더 snippet을 각 응답 위치에 include | 부모 add_header 상속에 의존: 자식 cache header 때문에 사라질 수 있어 제외 |
| 사이트 error_log 비활성 | 요청 쿼리가 포함되는 원문 오류 로그: 초대/재설정 토큰 유출 가능. 대신 access의 status/upstream/time과 시작 로그 사용 |
| /var/log/mysql/workflow/slow.log | /var/log/mysql/workflow-slow.log: Ubuntu 기본 /var/log/mysql/*.log와 중복 회전되므로 제외 |
| sysstat 1분 + journald 300M | 별도 관측 서비스 추가: 현재 한 대 메모리/운영 범위에 비해 큼 |

## 선택한 방법

bootstrap은 패키지·폴더·계정 기반·swap 설정·Nginx/MariaDB/Redis·systemd/journald·타이머·SSH 키 전용 정책을 배치합니다. 컨테이너 모드에서는 패키지 서비스 자동 시작을 막고 systemd·swap·sysctl 적용을 건너뜁니다. 비밀값 준비 뒤 실제 서버 bootstrap은 DB 권한과 DuckDNS 갱신을 설정합니다.

검증 중 `/dev/tty`를 Python 텍스트 r+로 여는 방식이 가상 터미널에서 거부되어 숨김 입력을 시작하지 못했습니다(입력 prompt 0개, exit 1). 파일 디스크립터 `os.open` + `os.isatty` 검사로 바꾼 뒤 7개 입력과 재실행이 통과했습니다. 입력을 평문 stdin으로 받는 fallback은 채택하지 않았습니다.

## 결과 — 출력 원문

### Nginx 1.24 전용 컨테이너

```text
nginx: the configuration file /checks/nginx.conf syntax is ok
nginx: configuration file /checks/nginx.conf test is successful
```

### MariaDB 10.11 전용 컨테이너

```text
VERSION()
10.11.19-MariaDB-ubu2204-log
Variable_name	Value
bind_address	127.0.0.1
innodb_buffer_pool_size	536870912
log_slow_verbosity	query_plan,explain
long_query_time	1.000000
max_connections	40
slow_query_log	ON
slow_query_log_file	/var/log/mysql/workflow/slow.log
time_zone	SYSTEM
tx_isolation	REPEATABLE-READ
```

### Ubuntu 재실행·권한·Redis

```text
workflow:x:999:994::/var/lib/workflow:/usr/sbin/nologin
nginx: the configuration file /etc/nginx/nginx.conf syntax is ok
nginx: configuration file /etc/nginx/nginx.conf test is successful
BOOTSTRAP files=72 changed=0 repeat=SUCCESS
MODE /etc/workflow uid=0 gid=0 mode=0o700
MODE /etc/workflow/env uid=0 gid=0 mode=0o600
MODE /etc/workflow/duckdns uid=0 gid=0 mode=0o600
MODE /var/backups/workflow uid=0 gid=0 mode=0o700
MODE /var/lib/workflow uid=999 gid=994 mode=0o700
MODE /opt/workflow/releases uid=0 gid=0 mode=0o755
MODE /var/www/workflow/releases uid=0 gid=0 mode=0o755
LOGROTATE parse=PASS duplicate_rules=0
REDIS bind 127.0.0.1 -::1 protected-mode yes maxmemory 67108864
```

### Nginx 실제 요청 (가짜 upstream)

```text
NGINX path=/ status=200 headers=6 cache=no-cache
NGINX path=/index.html status=200 headers=6 cache=no-cache
NGINX path=/projects/7/board status=200 headers=6 cache=no-cache
NGINX path=/assets/fixture.js status=200 headers=6 cache=public, max-age=31536000, immutable
NGINX path=/healthz status=200 headers=6 cache=none
NGINX path=/actuator/health status=404 headers=6 cache=none
NGINX path=/swagger-ui/index.html status=404 headers=6 cache=none
NGINX path=/v3/api-docs status=404 headers=6 cache=none
NGINX path=/api/unauthorized status=401 headers=6 cache=none
NGINX auth_burst=200,200,200,200,200,200,429,429,429
NGINX down path=/ status=503 content_type=text/html
NGINX down path=/api/v1/home status=503 content_type=application/json
NGINX down path=/healthz status=503 content_type=application/json
NGINX query_tokens_in_access_log=0
```

### 숨김 입력

```text
SECRETS hidden_inputs=7 echoed=0 modes=600 quote_roundtrip=PASS rerun_bytes_changed=0 initial_password_removed=PASS
```

### 타이머 해석

`systemd-analyze verify` 7개 유닛/타이머 exit 0. 실행 결과가 아니라 정적 검증입니다.

```text
Normalized form: *-*-* 03:30:00 Asia/Seoul
    Next elapse: Wed 2026-10-28 18:30:00 UTC
       From now: 13h left
Normalized form: *-*-* 09:10:00 Asia/Seoul
    Next elapse: Thu 2026-10-29 00:10:00 UTC
       From now: 19h left
```

## 부작용·남은 위험

- 컨테이너 PID 1이 systemd가 아니므로 실제 서비스 의존성, 재시작, sandbox, timer 발화, journal 용량 제한, swap 활성화, sysctl 적용, SSH 정책 적용은 미검증입니다.
- 실제 EC2 IMDSv2·DuckDNS DNS 전파·인증서 발급/갱신·보안 그룹은 운영자 확인이 필요합니다. 자동 갱신 실패 시 현재 IP를 콘솔에서 확인해야 합니다.
- Ubuntu 컨테이너 MariaDB는 패키지 10.11.14, 공식 이미지 검증은 10.11.19입니다. minor 버전 차이를 숨기지 않았습니다.
- Nginx 요청별 오류 로그를 버려 진단 정보가 줄어듭니다. 토큰 비노출을 우선한 선택이며 access JSON으로 상태·지연을 추적합니다.
- Redis 인증 없음·같은 서버 프로세스에서 접근 가능, 서버 한 대 장애 시 전체 중단이라는 원래 제약은 그대로입니다.

## 커밋

서버 설정·부팅 도구와 함께 커밋. 해시는 `M5-04-phase-one-results.md`에 기록합니다.
