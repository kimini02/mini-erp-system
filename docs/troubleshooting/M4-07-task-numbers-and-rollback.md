# M4-07 — 동시 업무 번호 발급과 알림 원자성

## 증상
v1 업무 등록은 task_no를 모르므로 V6 이후 기존 경로로 등록하면 500이다. 새 번호 발급이 잠금 없이 이뤄지거나 실패한 요청이 번호만 소비하면 중복/빈 번호가 생긴다. 알림만 따로 저장하면 업무와 배정 사실이 어긋난다.

## 재현 조건
`M4LegacyWriteBridgeTest.legacyCreateUsesSequenceAndHistoryInsteadOfNullTaskNumber`: 연결 수정 전 실제 구 등록 API의 기대 201 대신 500을 먼저 확인했다.

수정 후 `M4BoardConcurrencyTest.tenConcurrentCreatesHaveContiguousUniqueNumbers`: 빈 프로젝트에 실제 스레드 10개, 시작 barrier, 같은 팀장 JWT로 10개 등록한다. MariaDB 10.11 Testcontainers, READ COMMITTED다.

`M4BoardApiTest.notificationFailureRollsBackTaskSequenceHistoryAndAssignment`: NotificationService의 테스트 spy에서 TASK_ASSIGNED 저장을 실패시킨다. 트랜잭션 프록시 아래의 spy를 사용해 저장 시점에 실패하도록 한다.

## 원인
번호는 개별 업무가 아닌 프로젝트의 공유 상태다. 알림·이력·담당자·번호를 서로 다른 트랜잭션으로 쓰면 일부만 성공할 수 있다.

## 검토했지만 버린 방법
- MAX(task_no)+1 무잠금: 두 요청이 같은 값을 읽을 수 있다.
- 전역 DB AUTO_INCREMENT를 업무 번호로 표시: 프로젝트별 연속 번호가 아니며 롤백도 빈 번호를 남긴다.
- 앱 안 알림을 AFTER_COMMIT 저장: 알림 실패 시 업무와 배정을 롤백할 수 없다.

## 선택과 이유
프로젝트 잠금 안에서 `last_task_seq+1`로 업무를 만들고 seq를 갱신한다. `(project_id,task_no)` 유니크를 마지막 보호장치로 둔다. 업무·담당자·CREATED/ASSIGNED 이력·TASK_ASSIGNED 알림·프로젝트 상태는 같은 트랜잭션이다. 알림은 외부 메일/Redis가 아닌 DB 알림이므로 M2 결정대로 같은 트랜잭션에 저장한다.

## 전후 수치 / 결과
- 기존 등록 경로: V6 이후 500 → 새 서비스 연결 후 201, task_no=1, CREATED/ASSIGNED 이력 2개.
- 동시 10개 추가: 10개 성공, 번호 1..10, 중복 0, 빈 번호 0, 같은 열 position 중복 0, last_task_seq=10.
- 알림 저장 실패: 업무·담당자·이력·알림 0건, last_task_seq=0 유지. 다음 성공 요청은 task_no=1.
- 권한/범위 거절 시 알림·번호 변화 0은 M4BoardApiTest에서 별도로 확인한다.

v1에는 프로젝트별 번호가 없었으므로 과거 번호 발급의 중복률을 꾸며 비교하지 않았다. 새 기능의 동시성 불변식과 실제 기존 API의 500 재현을 구분한다.

## 남은 위험
전역 tasks.id AUTO_INCREMENT에는 실패 요청으로 빈 값이 생길 수 있다. 사용자에게 보이는 프로젝트별 task_no 연속성과 다른 개념이다. 향후 업무 삭제 기능이 생기면 번호 재사용/결번 정책을 별도로 정해야 한다. 현재 명세에는 삭제가 없다.

## 커밋
[1차 결과의 커밋 표](M4-00-phase-one-results.md) 참조.
