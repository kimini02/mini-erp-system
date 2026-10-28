# M5-06 홈 프로필 카드의 결근·업무 건수

## 증상
오늘 근태·홈 응답에 이번 달 결근 수가 없고, 홈의 내 업무는 5개까지만 반환해 전체 담당 업무 수를 표시할 수 없다.

## 재현 방법
Java 17 + MariaDB 10.11 Testcontainers, UTC JVM에서 `M5HomeCountersTest`를 먼저 추가했다. 기존 홈 테스트와 함께 실행한 결과 8개 중 기존 5개 성공, 새 3개 실패, 오류·건너뜀 0. 누락된 `monthAbsentCount`와 `myTasks.count`를 -1로 읽어 예상값 2·7·0과 다름을 확인했다. 수정 전 `M4_HOME_FUNCTIONAL http_queries=14`.

## 원인
근태는 지각만 집계했고, 내 업무 SELECT는 LIMIT 5 결과만 반환했다. 목록 크기는 전체 건수가 아니다.

## 검토했지만 버린 방법과 이유
- 건수 전용 SELECT 두 개 추가: 같은 범위를 반복 조회하고 홈 SQL 상한을 불필요하게 늘린다.
- 모든 업무를 읽어서 Java에서 세기: 반환량이 늘고 SQL의 범위·페이지 규칙과 중복된다.
- items 크기를 count로 사용: 담당 업무가 6개 이상이면 틀린다.

## 선택한 방법
근태의 월 범위 집계 한 번에서 지각과 `absent=1`을 함께 계산한다. 홈의 근태 joined snapshot도 같은 집계를 사용한다. 출퇴근 응답은 동일 render를 공유한다. 월 기준은 주입된 한국 Clock의 이번 달이며, 월 경계의 전날 퇴근도 현재 월의 프로필 건수를 표시한다.

내 업무는 동일 담당자·미완료·보기 범위 조건 아래 `COUNT(*) OVER()`로 LIMIT 이전 전체 건수를 계산한다. 0건이면 count=0. 목록은 기존 마감일·id 순 5개를 유지한다. 권한이 없으면 위젯은 기존처럼 null이다.

## 전후 수치·결과

| 검증 | 수정 전 | 수정 후 |
|---|---:|---:|
| 새 테스트 | 0/3 성공 | 3/3 성공 |
| 해당 월 내 결근 2일 + 지난달 1일 + 다른 사원 1일 | 필드 없음 | 2 |
| 보기 범위 안 담당 미완료 7개 + 완료/다른 담당/범위 밖 | 필드 없음, items 5 | count 7, items 5 |
| 빈 위젯 | 필드 없음 | 두 건수 0 |
| SELF 홈 HTTP SQL(인증·권한 포함) | 14 | 14 |
| 관련 UTC 테스트 | 새 테스트 실패 재현 | 13/13, 실패·오류·건너뜀 0 |

수정 후 관련 실행: `M5HomeCountersTest` 3개, `M4HomeTest` 5개, `M3AttendanceTest` 5개. 한국 시각 00:30(UTC 전날)에서 날짜 경계를 확인하고 /attendance/today, /home.today, clock-in, clock-out 네 응답을 단언한다. 전체 UTC 결과와 1·10·100배 데이터 쿼리 수는 M5 추가 결과 보고서에 기록한다.

## 부작용·남은 위험
윈도 함수는 전체 일치 행을 계산하므로 담당 업무가 늘면 SQL 한 번의 비용은 늘어난다. SQL 수 고정은 처리 비용 O(1)을 뜻하지 않는다. 기존 READ COMMITTED의 위젯 사이 시점 차이도 유지된다. 스키마·권한·프론트 변경 없음.

## 커밋
이 문서와 네 운영 클래스, `M5HomeCountersTest`를 포함한 독립 커밋. 최종 해시는 M5 추가 결과 보고서에 기록한다.

전체 UTC 실행(2026-10-28T05:42:55Z): **230/230 성공, 실패·오류·건너뜀 0**, 9분 56초. Surefire XML 60개 합산과 Maven 최종 출력이 일치한다.

```text
M4_HOME_GROWTH scale=1 tasks=1000 documents=100 notifications=100 queries=14 median_ms=16.326
M4_HOME_GROWTH scale=10 tasks=10000 documents=1000 notifications=1000 queries=14 median_ms=33.087
M4_HOME_GROWTH scale=100 tasks=100000 documents=10000 notifications=10000 queries=14 median_ms=77.018
```

위 시간은 동일 개발 머신에서 실행한 참고치다. 동시에 컨테이너 도구 검증도 진행했으므로 M4의 과거 시간과 성능 증감률로 비교하지 않는다. 데이터 증가에도 **14/14/14쿼리**라는 기준을 재검증했다.
