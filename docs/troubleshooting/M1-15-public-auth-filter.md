# M1 리뷰 3 — 공개 인증 경로 JWT 제외

## 증상
로그인에 무효화된 Bearer를 보내거나 password/setup에 엉터리 Bearer를 보내면 공개 인증 절차보다 JWT 검사가 먼저 401로 막았다.

## 재현 방법
해당 회귀 테스트를 Java 17 + Docker에서 구현 변경 전 실행하고, 변경 후 같은 테스트를 다시 실행한다. 아래 결과에 테스트 이름과 전후 결과를 기록한다.

## 원인
로그인에 무효화된 Bearer를 보내거나 password/setup에 엉터리 Bearer를 보내면 공개 인증 절차보다 JWT 검사가 먼저 401로 막았다.

## 검토했지만 버린 방법과 이유
화면에서만 제한하거나 테스트 기대값을 낮추는 방법은 서버의 계약을 보호하지 못하므로 사용하지 않았다. 새 라이브러리 없이 기존 구조를 유지한다.

## 선택한 방법
shouldNotFilter에서 컨텍스트 경로를 제외한 /api/v1/auth 및 /api/v1/auth/ 하위 요청을 제외한다. 로그인 자격 증명과 setupToken 검증은 각 서비스가 계속 수행한다.

## 부작용·남은 위험
보호된 다른 API는 기존 JWT 검사를 유지한다. 인증 경로에 잘못된 Bearer를 붙여도 허용한다는 뜻이지 로그인 비밀번호나 초대 토큰 검사를 생략하는 것은 아니다.

## 결과
- M1AcceptanceTest.loginIgnoresRevokedBearerToken, passwordSetupIgnoresMalformedBearerToken: 수정 전 2개 모두 401로 실패 → 수정 후 모두 200 성공.
- DepartmentHeadConcurrencyTest와 함께 총 8개 성공, 실패·오류·건너뜀 0.
