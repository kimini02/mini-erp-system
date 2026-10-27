# M3-4 — 대시보드 요약 KI-07 차단

## 증상
팀장이 /dashboard/admin-summary로 다른 부서의 휴가 대기를 볼 수 있었다.

## 재현 방법
수정 전 실제 API에 팀장 토큰을 보낸 회귀 테스트가 기대 403 대신 200으로 실패했다. M3SecurityTest.dashboardLeaderIsDenied의 최초 실행에서 확인했다.

## 원인
기존 요약은 관리자 또는 팀장을 허용하고 휴가 대기 목록은 전사 조회했다.

## 검토했지만 버린 방법과 이유
이 경로에 새 부서 범위를 설계하는 것은 M3 명세가 정한 관리소장 전용 계약과 다르므로 제외했다. 화면 메뉴만 숨기는 방법도 서버 접근을 막지 못한다.

## 선택한 방법
DashboardService.getAdminSummary 진입에서 기존 requireAdmin 정책을 적용한다. 역할 합산에서 CHIEF가 ADMIN으로 계산되는 M1 규칙을 그대로 사용한다. 다른 dashboard 경로와 새 결재함은 바꾸지 않는다.

## 부작용·남은 위험
팀장 화면이 이 API를 계속 호출하면 403을 받는다. 명세대로 화면은 새 API를 사용해야 하며 frontend는 수정하지 않는다.

## 결과
M3DashboardTest는 일반 사원·팀장 403, 관리소장 200을 확인한다. 기존 DashboardServiceTest의 팀장 허용 기대도 새 명세의 거절 기대로 변경했다. 전체 실행 결과는 1차 보고서를 따른다.
