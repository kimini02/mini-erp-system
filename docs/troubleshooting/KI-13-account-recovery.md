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
- 기존 비밀번호 재설정 API의 발송 구조·속도 제한 정책은 변경하지 않았다. 별도 아이디 찾기 속도 제한과 영속 재시도는 추가하지 않았다.
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
