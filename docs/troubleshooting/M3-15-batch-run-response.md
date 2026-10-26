# M3 실행 API의 배치 기록 응답

기준: `de9d307:backend/md/14-rest-api-v2-m3.md` 4.4절 (읽기 전용).

## 증상
부여 실행과 근태 다시 판정이 `{processed, skipped}`를 반환해 최근 실행 목록 항목 계약과 다르다.

## 재현 방법
M3BatchResponseTest의 grantReturnsItsPersistedRunWithLocalTimestamps와 rejudgeReturnsItsPersistedRunWithLocalTimestamps. 수정 전 두 API 모두 9개 필드 대신 내부 집계 필드를 반환해 2/2 실패했다. `/private/tmp/m3-phase3-response-before.log`.

## 원인
실행 서비스가 BatchRunRecorder 저장 결과 없이 작업의 Counts를 그대로 반환했다.

## 검토했지만 버린 방법과 이유
저장 후 가장 최근 행을 찾으면 동시 실행 때 다른 요청의 기록이 반환될 수 있다. 응답용 임의 ID·시각을 만들면 저장된 결과와 달라지므로 버렸다.

## 선택한 방법
INSERT에서 받은 생성 ID로 같은 기록 트랜잭션에서 해당 행을 읽어 반환한다. 두 실행 서비스가 이 행을 반환하며 GET 목록과 동일한 id, jobName, targetDate, status, processedCount, skippedCount, message, startedAt, finishedAt 형태다. SqlStore의 지역 날짜·시각 정규화를 그대로 사용한다.

## 부작용·남은 위험
기존 작업 트랜잭션과 REQUIRES_NEW 실행 기록 경계는 유지한다. 작업 실패는 롤백하고 FAILED 기록을 남기는 기존 검증을 유지했다. 작업 커밋 직후 실행 기록 저장 자체가 실패하는 경우까지 단일 트랜잭션으로 묶는 변경은 이번 범위가 아니다.

## 결과
수정 전 2/2 계약 실패. 응답 회귀 테스트는 저장된 ID 행과 전체 JSON 비교, 부여 GET 목록과 동일성, 반복 실행의 다른 ID, 한국 지역 시각 문자열을 확인한다. 최초 수정 후 검증에서 JSON 숫자 노드의 int/long 내부 타입 차이로 비교가 실패해, DB 맵도 JSON 직렬화·역직렬화한 뒤 비교하도록 테스트를 바로잡았다(실제 문자열은 같았음). 최종 검증 로그: `/private/tmp/m3-phase3-response-final.log`. 실패 롤백·미래 실행 차단은 `/private/tmp/m3-phase3-response-after.log`에서 M3BatchTest 4/4 및 M3GrantRunDateTest 1/1 통과. 전체 기본·UTC JVM 수치는 3차 최종 보고서에 기록한다.
