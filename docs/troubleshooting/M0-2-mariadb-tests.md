# M0-2 MariaDB 테스트 환경

## 증상
H2 테스트만으로 MariaDB 전용 마이그레이션 및 향후 잠금 동작을 검증할 수 없다. 기존 H2 통합 프로필은 Flyway를 끄고 Hibernate create-drop을 사용한다.

## 재현 방법
`cd backend && ./mvnw -Dtest=CoreApiIntegrationTest test`를 실행하면 H2만 사용한다. 새 테스트는 Docker 실행 상태에서 `./mvnw -Dtest=MariaDbMigrationTest test`로 재현한다.

## 원인
기존 테스트 설정이 기본적으로 영속성 자동 구성을 제외하고, H2 integration 프로필에서만 다시 활성화한다. 실제 MariaDB 전용 공통 환경이 없었다.

## 검토했지만 버린 방법과 이유
- H2 MySQL 모드로 계속 대체: Flyway의 실제 MariaDB 실행과 잠금 의미가 다르다.
- 개발 DB 공유: 데이터 손상 및 테스트 간 상태 간섭 가능성이 있다.
- Docker 없는 환경에서 전체 테스트 중단: 기존 단위·H2 테스트까지 막는다.
- Docker를 찾은 뒤 발생한 모든 오류도 skip: 마이그레이션 결함을 감출 수 있다.

## 선택한 방법
Spring Boot BOM이 관리하는 Testcontainers 1.21.4의 junit-jupiter와 mariadb만 test scope로 추가했다. MariaDB 10.11 컨테이너, 동적 접속 정보, `mariadb` 프로필, `ddl-auto=validate`, Flyway 활성화를 공통 추상 클래스 `MariaDbIntegrationTest`에 묶었다.

`@Testcontainers(disabledWithoutDocker = true)`는 Docker 부재 때만 건너뛴다. 클래스별 컨테이너 수명과 `@DirtiesContext(AFTER_CLASS)`를 맞춰 중단된 컨테이너의 연결이 캐시된 컨텍스트에 남지 않게 했다. 상속하는 클래스는 순차 실행한다. 기존 H2 프로필·테스트는 수정하지 않았다.

참고: [Testcontainers JUnit 5](https://java.testcontainers.org/test_framework_integration/junit_5/), [MariaDB 모듈](https://java.testcontainers.org/modules/databases/mariadb/).

## 부작용·남은 위험
- Docker와 첫 이미지 다운로드가 필요하다. Docker가 없으면 실제 스키마 검증 결과를 얻은 것이 아니다.
- 공통 컨테이너를 상속하므로 클래스 병렬 실행은 지원하지 않는다. 클래스 안의 테스트는 필요에 따라 데이터 롤백·정리를 해야 한다.
- MariaDB 10.11 태그의 패치 버전은 이미지 갱신에 따라 바뀔 수 있다.
- 이번에는 테스트 환경만 준비했으며 연차 동시 승인 재현·수정은 수행하지 않았다.
- 기존 테스트가 변경 전부터 실패하므로 전체 테스트 통과 조건은 충족하지 못했다. 범위 밖 버그를 고치지 말라는 요청에 따라 아래 문제를 그대로 남겼다.

## 결과
2026-10-12 검증:

| 실행 | 총 테스트 | 성공 | 실패 | 오류 | 건너뜀 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 변경 전 전체 테스트 (기본 Java 22) | 88 | 81 | 6 | 1 | 0 |
| M0-2 전체 테스트 (Java 17, Docker 사용) | 91 | 84 | 6 | 1 | 0 |
| M0-2 전체 테스트 (Java 17, Docker 접근 차단) | 91 | 81 | 6 | 1 | 3 |

Docker 접근 차단은 추적된 backend 파일과 새 테스트만 임시 디렉터리에 복사해 Docker 소켓 접근이 금지된 샌드박스에서 실행했다. 로컬 비밀 설정은 복사하지 않았다. 이 샌드박스에서는 Mockito 자체 attach도 차단되어, 이미 존재하는 Byte Buddy agent 1.17.8을 실행 옵션 `-DargLine=-javaagent:<로컬 Maven 저장소의 byte-buddy-agent-1.17.8.jar>`로 미리 로드했다. 저장소 설정·의존성은 변경하지 않았다. 그 결과 기존 88개가 모두 실행되었고 동일한 7개만 실패했다.

`MariaDbMigrationTest` 3개 모두 실제 MariaDB에서 통과:
- `emptyDatabaseRunsFlywayAndStartsValidatedContext`: 10.11 버전, V1 이력, 빈 사용자 테이블, 엔티티 검증 및 업무 테이블 8개 확인.
- `schemaHasAllForeignKeysAndUniqueConstraints`: FK 11개, 명명된 유니크 제약 5개, 필수 부서 컬럼 확인.
- `repeatedMigrationDoesNotRecreateSchema`: 두 번째 migrate의 적용 건수 0 확인.

변경 파일: `backend/pom.xml`, `backend/src/test/resources/application-mariadb.properties`, 공통 베이스, `MariaDbMigrationTest`, README, 이 문서.

### 발견했지만 수정하지 않은 문제
1. `CoreApiIntegrationTest`의 연차 날짜가 2026-04로 고정되어 현재 시점의 과거 날짜 검증에 걸린다. `approveLeave_shouldReturnApprovedStatus`, `calendar_shouldContainLeaveAndOvertimeEvents`, `cancelLeave_shouldSucceedForOwnPendingRequest`, `createHalfDayLeave_shouldNormalizeEndDate`, `createLeave_sameDateDuplicateShouldFail` 5개가 201 대신 400으로 실패한다.
2. `approveOvertime_shouldReturnApprovedStatus`는 overtimeDate를 전송하지만 `OvertimeRequestDto`는 startDate/endDate를 기대한다. 날짜 필수 검증 없이 `OvertimeService`가 null 날짜를 역참조해 500을 반환한다. 기존 테스트에서 재현됐다.
3. `TaskTest.isOverdue_whenTaskIsPastDueAndNotDone_returnsTrue`는 어제 날짜의 업무를 생성하지만 프로젝트 종료일은 2026-04-30으로 고정되어 기간 검증에서 실패한다.
4. 연차·특근 전체 조회가 `findAll()`을 사용하고 `AccessPolicy`의 팀장 판정에는 부서 조건이 없다. 다른 부서의 신청 조회·처리 가능성이 남아 있다(코드 확인, 이번 작업에서 별도 요청 재현은 하지 않음).
5. 연차 승인에서 신청과 사용자를 읽고 잔여 연차를 변경하지만 잠금·버전 검증이 없다. 동시 승인 시 유실 갱신 가능성은 다음 단계에서 실제 MariaDB로 재현해야 한다(현재는 코드상 위험).
