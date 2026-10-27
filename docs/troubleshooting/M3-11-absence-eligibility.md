# M3 결근 신규 행의 재직·활성화 조건

기준: `de9d307:backend/md/14-rest-api-v2-m3.md` (읽기 전용). Java 17 + MariaDB 10.11 Testcontainers.

## 증상
퇴사일이 NULL인 v1 퇴사자와 초대 대기 사원에게 결근 행이 만들어진다. 기록 때문에 기존 퇴사자가 월간 표에 반복 노출될 수 있다.

## 재현 방법
M3RetirementTest.legacyRetireeAndInvitationPendingHaveNoNewAbsenceRows. 수정 전 회귀 1회에서 퇴사자 행 0 기대가 실제 2로 실패했다. 수정 후 같은 테스트에서 두 대상 모두 0행이다.

## 원인
기존 SQL과 잠금 후 재검사가 retired_date NULL을 모든 상태에서 허용하고 비밀번호 설정 여부를 검사하지 않았다.

## 검토했지만 버린 방법과 이유
퇴사일을 추정해 채우는 방법과 이미 저장된 행을 삭제하는 방법은 이력 훼손이므로 버렸다. 단순히 모든 RETIRED를 제외하면 퇴사 전날 기록까지 빠지므로 쓰지 않았다.

## 선택한 방법
후보 SQL과 사용자 잠금 후 검사에 (status <> RETIRED OR retired_date > 대상일), ON_LEAVE 제외, user_pw IS NOT NULL을 적용한다. 암호 자체 대신 활성화 여부만 읽는다. 기존 행은 OR EXISTS로 포함해 재판정한다. 복직 테스트는 ACTIVE와 NULL 퇴사일을 함께 설정한다.

## 부작용·남은 위험
상태·활성화는 배치 시점 기준이다. 과거 휴직·비활성 기간 복원을 위한 상태 변경 이력은 여전히 결정 필요다. 이미 잘못 생성된 운영 행을 자동 삭제하지 않는다. 개발 DB에는 접속하지 않았다.

## 결과
M3RetirementTest 4/4, M3BatchTest 4/4 성공. 퇴사자·초대 대기 제외, 복직 포함, 기존 행 재판정 및 멱등성을 확인했다.

검증 로그: `/private/tmp/m3-phase3-before.log`, `/private/tmp/m3-phase3-after-main.log`. 전체 기본·UTC 결과는 최종 3차 보고서에 기록한다.
