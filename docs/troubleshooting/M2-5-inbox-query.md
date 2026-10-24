# M2-5 — 결재함 범위·일괄 조회·1만 건 실험

## 증상
새 결재 문서의 여섯 결재함 탭·건수와 화면 응답이 필요하다. 문서마다 결재선을 별도 조회하면 페이지 크기에 따라 조회 수가 늘어난다.

## 재현 방법
Java 17 + Docker에서 `./mvnw -Duser.timezone=UTC -Dtest=M2InboxTest,M2InboxPerformanceTest test`. 실제 MariaDB 10.11 Testcontainers에 100건을 넣어 조회한 뒤 9,900건을 추가해 같은 경로를 실행한다. 담당자가 대상인 문서는 1%다.

## 원인
문서 상태만으로는 내 차례·내 승인·참조를 구분할 수 없다. 목록별 후속 조회는 N+1을 만든다.

## 검토했지만 버린 방법과 이유
전체 문서를 읽고 Java에서 권한을 거르는 방식은 다른 사람의 데이터가 메모리에 섞이고 페이징이 틀어지므로 제외했다. 결재선 하나씩 조회도 제외했다.

## 선택한 방법
사용자와 결재선 상태를 SQL EXISTS로 제한한다. 전체 건수 1회, 페이지 문서 1회, 페이지 ID의 결재선 전체 1회로 조회한다. 빈 페이지는 결재선 조회가 없어 2회다. 탭 건수는 한 문장의 여섯 서브쿼리다. drafts는 updated_at DESC, id DESC. 화면에 formCode, 짧은 formName, drafter, submittedAt, updatedAt, steps, currentApprover를 반환한다.

## 부작용·남은 위험
부분 문자열 검색과 정렬에는 filesort가 남는다. 이번 수치는 단일 개발 머신의 작은 버퍼·특정 선택도에서의 측정이며 성능 SLA가 아니다. 쿼리 수는 인증·권한 조회를 제외한 InboxRepository 경로의 실제 SqlStore rows/count 호출을 계수한다.

## 결과
M2InboxTest 1개와 M2InboxPerformanceTest 1개 통과. 100건/10,000건 모두 3회 조회. 1만 건 EXPLAIN의 결재선은 비교군 IGNORE INDEX에서 type=ALL, 추정 9,824행 → 실제 SQL에서 type=ref, key=idx_approval_lines_turn, 추정 100행. 각 3회 예열 후 9회 중앙값: 인덱스 제외 11.494ms → 사용 5.250ms. 비교군은 인덱스 효과를 확인하는 같은 SQL의 IGNORE INDEX이며 실제 과거 운영 성능 수치가 아니다. 운영 쿼리에 FORCE/IGNORE INDEX를 넣지 않았다. V3의 기존 인덱스를 그대로 사용한다.

전체 199개 테스트 실행에서도 같은 실험이 통과했다. 100/10,000건 모두 3회, 중앙값 20.726ms → 6.718ms. 실행 부하에 따라 시간은 달라지지만 인덱스 선택과 조회 수는 같았다.
