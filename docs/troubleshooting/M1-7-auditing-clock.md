# M1-7 — 자동 기록 시각 (KI-05)

## 증상
JVM이 UTC이면 자동 생성 시각이 업무용 고정 Clock과 달라진다.

## 재현 방법
MariaDB에서 사용자를 저장하고 `created_at`을 고정 Clock의 한국 시각과 비교했다. 수정 전 실패를 확인했다. `AuditClockMariaDbTest.auditTimestampUsesInjectedClockWithUtcJvm`은 JVM 기본 시간대를 UTC로 바꾸고 원상 복구하며 실제 열 값도 검사한다.

## 원인
Spring Auditing과 Hibernate의 생성·수정 시각 주석이 함께 있었다. 또한 일반 설정의 `ConditionalOnBean(DataSource.class)`가 등록 순서에 따라 Auditing을 누락시켰다.

## 검토했지만 버린 방법과 이유
JVM 시간대만 한국으로 고정하면 테스트의 Clock 변경이 반영되지 않는다. Hibernate 시각 생성과 Spring Auditing을 함께 유지하면 공급자가 둘이 된다.

## 선택한 방법
`DateTimeProvider`가 주입된 Clock으로 `LocalDateTime`을 제공한다. Hibernate의 별도 시각 생성 주석과 순서에 의존하는 조건을 제거했다.

## 부작용·남은 위험
JPA를 직접 구성하는 테스트는 Auditing 설정과 Clock을 함께 등록해야 한다. 기존 저장 시각은 소급 변환하지 않는다.

## 결과
M0 기준 코드의 격리 검증에서 수정 전 실패, 수정 후 MariaDB 검증 1개 통과. M1 전체 회귀 결과는 최종 보고서에 기록한다.
