# M5-18 운영 문서의 누락 조건 보완

## 증상
켜자마자 생긴 자동 백업에는 counts가 없어 로컬 복구가 거부될 수 있고, 앱 계정 분리·메일·부하 측정의 조건을 운영 문서만으로 알기 어려웠다.

## 재현 방법
restore-local.sh의 필수 sidecar 검사, restore-rehearsal.sh의 counts 생성, batch-check.timer의 Persistent=true, InvitationService의 setup URL 기본값, Nginx auth 제한을 코드와 대조했다. 실제 서버·메일 발송·부하 요청은 하지 않았다.

## 원인
백업 생성과 복구 검증이 별도 단계이고, 프로세스 경계와 DB 연결 권한 경계가 다르다. 운영 기본값·타이머 성질의 일부가 runbook에 빠졌다.

## 검토했지만 버린 방법과 이유
- counts 없이 복구 허용: 기존 행 수 검증 계약을 약화한다.
- 운영 URL을 코드에 하드코딩: 설치마다 도메인이 달라진다.
- 로그인 rate limit을 부하 실험 때문에 해제: 운영 방어 규칙을 바꾸는 요청이 아니다.

## 선택한 방법
README 8·11·12절에 restore-rehearsal → 같은 세트 pull-backup → restore-local을 명시했다. 11절에 09:10 이후 시작 시 Persistent batch-check의 누락 알림 가능성을 추가했다. 10절에 DML 연결의 DDL 방어와 프로세스 침해의 차이, 메일 활성화 시 APP_INVITATION_SETUP_URL 설정을 추가했다. 9절에 10건/분·burst 5와 사전 토큰 발급 후 로그인 외 API로 부하 측정하도록 적었다.

## 결과
기존 구현을 변경하지 않고 문서가 실제 실행 조건을 설명하도록 보완했다. 초대 URL의 기본값은 http://localhost:5173/password/setup이며 환경변수로 실제 HTTPS 도메인의 /password/setup을 설정한다.

## 부작용·남은 위험
리허설 뒤 새로운 백업이 생기면 그 최신 세트에도 counts를 만들어야 한다. 앱 프로세스 환경의 migrator 비밀값과 실제 메일/서버 검증은 현재 운영 설계의 한계로 남는다.

## 커밋
README·이 문서의 독립 커밋. 최종 보고서에 해시를 기록한다.
