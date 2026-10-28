# M5-14 마이그레이션 배포의 health 대기·조기 실패

## 증상
긴 Flyway DDL이 90초를 넘으면 살아 있는 앱을 배포 실패로 보고 중간에 재시작할 수 있었다. MariaDB DDL은 jar rollback으로 되돌아가지 않는다.

## 재현 방법
offline-tools.py가 호출하는 health-wait.py에서 실제 common.sh와 release.sh의 버전 비교 구문을 시험 복사본으로 읽는다. systemctl·curl만 가짜 실행 파일이며 서비스나 네트워크를 호출하지 않는다. 운영 900/90초를 시험 복사본에서만 6/2초로 줄였다. UP은 네 번째 요청부터 반환한다. 기존 0271fef의 고정 대기는 동일 시험에서 2초로 줄여 비교한다.

## 원인
마이그레이션 시간과 일반 기동 시간을 같은 제한으로 취급했고 systemd가 재시작한 프로세스도 단순 health 결과만으로 판단했다.

## 검토했지만 버린 방법과 이유
- 모든 배포를 무제한 대기: 실제 장애를 늦게 발견한다.
- 모든 배포를 900초 대기: 일반 장애 판단까지 느려진다.
- Flyway 중단 뒤 자동 DDL rollback: MariaDB의 DDL을 원상 복구한다고 보장할 수 없다.

## 선택한 방법
health_wait는 제한 초와 migration 여부를 받는다. local_version > current_version이면 사전 백업 뒤 900초, 그 외 배포·자동/수동 rollback·rollback 실패 후 복구는 90초다.

대기 시작의 NRestarts를 기록하고 매번 ActiveState와 함께 확인한다. NRestarts 증가 또는 failed면 즉시 실패한다. UP을 받았어도 systemd를 다시 확인한다. 로그는 HEALTH_WAIT limit_s=… migration=YES|NO result=UP|TIMEOUT|APP_FAILED|STATUS_UNAVAILABLE elapsed_s=… 형태다. 각 curl도 남은 제한 이내로 제한한다.

## 전후 수치·결과
기존 구조:

```text
HEALTH_BEFORE scenario=LATE_UP exit=1 elapsed_s=2.568 fixed_limit_s=2 systemd_checks=0
HEALTH_BEFORE scenario=TIMEOUT exit=1 elapsed_s=1.307 fixed_limit_s=2 systemd_checks=0
HEALTH_BEFORE scenario=RESTART exit=1 elapsed_s=2.341 fixed_limit_s=2 systemd_checks=0
HEALTH_BEFORE scenario=FAILED exit=1 elapsed_s=2.313 fixed_limit_s=2 systemd_checks=0
```

수정 후:

```text
HEALTH_FIXTURE scenario=LATE_UP exit=0 elapsed_s=4.199 external_calls=0
HEALTH_WAIT limit_s=6 migration=YES result=UP elapsed_s=5
HEALTH_FIXTURE scenario=TIMEOUT exit=1 elapsed_s=2.530 external_calls=0
HEALTH_WAIT limit_s=2 migration=NO result=TIMEOUT elapsed_s=2
HEALTH_FIXTURE scenario=RESTART exit=1 elapsed_s=1.511 external_calls=0
HEALTH_WAIT limit_s=6 migration=YES result=APP_FAILED elapsed_s=2
HEALTH_FIXTURE scenario=FAILED exit=1 elapsed_s=1.503 external_calls=0
HEALTH_WAIT limit_s=6 migration=YES result=APP_FAILED elapsed_s=1
```

지연 UP은 기존 제한에서 실패 → migration 한도에서 성공. 일반 배포 초과는 실패 유지. 재시작/failed는 새 제한 6초 전에 조기 실패한다. 로그의 elapsed_s는 Bash 정수 초, fixture의 elapsed_s는 단조 시계 실제 경과이며 1초 안팎 차이가 날 수 있다. 실제 EC2·DDL 시간을 대신하는 성능 수치가 아니다.

## 부작용·남은 위험
이번 사용자 지시가 명세의 기존 90초보다 우선한다. 900초를 넘어도 되는 자동 배포를 의미하지 않는다. 큰 DDL은 최신 덤프를 로컬 MariaDB 10.11에 복구해 먼저 같은 DDL 시간을 재고 900초를 넘을 것으로 예상하면 별도 점검 작업으로 계획한다. 일반 rollback은 스키마를 되돌리지 않는다. 실제 systemd/배포는 미실행이며 README 7절에 이 절차를 적었다.

## 커밋
common.sh·release.sh·offline 검증과 이 문서의 독립 커밋. 최종 보고서에 해시를 기록한다.
