# M5-24 — 명시적 완료 경로에서만 배포 성공 확정

## 증상
6a1ab7b는 TERM·INT·HUP을 구분했지만 RUNNING의 health 대기에서 USR1을 받으면 EXIT 트랩이 직전 상태 0을 기록했다. app은 NEW, web은 OLD, previous-release는 없는데 exit.code와 follow가 모두 0이었다. 정상 운영의 stop 신호는 TERM이지만, 별도로 처리하지 않은 종료 신호에 대해 성공 오기록이 남았다.

## 재현 방법
작업 브랜치는 feat/m5-deploy, 기준은 6a1ab7b다. 운영 코드 변경 전에 해당 커밋의 common.sh/release.sh/release-job.sh를 /private/tmp/m5-completion-before에 보관했다. 먼저 추가한 completion_checks를 이 원본에 실행해 USR1 오기록을 3회 확인했다.

Ubuntu 24.04, Bash 5.2.21(aarch64), 실제 systemd 255.4-1ubuntu8.18 PID 1의 일회성 컨테이너를 사용했다. 호스트 파일·소켓·포트는 연결하지 않고 --network none, --privileged --cgroupns=private, /run·/run/lock·실행 가능한 /tmp tmpfs로 실행했다. 이 특권 설정은 로컬 검증 컨테이너용이다. 앱·curl·DB 버전은 기존 fixture이고 배포 관리자의 systemctl/systemd-run만 실제로 실행한다.

release-disconnect.py의 fixture가 health gate에서 기다리는 동안 systemctl kill --kill-whom=all --signal=USR1 workflow-release.service를 호출했다. gate는 열지 않는다. 종료 결과·follow 코드·링크·restart 횟수와 자동 복구 미실행을 검사한다. 수정 후에는 별도로 요청 파일을 대기 중 제거해, release.sh를 부르기 전 부모 셸의 mapfile 리다이렉션이 set -e로 실패하는 경우도 검증했다.

## 원인
신호별 처리기를 추가해도 모든 종료 원인을 열거할 수 없다. EXIT에 도달했다는 사실과 $?=0만으로 release.sh가 끝났다고 판단했다. 정상 완료를 증명하는 명시적인 상태가 없었다.

## 검토했지만 버린 방법과 이유
- USR1·USR2·ALRM 처리기 추가: 열거하지 않은 종료 신호에 같은 문제가 남는다.
- 완료 표시가 없는 모든 결과를 125로 변경: 기존 set -e 실패 1과 TERM·INT·HUP의 143·130·129를 덮어쓴다.
- 완료 표시만 남기고 EXIT의 $?를 그대로 사용: 종료 상태를 받은 뒤 변수 대입의 0이 실제 실패 코드를 덮을 수 있으므로 수거한 상태를 별도로 보존한다.
- 결과 파일 직접 덮어쓰기: 읽는 쪽에서 빈 파일·부분 기록을 볼 수 있어 기존 임시 파일 rename 방식을 유지한다.
- 중단 시 자동 복구·재실행: 이번 범위에 없고 반쯤 진행된 링크·스키마 상태를 확정할 수 없으므로 하지 않는다.

## 선택한 방법
초기 completed=NO와 release_result=125를 둔다. 외부 release.sh 호출이 반환한 상태를 if/else에서 직접 저장한 후에만 completed=YES를 표시하고 해당 코드로 종료한다. 외부 스크립트 자신의 set -e는 유지되며 기존 FAILURE 실험으로 1이 보존됨을 확인했다.

finish의 판정 순서는 다음과 같다.
1. 완료를 확인했고 명시적 중단이 아니면, 직전 명령의 $? 대신 수거한 release_result를 사용한다.
2. 완료를 확인하지 못했는데 EXIT 상태가 0이면 125로 바꾸고 interrupted=UNKNOWN을 기록한다.
3. 기존 비0 실패와 명시적인 TERM·INT·HUP 코드는 보존한다.

로그에 completed=YES/NO를 함께 남긴다. YES는 호출의 종료 상태를 수거했다는 의미이고, 성공 여부는 exit 코드로 판단한다. follow는 기존 비정상 코드 안내를 통해 125와 ABNORMAL_EXIT를 반환한다. README 7절도 결과 파일이 없는 경우와 명시적 완료 미확인의 125를 함께 설명하도록 수정했다.

### 결과 기록 경합 보완
첫 보완안은 성공 오기록을 막았지만 USR1 전체 프로세스 전달 중 새로 실행한 정리용 자식도 신호를 받아 결과 파일이 남지 않는 경우가 드러났다. 첫 targeted 검증은 결과 파일 대기 시간 초과로 실패했다. 추가 fixture 실험에서는 run.log에 125가 출력됐지만 archive와 request만 남고 exit.code가 없었다. systemd journal에서도 신호 전달 과정에 새 bash 자식이 포함되는 것을 확인했다.

```text
FIRST_IMPLEMENTATION exit.code_exists=False
FIRST_IMPLEMENTATION files=archive.tar.gz,request,run.log
RELEASE_JOB job=activate-20261028T105031Z-CgEHKJQJ state=WAITING_FOR_MAINTENANCE
RELEASE_JOB job=activate-20261028T105031Z-CgEHKJQJ state=RUNNING
RELEASE_RESULT job=activate-20261028T105031Z-CgEHKJQJ exit=125 interrupted=UNKNOWN completed=NO
/tmp/m5-completion-debug-ylhlc0h4/scripts/release-job.sh: line 73:   398 User defined signal 1   "$here/release.sh" "${request[@]}"
/tmp/m5-completion-debug-ylhlc0h4/scripts/common.sh: line 1:   393 User defined signal 1   curl --silent --fail --max-time "$request_limit" --resolve "$domain:443:127.0.0.1" "https://$domain/healthz"
       394                       | jq -e '.status == "UP"' > /dev/null 2>&1
/tmp/m5-completion-debug-ylhlc0h4/scripts/common.sh: line 1:   399 User defined signal 1   jq -e '.status == "UP"' > /dev/null 2>&1

```

따라서 finish는 별도의 rm/mv 자식을 만들지 않고 exec로 같은 PID에서 기존 Python을 실행한다. 표준 라이브러리로 아카이브 정리 후 임시 파일 작성·원자적 rename을 수행하고 원래 결과로 종료한다. 기존 maintenance FD 9도 exec를 통해 유지한다. 정리만 실패하면 경고를 남기고 결과 기록은 계속한다. 라이브러리나 런타임을 추가하지 않았으며 이미 배포 도구에서 사용하는 Python이다. 결과 파일 쓰기 자체가 불가능한 경우의 불확실 결과 처리(125)는 유지한다.

## 전후 수치
링크는 app/web/previous-release 순서이며 OLD/NEW는 fixture 릴리스다.

| 시나리오 | 수정 전 exit.code / follow | 수정 후 exit.code / follow | 링크 |
|---|---|---|---|
| RUNNING + USR1, 각 3회 | 0 / 0, 오기록 3/3 | 125 / 125, 오기록 0/3 | NEW / OLD / 없음 유지 |
| 완료 전 set -e 실패 | 추가 회귀 검증 | 1 / 1 | OLD / OLD / 없음 |
| SUCCESS | 0 / 0 | 0 / 0 | NEW / NEW / OLD |
| FAILURE | 1 / 1 | 1 / 1 | OLD / OLD / 없음, ROLLBACK_OK |
| MANUAL_ROLLBACK | 0 / 0 | 0 / 0 | OLD / OLD / NEW |
| TERM, 두 지점 각 3회 | 143 / 143 | 143 / 143 | 중단 전 링크 유지 |
| INT, 두 지점 각 1회 | 130 / 130 | 130 / 130 | 중단 전 링크 유지 |
| HUP, 두 지점 각 1회 | 129 / 129 | 129 / 129 | 중단 전 링크 유지 |

USR1 중단 이후 restart 추가 호출·자동 복구·gate 재개는 0건이다. 아래 elapsed_s는 로컬 gate 실험의 실행 시간이며 운영 발생 확률이나 성능 수치가 아니다.

### 수정 전 원본 출력
```text
COMPLETION_BEFORE scenario=USR1 attempt=1 exit.code=0 follow_exit=0 links=('20261028T120000Z-bbbbbbb', '20261027T120000Z-aaaaaaa', 'MISSING') restart_calls=1 elapsed_s=0.263
RELEASE_RESULT job=activate-20261028T104722Z-FjjKhEKV exit=0 interrupted=NO
COMPLETION_BEFORE scenario=USR1 attempt=2 exit.code=0 follow_exit=0 links=('20261028T120000Z-bbbbbbb', '20261027T120000Z-aaaaaaa', 'MISSING') restart_calls=1 elapsed_s=0.228
RELEASE_RESULT job=activate-20261028T104722Z-rI074WVK exit=0 interrupted=NO
COMPLETION_BEFORE scenario=USR1 attempt=3 exit.code=0 follow_exit=0 links=('20261028T120000Z-bbbbbbb', '20261027T120000Z-aaaaaaa', 'MISSING') restart_calls=1 elapsed_s=0.289
RELEASE_RESULT job=activate-20261028T104722Z-ObBPy7D5 exit=0 interrupted=NO
```

### 최종 전체 검증 출력
python3 /work/checks/offline-tools.py --release-jobs, 종료 코드 0. 기존 배포 3경로·명시적 신호 10회·USR1 3회·완료 전 실패 1회가 모두 통과했다. DuckDNS 로컬 6개, health 9개, 도메인·probe·로그·keepalive 검증도 포함된다.

```text
DUCKDNS_LOCAL scenario=OK exit=0 update_calls=1 ps_samples=11 token_leaks=0 external_calls=0 elapsed_s=0.185
DUCKDNS_LOCAL scenario=KO exit=1 update_calls=8 ps_samples=200 token_leaks=0 external_calls=0 elapsed_s=3.141
DUCKDNS_LOCAL scenario=NO_RESPONSE exit=1 update_calls=4 ps_samples=192 token_leaks=0 external_calls=0 elapsed_s=3.117
DUCKDNS_LOCAL scenario=DELAYED_DNS exit=0 update_calls=1 ps_samples=43 token_leaks=0 external_calls=0 elapsed_s=0.672
DUCKDNS_LOCAL scenario=DNS_MISMATCH exit=1 update_calls=1 ps_samples=204 token_leaks=0 external_calls=0 elapsed_s=3.097
DUCKDNS_LOCAL scenario=DELAYED_IMDS exit=0 update_calls=1 ps_samples=49 token_leaks=0 external_calls=0 elapsed_s=0.762
HEALTH_FIXTURE scenario=LATE_UP exit=0 elapsed_s=3.123 external_calls=0
HEALTH_WAIT limit_s=6 migration=YES result=UP elapsed_s=4
HEALTH_FIXTURE scenario=TIMEOUT exit=1 elapsed_s=2.047 external_calls=0
HEALTH_WAIT limit_s=2 migration=NO result=TIMEOUT elapsed_s=2
HEALTH_FIXTURE scenario=RESTART exit=1 elapsed_s=1.031 external_calls=0
HEALTH_WAIT limit_s=6 migration=YES result=APP_FAILED elapsed_s=1
HEALTH_FIXTURE scenario=FAILED exit=1 elapsed_s=1.015 external_calls=0
HEALTH_WAIT limit_s=6 migration=YES result=APP_FAILED elapsed_s=1
HEALTH_FIXTURE scenario=STATUS_DELAY exit=1 elapsed_s=3.024 external_calls=0
HEALTH_WAIT limit_s=2 migration=NO result=TIMEOUT elapsed_s=3
DEADLINE_AFTER scenario=UP exit=0 elapsed_s=2.311 HEALTH_WAIT limit_s=2 migration=NO result=UP elapsed_s=2
DEADLINE_AFTER scenario=UP exit=0 elapsed_s=2.238 HEALTH_WAIT limit_s=2 migration=NO result=UP elapsed_s=2
DEADLINE_AFTER scenario=UP exit=0 elapsed_s=2.241 HEALTH_WAIT limit_s=2 migration=NO result=UP elapsed_s=2
DEADLINE_AFTER scenario=UP_THEN_RESTART exit=1 elapsed_s=2.249 HEALTH_WAIT limit_s=2 migration=NO result=APP_FAILED elapsed_s=3
DOMAIN_CHECK remote=4/4 probe_rejections=2/2 keepalive=2/2 follow_exit=3/3 network_calls=0
PROBE requests=10 failures=3 longest_failure_s=0.461 interval_s=0.200
PROBE_CHECK csv_rows=10 expected_503=3 actual_503=3
method	path	count	p50_ms	p95_ms	max_ms
GET	/api/v1/tasks/{id}	3	20.000	30.000	30.000
invalid_lines=1
DISCONNECT_AFTER scenario=SUCCESS follower_exit=-1 worker_exit=0 reconnect_exit=0 duplicate_exit=75 maintenance_busy=1 queued_duplicate_exit=75 parent=1 elapsed_s=0.432 links=('20261028T120000Z-bbbbbbb', '20261028T120000Z-bbbbbbb', '20261027T120000Z-aaaaaaa')
RELEASE_JOB job=activate-20261028T105858Z-eAQ4ZaSv state=WAITING_FOR_MAINTENANCE
RELEASE_JOB job=activate-20261028T105858Z-eAQ4ZaSv state=RUNNING
HEALTH_WAIT limit_s=5 migration=NO result=UP elapsed_s=0
ACTIVATE release=20261028T120000Z-bbbbbbb health_elapsed_s=0
Started BackendApplication in 1.0 seconds
RETAIN releases=2
RELEASE_RESULT job=activate-20261028T105858Z-eAQ4ZaSv exit=0 interrupted=NO completed=YES
DISCONNECT_AFTER scenario=MANUAL_ROLLBACK follower_exit=-1 worker_exit=0 reconnect_exit=0 links=('20261027T120000Z-aaaaaaa', '20261027T120000Z-aaaaaaa', '20261028T120000Z-bbbbbbb')
RELEASE_JOB job=rollback-20261028T105859Z-DYuUkm9u state=WAITING_FOR_MAINTENANCE
RELEASE_JOB job=rollback-20261028T105859Z-DYuUkm9u state=RUNNING
HEALTH_WAIT limit_s=5 migration=NO result=UP elapsed_s=0
ROLLBACK_OK release=20261027T120000Z-aaaaaaa
RELEASE_RESULT job=rollback-20261028T105859Z-DYuUkm9u exit=0 interrupted=NO completed=YES
DISCONNECT_AFTER scenario=FAILURE follower_exit=-1 worker_exit=1 reconnect_exit=1 duplicate_exit=75 maintenance_busy=1 queued_duplicate_exit=75 parent=1 elapsed_s=0.350 links=('20261027T120000Z-aaaaaaa', '20261027T120000Z-aaaaaaa', 'MISSING')
RELEASE_JOB job=activate-20261028T105859Z-obL5ezHZ state=WAITING_FOR_MAINTENANCE
RELEASE_JOB job=activate-20261028T105859Z-obL5ezHZ state=RUNNING
HEALTH_WAIT limit_s=5 migration=NO result=APP_FAILED elapsed_s=0
ACTIVATE_FAILED release=20261028T120000Z-bbbbbbb rollback=20261027T120000Z-aaaaaaa
Started BackendApplication in 1.0 seconds
[REDACTED]
HEALTH_WAIT limit_s=5 migration=NO result=UP elapsed_s=0
ROLLBACK_OK release=20261027T120000Z-aaaaaaa
RELEASE_RESULT job=activate-20261028T105859Z-obL5ezHZ exit=1 interrupted=NO completed=YES
INTERRUPT_AFTER scenario=WAITING_STOP signal=TERM attempt=1 stop_exit=0 exit.code=143 follow_exit=143 links=('20261027T120000Z-aaaaaaa', '20261027T120000Z-aaaaaaa', 'MISSING') restart_calls=0 elapsed_s=0.119
RELEASE_RESULT job=activate-20261028T105859Z-q4teo9Qa exit=143 interrupted=YES completed=NO
INTERRUPTED: 링크가 반쯤 바뀌었을 수 있음, 확인 후 재시도
INTERRUPT_AFTER scenario=WAITING_STOP signal=TERM attempt=2 stop_exit=0 exit.code=143 follow_exit=143 links=('20261027T120000Z-aaaaaaa', '20261027T120000Z-aaaaaaa', 'MISSING') restart_calls=0 elapsed_s=0.120
RELEASE_RESULT job=activate-20261028T105859Z-EFsMTHj7 exit=143 interrupted=YES completed=NO
INTERRUPTED: 링크가 반쯤 바뀌었을 수 있음, 확인 후 재시도
INTERRUPT_AFTER scenario=WAITING_STOP signal=TERM attempt=3 stop_exit=0 exit.code=143 follow_exit=143 links=('20261027T120000Z-aaaaaaa', '20261027T120000Z-aaaaaaa', 'MISSING') restart_calls=0 elapsed_s=0.121
RELEASE_RESULT job=activate-20261028T105900Z-PVaxokyh exit=143 interrupted=YES completed=NO
INTERRUPTED: 링크가 반쯤 바뀌었을 수 있음, 확인 후 재시도
INTERRUPT_AFTER scenario=WAITING_STOP signal=INT attempt=4 stop_exit=0 exit.code=130 follow_exit=130 links=('20261027T120000Z-aaaaaaa', '20261027T120000Z-aaaaaaa', 'MISSING') restart_calls=0 elapsed_s=0.128
RELEASE_RESULT job=activate-20261028T105900Z-FJO3lLAq exit=130 interrupted=YES completed=NO
INTERRUPTED: 링크가 반쯤 바뀌었을 수 있음, 확인 후 재시도
INTERRUPT_AFTER scenario=WAITING_STOP signal=HUP attempt=5 stop_exit=0 exit.code=129 follow_exit=129 links=('20261027T120000Z-aaaaaaa', '20261027T120000Z-aaaaaaa', 'MISSING') restart_calls=0 elapsed_s=0.132
RELEASE_RESULT job=activate-20261028T105900Z-WgENB0tO exit=129 interrupted=YES completed=NO
INTERRUPTED: 링크가 반쯤 바뀌었을 수 있음, 확인 후 재시도
INTERRUPT_AFTER scenario=RUNNING_STOP signal=TERM attempt=1 stop_exit=0 exit.code=143 follow_exit=143 links=('20261028T120000Z-bbbbbbb', '20261027T120000Z-aaaaaaa', 'MISSING') restart_calls=1 elapsed_s=0.141
RELEASE_RESULT job=activate-20261028T105900Z-sEPwXq1t exit=143 interrupted=YES completed=NO
INTERRUPTED: 링크가 반쯤 바뀌었을 수 있음, 확인 후 재시도
INTERRUPT_AFTER scenario=RUNNING_STOP signal=TERM attempt=2 stop_exit=0 exit.code=143 follow_exit=143 links=('20261028T120000Z-bbbbbbb', '20261027T120000Z-aaaaaaa', 'MISSING') restart_calls=1 elapsed_s=0.152
RELEASE_RESULT job=activate-20261028T105900Z-bDzIRN0a exit=143 interrupted=YES completed=NO
INTERRUPTED: 링크가 반쯤 바뀌었을 수 있음, 확인 후 재시도
INTERRUPT_AFTER scenario=RUNNING_STOP signal=TERM attempt=3 stop_exit=0 exit.code=143 follow_exit=143 links=('20261028T120000Z-bbbbbbb', '20261027T120000Z-aaaaaaa', 'MISSING') restart_calls=1 elapsed_s=0.146
RELEASE_RESULT job=activate-20261028T105900Z-tkKdZfac exit=143 interrupted=YES completed=NO
INTERRUPTED: 링크가 반쯤 바뀌었을 수 있음, 확인 후 재시도
INTERRUPT_AFTER scenario=RUNNING_STOP signal=INT attempt=4 stop_exit=0 exit.code=130 follow_exit=130 links=('20261028T120000Z-bbbbbbb', '20261027T120000Z-aaaaaaa', 'MISSING') restart_calls=1 elapsed_s=0.170
RELEASE_RESULT job=activate-20261028T105900Z-lyRhyQvb exit=130 interrupted=YES completed=NO
INTERRUPTED: 링크가 반쯤 바뀌었을 수 있음, 확인 후 재시도
INTERRUPT_AFTER scenario=RUNNING_STOP signal=HUP attempt=5 stop_exit=0 exit.code=129 follow_exit=129 links=('20261028T120000Z-bbbbbbb', '20261027T120000Z-aaaaaaa', 'MISSING') restart_calls=1 elapsed_s=0.166
RELEASE_RESULT job=activate-20261028T105901Z-ZEsSKQpQ exit=129 interrupted=YES completed=NO
INTERRUPTED: 링크가 반쯤 바뀌었을 수 있음, 확인 후 재시도
COMPLETION_AFTER scenario=USR1 attempt=1 exit.code=125 follow_exit=125 links=('20261028T120000Z-bbbbbbb', '20261027T120000Z-aaaaaaa', 'MISSING') restart_calls=1 elapsed_s=0.166
RELEASE_RESULT job=activate-20261028T105901Z-h0jKTZBj exit=125 interrupted=UNKNOWN completed=NO
ABNORMAL_EXIT: code=125; inspect server logs and release links before retrying.
COMPLETION_AFTER scenario=USR1 attempt=2 exit.code=125 follow_exit=125 links=('20261028T120000Z-bbbbbbb', '20261027T120000Z-aaaaaaa', 'MISSING') restart_calls=1 elapsed_s=0.160
RELEASE_RESULT job=activate-20261028T105901Z-O1rrsMAp exit=125 interrupted=UNKNOWN completed=NO
ABNORMAL_EXIT: code=125; inspect server logs and release links before retrying.
COMPLETION_AFTER scenario=USR1 attempt=3 exit.code=125 follow_exit=125 links=('20261028T120000Z-bbbbbbb', '20261027T120000Z-aaaaaaa', 'MISSING') restart_calls=1 elapsed_s=0.165
RELEASE_RESULT job=activate-20261028T105901Z-JzMgu7Ab exit=125 interrupted=UNKNOWN completed=NO
ABNORMAL_EXIT: code=125; inspect server logs and release links before retrying.
COMPLETION_AFTER scenario=SET_E_FAILURE attempt=4 exit.code=1 follow_exit=1 links=('20261027T120000Z-aaaaaaa', '20261027T120000Z-aaaaaaa', 'MISSING') restart_calls=0 elapsed_s=0.162
RELEASE_RESULT job=activate-20261028T105901Z-hHwhTEit exit=1 interrupted=NO completed=NO
OFFLINE_EXIT=0
```

## 결과·변경 파일·커밋
- deploy/server/release-job.sh: 명시적 완료·종료 상태 수거, 미완료 성공 방지, 동일 PID의 원자적 결과 기록.
- deploy/checks/release-disconnect.py: USR1 전후 재현, 완료 전 set -e 실패와 completed 상태 단언. 기존 release_checks에서 호출하므로 offline-tools.py는 수정하지 않았다.
- deploy/README.md: 125와 completed 로그 해석.
- docs/troubleshooting/M5-24-explicit-release-completion.md: 본 기록.

배포 셸 20개 bash -n, 검증 Python 5개 및 release-job.sh 내장 Python 2개 구문 검사, git diff --check가 통과했다. shellcheck는 미설치라 설치·실행하지 않았다. 백엔드 변경이 없어 요청대로 전체 Maven/UTC 테스트는 재실행하지 않았다. frontend/·backend/·.claude/ 변경 0이며 공유 원본의 feat/m4-home-board도 유지했다. 개발 DB·Redis와 실제 서버·SSH·AWS·DuckDNS는 호출하지 않았다.

이 보완과 문서를 한 독립 커밋으로 기록한다. 최종 해시는 완료 보고와 git log -1 --oneline -- docs/troubleshooting/M5-24-explicit-release-completion.md로 확인할 수 있다. 병합·푸시는 하지 않았다.

## 부작용·남은 위험·확인하지 못한 것
USR2·ALRM을 별도로 실험하지 않았다. 보완은 신호별 분기 대신 명시적 완료 표시를 기준으로 하지만, 전후 수치의 근거는 요청한 USR1이다. 실제 EC2 Stop/재부팅·운영 앱/DB·큰 DDL·SIGKILL·전원 상실·디스크 오류도 시험하지 않았다. SIGKILL이나 기록 실패로 결과 파일이 없으면 기존 follow는 125/OUTCOME_UNKNOWN을 반환한다.

완료 전 set -e 실패의 비0 값은 유지한다. 중단된 링크는 그대로 남을 수 있으므로 운영자가 두 current·previous-release·스키마·health를 확인해야 한다. 자동 복구나 재실행은 추가하지 않았다. 수정 전에 이미 기록된 잘못된 0을 소급 변경하지 않는다. 아카이브 정리 실패 시 root 전용 디렉터리에 파일이 남을 수 있고 RELEASE_CLEANUP_FAILED를 보고 운영자가 정리해야 한다.

검증용 workflow-m5-completion 컨테이너·workflow-m5-completion-systemd 이미지만 제거했다. 사용자 개발 및 다른 테스트 컨테이너·볼륨은 건드리지 않는다. 원본·진단·전체 출력은 로컬 임시 로그와 이 문서에 보존한다.

## 재실행
M5-22의 일회용 systemd 컨테이너 준비 절차를 사용한다. 표시 파일 /run/m5-release-test-container와 실제 PID 1 systemd가 필요하다. 원본은 6a1ab7b의 common.sh/release.sh/release-job.sh를 /before에, 현재 deploy는 /work에 복사한다. 운영 서버에서 실행하지 않는다.

```bash
python3 /work/checks/release-disconnect.py --before-completion-source /before
python3 /work/checks/release-disconnect.py --completion-only
python3 /work/checks/offline-tools.py --release-jobs
```
