# M0-3 최초 관리자 생성

## 증상
빈 DB에서는 ADMIN 사용자가 없어 설치 후 DB를 직접 수정해야 한다.

## 재현 방법
빈 DB로 앱을 기동하면 users 테이블에 관리자가 없다. 변경 후에는 README의 INITIAL_ADMIN 환경변수 네 개를 설정하고 실행하면 자동 생성되며, 하나라도 생략하면 생성하지 않고 경고만 남긴다.

## 원인
애플리케이션 기동 시 관리자 존재 확인 및 초기 생성 절차가 없었다. 기존 User.create는 USER 역할과 직급별 초기 연차를 설정하므로 그대로 사용하면 이번 초기 계정 조건에 맞지 않는다.

## 검토했지만 버린 방법과 이유
- Flyway에 관리자 INSERT 및 고정 비밀번호 작성: 비밀번호가 저장소에 남고 환경별 주입과 기존 암호화 정책 적용이 어렵다.
- 기존 회원가입 흐름 변경: M0 범위를 넘어 일반 사용자 생성 동작까지 바꾼다.
- 같은 아이디의 일반 사용자를 관리자 승격: 잘못된 계정의 권한 상승 및 비밀번호 덮어쓰기 위험이 있다.
- Redis 잠금 도입: 사용 범위와 라이브러리 제약에 맞지 않고 단일 인스턴스 초기 설치에 필요하지 않다.

## 선택한 방법
`domain/user/service/InitialAdminInitializer`가 ApplicationRunner로 실행되며 트랜잭션 안에서 ADMIN 존재 여부를 먼저 확인한다. 존재하면 환경값조차 읽지 않고 반환한다. 없다면 Spring Environment에서 네 INITIAL_ADMIN 값을 읽어 누락·공백을 확인한다. 누락 시 값이 포함되지 않은 경고만 기록한다.

유효한 입력은 기존 PasswordEncoder(BCrypt)로 암호화하고 전용 `User.createInitialAdmin`으로 생성한다. 역할 ADMIN, 직급 관리소장, `DepartmentCode.values()[0].getCode()`, 총·사용·잔여 연차 0을 지정한다. 아이디·이메일 충돌은 기존 BusinessException/ErrorCode를 사용한다. 로그인 응답·JWT·Redis는 변경하지 않았다.

테스트 비밀번호도 실행 시 임의로 생성하며 상수 비밀번호를 추가하지 않았다. MariaDB 공통 베이스에서 개발자 환경의 초기 관리자 변수를 빈 테스트 속성으로 덮어쓰고, 초기 계정 전용 테스트에서만 동적 속성으로 주입한다.

## 부작용·남은 위험
- ADMIN이 없고 환경변수도 없으면 앱은 실행되지만 관리자 로그인은 불가능하다. 경고 확인 후 환경변수를 지정하고 다시 시작해야 한다.
- 이미 사용 중인 아이디·이메일은 기동 오류로 처리한다. 사용하지 않는 초기 계정 값을 지정해야 한다.
- ADMIN 존재 확인 후 삽입은 여러 앱 인스턴스의 동시 최초 기동을 직렬화하지 않는다. 동일 아이디·이메일은 유니크 제약으로 중복을 막지만 서로 다른 설정으로 동시에 시작하면 여러 관리자가 생길 수 있다. 최초 설치는 한 인스턴스로 실행한다.
- 초기 비밀번호는 최초 생성 후 환경에서 제거할 수 있다. 이 기능은 비밀번호 재설정 수단이 아니다.
- 기존 일반 사용자 생성·연차 정책·인증 흐름은 그대로 유지했다. 별도 비밀번호 정책 확대나 첫 로그인 강제 변경은 이번 범위가 아니다.

## 결과
2026-10-12 Java 17 검증 결과:

- `InitialAdminInitializerTest`: 12개 통과. 기존 ADMIN 시 무동작, 네 값 각각의 누락/공백(8개), 암호화와 필수 필드, 아이디·이메일 충돌을 확인했다. 초기 비밀번호가 생성·누락 처리 로그에 포함되지 않음을 확인했다.
- `InitialAdminMariaDbTest.startupCreatesUsableAdminAndRepeatedStartupCallbackDoesNotDuplicate`: 실제 MariaDB 10.11에서 기동 시 생성, 필수 컬럼·연차 0, BCrypt 일치, 기존 AuthService의 JWT 로그인, 초기화 재호출 시 동일 ID·해시 유지와 관리자 1명을 확인했다.
- `MariaDbMigrationTest`: 3개 재통과. 관리자 환경값을 비운 상태에서 초기 스키마 및 사용자 0건 유지 확인.
- `InitialAdminProcessRestartSmoke` 수동 검증: 별도 임시 MariaDB에서 서버 프로세스를 실제로 종료·재기동했다. 첫 실행은 환경변수로 생성, 두 번째는 네 환경변수 모두 제거 후 실행했다. ADMIN 1명, 동일 ID·비밀번호 해시, 연차 0 유지 및 양쪽 로그에 초기 비밀번호가 없음을 확인했다. 임시 앱과 컨테이너를 정리했다.
- 최종 `./mvnw test`: **104개 중 97개 성공, 기존 실패 6개·오류 1개, 건너뜀 0개**. 새 테스트 16개는 모두 통과했고 변경 전의 7개 실패 목록은 동일하다.
- Docker 접근 차단 환경의 M0 테스트: 16개 중 단위 테스트 12개 통과, MariaDB 테스트 4개 건너뜀. 전체 기존 H2·단위 테스트의 Docker 부재 결과와 샌드박스 실행 조건은 M0-2 문서 참조.

### 변경 파일
- `backend/src/main/java/com/minierp/backend/domain/user/entity/User.java`
- `backend/src/main/java/com/minierp/backend/domain/user/repository/UserRepository.java`
- `backend/src/main/java/com/minierp/backend/domain/user/service/InitialAdminInitializer.java`
- `backend/src/test/java/com/minierp/backend/domain/user/service/InitialAdminInitializerTest.java`
- `backend/src/test/java/com/minierp/backend/integration/InitialAdminMariaDbTest.java`
- `backend/src/test/java/com/minierp/backend/support/MariaDbIntegrationTest.java`
- `README.md`, 이 문서.

### 미완료 및 범위 밖 문제
기존 H2·단위 테스트 전부 통과는 변경 전부터 존재한 7개 실패 때문에 달성하지 못했다. 기존 테스트·업무 로직을 고치지 말라는 범위를 지켰으며, 구체적인 실패 이름과 연차·특근 접근 범위/동시성 위험은 `M0-2-mariadb-tests.md`에 정리했다. 다음 단계 설계 문서와 frontend는 수정하지 않았다.
