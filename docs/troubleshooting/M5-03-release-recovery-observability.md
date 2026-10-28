# M5-03 — 배포·되돌리기·백업·복구·관측 도구

## 증상

배포 실패 시 이전 버전으로 돌아오는 순서와 백업 검증 절차가 없으면 기동 실패·불완전 덤프·스키마/코드 불일치를 운영자가 수동으로 판단해야 합니다. 다음 단계에 쓸 API 지연과 배포 중 끊김도 같은 방식으로 수집할 필요가 있습니다.

## 재현 조건과 방법

- 모든 shell: `find deploy -name '*.sh'`의 각 파일에 `bash -n`. 18개, 실패 0. Python heredoc도 compile 검사 통과.
- `command -v shellcheck`: 설치되어 있지 않아 **못 함**. 설치하지 않았습니다.
- `python3 deploy/checks/offline-tools.py`: localhost 가짜 HTTP API 10회, 요청 4~6번만 503; 로그 숫자 경로 및 percentile fixture; DuckDNS 전송 모킹. 실제 외부 호출 없음.
- Ubuntu 24.04 컨테이너의 MariaDB 10.11에 시험용 테이블 2개를 생성. `flyway_schema_history`는 버전 6 한 행인 **복구 도구용 fixture**, 실제 마이그레이션 실행 증거는 M5-01의 Testcontainers입니다. `recovery_fixture`는 2행.
- 컨테이너 안에서 provision-db 2회 → backup → restore-rehearsal. 백업을 docker cp로 꺼내 `bash deploy/bin/restore-local.sh <시험덤프>`로 별도 `mariadb:10.11 --network none` 컨테이너에 복구. SSH가 금지되어 pull-backup의 실제 SSH 전송은 실행하지 않았습니다.

## 원인

링크와 스키마의 수명은 다릅니다. 새 jar가 실패해도 마이그레이션은 이미 커밋됐을 수 있습니다. 또 `--single-transaction`은 동시 DDL을 보호하지 않고, 서버 디스크에만 둔 백업은 서버 소실을 복구하지 못합니다.

## 검토했지만 버린 방법과 이유

| 선택 | 버린 방법과 이유 |
|---|---|
| 로컬 빌드, 서버에서는 기동만 | 2 GiB 서버 Maven/npm 빌드: 운영 프로세스와 메모리를 다툼 |
| backend health 성공 뒤 frontend 링크 변경 | 둘을 먼저 교체: 새 화면이 실패한 API를 바라볼 수 있음 |
| 직전 릴리스 복귀, 최근 3개 보관 | 실패 때 수동 파일 복사: 복구 경로가 반복 가능하지 않음 |
| 사전 백업 + 공통 flock | 시간만 피해 실행: 수동 백업/배포가 겹칠 수 있음 |
| 원자적 덤프 공개 + checksum | 파이프 실패를 무시한 최종 파일 생성: 반쪽 덤프를 정상으로 오인 |
| 새 임시 DB 비교 후 삭제 | 운영 DB에 복구 리허설: 기존 데이터를 훼손할 수 있음 |
| 로컬 백업 스트리밍, 0600 | 서버 백업을 world-readable로 바꿔 scp: 개인정보 접근 범위를 넓힘 |
| 0.2초마다 별도 요청 시작 | 앞 요청을 기다린 뒤 sleep: 장애 지연 때문에 표본 간격이 길어짐 |
| 0600 헤더 파일 | Bearer를 명령 옵션에 직접 입력: 프로세스/셸 기록에 남김 |
| 요청 쿼리 없는 경로 집계 | 전체 요청 URL 출력: 초대/재설정 토큰을 노출할 수 있음 |

## 선택한 방법

- 로컬 deploy는 깨끗한 작업 트리(보호 대상 .claude 제외)와 M5 프론트 maintenance 산출물을 요구합니다. timestamp+SHA 릴리스, 신규 migration 사전 백업, root 릴리스 배치, 90초 health 확인, 실패 시 이전 backend 복귀, 수동 rollback 시 backend/frontend 같은 릴리스로 전환합니다.
- 스키마는 자동 되돌리지 않습니다. 운영자에게 구버전 호환 migration 또는 백업 복구 계획을 요구합니다. 실제 서버 시험 전에는 자동 rollback 성공을 주장하지 않습니다.
- 로그 출력은 알려진 비밀값을 가리고, 가릴 환경 파일이 없으면 로그를 생략합니다. 로그 실패가 rollback을 중단하지 않게 처리합니다.
- 백업·서버 리허설·배포·rollback은 같은 잠금을 사용합니다. 03:30~03:39 KST 신규 배포를 거절합니다. 백업은 임시 파일·gzip 검사·이름 변경·sha256·7일 정리 순서입니다.
- probe는 CSV의 UTC 요청 시각, 상태, 응답 ms, curl 종료 코드를 저장합니다. 첫 실패 시작→다음 성공 시작 간격을 연속 실패 구간으로 보고, 끝까지 실패하면 마지막 응답 종료까지 셉니다. 실운영 끊김 시간과 별도로 샘플링 오차가 있습니다.

## 결과 — 출력 원문

### 계정 재설정 2회

```text
DB accounts ready: migrator=DDL+DML, app=SELECT/INSERT/UPDATE/DELETE, no GRANT OPTION.
DB accounts ready: migrator=DDL+DML, app=SELECT/INSERT/UPDATE/DELETE, no GRANT OPTION.
```

### 백업과 서버 복구

```text
BACKUP file=20261028T044346.889725591Z.sql.gz bytes=940 elapsed_s=1
20261028T044346.889725591Z.sql.gz: OK
RESTORE tables=2 flyway=6 counts=MATCH elapsed_s=0
flyway_schema_history	1
recovery_fixture	2
remaining_restore_database
0
GRANTEE	PRIVILEGE_TYPE	IS_GRANTABLE
'workflow_app'@'localhost'	DELETE	NO
'workflow_app'@'localhost'	INSERT	NO
'workflow_app'@'localhost'	SELECT	NO
'workflow_app'@'localhost'	UPDATE	NO
```

### 오프라인 로컬 복구

```text
LOCAL_RESTORE tables=       2 flyway=6 counts=MATCH elapsed_s=2
```

### 재실행 가능한 오프라인 도구 검사

```text
DUCKDNS_MOCK outcome=OK calls=3 exit=0 token_or_url_leaks=0 external_calls=0
DUCKDNS_MOCK outcome=KO calls=3 exit=1 token_or_url_leaks=0 external_calls=0
DUCKDNS_MOCK outcome=exception calls=3 exit=1 token_or_url_leaks=0 external_calls=0
PROBE requests=10 failures=3 longest_failure_s=0.600 interval_s=0.200
PROBE_CHECK csv_rows=10 expected_503=3 actual_503=3
method	path	count	p50_ms	p95_ms	max_ms
GET	/api/v1/tasks/{id}	3	20.000	30.000	30.000
invalid_lines=1
```

시간은 2개 테이블·3행 fixture와 로컬 Docker 환경의 참고치입니다. 실제 전체 데이터 백업/복구 시간이나 EC2 성능으로 쓰지 않습니다. probe의 3/10 실패는 테스트가 강제로 만든 구간이며 서비스 장애율이 아닙니다.

## 부작용·남은 위험

- 실제 SSH 전송·Java 빌드/React 빌드 결합·systemd 재시작·90초 실패 및 rollback·릴리스 정리는 실제 서버 미실행입니다. 셸 문법 통과가 이 동작들의 성공을 보장하지 않습니다.
- 새 migration이 이전 jar와 호환되지 않으면 자동 링크 rollback만으로 복구되지 않습니다. 추가 DDL 경로가 flock을 우회하면 백업과 충돌할 수 있습니다.
- 백업은 일 단위이며 서버가 사라지기 전에 로컬로 내려받아야 합니다. 복구 counts 비교는 쓰기 없는 시간에 실행해야 합니다.
- 선택 웹후크의 실제 전송과 다음 날 배치 미실행 알림은 미검증입니다. 웹후크를 설정하지 않으면 아무것도 보내지 않습니다.
- probe는 2xx 외와 전송 실패를 모두 세므로 인증 만료 401도 포함합니다. 헤더 파일은 저장소 밖에 두고 측정 뒤 삭제합니다.
- 운영 서버 9장 측정은 사용자가 실행합니다. 이 문서는 컨테이너/가짜 API 결과만 기록합니다.

## 커밋

로컬 배포·복구·probe·검증 도구·runbook과 함께 커밋. 해시는 `M5-04-phase-one-results.md`에 기록합니다.

## 추가 재현 — 첫 배포 전 빈 DB 백업

첫 V1 적용 전 사전 백업에는 flyway_schema_history가 없습니다. 이 덤프를 복구하면 기존 복구 도구가 그 테이블을 무조건 조회해 1146으로 실패했습니다. 별도 오프라인 MariaDB에서 수정 전 1/1 실패를 먼저 확인했습니다.

빈 덤프를 복구 불가로 배제하는 대안은 첫 배포의 정상 사전 백업을 다룰 수 없어 버렸습니다. 이력 없음은 버전 0으로 통일하고, 로컬 복구는 복구된 테이블 목록 전체를 읽어 빈 목록 및 예상 밖 추가 테이블도 비교하도록 바꿨습니다. 데이터가 있는 버전 6 백업도 다시 확인했습니다.

수정 전 원문:

```text
BACKUP file=20261028T050238.678198375Z.sql.gz bytes=520 elapsed_s=1
20261028T050238.678198375Z.sql.gz: OK
--------------
SELECT version FROM flyway_schema_history WHERE success=1 AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1
--------------

ERROR 1146 (42S02) at line 1: Table 'mini_erp_restore.flyway_schema_history' doesn't exist
EMPTY_RESTORE_BEFORE exit=1
```

수정 후 원문 (빈 서버·빈 로컬·버전 6 로컬·버전 6 서버 모두 성공):

```text
20261028T050238.678198375Z.sql.gz: OK
RESTORE tables=0 flyway=0 counts=MATCH elapsed_s=1
LOCAL_RESTORE tables=       0 flyway=0 counts=MATCH elapsed_s=3
LOCAL_RESTORE tables=       2 flyway=6 counts=MATCH elapsed_s=3
BACKUP file=20261028T050447.871703921Z.sql.gz bytes=894 elapsed_s=0
20261028T050447.871703921Z.sql.gz: OK
RESTORE tables=2 flyway=6 counts=MATCH elapsed_s=0
flyway_schema_history	1
recovery_fixture	2
```

한계는 동일합니다. 버전 0은 아직 성공한 migration 이력이 없다는 뜻이며, 실패한 DDL의 원상복구를 의미하지 않습니다. 이 보완은 별도 수정 커밋으로 남깁니다.

## 추가 재현 — 로컬 복구의 익명 데이터 볼륨

MariaDB 이미지에는 `/var/lib/mysql` VOLUME 선언이 있습니다. `docker rm -f`만으로는 복구한 데이터의 익명 볼륨이 남았습니다. 복구 도구가 만든 컨테이너의 mount 이름만 기록해 제거 뒤 잔존을 확인했습니다.

모든 dangling volume을 지우는 대안은 다른 작업의 데이터를 삭제할 수 있어 버렸습니다. 자신이 생성한 컨테이너를 `docker rm --force --volumes`로 제거하도록 한정했습니다. 수정 전 1개 잔존, 수정 후 0개입니다. 재현에서 발견한 그 실행의 볼륨만 지정해 정리했습니다.

```text
LOCAL_RESTORE tables=       2 flyway=6 counts=MATCH elapsed_s=1
RESTORE_VOLUME_BEFORE retained=1
LOCAL_RESTORE tables=       2 flyway=6 counts=MATCH elapsed_s=2
RESTORE_VOLUME_AFTER retained=0
```

Docker daemon 중단이나 SIGKILL로 cleanup 자체가 실행되지 못하는 상황은 별도 운영 확인이 필요합니다. 임의의 다른 볼륨을 지우는 명령은 제공하지 않습니다.
