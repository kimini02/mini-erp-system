# M1 리뷰 2 — 이메일 변경 후 커밋 시 토큰 폐기

## 증상
기존 구현은 이메일 변경 트랜잭션 안에서 토큰을 삭제해 롤백 후에도 링크가 복구되지 않았다.

## 재현 방법
해당 회귀 테스트를 Java 17 + Docker에서 구현 변경 전 실행하고, 변경 후 같은 테스트를 다시 실행한다. 아래 결과에 테스트 이름과 전후 결과를 기록한다.

## 원인
기존 구현은 이메일 변경 트랜잭션 안에서 토큰을 삭제해 롤백 후에도 링크가 복구되지 않았다.

## 검토했지만 버린 방법과 이유
화면에서만 제한하거나 테스트 기대값을 낮추는 방법은 서버의 계약을 보호하지 못하므로 사용하지 않았다. 새 라이브러리 없이 기존 구조를 유지한다.

## 선택한 방법
이메일 변경 이벤트를 AFTER_COMMIT에서 처리한다. 토큰을 발급 당시 이메일과 함께 저장하고 사용 시 현재 이메일과 비교한다. 지연된 폐기는 이전 이메일에 연결된 토큰만 삭제하여 새 주소로 재발송한 링크를 보호한다. 재발송의 단일 토큰 교체는 기존 Lua 원자 처리를 유지한다.

## 부작용·남은 위험
DB와 Redis의 분산 트랜잭션은 없다. 커밋 뒤 Redis 삭제 실패 시 DB 이메일 변경은 유지되지만, 이전 이메일 토큰은 이메일 검증으로 사용할 수 없다. 이전 형식 토큰은 이메일 정보가 없어 재발송이 필요하다.

## 결과
- InvitationLifecycleTest 기존 5개는 변경 전부터 통과했다. 새 emailChangeRevokesOnlyAfterCommit, rolledBackEmailChangePreservesOriginalInvitation은 변경 전 2개 실패 → 변경 후 모두 통과.
- M1AcceptanceTest와 함께 총 41개 성공, 실패·오류·건너뜀 0.
- 추가 경계 테스트 delayedDeletionPreservesNewEmailToken, tokenBoundToOldEmailCannotSetPasswordEvenBeforeDeletion 2개 성공. 이 두 테스트는 보완 구현 후 추가했으므로 수정 전 실패를 주장하지 않는다.
- 토큰 A → 이메일 변경 → 재발송 B에서 A=401 SETUP_TOKEN_INVALID, B=200 및 재발송만으로 A 무효화를 실제 Redis로 계속 검증한다.
