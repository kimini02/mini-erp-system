# KI-11 — 처리되지 않은 서버 오류 로그 누락 (해결됨)

## 증상

catch-all은 500 응답만 반환하고 예외를 기록하지 않았다. 결재 중 알림 저장을 실패시켜도 서버 오류의 요청 경로와 원인 스택을 이 처리기의 로그에서 찾을 수 없었다. 클라이언트의 잘못된 요청과 실제 서버 실패를 구분해 조사할 근거가 부족했다.

## 재현 방법과 수정 전 결과

기준 `9c82fab`의 외부 archive에 `ServerErrorLoggingTest`를 추가하고 Java 17·UTC·Testcontainers로 실행했다. 기존 알림 저장 실패 롤백 테스트와 같이 `NotificationService.store`가 실제 저장한 뒤 `IllegalStateException("injected logging notification storage failure")`을 던지게 했다. 인증된 승인 요청에는 쿼리·본문·추가 헤더의 식별용 문자열과 테스트용 JWT를 함께 전달했다.

Logback의 기존 `ListAppender`로 `GlobalExceptionHandler`의 이벤트를 수집한다. 실제 토큰이나 요청 덤프는 이 문서에 넣지 않는다.

```text
SERVER_LOG_CAPTURE server_events=0 client_error_events=0
Expected size: 1 but was: 0 in: []
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
```

500 응답 자체와 이어서 보낸 `page=x`의 400은 수정 전에도 맞았다. 실패 원인은 서버 로그가 0건이라는 단언이다. 같은 실행의 `ClientErrorStatusTest` 3건도 실패해, 대상 21건 중 실패는 4건이었다.

### 테스트 단언 보완

첫 수정 후 실행에서는 405·406·페이지 20건은 모두 통과했으나 로그 테스트 1건이 실패했다. 스택의 `org.springframework.security.web.access.intercept.AuthorizationFilter` 이름에 포함된 `Authorization`까지 노출로 오인한 것이었다. 스택을 없애지 않고, 로그 문구에는 헤더 이름이 없으며 문구+스택에는 `Authorization:`·`Authorization=`·`Bearer `·실제 테스트 토큰·요청 식별값이 없는지 검사하도록 단언을 좁혔다. 이 최종 테스트를 수정 전 코드에서 다시 실행해 로그 0건으로 실패함을 재확인했다.

## 원인

`GlobalExceptionHandler.handleException`에 로거 호출이 없었다. 한편 XML Accept의 406은 전용 처리가 없어 catch-all로 들어왔으므로 로그만 추가하면 클라이언트 요청까지 ERROR로 기록된다. 이를 함께 전용 빈 406 처리기로 분리했다.

## 선택한 방법과 남기는 항목·뺀 항목

catch-all에서만 ERROR 1건을 기록한다. 응답의 기존 `INTERNAL_SERVER_ERROR`와 일반 문구는 유지한다.

| 구분 | 내용 |
| --- | --- |
| 남김 | 요청 메서드, `getRequestURI()` 경로, 예외 타입·문구·스택 추적 |
| 뺌 | 쿼리 문자열, 요청 본문, 모든 요청 헤더(Authorization 포함), 인증 토큰 |
| 클라이언트 오류 | 기존 전용 처리기를 유지하며 ERROR를 추가하지 않음. 406은 빈 응답으로 전용 처리 |

예외는 로깅 인자의 마지막 Throwable로 넘겨 스택을 보존한다. `getRequestURI()`는 쿼리를 포함하지 않는다. 요청 객체 전체, URL+쿼리, 헤더 맵·본문을 직렬화하지 않는다.

검증에 쓴 실제 출력 형태(행 식별자만 자리 표시자로 바꿈):

```text
ERROR ... GlobalExceptionHandler : Unhandled server error method=POST path=/api/v1/approval-documents/<id>/approve
java.lang.IllegalStateException: injected logging notification storage failure
    ... NotificationService ...
SERVER_LOG_CAPTURE server_events=1 client_error_events=0
```

검토했지만 버린 방법:

- 모든 예외 처리기에서 ERROR 기록: 입력 오류를 서버 장애와 섞으므로 제외했다.
- 요청 전체를 출력하는 필터나 새 로그 수집 도구: 민감한 값 노출과 작업 범위를 늘리므로 제외했다.
- 메시지만 기록하고 스택 생략: 실패 위치와 호출 흐름을 알 수 없어 제외했다.

## 수정 후 검증

`logsServerFailureWithStackButNoRequestSecretsAndDoesNotLogBadRequestAsError`는 다음을 함께 확인한다.

- 실패 주입 응답은 500이고 문서는 IN_PROGRESS로 롤백된다.
- ERROR 이벤트는 정확히 1개이며 POST·경로·예외 문구·예외 클래스·비어 있지 않은 스택이 있다.
- 이벤트 문구와 스택을 합친 문자열에 테스트 JWT, Authorization 헤더 표현·Bearer 토큰, 쿼리, 본문, 추가 헤더 값이 없다.
- 이어서 보낸 400 요청에는 ERROR 이벤트가 0개다.
- XML Accept의 406은 별도 `ClientErrorStatusTest`에서 빈 본문과 예외 처리 경고·ERROR 부재를 확인한다.

검증은 작업 트리 밖 `git archive HEAD backend docs/refactoring` 사본에 이번 변경 파일 5개를 덮어쓴 뒤 수행했다. 운영·테스트 소스는 검증 사본과 작업 트리의 SHA-256을 대조했다. Java 17.0.16, UTC, MariaDB 10.11 Testcontainers를 사용했다.

- 전체 **252개 성공 / 실패 0 / 오류 0 / 건너뜀 0** (62개 suite, Maven 표시 시간 14분 38초). 기존 249개 + 신규 3개이며 기존 405 검사는 Allow 단언을 보강했다.
- 대상 `ClientErrorStatusTest` 20개, `ServerErrorLoggingTest` 1개 모두 성공.
- 알림 저장 실패 롤백의 기존 3개 테스트(M2 문서·M3 근태 정정·M4 업무)도 변경 없이 성공.
- 기록 기본 OFF, `REFACTORING_RECORDED` 출력 0건. 기준선 3개 바이트 동일.

```text
Tests run: 252, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
ROLE_MEMBER_LARGE_PAGE status=200
SERVER_LOG_CAPTURE server_events=1 client_error_events=0
```

실행 명령(외부 사본의 backend에서):

```bash
JAVA_HOME=/Users/kimmini/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home \
  ./mvnw -Duser.timezone=UTC test
```

수정 전 대상 실행에는 같은 명령에 `-Dtest=ClientErrorStatusTest,ServerErrorLoggingTest`를 추가했다. 로그 단언 보완 뒤 수정 전 재실행은 `-Dtest=ServerErrorLoggingTest`로 수행했으며 1개 실패(로그 0건)를 다시 확인했다.

## 부작용·남은 위험 / 확인하지 못한 것

- 예상하지 못한 500은 이제 스택을 포함하므로 실제 서버 오류가 반복되면 로그량이 늘어난다. 로그 설정이나 수집 도구는 바꾸지 않았다.
- 이 테스트는 요청의 민감한 값이 처리기의 문구·해당 예외 스택에 들어가지 않는 것을 검증한다. 임의의 발생처가 예외 문구 자체에 비밀값을 넣는 상황의 범용 마스킹까지 구현한 것은 아니다. 예외에는 비밀값을 넣지 않는 기존 원칙을 유지해야 한다.
- 의도된 500·롤백 검사 3개(M2DocumentIntegrationTest, M3AttendanceFixTest, M4BoardApiTest)는 변경 없이 전체 실행에서 통과했다.
- 개발 DB/Redis·실제 서버·배포는 호출하거나 검증하지 않았다. 병합·푸시는 하지 않았다.

## 커밋

`fix(api): 405 Allow 헤더·서버 오류 로그·역할 멤버 페이지 계산`. 문서·코드·테스트를 같은 커밋에 담았으며 `git log --diff-filter=A --format='%h %s' -- docs/troubleshooting/KI-11-server-error-logging.md`로 해시를 확인한다.
