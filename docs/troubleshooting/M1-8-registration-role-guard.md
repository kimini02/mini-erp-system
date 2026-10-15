# M1-8 — 사원 등록 시 역할 권한 상승 방지

## 증상
EMPLOYEE C만 가진 담당자가 사원을 등록하면서 CHIEF를 지정해 관리소장 계정을 만들 수 있었다.

## 재현 방법
`M1AcceptanceTest.employeeCreatorCannotAssignOtherRoles`와 `employeeCreatorCannotHidePrivilegedRoleAlongsideEmployee`를 실행한다. EMPLOYEE C만 부여한 계정으로 CHIEF, LEADER, 사용자 정의 역할, EMPLOYEE+CHIEF를 보내면 수정 전 4건 모두 기대 403 대신 201이었다.

## 원인
등록 API는 EMPLOYEE C만 검사하고 요청 roleCodes를 그대로 저장했다. 기존 사원의 역할 변경 API에만 ROLE U 검사가 있었다.

## 검토했지만 버린 방법과 이유
프론트의 역할 선택 숨김은 직접 요청을 막지 못한다. 모든 등록에 ROLE U를 요구하면 일반 사원만 등록하는 인사 담당의 권한까지 불필요하게 제한한다. CHIEF만 제한하면 다른 역할이나 사용자 정의 역할로 우회할 수 있다.

## 선택한 방법
등록 서비스의 저장·번호 발급 전에 roleCodes 중 EMPLOYEE 이외 값이 하나라도 있으면 ROLE U를 추가 검사한다. 없으면 기존 BusinessException의 403 ACCESS_DENIED를 반환한다. EMPLOYEE C 검사는 컨트롤러의 기존 어노테이션으로 유지한다.

## 부작용·남은 위험
ROLE U를 부여받은 사용자는 요청대로 다른 역할을 지정할 수 있다. 범위가 넓은 권한이므로 관리자에게 이 의미를 안내해야 한다. 빈 역할 목록의 기존 ROLE_REQUIRED 검증은 유지한다.

## 결과
수정 전 권한 차단 4건 실패 → 수정 후 등록 권한 테스트 6건 통과. EMPLOYEE만 등록하면 201, ROLE U도 있으면 CHIEF 등록 201이다. 거절 시 사용자·번호 발급·메일 발송이 일어나지 않는 것도 검사한다.
