# M3 미래 날짜의 연차 부여 실행 차단

기준: `de9d307:backend/md/14-rest-api-v2-m3.md` (읽기 전용). Java 17 + MariaDB 10.11 Testcontainers.

## 증상
수동 실행에 다음 해 날짜를 보내면 내년 몫까지 현재 부여할 수 있다.

## 재현 방법
M3GrantRunDateTest.futureGrantDateIsRejectedBeforeLedgerOrBatchWrites. 수정 전 400 기대 대신 200으로 1/1 실패했다.

## 원인
공통 서비스에서 날짜의 NULL만 검사했다.

## 검토했지만 버린 방법과 이유
화면 날짜 선택만 막으면 직접 요청으로 우회한다. 컨트롤러만 검사하면 다른 호출 경로가 달라지므로 공통 실행 서비스에서 검사한다.

## 선택한 방법
LeaveGrantService에서 주입된 Asia/Seoul Clock의 오늘보다 뒤 날짜를 400 INVALID_INPUT_VALUE로 거부한다. 작업과 배치 기록을 남기기 전에 검사한다.

## 부작용·남은 위험
오늘과 과거 날짜 수동 실행은 유지한다. 과거 실행도 대상 실행 연도의 확정된 정책을 따른다.

## 결과
M3GrantRunDateTest 1/1 성공. 내일·다음 해 1월 1일 모두 400이며 원장·batch_runs 증가가 없다.

검증 로그: `/private/tmp/m3-phase3-before.log`, `/private/tmp/m3-phase3-after-main.log`. 전체 기본·UTC 결과는 최종 3차 보고서에 기록한다.
