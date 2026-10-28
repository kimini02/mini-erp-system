# M4-08 — 기존 쓰기 경로의 잠금·번호·version 우회 차단

## 증상
1차에서 구 쓰기 API를 그대로 두면 V6 뒤 업무 등록이 실패하고, 구 상태 변경은 version 없이 새 보드 규칙을 우회한다. 전역 USER 역할의 프로젝트 팀장도 구 경로에서는 배정을 못 한다.

## 재현 조건
수정 전에 `M4LegacyWriteBridgeTest` 4개를 먼저 실행했다. 2026-10-27 결과는 4개 실행, **3개 실패**, 오류·건너뜀 0이다. 이후 같은 테스트를 다시 실행했다.

## 원인
기존 TaskService/ProjectService의 JPA 쓰기는 새 필수 컬럼과 프로젝트 행 잠금·메뉴 범위를 모른다. v1 TaskStatusUpdateDto에는 version이 없다.

## 검토했지만 버린 방법
- 구 경로 그대로 유지: V6 이후 500·새 규칙 우회.
- 전부 삭제: 사용자는 최종 삭제를 2차로 정했다.
- 두 서비스에 새 잠금·번호 로직을 각각 복제: 유지 비용과 규칙 차이가 다시 생긴다.

## 선택과 이유
사용자에게 범위를 확인한 뒤 **기존 쓰기도 새 규칙으로 연결** 승인을 받았다. 구 입력 이름은 어댑터에서 새 DTO로 바꾸고 새 서비스에 위임한다. 구 상태 변경도 version을 필수로 받는다. 응답은 새 프로젝트/카드 응답으로 반환한다. 구 읽기 경로는 2차까지 유지한다.

프로젝트 수정·팀장 변경·멤버 추가/제거·사용자별 프로젝트 멤버십 일괄 변경도 새 관리 권한과 프로젝트 잠금을 쓴다. 일괄 변경은 모든 관련 프로젝트를 id 순서로 잠그고 모든 계획을 검증한 후 변경한다. 새 업무 등록·수정·담당자 배정은 같은 번호·이력·알림·상태 계산을 통과한다.

## 전후 수치 / 결과
| 회귀 | 수정 전 | 수정 후 |
|---|---|---|
| 구 업무 등록 | 500 (기대 201) | 201, 프로젝트 번호·이력 기록 |
| 구 상태 변경 version 누락 | 200 (기대 400) | 400; version 0 성공 뒤 같은 0 재요청 409 |
| USER인 프로젝트 팀장의 구 배정 | 403 (기대 201) | 201; 미완료 담당자 제거 409 |
| 타 부서 팀장 프로젝트의 구 수정 | 403 (이미 통과) | 403 유지 |

수정 후 4/4 통과. 새 API 9개와 묶은 실행도 13/13, 실패·오류·건너뜀 0이었다.

새 명세가 우선한 중복 경로:
- GET /api/v1/projects: v1 배열 → M1 방식의 content/page.
- POST /api/v1/projects: v1 요청 → 새 key/leaderId 등을 받는 계약.
- GET /api/v1/tasks/{id}: v1 TaskResponseDto → 새 카드 상세·이력.

연결한 구 쓰기 경로:
- PUT /projects/{id}, PATCH /projects/{id}/leader, POST /projects/{id}/members, DELETE /projects/{id}/members/{userId}, PUT /projects/permissions/{userId}.
- POST /tasks, PUT /tasks/{id}, PATCH /tasks/{id}/status, POST /tasks/{id}/assignments, DELETE /tasks/{id}/assignments/{userId}.

위 경로는 /api/v1 기준이다. `PATCH /tasks/{id}/status`는 `taskState`와 `version`을 받고 해당 열 맨 위로 이동한다. 새 화면은 /move의 afterTaskId를 사용해야 한다.

## 남은 위험
구 화면의 요청·응답이 완전히 호환되는 어댑터는 아니다. 특히 version 없는 구 상태 변경은 의도적으로 400이며, 쓰기 응답도 새 모양이다. 사용자 승인에 따른 한시적 연결이며 2차에서 경로·구 서비스를 함께 삭제해야 한다. 구 조회의 전역 역할 기반 권한과 N+1 가능성은 이번 연결로 고쳐지지 않는다. 로컬 DB나 프론트 코드는 바꾸지 않았다.

## 커밋
`4b7b6d8` — 프로젝트 쓰기 연결, `fbbf0e2` — 업무 쓰기 연결·회귀 테스트.
전체 검증 결과는 [1차 결과](M4-00-phase-one-results.md)를 따른다.
