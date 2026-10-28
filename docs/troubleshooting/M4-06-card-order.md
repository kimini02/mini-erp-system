# M4-06 — 카드 간격 정렬·동시 이동·version

## 증상
전체 재번호 방식은 카드 하나를 옮겨도 열 전체 UPDATE가 필요하다. 잠금 없이 중간값을 구하면 두 카드가 같은 position을 차지할 수 있고, 오래된 화면의 이동이 다른 사람의 변경을 덮어쓸 수 있다.

## 재현 조건
수정 전 `M4LegacyExperimentTest.fullRenumberUpdatesOneHundredRowsAndUnlockedMovesDuplicateTwentyTimes`를 먼저 실행했다. v1에는 position이 없으므로 비교 알고리즘을 별도로 구현했다. 카드 100개, 마지막 카드를 맨 앞으로 이동한다. 경합 실험은 서로 다른 두 카드가 같은 간격을 읽은 뒤 각각 UPDATE하도록 barrier로 제어한다. 같은 조건을 20회 반복하며 매회 1024 간격으로 초기화한다.

수정 후 `M4BoardConcurrencyTest.gapsUpdateOneRowSerializeTwentyMovesAndRebalanceOnlyWhenExhausted`는 실제 이동 API를 사용한다. 두 요청은 시작 barrier에서 함께 출발하고 프로젝트 잠금으로 직렬화된다. 별도 테스트 `M4BoardApiTest.staleVersionAndInvalidAnchorsLeaveOrderAndHistoryUnchanged`는 성공한 이동 전 version을 다시 보낸다.

## 원인
정렬값은 카드 하나가 아닌 같은 열의 앞뒤 카드를 보고 정하는 값이다. 개별 업무 행 잠금만으로는 두 카드가 같은 빈 간격을 고르는 것을 막지 못한다. 상태만 보내면 어느 화면 버전을 기준으로 이동했는지 알 수 없다.

## 검토했지만 버린 방법
- 매번 모든 카드 UPDATE: O(N) 쓰기.
- 실수 position: 반복 중간값 계산의 정밀도 문제가 있다.
- position 유니크 제약만: 충돌 오류·재시도가 늘고 프로젝트 상태·번호를 함께 보호하지 못한다.
- 오래된 요청을 자동 재시도: 사용자 의도를 확인하지 않고 최신 이동을 덮어쓸 수 있다.

## 선택과 이유
프로젝트 잠금 → 권한 → version 비교 → 위치 계산 → 저장/version 증가 → 이력 → 상태 재계산 순서다. 1024 간격 정수 사이의 중간값을 쓰고, 간격 1 이하일 때 목적지 열만 ROW_NUMBER로 한 번 재정렬한다. 이동 카드 자체는 재정렬에서 제외하고 새 위치로 한 번 저장한다. 정상 이동 시 기존 다른 카드 position은 바뀌지 않는다.

## 전후 수치 / 결과
| 항목 | 수정 전 비교 구현 | 수정 후 API |
|---|---:|---:|
| 카드 100개의 일반 이동 UPDATE 업무 행 | 100 | 1 |
| 동시 이동 20쌍(40요청)의 position 중복 합계 | 20 | 0 |
| 정상 실험 재정렬 횟수 | 매 이동 전체 재번호 | 0 |
| 간격을 1로 만든 추가 실험 재정렬 | 해당 없음 | 1 |
| 강제 재정렬의 UPDATE 업무 행 | 해당 없음 | 100 (다른 카드 99 + 이동 카드 1) |
| 오래된 version | 검사 없음 | 409, 순서·이력 변화 0 |

JDBC executeUpdate 반환값을 합산했으며 프로젝트 상태 UPDATE는 위 업무 행 수에서 제외했다. 재정렬 SQL 실행 횟수도 실제 관측했다. 목적지 아닌 다른 열·다른 프로젝트·자기 자신을 afterTaskId로 지정하면 422, 쓰기 없음.

409 응답은 공통 error에 `details.currentTask`를 추가해 최신 카드를 반환한다. 일반 오류에는 details가 없으며 기존 code/message/timestamp를 유지한다. 이동 성공 응답은 `{task, project}`다. 두 wrapper의 세부 키는 명세에 완전히 고정되어 있지 않아 이 모양으로 구체화했으며 프론트 연결 시 공유해야 한다.

## 남은 위험
재정렬은 목적지 열 O(N) 쓰기다. 그때 다른 카드의 물리적 position은 바뀌지만 상대 순서는 같으므로 다른 카드의 version은 증가시키지 않았다. 이동 카드 version은 항상 증가한다. 실험의 중복 20/20은 취약한 순서를 강제로 만든 재현율이며 운영 빈도가 아니다. 명세대로 별도 position 유니크 제약은 추가하지 않았다.

## 커밋
`ba520b5` — 비교 알고리즘, `fbbf0e2` — 간격·version·프로젝트 잠금.
전체 검증 결과는 [1차 결과](M4-00-phase-one-results.md)를 따른다.

## 2차 리뷰 보완 — 순서 이동의 잠금 대기
같은 열 재정렬에도 첫 UPDATE 뒤 정지 → 두 번째 FOR UPDATE 대기 행 확인 → 첫 commit 후 위치 계산의 결정적 순서를 적용했다. 대기 행 1개, 첫 변경 가시성 true, 최종 위치 중복 0을 확인했다. [M4-09](M4-09-deterministic-lock-wait.md).
