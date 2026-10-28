# WorkFlow 운영 배포 — M5

## 왜 서버 한 대인가

직원 1,000명·부서 50개·피크 초당 30건을 **가정**하고, 운영자 한 명이 원인을 추적하기 쉬운 EC2 한 대로 시작합니다. 이 규모를 처리한다는 성능 검증은 아직 하지 않았습니다.
야간 점검을 허용하고 복구 목표 1시간·최대 하루치 손실을 받아들이는 구성입니다. 부하·가용성·복구 요구가 커지면 구성을 바꿉니다.
근거: [명세 16의 0.1절](../backend/md/16-deploy-ops-m5.md), [아키텍처 8장](../backend/md/11-architecture.md). 이번 구현 기준은 사용자 승인으로 `2027ebd`이며, 명세 파일은 M5 프론트 브랜치 병합 뒤 이 링크에서 볼 수 있습니다. 병합 전에는 `git show 2027ebd:backend/md/16-deploy-ops-m5.md`로 읽습니다.

이 문서의 서버 명령은 **운영자가 허락된 서버에서 실행**합니다. M5 구현 중에는 실제 SSH·AWS CLI·DuckDNS 갱신·인증서 발급을 실행하지 않았습니다. 서버 9장 측정도 별도 수행해야 합니다.

## 1. 배포 전 준비

1. 백엔드와 M5 프론트 변경을 리뷰하고 배포할 커밋에 함께 반영합니다. 프론트의 같은 출처 `/api/v1`, mock 제외, `public/maintenance.html`이 필요합니다. 이 브랜치에서 프론트 파일을 수정하거나 병합하지 않았습니다.
2. Java 17, Node/npm(프론트 lock 파일 기준), Python 3, curl, Git, OpenSSH를 로컬에 준비합니다. Docker는 로컬 복구 리허설과 테스트에만 씁니다. 서버는 jar + systemd로 운영합니다.
3. 전체 테스트 통과 커밋을 사용합니다. 배포 스크립트는 빌드만 하며 테스트를 대신하지 않습니다.
4. 비밀값을 채팅·Git·명령 인자·화면 캡처·보고서에 붙이지 않습니다. `set -x`, `env`, `systemctl show ... Environment`, 비밀값 파일 `cat`도 하지 않습니다. 예시의 `CHANGE_ME`는 실제 값으로 대체하되 비밀값은 아래 숨김 입력으로만 넣습니다.
5. AWS 계정은 배포 직전에 준비합니다. 명세의 무료 플랜 조건은 계정별 콘솔에서 확인하고 유료 전환·Organizations 가입을 하지 않습니다. 가격·잔여 크레딧·종료일은 콘솔에서 확인합니다.

## 2. AWS 콘솔에서 인스턴스 만들기

1. 리전을 **서울(ap-northeast-2)**로 선택합니다.
2. EC2 → 인스턴스 시작: Ubuntu Server **24.04 LTS, x86_64**, **t3.small**, 루트 볼륨 **gp3 30 GiB**. 기본 보안 업데이트를 유지합니다.
3. 키 페어를 만들고 개인 키는 저장소 밖에 보관합니다. 개인 키 권한은 `chmod 600`으로 제한합니다. 키 내용은 공유하지 않습니다.
4. 네트워크는 인터넷 게이트웨이가 있는 퍼블릭 서브넷, **자동 공인 IPv4 할당 활성화**. 중지 후 시작하면 공인 IP가 바뀝니다.
5. 보안 그룹 인바운드: TCP **22는 내 IP/32**, **80·443은 0.0.0.0/0**. 3306·6379·8080·8081은 추가하지 않습니다. IPv6를 별도 구성하지 않는다면 IPv6 인바운드는 추가하지 않습니다.
6. 고급 세부 정보의 **크레딧 사양을 Standard**로 지정합니다. 시작 후 인스턴스의 크레딧 사양 변경 화면에서도 Standard인지 확인합니다. Unlimited로 두지 않습니다.
7. 메타데이터 서비스는 활성화, **IMDSv2 필수**, hop limit 1. DuckDNS 부팅 서비스가 현재 공인 IPv4를 읽습니다. AWS 액세스 키나 IAM 인스턴스 역할은 이 작업에 필요 없습니다.
8. 인스턴스 시작 후 상태 검사 통과와 현재 공인 IPv4를 확인합니다. SSH 호스트 키 지문은 EC2 콘솔의 시스템 로그 등 신뢰할 수 있는 경로와 비교하고 로컬 known_hosts에 등록합니다. 자동 배포는 `StrictHostKeyChecking=yes`라 미확인 키를 자동 수락하지 않습니다.

T3의 기본 Unlimited와 Standard 차이: [AWS 문서](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/burstable-performance-instances-how-to.html). 크레딧이 없으면 성능이 제한되므로 부하 실험 때 `CPUCreditBalance`와 켠 뒤 경과 시간을 기록합니다. 새 인스턴스는 크레딧이 쌓인 뒤 실험합니다.

## 3. DuckDNS와 최초 접속

1. 사용자가 DuckDNS 사이트에 직접 로그인해 서브도메인을 하나 만듭니다.
2. 최초에는 사이트에서 그 도메인의 IPv4를 EC2 콘솔의 현재 공인 IP로 지정합니다. 토큰을 터미널 명령에 붙이지 않습니다.
3. 로컬에서 `dig +short CHANGE_ME.duckdns.org A` 또는 `nslookup`으로 현재 IP가 나오는지 확인합니다.
4. 서버에 최초 접속합니다. 아래는 **사용자가 실행하는 예시**입니다. 개인 키 파일 경로만 인자로 들어갑니다.

```bash
ssh -i /저장소밖/키파일.pem ubuntu@CHANGE_ME.duckdns.org
```

키 페어에 암호를 걸었다면 로컬 ssh-agent에 먼저 등록합니다. 로컬 스크립트는 BatchMode를 사용합니다. 이후 중지/시작으로 IP가 바뀌면 DNS 갱신·전파를 기다립니다. 자동 갱신이 실패하면 EC2 콘솔의 시스템 로그·상태와 DuckDNS 사이트를 확인해 주소를 복구한 뒤 **도메인으로** 접속합니다. deploy·rollback·pull-backup·probe는 DuckDNS 도메인만 받으며 공인 IP를 인자로 쓰지 않습니다.

Stop/Start로 공인 IP는 바뀌어도 디스크에 저장된 SSH 호스트 키는 보통 그대로입니다. 로컬 known_hosts의 옛 IP 매핑이나 재할당된 IP의 이전 키 때문에 경고가 날 수 있습니다. 콘솔에서 인스턴스 ID·현재 IP를 확인하고 `dig` 결과와 비교한 뒤, 신뢰할 수 있는 EC2 시스템 로그의 SSH 키 지문을 대조합니다. 키 자체가 달라졌다면 재설치·다른 서버 접속 여부를 확인합니다. 확인 없이 StrictHostKeyChecking을 끄거나 known_hosts 전체를 지우지 않습니다. 같은 서버·같은 키임을 확인한 경우에만 해당 낡은 IP/호스트 항목을 정리합니다.

집·회사·모바일망 이동으로 **내 공인 IP**가 바뀌면 보안 그룹의 22번 출처를 콘솔의 ‘내 IP’ `/32`로 바꿉니다. 이 문제를 서버의 IP 변경과 혼동하거나 22번을 전체 공개하지 않습니다.

## 4. 서버 파일 배치와 bootstrap

로컬 저장소에서 `deploy/server`를 서버의 임시 작업 폴더로 복사합니다. 업로드한 원본 폴더는 bootstrap이 참조하는 설정 파일을 모두 포함해야 합니다.

```bash
scp -r -i /저장소밖/키파일.pem deploy/server ubuntu@CHANGE_ME.duckdns.org:~/workflow-setup
```

서버에서:

```bash
sudo bash ~/workflow-setup/bootstrap.sh --domain CHANGE_ME.duckdns.org
sudo /usr/local/lib/workflow/setup-secrets.sh
sudo bash ~/workflow-setup/bootstrap.sh --domain CHANGE_ME.duckdns.org
```

- **앱 운영 중 bootstrap 재실행은 짧은 중단을 일으키므로 점검 시간에만 합니다.** 패키지 설치·설정 배치·서비스 재시작을 배포/백업과 같은 maintenance 잠금으로 직렬화합니다. SSH 설정 검사·reload를 DuckDNS보다 먼저 마치며, DNS 실패 시 bootstrap은 실패하고 journalctl·reset-failed 안내를 출력합니다. 타이머 시작과 DNS 대기 전에 잠금을 풀어 밀린 백업이 진행될 수 있게 합니다.
- 첫 실행: Java 17·MariaDB 10.11·Redis·Nginx·certbot·sysstat 등 설치, workflow 시스템 사용자(nologin), 폴더·설정·유닛·UTC·2 GiB swap·swappiness 10 배치. 인증서가 없으므로 HTTP ACME 설정을 사용합니다.
- 숨김 입력: 관리자 아이디·이름·이메일·비밀번호 및 확인, 선택 Discord 웹후크, DuckDNS 서브도메인(접미사 제외)·토큰. 서브도메인도 숨김 입력이며 bootstrap의 도메인과 다르면 저장하지 않습니다. 비밀번호는 기존 정책(8~20자, 대문자·소문자·숫자·기호)을 만족해야 합니다. DB 비밀번호 두 개와 JWT 비밀값은 서버 `openssl rand`로 생성하며 화면에 출력하지 않습니다.
- `/etc/workflow/env`, `/etc/workflow/duckdns`는 root:root 600, 부모 폴더는 700. 재실행 시 기존 DB/JWT 비밀값·DuckDNS 토큰을 유지합니다. 자동 회전 도구는 아닙니다.
- 두 번째 bootstrap: root unix_socket으로 계정 권한을 맞추고 백업·배치 확인 타이머 및 DuckDNS 갱신 서비스를 시작합니다. `workflow_migrator@127.0.0.1`은 mini_erp.* 전체 권한(GRANT OPTION 없음), `workflow_app@127.0.0.1`은 SELECT/INSERT/UPDATE/DELETE만 보유합니다. 명세의 @localhost는 skip-name-resolve + JDBC TCP 접속에서 1045가 재현되어 실제 접속 IP로 명시했습니다. root는 Unix socket을 유지합니다([재현 기록](../docs/troubleshooting/M5-05-loopback-db-accounts.md)).
- bootstrap은 **업로드 원본 폴더**에서 재실행합니다. `/usr/local/lib/workflow/bootstrap.sh`만 따로 실행하면 옆의 설정 템플릿이 없으므로 안 됩니다.
- `--no-systemd`는 컨테이너 설치 검증용입니다. 실제 서버에는 사용하지 않습니다.

서버에서 값이 아닌 권한·상태만 확인합니다.

```bash
sudo stat -c '%U:%G %a %n' /etc/workflow/env /etc/workflow/duckdns
id workflow
sudo sshd -T | grep -E '^(permitrootlogin|passwordauthentication) '
sudo systemctl status duckdns-update.service --no-pager
sudo journalctl -u duckdns-update.service -n 10 --no-pager
swapon --show
sysctl vm.swappiness
```

DuckDNS 서비스는 network-online.target 뒤에 IMDSv2로 IPv4를 읽고 HTTPS 갱신의 OK를 확인합니다. 그 뒤 OS의 IPv4 도메인 조회 결과가 현재 공인 IP 하나와 일치하는지 확인해 두 주소와 MATCH/PENDING을 로그에 남깁니다. 네트워크/전파 지연은 2·4·8·16·20초(이후 최대 20초) 간격으로 재시도하며 전체 180초 안에 같아지지 않으면 실패합니다(systemd 강제 제한 195초). OnFailure가 기존 실패 알림에 연결됩니다. 웹후크 미설정 시 외부 알림은 없고 서비스 실패와 journal로 확인합니다. URL·토큰은 프로세스 인자·화면·오류 로그에 출력하지 않습니다. 재시도하려면 원인을 고친 뒤 `sudo systemctl reset-failed duckdns-update; sudo systemctl restart duckdns-update`를 실행합니다. [DuckDNS API](https://www.duckdns.org/spec.jsp), [IMDSv2](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/configuring-instance-metadata-service.html).

## 5. HTTPS 발급

DNS가 현재 IP를 가리키고 보안 그룹 80이 열렸는지 확인한 뒤 서버에서 실행합니다.

```bash
sudo certbot certonly --webroot -w /var/www/letsencrypt -d CHANGE_ME.duckdns.org
sudo bash ~/workflow-setup/bootstrap.sh --domain CHANGE_ME.duckdns.org
sudo nginx -t
sudo systemctl status certbot.timer --no-pager
sudo certbot renew --dry-run
```

연락처와 약관은 certbot의 대화형 안내로 입력합니다. 인증서가 있으면 bootstrap이 HTTPS 설정으로 전환합니다. `certbot.timer`가 갱신하고 성공 후 hook이 설정 검사·Nginx reload를 합니다. 인증서는 도메인에 연결되므로 공인 IP가 바뀌어도 다시 발급할 필요가 없습니다. 최초 앱 배포 전 `/healthz`와 화면은 503이 정상입니다.

## 6. 첫 배포와 관리자 로그인

로컬에서 리뷰한 깨끗한 커밋을 준비합니다. 미추적 `.claude/`만 작업 트리 검사에서 제외하고, 다른 미추적·수정 파일은 배포를 막습니다.

```bash
export JAVA_HOME=/Users/kimmini/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home
export SSH_KEY=/저장소밖/키파일.pem
bash deploy/bin/deploy.sh CHANGE_ME.duckdns.org
```

흐름: 로컬 Maven clean package(테스트 제외) → npm ci/build → `<UTC시각>-<커밋7자리>` 아카이브 → 서버 업로드 → 스키마 버전 증가 시 사전 백업 → root 소유 릴리스 배치 → 백엔드 current 링크 교체·재시작 → 스키마 버전 증가 시 최대 900초, 일반 배포는 최대 90초 HTTPS `/healthz` 확인 → 프론트 current 교체. 로컬 로그에는 빌드·전송·활성화 시간과 릴리스 이름이 남습니다. 최근 3개 릴리스를 유지합니다.

서버에서:

```bash
sudo systemctl status workflow --no-pager
curl -sS http://127.0.0.1:8081/actuator/health
sudo ss -ltnp
sudo systemctl list-timers workflow-backup.timer batch-check.timer sysstat-collect.timer certbot.timer
```

브라우저에서 HTTPS로 최초 관리자 로그인 후 비밀번호를 변경합니다. 관리자 생성·변경 성공을 확인하고 서버에서 초기 비밀번호를 지웁니다.

```bash
sudo /usr/local/lib/workflow/clear-initial-password.sh
sudo systemctl restart workflow
```

파일에서만 지우면 기존 프로세스 환경에는 남으므로 재시작까지 합니다. 이미 관리자가 있으면 계정은 다시 생성하지 않습니다. 메일은 기본 꺼짐이고 초대 응답은 `invitationSent=false`입니다.

### OOM 힙 덤프 관리

새 비밀 설정의 HeapDumpPath는 **/var/lib/workflow/heap.hprof**입니다. 첫 OOM 덤프 한 개만 남기며 JVM은 기존 파일을 덮어쓰지 않습니다. 이 파일이 있으면 다음 OOM에서는 새 덤프를 만들지 못합니다. 기존 /etc/workflow/env는 재실행해도 자동 변경하지 않으므로, 예전 설정을 쓰는 운영자는 점검 시간에 JAVA_OPTS의 HeapDumpPath만 이 파일 경로로 바꾸고 앱을 재시작해야 합니다. 다른 비밀값은 보존합니다.

내용을 출력하지 않고 서버에서 크기·소유자·권한만 확인합니다. **힙 덤프에는 비밀값·개인정보가 포함될 수 있으므로 로컬로 옮기거나 공유하지 않습니다.** 서버에서 필요한 확인을 마친 뒤 파일 하나만 삭제합니다. 실행 예시는 운영자가 승인된 점검 시간에 수행합니다.

```bash
sudo stat -c '%s bytes %U:%G %a %n' /var/lib/workflow/heap.hprof
sudo rm -- /var/lib/workflow/heap.hprof
```

## 7. 배포 실패와 되돌리기

```bash
# 로컬에서
bash deploy/bin/rollback.sh CHANGE_ME.duckdns.org
```

- 배포 health 실패: 프론트 링크는 그대로 유지하고 이전 백엔드 링크로 복귀·재시작·health 확인, 종료 코드 1. 처음 배포라 이전 버전이 없으면 앱을 중지하고 점검 페이지를 유지합니다.
- 수동 rollback: 직전 백엔드가 정상인지 확인한 뒤 같은 릴리스의 프론트 링크로 바꿉니다. 실패하면 시작 전 백엔드 링크를 복구합니다.
- 실패 로그 최근 50줄은 알려진 비밀값·Bearer·설정/재설정 토큰을 가린 뒤 보여 줍니다. 비밀값 파일을 읽을 수 없으면 로그 출력을 생략합니다.
- health 대기는 신규 스키마 버전 배포 900초, 일반 배포·자동/수동 rollback 90초입니다. 시작 시점보다 NRestarts가 증가하거나 ActiveState=failed면 제한을 채우지 않고 실패합니다. HEALTH_WAIT limit_s=… migration=YES|NO result=… 로그를 확인합니다.
- 큰 테이블의 DDL은 **배포 전에 최신 덤프로 로컬 복구를 검증하고 같은 DDL 시간을 측정**합니다. restore-local.sh는 검증 뒤 컨테이너를 지우므로, DDL 측정은 동일 덤프·mariadb:10.11의 별도 일회성 로컬 복구 컨테이너에서 진행합니다(포트 공개 없이, 운영 DB 연결 금지). 덤프 import → 같은 DDL을 클라이언트 /usr/bin/time으로 실행 → 결과와 데이터 규모 기록 → 해당 시험 컨테이너/볼륨 제거 순서입니다. 개발 머신 시간과 서버 시간은 다르므로 여유를 둡니다. **900초를 넘을 것 같으면 배포 스크립트로 강행하지 말고 점검 시간을 잡아 별도로 진행합니다.**
- **jar를 되돌려도 스키마는 돌아가지 않습니다.** 운영 V 파일은 수정하지 않고 V7부터 추가합니다. 구버전 코드와 호환되지 않는 변경은 별도 복구 계획이 필요합니다.
- 배포·rollback·백업·서버 복구 리허설은 같은 flock을 사용합니다. 03:30~03:39(KST)에는 신규 배포를 거부합니다. 직접 실행한 Flyway·수동 DDL은 이 잠금을 우회하므로 수행하지 않습니다.

## 8. 백업과 복구 리허설

서버 03:30 KST 전체 덤프, `Persistent=true` 타이머, **최근 7세트 보관**. 파일 이름의 UTC 시각 순으로 덤프 7개와 짝 파일(.sha256/.version/.counts.tsv)을 유지하며 배포 전 백업도 같은 세트로 셉니다. 중지 기간이 길어도 경과 일수만으로 이전 세대를 지우지 않습니다. 03:30에 꺼져 있었다면 켜서 타이머가 활성화될 때 놓친 실행 한 번이 즉시 예약됩니다. 빠진 날짜마다 백업을 재현하는 것은 아닙니다. **이 백업이 끝난 뒤에 측정합니다.** 압축 임시 파일을 성공 시에만 공개하고 sha256과 Flyway 버전을 저장합니다. root socket 접속이며 비밀번호 인자가 없습니다.

쓰기 없는 점검 시간에 서버에서:

```bash
sudo /usr/local/lib/workflow/backup.sh
sudo /usr/local/lib/workflow/restore-rehearsal.sh
```

복구는 **새 mini_erp_restore**로만 합니다. 이미 같은 이름의 DB가 있으면 거부합니다. 테이블별 COUNT와 Flyway 버전을 원본과 비교하고 counts 파일을 백업 옆에 저장한 뒤 임시 DB를 지웁니다. 쓰기가 진행되면 덤프 시점과 비교 시점이 달라 실패할 수 있습니다. 사용자 데이터가 있는 운영 DB를 삭제하지 않습니다.

로컬에서 내려받고 실제 Docker 복구를 합니다.

```bash
bash deploy/bin/pull-backup.sh CHANGE_ME.duckdns.org
bash deploy/bin/restore-local.sh "$HOME/workflow-backups/CHANGE_ME.duckdns.org/CHANGE_ME.sql.gz"
```

저장소 밖 `~/workflow-backups/<서버주소>/`에 root 백업을 SSH로 스트리밍해서 보관합니다. 덤프·sha256·version·counts.tsv 네 파일을 함께 보존합니다. 로컬 복구 컨테이너는 `--network none`, 포트 공개 없음, 실행 뒤 컨테이너와 그 익명 데이터 볼륨을 함께 제거합니다. counts 파일은 서버 리허설 성공 시 생깁니다.

서버를 잃었을 때 운영 복구는 운영자 승인 하에 새 서버에 bootstrap·비밀값·빈 mini_erp를 준비하고, 검증한 덤프를 root socket으로 가져온 뒤 그 스키마와 호환되는 릴리스를 배포합니다. 임의로 운영 DB를 덮어쓰는 자동 명령은 제공하지 않습니다. 마지막으로 DuckDNS가 새 서버 IP를 가리키는지 확인합니다.

한계: 서버 백업만으로는 디스크 장애에 대비할 수 없습니다. 데이터 투입 전후·시연 뒤·종료 전에 로컬로 내려받습니다. 기본 하루 한 번이므로 최대 하루치를 잃을 수 있습니다. 이 목표가 부족하면 외부 binlog/관리형 DB 등 후속 설계를 합니다.

## 9. 관측과 M5 서버 측정

실험 전 체크리스트:

1. 서버를 켠 뒤 **2~3시간** 지났는지 확인합니다. T3는 시작 크레딧이 없으며 중지 후 7일이 지나면 남은 크레딧이 사라지는 조건을 기준으로 준비합니다. 실제 `CPUCreditBalance`는 AWS 콘솔에서 읽어 기록합니다. 2~3시간이 지났다는 사실만으로 충분한 크레딧을 보장하지 않습니다.
2. 놓친 백업 실행이 끝났는지 `systemctl status workflow-backup.service`와 아래 `backup_in_progress`를 함께 봅니다. `activating`(잠금 대기 포함)이거나 YES면 기다립니다.
3. 아래 출력을 저장소 밖에 저장합니다. 서버·MariaDB 가동 시간, 버퍼 풀 적재량/물리 읽기/요청, 메모리·swap, 마지막 완료 백업, 진행 중 백업, 릴리스가 나옵니다.
4. 같은 대상·같은 요청으로 **예열 1회** 후 같은 출력을 한 번 더 저장하고 측정합니다. 개별 측정 명세가 예열 횟수를 더 요구하면 그 조건도 따릅니다. 예열 전/후 조건을 섞어 비교하지 않습니다.

```bash
sudo /usr/local/lib/workflow/measure-context.sh | tee "$HOME/workflow-context-before.txt"
# 위젯/측정 대상에 같은 요청으로 예열 1회, 토큰은 기존 0600 헤더 파일로만 전달
sudo /usr/local/lib/workflow/measure-context.sh | tee "$HOME/workflow-context-after.txt"
```

측정 기록은 [M5 측정 양식](../docs/troubleshooting/M5-09-measurement-template.md)을 복사해 씁니다. CPUCreditBalance는 서버 스크립트가 AWS를 호출해서 읽지 않으므로 콘솔 값을 직접 적습니다. 일반 컨테이너에서는 `/proc/uptime`이 호스트 커널 가동 시간이고 systemd 항목은 확인 못 함으로 표시됩니다.

```bash
# 서버
sudo /usr/local/lib/workflow/slow-endpoints.sh /var/log/nginx/workflow-access.json
sudo mariadb-dumpslow /var/log/mysql/workflow/slow.log
sar -u
sar -r
sar -d
sudo journalctl -u workflow --since today --no-pager
sudo mariadb mini_erp -e 'SELECT job_name,target_date,status,processed_count,skipped_count,started_at,finished_at FROM batch_runs ORDER BY id DESC LIMIT 10'
```

sysstat는 1분, journald는 persistent·300M 한도입니다. Nginx 접근 로그는 요청 본문·쿼리 문자열을 기록하지 않습니다. 숫자 경로를 `{id}`로 묶어 횟수·nearest-rank p50/p95/최대를 출력합니다. Nginx 로그와 느린 SQL 로그는 매일 회전해 7개를 유지합니다. 느린 SQL에는 업무 정보가 포함될 수 있으므로 원문을 포트폴리오에 올리지 않습니다.

배포 중 끊김 측정(9장 7번)은 로컬에서 실행합니다. 인증이 필요한 API는 저장소 밖 **0600 헤더 파일**에 Bearer 값을 숨김 입력으로 저장합니다. 토큰을 옵션으로 직접 넣지 않습니다.

```bash
python3 - <<'PY'
from pathlib import Path
import getpass,os
p=Path.home()/'.workflow-probe-headers'
fd=os.open(p,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
with os.fdopen(fd,'w') as f:f.write('Authorization: Bearer '+getpass.getpass('Token (hidden): ')+'\n')
PY
bash deploy/bin/probe.sh https://CHANGE_ME.duckdns.org/api/v1/home /tmp/workflow-probe-1.csv --seconds 180 --header-file "$HOME/.workflow-probe-headers"
```

별도 터미널에서 배포합니다. 0.2초마다 요청을 시작하고 CSV에 UTC 시각·HTTP 상태·응답 ms·curl 종료 코드를 기록합니다. 종료/Ctrl+C 후 비2xx·전송 실패 수, 가장 긴 연속 실패 구간을 출력합니다. 구간은 첫 실패 요청 시작부터 다음 성공 요청 시작까지이며, 끝까지 실패하면 마지막 응답 종료까지입니다. 0.2초 샘플링·클라이언트 스케줄링 오차가 있는 관측치이고 요청별 curl 제한은 2초입니다. 토큰 만료에 따른 401도 실패에 포함되므로 실행 전 성공 응답을 확인합니다. 헤더 파일은 측정 후 삭제합니다.

서버에서 다음을 3회 또는 명세 횟수대로 기록합니다. 이번 컨테이너 검증을 실제 EC2 결과로 대신하지 않습니다.

| 측정 | 남길 값 |
|---|---|
| 노출/HTTPS | 외부 3306·6379·8080·8081 차단, 허용 IP 외 22 차단, HTTP 리다이렉트, 문서/actuator 404 |
| 바인딩/보안 | ss 출력, 헤더 목록, 주요 화면 CSP 위반 0, 인증서 만료/갱신 dry-run |
| 앱 | 실제 app 계정 DDL 1142, 재시작 3회 시작 시간·UP 시간, 자동 rollback 시험 |
| 배포 | probe 3회 CSV·실패 수·최장 구간·화면 안내 |
| 자원 | 기동 직후/화면 순회 후 RSS·free·swap·GC·CPUCreditBalance |
| 데이터 | 백업 bytes·시간, 서버/로컬 복구 counts·버전·시간 |
| 배치/비밀값 | 다음 날 KST 00:10 결근(전날 대상)·09:00 마감 SUCCESS, 파일 권한·로그 비밀값 노출 0 |

각 기록에 UTC 측정 시각·릴리스·인스턴스 기동 후 경과·CPU 크레딧·예열 여부를 적습니다. 수치가 나빠도 JVM/풀 설정부터 바꾸지 말고 쿼리·CPU·메모리 근거를 먼저 모읍니다.

로컬 도구의 재현 검사는 `python3 deploy/checks/offline-tools.py`로 실행할 수 있습니다. DuckDNS/IMDS는 로컬 가짜 HTTP 서버로 OK·KO·무응답·지연을 검증합니다. 테스트 복사본에서 고정 endpoint·시간 제한·파일 경로만 바꾸며, 운영 스크립트에는 외부 endpoint 변경 옵션이 없습니다. DNS 판정은 가짜 getent, probe는 테스트 curl의 로컬 주소 고정으로 검증하며 외부 요청은 없습니다. Java 테스트 수에는 포함하지 않습니다.

## 10. 운영상의 선택과 한계

- 실제 값 없는 prod는 조기 실패합니다. management는 loopback 8081의 health만 노출하고 SMTP health는 제외합니다(메일 비활성 운영에서 SMTP로 앱 상태가 DOWN이 되지 않게 함).
- Redis는 loopback·protected-mode·64MB, 인증 없음입니다. 같은 서버의 다른 프로세스도 접속할 수 있다는 한계가 있습니다. 초대 링크/비밀번호 재설정 외 용도를 추가하지 않습니다.
- 페이지에는 health 확인용 내부 subrequest를 사용합니다. 정적 index가 앱 장애 중 정상으로 보이지 않도록 한 선택이며, 페이지 방문마다 health 요청 한 번의 비용이 있습니다. assets는 이 확인을 하지 않습니다.
- Nginx **사이트 error_log는 /dev/null**입니다. 해당 로그는 쿼리 토큰을 지울 수 없어 그대로 보관하는 대안을 버렸습니다. 요청별 상세 오류 대신 쿼리 없는 access JSON의 status/upstream_status/time을 사용하고, 설정·시작 오류는 nginx -t/journal로 확인합니다.
- 단일 신뢰 프록시이므로 외부에서 온 X-Forwarded-For를 그대로 잇지 않고 Nginx의 실제 remote_addr로 덮어씁니다.
- 느린 로그는 `/var/log/mysql/workflow/slow.log`로 분리합니다. Ubuntu 패키지의 `/var/log/mysql/*.log` 회전 규칙과 중복되지 않습니다.
- DuckDNS와 IMDS 장애 시 DNS가 낡은 IP를 가리킬 수 있습니다. 현재 IP는 콘솔에서 확인합니다. DuckDNS 토큰은 계정의 다른 도메인도 변경할 수 있으므로 root 전용 파일을 유지합니다.
- systemd·실제 swap·SSH 정책 적용·타이머 발화·인증서 갱신·부팅 DNS 갱신·실제 배포/rollback·EC2 메모리 예산은 실제 서버에서 검증해야 합니다.

## 11. 잠깐 끄고 다시 켜기

이번 운영은 실험할 때만 켜는 데모입니다. **잠깐 끄기는 Stop만** 사용합니다. 작업 저장·쓰기 중단 후 백업 실행이 끝났는지 확인하고, 서버의 `sudo systemctl stop workflow` 후 AWS 콘솔에서 Stop합니다. 디스크·데이터는 유지됩니다. 실행 중 백업이나 측정을 끊지 않습니다.

다시 Start한 뒤 확인 순서:

1. **DuckDNS 갱신**: 콘솔의 현재 공인 IPv4와 도메인 조회가 같은지 확인합니다. 도메인으로 SSH 접속한 뒤 `systemctl status duckdns-update`와 journal의 `result=SUCCESS`, `dns=MATCH`를 봅니다.
2. **헬스**: `https://CHANGE_ME.duckdns.org/healthz`가 200/UP인지 확인합니다. 앱 기동 중 503이면 서비스 journal을 확인합니다. 서버 내부 health 진단만 loopback 주소를 씁니다.
3. **인증서**: HTTPS 접속과 인증서 만료일, certbot.timer 상태를 확인합니다. 오래 꺼 두어 만료됐다면 80번/DNS를 확인하고 certbot 갱신 뒤 Nginx 설정 검사·reload를 합니다.
4. **놓친 백업**: workflow-backup.timer의 LAST/NEXT와 service 상태를 확인합니다. Persistent=true로 재개 시 실행된 백업이 끝나고 `measure-context.sh`에 완료 백업 시각이 갱신됐는지 확인합니다. 데이터가 아직 없는 첫 설치는 첫 배포 후 백업을 확인합니다.
5. 아래 앱 배치 누락을 확인하고 필요한 수동 실행을 합니다. 이후 실험 전 체크리스트를 따릅니다.

### 앱 배치 누락과 기존 수동 실행 경로

앱의 Spring 스케줄러는 systemd의 Persistent 타이머와 달라 꺼진 시간의 실행을 자동 재생하지 않습니다. 수동 API도 로그인·해당 메뉴 권한·ALL 범위가 필요하며, 명시한 날짜의 작업 한 번을 요청합니다. 인증값을 명령 인자에 붙이지 않습니다.

| 배치 | 실제 코드의 한국 시각 | 기존 수동 실행 | 제한 |
|---|---|---|---|
| 결근 판정 | 매일 00:10, 전날 대상 | `POST /api/v1/attendance/rejudge`, 본문 `{"date":"YYYY-MM-DD"}` | ATTENDANCE_MONTHLY U + ALL. 오늘 이전만 가능. 누락 날짜별로 실행·응답의 batch_runs 확인 |
| 연차 부여 | **매일 00:30**. 연초 Y 부여와 입사일 기준 월별 부여를 같은 코드에서 검사 | `POST /api/v1/leave/admin/grant-runs`, 본문 `{"date":"YYYY-MM-DD"}` | LEAVE_ADMIN U + ALL. 오늘 이후 거부. 실행 날짜의 해만 계산, 옛 GRANT가 있는 해는 건너뜀. 결과는 GET /api/v1/leave/admin/grant-runs로 확인 |
| 마감 업무 알림 | 매일 09:00 | **없음, 데모 운영의 한계** | 다음 예정 시각의 현재 조건으로 실행. 꺼진 날의 알림은 자동 소급하지 않음 |

명세의 ‘연차 1/1’은 연간 부여 시점이며 현재 스케줄러 자체는 매일입니다. 기존 수동 경로만 문서화했고 새 배치 기능은 만들지 않았습니다. 연차의 현재 해 누락은 같은 해 실행으로 따라잡지만 지난 해 자동 소급은 하지 않습니다. 결근 재판정은 현재 휴직 상태로 대상을 판단하는 기존 한계가 있으므로 오래된 누락 날짜를 실행하기 전에 사원 상태도 확인합니다.

### 비용 기준

예산 가정은 계속 켰을 때 **약 $25/월**, 껐을 때 디스크만 **약 $3/월**입니다. 확정 청구액이 아니며 사용 시간·지역·스냅샷/트래픽 등과 계정 조건에 따라 달라집니다. 같은 AWS 계정의 다른 프로젝트와 크레딧을 나눠 쓰므로 이 프로젝트만의 잔액처럼 계산하지 않습니다. 사용량·잔여 크레딧은 콘솔에서 함께 확인합니다.

## 12. 최종 종료 체크리스트

**Terminate는 다음을 마친 뒤에만** 실행합니다.

1. 쓰기를 멈추고 최종 백업 → 서버 리허설 → 로컬 내려받기 → 로컬 Docker 복구 확인.
2. 로컬에 덤프·검증값·버전·행 수와 필요한 시연/측정 기록이 있는지 확인. 원문 데이터·비밀값은 포트폴리오에 올리지 않음.
3. 최종 종료할 때만 콘솔에서 Terminate. 남은 EBS 볼륨·스냅샷·기타 과금 리소스를 콘솔에서 확인. 단순 중지와 달리 루트 디스크가 삭제될 수 있음.
4. DuckDNS의 불필요한 도메인과 Discord 웹후크를 사용자 계정에서 정리하고 로컬 키·비밀값·백업 보관 계획을 결정.

재구축은 이 문서 2절부터 진행하고 검증한 백업을 복구합니다.
