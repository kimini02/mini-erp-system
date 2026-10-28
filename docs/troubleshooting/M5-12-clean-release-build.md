# M5-12 배포 빌드의 오래된 리소스 제거

## 증상
소스에서 삭제/이름 변경한 migration SQL이 target/classes에 남아 다음 jar에 포함될 수 있다.

## 재현 방법
소스 migration은 수정하지 않고 target/classes/db/migration/V999__m5_stale_probe.sql만 임시로 만들었다. Java 17에서 배포 스크립트와 같은 기존 `./mvnw -DskipTests package` 실행 후 Python zipfile로 jar 목록을 검사했다. 실제 deploy/SSH/프론트 빌드는 실행하지 않았다.

## 원인
Maven package는 이전 target 리소스를 제거하지 않는다.

## 검토했지만 버린 방법과 이유
- migration 폴더만 수동 삭제: 다른 종류의 오래된 리소스가 남는다.
- 날짜/타임스탬프로 파일 필터링: 소스 변경·체크아웃 시간으로 정합성을 판단할 수 없다.

## 선택한 방법
배포 빌드를 `./mvnw -DskipTests clean package`로 바꾸고 기존 BUILD backend_s 시간 로그를 유지한다.

## 전후 수치·결과
```text
PACKAGE_BEFORE stale_entries=1
BOOT-INF/classes/db/migration/V999__m5_stale_probe.sql
PACKAGE_AFTER stale_entries=0 source_migrations=6 target_probe_removed=YES
```
수정 후 jar에는 V1__init, V2__organization_permission, V3__approval_and_leave_ledger, V4__attendance_and_batch_runs, V5__user_retirement_date, V6__project_board 여섯 SQL만 남았다. 시험 SQL은 clean으로 target에서도 제거됐다. V1~V6 소스 변경 0.

## 부작용·남은 위험
전체 재컴파일로 빌드 시간이 늘 수 있다. 시간 로그를 유지해 관측한다. skipTests는 배포 빌드에만 적용하고 검증은 별도 전체 UTC 테스트로 한다.

## 커밋
이 문서·deploy/bin/deploy.sh의 독립 커밋. 최종 보고서에 해시를 기록한다.
