# M4-05 — 업무 1,000개의 상태 계산 비용

## 증상
업무 한 개를 변경할 때도 프로젝트 업무 목록 전체를 메모리로 가져와 상태를 계산한다.

## 재현 조건
수정 전 단독 실험은 [M4-02](M4-02-baseline-experiments.md)에 기록했다. 최종 비교는 `M4BoardConcurrencyTest.aggregateCostComparedWithWholeListOnSameThousandRows`: MariaDB 10.11, Java 17, READ COMMITTED, 같은 프로젝트 업무 1,000개 중 한 개 DONE/나머지 DOING. 전후를 번갈아 실행하며 각각 예열 3회, 측정 9회 중앙값이다.

측정 구간은 트랜잭션 시작부터 commit까지다. 전은 업무 UPDATE → 전체 SELECT → 프로젝트 UPDATE, 후는 프로젝트 잠금 SELECT → 동일 업무 UPDATE → 실제 `ProjectStateStore.recalculate`의 GROUP BY/프로젝트 UPDATE다. HTTP 인증·이력·응답 조립 비용은 이 비교 구간에 포함하지 않았다. 전체 move API의 SQL 수로 오해하면 안 된다.

## 원인
상태 결정에는 상태별 개수만 필요한데 행의 모든 열을 애플리케이션으로 보낸다. JPA 객체 생성 없이 JDBC 목록 비교 구현만 사용해도 전송·행 변환 비용이 생긴다.

## 검토했지만 버린 방법
- 업무 수를 프로젝트에 증감 저장: 초기 이관·상태 이동·롤백 등에서 중복 상태를 관리해야 한다.
- 캐시: 정합성 문제를 다른 곳으로 옮기고 명세 밖이다.
- 쿼리 수만 감소시키기: 필요한 프로젝트 잠금 SELECT는 오히려 한 번 추가된다. 쿼리 수만으로 최적화를 판단하지 않는다.

## 선택과 이유
`SELECT task_status,COUNT(*) FROM tasks WHERE project_id=? GROUP BY task_status`로 최대 3행만 반환한다. 프로젝트 잠금 안에서 실행하고, 업무가 없거나 모두 TODO면 READY, 모두 DONE이면 DONE, 나머지는 PROGRESS다.

## 전후 수치 / 결과
2026-10-27 기본 JVM 전체 실행에서 관측한 한 쌍이다. 같은 소스의 UTC 전체 검증도 다른 임시 빌드 폴더에서 실행 중이었으므로 Docker 공유 부하가 포함된다.

| 항목 | 목록 전체 방식 | 프로젝트 잠금 + 집계 |
|---|---:|---:|
| 업무 계산 SELECT 반환 행 | 1,000 | 2 (DONE/DOING) |
| 전체 SELECT 반환 행 (프로젝트 잠금 포함) | 1,000 | 3 |
| 실행 SQL | 3 | 4 |
| SELECT / UPDATE | 1 / 2 | 2 / 2 |
| 중앙값, 예열 3 / 측정 9 | 11.604 ms | 7.330 ms |

`JdbcProbe`에서 실제 실행 SQL과 ResultSet.next 성공 횟수를 센다. 반환 행 감소 99.8%는 **애플리케이션이 받는 업무 행** 기준이며 DB가 2행만 스캔했다는 뜻이 아니다.

같은 실행에서 MariaDB `ANALYZE SELECT`로 실제 읽기와 계획을 확인했다(테이블 전체 1,120행, 해당 프로젝트 1,000행):

| 항목 | 목록 | 집계 |
|---|---|---|
| 접근 방식 | ALL | ref |
| 선택 인덱스 | 없음 | idx_tasks_project_status |
| 추정 rows | 1,120 | 1,001 |
| 실제 r_rows | 1,120 | 1,000 |
| Extra | Using where | Using where; Using index |

기존 상태 인덱스를 재사용했다. 아직 M4 2차 홈 쿼리용 인덱스는 추가하지 않았다.

## 남은 위험
시간은 기기·Docker 부하·캐시에 민감하다. 시간 단축률을 CI 성공 조건으로 두지 않고 반환 행/SQL 수/정합성을 검증한다. 집계도 프로젝트의 인덱스 엔트리 약 1,000개를 읽으므로 O(1) 계산이 아니다. 이 실험은 전체 HTTP 응답 시간이나 운영 처리량 측정이 아니다. UTC 실행의 동일 조건 중앙값은 [1차 결과](M4-00-phase-one-results.md)에 별도로 기록한다.

## 커밋
[1차 결과의 커밋 표](M4-00-phase-one-results.md) 참조.
