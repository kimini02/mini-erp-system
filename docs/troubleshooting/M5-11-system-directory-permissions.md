# M5-11 시스템 디렉터리 권한 보존

## 증상
백업의 install -d가 기존 /run/lock의 sticky/world-write 권한을 제거한다. bootstrap도 기존 시스템·패키지 설정 폴더에 install의 기본 권한을 적용할 수 있다.

## 재현 방법
Ubuntu 24.04 + MariaDB 10.11.19를 별도 컨테이너로 만들고 시험 socket만 공유했다. /run/lock은 이미지의 실제 초기값 1777 root:root다. 이 Ubuntu 패키지는 service 시작을 막은 환경에서 /var/log/mysql을 만들지 않았으므로 **기존 폴더 fixture**를 mysql:adm 2775로 준비했다. 이를 패키지 기본 권한이라고 주장하지 않는다. 기존 backup.sh를 실제 실행하고 기존 bootstrap의 로그 폴더 구문을 실행했다.

## 원인
install -d는 디렉터리가 있어도 mode/owner를 적용한다. mkdir -p는 이미 있는 디렉터리를 바꾸지 않는다.

## 검토했지만 버린 방법과 이유
- 실행 뒤 원래 권한 복원: 그 사이 다른 프로세스에 영향을 주고 복원 실패 가능성이 있다.
- 시스템 디렉터리를 모두 755로 통일: 패키지/운영자가 정한 sticky·setgid·접근 정책을 손상한다.

## 선택한 방법
/run/lock·/var/log/mysql 및 공용 설정 디렉터리는 mkdir -p. 소유권·750은 /var/log/mysql/workflow 하위에만 적용한다.

다른 install -d 전수 확인 결과 `/etc/systemd/journald.conf.d`, `/etc/systemd/system`, `/etc/letsencrypt/renewal-hooks/deploy`, `/etc/ssh/sshd_config.d`, `/etc/systemd/system/sysstat-collect.timer.d`도 mkdir -p로 바꿨다. 나머지는 /opt/workflow/releases, /var/www/workflow/releases, /usr/local/lib/workflow, /etc/nginx/snippets/workflow, /etc/workflow, /var/backups/workflow, /var/lib/workflow 및 릴리스별 신규 디렉터리로 앱이 관리하는 경로다. /var/www/letsencrypt는 이 설치가 관리하는 ACME webroot라 지정 권한을 유지한다. 상위 /var·/etc 자체를 install 대상으로 지정한 곳은 없다.

## 전후 수치·결과

| 경로 | 수정 전 실행 | 수정 후 backup + bootstrap --no-systemd 2회 |
|---|---|---|
| /run/lock | 1777 root:root → 755 root:root | 1777 root:root → 1777 root:root |
| /var/log/mysql(기존 폴더 fixture) | 2775 mysql:adm → 2750 mysql:adm | 2775 mysql:adm → 2775 mysql:adm |

MariaDB의 1테이블/1행 실제 덤프 성공. nginx -t 성공. 로컬 개발 DB/Redis 접근 없음. 실행 원문은 최종 M5 리뷰 보고서에 모은다.

## 부작용·남은 위험
이미 잘못 바뀐 운영 권한을 추측해 복구하지 않는다. 이 수정은 이후 변경을 막는다. 실제 서버의 원래 권한은 운영자가 패키지 기준과 비교해야 한다.

## 커밋
이 문서·common.sh·bootstrap.sh의 독립 커밋. 최종 보고서에 해시를 기록한다.
