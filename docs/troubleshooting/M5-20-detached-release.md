# M5-20 — SSH 연결과 배포 실행 수명 분리

## 증상
900초까지 health를 기다리는 release.sh의 stdout이 SSH에 연결돼 있었다. Mac의 연결이 끊기면 다음 printf가 SIGPIPE로 종료돼 백엔드만 새 버전이 되거나 자동 rollback이 실행되지 않았다.

## 재현 조건·방법
기준 1f27cb0의 common.sh/release.sh를 먼저 복사해 시험했다. Ubuntu 24.04, systemd 255.4-1ubuntu8.18, Docker Desktop arm64의 일회성 컨테이너다. 호스트 디렉터리 마운트·공개 포트 없이 --network none, --privileged, --cgroupns=private와 /run·/run/lock·/tmp tmpfs로 PID 1 systemd를 실행했다. /tmp는 fixture 실행을 위해 exec로 remount했다. 프로덕션에 적용할 옵션은 아니다.

release-disconnect.py가 별도 임시 경로에 스크립트를 복사한다. DB 버전은 6으로 고정하고 앱 systemctl/curl/journalctl만 가짜로 대체한다. 배포 관리자의 systemd-run/systemctl은 실제 PID 1에 연결한다. health 중간 gate에서 stdout reader를 닫은 뒤 진행시켰다. 기존 성공·실패 경로 모두 -13(SIGPIPE; 셸 표기 141), restart 1회로 끝나 다음 단계가 없음을 확인했다. 표의 OLD/NEW는 서로 다른 fixture 릴리스다.

| 경로 | 수정 전 종료·링크(app/web)·previous | 수정 후 종료·링크(app/web)·previous |
|---|---|---|
| 활성화 성공 | -13, NEW/OLD, 없음 | worker 0, NEW/NEW, OLD |
| 활성화 실패 | -13, NEW/OLD, 없음(복구 안 됨) | worker 1, OLD/OLD, 없음(기존 상태 보존), ROLLBACK_OK |
| 수동 rollback | 동일한 직접 SSH 실행 구조 | worker 0, OLD/OLD, NEW, ROLLBACK_OK |

수정 전후 출력은 아래에 그대로 보존한다. 대기 시점을 gate로 강제한 기능 실험으로 운영 발생 확률·성능 측정이 아니다.

## 원인
SSH 프로세스가 worker의 표준 입출력과 수명을 소유했다. 기존 maintenance flock은 상호 배제만 보장하고 두 번째 배포를 거절하지 않으며, 원격 명령 종료 뒤 작업을 계속 실행시키지도 않는다.

## 검토했지만 버린 방법과 이유
- keepalive만 추가: 네트워크 단절 감지에는 도움이 되지만 잠자기·연결 종료 후 SIGPIPE를 해결하지 않는다.
- nohup/단순 백그라운드: 출력과 세션·프로세스 관리, 종료 결과 회수까지 자체 구현해야 한다. 이미 운영 전제가 systemd여서 기존 관리자를 사용한다.
- systemd-run --scope/--pipe/--pty: 호출자 또는 호출자의 출력 FD와 연결된다. 이 문제의 독립 실행 경계로 쓰지 않는다.
- --wait: 클라이언트 수명에 의존하는 결과 회수를 유지하지 않고, 서버 결과 파일을 재조회하도록 했다.
- maintenance 잠금 하나만 사용: 실행을 대기열에 넣어 버려 중복 배포 거부 요구를 만족하지 않는다.

## 선택과 이유
systemd-run의 기본 transient service를 Type=exec로 실행한다. 공식 문서의 서비스 관리자 소유 비동기 실행 규칙을 근거로 삼고 실제 systemd 실험으로 확인했다: https://www.freedesktop.org/software/systemd/man/latest/systemd-run.html (Description, --pipe, --scope).

구현 전 별도 서비스 실험에서 호출자 프로세스 그룹에 SIGHUP을 보내고 파이프 reader를 닫았다. caller_exit=-1, 서비스 PPid=1, 이후 로그 SURVIVED를 확인했다. 아래 수정 후 실험에서도 worker PPid=1을 단언한다. mock systemd-run으로 독립 실행을 주장한 것이 아니다.

start는 접수용 nonblocking flock을 잡고 고정 workflow-release.service의 상태를 검사한다. 실행 중이면 75를 돌려준다. 고정 유닛 이름 자체도 중복 생성을 막는다. 요청·업로드를 root 전용 /var/lib/workflow-release/<JOB>으로 복사한 뒤 서비스에 넘긴다. SSH 임시 업로드를 지워도 진행된다. bootstrap의 기존 *.sh 설치 규칙으로 새 release-job.sh도 설치된다.

worker는 maintenance 잠금을 잡고 release.sh에 같은 FD 9의 열린 파일 설명을 상속한다. lock_ops는 같은 inode의 FD만 재사용하며 다시 잠근다. wrapper가 결과를 원자적으로 기록한 뒤 종료할 때까지 잠금이 유지된다. 로그 run.log와 결과 exit.code는 root 600, 작업 폴더는 700이다. 기존 실패 로그의 비밀값 치환도 유지되고 자리표시자 기반 회귀 검증을 했다. archive 복사본은 작업 종료 시 지운다.

로컬 deploy/rollback은 접수 후 follow 명령으로 로그를 읽고 서버의 종료 코드를 받는다. reader가 죽어도 worker에는 영향이 없다. 접수 응답 유실은 성공/실패로 추정하지 않고 latest와 서버 상태 확인을 안내한다. 결과가 없고 유닛도 끝났다면 125/OUTCOME_UNKNOWN이다. keepalive는 30초·4회로 설정했다.

## 전후 수치·결과
```text
DISCONNECT_BEFORE scenario=SUCCESS exit=-13 elapsed_s=0.084 links=('20261028T120000Z-bbbbbbb', '20261027T120000Z-aaaaaaa', 'MISSING') next_stage=NOT_RUN
DISCONNECT_BEFORE scenario=FAILURE exit=-13 elapsed_s=0.134 links=('20261028T120000Z-bbbbbbb', '20261027T120000Z-aaaaaaa', 'MISSING') next_stage=NOT_RUN
```

```text
DISCONNECT_AFTER scenario=SUCCESS follower_exit=-1 worker_exit=0 reconnect_exit=0 duplicate_exit=75 maintenance_busy=1 queued_duplicate_exit=75 parent=1 elapsed_s=0.619 links=('20261028T120000Z-bbbbbbb', '20261028T120000Z-bbbbbbb', '20261027T120000Z-aaaaaaa')
RELEASE_JOB job=activate-20261028T092401Z-0G6Qbb5K state=WAITING_FOR_MAINTENANCE
RELEASE_JOB job=activate-20261028T092401Z-0G6Qbb5K state=RUNNING
HEALTH_WAIT limit_s=5 migration=NO result=UP elapsed_s=0
ACTIVATE release=20261028T120000Z-bbbbbbb health_elapsed_s=0
Started BackendApplication in 1.0 seconds
RETAIN releases=2
RELEASE_RESULT job=activate-20261028T092401Z-0G6Qbb5K exit=0
DISCONNECT_AFTER scenario=MANUAL_ROLLBACK follower_exit=-1 worker_exit=0 links=('20261027T120000Z-aaaaaaa', '20261027T120000Z-aaaaaaa', '20261028T120000Z-bbbbbbb')
RELEASE_JOB job=rollback-20261028T092401Z-UcLrLr8P state=WAITING_FOR_MAINTENANCE
RELEASE_JOB job=rollback-20261028T092401Z-UcLrLr8P state=RUNNING
HEALTH_WAIT limit_s=5 migration=NO result=UP elapsed_s=0
ROLLBACK_OK release=20261027T120000Z-aaaaaaa
RELEASE_RESULT job=rollback-20261028T092401Z-UcLrLr8P exit=0
DISCONNECT_AFTER scenario=FAILURE follower_exit=-1 worker_exit=1 reconnect_exit=1 duplicate_exit=75 maintenance_busy=1 queued_duplicate_exit=75 parent=1 elapsed_s=0.402 links=('20261027T120000Z-aaaaaaa', '20261027T120000Z-aaaaaaa', 'MISSING')
RELEASE_JOB job=activate-20261028T092402Z-EJwhstS8 state=WAITING_FOR_MAINTENANCE
RELEASE_JOB job=activate-20261028T092402Z-EJwhstS8 state=RUNNING
HEALTH_WAIT limit_s=5 migration=NO result=APP_FAILED elapsed_s=0
ACTIVATE_FAILED release=20261028T120000Z-bbbbbbb rollback=20261027T120000Z-aaaaaaa
Started BackendApplication in 1.0 seconds
[REDACTED]
HEALTH_WAIT limit_s=5 migration=NO result=UP elapsed_s=0
ROLLBACK_OK release=20261027T120000Z-aaaaaaa
RELEASE_RESULT job=activate-20261028T092402Z-EJwhstS8 exit=1
```

추가 검증: 기존 maintenance 보유 중에는 worker가 WAITING 상태이고 링크 변경이 0이었다. 대기 중 두 번째 activate와 실행 중 rollback은 모두 75. 작업 중 다른 flock -n은 1, 종료 후 0. follow 재접속 결과는 worker의 0/1과 일치했다. root 권한 700/600 및 업로드 복사본 삭제를 단언했다.

## 부작용·남은 위험·확인하지 못한 것
실제 EC2, SSH 네트워크 전환, Mac 잠자기, AWS/DuckDNS는 호출하지 않았다. 운영 앱/DB/Flyway의 900초 실행 대신 가짜 앱 상태와 짧은 fixture 한계를 사용했다. 실제 systemd 실행·부모 관계·출력 연결 종료·복구 분기는 확인했다.

호스트 Stop/재부팅·디스크 고갈·관리자의 서비스 강제 종료는 계속 실행을 보장할 수 없다. 결과 파일 없는 작업은 자동 재실행하지 않는다. MariaDB DDL은 이전 jar로 되돌려도 복구되지 않는다. 서버 로그는 자동 보관 상한이 없고 root가 관리해야 한다. 완료 유닛은 --collect로 제거되므로 결과 파일이 기준이다. raw release.sh를 직접 호출하면 접수 거부 규칙을 우회하므로 운영 절차에서 금지한다(maintenance 상호 배제는 유지).

## 커밋
이 문서를 추가한 변경이 P2 연결 분리의 독립 커밋이다. git log --oneline -- docs/troubleshooting/M5-20-detached-release.md로 확인할 수 있다. 최종 해시는 완료 보고에 남긴다.
