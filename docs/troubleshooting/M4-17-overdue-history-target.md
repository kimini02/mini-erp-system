# M4 2차 리뷰 — 마감 알림 이력의 대상자

## 증상
업무 상세 OVERDUE_NOTIFIED 이력에는 수신자 id 문자열만 있고 화면에서 사용할 target.name이 없었다. 프로젝트 멤버에서 빠진 사람은 멤버 목록으로 이름을 복구할 수도 없다.

## 재현 방법
마감 업무를 배정하고 실제 TaskOverdueService로 알림과 이력을 만든다. 그 사용자의 업무 배정과 프로젝트 멤버를 제거한 뒤 프로젝트 팀장이 업무 상세를 조회한다. M4ReviewRegressionTest.overdueHistoryResolvesRecipientEvenAfterMemberRemoval에서 target.id·name과 시스템 실행의 actor=null을 단언한다.

## 원인
BoardTaskService.detail의 target id 계산과 users 조인은 ASSIGNED·UNASSIGNED만 처리했다.

## 검토했지만 버린 방법과 이유
화면에서 멤버 id를 이름으로 바꾸는 방식은 멤버 제거 뒤 동작하지 않는다. 이력별 별도 사용자 조회는 N+1을 만든다. 과거 이름 스냅샷 도입은 이번 요청과 스키마 범위를 넘는다.

## 선택한 방법
기존 CASE와 LEFT JOIN에 OVERDUE_NOTIFIED의 to_value를 포함한다. 기존 from/to·actor·at 응답과 저장된 이력은 유지하며 target: {id, name}만 추가한다.

## 전후 수치와 결과
- 수정 전 새 대상자 단언 1개 실패(target.id 없음). 처음에는 M4OverdueTest에서 재현한 뒤 논리적 변경 단위를 분리하려고 같은 회귀를 M4ReviewRegressionTest로 옮겼다.
- 수정 후 M4ReviewRegressionTest 7개 성공. 탈퇴한 멤버의 알림 수신자 이름도 조회된다.
- 최종 Java 17 전체 검증: 기본 JVM 218/218, UTC JVM 218/218 성공. 두 환경 모두 실패·오류·건너뜀 0. 상세 실행 조건은 [M4-16](M4-16-home-task-project-id.md#전체-검증)에 기록했다.

## 부작용·남은 위험
이름은 users의 현재 이름이며 과거 발송 당시 스냅샷은 아니다. users 자체가 삭제돼 조인이 끊기면 id는 남고 name은 null이다. 기존 ASSIGNED·UNASSIGNED와 같은 계약이다.

## 변경 파일
- backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java
- backend/src/test/java/com/minierp/backend/integration/M4ReviewRegressionTest.java
- 이 문서
