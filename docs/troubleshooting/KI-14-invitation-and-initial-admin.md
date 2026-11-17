# KI-14 — 초대 메일 아이디 안내·초기 관리자 비밀번호 검사

## 초대 메일 아이디 안내

### 증상·원인

사원 등록 시 로그인 아이디가 자동 발급되지만 초대 메일에는 설정 링크만 있어 신규 사원이 첫 로그인 전에 아이디 찾기를 해야 했다. 등록과 재초대는 모두 EmployeeService의 InvitationRequested 이벤트 → InvitationService.send → MailService.sendInvitationMail을 사용한다.

### 선택한 방법·버린 방법

- InvitationService가 잠금으로 읽은 대상 사원의 loginId를 메일 함수에 넘기고, 기존 설정 링크 앞에 `로그인 아이디: …`를 넣었다. 재초대도 같은 경로라 현재 대상 사원의 아이디와 새 링크를 보낸다.
- 등록된 이메일의 수신자는 기존 아이디 찾기에서도 동일한 정보를 받을 수 있다. 로그나 공개 응답에는 아이디·토큰을 추가하지 않았다.
- 버린 방법: 비밀번호 설정 공개 응답에 아이디를 추가하면 노출 경로와 API 계약이 늘어난다. 이번에는 기존 수신자에게 보내는 초대 메일만 변경했다.

### 수정 전 실패·수정 후 검증

05f32e8의 작업 트리 밖 archive에 테스트만 추가했다. Java 17·UTC·MariaDB/Redis Testcontainers, JavaMailSender는 가짜다. 실제 SMTP는 호출하지 않았다.

```text
InvitationLifecycleTest: Tests run: 11, Failures: 2, Errors: 0, Skipped: 0
registrationMailIncludesGeneratedLoginIdAndSetupLink: AssertionError (본문에 로그인 아이디 안내 없음)
reinvitationApiIncludesSameEmployeeIdAndNewSetupLink: AssertionError (본문에 로그인 아이디 안내 없음)
→ 수정 후 11/0/0/0
```

두 새 테스트는 실제 POST /users와 POST /users/{id}/invitation을 호출한다. 수신자, 자동 생성 아이디, 설정 링크의 토큰 소유자, 재초대 토큰 교체를 확인한다. 실패 원문에는 일회용 링크가 포함되므로 문서에는 메서드·실패 종류와 원인만 옮겼다. 기존 초대 무효화·동시 교체·롤백 검증도 통과했다.

### 남은 한계

실제 SMTP 수신·스팸 처리·메일 클라이언트 표시는 검증하지 않았다. 기존 초대 발송의 커밋 후 동기 실행·직원 잠금 구조는 이번 범위에서 바꾸지 않았다. 기존 SMTP 시간 제한은 유지한다.

## 초기 관리자 비밀번호 검사 일치

### 증상·원인

setup-secrets.sh가 영문·숫자가 아닌 모든 문자를 기호로 인정했으나 PasswordPolicy는 정해진 기호 중 하나가 있어야 통과시켰다. 스크립트를 통과한 값으로도 최초 관리자를 만들지 못하고 WARN 후 앱만 기동할 수 있었다. Python은 유니코드 코드 포인트로, Java String.length는 UTF-16 단위로 길이를 세는 차이도 있었다.

### 선택한 방법·버린 방법

- 스크립트는 기존 PasswordPolicy와 동일한 기호 집합·대문자·소문자·숫자·8~20 UTF-16 단위 길이를 검사한다. Java 정규식의 줄 구분자 판정도 맞췄다. 백엔드 PasswordPolicy 자체는 바꾸지 않았다.
- 주의: 기존 정책은 틸드·백틱·공백·한글을 **필수 기호로 인정하지 않는 것**이다. 허용 기호가 따로 들어 있으면 이 문자들이 추가로 포함되는 것은 허용한다. 사용자 지시의 “같은 기준”을 실제 PasswordPolicy.accepts와 맞췄으며, 전체 허용 문자 집합을 더 좁히지는 않았다.
- prod의 기존 조기 검사에서 INITIAL_ADMIN_* 네 값에 모두 내용이 있을 때만 PasswordPolicy.accepts를 검사한다. 불합격은 `Invalid production setting: INITIAL_ADMIN_PASSWORD`로 기동 실패시킨다. 값이나 원래 입력 예외는 붙이지 않는다.
- 네 값 전부 없거나 일부가 없거나 비어 있으면 조기 검사는 통과하고 기존 InitialAdminInitializer 흐름을 유지한다. 관리자가 없으면 WARN 후 생성 생략, 이미 있으면 아무것도 하지 않는다. clear-initial-password.sh가 실제로는 **PASSWORD 하나만 삭제하고 세 값은 남기므로** 일부 누락도 실패시키면 정상 재기동이 막힌다. local에는 새 검사가 적용되지 않는다.
- 버린 방법: 스크립트만 수정하면 환경 변수를 직접 넣는 경로가 남는다. 초기 관리자 없이 prod를 계속 띄우는 방식은 원래 사고를 반복하므로 네 값이 완비된 잘못된 설정은 기동 전에 거절한다. 일부 누락까지 모두 실패시키거나 비밀번호 정책을 새로 강화하는 변경은 선택하지 않았다.

### 수정 전 실패·수정 후 검증

공통 테스트 리소스 `onboarding/password-policy-cases.json`의 51개 합성 입력을 Java와 실제 setup-secrets 내장 Python에 각각 넣었다. 허용 기호 30개, 지원하지 않는 기호만 있는 경우와 허용 기호를 함께 쓴 경우, 길이·문자 종류·유니코드 경계를 포함한다. 테스트 출력은 입력값 대신 사례 이름만 사용한다.

```text
InitialAdminProdConfigTest: Tests run: 58, Failures: 14, Errors: 0, Skipped: 0
productionMatchesBackendPolicyWithoutEchoingValues: Expecting actual not to be null
  (잘못된 네 값 완비 설정인데 startupFailure가 null)
→ 수정 후 58/0/0/0

INITIAL_ADMIN_POLICY cases=51 mismatches=8 secret_output=0
  틸드·백틱·공백·한글 단독, 유니코드 줄 구분자 3종, UTF-16 길이 22
→ INITIAL_ADMIN_POLICY cases=51 mismatches=0 secret_output=0
```

- ApplicationContextRunner는 prod 설정 검증 클래스만 로드한다. 51개 정책 사례 외에 전부 누락, 네 가지 일부 누락, 빈 비밀번호, local 비활성 검사도 포함한다. DB/Redis/SMTP 연결 전에 판정하며 오류에 설정 값이 포함되지 않는지 확인한다.
- 기존 InitialAdminInitializerTest **13/0/0/0**, InitialAdminMariaDbTest **1/0/0/0**. 암호화·중복 생성 방지·local의 경고 후 생략 계약을 유지한다.
- `bash -n deploy/server/setup-secrets.sh`: exit 0.
- `python3 deploy/checks/initial-admin-password.py`: exit 0, 51개 일치. 임시 폴더와 가짜 숨김 입력으로 실행하며 서버 경로를 수정하지 않는다.
- 기존 mail-secrets.py: first_setup=PASS, existing_update=PASS, rerun=IDENTICAL, invalid_input=UNCHANGED, permissions=600, quoting=PASS, hidden_input=PASS, secret_output=0.
- 검사 하네스 첫 시도에서는 임시 PTY를 제어 터미널로 획득해 종료 때 HUP(129)가 발생했다. 새 검사에는 O_NOCTTY를 사용했다. 기존 mail-secrets 파일은 바꾸지 않고 외부 래퍼에서 os.open에 O_NOCTTY만 더해 위 PASS와 **exit 0**을 확인했다. 스크립트 업무 로직은 동일하다.

### 남은 한계

일부 초기 관리자 값이 누락된 새 설치는 기존처럼 관리자 없이 기동할 수 있다. 운영자가 초기 설정 네 값을 함께 제공해야 하며, 정상 재기동을 위한 일부 누락 허용과 구별할 상태 저장은 추가하지 않았다. 실제 서버의 systemd 환경 파일 입력·관리자 생성은 수행하지 않았다. 입력 스크립트의 기존 단일 행/NUL 제한도 유지했다.


## 최종 전체 검증

결재선 변경과 기존 배치 테스트의 승인 준비 순서까지 반영한 최종 외부 archive에서 Java 17·UTC 전체 **370/0/0/0**, 67개 클래스, **10:15 min**, BUILD SUCCESS를 확인했다. 소스 373개 파일이 작업 트리와 동일하며 Testcontainers와 가짜 메일만 사용했다. 첫 전체 실행의 옛 자동 승인 가정 실패와 재실행 과정은 [KI-15](KI-15-self-approval-escalation.md)에 기록했다.

초대 안내 커밋은 `9b77385`, 초기 관리자 검사 커밋은 `e8e5eb2`다. 이 최종 결과는 결재선 커밋에 함께 기록한다. 프론트·명세·마이그레이션·기준선은 변경하지 않았고 실제 서버·메일 호출, 병합·푸시·배포는 하지 않았다.
