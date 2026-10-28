# M5 리뷰 수정 결과

## 범위·작업 위치
- feat/m5-deploy, 0271fef 이후 리뷰 수정.
- 별도 작업 트리: /Users/kimmini/sk-final-deepfake/mini-erp-system-m5-deploy.
- 공유 원본 브랜치는 변경하지 않았으며 마지막 읽기에서도 feat/m4-home-board.
- 3·4번의 900/90초·최근 7세트는 이번 사용자 지시를 기준으로 구현했다. @127.0.0.1 계정 판단은 유지.
- frontend/·backend/md/·V1~V6·.claude/·개발 DB/Redis 변경 없음. 병합·푸시·실제 서버·SSH·AWS·실제 DuckDNS 호출 없음. 새 라이브러리 없음.

## 커밋
| 요청 | 커밋 | 변경·기록 |
|---|---|---|
| 1 | 9ed4bf1 | 시스템 디렉터리 권한 보존, [M5-11](M5-11-system-directory-permissions.md) |
| 2 | cae3d11 | clean package, [M5-12](M5-12-clean-release-build.md) |
| 5 | a25634a | 한국 월 경계·UTC 변이 검증, [M5-13](M5-13-month-boundary-test.md) |
| 3 | ee05666 | migration 대기·조기 실패, [M5-14](M5-14-migration-health-window.md) |
| 4 | 5e6c168 | 최근 백업 7세트, [M5-15](M5-15-backup-generations.md) |
| 6 | 3157d2f | bootstrap 직렬화·SSH/DNS 순서, [M5-16](M5-16-bootstrap-order.md) |
| 7 | 1a897cb | 힙 파일 하나로 제한, [M5-17](M5-17-single-heap-dump.md) |
| 8 | 1c816b8 | 복구·메일·권한 경계·부하 실험 문서, [M5-18](M5-18-runbook-review.md) |
| 3 보강 | cd2879b | 대기 중 한도 소진 시 curl 시작 차단 |

이 최종 보고서는 별도 문서 커밋이다. git log -- docs/troubleshooting/M5-19-review-results.md로 식별한다.

## 항목별 전후 결과

### 1. 폴더 권한
Ubuntu 24.04 + MariaDB 10.11 컨테이너, 실제 backup.sh 실행. /run/lock은 이미지의 기본값. service 시작을 막은 Ubuntu 패키지 환경에는 /var/log/mysql이 없어서 기존 디렉터리 상황을 mysql:adm 2775 fixture로 만들었다. 이 값을 패키지 기본값이라고 주장하지 않는다.

| 경로 | 수정 전 | 수정 후 backup·bootstrap 재실행 |
|---|---|---|
| /run/lock | 1777 root:root → 755 root:root | 1777 root:root 유지 |
| /var/log/mysql | 2775 mysql:adm → 2750 mysql:adm | 2775 mysql:adm 유지 |

최종 실행 원문(실제 시험 DB의 백업은 당시 5세트였다. 아래 9세트 정리 fixture와 별개):

```text
1777 root:root /run/lock
2775 mysql:adm /var/log/mysql
BACKUP_RETAIN sets=5 removed_sets=0
BACKUP file=20261028T073358.716945465Z.sql.gz bytes=768 elapsed_s=0
1777 root:root /run/lock
2775 mysql:adm /var/log/mysql
1777 root:root /run/lock
2775 mysql:adm /var/log/mysql
```

공용 systemd/journald/SSH/certbot 설정 디렉터리도 install -d 대신 mkdir -p로 변경했다. 앱 전용 경로와 ACME webroot만 소유권·권한을 지정한다. 전체 감사 목록은 M5-11에 있다.

### 2. 오래된 빌드 리소스
target/classes 안에만 V999__m5_stale_probe.sql을 만들었다.

```text
PACKAGE_BEFORE stale_entries=1
BOOT-INF/classes/db/migration/V999__m5_stale_probe.sql
PACKAGE_AFTER stale_entries=0 source_migrations=6 target_probe_removed=YES
BOOT-INF/classes/db/migration/V1__init.sql
BOOT-INF/classes/db/migration/V2__organization_permission.sql
BOOT-INF/classes/db/migration/V3__approval_and_leave_ledger.sql
BOOT-INF/classes/db/migration/V4__attendance_and_batch_runs.sql
BOOT-INF/classes/db/migration/V5__user_retirement_date.sql
BOOT-INF/classes/db/migration/V6__project_board.sql
```

clean package 뒤 시험 SQL은 jar·target 모두에서 사라졌다. BUILD backend_s 로그는 유지했다. 전체 배포 스크립트/프론트 빌드/SSH는 실행하지 않았다.

### 3. health 대기
운영은 migration 900초·그 외/rollback 90초. 시험 복사본에서만 6/2초로 줄였으며 실제 서비스/네트워크 호출 0. release.sh의 실제 버전 선택 구문을 사용하고 자동/수동 rollback 세 경로가 모두 90초인지 검사한다.

| 시나리오 | 수정 전 구조 | 수정 후 종료 코드·실제 경과 |
|---|---|---|
| 늦게 UP | 고정 2초에서 실패 | 0 · 3.843초, migration=YES UP |
| 일반 배포 초과 | 실패 | 1 · 2.646초, migration=NO TIMEOUT |
| NRestarts 증가 | systemd 검사 없음, 고정 시간까지 대기 | 1 · 1.586초, APP_FAILED |
| ActiveState=failed | systemd 검사 없음, 고정 시간까지 대기 | 1 · 1.533초, APP_FAILED |
| 상태 조회가 한도를 소진 | 미검증 경계 | 1 · 3.321초, TIMEOUT, curl 0회 |

마지막 시나리오의 상태 조회 자체는 3초 지연시켰다. Bash의 elapsed_s는 정수 초이며 위 시간과 약 1초 차이가 날 수 있다. 시험의 고의 지연을 실제 서버 성능으로 사용하지 않는다. 큰 DDL의 사전 복구·시간 측정과 900초 초과 예상 시 별도 점검 절차를 README 7절에 추가했다. restore-local이 컨테이너를 지우므로 DDL 시간은 같은 덤프의 별도 일회성 로컬 복구 컨테이너에서 측정하도록 설명했다.

### 4. 백업 7세트
9세트(앞 8세트 mtime 20일 전, 마지막 세트 현재)에서 기존 일수 정리는 1세트만 남겼다. 새 정리는 최근 UTC 파일 이름 7개와 짝 파일 21개를 보존한다.

```text
BACKUP_RETAIN sets=7 removed_sets=2
RETENTION_AFTER sets=7 files=28 removed_sets=2 sidecars=MATCH
20261003T000000.000000000Z.sql.gz
20261004T000000.000000000Z.sql.gz
20261005T000000.000000000Z.sql.gz
20261006T000000.000000000Z.sql.gz
20261007T000000.000000000Z.sql.gz
20261008T000000.000000000Z.sql.gz
20261009T000000.000000000Z.sql.gz
BACKUP_RETAIN sets=7 removed_sets=0
```

배포 전 백업도 같은 세대 수에 포함한다. 기존 문서의 보관 문구도 갱신했다.

### 5. 한국 월 경계
한국 1일 00:30은 UTC 전달 말일이다. 전달 결근 2건, 이번 달 1건으로 구분했다.
- UTC 월로 임시 변경: 신규 테스트 1개 실패, attendance/home 두 assertion 모두 expected 1 / actual 2.
- 원복: M5HomeCountersTest 4/4 성공.
- 운영 Java는 원복 후 0271fef와 diff 0. 커밋은 테스트만 추가했다.

### 6. bootstrap
실제 서비스 처리 구문과 가짜 명령으로 점검했다.

```text
BOOTSTRAP_FIXTURE scenario=DNS_OK exit=0 waited_for_backup=YES restart_locked=YES ssh_before_dns=YES dns_unlocked=YES
BOOTSTRAP_FIXTURE scenario=DNS_FAIL exit=1 waited_for_backup=YES restart_locked=YES ssh_before_dns=YES dns_unlocked=YES
```

다른 프로세스가 backup 잠금을 잡은 동안 0.3초간 서비스 동작 0. restart 안에서는 잠금 점유, DNS 단계에서는 해제를 확인했다. SSH 검증/reload가 DNS보다 먼저다. DNS 실패는 exit 1과 journalctl/reset-failed 안내. 패키지에서 유발하는 재시작도 겹치지 않도록 apt 앞부터 잠근다.

### 7. 힙 덤프
사용자 데이터 없는 시험 JVM에서만 OOM을 실행했다.

```text
HEAP_BEFORE oom_runs=2 files=2
HEAP_CHECK first_exit=3 second_exit=3 files=1 bytes=3221793 unchanged=YES
java.lang.OutOfMemoryError: Java heap space
Dumping heap to /tmp/heap-check/heap.hprof ...
Heap dump file created [3221793 bytes in 0.059 secs]
Terminating due to java.lang.OutOfMemoryError: Java heap space
java.lang.OutOfMemoryError: Java heap space
Dumping heap to /tmp/heap-check/heap.hprof ...
Unable to create /tmp/heap-check/heap.hprof: File exists
Terminating due to java.lang.OutOfMemoryError: Java heap space
```

새 설정은 /var/lib/workflow/heap.hprof. 기존 env 자동 변경은 하지 않으므로 기존 설정 사용자의 경로 변경·재시작 안내도 넣었다. 시험 덤프는 컨테이너 안에서 즉시 삭제했으며 로컬로 가져오지 않았다.

### 8. README
서버 restore-rehearsal로 counts 생성 후 같은 세트 다운로드/로컬 복구, batch-check Persistent 알림, migrator 비밀값을 가진 앱 프로세스의 한계, 메일 활성화 시 APP_INVITATION_SETUP_URL, 로그인 10건/분·burst 5와 사전 토큰 발급 조건을 반영했다.

## 최종 검증
Java 17 + UTC 전체: **231/231 성공, 실패·오류·건너뜀 0**, 9분 27초. Surefire XML 60개 합산과 Maven 요약이 일치한다. 이전 230개에서 한국 월 경계 테스트 1개를 추가했다. M5HomeCountersTest는 4/4 통과했다.

```bash
cd /Users/kimmini/sk-final-deepfake/mini-erp-system-m5-deploy/backend
JAVA_HOME=/Users/kimmini/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home ./mvnw -Duser.timezone=UTC test
```

```text
[INFO] Tests run: 231, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  09:27 min
[INFO] Finished at: 2026-10-28T07:42:35Z
```

- bash -n: deploy .sh 19개 통과. 내장 Python 11블록과 검증 Python 문법 통과.
- offline-tools: DuckDNS 6, health 5, 도메인 검증 4+2, probe 10요청(의도한 실패 3)·로그 집계 통과.
- systemd-analyze verify: 컨테이너에서 duckdns-update/workflow/workflow-backup 유닛 통과. 실제 systemd 서비스 실행은 아님.
- shellcheck 미설치, 설치하지 않음.
- git 보호 경로 diff 0, 임시 월 변경을 제외한 운영 Java diff 0. AWS 키·개인 키·실제 웹후크 URL 패턴 검사 0건(모든 비밀값의 완전 탐지를 뜻하지 않음).

## 확인하지 못한 것·남은 위험
실제 EC2/SSH/AWS/DuckDNS·systemd 재시작·Flyway 대형 DDL 900초·실제 배포/rollback은 금지된 서버 작업이므로 실행하지 않았다. 실제 성능·부팅 동작은 운영자 검증이 필요하다. shellcheck는 미설치다. 기본 JVM 전체를 별도 실행하지 않았으며 요청한 UTC 전체를 실행했다.

이전 잘못된 OS 권한을 자동 복구하지 않는다. 현장 기본 권한은 운영자가 확인해야 한다. 900초도 긴 DDL의 안전한 중단을 보장하지 않으므로 사전 측정이 필요하다. 스키마는 jar rollback으로 돌아가지 않는다. 최근 7세트는 공간 총량의 상한이 아니며 잦은 배포가 오래된 세대를 밀어낼 수 있다. 기존 env의 힙 경로는 운영자가 반영해야 한다.

검증용 workflow-m5-review-ubuntu, workflow-m5-review-db와 전용 socket/데이터 볼륨, 시험 클래스·힙 덤프는 정리했다. 전체 테스트의 Testcontainers와 사용자 개발 컨테이너는 건드리지 않았다.

## 변경 파일

```text
backend/src/test/java/com/minierp/backend/integration/M5HomeCountersTest.java
deploy/README.md
deploy/bin/deploy.sh
deploy/checks/bootstrap-order.py
deploy/checks/health-wait.py
deploy/checks/offline-tools.py
deploy/server/bootstrap.sh
deploy/server/common.sh
deploy/server/release.sh
deploy/server/setup-secrets.sh
docs/troubleshooting/M5-03-release-recovery-observability.md
docs/troubleshooting/M5-11-system-directory-permissions.md
docs/troubleshooting/M5-12-clean-release-build.md
docs/troubleshooting/M5-13-month-boundary-test.md
docs/troubleshooting/M5-14-migration-health-window.md
docs/troubleshooting/M5-15-backup-generations.md
docs/troubleshooting/M5-16-bootstrap-order.md
docs/troubleshooting/M5-17-single-heap-dump.md
docs/troubleshooting/M5-18-runbook-review.md
docs/troubleshooting/M5-19-review-results.md
```
