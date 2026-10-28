# M4 2차 리뷰 — 마감 알림의 중복만 무시하기

## 증상
INSERT IGNORE는 중복뿐 아니라 잘못된 외래키 입력도 경고로 넘길 수 있다. 실제 알림 기록이 실패해도 배치가 성공으로 끝날 수 있었다.

## 재현 방법
M4OverdueTest.invalidClaimFailsBatchRatherThanSilentlySkipping에서 Testcontainers DB에만 임시 트리거를 두어 task_due_alerts.user_id를 존재하지 않는 값으로 바꾼다. 예외·FAILED 기록·알림/이력/중복 방지 행 0개를 확인하고 finally에서 트리거를 제거한 뒤 재시도한다. 로컬 개발 DB는 사용하지 않는다.

M4OverdueTest.claimAffectedRowsDistinguishFirstInsertFromRepeat는 같은 업무의 첫 실행과 재실행 SQL 반환 행 수를 JdbcProbe로 측정한다. 이전 테스트가 남긴 업무는 해당 테스트에서 DONE으로 바꿔 측정 대상을 하나로 격리했다.

## 원인
INSERT IGNORE의 오류 완화 범위가 넓다. 또한 그대로 ON DUPLICATE KEY UPDATE id=id로만 바꾸면 현재 MariaDB JDBC의 기본 CLIENT_FOUND_ROWS 동작 때문에 첫 삽입과 아무것도 바꾸지 않는 중복 처리가 모두 1을 반환한다. 이는 기존 `inserted == 0` 판단으로 구분할 수 없다. 이 동작은 실제 사용 중인 드라이버와 MariaDB 10.11에서 재현했다. [MariaDB의 INSERT ON DUPLICATE KEY UPDATE 문서](https://mariadb.com/docs/server/reference/sql-statements/data-manipulation/inserting-loading-data/insert-on-duplicate-key-update)도 반환 행 수가 CLIENT_FOUND_ROWS의 영향을 받는다고 설명한다.

## 검토했지만 버린 방법과 이유
- 단순 SQL 치환: 첫 삽입/재실행 모두 1이라 순차·동시 실행에서 알림이 각각 2건이 되는 회귀가 발생했다.
- 전역 useAffectedRows 변경: 다른 코드의 변경 없는 UPDATE 반환값도 달라진다. 예를 들어 OrgService의 직급 순서 변경은 0을 미존재로 판단하므로 범위 밖 영향이 생긴다.
- 예외를 모두 잡아서 무시: 중복 이외의 오류를 다시 숨긴다.
- 별도 연결·트랜잭션으로 중복 방지 행 저장: 알림·이력과의 원자성이 깨진다.

## 선택한 방법
사용자가 요청한 ON DUPLICATE KEY UPDATE id=id를 사용하되, 이미 잡은 프로젝트 행 잠금 안에서 INSERT SELECT의 WHERE NOT EXISTS로 같은 (task_id,user_id,due_date)를 제외한다. 별도 왕복 없이 기존 유니크 인덱스를 조회한다. 삽입 대상이 있으면 반환 행 수 1, 이미 있으면 삽입 대상 자체가 없어 0이다. 반환값이 정확히 1일 때만 알림과 이력을 쓴다. 외래키 등 다른 오류는 예외로 전파하여 같은 트랜잭션을 롤백하고 배치 기록은 FAILED로 남긴다.

## 전후 수치와 결과
| 실험 | 원래 INSERT IGNORE | 단순 no-op upsert | 최종 잠금+삽입 대상 제외+upsert |
|---|---|---|---|
| 첫 실행/재실행 반환 행 수 | 1 / 0 | 1 / 1 | 1 / 0 |
| 같은 마감일 순차 2회 알림 | 1 | 2 | 1 |
| 실제 스레드 동시 2회 알림 | 1 | 2 | 1 |
| 잘못된 외래키 입력 | 예외 없음(회귀 실패) | 예외 발생 | 예외 발생, FAILED·업무 데이터 0, 재시도 성공 |

- 원래 코드 + 격리된 회귀: M4HomeTest/M4OverdueTest 총 11개 중 실패 4개(홈 2·이력 1·외래키 오류 1), 오류·건너뜀 0.
- 단순 upsert 실험: 같은 11개 중 실패 3개(순차 중복·동시 중복·반환 행 수), 오류·건너뜀 0.
- 최종 M4OverdueTest 5개 성공. 수정 전 발생하던 오류와 SQL 치환으로 생긴 중복을 모두 확인한 뒤 해결했다.
- 첫 회귀 실험에는 이전 테스트 데이터가 측정에 섞여 1개의 추가 실패가 있었다. 대상 격리 후 운영 코드 변경 없이 다시 재현한 위 11개/4개 수치를 최종 수정 전 근거로 쓴다.
- 최종 Java 17 전체 검증: 기본 JVM 218/218, UTC JVM 218/218 성공. 두 환경 모두 실패·오류·건너뜀 0. 상세 실행 조건은 [M4-16](M4-16-home-task-project-id.md#전체-검증)에 기록했다.

## 부작용·남은 위험
task_due_alerts를 쓰는 경로는 반드시 같은 프로젝트 행 잠금부터 잡아야 한다. 새 경로가 이를 우회하면 WHERE NOT EXISTS와 삽입 사이에 경쟁이 생길 수 있고 기본 found-row 모드의 no-op 결과는 삽입과 구분되지 않는다. 현재 운영 쓰기는 TaskOverdueWork 한 곳이며 실제 동시 실행 테스트를 유지한다. 프로젝트 잠금·전체 배치 트랜잭션·batch_runs 기록의 기존 특성은 M4-13과 같다. 마이그레이션과 연결 설정은 바꾸지 않았다.

## 변경 파일
- backend/src/main/java/com/minierp/backend/domain/task/service/TaskOverdueWork.java
- backend/src/test/java/com/minierp/backend/integration/M4OverdueTest.java
- 이 문서
