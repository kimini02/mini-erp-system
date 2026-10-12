# M0-1 빈 DB 초기 스키마

## 증상
기존 V1은 주석뿐이고 V2부터 기존 테이블을 변경한다. 빈 DB에서 Flyway가 먼저 실행되면 테이블 부재로 실패하며, `ddl-auto=update`와 Flyway가 스키마 소유권을 나눠 갖는다.

## 재현 방법
이전 브랜치의 설정으로 테이블이 없는 MariaDB를 연결하고 앱을 실행한다. V2가 존재하지 않는 `leave_requests`를 변경하려 한다. 사용자 DB는 재현에 사용하지 않았다.

## 원인
기존 DB에 baseline을 찍는 전제로 마이그레이션을 작성했지만 신규 설치용 DDL이 없었다. 현재 엔티티는 BaseEntity를 포함한 8개 테이블을 필요로 한다.

## 검토했지만 버린 방법과 이유
- Hibernate update로 먼저 테이블 생성: 실행 순서 문제를 남기고 스키마 변경의 이중 소유를 유지한다.
- V6에 초기 생성문 추가: 빈 DB는 V2에서 이미 실패한다.
- 기존 DB에 repair/baseline 강제 적용: 새로운 V1이 실행되었다는 보장이 없어 불일치를 숨긴다.

## 선택한 방법
V1~V5를 신규 설치용 `V1__init.sql`로 통합했다. 현재 엔티티의 컬럼 길이·정밀도·필수 여부·외래키 11개·유니크 제약 5개를 반영했다. 기본키·유니크·보조 인덱스마다 조회 근거를 주석으로 작성했다. 상태는 기존 V5 방침에 맞춰 VARCHAR로 유지하며 MariaDB/Hibernate validate 통과를 확인했다. JPA cascade를 DB cascade로 확대하지 않았다.

공통 설정과 로컬 예제에서 `ddl-auto=validate`, `baseline-on-migrate=false`를 사용한다. README에 기존 로컬 DB 백업 후 삭제·재생성 및 기존 로컬 설정 갱신 절차를 명시했다.

## 부작용·남은 위험
- 이전 Flyway 이력이 있는 DB에는 그대로 적용할 수 없다. 기존 운영 데이터 보존·전환 마이그레이션은 이번 범위가 아니다.
- 이미 존재하는 로컬 설정이나 환경변수가 공통 설정을 덮어쓸 수 있으므로 실행 전 validate 여부를 확인해야 한다.
- Hibernate validate는 모든 제약·인덱스를 검증하지 않는다. M0-2의 실제 MariaDB 메타데이터 검증을 함께 사용한다.
- 엔티티의 기존 연차 정책이나 업무 로직은 수정하지 않았다.

## 결과
2026-10-12, Java 17, 임시 `mariadb:10.11`에서 수동 `EmptyMariaDbStartupSmoke` 검증 통과:
1. 새로운 빈 DB와 임의 포트로 독립 컨테이너 기동.
2. `./mvnw -q clean spring-boot:run`을 별도 프로필과 임시 접속 정보로 실행.
3. Flyway V1 성공, Hibernate EntityManagerFactory 초기화 및 `Started BackendApplication` 확인.
4. 업무 테이블 8개와 `flyway_schema_history(version=1, script=V1__init.sql, success=1)` 확인.
5. 앱 종료 및 임시 컨테이너 삭제. 기존 개발 DB는 변경하지 않았다.

재실행 가능한 자동 검증은 다음 커밋의 `MariaDbMigrationTest`에서 제공한다. 변경 파일은 README, 공통·로컬 예제 설정, 마이그레이션(V1~V5 삭제/V1 init 추가), 이 문서이다.
