# M5-08 필요할 때만 켜는 운영과 측정 조건

## 증상
부팅 직후의 빈 버퍼 풀·부족한 CPU 크레딧·밀린 백업을 고려하지 않으면 같은 요청의 시간 차이를 코드 개선 효과로 오해할 수 있다. 앱 스케줄러는 꺼져 있던 날짜를 자동 재생하지 않는다.

## 재현 방법
별도 mariadb:10.11(실제 10.11.19) 컨테이너를 --network none으로 만들고 포트를 공개하지 않았다. Ubuntu 24.04에 mariadb-client·procps·util-linux 등을 설치해 시험용 소켓 볼륨만 공유했다. 개발 DB/Redis는 접근하지 않았다.

시험 DB mini_erp의 1테이블/3행과 가짜 current 릴리스를 준비하고 measure-context.sh를 백업 전·중·후에 실행했다. 진행 중 측정은 시험용 mariadb-dump 래퍼가 8초 멈춘 뒤 실제 dump를 실행해 순서를 보장했다. 운영 스크립트에는 이 지연이 없다.

## 원인
SQL 수가 같아도 데이터 캐시·물리 읽기·메모리·동시 백업·T3 크레딧이 경과 시간에 영향을 준다. systemd Persistent 백업과 Spring 스케줄러의 누락 처리도 다르다.

## 검토했지만 버린 방법과 이유
- 첫 요청 시간만 비교: 콜드/웜 캐시와 CPU 크레딧 조건이 섞인다.
- 스크립트가 AWS API로 크레딧 조회: 실제 AWS 호출 금지와 키 관리 범위를 지킨다. 콘솔 값을 운영자가 기록한다.
- service 상태만으로 백업 판단: 수동·배포 전 백업을 놓친다. backup_now에 전용 flock을 두어 모두 감지한다.
- 공용 maintenance 잠금만 확인: 배포/복구도 같은 잠금이므로 백업으로 오판한다.
- 새 소급 배치/API 추가: 이번 범위 밖이다. 기존 수동 API와 없는 경로를 문서로 구분한다.

## 선택한 방법
measure-context는 비밀 env를 읽지 않는 상태 수집 도구다. 서버 uptime, MariaDB Uptime/버퍼 풀 4항목, free -m, swap, 마지막 완료 백업, 전용 잠금과 systemd 상태, current 릴리스를 출력한다. SQL은 root socket의 SHOW GLOBAL STATUS만 사용한다. 메타데이터/외부 네트워크 호출은 없다.

backup_now의 전용 잠금은 기존 maintenance 잠금 뒤에 잡고 서브셸 종료 시 풀린다. 수동·배포 전·타이머 백업 모두 같은 코드를 쓰며 dump 실패 시에도 잠금이 풀린다. 마지막 백업은 dump와 sha256·version sidecar가 갖춰진 것만 사용한다.

README는 Stop→Start 후 DuckDNS→health→인증서→밀린 백업 확인 순서, 2~3시간 대기/CPUCreditBalance/예열 전후 기록, 최종 Terminate 전 로컬 복구까지 포함한다. 비용은 사용자 예산 가정($25/$3)으로 표시하고 같은 계정의 다른 프로젝트와 크레딧을 공유한다고 적었다.

## 전후 수치·결과

| 항목 | 전 | 후 |
|---|---|---|
| 측정 전 상태 스크립트 | 없음 | uptime·DB·메모리·백업·릴리스 출력 |
| 백업 진행 판별 | service만으로 수동 실행 구분 불가 | NO → YES → NO |
| 마지막 완료 백업 | NONE | 2026-10-28T05:39:24Z |
| 시험 덤프 | 없음 | 776 bytes, 1테이블/3행 |
| dump 시간 | 해당 없음 | 8초(시험 지연 포함, 성능값 아님) |
| systemd 실제 상태 | 실행 환경 없음 | 확인 못 함 명시 |

출력 원문(백업 전):

```text
measured_at_utc=2026-10-28T05:39:15Z
server_uptime_seconds=219003
server_uptime_hours=60.83
[mariadb_global_status]
Innodb_buffer_pool_pages_data	288
Innodb_buffer_pool_pages_total	8112
Innodb_buffer_pool_read_requests	995
Innodb_buffer_pool_reads	149
Uptime	258
[free_mib]
               total        used        free      shared  buff/cache   available
Mem:            3919        1682         248          22        2193        2236
Swap:           1023           0        1023
[swap_bytes]
NAME          TYPE       SIZE USED PRIO UUID LABEL
/var/lib/swap file 1073737728    0   -2
SwapTotal:       1048572 kB
SwapFree:        1048572 kB
last_backup_utc=NONE
backup_in_progress=NO (no backup lock yet)
backup_service_state=확인 못 함 (systemd unavailable)
current_release=CHANGE_ME
```

백업 중/후 출력 원문:

```text
measured_at_utc=2026-10-28T05:39:16Z
server_uptime_seconds=219004
server_uptime_hours=60.83
[mariadb_global_status]
Innodb_buffer_pool_pages_data	288
Innodb_buffer_pool_pages_total	8112
Innodb_buffer_pool_read_requests	995
Innodb_buffer_pool_reads	149
Uptime	259
[free_mib]
               total        used        free      shared  buff/cache   available
Mem:            3919        1681         249          22        2193        2237
Swap:           1023           0        1023
[swap_bytes]
NAME          TYPE       SIZE USED PRIO UUID LABEL
/var/lib/swap file 1073737728    0   -2
SwapTotal:       1048572 kB
SwapFree:        1048572 kB
last_backup_utc=NONE
backup_in_progress=YES
backup_service_state=확인 못 함 (systemd unavailable)
current_release=CHANGE_ME
BACKUP file=20261028T053916.025543461Z.sql.gz bytes=776 elapsed_s=8
measured_at_utc=2026-10-28T05:39:24Z
server_uptime_seconds=219012
server_uptime_hours=60.84
[mariadb_global_status]
Innodb_buffer_pool_pages_data	288
Innodb_buffer_pool_pages_total	8112
Innodb_buffer_pool_read_requests	1284
Innodb_buffer_pool_reads	149
Uptime	267
[free_mib]
               total        used        free      shared  buff/cache   available
Mem:            3919        1684         247          22        2193        2235
Swap:           1023           0        1023
[swap_bytes]
NAME          TYPE       SIZE USED PRIO UUID LABEL
/var/lib/swap file 1073737728    0   -2
SwapTotal:       1048572 kB
SwapFree:        1048572 kB
last_backup_file=20261028T053916.025543461Z.sql.gz
last_backup_utc=2026-10-28T05:39:24Z
backup_in_progress=NO
backup_service_state=확인 못 함 (systemd unavailable)
current_release=CHANGE_ME
```

위 메모리·swap·server uptime은 Docker VM 커널 값이다. EC2 t3.small 결과로 사용하지 않는다. MariaDB Uptime은 이 검증 DB 프로세스 값이다.

## 기존 수동 배치 확인

| 기능 | 기존 경로 | 권한/날짜 |
|---|---|---|
| 결근 | POST /api/v1/attendance/rejudge | ATTENDANCE_MONTHLY U + ALL, {date}, 오늘 이전 |
| 연차 | POST /api/v1/leave/admin/grant-runs | LEAVE_ADMIN U + ALL, {date}, 오늘 이하 |
| 마감 알림 | 없음, 데모 운영의 한계 | 내부 runToday만 있고 공개 수동 API 없음 |

코드: AttendanceScheduler는 매일 00:10에 전날, TaskOverdueScheduler는 매일 09:00. LeaveGrantScheduler는 **매일 00:30**이며 연간 1/1·입사일 월별 부여를 같은 규칙으로 계산한다. 따라서 사용자 지시의 ‘연차 1/1’만 쓰지 않고 실제 실행 주기를 명시했다. 연차는 실행 날짜의 해만 계산하고 옛 GRANT가 있는 해는 건너뛴다. Spring 스케줄러의 기동 시 밀린 실행 기능은 없다. 새 배치 기능을 추가하지 않았다.

## 부작용·남은 위험
서로 다른 시점에 상태를 읽는 진단 출력이라 원자적 스냅샷은 아니다. 백업 service가 잠금 대기 중이면 dedicated lock은 NO일 수 있어 service의 activating도 함께 확인한다. systemd 없는 컨테이너에서는 service 상태를 확인 못 함으로 표시한다. 실제 부팅·CPUCreditBalance·Persistent 타이머·Stop/Start·Terminate는 금지된 서버 작업이므로 미실행이다.

## 커밋
measure-context·backup_now 전용 잠금·운영 README·측정 양식과 이 문서의 독립 커밋. 최종 해시는 M5 추가 결과 보고서에 기록한다.
