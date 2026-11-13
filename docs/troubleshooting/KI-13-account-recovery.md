# KI-13 — 아이디·비밀번호 찾기 연결

## 증상과 원인

로그인 전 찾기 화면이 권한이 필요한 `/users` 조회·사원 수정 경로를 호출했다. 401이 세션 만료로 처리되어 로그인 화면으로 이동했고, 인증번호는 화면에서 고정 문자열과 직접 비교했다. 아이디 찾기 백엔드는 성공 시 loginId, 불일치 시 404를 반환했다.

## 수정과 계약 결정

**아이디 찾기 계약 변경: 직접 반환 → 이메일 발송, 계정 존재 여부 노출 방지.** 사용자 승인에 따른 명세 12와의 의도된 차이이며 backend/md는 수정하지 않았다.

| 요청 | 본문 | 결과 |
| --- | --- | --- |
| POST /auth/find-id/request | name, email | 일치/불일치 모두 200, data.message 동일, loginId 없음 |
| POST /auth/password/reset/request | email | 계정 유무와 무관하게 같은 안내 |
| POST /auth/password/reset/verify | email, verificationCode | 서버가 resetProof 발급 |
| POST /auth/password/reset/confirm | resetProof, newPassword, newPasswordConfirm | 서버 정책 검사 후 변경 |

- 아이디 일치 시 read-only 트랜잭션에서 이벤트를 발행하고 AFTER_COMMIT에서 전용 작업 스레드로 메일을 넘긴다. SMTP 대기·실패를 HTTP 응답에 연결하지 않는다. 롤백은 발송하지 않는다.
- 대안: AFTER_COMMIT에서 동기 발송만 하면 여전히 응답이 SMTP를 기다리므로 채택하지 않았다. 외부 메시지 브로커·새 Redis 용도는 추가하지 않았다. 기존 Spring 실행기로 스레드 1개·대기열 100개를 사용한다. 대기열 거절과 메일 실패는 개인정보·예외 원문 없이 일반 경고만 남긴다.
- 찾기 화면은 authApi를 통해 요청하며 사원 API를 사용하지 않는다. 인증번호의 일치·불일치는 서버에서 판단한다. 이메일 변경·재발송·증명 만료 시 흐름을 다시 시작할 수 있다. 요청 중 중복 제출을 막는다.
- 실제 초대 링크는 `?setupToken=…`인데 화면은 `token`만 읽던 불일치도 확인했다. 초대 화면이 setupToken을 우선하도록 맞추고 기존 token 링크를 호환한다.

## 수정 전 재현

작업 트리 밖 사본, Java 17, UTC, MariaDB Testcontainers. JavaMailSender는 가짜로 교체했다. 실제 메일/개발 DB/Redis 호출 없음.

```text
ProdMailConfigTest: Tests run: 24, Failures: 23, Errors: 0, Skipped: 0
FindIdRecoveryTest: Tests run: 5, Failures: 4, Errors: 0, Skipped: 0
matchingAndNonMatchingRequestsHaveIdenticalPublicResponse: expected: 200 / but was: 404
mailFailureDoesNotExposeAccountExistence: expected: 200 / but was: 404
sendsIdOnlyToMatchingNameAndEmail: Wanted but not invoked; zero interactions with this mock
smtpWaitDoesNotHoldHttpResponse: Expecting value to be true but was false
```

마지막 실패는 기존 구현이 메일을 보내지 않아 가짜 발송기 gate에 진입하지 않은 것이다. 수정 후에는 발송기에 진입하고 SMTP gate가 닫힌 동안 응답이 먼저 완료되는지 검사한다. 롤백 테스트는 수정 전에도 발송이 없으므로 통과했으며 나머지 네 건이 계약 변경을 판별한다.

## 검증

- ProdMailConfigTest 24건: 필수 값·주소·포트·환경 연결. 기존 M5ProdSecretsTest 포함 29건 통과.
- FindIdRecoveryTest 5건: 동일 응답·일치 시만 발송·HTTP/SMTP 분리·SMTP 실패 동일 응답·롤백 미발송. 실제 발송 대신 가짜 JavaMailSender와 latch를 사용한다.
- `node frontend/scripts/check-recovery.mjs`: 공개 라우트 4개, 계정 유무 동일 안내, 잘못된 코드·재발송·정책·증명 일회성·5분 코드 만료·15분 증명 만료·요청 제한 통과.
- 프론트 `npm run build`, `npm run lint` 통과. 기존 잠금 파일로 임시 사본에 npm ci를 실행했으며 새 라이브러리는 추가하지 않았다.
- 브라우저 목 모드: 일치/없는 계정 모두 같은 아이디 안내, 잘못된 인증번호 거절, 재발송, 정상 확인·재설정 완료·새 비밀번호 로그인 성공. 실제 초대의 setupToken 링크에서 설정 완료도 확인했다. 콘솔 error 0건.
- 최종 전체 UTC: **281/0/0/0**(실행/실패/오류/건너뜀), 64개 테스트 클래스, Maven **27:00 min**, BUILD SUCCESS. 기존 252개 + ProdMailConfigTest 24개 + FindIdRecoveryTest 5개. 의도된 알림 저장 실패 500 롤백 테스트 3개도 통과했다.
- 실행: 작업 트리 밖에 `git archive HEAD backend docs/refactoring`으로 만든 사본에 최종 소스를 적용하고, `JAVA_HOME=/Users/kimmini/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home ./mvnw -Duser.timezone=UTC test`를 실행했다. src 368개가 작업 트리와 같은 바이트임을 확인했다. 기록 모드는 끈 상태이며 기준선 3개는 시작점과 동일하다.
- 아이디 찾기 변경 전 시작했던 전체 실행은 추가 요청 반영을 위해 중단했다. 그 실행은 완료 증거에 포함하지 않으며 위 수치는 새 최종 사본의 결과다.
- 프론트 최종 build 12.85s, lint exit 0. 운영 빌드의 목 인증번호/안내 문자열 검색 결과 0건. 화면 검증용 로컬 서버·탭은 정리했다.

## 남은 한계와 배포 때 확인할 것

- 비동기 대기열은 영속 저장이 아니다. 앱 종료·대기열 포화·SMTP 실패 시 자동 재시도하지 않는다. 화면 안내대로 다시 신청해야 하며 실제 수신은 별도 확인해야 한다.
- 응답 분리는 SMTP 대기에 의한 큰 시간 차이를 제거하는 방식이다. DB 조회나 스케줄링까지 동일 시간임을 보장하는 것은 아니다. 실서비스 지연 분포는 측정하지 않았다.
- 초기 구현에서는 비밀번호 재설정 발송 구조를 유지했고, 아이디 찾기 속도 제한은 추가하지 않았다. 아래 리뷰 보완에서 메일 응답 분리와 아이디 찾기 한도를 추가했으며 영속 재시도는 추가하지 않았다.
- npm ci는 기존 잠금 파일에서 취약점 17건(낮음 1, 보통 7, 높음 9)을 보고했다. 이번 범위에서 의존성 업데이트·audit fix는 하지 않았다.
- 개발 DB/Redis, 실제 SMTP/서버, 명세, 마이그레이션, 기준선, 토큰 만료 동작과 포트폴리오 재현 항목은 변경·호출하지 않았다.

## 커밋 범위

- 운영 설정: `7a44d5a` — prod 환경 변수·조기 실패·안전한 비밀값 입력·문서/검증.
- 인증 연결: `c051305` — `fix(frontend): 아이디·비밀번호 찾기 화면을 인증 API에 연결`. 사용자 추가 승인에 따른 아이디 찾기 백엔드 계약 변경·회귀 테스트·실제 초대 링크 키 호환도 포함한다.
- 비밀번호 보기 버튼: 아래 검증 절과 같은 `feat(frontend): 비밀번호 보기 버튼` 독립 커밋. 최종 전체 검증은 이 프론트 변경도 포함한 소스를 기준으로 수행했다.

## 공통 비밀번호 보기 버튼

PasswordInput 하나로 통합했다. 각 필드는 별도 표시 상태를 가지며 초기값은 숨김이다. native button의 type=button으로 폼 제출과 분리하고, 화면 읽기용 필드별 aria-label·aria-controls·aria-pressed와 키보드 포커스 표시를 둔다.

| 목 화면 | 직접 확인 |
| --- | --- |
| 로그인 | 보기 Enter → text, 숨기기 Space → password, 재설정한 목 비밀번호 로그인 성공 |
| 첫 로그인 비밀번호 변경 | firstadmin 강제 변경 화면 진입, 현재/새/확인 세 필드별 보기 동작 |
| 내 비밀번호 변경 | 세 필드 각각 Enter·Space 전환, 다른 입력란은 그대로 |
| 초대 비밀번호 설정 | setupToken 링크로 진입, 두 필드 Enter·Space 전환, 설정 완료 안내 |
| 비밀번호 재설정 | 두 필드 개별 표시, Enter·Space 전환이 폼을 제출하지 않음, 재설정 완료 |
| 내 프로필 모달 | 가짜 인증번호 확인 후 두 필드에서 공통 보기 버튼 동작 |

최종 프론트 build·lint exit 0, 브라우저 console error 0. 키보드 버튼과 접근성 속성을 확인했으며 실제 화면 읽기 프로그램으로 듣는 시험은 수행하지 않았다. 실제 계정의 비밀번호나 SMTP 자격 증명은 입력·변경하지 않았다.

## 리뷰 보완 — 재설정 메일 응답 분리·아이디 찾기 요청 제한

### 증상·원인

1. 재설정 요청은 계정이 있을 때만 트랜잭션 안에서 SMTP를 기다렸다. 같은 본문이라도 응답 지연과 SMTP 실패 시 400 응답으로 계정 존재 여부를 구별할 수 있었다. DB 트랜잭션을 롤백해도 이미 보낸 메일은 취소되지 않았다.
2. 아이디 찾기는 이메일별 제한이 없어 같은 계정으로 반복 발송할 수 있었다.

### 수정·선택

- 인증번호의 생성·Redis 저장 시점, TTL, 확인/재설정 API는 그대로다. AuthService는 PasswordResetMailService.Requested를 발행하고 AFTER_COMMIT 리스너가 기존 accountRecoveryMailExecutor에 전송만 넘긴다. 트랜잭션이 롤백되면 발송하지 않는다.
- 아이디 찾기와 스레드 1개·대기열 100개의 기존 실행기를 공유한다. SMTP 실패·작업 제출 거절은 개인정보·인증번호·예외 원문 없이 일반 경고로 남기며 공개 응답을 바꾸지 않는다.
- 아이디 찾기 제한도 Redis INCR와 첫 요청의 1분 TTL, 최대 5회, 초과 시 429 TOO_MANY_REQUESTS를 사용한다. 계정 조회보다 먼저 처리하므로 이름 불일치·없는 계정도 동일하게 제한한다.
- findid:req:<email>과 기존 pwdreset:req:<email>을 분리해 아이디 찾기가 기존 비밀번호 재설정 허용 횟수를 소모하지 않게 했다. 다른 이메일도 독립적이다. 제한 초과 시 메일을 발행하지 않는다.
- AuthVerificationCodeTest는 기존 SecureRandom 양 끝값과 Redis 저장 단언을 유지하고, 직접 메일 호출 대신 발행된 이벤트의 인증번호를 대조한다.

버린 방법: AFTER_COMMIT 동기 발송은 여전히 응답을 지연시킨다. 임의 sleep으로 응답 시간을 맞추면 자원 점유만 늘고 SMTP 지연 상한을 맞추기 어렵다. 새 브로커·재시도·영속 대기열은 요청 범위 밖이므로 기존 실행기로 분리했다. 두 API의 요청 카운터 공유는 기존 재설정 한도를 바꾸므로 채택하지 않았다.

### 수정 전 실패

작업 트리 밖 32cbf2c archive에 테스트만 추가했다. 모든 새 회귀 테스트는 기존 AuthService·MockMvc·JavaMailSender만 사용하므로 새 이벤트/리스너 클래스 없이 컴파일됐다. MariaDB 10.11·Redis 7.4-alpine은 Testcontainers, 메일은 Mockito 가짜 또는 KI-12의 로컬 소켓이다.

```text
FindIdRecoveryTest: Tests run: 8, Failures: 3, Errors: 0, Skipped: 0
  findIdLimitsEachEmailAndDoesNotConsumeResetAllowance: expected: 429 / but was: 200
  nonMatchingRequestsUseTheSameEmailLimitWithoutSending: expected: 429 / but was: 200
  expiredFindIdWindowAllowsAnotherRequest: expected: 429 / but was: 200
PasswordResetRecoveryTest: Tests run: 6, Failures: 3, Errors: 1, Skipped: 0
  resetMailWaitsForCommitWhileCodeStorageIsUnchanged: Wanted at most 0 times but was 1
  rolledBackResetDoesNotSendMail: Wanted at most 0 times but was 1
  smtpWaitDoesNotHoldResetHttpResponse: java.util.concurrent.TimeoutException
  smtpFailureDoesNotExposeWhetherResetAccountExists: expected: 200 / but was: 400
전체 선택 실행: Tests run: 41, Failures: 9, Errors: 1, Skipped: 0
Total time: 49.830 s
```

재설정 SMTP gate를 닫은 채 HTTP 결과를 2초 기다리면 수정 전에는 TimeoutException으로 실패했다. finally에서 gate를 열어 요청을 정리했다. 수정 후에는 SMTP가 여전히 기다리는 동안 HTTP 200이 먼저 끝난다. 기존 동일 응답·기존 재설정 제한 두 테스트는 수정 전에도 통과했으며, 변경 후에도 그 계약을 지키는 회귀 검증이다.

### 수정 후 검증

- 수정 후 선택 실행: **42/0/0/0**(실행/실패/오류/건너뜀), Maven **01:47 min**. ProdMailConfigTest 27, FindIdRecoveryTest 8, PasswordResetRecoveryTest 6, AuthVerificationCodeTest 1.
- 로컬 무응답 SMTP 출력: `SMTP_SILENT_PROBE elapsed_ms=404 result=READ_TIMEOUT`.
- 최종 전체 UTC: **293/0/0/0**, 65개 테스트 클래스, Maven **12:07 min**, BUILD SUCCESS. 기존 281개 + 새 12개. 전체 실행의 로컬 SMTP 출력은 `SMTP_SILENT_PROBE elapsed_ms=430 result=READ_TIMEOUT`이었다.
- 실행 사본: `/private/tmp/mail-account-recovery-review/full`. `git archive 32cbf2c backend docs/refactoring` 후 이번 변경 파일을 적용했다. `backend/src` 370개 파일의 SHA-256이 작업 트리와 모두 같다.
- 실행 명령: `JAVA_HOME=/Users/kimmini/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home ./mvnw -Duser.timezone=UTC test`. 기록 옵션은 사용하지 않았다.

- FindIdRecoveryTest: 기존 5개 + 한도/독립성/만료 3개. 기존 테스트도 실제 일회용 Redis로 실행한다. 만료 테스트는 1분을 기다리는 대신 해당 키만 expire(Duration.ZERO)하여 다음 요청이 다시 허용되는지 확인한다. 첫 요청 TTL이 1~60초인 것도 확인한다.
- PasswordResetRecoveryTest 6개: 계정 유무 동일 응답·일치 시 저장 코드 발송, 커밋 전 미발송/커밋 후 발송, 롤백 미발송, SMTP/HTTP 분리, SMTP 실패 동일 응답, 기존 재설정 5회 한도·초과 시 코드 불변/미발송.
- 프론트 변경 없음. 이번 보완에서는 build·lint를 다시 실행하지 않았다. 응답 계약과 기존 429 처리 형태는 유지한다.
- 기준선·기록 정책·명세·마이그레이션·포트폴리오 재현 항목은 변경하지 않았다. 실제 메일·개발 DB/Redis·서버 호출, 병합·푸시·배포는 하지 않았다.

### 남은 한계

- SMTP 대기를 HTTP에서 분리한 것이며 계정 조회·Redis 저장·스케줄링까지 응답 시간을 완전히 동일하게 만든 것은 아니다.
- Redis의 인증번호와 요청 횟수는 기존처럼 DB 롤백과 함께 취소되지 않는다. 롤백 미발송 테스트는 코드가 Redis에는 남는 기존 동작도 명시한다.
- 공유 실행기는 메모리 대기열이다. 장시간 적체·프로세스 종료·제출 거절·SMTP 실패 시 메일이 유실되거나 인증번호가 발송 전에 만료될 수 있다. 자동 재시도나 영속 대기열은 추가하지 않았다.
- 기존 제한 방식의 INCR와 EXPIRE는 별도 명령이다. 두 명령 사이 프로세스 장애에 대한 원자화는 이번 범위에서 변경하지 않았다. 이메일 표기 정규화나 IP별 제한도 추가하지 않았다.
