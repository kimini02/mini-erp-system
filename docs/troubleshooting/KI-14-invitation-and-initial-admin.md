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
