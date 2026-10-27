# M4 1차 — 프로젝트·보드 결과

## 기준과 범위
- 작업 브랜치: `feat/m4-home-board`, 시작점 `develop e20da6b`.
- 읽은 명세: `git show 57cf2a8:backend/md/15-rest-api-v2-m4.md` 0·1·3·4·6·7장과 8장 측정 1·2·3·5·6. ERD는 같은 커밋의 10번 문서 4장. 공통 권한·응답·시계·트랜잭션 규칙은 12·13·14번 문서를 따른다.
- 1차 구현: V6, 프로젝트 메뉴 권한·SQL 범위, 프로젝트 CRUD 중 명세의 등록/조회/수정/멤버 변경, 보드·업무 등록/상세/수정/이동, 이력·배정 알림, 프로젝트 행 잠금·상태 집계·번호·version·간격 정렬.
- 2차 미구현: GET /home, 마감 알림 배치, 구 조회·프로젝트별 권한 API의 최종 삭제, 측정 4·7·8의 삭제 확인. 미완료가 아닌 사용자가 분리한 다음 범위다.

## 커밋과 변경 파일
모두 로컬 독립 커밋이며 병합·푸시는 하지 않았다. 아래 파일 경로는 저장소 루트 기준이다. 전체 변경 파일은 `git diff --name-only e20da6b..HEAD`로 확인할 수 있다.

| 커밋 | 작업 / 주요 변경 파일 | 테스트 | troubleshooting |
|---|---|---|---|
| `6fea420` | `backend/src/main/resources/db/migration/V6__project_board.sql`, `M4DataMigrationTest`, `MariaDbMigrationTest` | V5 데이터 이관, 빈 MariaDB 전체 Flyway와 validate | [M4-01](M4-01-schema-migration.md) |
| `ba520b5` | `M4LegacyExperimentTest`, `support/BoardTestSupport`, `support/JdbcProbe` | 수정 전 쓰기 왜곡·전체 목록 비용·전체 재번호/무잠금 이동 | [M4-02](M4-02-baseline-experiments.md) |
| `4b7b6d8` | `domain/project/controller/{ProjectBoardController,ProjectController}`, `dto/{BoardProjectRequests,ProjectUpdateRequestDto,ProjectPermissionUpdateRequestDto}`, `service/BoardProjectService`; `global/exception/{BusinessException,ErrorCode,GlobalExceptionHandler}`, `global/response/ErrorResponse` | M4BoardApiTest의 프로젝트·권한·쿼리 수 검증, M4LegacyWriteBridgeTest | [M4-03](M4-03-project-permissions.md), [M4-08](M4-08-legacy-write-bridge.md) |
| `fbbf0e2` | `domain/task/controller/{TaskBoardController,TaskController}`, `dto/{BoardTaskRequests,TaskCreateRequestDto,TaskUpdateRequestDto,TaskStatusUpdateDto}`, `service/BoardTaskService`; `domain/project/service/ProjectStateStore`, `domain/notification/service/NotificationService`; `M4BoardApiTest`, `M4BoardConcurrencyTest`, `M4LegacyWriteBridgeTest` | 잠금/집계/순서/version/번호/롤백/구 쓰기 연결 | [M4-04](M4-04-write-skew.md), [M4-05](M4-05-status-cost.md), [M4-06](M4-06-card-order.md), [M4-07](M4-07-task-numbers-and-rollback.md), [M4-08](M4-08-legacy-write-bridge.md) |

축약된 운영 코드 경로 앞에는 `backend/src/main/java/com/minierp/backend/`, 테스트 클래스 앞에는 `backend/src/test/java/com/minierp/backend/integration/`을 붙인다. support는 같은 테스트 루트의 support 패키지다. 문서 자체의 최종 정리 커밋은 이 표의 구현 커밋 이후이며 `git log`에서 확인한다.

## 전체 테스트
2026-10-27, Java 17.0.16 + Docker + MariaDB 10.11 Testcontainers. 두 Maven 종료 코드 0, BUILD SUCCESS와 모든 Surefire XML 합계를 대조했다.

| JVM | 전체 | 성공 | 실패 | 오류 | 건너뜀 | 소요 |
|---|---:|---:|---:|---:|---:|---|
| 기본 (M4 테스트에서 Asia/Seoul 확인) | 262 | 262 | 0 | 0 | 0 | 5분 44초 |
| UTC (`-Duser.timezone=UTC`) | 262 | 262 | 0 | 0 | 0 | 5분 39초 |

기존 241개 + M4 신규 21개다. 신규 구성: 이관 1, 수정 전 비교 3, API 9, 동시성·측정 4, 구 쓰기 연결 4. 비교 테스트의 성공은 의도한 기존 결함이 재현된다는 뜻이다.

- 수정 전 재현 실행: `M4LegacyExperimentTest` 3/3 성공(쓰기 왜곡 10/10, 순서 중복 20/20 관측). 운영 코드 수정 후에 새로 '과거 결과'를 만든 것이 아니다.
- 구 쓰기 연결 수정 전: `M4LegacyWriteBridgeTest` 4개 중 3개 실패, 1개 통과. 실제 500/권한 거절/version 누락 허용을 확인했다. 연결 후 같은 4개 모두 통과.
- 새 API 테스트 작성 중 알림 spy의 트랜잭션 프록시를 잘못 stub해 테스트 장치 오류 2개가 한 차례 있었다. AOP target spy로 바꿨으며 제품 결함 재현 수치에 포함하지 않았다.
- UTC 빌드는 `/private/tmp`의 별도 복사본에서 실행했다. pom/mvnw/.mvn/src의 **254개 파일 SHA-256**을 원본·복사본과 대조해 차이 0개였다. 소스는 전체 검증 중 변경하지 않았고, 이후 문서만 갱신했다. 임시 복사본은 검증 후 삭제했다.
- 로컬 실행 로그: `/private/tmp/m4-before.log`, `/private/tmp/m4-bridge-before.log`, `/private/tmp/m4-bridge-after.log`, `/private/tmp/m4-full-default.log`, `/private/tmp/m4-full-utc.log`. 로그 파일은 저장소 산출물이 아니며 재현 가능한 근거는 커밋한 테스트·문서다.

## 측정 표
모든 경합은 실제 스레드와 실제 MariaDB 트랜잭션이다. barrier로 취약한 실행 순서를 만들었으므로 재현율을 운영 사고 확률로 해석하면 안 된다.

| 측정 | 수정 전 | 수정 후 |
|---|---|---|
| 1. 마지막 업무 두 개 동시 완료 | 프로젝트 상태 불일치 10/10 | 0/10 (기본·UTC 동일) |
| 2. 1,000개 업무 상태 계산의 반환 행 | 1,000 | 2 (상태 2종), 프로젝트 잠금 행까지 총 3 |
| 2. SQL 수 | 3 = SELECT 1 + UPDATE 2 | 4 = 잠금 SELECT 추가, 나머지 SELECT 1 + UPDATE 2 |
| 2. 중앙값, 기본 JVM | 11.604 ms | 7.330 ms |
| 2. 중앙값, UTC JVM | 5.836 ms | 3.440 ms |
| 2. ANALYZE 실제 스캔 행 | 1,120 (테이블 스캔) | 1,000 (기존 상태 인덱스의 해당 프로젝트) |
| 3. 카드 100개 일반 이동 UPDATE 업무 행 | 100 | 1 |
| 3. 동시 이동 20쌍 위치 중복 합계 | 20 | 0 |
| 3. 재정렬 | 전체 재번호 매 이동 | 정상 실험 0회, 강제 간격 고갈 실험 1회·100행 UPDATE |
| 3. 오래된 version | 검사 없음 | 409, 순서·이력 변화 0, 최신 카드 포함 |
| 5. USER인 프로젝트 팀장의 구 배정 | 403 | 201, U 없이도 자기 프로젝트 관리 |
| 5. 비멤버/범위 밖/비담당 멤버 | 새 규칙은 v1에 없음 | 요구한 거절 모두 403, 쓰기 흔적 0 |
| 6. 구 등록 / 새 동시 번호 | V6 뒤 구 등록 500, v1 번호 기능 없음 | 구 등록 201; 새 동시 10개 번호 1..10, 중복·빈 번호 0 |
| 목록·보드 고정 쿼리 | 데이터 증가 전 | 프로젝트 20개 증가 후에도 목록 2; 카드 1→100에도 보드 3 |

측정 2는 같은 실행에서 전후를 번갈아 **각각 예열 3·측정 9 중앙값**이다. 개별 환경의 서로 다른 실행 시간을 섞어 속도 배수를 주장하지 않는다. 결과 행 감소는 99.8%지만 DB 내부 스캔이 2행이라는 뜻은 아니다. 쿼리 계획과 측정 경계는 M4-05에 상세히 적었다. 목록/보드 수는 기능 데이터 SQL이고 인증·권한까지 포함하면 6/7로 고정됐다.

## 명세를 구체화한 곳 / 결정 필요
1. **업무 알림의 document_id**: 명세의 TASK_ASSIGNED를 저장하려면 기존 NOT NULL을 풀어야 한다. V6에만 NULL 허용을 추가했고 결재 문서 FK는 유지했다. 업무 알림은 문서 id 없이 title/link로 연결한다.
2. **구 쓰기 연결**: 사용자 추가 승인대로 구 쓰기도 새 서비스에 연결했다. 구 상태 변경에도 version 필수. 구 입력 이름을 일부 유지하지만 응답은 새 모양이다. 삭제는 2차다.
3. **응답 세부 모양**: 이동 성공은 `data: {task, project}`, 충돌은 `error.details.currentTask`. 상세·등록·수정 프로젝트는 요약 필드에 content/members를 추가하고 조회 상세는 permissions도 포함한다. 명세에 wrapper가 정확히 지정되지 않은 부분을 구체화했으므로 프론트에서 이 모양으로 연결해야 한다.
4. **권한 조합**: 보기 범위를 가진다는 전제로 쓰기에도 PROJECT V를 먼저 검사한다. V 없는 U-only 역할/프로젝트 팀장 허용 여부는 명시되지 않았다. 기본 역할은 V가 있어 요구한 예외가 동작한다. 이 특수 조합이 필요하면 결정해야 한다.
5. **한시적 어댑터의 상태 이동**: 구 PATCH /tasks/{id}/status는 afterTaskId가 없으므로 목적지 열 맨 위로 이동한다. 새 화면은 /move를 사용한다.
6. **알림 링크**: TASK_ASSIGNED는 `/projects/{projectId}/board?taskId={taskId}`를 저장했다. 화면 경로는 명세에 고정돼 있지 않아 프론트의 보드 경로와 맞출 필요가 있다.
7. **재정렬 version**: 상대 순서는 같은 다른 카드의 물리적 position 변경에는 version을 늘리지 않는다. 직접 이동/수정된 카드만 증가한다.
8. **이관 키 길이**: 명세의 P{id}는 id ≥ 1,000,000,000이면 10자를 넘는다. 예외 매핑은 명세 밖이라 추가하지 않았다.

겹친 기존 경로는 GET/POST /api/v1/projects와 GET /api/v1/tasks/{id}이며 새 명세로 교체했다. 그 밖의 구 쓰기 연결 목록은 M4-08에 있다.

## 발견했지만 고치지 않은 항목
- 구 GET /tasks, /tasks/{id}/assignments, 프로젝트 멤버·progress·permissions 등의 읽기는 전역 v1 역할 규칙이다. PROJECT V를 회수하거나 범위를 줄여도 구 읽기는 새 메뉴 권한을 검사하지 않는다. 2차에 구 API를 삭제하기 전까지 남는 접근 경로이며, 새 API 권한 검증 통과가 전체 구 경로까지 안전하다는 의미는 아니다.
- 구 대시보드의 N+1과 홈 응답 구성은 2차 측정·구현 대상이라 손대지 않았다.
- 구 JPA 서비스의 쓰기 메서드는 새 HTTP 경로에서는 호출하지 않지만 코드에 남아 있다. 2차에서 구 API와 같이 정리해야 한다.
- 기존 `remainingAnnualLeave` 호환 필드의 여러 연도 합산 문제는 M3 지시대로 유지한다.

## 실행·이관
개발 DB에는 접속하지 않았다. 이관 검증은 빈 컨테이너 또는 V5까지 적용한 컨테이너에 기존 데이터 fixture를 넣어서만 했다. 실제 배포에서는 검토·백업 후 평소 애플리케이션 시작 절차로 Flyway V6을 실행하며, V1~V5의 체크섬은 변경하지 않는다. 빈 DB도 V1~V6 전체 실행과 Hibernate validate가 통과한다.

```sh
cd /Users/kimmini/sk-final-deepfake/mini-erp-system/backend
export JAVA_HOME=/Users/kimmini/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home
./mvnw test
./mvnw -Duser.timezone=UTC test
# 실험만 다시 실행
./mvnw -Dtest=M4LegacyExperimentTest,M4BoardConcurrencyTest test
# API·이관·구 쓰기 연결
./mvnw -Dtest=M4BoardApiTest,M4DataMigrationTest,M4LegacyWriteBridgeTest test
```

Docker가 실행 중이어야 건너뜀 없이 DB 실험이 동작한다. 테스트 데이터의 날짜는 주입된 한국 시계 기준이며 UTC JVM의 한국 날짜 경계(00:30), overdueDays와 시간대 표시 없는 이력 시각도 검증했다.

frontend/, backend/md/, V1~V5, .claude/는 변경·커밋하지 않았고 새 라이브러리도 추가하지 않았다. 개발 DB 조작·병합·푸시는 수행하지 않았다.
