# M1-3 — 인증·메뉴·데이터 범위

## 증상
JWT만 검증하면 퇴사/비밀번호 변경 뒤에도 기존 토큰을 사용할 수 있고, 화면 메뉴 숨김만으로 접근을 막을 수 없다.

## 재현 방법
M1AcceptanceTest의 retirementRevokesExistingTokenImmediately, lastChiefCannotRetireOrLoseRole, permissionChangeAppliesToSameTokenOnNextRequest, leaderSeesDepartmentTreeAndOutsideFilterIs403, permissionWithoutAnyActionDoesNotWidenAnotherRolesScope를 실행한다.

## 원인
JWT에 담긴 역할은 발급 시점에 고정된다. 데이터 범위는 메뉴 동작 권한과 별도로 쿼리에 적용해야 한다.

## 검토했지만 버린 방법과 이유
Redis 블랙리스트/권한 캐시와 조회 후 자바 필터는 사용하지 않는다. 권한이 전부 꺼진 행의 scope까지 합치면 범위가 확대되므로 제외한다(재현 테스트에서 수정 전 200, 기대 403).

## 선택한 방법
토큰 ver를 DB와 매 요청 비교한다. 컨트롤러 어노테이션/인터셉터에서 메뉴 동작을 검사하고 서비스에서 ScopeFilter를 리포지토리로 넘긴다. 재귀 CTE로 부서 하위를 구하며 역할별 동작 합집합/해당 메뉴의 최대 범위를 적용한다. 한 요청 안에서만 권한 조회 결과를 재사용한다.

## 부작용·남은 위험
명세 해석: 최대 범위는 전체 메뉴가 아니라 같은 메뉴의 유효 권한 사이에서 계산한다. 과거 ver 없는 토큰은 다시 로그인해야 한다. 기존 프로젝트 API 정책의 전체 RBAC 전환은 이번 명세에 없으므로 유지한다.

## 결과
요청 간 권한 캐시는 없다. 퇴사 즉시 차단·같은 토큰에 대한 다음 요청의 권한 변경과 외부 부서 403을 MariaDB에서 확인했다.
