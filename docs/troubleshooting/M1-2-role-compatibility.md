# M1-2 — 기존 역할·응답 호환

## 증상
users.user_role 삭제 뒤 기존 프로젝트·업무·연차·특근이 사용하는 UserRole 판정이 끊긴다.

## 재현 방법
기존 ProjectServiceTest, TaskServiceTest, DashboardServiceTest, CoreApiIntegrationTest 및 InitialAdminMariaDbTest를 실행한다.

## 원인
기존 기능은 UserRole과 부서 코드 문자열을 사용하지만 M1은 user_roles, departments, positions를 사용한다.

## 검토했지만 버린 방법과 이유
구 컬럼 이중 쓰기는 어느 쪽이 실제 권한인지 모호해진다. 기존 프로젝트·업무 정책 재설계는 이번 범위가 아니다.

## 선택한 방법
User의 읽기용 투영에서 CHIEF → ADMIN, LEADER → TEAM_LEADER, 그 외 → USER를 계산한다. 01/02/03의 응답 별칭을 유지하고 신규 부서는 실제 부서 코드를 준다. 기존 테스트용 팩터리는 예약된 기본 역할/직급 ID로 연결한다.

## 부작용·남은 위험
M1 프론트 작업 후 제거: departmentCode, departmentName, position, role의 기존 호환 표현. 명세도 position 문자열을 사용하므로 최종 제거 모양은 프론트 작업 때 결정해야 한다. 기존 역할별 결재 계층(사원 신청은 팀장, 팀장 신청은 관리소장)은 유지한다.

## 결과
H2 초기 데이터를 새 구조에 맞췄고 기존 단위 테스트는 PermissionAccess 의존성만 가짜로 주입했다. 기존 성공/실패 기대를 약화하지 않았다. Core API의 정상 승인 fixture만 같은 부서로 맞췄다.
