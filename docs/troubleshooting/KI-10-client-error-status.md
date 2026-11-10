# KI-10 — 클라이언트 입력 오류의 500 응답 수정

## 증상

숫자·날짜·enum 입력 오류, 필수 쿼리 누락, 지원하지 않는 HTTP 메서드·본문 형식이 500으로 기록돼 실제 서버 장애와 구분되지 않았다. 잘못된 JSON은 400이지만 파서 문구와 Java 타입을 응답했다.

## 재현 방법과 수정 전 실패

- 시작: 깨끗한 `refactor/backend-readability` 작업 트리를 확인하고 `develop(5956bd0)`에서 `fix/client-error-status` 생성.
- 운영 코드 수정 전에 `ClientErrorStatusTest`를 추가했다. 관리소장 JWT로 실제 컨트롤러를 호출하며, 인증 없음과 제거된 경로는 별도 계약으로 확인한다.
- `git archive HEAD backend docs/refactoring`로 작업 트리 밖 사본을 만들고 새 테스트만 복사했다. Java 17.0.16, UTC JVM, MariaDB 10.11 Testcontainers 사용.

```bash
JAVA_HOME=/Users/kimmini/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home \
  ./mvnw -Duser.timezone=UTC -Dtest=ClientErrorStatusTest test
```

수정 전 실제 출력(프로세스 종료 코드 1):

```text
Tests run: 18, Failures: 12, Errors: 0, Skipped: 0, Time elapsed: 55.07 s
Response status expected:<400> but was:<500>  # 타입 오류 7건 + 필수 쿼리 누락 1건
Response status expected:<405> but was:<500>
Response status expected:<415> but was:<500>
JSON path "$.error.message" expected:<잘못된 요청 값입니다.> but was:<JSON parse error: Unexpected end-of-input: ...>
JSON path "$.error.message" expected:<잘못된 요청 값입니다.> but was:<JSON parse error: Cannot deserialize value of type `java.lang.Long` from String "not-a-number": not a valid `java.lang.Long` value>
BUILD FAILURE
```

긴 파서 메시지의 첫 사례만 `...`로 줄였다. 상태 코드·원문 일부는 새 테스트의 `CLIENT_ERROR` 출력과 Surefire 실패 결과를 따랐다. 6건(미인증 2, 제거 경로 4)은 수정 전에도 통과했다.

## 원인

- `MethodArgumentTypeMismatchException`·`MissingServletRequestParameterException`·`HttpMediaTypeNotSupportedException`의 전용 처리 없이 catch-all이 500을 반환했다.
- `handleRemovedBoardMethod`가 삭제된 v1 메서드 이외의 불일치를 catch-all에 넘겼다.
- `HttpMessageNotReadableException`을 검증 문구 추출 경로에서 처리해 `getMessage()` 원문을 사용자에게 보냈다.

## 검토했지만 버린 방법과 이유

- `handleBadRequest` 예외 목록에 타입 오류·필수 누락을 추가: `extractValidationMessage`가 스프링 내부 문구를 노출하므로 제외했다.
- 모든 `IllegalArgumentException`·검증 메시지를 기본 문구로 바꾸기: 기존 업무 검증 문구를 유지하라는 범위에 어긋나므로 제외했다.
- 메서드 불일치를 일괄 405로 바꾸기: 제거된 v1 경로의 404 계약이 깨지므로 제외했다.

## 선택한 방법

- 타입 오류·필수 쿼리 누락·JSON 읽기 오류만 별도 처리기로 묶어 `INVALID_INPUT_VALUE`의 기본 문구를 반환한다.
- `METHOD_NOT_ALLOWED`(405), `UNSUPPORTED_MEDIA_TYPE`(415)만 추가했다. 공통 `success/error/timestamp` 형식은 유지한다.
- v1 제거 경로는 기존 조건을 먼저 검사해 404를 유지한다.
- `IllegalArgumentException`·`ConstraintViolationException`과 나머지 검증 처리, catch-all 500은 그대로다.
- 요청받은 `AttendanceDocumentResponseDto`의 연속 빈 줄만 함께 정리했다.

## 전후 결과

| 요청/조건 | 수정 전 | 수정 후 |
| --- | --- | --- |
| `GET /users?page=x` (int) | 500 | 400, 기본 문구 |
| `GET /users?departmentId=x` (Long) | 500 | 400, 기본 문구 |
| `GET /users/x` (경로 long) | 500 | 400, 기본 문구 |
| `GET /users?includeSubDepartments=x` (boolean) | 500 | 400, 기본 문구 |
| `GET /users/login-id-preview?hireDate=not-a-date` | 500 | 400, 기본 문구 |
| `GET /attendance/records/1/not-a-date` | 500 | 400, 기본 문구 |
| `GET /projects?status=UNKNOWN` (enum) | 500 | 400, 기본 문구 |
| `GET /calendar/events?year=2026` (`month` 누락) | 500 | 400, 기본 문구 |
| `POST /home` | 500 | 405, METHOD_NOT_ALLOWED |
| `POST /projects`, `Content-Type: text/plain` | 500 | 415, UNSUPPORTED_MEDIA_TYPE |
| `POST /projects`, JSON `{` / `{"leaderId":"not-a-number"}` | 400, 내부 파싱 문구 | 400, 기본 문구 |
| 인증 없이 잘못된 쿼리·필수 쿼리 누락 (2건) | 401 | 401 |
| 제거된 `PUT /projects/1`, `PUT /tasks/1`, `GET·POST /projects/1/members` (4건) | 404 | 404 |
| 알림 저장 실패 롤백 (기존 3건) | 500 계약 | 500 계약 유지 |

경로의 공통 접두어는 `/api/v1`이다. 위 400 응답 모두 `잘못된 요청 값입니다.`이며 예외 클래스 이름·`Failed to convert`·파싱 문구·Java 클래스 이름이 없음을 테스트한다. 처리된 예외의 실제 타입도 확인한다.

## 검증 결과

- 수정 후 신규 테스트: **18개 성공 / 실패 0 / 오류 0 / 건너뜀 0**.
- 전체 UTC 실행: **249개 성공 / 실패 0 / 오류 0 / 건너뜀 0** (기존 231 + 신규 18, 61개 테스트 suite). 프로세스 종료 코드 0, `BUILD SUCCESS`.
- 의도된 알림 저장 실패 500·롤백 검사 3개 모두 통과.
- 기록 기본 OFF, `REFACTORING_RECORDED` 출력 0건.
- 외부 검증 사본의 변경 소스 6개(새 테스트 포함)가 작업 트리 파일과 바이트 단위로 같은지 확인했다. 기존 테스트 소스 79개는 수정하지 않았다.

```text
[INFO] Tests run: 249, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

의도한 500과 롤백을 검사하는 기존 테스트는 수정하지 않았다:

- `M2DocumentIntegrationTest.notificationInsertFailureRollsBackDocumentLineHistoryAndLeaveLedger`
- `M3AttendanceFixTest.notificationFailureRollsBackCorrectionAndRepeatApprovalCreatesNothing`
- `M4BoardApiTest.notificationFailureRollsBackTaskSequenceHistoryAndAssignment`

전체 실행 명령(외부 사본의 backend에서):

```bash
JAVA_HOME=/Users/kimmini/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home \
  ./mvnw -Duser.timezone=UTC test
```

기준선 3개(`sql.json`, `responses.json`, `masked-fields.txt`) 바이트는 시작점과 동일하며 기록 모드 실행·비교는 하지 않았다. 기존 테스트를 바꾸지 않았고, 추가 테스트는 18건이다.

## 부작용·남은 위험 / 확인하지 못한 것

- 이번 변경은 지정한 예외의 상태 코드와 문구만 다룬다. 모든 잘못된 요청이 반드시 4xx라는 보장은 하지 않는다.
- `IllegalArgumentException`·`ConstraintViolationException` 문구는 요청대로 보존했다. 코드 확인상 `AttendanceAdminService.csvLabel`의 예상하지 못한 코드에는 `Unknown attendance code: ...`가 있다. 해당 경로가 실행되면 내부 코드가 그대로 응답될 수 있지만, 정상 요청으로 그 상태를 재현한 것은 아니며 수정하지 않았다. `ConstraintViolationException`의 실제 내부 정보 노출 사례는 이번 검증에서 확인하지 못했다.
- HTTP 메서드와 입력에 대한 인증·권한 정책, 포트폴리오 재현 항목, 스키마·의존성·기록 정책은 변경하지 않았다.
- 개발 DB/Redis, 실제 서버, 배포는 검증하지 않았고 호출하지 않았다. 공유 원본 폴더의 브랜치 변경·develop 병합·푸시도 하지 않았다.

## 커밋

`fix(api): 클라이언트 입력 오류의 500 응답 수정`. 이 문서의 최초 커밋은 `git log --diff-filter=A --format='%h %s' -- docs/troubleshooting/KI-10-client-error-status.md`로 확인한다.
