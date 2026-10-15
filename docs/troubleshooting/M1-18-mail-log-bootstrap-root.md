# M1 리뷰 6 — 인증번호 로그 제거·최초 관리자 소속

## 증상
메일 비활성 경로가 인증번호를 INFO 로그에 남겼고 최초 관리자가 개발팀(id 2)으로 생성되었다.

## 재현 방법
해당 회귀 테스트를 Java 17 + Docker에서 구현 변경 전 실행하고, 변경 후 같은 테스트를 다시 실행한다. 아래 결과에 테스트 이름과 전후 결과를 기록한다.

## 원인
메일 비활성 경로가 인증번호를 INFO 로그에 남겼고 최초 관리자가 개발팀(id 2)으로 생성되었다.

## 검토했지만 버린 방법과 이유
화면에서만 제한하거나 테스트 기대값을 낮추는 방법은 서버의 계약을 보호하지 못하므로 사용하지 않았다. 새 라이브러리 없이 기존 구조를 유지한다.

## 선택한 방법
비활성 메일 로그에는 발송 비활성 사실만 남긴다. User.createInitialAdmin에서 departmentId=1과 루트 호환 필드를 지정하고 CHIEF·최초 비밀번호 변경·연차 0 정책은 유지한다.

## 부작용·남은 위험
기존 관리자의 부서는 바꾸지 않는다. 이미 관리자가 있으면 초기화기를 건너뛰는 기존 정책을 유지한다.

## 결과
- MailServiceLoggingTest.disabledMailNeverLogsVerificationCode 신규 테스트: 수정 전 실패 → 수정 후 통과. 비활성 시 메일 발송기 호출도 없는지 확인.
- InitialAdminInitializerTest.createsAdminWithEncodedPasswordAndZeroLeave 및 InitialAdminMariaDbTest.startupCreatesUsableAdminAndRepeatedStartupCallbackDoesNotDuplicate에 루트 부서 기대값 추가: 수정 전 둘 다 실패 → 수정 후 통과.
- 세 클래스 합계 15개: 수정 전 성공 12·실패 3 → 수정 후 성공 15, 실패·오류·건너뜀 0.
