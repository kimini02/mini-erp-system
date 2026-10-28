# M5-07 부팅 후 동적 주소 갱신·확인

## 증상
Stop/Start 후 바뀐 공인 IP가 도메인에 아직 반영되지 않아 접속할 수 있다가 끊길 수 있다. 기존 갱신기는 OK만 확인했으며 DNS 전파를 확인하지 않았다. 로컬 배포 도구도 IP 주소를 허용해 이전 주소를 재사용할 수 있었다.

## 재현 조건
EC2/DuckDNS에는 요청하지 않았다. `python3 deploy/checks/offline-tools.py`의 가짜 HTTP 서버가 IMDSv2 PUT·IP GET·DuckDNS update를 실제 loopback 소켓으로 받는다. 테스트 복사본에서 endpoint/경로/시간 상수와 파일 소유자 검사만 시험 환경에 맞춰 치환한다. 생산 스크립트에 endpoint 변경 옵션은 없다. DNS만 가짜 getent로 제어하며 공개 DNS는 조회하지 않는다.

운영 시간 상한 180초를 시험에서는 3초로 줄여 KO·무응답·DNS 불일치가 유한 시간에 종료하는지 확인했다. 요청 제한은 시험 0.5초(운영 5초). 단시간 fixture의 최초 0.15초 제한은 개발 머신의 Python resolver 시작 시간에도 걸려 성공 시나리오가 실패했다. 운영 구현 오류와 구분하여 fixture 시간을 늘린 뒤 6가지가 통과했다.

## 원인
갱신 API의 OK는 로컬 resolver에서 새 주소가 보인다는 보장이 아니다. network-online 이후에도 메타데이터·외부 DNS가 늦어질 수 있다. 갱신 URL에는 토큰이 들어가므로 URL을 curl argv나 HTTP 오류 메시지로 출력하면 비밀값이 노출된다.

## 검토했지만 버린 방법과 이유
- 고정 공인 주소: 사용자 결정인 필요할 때만 켜는 운영과 비용 기준을 따른다.
- 무한 재시도: 실패를 감추고 부팅 확인을 끝낼 수 없다.
- OK 응답만 확인: DNS가 낡은 주소여도 성공으로 판단한다.
- curl URL 인자로 토큰 전달: ps에 노출된다. Python 표준 라이브러리의 메모리 안에만 URL을 만든다.
- 운영 endpoint를 환경변수로 바꾸는 시험 우회: 토큰을 임의 서버로 보내는 경로를 만들지 않는다.
- IP로 배포 허용: 중지 전 주소 재사용을 막기 위해 DuckDNS 호스트만 받는다.

## 선택한 방법
network-online.target 뒤 oneshot 실행. IMDSv2로 읽은 IPv4를 HTTPS 갱신에 사용하고 OK를 확인한다. `getent ahostsv4`의 중복 제거 결과가 그 IP 하나와 같은지 확인해 공인 IP/조회 IP와 MATCH/PENDING을 남긴다. 2→4→8→16→20초(최대 20초) 간격으로 재시도, 전체 180초 안에 같아지지 않으면 exit 1. systemd TimeoutStartSec=195가 추가 상한이며 OnFailure가 기존 실패 알림에 연결된다. 실제 웹후크가 없으면 외부 알림은 보내지 않는다.

/etc/workflow/duckdns는 root:root 600. 새 설정에서 서브도메인과 토큰을 모두 숨김 입력으로 받는다. 서브도메인은 bootstrap의 도메인과 같아야 하고 불일치하면 저장하지 않는다. 기존 파일은 보존한다. token·IMDS session·URL을 로그나 argv에 넣지 않는다. IP와 도메인은 운영 식별 정보이며 로그에 남긴다.

## 전후 수치·결과
이전 모킹 OK/KO/예외 3가지에서 실제 로컬 HTTP 6가지로 확장했다. 종전 DNS 확인 0회 → 이번에는 MATCH가 있어야 성공. 아래 원문은 마지막 실행 결과다(개발 머신의 시간은 운영 성능 수치가 아님).

```text
DUCKDNS_LOCAL scenario=OK exit=0 update_calls=1 ps_samples=37 token_leaks=0 external_calls=0 elapsed_s=0.915
DUCKDNS_LOCAL scenario=KO exit=1 update_calls=8 ps_samples=168 token_leaks=0 external_calls=0 elapsed_s=3.089
DUCKDNS_LOCAL scenario=NO_RESPONSE exit=1 update_calls=4 ps_samples=168 token_leaks=0 external_calls=0 elapsed_s=3.159
DUCKDNS_LOCAL scenario=DELAYED_DNS exit=0 update_calls=1 ps_samples=59 token_leaks=0 external_calls=0 elapsed_s=1.616
DUCKDNS_LOCAL scenario=DNS_MISMATCH exit=1 update_calls=1 ps_samples=133 token_leaks=0 external_calls=0 elapsed_s=3.144
DUCKDNS_LOCAL scenario=DELAYED_IMDS exit=0 update_calls=1 ps_samples=80 token_leaks=0 external_calls=0 elapsed_s=1.611
DOMAIN_CHECK remote=4/4 probe_rejections=2/2 network_calls=0
PROBE requests=10 failures=3 longest_failure_s=0.604 interval_s=0.200
PROBE_CHECK csv_rows=10 expected_503=3 actual_503=3
method	path	count	p50_ms	p95_ms	max_ms
GET	/api/v1/tasks/{id}	3	20.000	30.000	30.000
invalid_lines=1
```

각 실행 중 `ps -p <pid> -o command=`를 반복 조회하고 stdout/stderr와 함께 런타임 생성 시험 토큰·IMDS 세션이 없는지 단언했다. 외부 API 요청은 0건. 인자 검증은 remote 4/4(정상 도메인, IP, 다른 도메인, 위장 접미사), probe의 IP/다른 도메인 거부 2/2. probe는 curl의 테스트 래퍼에서 DuckDNS 이름을 loopback으로 고정한다.

Ubuntu 24.04 컨테이너의 실제 /dev/tty 입력 검증:

```text
SETUP_INPUT prompts=8 secret_echoes=0 permissions=root:root:600 rerun_changes=0
```

시험용 값은 실행 중 생성하며 보고서에 적지 않았다. 실제 /etc 파일은 검증 컨테이너 내부에만 생성했다.

## 부작용·남은 위험
실제 EC2 IMDS·외부 HTTPS·DNS 캐시/전파와 systemd OnFailure 발화는 사용자가 서버에서 확인해야 한다. 로컬 fixture는 TLS 검증을 대체하지 않는다. 전파가 180초보다 오래 걸리면 실패로 끝나므로 journal과 도메인/콘솔 IP를 확인하고 수동 restart한다. IP가 같아도 애플리케이션·인증서가 정상이라는 뜻은 아니며 README의 후속 체크가 필요하다. SSH 키 경고는 지문 확인 없이 무시하지 않는다.

## 커밋
DuckDNS·setup-secrets·도메인 제한·검증 도구와 이 문서의 독립 커밋. 최종 해시는 M5 추가 결과 보고서에 기록한다.
