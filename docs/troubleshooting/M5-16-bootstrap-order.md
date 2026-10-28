# M5-16 bootstrap 재실행 직렬화와 SSH 적용 순서

## 증상
DuckDNS 갱신 실패가 set -e로 bootstrap을 종료해 뒤의 SSH 검사/reload를 건너뛰었다. 운영 중 재실행의 DB·Redis·Nginx 재시작도 백업/배포와 겹칠 수 있었다.

## 재현 방법
Ubuntu 24.04에서 bootstrap-order.py를 실행했다. 실제 bootstrap의 서비스 처리 부분을 읽고 systemctl·sshd·provision만 가짜로 바꾼다. 다른 프로세스가 먼저 maintenance flock을 잡은 동안 0.3초간 bootstrap 서비스 동작이 없는지 단언한 뒤 잠금을 풀어 진행한다. DNS 성공/실패 두 가지를 실행한다.

## 원인
외부 DNS 실행이 SSH 단계 앞에 있었고 공통 maintenance 잠금을 사용하지 않았다.

## 검토했지만 버린 방법과 이유
- DNS 실패를 무시하고 성공 처리: 주소 갱신 실패를 감춘다.
- 명시적 restart만 잠그기: apt의 패키지 갱신이 유발하는 재시작과 설정 배치가 여전히 겹칠 수 있다.
- DNS/타이머까지 잠금 유지: 밀린 백업이 같은 잠금을 기다리며 불필요하게 지연된다.

## 선택한 방법
OS/인자 확인 뒤 apt 실행 전에 lock_ops를 잡아 설치·설정 배치·DB/Redis/Nginx 재시작·계정 준비·SSH 적용을 직렬화한다. 타이머·DuckDNS 앞에서 잠금을 푼다. SSH 검사와 reload를 DNS보다 먼저 하고, DNS 실패는 명시적으로 exit 1이며 journalctl/reset-failed/restart 안내를 출력한다.

## 전후 수치·결과
이전 구조는 DNS 실패 시 SSH 단계 도달 불가, 서비스 재시작 잠금 없음. 수정 후 가짜 서비스 시나리오:

```text
BOOTSTRAP_FIXTURE scenario=DNS_OK exit=0 waited_for_backup=YES restart_locked=YES ssh_before_dns=YES dns_unlocked=YES
BOOTSTRAP_FIXTURE scenario=DNS_FAIL exit=1 waited_for_backup=YES restart_locked=YES ssh_before_dns=YES dns_unlocked=YES
```

가짜 systemctl 안에서도 별도 fd로 같은 잠금을 시도해 서비스 재시작 시 점유 중, DNS 시 해제됨을 확인했다. service tail의 순서는 실제 파일에서 가져오고 lock_ops가 apt 앞에 있는지 별도 단언한다.

## 부작용·남은 위험
bootstrap이 패키지를 받는 동안 배포·백업도 기다린다. 운영 중 재실행은 서비스 중단을 포함하므로 README에 점검 시간을 요구한다. 실제 systemd 재시작·SSH 연결·DuckDNS는 실행하지 않았다.

## 커밋
bootstrap.sh·bootstrap-order.py·README와 이 문서의 독립 커밋. 최종 보고서에 해시를 기록한다.
