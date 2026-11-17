# KI-12 — 운영 메일과 초대 주소

## 증상과 원인

운영에서 메일이 꺼져 있으면 InvitationService는 링크 발급·발송을 하지 않아 신규 사원이 첫 비밀번호를 정할 수 없다. production 설정에는 SMTP 값이 없고 초대 주소는 개발 서버 기본값을 사용했다. 사용자가 운영 메일 활성화를 결정했다.

## 수정

- prod 설정에 SMTP_HOST/PORT/USERNAME/PASSWORD, APP_MAIL_FROM, APP_INVITATION_SETUP_URL 연결. 운영 스크립트는 APP_MAIL_ENABLED=true를 저장한다.
- 기존 BeanFactoryPostProcessor에서 활성 시 필수 값·포트·외부 HTTPS 초대 주소를 검증한다. DB/Redis/SMTP 클라이언트 생성 전 실패하며 값과 원래 파싱 예외를 출력하지 않는다.
- setup-secrets는 숨김 입력·원자적 파일 교체·0600을 유지한다. 재실행은 DB/JWT/JAVA_OPTS와 제거된 초기 관리자 정보 상태를 보존하면서 메일 설정만 교체한다.
- 대안: 메일 없이 초대 토큰을 응답에 노출하는 방식은 기존 보안 계약과 달라 선택하지 않았다. SMTP 네트워크 연결을 기동 검사에 추가하면 외부 장애로 앱까지 못 뜰 수 있어 이번에는 설정 형식만 검증한다.

## 검증

작업 트리 밖 git archive 사본, Java 17, UTC. 실제 SMTP 계정·발송·개발 DB/Redis 사용 없음.

- ProdMailConfigTest: 수정 전 24건 중 실패 23·오류 0·건너뜀 0 → 수정 후 24건 모두 통과. 누락 6종, 빈 값 6종, 부적절한 초대 주소 7종, 잘못된 포트 3종, 정상 설정·비활성 각 1건. 정상 테스트도 JavaMailSender 구성만 확인하고 send는 호출하지 않는다.
- 기존 M5ProdSecretsTest 5건과 합쳐 29/0/0/0.
- `bash -n deploy/server/setup-secrets.sh` 통과. shellcheck 미설치로 미실행.
- `python3 deploy/checks/mail-secrets.py`: first_setup=PASS, existing_update=PASS, rerun=IDENTICAL, invalid_input=UNCHANGED, permissions=600, quoting=PASS, hidden_input=PASS, secret_output=0. 임시 폴더에서 TTY와 소유자만 테스트용으로 대체하고 실제 입력 검증·인용·파일 교체를 실행했다. 실제 서버 root 소유자·systemd EnvironmentFile 로딩은 미검증이다.
- 같은 브랜치의 아이디 찾기 계약 변경까지 포함한 최종 전체 UTC 결과는 [KI-13 기록](KI-13-account-recovery.md)을 따른다.

## 남은 확인

외부 SMTP 인증·발신자 승인·스팸함·실제 초대/재설정 메일 수신은 운영자가 배포 시 확인한다. mail.enabled=false를 명시한 환경은 기존대로 기동 가능하지만 신규 사원 초대는 제공되지 않는다. STARTTLS 구성만 지원하며 465 전용 SSL 설정은 추가하지 않았다.

## 리뷰 보완 — 운영 SMTP 시간 제한

### 증상·원인

SMTP가 연결 뒤 응답하지 않으면 초대 요청이나 계정 복구 작업이 무기한 대기할 수 있었다. prod에 시간 제한이 없었고, 직접 생성하는 MailConfig의 JavaMailSenderImpl도 세 JavaMail 속성을 전달하지 않았다. 따라서 설정 파일에만 값을 추가하는 것으로는 충분하지 않았다.

### 수정·선택

| 단계 | JavaMail 속성 | 운영 환경 변수 | 기본값 |
| --- | --- | --- | --- |
| 연결 | mail.smtp.connectiontimeout | SMTP_CONNECTION_TIMEOUT_MS | 5000ms |
| 읽기 | mail.smtp.timeout | SMTP_READ_TIMEOUT_MS | 5000ms |
| 쓰기 | mail.smtp.writetimeout | SMTP_WRITE_TIMEOUT_MS | 5000ms |

application-prod.properties에서 환경 변수를 연결하고 MailConfig에서 실제 Session 속성으로 전달한다. 값은 밀리초 단위의 양수로 설정한다. 자동 재시도나 새 대기열은 추가하지 않았다. 기존 초대 발송 구조는 유지하며, 비밀번호 재설정 메일의 HTTP 응답 분리는 KI-13의 이번 보완 절을 따른다.

버린 방법: 설정 파일만 변경하면 수동 생성한 발송기에 적용되지 않아 제외했다. 요청 전체의 Future 대기 시간만 제한하면 내부 SMTP 소켓이 계속 묶일 수 있어 소켓별 JavaMail 제한을 선택했다.

### 수정 전 실패 → 수정 후 검증

기존 32cbf2c에 새 테스트만 넣은 외부 archive에서 먼저 실행했다. 새 운영 클래스에 의존하지 않아 수정 전에도 컴파일됐다.

```text
productionSmtpTimeoutsDefaultToFiveSeconds: expected: "5000" / but was: null
productionSmtpTimeoutsAcceptEnvironmentOverrides: expected: "1100" / but was: null
silentLocalSmtpFailsWithinReadTimeout:
  [SMTP send must finish while the silent socket remains open] Expecting value to be true but was false
ProdMailConfigTest: Tests run: 27, Failures: 3, Errors: 0, Skipped: 0
```

무응답 소켓은 127.0.0.1의 임의 포트에만 열었다. SMTP greeting·인증·메시지 교환 없이 연결만 유지한다. 수정 전에는 2초 안에 send가 끝나지 않았고, 테스트 finally가 소켓을 닫아 무한 대기를 정리했다. 수정 후에는 테스트에서 읽기 제한을 350ms로 줄여 SocketTimeoutException을 원인으로 하는 MailSendException과 종료 시간을 확인했다. 연결·읽기·쓰기 기본값 5000 및 각각 1100/1200/1300 override는 실제 prod 프로퍼티를 로드한 발송기의 Session 속성에서 확인했다.

- 수정 후 선택 실행: **42/0/0/0**(실행/실패/오류/건너뜀), Maven **01:47 min**. ProdMailConfigTest 27, FindIdRecoveryTest 8, PasswordResetRecoveryTest 6, AuthVerificationCodeTest 1.
- 로컬 무응답 SMTP 출력: `SMTP_SILENT_PROBE elapsed_ms=404 result=READ_TIMEOUT`.
- 최종 전체 UTC: **293/0/0/0**, 65개 테스트 클래스, Maven **12:07 min**, BUILD SUCCESS. 기존 281개 + 새 12개. 전체 실행의 로컬 SMTP 출력은 `SMTP_SILENT_PROBE elapsed_ms=430 result=READ_TIMEOUT`이었다.
- 실행 사본: `/private/tmp/mail-account-recovery-review/full`. `git archive 32cbf2c backend docs/refactoring` 후 이번 변경 파일을 적용했다. `backend/src` 370개 파일의 SHA-256이 작업 트리와 모두 같다.
- 실행 명령: `JAVA_HOME=/Users/kimmini/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home ./mvnw -Duser.timezone=UTC test`. 기록 옵션은 사용하지 않았다.

### 남은 한계

5초는 각 연결·읽기·쓰기 작업의 제한이며, 메일 전체 전달이나 HTTP 전체의 5초 기한을 뜻하지 않는다. 실제 소켓 시험은 읽기 무응답만 검증했다. 연결 패킷 유실·쓰기 버퍼 정체·DNS 지연·외부 SMTP 수신은 시험하지 않았다. 메일 서버 장애 시 전송 성공을 보장하지 않으며, 재시도·영속 저장은 이번 범위에 넣지 않았다.
