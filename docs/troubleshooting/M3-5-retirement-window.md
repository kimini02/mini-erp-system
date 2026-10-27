# M3-5 — 퇴사일과 재직 기간

## 증상
퇴사일이 없어 과거 재직 기간의 결근과 월간 포함 여부를 판단하지 못했다.

## 재현 방법
수정 전 M3RetirementTest.retirementRecordsKoreanDateAndReactivationClearsIt를 실행했다. 1회 중 1회 retiredDate가 없어서 실패했다.

## 원인
users에는 현재 상태만 있고 퇴사 날짜가 없었다. 1차 결근 배치는 현재 ACTIVE만 새 행 대상으로 삼았다.

## 검토했지만 버린 방법과 이유
updated_at으로 과거 퇴사일을 추정하면 무관한 수정 날짜를 퇴사일로 만들게 된다. 임의로 기존 퇴사자를 배치에서 제외하는 것도 확정된 NULL 무제한 규칙과 다르다.

## 선택한 방법
사용자 확정과 feat/m3-frontend d689d15 명세를 따른다. V5로 retired_date NULL 컬럼만 추가한다. 실제 RETIRED 전환 때 한국 Clock 날짜, RETIRED→ACTIVE 때 NULL, 반복 RETIRED는 기존 날짜 유지. 목록·상세에 retiredDate를 포함한다. 새 결근 대상은 입사일(없으면 생성일)≤대상일<퇴사일(NULL 무제한)이고 현재 ON_LEAVE가 아닌 사람이다. 기존 기록의 재판정은 유지한다.

## 부작용·남은 위험
결정 필요: 상태 변경 이력 테이블. 휴직 기간을 복원하지 못하므로 현재 휴직 상태를 과거 배치에도 사용한다. 기존 RETIRED+NULL은 명세대로 퇴사 날짜 제한이 없다. 재입사 때 퇴사일을 지우므로 과거 퇴사 기간도 별도 이력 없이는 복원할 수 없다.

## 결과
UTC JVM·MariaDB Testcontainers에서 M3RetirementTest 2개, M3BatchTest 4개, MariaDbMigrationTest 3개 = 9개 성공, 실패·건너뜀 0. 퇴사일 기록/복직 NULL, 퇴사 전날 생성·당일부터 제외, 휴직·입사 전 제외, 기존 NULL의 무제한을 검증했다. 월간 표시 검증은 M3-6에서 진행한다.
