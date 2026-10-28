# M5-23 — 중단된 배포 작업을 성공으로 기록하는 문제

## 증상
5fb39d3의 release-job.sh는 SIGTERM으로 종료될 때 EXIT 트랩이 직전 명령의 0을 받아 exit.code=0과 RELEASE_RESULT exit=0을 기록했다. follow도 0을 반환해 운영자가 완료되지 않은 작업을 성공으로 해석할 수 있었다. WAITING에서는 실제 배포를 시작하지 않았고 RUNNING의 health 대기에서는 app만 NEW, web은 OLD였다.

## 재현 방법
작업 브랜치는 feat/m5-deploy, 수정 전 기준은 5fb39d3이다. 운영 스크립트를 바꾸기 전에 그 커밋의 common.sh, release.sh, release-job.sh를 /private/tmp/m5-interrupt-before에 복사했다. 먼저 추가한 release-disconnect.py의 interruption_checks에 --before-interrupt-source로 이 원본 경로를 전달했다.

일회용 Ubuntu 24.04 컨테이너에서 실제 systemd 255.4-1ubuntu8.18을 PID 1로 실행했다. Bash는 5.2.21, aarch64이다. --privileged --cgroupns=private, /run·/run/lock·실행 가능한 /tmp tmpfs를 사용했고, 호스트 파일·소켓 마운트와 공개 포트 없이 --network none으로 실행했다. 이 옵션은 검증 컨테이너용이며 운영 배포 설정이 아니다. 앱 systemctl/curl/journalctl·DB 버전은 기존 fixture를 쓰고, 배포 유닛의 systemctl stop과 systemd-run은 실제 서비스 관리자에 연결했다.

- WAITING_STOP: 시험 프로세스가 maintenance flock을 먼저 잡는다. worker의 WAITING_FOR_MAINTENANCE 로그를 확인한 후 실제 systemctl stop workflow-release.service를 호출한다.
- RUNNING_STOP: 가짜 curl의 health gate에 도달한 것을 확인한다. app NEW/web OLD/previous 없음 상태에서 같은 stop을 호출한다. gate를 열지 않는다.
- 각 지점은 수정 전·후 동일하게 3회 실행한다. stop 자체의 종료 코드 0과 배포 작업의 exit.code를 구분한다. stop이 성공했다는 뜻은 배포 성공과 다르다.
- 수정 후에는 각 지점에서 systemctl kill --kill-who=all --signal=INT/HUP도 1회씩 추가한다. 총 10회다.

## 원인
EXIT만 등록돼 있으면 중단 원인이 $?에 확실히 보존되지 않는다. 실제 컨테이너에서 두 지점 모두 3/3회 0이 기록됐다. 기존 코드는 이 값을 그대로 결과 파일과 follow에 전달했으며, 신호 중단과 정상 종료를 구분하는 정보도 없었다.

## 검토했지만 버린 방법과 이유
- EXIT의 $?만 계속 사용: 수정 전 6/6 오기록이 재현돼 중단 판별 근거로 사용할 수 없다.
- 모든 비정상 종료를 1로 통일: 일반 배포 실패와 외부 중단을 구별하라는 요구를 만족하지 않는다.
- 중단 시 자동 rollback 또는 재실행: 링크·마이그레이션이 어느 단계까지 진행됐는지 확정할 수 없고 이번 지시에서도 금지했다.
- 작업 전체에서 종료 신호 무시: 호스트 종료나 운영자 중단 의도를 숨기므로 선택하지 않았다. 결과 기록의 짧은 마무리 구간에서만 재진입 신호를 무시한다.

## 선택한 방법
TERM/INT/HUP 처리기를 EXIT보다 먼저 설치한다. 처리기는 interrupted=YES로 표시한 뒤 각각 143/130/129(128+신호 번호)로 exit한다. EXIT는 그 명시적인 코드를 원자적 exit.code에 남기며 로그에도 interrupted=YES를 포함한다. 정상 경로는 기존 0/1과 interrupted=NO를 유지한다. 결과 기록 도중 추가 TERM/INT/HUP이 들어와 첫 신호 상태를 바꾸거나 마무리에 재진입하지 않도록 한다.

follow는 0/1을 그대로 반환하고 129/130/143에는 다음 안내를 함께 출력한 뒤 같은 중단 코드를 반환한다. 다른 비정상 코드도 보존하고 ABNORMAL_EXIT로 안내한다. 파일 없이 서비스가 종료된 경우의 기존 125/OUTCOME_UNKNOWN도 유지한다.

```text
RELEASE_RESULT job=… exit=143 interrupted=YES
INTERRUPTED: 링크가 반쯤 바뀌었을 수 있음, 확인 후 재시도
```

README 7절에 0·1·75·125·중단 코드 표와 Stop/재부팅 금지, 아직 대기 중인 작업만 systemctl stop으로 취소한다는 절차를 추가했다. WAITING 로그 확인 직후 RUNNING으로 전환할 수 있는 경합도 명시해 취소 후 링크 확인을 생략하지 않도록 했다. 신호 처리기에서 링크 변경·복구·재실행을 호출하지 않는다.

## 전후 수치
OLD/NEW는 fixture 릴리스이고 링크 열은 app/web/previous-release 순서다.

| 시나리오 | 수정 전 exit.code / follow | 수정 후 exit.code / follow | 중단 뒤 링크(전후 동일) |
|---|---|---|---|
| WAITING + TERM, 각 3회 | 0 / 0 (오기록 3/3) | 143 / 143 (오기록 0/3) | OLD / OLD / 없음 |
| RUNNING + TERM, 각 3회 | 0 / 0 (오기록 3/3) | 143 / 143 (오기록 0/3) | NEW / OLD / 없음 |
| WAITING·RUNNING + INT, 각 1회 | 별도 측정 안 함 | 130 / 130, 2/2 | 각 지점의 중단 전 상태 유지 |
| WAITING·RUNNING + HUP, 각 1회 | 별도 측정 안 함 | 129 / 129, 2/2 | 각 지점의 중단 전 상태 유지 |
| 기존 SUCCESS | 0 / 0 유지 | 0 / 0, interrupted=NO | NEW / NEW / OLD |
| 기존 FAILURE | 1 / 1 유지 | 1 / 1, interrupted=NO | OLD / OLD / 없음, ROLLBACK_OK |
| 기존 MANUAL_ROLLBACK | 0 / 0 유지 | 0 / 0, interrupted=NO | OLD / OLD / NEW |

WAITING 중단의 앱 restart 호출은 0번, RUNNING 중단은 중단 전 호출한 1번뿐이었다. 모든 중단 실험에서 ACTIVATE 완료·ROLLBACK_OK가 없고, 중단 뒤 링크가 바뀌지 않음을 단언했다. 유닛 종료와 maintenance 잠금 해제도 확인했다. 아래 시간은 로컬 gate 실험의 실행 시간이며 운영 성능이나 중단 발생 확률을 나타내지 않는다.

### 수정 전 원본 출력
```text
INTERRUPT_BEFORE scenario=WAITING_STOP signal=TERM attempt=1 stop_exit=0 exit.code=0 follow_exit=0 links=('20261027T120000Z-aaaaaaa', '20261027T120000Z-aaaaaaa', 'MISSING') restart_calls=0 elapsed_s=0.210
RELEASE_RESULT job=activate-20261028T100415Z-EaJVIRkt exit=0
INTERRUPT_BEFORE scenario=WAITING_STOP signal=TERM attempt=2 stop_exit=0 exit.code=0 follow_exit=0 links=('20261027T120000Z-aaaaaaa', '20261027T120000Z-aaaaaaa', 'MISSING') restart_calls=0 elapsed_s=0.180
RELEASE_RESULT job=activate-20261028T100415Z-fppdUZqP exit=0
INTERRUPT_BEFORE scenario=WAITING_STOP signal=TERM attempt=3 stop_exit=0 exit.code=0 follow_exit=0 links=('20261027T120000Z-aaaaaaa', '20261027T120000Z-aaaaaaa', 'MISSING') restart_calls=0 elapsed_s=0.090
RELEASE_RESULT job=activate-20261028T100415Z-mwnjD5AS exit=0
INTERRUPT_BEFORE scenario=RUNNING_STOP signal=TERM attempt=1 stop_exit=0 exit.code=0 follow_exit=0 links=('20261028T120000Z-bbbbbbb', '20261027T120000Z-aaaaaaa', 'MISSING') restart_calls=1 elapsed_s=0.122
RELEASE_RESULT job=activate-20261028T100415Z-3jYpxQIm exit=0
INTERRUPT_BEFORE scenario=RUNNING_STOP signal=TERM attempt=2 stop_exit=0 exit.code=0 follow_exit=0 links=('20261028T120000Z-bbbbbbb', '20261027T120000Z-aaaaaaa', 'MISSING') restart_calls=1 elapsed_s=0.136
RELEASE_RESULT job=activate-20261028T100416Z-62AWSxkH exit=0
INTERRUPT_BEFORE scenario=RUNNING_STOP signal=TERM attempt=3 stop_exit=0 exit.code=0 follow_exit=0 links=('20261028T120000Z-bbbbbbb', '20261027T120000Z-aaaaaaa', 'MISSING') restart_calls=1 elapsed_s=0.155
RELEASE_RESULT job=activate-20261028T100416Z-sWBsYnrW exit=0
```

### 수정 후 전체 오프라인 실행 출력
명령: python3 /work/checks/offline-tools.py --release-jobs. 종료 코드 0. 기존 시나리오와 새 중단 검증이 모두 포함된다.

```text
DUCKDNS_LOCAL scenario=OK exit=0 update_calls=1 ps_samples=22 token_leaks=0 external_calls=0 elapsed_s=1.211
DUCKDNS_LOCAL scenario=KO exit=1 update_calls=8 ps_samples=176 token_leaks=0 external_calls=0 elapsed_s=3.127
DUCKDNS_LOCAL scenario=NO_RESPONSE exit=1 update_calls=4 ps_samples=151 token_leaks=0 external_calls=0 elapsed_s=3.297
DUCKDNS_LOCAL scenario=DELAYED_DNS exit=0 update_calls=1 ps_samples=60 token_leaks=0 external_calls=0 elapsed_s=1.412
DUCKDNS_LOCAL scenario=DNS_MISMATCH exit=1 update_calls=1 ps_samples=107 token_leaks=0 external_calls=0 elapsed_s=3.860
DUCKDNS_LOCAL scenario=DELAYED_IMDS exit=0 update_calls=1 ps_samples=59 token_leaks=0 external_calls=0 elapsed_s=1.980
HEALTH_FIXTURE scenario=LATE_UP exit=0 elapsed_s=3.408 external_calls=0
HEALTH_WAIT limit_s=6 migration=YES result=UP elapsed_s=3
HEALTH_FIXTURE scenario=TIMEOUT exit=1 elapsed_s=1.025 external_calls=0
HEALTH_WAIT limit_s=2 migration=NO result=TIMEOUT elapsed_s=2
HEALTH_FIXTURE scenario=RESTART exit=1 elapsed_s=1.035 external_calls=0
HEALTH_WAIT limit_s=6 migration=YES result=APP_FAILED elapsed_s=1
HEALTH_FIXTURE scenario=FAILED exit=1 elapsed_s=1.063 external_calls=0
HEALTH_WAIT limit_s=6 migration=YES result=APP_FAILED elapsed_s=1
HEALTH_FIXTURE scenario=STATUS_DELAY exit=1 elapsed_s=3.026 external_calls=0
HEALTH_WAIT limit_s=2 migration=NO result=TIMEOUT elapsed_s=3
DEADLINE_AFTER scenario=UP exit=0 elapsed_s=2.255 HEALTH_WAIT limit_s=2 migration=NO result=UP elapsed_s=2
DEADLINE_AFTER scenario=UP exit=0 elapsed_s=2.252 HEALTH_WAIT limit_s=2 migration=NO result=UP elapsed_s=2
DEADLINE_AFTER scenario=UP exit=0 elapsed_s=2.272 HEALTH_WAIT limit_s=2 migration=NO result=UP elapsed_s=2
DEADLINE_AFTER scenario=UP_THEN_RESTART exit=1 elapsed_s=2.246 HEALTH_WAIT limit_s=2 migration=NO result=APP_FAILED elapsed_s=3
DOMAIN_CHECK remote=4/4 probe_rejections=2/2 keepalive=2/2 follow_exit=3/3 network_calls=0
PROBE requests=10 failures=3 longest_failure_s=0.600 interval_s=0.200
PROBE_CHECK csv_rows=10 expected_503=3 actual_503=3
method	path	count	p50_ms	p95_ms	max_ms
GET	/api/v1/tasks/{id}	3	20.000	30.000	30.000
invalid_lines=1
DISCONNECT_AFTER scenario=SUCCESS follower_exit=-1 worker_exit=0 reconnect_exit=0 duplicate_exit=75 maintenance_busy=1 queued_duplicate_exit=75 parent=1 elapsed_s=0.405 links=('20261028T120000Z-bbbbbbb', '20261028T120000Z-bbbbbbb', '20261027T120000Z-aaaaaaa')
RELEASE_JOB job=activate-20261028T100749Z-KjaYXiKU state=WAITING_FOR_MAINTENANCE
RELEASE_JOB job=activate-20261028T100749Z-KjaYXiKU state=RUNNING
HEALTH_WAIT limit_s=5 migration=NO result=UP elapsed_s=0
ACTIVATE release=20261028T120000Z-bbbbbbb health_elapsed_s=0
Started BackendApplication in 1.0 seconds
RETAIN releases=2
RELEASE_RESULT job=activate-20261028T100749Z-KjaYXiKU exit=0 interrupted=NO
DISCONNECT_AFTER scenario=MANUAL_ROLLBACK follower_exit=-1 worker_exit=0 reconnect_exit=0 links=('20261027T120000Z-aaaaaaa', '20261027T120000Z-aaaaaaa', '20261028T120000Z-bbbbbbb')
RELEASE_JOB job=rollback-20261028T100750Z-OTVcAlLw state=WAITING_FOR_MAINTENANCE
RELEASE_JOB job=rollback-20261028T100750Z-OTVcAlLw state=RUNNING
HEALTH_WAIT limit_s=5 migration=NO result=UP elapsed_s=0
ROLLBACK_OK release=20261027T120000Z-aaaaaaa
RELEASE_RESULT job=rollback-20261028T100750Z-OTVcAlLw exit=0 interrupted=NO
DISCONNECT_AFTER scenario=FAILURE follower_exit=-1 worker_exit=1 reconnect_exit=1 duplicate_exit=75 maintenance_busy=1 queued_duplicate_exit=75 parent=1 elapsed_s=0.366 links=('20261027T120000Z-aaaaaaa', '20261027T120000Z-aaaaaaa', 'MISSING')
RELEASE_JOB job=activate-20261028T100750Z-h21ZrYv6 state=WAITING_FOR_MAINTENANCE
RELEASE_JOB job=activate-20261028T100750Z-h21ZrYv6 state=RUNNING
HEALTH_WAIT limit_s=5 migration=NO result=APP_FAILED elapsed_s=0
ACTIVATE_FAILED release=20261028T120000Z-bbbbbbb rollback=20261027T120000Z-aaaaaaa
Started BackendApplication in 1.0 seconds
[REDACTED]
HEALTH_WAIT limit_s=5 migration=NO result=UP elapsed_s=0
ROLLBACK_OK release=20261027T120000Z-aaaaaaa
RELEASE_RESULT job=activate-20261028T100750Z-h21ZrYv6 exit=1 interrupted=NO
INTERRUPT_AFTER scenario=WAITING_STOP signal=TERM attempt=1 stop_exit=0 exit.code=143 follow_exit=143 links=('20261027T120000Z-aaaaaaa', '20261027T120000Z-aaaaaaa', 'MISSING') restart_calls=0 elapsed_s=0.108
RELEASE_RESULT job=activate-20261028T100750Z-NNBLZLZu exit=143 interrupted=YES
INTERRUPTED: 링크가 반쯤 바뀌었을 수 있음, 확인 후 재시도
INTERRUPT_AFTER scenario=WAITING_STOP signal=TERM attempt=2 stop_exit=0 exit.code=143 follow_exit=143 links=('20261027T120000Z-aaaaaaa', '20261027T120000Z-aaaaaaa', 'MISSING') restart_calls=0 elapsed_s=0.112
RELEASE_RESULT job=activate-20261028T100750Z-gFUePfRB exit=143 interrupted=YES
INTERRUPTED: 링크가 반쯤 바뀌었을 수 있음, 확인 후 재시도
INTERRUPT_AFTER scenario=WAITING_STOP signal=TERM attempt=3 stop_exit=0 exit.code=143 follow_exit=143 links=('20261027T120000Z-aaaaaaa', '20261027T120000Z-aaaaaaa', 'MISSING') restart_calls=0 elapsed_s=0.166
RELEASE_RESULT job=activate-20261028T100751Z-vmgIPO0S exit=143 interrupted=YES
INTERRUPTED: 링크가 반쯤 바뀌었을 수 있음, 확인 후 재시도
INTERRUPT_AFTER scenario=WAITING_STOP signal=INT attempt=4 stop_exit=0 exit.code=130 follow_exit=130 links=('20261027T120000Z-aaaaaaa', '20261027T120000Z-aaaaaaa', 'MISSING') restart_calls=0 elapsed_s=0.160
RELEASE_RESULT job=activate-20261028T100751Z-wVLPzLNh exit=130 interrupted=YES
INTERRUPTED: 링크가 반쯤 바뀌었을 수 있음, 확인 후 재시도
INTERRUPT_AFTER scenario=WAITING_STOP signal=HUP attempt=5 stop_exit=0 exit.code=129 follow_exit=129 links=('20261027T120000Z-aaaaaaa', '20261027T120000Z-aaaaaaa', 'MISSING') restart_calls=0 elapsed_s=0.151
RELEASE_RESULT job=activate-20261028T100751Z-EEM6DI24 exit=129 interrupted=YES
INTERRUPTED: 링크가 반쯤 바뀌었을 수 있음, 확인 후 재시도
INTERRUPT_AFTER scenario=RUNNING_STOP signal=TERM attempt=1 stop_exit=0 exit.code=143 follow_exit=143 links=('20261028T120000Z-bbbbbbb', '20261027T120000Z-aaaaaaa', 'MISSING') restart_calls=1 elapsed_s=0.127
RELEASE_RESULT job=activate-20261028T100751Z-TncHLg0f exit=143 interrupted=YES
INTERRUPTED: 링크가 반쯤 바뀌었을 수 있음, 확인 후 재시도
INTERRUPT_AFTER scenario=RUNNING_STOP signal=TERM attempt=2 stop_exit=0 exit.code=143 follow_exit=143 links=('20261028T120000Z-bbbbbbb', '20261027T120000Z-aaaaaaa', 'MISSING') restart_calls=1 elapsed_s=0.145
RELEASE_RESULT job=activate-20261028T100751Z-dT89mhTM exit=143 interrupted=YES
INTERRUPTED: 링크가 반쯤 바뀌었을 수 있음, 확인 후 재시도
INTERRUPT_AFTER scenario=RUNNING_STOP signal=TERM attempt=3 stop_exit=0 exit.code=143 follow_exit=143 links=('20261028T120000Z-bbbbbbb', '20261027T120000Z-aaaaaaa', 'MISSING') restart_calls=1 elapsed_s=0.127
RELEASE_RESULT job=activate-20261028T100751Z-dR9HVKgy exit=143 interrupted=YES
INTERRUPTED: 링크가 반쯤 바뀌었을 수 있음, 확인 후 재시도
INTERRUPT_AFTER scenario=RUNNING_STOP signal=INT attempt=4 stop_exit=0 exit.code=130 follow_exit=130 links=('20261028T120000Z-bbbbbbb', '20261027T120000Z-aaaaaaa', 'MISSING') restart_calls=1 elapsed_s=0.159
RELEASE_RESULT job=activate-20261028T100751Z-aUI3q8RB exit=130 interrupted=YES
INTERRUPTED: 링크가 반쯤 바뀌었을 수 있음, 확인 후 재시도
INTERRUPT_AFTER scenario=RUNNING_STOP signal=HUP attempt=5 stop_exit=0 exit.code=129 follow_exit=129 links=('20261028T120000Z-bbbbbbb', '20261027T120000Z-aaaaaaa', 'MISSING') restart_calls=1 elapsed_s=0.161
RELEASE_RESULT job=activate-20261028T100752Z-eO21AaOu exit=129 interrupted=YES
INTERRUPTED: 링크가 반쯤 바뀌었을 수 있음, 확인 후 재시도
OFFLINE_EXIT=0
```

## 결과·변경 파일·커밋
- deploy/server/release-job.sh: 명시적 신호 상태, interrupted 로그, follow 중단 안내.
- deploy/checks/release-disconnect.py: 두 지점의 수정 전·후 stop 재현, INT/HUP 추가 검증, 기존 정상/실패 결과 보존 단언. 기존 release_checks 호출에 연결돼 offline-tools.py 파일 수정 없이 전체 실행에 포함된다.
- deploy/README.md: 종료 코드 표와 안전한 중단 절차.
- docs/troubleshooting/M5-23-interrupted-release-result.md: 본 기록.

배포 셸 20개 bash -n, 검증 Python 5개와 release-job.sh 내장 follow Python 구문 검사, git diff --check가 통과했다. shellcheck는 미설치라 실행·설치하지 않았다. 백엔드 변경이 없으므로 요청대로 전체 Maven/UTC 테스트는 재실행하지 않았다. frontend/·backend/·.claude/ 변경은 0이다. 로컬 개발 DB·Redis·실제 SSH·AWS·DuckDNS는 호출하지 않았다. 공유 원본 체크아웃의 feat/m4-home-board도 유지했다.

이 항목과 본 문서는 하나의 독립 커밋이다. 최종 해시는 완료 보고와 git log -1 --oneline -- docs/troubleshooting/M5-23-interrupted-release-result.md로 확인한다. 병합·푸시는 하지 않았다.

## 부작용·남은 위험·확인하지 못한 것
실제 EC2 Stop/재부팅·네트워크·운영 앱·DB·마이그레이션은 시험하지 않았다. 이번 근거는 실제 systemd PID 1의 stop/kill과 가짜 앱 상태다. SIGKILL·전원 상실·디스크 오류는 신호 처리기 실행이나 결과 기록을 보장하지 못한다. 결과 파일이 없으면 기존 follow가 125를 돌려주며 성공으로 판단해서는 안 된다. 이번에 SIGKILL·전원 상실을 별도로 실험하지 않았다.

신호가 온 RUNNING 작업은 반쯤 전환된 링크를 그대로 남길 수 있다. 운영자가 링크·previous-release·스키마·health를 확인해야 하고, 자동 복구는 추가하지 않았다. 수정 전에 이미 만들어진 0 결과를 소급 변경하지 않는다. 오래된 중단 작업은 기록과 실제 상태를 따로 확인해야 한다.

검증용 workflow-m5-interrupt 컨테이너와 workflow-m5-interrupt-systemd 이미지만 종료·제거했다. 사용자 개발 컨테이너와 볼륨은 대상이 아니다. 원본/전체 출력은 로컬 임시 로그와 이 문서에 보존한다.

## 재실행
M5-22의 일회성 systemd 컨테이너 준비 절차를 사용한다. /run/m5-release-test-container 표시와 실제 PID 1 systemd를 요구하므로 운영 서버에서 실행하지 않는다. 수정 전 source는 5fb39d3의 common.sh/release.sh/release-job.sh 세 파일이다.

```bash
# 컨테이너에 원본 세 파일을 /before, 현재 deploy를 /work로 복사한 뒤
python3 /work/checks/release-disconnect.py --before-interrupt-source /before
python3 /work/checks/release-disconnect.py --interrupt-only
python3 /work/checks/offline-tools.py --release-jobs
```
