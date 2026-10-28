# M5-21 — 마감 직전 UP을 TIMEOUT으로 판정하는 문제

## 증상
health_wait는 UP을 받으면 플래그를 켜고 while 처음으로 돌아갔다. 응답 처리 중 제한 시간이 지나면 systemd 재검사보다 while 조건이 먼저 실패해 정상 응답도 TIMEOUT이었다.

## 재현 방법
기준 1f27cb0 common.sh 복사본으로 먼저 실행했다. health-wait.py의 deadline_checks는 가짜 curl이 1.2초 뒤 UP 본문을 돌려주고, 가짜 jq가 그 본문을 받은 뒤 1초 기다려 실제 jq에 전달한다. 제한은 2초다. curl 응답 자체는 요청 제한 안에 들어오며 본문 처리 중 경계를 반드시 넘기도록 해 실행 시각의 소수점에 좌우되지 않는다. 실제 네트워크 지연·운영 발생 확률을 뜻하지 않는다.

정상 UP 3회는 모두 수정 전 실패했다. 별도 UP_THEN_RESTART에서는 가짜 curl이 응답을 보낸 뒤 systemctl의 NRestarts를 증가시켰다. 수정 전에는 이 경우도 재검사를 못 하고 TIMEOUT이었다.

## 원인
UP 이후 안전 확인과 성공 판정이 다음 반복에 있어, deadline 검사에 의해 두 단계 모두 건너뛰었다.

## 검토했지만 버린 방법과 이유
- 제한 시간을 늘리기: 경계 조건 자체는 남는다.
- UP 즉시 성공 반환: 응답 직후 앱 재시작·실패를 놓친다.
- curl max-time 제거: 무응답 요청의 대기 상한을 없애므로 사용하지 않는다.

## 선택한 방법
UP 분기 안에서 즉시 health_snapshot을 다시 읽는다. 재시작 수 증가·failed는 APP_FAILED, 조회 실패는 STATUS_UNAVAILABLE, 정상은 UP으로 반환한다. 이 분기는 deadline을 다시 검사하지 않는다. 나머지 timeout·900/90초 선택·남은 요청 시간 상한은 유지한다.

## 전후 수치
| 시나리오 | 수정 전 | 수정 후 |
|---|---|---|
| 경계 UP 3회 | TIMEOUT 3/3, exit 1 | UP 3/3, exit 0 |
| UP 직후 NRestarts 증가 | TIMEOUT, exit 1 | APP_FAILED, exit 1 |

```text
DEADLINE_BEFORE scenario=UP exit=1 elapsed_s=2.270 HEALTH_WAIT limit_s=2 migration=NO result=TIMEOUT elapsed_s=2
DEADLINE_BEFORE scenario=UP exit=1 elapsed_s=2.234 HEALTH_WAIT limit_s=2 migration=NO result=TIMEOUT elapsed_s=2
DEADLINE_BEFORE scenario=UP exit=1 elapsed_s=2.234 HEALTH_WAIT limit_s=2 migration=NO result=TIMEOUT elapsed_s=3
DEADLINE_BEFORE scenario=UP_THEN_RESTART exit=1 elapsed_s=2.230 HEALTH_WAIT limit_s=2 migration=NO result=TIMEOUT elapsed_s=2
DEADLINE_AFTER scenario=UP exit=0 elapsed_s=2.247 HEALTH_WAIT limit_s=2 migration=NO result=UP elapsed_s=2
DEADLINE_AFTER scenario=UP exit=0 elapsed_s=2.247 HEALTH_WAIT limit_s=2 migration=NO result=UP elapsed_s=2
DEADLINE_AFTER scenario=UP exit=0 elapsed_s=2.279 HEALTH_WAIT limit_s=2 migration=NO result=UP elapsed_s=3
DEADLINE_AFTER scenario=UP_THEN_RESTART exit=1 elapsed_s=2.255 HEALTH_WAIT limit_s=2 migration=NO result=APP_FAILED elapsed_s=2
```

## 부작용·남은 위험
정상 UP을 받은 뒤의 systemd 확인 때문에 함수가 deadline보다 조금 늦게 끝날 수 있다. 이는 요청한 판정 순서다. systemctl 조회 자체가 OS 장애로 멈추는 상황의 별도 강제 제한은 이번 범위에 없다. 네트워크·실제 앱 대신 로컬 fixture로 판별력을 확인했다.

## 결과·커밋
기존 health 5개도 통과하고 새 경계 4개가 offline-tools.py 전체 실행에 포함됐다. 이 문서를 추가한 변경이 P3 경계 처리의 독립 커밋이다. 전체 결과는 M5-22-rereview-results.md에 기록한다.
