# M3 근태 CSV 칸의 한글 표시

기준: `de9d307:backend/md/14-rest-api-v2-m3.md` (읽기 전용). Java 17 + MariaDB 10.11 Testcontainers.

## 증상
CSV 날짜 칸에 ON_TIME 등 내부 코드가 노출된다.

## 재현 방법
M3CsvLabelsTest.csvUsesKoreanLabelsAndEmptyNonAttendanceCellsWhileJsonKeepsCodes. 수정 전 정상 기대 대신 ON_TIME으로 1/1 실패했다.

## 원인
월간 표의 code를 CSV 문자열에 그대로 넣었다.

## 검토했지만 버린 방법과 이유
월간 JSON code까지 바꾸면 화면 계약이 깨지므로 CSV 출력 지점에서만 변환한다.

## 선택한 방법
정상·지각·조퇴·퇴근 누락·결근·휴가·오전 반차·오후 반차·휴일 근무로 표시한다. HOLIDAY/NONE/FUTURE는 빈 문자열이다. 기존 BOM·인용·수식 문자 방어·범위 검사를 유지한다.

## 부작용·남은 위험
JSON 범례와 셀 코드는 그대로다. 이후 코드가 추가되면 표시 매핑도 함께 추가해야 하며 미등록 코드를 조용히 빈칸으로 숨기지 않는다.

## 결과
M3CsvLabelsTest 1/1 성공. MariaDB 실제 행 12종을 CSV와 JSON으로 각각 조회해 9개 한글·3개 빈칸과 JSON 코드 유지, UTF-8 BOM을 확인했다.

검증 로그: `/private/tmp/m3-phase3-before.log`, `/private/tmp/m3-phase3-after-main.log`. 전체 기본·UTC 결과는 최종 3차 보고서에 기록한다.
