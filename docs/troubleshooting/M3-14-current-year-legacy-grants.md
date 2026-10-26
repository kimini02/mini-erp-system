# M3 현재 연도 소급과 옛 부여 인정

기준: `de9d307:backend/md/14-rest-api-v2-m3.md` (읽기 전용). Java 17 + MariaDB 10.11 Testcontainers.

## 증상
자동 부여가 지난 해 행을 만들고, 키 없는 옛 GRANT가 있는 해에도 중복 성격의 부여를 만든다.

## 재현 방법
M3LeaveGrantTest.backfillOnlyCurrentYearSkipsUnknownHireAndIsIdempotent와 legacyGrantSkipsWholeYearAndNextJanuaryResumesWithoutDeletingLedger. 수정 전 지난 해 M 키 0 기대가 1, 옛 GRANT 사용자 자동 행 0 기대가 14로 2/2 실패했다.

## 원인
계산기가 모든 지난 부여일을 반환했고, 중복 검사가 grant_key만 보므로 키 없는 GRANT를 인식하지 못했다.

## 검토했지만 버린 방법과 이유
옛 원장 삭제·상계나 지난 해 전체 소급은 사용자 확정 결정과 달라 버렸다. 입사일 없는 사원에게 날짜를 추정해 부여하지 않는다.

## 선택한 방법
실행 연도와 같은 해의 월 부여·1주년·1월 1일 부여만 계산한다. 사용자 행 잠금 안에서 같은 해 GRANT 중 grant_key NULL이 있으면 해당 사원의 자동 부여를 전부 건너뛴다. skipped_count는 건너뛴 사원 수이며 message에 입사일 없음과 옛 부여 있음을 각각 ID 목록으로 남긴다. 둘 다 해당하면 입사일 없음으로 한 번만 센다.

## 부작용·남은 위험
옛 원장은 보존한다. 다음 해에는 그해 옛 GRANT가 없다면 규칙대로 부여한다. 현재 연도에서 이미 잘못 부여된 행이나 지난 해 자동 행을 정리하는 데이터 변경은 하지 않는다. remainingAnnualLeave 호환 필드는 변경하지 않는다.

## 결과
M3LeaveGrantTest 6/6 성공. 지난 해 0행, 옛 GRANT 해 0 자동 행·건너뜀 2명(입사일 없음 1/옛 부여 1), 다음 해 Y 16일, 옛 GRANT 7일 보존. 동시 실행 10회 중 중복 0회.

검증 로그: `/private/tmp/m3-phase3-before.log`, `/private/tmp/m3-phase3-after-main.log`. 전체 기본·UTC 결과는 최종 3차 보고서에 기록한다.
