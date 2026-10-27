# M4 2차 리뷰 — 홈 업무의 프로젝트 식별자

## 증상
홈의 내 업무를 누를 때 `/projects/{projectId}/board?taskId={id}`로 이동해야 하지만 items에 projectId가 없었다. 요약 프로젝트와 각 업무의 프로젝트는 서로 다를 수 있다.

## 재현 방법
MariaDB 10.11 Testcontainers에서 M4HomeTest를 실행한다. 기존 위젯 테스트에 projectId 단언을 추가하고, 서로 다른 두 프로젝트의 내 업무·남의 담당 업무·완료 업무·범위 밖 담당 업무를 함께 생성했다. 남의 담당 업무는 상세 조회 200으로 보기 범위 안임을 먼저 증명하고, 마감일도 더 이르게 두어 LIMIT 때문에 제외됐다는 오해를 막았다.

## 원인
HomeQueries.myItems의 SELECT와 HomeService.myTasks의 응답 매핑에 project_id가 빠졌다. 담당자·상태·보기 범위 필터는 이미 올바르므로 그대로 유지한다.

## 검토했지만 버린 방법과 이유
요약의 myTasks.project.id를 모든 items에 적용하면 여러 프로젝트의 업무 중 일부가 다른 보드로 연결된다. 항목마다 상세를 추가 조회하는 방법은 불필요한 왕복을 만든다.

## 선택한 방법
같은 조회에 t.project_id를 추가하고 각 items에 projectId를 반환한다. 기존 마감일·id 정렬과 최대 5건을 유지한다. M4-15와 동일한 오해가 있는 M4-12 문구도 “내가 담당인 미완료 업무 중 보기 범위 안의 마감일순 5건”으로 바로잡았다.

## 전후 수치와 결과
- 운영 코드 수정 전 M4HomeTest: 5개 중 projectId 관련 2개 실패. 없는 필드를 asLong으로 읽어 실제 0/기대 프로젝트 id로 실패했다.
- 수정 후 M4HomeTest: 5개 성공. 서로 다른 프로젝트 항목 2개의 id가 각각 정확하고, 범위 안 타인 담당·완료·범위 밖 업무는 제외된다.
- M4HomeTest + M4ReviewRegressionTest + M4OverdueTest: 17개 성공, 실패·오류·건너뜀 0.
- 최종 기본·UTC JVM 전체 검증은 각각 218/218 성공. 실패·오류·건너뜀 0.

## 부작용·남은 위험
응답 필드 추가만으로 SQL 수는 늘지 않는다. 내 업무 양에 따라 기존 정렬 비용이 증가할 수 있는 점은 그대로다. 프론트는 수정하지 않았다.

## 변경 파일
- backend/src/main/java/com/minierp/backend/domain/home/service/HomeQueries.java
- backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java
- backend/src/test/java/com/minierp/backend/integration/M4HomeTest.java
- docs/troubleshooting/M4-12-home-and-growth.md
- docs/troubleshooting/M4-15-phase-two-results.md
- 이 문서

## 전체 검증
2026-10-27, Java 17.0.16 + Docker + MariaDB 10.11 Testcontainers. Maven 최종 요약과 JUnit XML 57개 클래스 합계를 대조했다.

| 환경 | 실행 | 성공 | 실패 | 오류 | 건너뜀 | 전체 경과 시간 |
|---|---:|---:|---:|---:|---:|---|
| 기본 JVM | 218 | 218 | 0 | 0 | 0 | 8분 49초 |
| UTC JVM | 218 | 218 | 0 | 0 | 0 | 8분 52초 |

이전 214개에 홈 1개·이력 1개·마감 알림 2개를 추가했다. 기존 테스트 삭제나 @Disabled 추가는 없다. UTC는 동일 빌드 입력을 임시 폴더로 복사해 독립 실행했으며 pom/mvnw/.mvn/src 262개 파일 SHA-256이 원본과 같다. 실행 전후에도 입력이 바뀌지 않았고 UTC XML의 user.timezone=UTC를 확인했다. 두 전체 실행은 병렬로 진행했으므로 위 시간은 성능 개선 수치로 쓰지 않는다. 실행 중에만 caffeinate로 잠자기를 막았으며 시스템 설정은 바꾸지 않았다.

```sh
cd /Users/kimmini/sk-final-deepfake/mini-erp-system/backend
export JAVA_HOME=/Users/kimmini/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home
./mvnw test
./mvnw -Duser.timezone=UTC test
# 요청한 수정만 빠르게 검증
./mvnw -Dtest=M4HomeTest,M4ReviewRegressionTest,M4OverdueTest test
```

문제별로 홈 응답/보고서 문구, [이력 대상자](M4-17-overdue-history-target.md), [중복만 무시하는 알림 SQL](M4-18-strict-overdue-claim.md)을 독립 커밋한다. frontend/, backend/md/, V1~V6, .claude/, 로컬 개발 DB는 변경하지 않았고 새 라이브러리·마이그레이션도 없다. 병합·푸시는 수행하지 않았다.
