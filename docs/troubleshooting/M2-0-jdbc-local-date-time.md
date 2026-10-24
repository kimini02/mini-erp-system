# M2 선행 수정 — KI-06 JDBC 날짜 응답의 시간대

## 증상
사원 목록의 `createdAt`이 시간대 없는 한국 시각 대신 UTC 오프셋이 붙은 문자열로 반환된다. JVM 시간대가 UTC일 때 DB의 `21:07:29.213456`이 `21:07:29.213+00:00`으로 나가므로, 한국 시간으로 표시하는 화면은 이를 다음 날 06:07로 해석할 수 있다. JSON 직렬화 과정에서 마이크로초 정밀도도 밀리초로 줄었다.

## 재현 방법
1. 로컬 개발 DB 대신 MariaDB 10.11 Testcontainers에 V1·V2를 적용한다.
2. JVM 시간대를 UTC로 두고, 고정된 Asia/Seoul Clock에서 날짜를 가져와 `created_at`에 21:07:29.213456을 저장한다.
3. `DATE_FORMAT`으로 DB의 원문 시각을 읽어 기대값으로 삼는다. 같은 날짜를 JDBC Timestamp로 다시 읽어서 기대값을 만드는 방식은 피한다.
4. 관리소장 JWT로 `GET /api/v1/users`를 호출해 `createdAt`이 기대 문자열과 같은지 확인한다. `hireDate`, NULL `updatedAt`도 확인한다.
5. `SqlStore.rows`와 이를 사용하는 `one`에서 임의 컬럼명(`submittedAt`, `completedAt`)도 같은 변환을 거치는지 확인한다.

```sh
# backend/에서 Java 17 + Docker 실행 상태로 실행
./mvnw -Duser.timezone=UTC -Dtest=SqlStoreDateTimeMariaDbTest test
```

## 원인
`NamedParameterJdbcTemplate.queryForList`는 MariaDB DATETIME을 `java.sql.Timestamp`, DATE를 `java.sql.Date`로 반환한다. `EmployeeService.views`는 이를 Map에 그대로 담는다. Jackson은 Timestamp를 시간대 없는 지역 시각이 아니라 날짜/시각의 순간 값으로 직렬화하므로 JVM 시간대에 따라 표시가 달라진다. JPA 자동 기록에 Clock을 연결한 KI-05와는 별개인, 조회 결과의 직렬화 문제다.

## 검토했지만 버린 방법과 이유
- JVM 또는 Jackson의 시간대를 Asia/Seoul로 고정: 명세가 요구하는 시간대 없는 문자열을 보장하지 못하며 실행 환경에 의존한다.
- 화면에서 9시간을 빼기: 서버 계약 오류를 화면에 전가하고, 실행 환경에 따라 중복 보정할 위험이 있다.
- 각 서비스나 필드에서 문자열로 변환: 사원·결재·이력·알림별로 변환이 누락되기 쉽고, 날짜 타입을 일찍 잃는다.
- Jackson 전역 Date 직렬화 변경: 이번 문제와 다른 의미를 가진 순간 값까지 영향을 줄 수 있다.

## 선택한 방법
`SqlStore.rows`가 반환하기 직전에 모든 행의 값을 한 곳에서 정규화한다.
- `java.sql.Timestamp` → `LocalDateTime` (`toLocalDateTime`)
- `java.sql.Date` → `LocalDate` (`toLocalDate`)
- NULL, 숫자, 문자열 등 나머지 값은 유지

`one`은 `rows`를 사용하므로 같은 규칙을 적용받는다. 기존 Jackson의 Java Time 지원을 그대로 사용해 시간대 없는 ISO 문자열을 반환하며 DATETIME(6)의 마이크로초도 보존한다. 필드 이름에 의존하지 않는다.

M2의 `submittedAt`, `completedAt`, 이력 `at`, 알림 `createdAt`도 한국 지역 시각을 나타내는 `LocalDateTime`으로 응답한다. JDBC 조회는 이 공통 경계를 사용하고 DTO도 `LocalDateTime`을 유지한다. 개별 서비스에서 Timestamp를 다시 만들거나 오프셋을 붙이지 않는다. M2 API 자체의 응답 검증은 해당 API 구현 때 추가한다.

## 부작용·남은 위험
- `SqlStore` 결과를 소비하는 코드가 JDBC 날짜 타입으로 강제 변환하면 수정이 필요하다. 현재 운영 코드에서는 해당 강제 변환이 없음을 검색으로 확인했다.
- 이 규칙은 한국 지역 시각을 저장하는 현재 DATETIME/DATE 계약을 전제로 한다. 향후 실제 순간을 저장하는 TIMESTAMP/Instant가 필요하면 별도 타입 계약을 정해야 한다.
- 시간대 없는 문자열은 자체적으로 Asia/Seoul 정보를 담지 않는다. 클라이언트도 명세의 한국 시각 계약을 따라야 한다.
- 데이터 저장값·스키마는 변경하지 않는다. 로컬 개발 DB에는 접속하지 않았다.

## 결과
수정 전 신규 테스트 2개 모두 실패했다(오류 0, 건너뜀 0).
- API: 기대 `2026-04-06T21:07:29.213456`, 실제 `2026-04-06T21:07:29.213+00:00`.
- 공통 조회: 기대 `LocalDateTime`, 실제 `java.sql.Timestamp`.

수정 후 Java 17.0.16 + Docker, UTC JVM에서 아래 명령으로 관련 테스트를 실행했다.

```sh
./mvnw -Duser.timezone=UTC -Dtest=SqlStoreDateTimeMariaDbTest,AuditClockMariaDbTest,M1AcceptanceTest test
```

| 테스트 클래스 | 실행 | 성공 | 실패·오류 | 건너뜀 |
| --- | ---: | ---: | ---: | ---: |
| SqlStoreDateTimeMariaDbTest | 2 | 2 | 0 | 0 |
| AuditClockMariaDbTest | 1 | 1 | 0 | 0 |
| M1AcceptanceTest | 37 | 37 | 0 | 0 |
| 합계 | 40 | 40 | 0 | 0 |

BUILD SUCCESS. 신규 회귀가 같은 DB 시각의 시간대 없는 문자열, DATE, NULL 및 마이크로초 보존을 확인한다. 이 실행은 관련 테스트 40개이며 전체 테스트 실행 결과로 표시하지 않는다. 검증에는 Testcontainers만 사용했고 로컬 개발 DB, frontend, backend/md, V1·V2, .claude/는 변경하지 않았다. 새 라이브러리·스키마 변경도 없다.

변경 파일: SqlStore.java, SqlStoreDateTimeMariaDbTest.java, docs/known-issues.md, 이 문서. M2 기능 작업에 앞선 독립 커밋으로 기록한다.
