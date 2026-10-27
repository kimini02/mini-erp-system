# M4-09 — 커밋 전 위험 구간과 프로젝트 잠금 대기 관측

## 증상
1차 수정 후 실험은 요청 시작만 맞춰 실제 커밋 전 경합이 발생했음을 직접 증명하지 못했다. 10/10 무결성 성공만으로 잠금 대기 경로까지 확인했다고 말할 수 없다.

## 재현 조건
`M4DeterministicLockTest`의 완료 이동·같은 열 재정렬 2개 테스트. MariaDB 10.11 Testcontainers, 별도 스레드·HTTP 요청·READ COMMITTED 트랜잭션이다. 테스트 전용 JdbcProbe observer로 첫 요청이 업무 UPDATE를 실제 실행한 뒤 commit 전에 멈춘다. 운영 코드에 테스트 훅은 추가하지 않았다.

첫 미커밋 변경이 다른 연결에서 보이지 않는 것(version=0)을 확인하고 두 번째 이동을 시작한다. 두 번째 프로젝트 FOR UPDATE의 연결 id를 읽어 컨테이너의 `information_schema.INNODB_LOCK_WAITS`·`INNODB_TRX`에서 실제 대기를 확인한다. PROCESS 조회가 필요해 이 테스트의 감시 연결만 일회용 컨테이너 root 계정을 사용한다. 개발 DB에는 연결하지 않는다.

## 원인
이전 시작 barrier는 요청을 동시에 출발시키지만 스케줄러가 실제 위험 구간을 겹치게 실행한다는 보장이 없었다.

## 검토했지만 버린 방법
- 두 요청을 오래 반복하기만 하기: 경합 순서를 보장하지 않는다.
- 프로젝트 잠금 뒤 두 작업에 같은 barrier 설치: 두 번째는 잠금에서 막히므로 첫 번째와 인위적 교착을 만든다.
- Thread.sleep만 보고 잠금 대기라고 판정: 스케줄링 지연과 DB 잠금을 구분하지 못한다.

## 선택과 이유
첫 UPDATE 뒤 latch로 정지 → 두 번째 잠금 진입 → DB 대기 행 확인 → 추가 150ms 동안 미진행 확인 → 첫 commit 허용 → 두 번째 잠금 획득·첫 version=1 조회 순서다. HTTP 응답 완료의 스레드 스케줄링 순서 대신 DB 잠금/커밋 가시성 순서를 증거로 사용한다. 첫 commit 전에 두 번째가 같은 프로젝트 상태나 위치를 읽지 못함을 직접 확인한다.

## 전후 수치 / 결과
- 이전 증거: 시작 barrier, 실제 잠금 대기 행 관측 없음.
- 추가 검증: 완료·재정렬 모두 실제 대기 행 1개, 첫 commit 후 version=1 가시성 true.
- 보완 검증 실행: 완료 대기 166.783ms, 재정렬 대기 166.790ms. 이 중 최소 150ms는 테스트가 의도적으로 유지한 대기이며 운영 지연 수치가 아니다.
- 완료 최종 프로젝트 DONE, 재정렬 position 중복 0.
- 이벤트: A_TASK_UPDATED_UNCOMMITTED → B_ATTEMPTS_PROJECT_LOCK → DATABASE_CONFIRMS_B_LOCK_WAIT → A_ALLOWED_TO_COMMIT → B_LOCK_ACQUIRED_A_COMMIT_VISIBLE.
- `M4ReviewRegressionTest`와 묶은 8/8 성공.

## 남은 위험
**10/10은 barrier로 최악의 순서를 강제한 재현율이며 운영 발생 확률이 아니다.** 이번 테스트는 잠금 경로를 결정적으로 입증하며 장시간 경합의 처리량/운영 지연을 측정하지 않는다. 공유 프로젝트의 쓰기 직렬화 비용은 남는다.

## 커밋
이 문서와 JdbcProbe/M4DeterministicLockTest가 같은 커밋이다. 최종 커밋 표는 M4 2차 보고서에 기록한다.
