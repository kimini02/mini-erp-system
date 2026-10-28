# M5-01 — 운영 프로필, DB 계정 분리, health 노출

## 증상

로컬 실행 설정만으로 운영하면 스키마 변경 계정과 업무 실행 계정이 분리되지 않고, health와 API 문서의 노출 범위를 검증할 근거가 부족합니다. 운영 비밀값 누락도 명확하게 기동 단계에서 거부해야 합니다.

## 재현 방법

Java 17·UTC JVM, MariaDB 10.11 및 Redis 7.4 Testcontainers로 실행합니다. 로컬 개발 DB/Redis는 사용하지 않습니다.

```bash
cd backend
JAVA_HOME=/Users/kimmini/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home ./mvnw -Duser.timezone=UTC -Dtest=M5ProductionTest,M5ProdSecretsTest test
```

`M5ProductionTest`는 test 프로필을 섞지 않고 prod 프로필로 실제 HTTP 서버 두 개를 엽니다. 테스트에서만 무작위 포트를 사용합니다. 비밀번호·JWT 키는 실행 중 생성하고 출력하지 않습니다. 컨테이너 root는 계정 준비에만, Flyway는 migrator, 앱은 DML 계정으로 연결합니다.

## 원인

DDL 계정을 실행 계정으로 재사용하면 앱이 불필요한 스키마 변경 권한을 갖습니다. 별도 management 서버를 추가해도 기존 인증 체인이 health를 막거나 앱 포트에서 401을 반환할 수 있어 실제 HTTP 검증이 필요합니다.

## 검토했지만 버린 방법과 이유

- 앱 계정에 ALL 권한 부여: 업무 쓰기에 필요 없는 DDL까지 허용하므로 제외.
- H2 또는 health 모킹만 사용: MariaDB 1142 권한 오류와 실제 DB/Redis 단절을 검증하지 못하므로 제외.
- 기본 비밀값 제공: 설정 누락 상태로 운영 앱이 떠 버리므로 제외.
- actuator 전체 공개: 필요한 것은 health뿐이므로 제외.
- SMTP health 유지: 메일이 꺼진 기본 운영에서 SMTP 연결 때문에 DOWN이 되는 것을 피하려고 제외.

## 선택한 방법

- actuator starter만 추가. prod는 loopback 8080/8081, graceful 20초, Swagger 비활성, health만·상세 없음, CORS 빈 목록.
- Flyway용 계정을 별도 설정하고 Hibernate validate 유지. CORS는 로컬 기본값을 유지하며 속성화.
- prod 전용 비밀값 검사로 DB_APP_USERNAME/PASSWORD, DB_MIGRATOR_USERNAME/PASSWORD, JWT_SECRET의 누락·공백·CHANGE_ME를 외부 클라이언트 생성 전에 거부. 오류에는 키 이름만 기록.
- prod의 actuator/비활성 문서 경로는 인증을 통과시켜 실제 라우팅 결과를 반환. 관리 서버 health는 200, 앱 서버 actuator 및 비활성 경로는 404.
- TaskOverdueWork 및 M4-18의 옛 ODKU 설명만 현재 구현에 맞게 정리. 업무 SQL은 바꾸지 않음.

## 결과

2026-10-28, 전용 실행 **9/9 성공, 실패·오류·건너뜀 0**. 4개 운영 통합 테스트와 비밀값 누락 5개 매개변수 테스트입니다. 초기 테스트 작성 중 로그인 요청 필드와 상위 부서장 fixture를 보완했으며, 이를 제품 버그 수정 전 재현으로 주장하지 않습니다.

안전한 출력 원문:

```text
M5_DDL sql=DROP TABLE tasks code=1142 state=42000
M5_DDL sql=ALTER TABLE tasks ADD COLUMN forbidden_m5 INT code=1142 state=42000
M5_DDL sql=CREATE TABLE forbidden_m5(id INT) code=1142 state=42000
M5_DB migrations=6 installed_by=workflow_migrator runtime=workflow_app writes=login,submit,approve,clock-in,move,overdue SUCCESS
M5_HEALTH management=200 body={"status":"UP"} app_actuator=404 swagger=404 api_docs=404 other_management=404 cors_allow_origin=absent
M5_HEALTH redis_paused=503 body={"status":"DOWN"}
M5_HEALTH db_stopped=503 body={"status":"DOWN"} db_contributor=DOWN
M5_PROD missing=DB_APP_USERNAME startup=REJECTED before_external_connections
M5_PROD missing=DB_APP_PASSWORD startup=REJECTED before_external_connections
M5_PROD missing=DB_MIGRATOR_USERNAME startup=REJECTED before_external_connections
M5_PROD missing=DB_MIGRATOR_PASSWORD startup=REJECTED before_external_connections
M5_PROD missing=JWT_SECRET startup=REJECTED before_external_connections
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
```

| 테스트 | 검증 |
|---|---|
| separatedAccountsMigrateAndPerformRepresentativeWritesButRejectDdl | V1~V6 migrator 실행, DML 계정 업무 쓰기 및 1142 세 종류 |
| healthIsAnonymousAndOnlyOnManagementPortWithNoDetailsOrCors | 무인증 UP, 비노출 경로 404, prod CORS 없음 |
| redisFailureReportsDownWithoutDetails | Redis pause → 503 DOWN → 복구 UP |
| dbFailureReportsDownWithoutDetails | DB 정지 → contributor DOWN 및 503 DOWN |
| missingSecretFailsBeforeOpeningDatabase × 5 | 각 필수 설정 누락 조기 실패 |

## 부작용·남은 위험

- 앱 프로세스는 기동 시 Flyway 비밀값도 보유합니다. DB 권한 분리는 일반 업무 연결의 DDL을 막지만 프로세스 전체 침해에 대한 완전한 계정 격리는 아닙니다.
- 실제 EC2의 보안 그룹·바인딩·HTTP 포트 번호는 서버 측정에서 별도로 확인합니다. 컨테이너 검증을 외부 노출 검증으로 대신하지 않습니다.
- Redis 단절 테스트는 상태 검사 확인이며, 운영 장애 시 업무별 복구 정책을 새로 구현한 것이 아닙니다.

## 커밋

이 문서와 함께 커밋한 운영 프로필 작업. 최종 해시는 `M5-04-phase-one-results.md`의 커밋 표에 기록합니다.
