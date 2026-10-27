# M4-03 — 프로젝트 권한·범위와 고정 쿼리 수

## 증상
v1의 전역 역할 3개로 판단하면 일반 사원 역할의 프로젝트 팀장이 자기 프로젝트를 관리하지 못한다. 프로젝트 응답마다 팀장을 지연 로딩하면 목록의 쿼리 수가 데이터 양에 따라 늘 수 있다.

## 재현 조건
`M4LegacyWriteBridgeTest.legacyMembersAndAssignmentsFollowManagerAndUnfinishedTaskRules`: EMPLOYEE 역할의 프로젝트 팀장이 기존 담당자 추가 경로를 호출한다. 수정 전 기대 201 대신 403을 확인했다. 수정 후 같은 요청이 201이며 미완료 업무 담당자 제거는 409다.

새 권한 계약은 `M4BoardApiTest`에서 검증한다. MariaDB Testcontainers, 실제 JWT/인터셉터/서비스/쿼리를 통과한다. `JdbcProbe`는 테스트 전용 DataSource 프록시로 실행된 SQL을 세며 새 라이브러리를 사용하지 않는다.

## 원인
전역 역할은 특정 프로젝트의 팀장 여부와 PROJECT 메뉴 권한·범위를 표현하지 못한다. 프로젝트마다 팀장·업무·멤버를 별도로 조회하면 N+1이 생긴다.

## 검토했지만 버린 방법
- USER이면 항상 쓰기 금지: 프로젝트 팀장 예외를 무시한다.
- 전체 조회 후 Java에서 범위 필터: 권한 밖 데이터가 먼저 읽히고 페이지 수가 잘못된다.
- 컨트롤러에 PROJECT U만 지정: 자기 프로젝트 팀장의 예외가 막힌다.
- 팀장·집계 정보를 프로젝트별 조회: 목록 크기만큼 SQL이 증가한다.

## 선택과 이유
읽기는 PROJECT V, 관리는 서비스에서 `U + 보기 범위` 또는 프로젝트 팀장으로 판단한다. SELF는 팀장/멤버, DEPT는 SELF 또는 팀장 소속 부서, DEPT_TREE는 재귀 부서 목록을 SQL IN 조건으로 적용한다. ALL은 전체다. 요청 내 권한 재사용 외 별도 캐시는 없다.

목록은 COUNT와 팀장 JOIN·GROUP BY 집계를 포함한 조회로 구성한다. 보드는 프로젝트 요약, 업무, 멤버·전체 담당자 UNION의 3쿼리다. Java는 허용된 프로젝트의 결과를 화면 모양으로 묶는 데만 사용한다. 쓰기는 프로젝트를 먼저 잠그고 권한·범위를 재확인한다. 멤버 변경은 제거 대상의 미완료 업무를 쓰기 전에 검사한다.

## 전후 수치 / 결과
- 일반 사원 역할의 프로젝트 팀장: 기존 담당자 추가 403 → 201, PROJECT U 없어도 관리 가능.
- 비멤버 사원의 프로젝트 상세·보드·업무 상세·이동: 모두 403.
- DEPT 팀장의 타 부서 팀장 프로젝트 상세·이동: 403.
- 담당자가 아닌 멤버의 이동: 403. 자기 카드 담당자는 200, 카드 일반 수정은 403.
- 범위 거절 시 이력·업무 버전·번호·알림 변화 0.
- DEPT_TREE 재귀 쿼리와 `u.department_id IN` 조건을 JDBC 관측으로 확인.

| 조회 | 데이터 변화 | 기능 데이터 SQL | 인증·권한 포함 SQL |
|---|---|---:|---:|
| 프로젝트 목록 | 기존 데이터에 프로젝트 20개·업무 2,000개 추가 | 2 → 2 | 6 → 6 |
| 프로젝트 보드 | 업무 1개 → 100개 | 3 → 3 | 7 → 7 |

범위가 ALL인 관리소장 요청의 측정값이다. DEPT_TREE는 재귀 조회가 추가되지만 프로젝트/카드 수에 따른 증가가 아니다. v1 목록 전체 HTTP 쿼리 수의 별도 전후 측정은 이번 실험에 포함하지 않았다. 홈의 1+2N 측정은 2차다.

테스트: `projectLifecycleKeepsKeyLeaderAndMemberContract`, `visibilityAndMovePermissionsRejectBeforeAnyWrite`, `departmentTreeAndAllScopesAreSqlPredicates`, `assigneeCanMoveOwnCardButCannotManageOrMoveOthers`, `memberRemovalAndAssignmentsAreValidatedAtomically`, `projectListAndBoardQueryCountsStayConstant`.

## 남은 위험
명세의 기본 역할은 V가 있다. 관리 API도 보기 권한을 먼저 확인하므로 V 없이 U만 부여한 역할 또는 V 없는 프로젝트 팀장은 접근할 수 없다. 보기 범위 계약을 우선했으며 이 조합을 허용해야 한다면 결정이 필요하다. 기존 읽기 API는 2차 제거 전까지 v1 역할 규칙이 남는다. 기존 읽기 API까지 새 권한으로 통일했다는 의미가 아니다.

## 커밋
`4b7b6d8` — 프로젝트 API·권한·구 프로젝트 쓰기 연결. 통합 회귀 테스트는 `fbbf0e2`.
전체 검증 결과는 [1차 결과](M4-00-phase-one-results.md)를 따른다.
