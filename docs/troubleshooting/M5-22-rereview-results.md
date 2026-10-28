# M5-22 — 재리뷰 수정 최종 결과

## 범위와 커밋
작업 경로: /Users/kimmini/sk-final-deepfake/mini-erp-system-m5-deploy, 브랜치 feat/m5-deploy. 기준 1f27cb0. 공유 원본의 feat/m4-home-board는 유지했다. 병합·푸시는 하지 않았다.

- 61fb6d2: P2 SSH 독립 실행. M5-20-detached-release.md.
- 이 문서를 추가한 커밋: P3 UP 경계 판정. M5-21-health-deadline-up.md. 최종 해시는 사용자 완료 보고와 git log에서 확인한다.

## 변경 파일
P2: deploy/bin/common.sh, deploy/bin/deploy.sh, deploy/bin/rollback.sh, deploy/server/common.sh, deploy/server/release-job.sh(신규), deploy/checks/release-disconnect.py(신규), deploy/checks/offline-tools.py, deploy/README.md, docs/troubleshooting/M5-20-detached-release.md.

P3: deploy/server/common.sh, deploy/checks/health-wait.py, deploy/checks/offline-tools.py, docs/troubleshooting/M5-21-health-deadline-up.md, 본 문서.

frontend/·backend/ 전체·.claude/ 변경 0. backend/md와 Flyway V1~V6도 그대로다. 개발 MariaDB·Redis와 기존 컨테이너에는 접근하지 않았다.

## 검증 결과
- deploy 셸 20개 bash -n 통과, checks Python 5개 구문 검사 통과, git diff --check 통과.
- offline-tools.py --release-jobs 전체 성공(종료 코드 0). 실 systemd PID 1 컨테이너에서 아래 출력을 얻었다.
- DuckDNS 가짜 HTTP 6개, 기존 health 5개, deadline 4개, 도메인 허용 4개·거부 2개, keepalive 옵션 2개, 로컬 follow 종료 전달 3개(0/1/255), probe 10요청(예상 503 3건), 로그 집계, 독립 서비스 3경로 모두 통과.
- 별도 기준 커밋 실험: 연결 단절 2경로 모두 SIGPIPE -13, 경계 UP 3회 모두 TIMEOUT. raw 출력은 M5-20·21에 보존했다.
- 실제 systemd 255에서 서비스 부모 PID=1, 호출자 SIGHUP 뒤 서비스 완료, 로그 reader 종료 뒤 성공/복구/수동 rollback을 확인했다. 배포 중 동시 접수 75, maintenance 잠금 유지, 재접속 종료 코드 일치, root 700/600, 비밀값 자리표시자 치환까지 단언했다.
- 백엔드 코드는 변경하지 않아 요청대로 Maven 전체 테스트를 다시 돌리지 않았다. 이전 UTC 231/231은 M5-19의 이전 실행 결과이며 이번 실행 결과로 재사용하지 않는다.
- shellcheck 미설치. 새로 설치하지 않았다.

## 전체 실행 출력
```text
DUCKDNS_LOCAL scenario=OK exit=0 update_calls=1 ps_samples=8 token_leaks=0 external_calls=0 elapsed_s=0.183
DUCKDNS_LOCAL scenario=KO exit=1 update_calls=8 ps_samples=200 token_leaks=0 external_calls=0 elapsed_s=3.155
DUCKDNS_LOCAL scenario=NO_RESPONSE exit=1 update_calls=4 ps_samples=201 token_leaks=0 external_calls=0 elapsed_s=3.183
DUCKDNS_LOCAL scenario=DELAYED_DNS exit=0 update_calls=1 ps_samples=36 token_leaks=0 external_calls=0 elapsed_s=0.507
DUCKDNS_LOCAL scenario=DNS_MISMATCH exit=1 update_calls=1 ps_samples=207 token_leaks=0 external_calls=0 elapsed_s=3.116
DUCKDNS_LOCAL scenario=DELAYED_IMDS exit=0 update_calls=1 ps_samples=52 token_leaks=0 external_calls=0 elapsed_s=0.802
HEALTH_FIXTURE scenario=LATE_UP exit=0 elapsed_s=3.147 external_calls=0
HEALTH_WAIT limit_s=6 migration=YES result=UP elapsed_s=3
HEALTH_FIXTURE scenario=TIMEOUT exit=1 elapsed_s=2.041 external_calls=0
HEALTH_WAIT limit_s=2 migration=NO result=TIMEOUT elapsed_s=2
HEALTH_FIXTURE scenario=RESTART exit=1 elapsed_s=1.028 external_calls=0
HEALTH_WAIT limit_s=6 migration=YES result=APP_FAILED elapsed_s=1
HEALTH_FIXTURE scenario=FAILED exit=1 elapsed_s=1.019 external_calls=0
HEALTH_WAIT limit_s=6 migration=YES result=APP_FAILED elapsed_s=1
HEALTH_FIXTURE scenario=STATUS_DELAY exit=1 elapsed_s=3.018 external_calls=0
HEALTH_WAIT limit_s=2 migration=NO result=TIMEOUT elapsed_s=3
DEADLINE_AFTER scenario=UP exit=0 elapsed_s=2.247 HEALTH_WAIT limit_s=2 migration=NO result=UP elapsed_s=2
DEADLINE_AFTER scenario=UP exit=0 elapsed_s=2.247 HEALTH_WAIT limit_s=2 migration=NO result=UP elapsed_s=2
DEADLINE_AFTER scenario=UP exit=0 elapsed_s=2.279 HEALTH_WAIT limit_s=2 migration=NO result=UP elapsed_s=3
DEADLINE_AFTER scenario=UP_THEN_RESTART exit=1 elapsed_s=2.255 HEALTH_WAIT limit_s=2 migration=NO result=APP_FAILED elapsed_s=2
DOMAIN_CHECK remote=4/4 probe_rejections=2/2 keepalive=2/2 follow_exit=3/3 network_calls=0
PROBE requests=10 failures=3 longest_failure_s=0.605 interval_s=0.200
PROBE_CHECK csv_rows=10 expected_503=3 actual_503=3
method	path	count	p50_ms	p95_ms	max_ms
GET	/api/v1/tasks/{id}	3	20.000	30.000	30.000
invalid_lines=1
DISCONNECT_AFTER scenario=SUCCESS follower_exit=-1 worker_exit=0 reconnect_exit=0 duplicate_exit=75 maintenance_busy=1 queued_duplicate_exit=75 parent=1 elapsed_s=0.390 links=('20261028T120000Z-bbbbbbb', '20261028T120000Z-bbbbbbb', '20261027T120000Z-aaaaaaa')
RELEASE_JOB job=activate-20261028T092828Z-RzxR54qJ state=WAITING_FOR_MAINTENANCE
RELEASE_JOB job=activate-20261028T092828Z-RzxR54qJ state=RUNNING
HEALTH_WAIT limit_s=5 migration=NO result=UP elapsed_s=1
ACTIVATE release=20261028T120000Z-bbbbbbb health_elapsed_s=1
Started BackendApplication in 1.0 seconds
RETAIN releases=2
RELEASE_RESULT job=activate-20261028T092828Z-RzxR54qJ exit=0
DISCONNECT_AFTER scenario=MANUAL_ROLLBACK follower_exit=-1 worker_exit=0 links=('20261027T120000Z-aaaaaaa', '20261027T120000Z-aaaaaaa', '20261028T120000Z-bbbbbbb')
RELEASE_JOB job=rollback-20261028T092829Z-6OzjD1hC state=WAITING_FOR_MAINTENANCE
RELEASE_JOB job=rollback-20261028T092829Z-6OzjD1hC state=RUNNING
HEALTH_WAIT limit_s=5 migration=NO result=UP elapsed_s=0
ROLLBACK_OK release=20261027T120000Z-aaaaaaa
RELEASE_RESULT job=rollback-20261028T092829Z-6OzjD1hC exit=0
DISCONNECT_AFTER scenario=FAILURE follower_exit=-1 worker_exit=1 reconnect_exit=1 duplicate_exit=75 maintenance_busy=1 queued_duplicate_exit=75 parent=1 elapsed_s=0.331 links=('20261027T120000Z-aaaaaaa', '20261027T120000Z-aaaaaaa', 'MISSING')
RELEASE_JOB job=activate-20261028T092829Z-ahgBA8cd state=WAITING_FOR_MAINTENANCE
RELEASE_JOB job=activate-20261028T092829Z-ahgBA8cd state=RUNNING
HEALTH_WAIT limit_s=5 migration=NO result=APP_FAILED elapsed_s=0
ACTIVATE_FAILED release=20261028T120000Z-bbbbbbb rollback=20261027T120000Z-aaaaaaa
Started BackendApplication in 1.0 seconds
[REDACTED]
HEALTH_WAIT limit_s=5 migration=NO result=UP elapsed_s=0
ROLLBACK_OK release=20261027T120000Z-aaaaaaa
RELEASE_RESULT job=activate-20261028T092829Z-ahgBA8cd exit=1
```

## 재실행 방법
아래는 실제 서버가 아닌 폐기 가능한 Docker 컨테이너 전용이다. 이름 충돌을 확인하고 사용한다. 특권은 Docker Desktop의 이 시험 컨테이너에만 주고 호스트 파일·소켓은 마운트하지 않는다. Dockerfile은 ubuntu:24.04에 systemd, systemd-sysv, dbus, util-linux, curl, jq, python3, procps, ca-certificates를 설치하고 /sbin/init으로 시작하도록 준비한다. 의존성 설치 뒤 실행 컨테이너는 --network none으로 외부 연결을 막는다.

```bash
docker run -d --name workflow-m5-rereview --privileged --cgroupns=private   --tmpfs /run --tmpfs /run/lock --tmpfs /tmp --network none workflow-m5-rereview-systemd
docker exec workflow-m5-rereview mount -o remount,exec /tmp
docker exec workflow-m5-rereview touch /run/m5-release-test-container
docker cp deploy/. workflow-m5-rereview:/work/
docker exec workflow-m5-rereview python3 /work/checks/offline-tools.py --release-jobs
```

수정 전 실험은 git show 1f27cb0:deploy/server/common.sh와 release.sh를 컨테이너 /before에 복사한 뒤 release-disconnect.py --before-source /before를 실행한다. health 경계도 runpy로 health-wait.py를 읽고 deadline_checks('/before/common.sh')를 호출한다. fixture는 root 및 /run/m5-release-test-container·실제 systemd PID 1을 요구하며 임의 서버에서 실행하지 않는다.

## 선택·버린 방법
분리 실행과 health 판정의 대안·이유는 M5-20·21에 각각 기록했다. 명세를 조용히 변경하지 않았다. systemd transient 서비스, root 전용 결과 파일, 중복 접수 75, 불확실 결과 125는 이번 지시를 구현하기 위한 운영 인터페이스다. 기존 작업 성공/실패 종료 코드는 유지한다.

## 확인하지 못한 것
실제 EC2/SSH/AWS/DuckDNS, Mac 잠자기·Wi-Fi 전환, 운영 앱/DB, 900초 대형 DDL은 실행하지 않았다. 이번 컨테이너는 systemd 자체를 실제로 실행했지만 앱 systemctl/curl/DB 버전은 fixture다. 호스트 재부팅·SIGKILL·디스크 고갈 시 트랜잭션 같은 배포 완결성은 보장하지 않는다. 결과 없는 작업은 OUTCOME_UNKNOWN으로 보고 확인 후 재시도해야 한다. MariaDB DDL rollback의 기존 한계도 남는다.

## 정리
전용 workflow-m5-rereview 컨테이너와 workflow-m5-rereview-systemd 이미지를 검증 뒤 제거한다. 사용자 개발 컨테이너·볼륨은 정리 대상에 포함하지 않는다. 측정 로그는 로컬 임시 폴더 및 이 문서에 보존한다.
