# M5-05 — skip-name-resolve와 loopback TCP 계정

## 증상

명세대로 `workflow_app@localhost`, `workflow_migrator@localhost`를 만들고 `skip-name-resolve`를 켜면, prod의 `jdbc:mariadb://127.0.0.1:3306/mini_erp`에 해당하는 TCP 연결이 `1045 Access denied ... @127.0.0.1`로 실패했습니다. 서버 설정을 그대로 적용하면 앱이 기동하지 못하는 조합입니다.

## 재현 방법

2026-10-28, 호스트 포트 없는 MariaDB 10.11 컨테이너에 실제 `60-workflow.cnf`와 `provision-db.sh`를 넣었습니다. 비밀번호는 컨테이너 안에서 생성해 root 600 파일에만 저장했습니다. 실제 스크립트로 계정을 만든 뒤 `mariadb --protocol=tcp --host=127.0.0.1`로 접속했습니다. 비밀번호는 컨테이너 내부 프로세스 환경으로만 전달했으며 명령 인자나 로그에는 넣지 않았습니다.

첫 socket ping은 이미지의 임시 초기화 서버도 응답할 수 있어 TCP 준비 증거가 아니었습니다. 최종 서버의 `@@skip_networking=0`을 확인한 뒤 다시 실행해 1045를 재현했습니다. 로컬 복구 도구의 준비 검사도 같은 기준으로 보완했습니다.

## 원인

MariaDB의 계정 식별자는 사용자 이름과 호스트의 조합입니다. DNS 해석을 끈 상태에서 Unix socket의 localhost 계정과 TCP 127.0.0.1 계정은 일치하지 않았습니다. M5ProductionTest는 호스트 JVM→컨테이너 TCP 연결 때문에 `@%` 시험 계정을 사용하므로 권한 분리는 검증했지만 서버 스크립트의 이 호스트 조합까지는 검증하지 못했습니다.

## 검토했지만 버린 방법과 이유

- @%로 변경: 필요한 loopback보다 접근 가능한 호스트 범위가 넓어져 제외.
- skip-name-resolve 끄기: 명세의 DNS 해석 없는 설정을 포기하고 이름 해석에 의존하게 되어 제외.
- 앱을 Unix socket으로 연결: 기존 JDBC TCP 구성을 바꾸고 별도 연결 지원을 검토해야 하므로 제외.
- socket ping만으로 컨테이너 준비 판정: 임시 초기화 서버를 정상 기동으로 오인할 수 있어 제외.

## 선택한 방법

앱과 migrator 계정을 각각 `@127.0.0.1`로 명시했습니다. DML 4종/스키마 전체 권한 분리는 그대로이며 root는 Unix socket을 유지합니다. DB 비밀번호·JDBC URL·bind-address·skip-name-resolve는 변경하지 않았습니다. 두 번 실행해도 같은 계정과 권한이 유지되는 것을 확인했습니다.

이는 명세 3.2의 @localhost 표기에서 달라진 부분입니다. 실제 1045 재현을 근거로 사용자에게 진행 중 설명하고 runbook·최종 보고서에 명시했습니다. 실제 서버는 수정하지 않았습니다.

## 결과 — 수정 전

실제 provision 스크립트 기준 접속 실패 1/1:

```text
DB accounts ready: migrator=DDL+DML, app=SELECT/INSERT/UPDATE/DELETE, no GRANT OPTION.
ERROR 1045 (28000): Access denied for user 'workflow_app'@'127.0.0.1' (using password: YES)
PROVISION_TCP_BEFORE exit=1
```

## 결과 — 수정 후

기존 시험용 localhost 계정을 제거한 격리 컨테이너에서 새 스크립트를 2회 실행했습니다. 두 계정 TCP 연결, migrator CREATE, app INSERT/UPDATE/SELECT/DELETE, app DDL 거부를 확인했습니다.

```text
DB accounts ready: host=127.0.0.1 migrator=DDL+DML, app=SELECT/INSERT/UPDATE/DELETE, no GRANT OPTION.
DB accounts ready: host=127.0.0.1 migrator=DDL+DML, app=SELECT/INSERT/UPDATE/DELETE, no GRANT OPTION.
CURRENT_USER()
workflow_migrator@127.0.0.1
CURRENT_USER()
workflow_app@127.0.0.1
COUNT(*)
1
Variable_name	Value
skip_name_resolve	ON
TCP_DDL statement=DROP code=1142
TCP_DDL statement=ALTER code=1142
TCP_DDL statement=CREATE code=1142
PROVISION_TCP_AFTER migrator=OK app_DML=OK forbidden_DDL=3
```

최종 서버 준비까지 기다리는 로컬 복구 회귀:

```text
LOCAL_RESTORE tables=       2 flyway=6 counts=MATCH elapsed_s=0
```

## 부작용·남은 위험

- 지금 구성은 IPv4 loopback만을 지원합니다. 나중에 JDBC 호스트를 ::1이나 다른 서버로 바꾸면 계정 호스트와 접근 정책도 함께 검토해야 합니다.
- 기존 초안 스크립트를 이미 적용한 별도 환경에 @localhost 계정이 남아 있다면 자동 삭제하지 않습니다. 이 작업에서는 실제 배포를 하지 않았으며, 테스트에서는 자신이 만든 계정만 제거했습니다.
- 실제 EC2 전체 기동은 사용자 실행 범위입니다. 컨테이너의 TCP 계정 확인을 서버 전체 배포 성공으로 표현하지 않습니다.

## 커밋

provision의 loopback 호스트 수정·복구 준비 검사·runbook·이 문서를 함께 커밋합니다. 해시는 `M5-04-phase-one-results.md`에 기록합니다.
