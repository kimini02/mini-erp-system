# M5 추가 구현·검증 결과

## 작업 기준

- 브랜치: `feat/m5-deploy`, 1차 완료 `8d638d3`에서 이어서 작업.
- 작업 트리: `/Users/kimmini/sk-final-deepfake/mini-erp-system-m5-deploy`.
- 읽은 기준: 운영 명세 `2027ebd`, 홈 프로필 숫자 `f9667c6`, 이번 사용자 추가 지시.
- 공유 원본 브랜치는 변경하지 않았으며 최종 읽기에서 `feat/m4-home-board` 확인.
- 병합·푸시·실제 서버·SSH·AWS API·실제 DuckDNS 호출 없음. 프론트·명세·V1~V6·.claude/·개발 DB/Redis 변경 없음. 새 라이브러리 없음.

## 추가 커밋

| 커밋 | 작업 | 근거 문서 |
|---|---|---|
| `ba49fe8` | 근태 결근 수·내 업무 전체 건수, 쿼리 수 유지 | [M5-06](M5-06-home-counters.md), [M4-12 추기](M4-12-home-and-growth.md) |
| `dedd428` | DuckDNS 재시도·DNS 일치 확인·토큰 보호·도메인 제한 | [M5-07](M5-07-dynamic-dns.md) |
| `493e46c` | 측정 상태·백업 감지·Stop/Start 문서·측정 양식 | [M5-08](M5-08-intermittent-operation.md), [M5-09 양식](M5-09-measurement-template.md) |

이 결과 보고서와 원문 출력의 줄 끝 공백 정리는 별도 문서 커밋이다. `git log -- docs/troubleshooting/M5-10-additional-results.md`로 식별한다.

## 백엔드 전체 검증

```bash
cd /Users/kimmini/sk-final-deepfake/mini-erp-system-m5-deploy/backend
JAVA_HOME=/Users/kimmini/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home ./mvnw -Duser.timezone=UTC test
```

```text
[INFO] Tests run: 230, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  09:56 min
[INFO] Finished at: 2026-10-28T05:42:55Z
```

Surefire XML 60개 합산도 동일. 이번 새 `M5HomeCountersTest` 3개는 먼저 실행해 **수정 전 3/3 실패 → 수정 후 3/3 성공**을 확인했다. 기존 `M4HomeTest` 5개는 전후 통과. 변경 후 관련 테스트 13개도 별도 통과했다. 전체 UTC 실행 후 백엔드 변경은 없으며 문서·셸 정리만 수행했다.

## 홈 쿼리 수와 응답

| 항목 | 이전 | 추가 후 |
|---|---|---|
| SELF 홈 HTTP SQL(인증·권한 포함) | 14 | 14 |
| 1천/1만/10만 업무 데이터 | 14/14/14 | 14/14/14 |
| 이번 달 내 결근 2일, 지난달 1일, 다른 사원 1일 | 필드 없음 | monthAbsentCount=2 |
| 내 담당 미완료·범위 안 7개 | items 5 | count=7, items 5 |
| 완료·다른 담당·범위 밖 | 기존 items에서 제외 | count에서도 제외 |
| 0건 | 새 필드 없음 | count=0, monthAbsentCount=0 |

근태 숫자는 오늘 조회·출근·퇴근·홈 today에 모두 반영했다. 한국 00:30/UTC 전날 경계를 테스트했다. 1·10·100배의 참고 중앙값 16.326/33.087/77.018ms는 개발 머신의 동시 부하가 있어 과거 시간과 개선율 비교에 사용하지 않는다. 동일 조건의 범위 판정과 SQL 수가 주된 근거다.

## 스크립트·컨테이너 검증

| 검증 | 결과 |
|---|---|
| bash -n | deploy .sh 전체 19개 통과 |
| 내장 Python/검증 도구 | heredoc 11개 및 Python 도구 compile 통과 |
| shellcheck | 미설치로 못 함. 설치하지 않음 |
| DuckDNS OK | exit 0, UPDATE 1회, DNS MATCH |
| DuckDNS KO | exit 1, UPDATE 8회, 제한 내 실패 |
| DuckDNS 응답 없음 | exit 1, UPDATE 4회, 제한 내 실패 |
| DNS 지연 후 일치 | exit 0, UPDATE 1회, 세 번째 DNS 조회에서 일치 |
| DNS 계속 불일치 | exit 1, UPDATE 1회, 제한 내 실패 |
| IMDS 지연 후 복구 | exit 0, UPDATE 1회 |
| 비밀값 노출 | 각 시나리오의 실제 프로세스 argv·stdout/stderr에서 시험 토큰/IMDS session 노출 0 |
| 도메인 검증 | remote 4/4, probe 거부 2/2. 외부 호출 0 |
| probe 로컬 HTTP | 10요청, 의도한 503 3개 정확히 집계, 최장 구간 0.604초 |
| 숨김 입력 | Ubuntu 가상 tty의 8개 입력 노출 0, root:root 600, 재실행 파일 변경 0 |
| measure-context | Ubuntu 24.04 + MariaDB 10.11.19 실제 socket 조회. 버퍼 풀·메모리·swap·릴리스 출력 |
| 백업 상태 | NO → YES → NO, 완료 백업 시각 갱신, 시험 테이블 3행 유지 |
| systemd | 일반 컨테이너로 실행 불가, 스크립트에 확인 못 함 출력. systemd-analyze도 미설치 |

DuckDNS 재현은 실제 loopback HTTP 서버를 쓰고 시험 복사본의 주소·파일·시간만 교체했다. 공개 DNS 대신 가짜 resolver를 사용했다. 시험 상한 3초, 운영 상한 180초/서비스 제한 195초. 원문 출력과 조건은 M5-07·08에 있다. 실제 서버의 지연·성능 측정이 아니다.

검증용 `workflow-m5-additional-ubuntu`, `workflow-m5-additional-db`와 전용 socket/익명 데이터 볼륨은 끝난 뒤 제거했다. 다른 컨테이너나 개발 DB·Redis는 변경하지 않았다.

## 기존 배치 수동 실행

| 기능 | 기존 API | 조건 |
|---|---|---|
| 결근 재판정 | POST /api/v1/attendance/rejudge | {date}, 오늘 이전, ATTENDANCE_MONTHLY U + ALL |
| 연차 부여 | POST /api/v1/leave/admin/grant-runs | {date}, 오늘 이하, LEAVE_ADMIN U + ALL |
| 마감 알림 | **없음, 데모 운영의 한계** | 내부 서비스만 있고 수동 API 없음 |

실제 스케줄은 결근 00:10, 마감 09:00, **연차 매일 00:30**(연간 1/1과 입사일 월별 규칙을 함께 판단). 꺼진 시간의 Spring 작업은 자동 재생하지 않는다. 누락 처리와 권한·날짜 제한, 현재 해만 부여하는 정책을 README에 적었으며 새 배치 기능은 만들지 않았다.

## 명세를 구체화한 부분·결정 필요

- 고정 주소를 없애고 모든 외부 운영 도구는 DuckDNS 도메인만 허용했다. 서버 내부 health/socket 진단은 기존 loopback을 유지한다.
- DNS 일치 기준은 OS IPv4 조회의 중복 제거 결과가 IMDSv2 IPv4 하나와 같은 것. 실패 상한은 180초, systemd 상한은 195초로 정했다.
- token URL은 curl -K 대신 Python 표준 HTTP 라이브러리의 메모리에만 만들었다. 예외 본문을 출력하지 않는다.
- measure-context에서 수동/배포 전 백업도 감지하도록 공통 backup_now에 전용 flock을 추가했다. 스키마 변경 없음.
- ‘연차 1/1’ 표기는 실제 코드의 매일 00:30 스케줄과 연간 부여 시점으로 구분해 설명했다.
- 월 경계의 전날 퇴근 응답도 프로필 숫자는 Clock의 **현재 월** 기준으로 맞췄다.
- 구현을 막는 결정 질문 없음. 비용은 사용자 예산 가정으로 적었으며 실제 요금·크레딧은 운영자 콘솔 확인 대상이다.

## 확인하지 못한 것·남은 위험

실제 EC2의 Stop/Start·IMDS·외부 DuckDNS/TLS·DNS 전파·SSH 키·보안 그룹·CPUCreditBalance·인증서와 systemd의 OnFailure/백업 Persistent 발화는 금지된 서버 작업이므로 실행하지 않았다. 실제 서버의 절차는 [운영 README](../../deploy/README.md)에 있다. shellcheck와 systemd-analyze는 미설치로 미실행이다. 이번에는 기본 JVM 전체를 추가 실행하지 않았으며 요청한 UTC JVM 전체를 실행했다.

메모리·uptime 출력은 Docker VM 커널 값이므로 EC2 측정으로 주장하지 않는다. window count의 실제 처리량은 담당 업무 수에 비례한다. 스크립트 상태 수집은 원자적 스냅샷이 아니며 백업 service가 대기 중이면 lock과 service 상태를 함께 확인한다. 토큰 패턴 검사는 이번 변경에서 AWS 키·개인 키·실제 웹후크 URL 0건이었지만 모든 비밀값을 자동 탐지한다는 보장은 아니다.

## 변경 파일

### ba49fe8

```text
backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java
backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceStore.java
backend/src/main/java/com/minierp/backend/domain/home/service/HomeQueries.java
backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java
backend/src/test/java/com/minierp/backend/integration/M5HomeCountersTest.java
docs/troubleshooting/M4-12-home-and-growth.md
docs/troubleshooting/M5-06-home-counters.md
```

### dedd428

```text
deploy/bin/common.sh
deploy/bin/probe.sh
deploy/checks/offline-tools.py
deploy/checks/duckdns-local.py
deploy/server/duckdns-update.service
deploy/server/duckdns-update.sh
deploy/server/setup-secrets.sh
docs/troubleshooting/M5-07-dynamic-dns.md
```

### 493e46c

```text
deploy/README.md
deploy/server/common.sh
deploy/server/measure-context.sh
docs/troubleshooting/M5-08-intermittent-operation.md
docs/troubleshooting/M5-09-measurement-template.md
```

### 최종 문서 커밋

```text
docs/troubleshooting/M5-08-intermittent-operation.md
docs/troubleshooting/M5-10-additional-results.md
```
