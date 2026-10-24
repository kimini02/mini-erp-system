# M2-7 — 결재와 앱 안 알림의 트랜잭션 경계

## 증상
작업 지시의 “커밋 뒤에 해야 하는 일(알림 등)은 AFTER_COMMIT” 예시와 명세 6장의 “알림 행은 결재 처리와 같은 트랜잭션에서 저장” 규칙이 달랐다.

## 재현 방법
아래 두 방향의 MariaDB Testcontainers 회귀 테스트로 검증한다.
1. 최종 승인 중 알림 저장을 일부러 실패시킨 뒤, 결재 문서·결재선·결재 이력·연차 원장이 모두 변경 전 상태인지 확인한다.
2. 동일 문서의 동시 승인에서 패배한 요청이 409 DOCUMENT_STATE_CONFLICT로 끝날 때, 성공한 요청의 알림만 존재하고 실패한 요청의 추가 알림이 없는지 확인한다.

## 원인
DB 안의 알림 행과 메일·Redis 등 DB 밖의 부수 효과를 같은 “알림” 예시로 표현해 트랜잭션 경계가 모호해졌다.

## 검토했지만 버린 방법과 이유
앱 안 알림을 AFTER_COMMIT에서 저장하면 결재 성공 뒤 알림 저장만 실패할 수 있고, 명세가 요구하는 원자성을 보장하지 못한다. M2는 외부 알림 발송이나 아웃박스 기능을 요구하지 않으므로 추가하지 않는다.

## 선택한 방법
**명세와 작업 지시가 달라서 명세를 따름.** 2026-10-22 사용자 확인에 따라 명세 6장을 기준으로 결재 상태·연차 원장·앱 안 알림 행을 같은 DB 트랜잭션에서 동기로 저장한다. AFTER_COMMIT은 메일·Redis처럼 DB 밖으로 나가는 작업에만 적용한다. 알림 저장에 별도 트랜잭션이나 예외 무시를 사용하지 않는다.

## 부작용·남은 위험
앱 안 알림 저장 실패가 결재 전체를 실패시킨다. 이는 누락된 알림 없이 결재 결과와 함께 저장하려는 의도적인 선택이다. 외부 발송이 추가되면 커밋 뒤 처리의 재시도·누락 문제는 별도로 설계해야 한다.

## 결과
M2DocumentIntegrationTest의 다음 두 테스트가 실제 MariaDB 10.11 + UTC JVM에서 통과했다.
- notificationInsertFailureRollsBackDocumentLineHistoryAndLeaveLedger: 알림 INSERT 실행 직후 예외를 주입한다. 문서·줄·원장 행은 처리 전 값과 같고 이력·알림 수도 늘지 않는다.
- concurrentApprovalHasOne409AndNoNotificationFromLosingRequestInTenTrials: 10회 모두 한 요청은 200, 다른 요청은 409 DOCUMENT_STATE_CONFLICT다. 문서당 승인 알림은 1개이며 실패 요청 때문에 추가된 알림은 없다. 원장 USE와 승인 이력도 각 1개다.

장애는 트랜잭션 프록시 내부의 실제 저장 대상에 주입하므로 AFTER_COMMIT이나 별도 트랜잭션으로 우회하지 않는다. 상세 동시성 전후 수치는 M2-4 문서를 따른다.

## 2차 조회·읽음 API
NotificationQueryService/NotificationController에서 본인 알림 목록, unread-count `{ "count": n }`, 단건 읽음, 모두 읽음을 제공한다. 읽음은 반복 요청에 멱등이며 다른 사원 알림은 변경 전에 403으로 거절한다. 목록/건수/읽음 SQL 모두 user_id로 제한한다. 시각은 공통 SqlStore의 LocalDateTime 변환을 따른다.

M2NotificationTest 1개 통과: 다른 사원 목록 0건, 타인 읽음 403 후 미읽음 수 유지, 반복 읽음, 모두 읽음, 링크 및 시간대 없는 createdAt 확인. 기존 M2DocumentIntegrationTest는 알림 저장 실패 시 문서·원장 롤백과 409 경쟁 패자의 알림 부재를 계속 검증한다.
