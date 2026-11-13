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
