# M1 리뷰 1 — 권한 있는 계정의 이메일·초대 보호

## 증상
EMPLOYEE U/C만 가진 사용자가 관리소장 이메일을 변경하거나 초대 대기 팀장에게 초대를 재발송할 수 있었다.

## 재현 방법
해당 회귀 테스트를 Java 17 + Docker에서 구현 변경 전 실행하고, 변경 후 같은 테스트를 다시 실행한다. 아래 결과에 테스트 이름과 전후 결과를 기록한다.

## 원인
EMPLOYEE U/C만 가진 사용자가 관리소장 이메일을 변경하거나 초대 대기 팀장에게 초대를 재발송할 수 있었다.

## 검토했지만 버린 방법과 이유
화면에서만 제한하거나 테스트 기대값을 낮추는 방법은 서버의 계약을 보호하지 못하므로 사용하지 않았다. 새 라이브러리 없이 기존 구조를 유지한다.

## 선택한 방법
이메일이 실제 변경되거나 초대를 재발송할 때 대상에게 EMPLOYEE 외 역할이 하나라도 있으면 ROLE U를 요구한다. 검사 실패는 DB 변경·토큰 접근·메일 이벤트 발행 전에 ACCESS_DENIED로 종료한다.

## 부작용·남은 위험
역할 권한이 없는 사용자가 같은 이메일로 다른 프로필 필드만 바꾸는 것은 기존 EMPLOYEE U 규칙을 따른다.

## 결과
- M1AcceptanceTest 신규 4개: employeeUpdateCannotChangeChiefEmail, employeeUpdateCanChangeEmployeeOnlyEmail, roleUpdateAllowsChiefEmailChange, employeeCreateCannotResendPendingLeaderInvitation.
- 수정 전: 차단 사례 2개 실패(기대 403, 실제 200), 허용 사례 2개 성공.
- 수정 후: 4개 모두 성공. 거절 시 사원 행·이메일·토큰 내용 유지 및 메일/토큰 저장소 호출 없음까지 검증.
- 추가 경계 privilegedResendRequiresRolePermissionBeforeAcceptedStateCheck: 수정 전 기대 403 대신 409로 실패. ROLE U 검사를 수락 상태 검사보다 먼저 수행하도록 보완했고 전체 실행에서 통과했다.
- 최종 전체 검증: 169개 중 성공 168, 실패·오류 0, 기존 특근 건너뜀 1.
