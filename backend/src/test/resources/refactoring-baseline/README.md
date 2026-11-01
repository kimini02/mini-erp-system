# 리팩토링 기준선

운영 코드 기준: develop `faf7972`. Java 17, UTC JVM, MariaDB Testcontainers.

기록은 기본으로 꺼져 있다. backend 폴더에서 후보를 만든 뒤 비교한다.

```bash
./mvnw -Duser.timezone=UTC -Drefactoring.record=target/refactoring-candidate test
REFACTORING_CLASSPATH=target/refactoring-candidate/classpath.txt \
  bash src/test/resources/refactoring-tools/compare.sh \
  src/test/resources/refactoring-baseline target/refactoring-candidate
```

`JAVA_HOME`은 Java 17로 설정한다. 후보 기록에는 실행 환경의 classpath.txt가 생성되지만 이 파일은 커밋하지 않는다. sql.json은 바인딩 값을 포함하지 않는 JDBC SQL 템플릿, 원본 순서·횟수, 진단 조회 제외 후 비교 횟수를 담는다. responses.json의 본문은 숫자의 원문 표현을 보존하는 JSON 문자열이다. 객체 키만 정렬하며 배열 순서·null·날짜·숫자 표현을 비교한다. masked-fields.txt는 가린 경로와 이유다.

기록 정책 `ordered-outcome-dependent-http-races-v2`은 사용자 승인에 따라 결과가 순서에 의존하는 6개 메서드의 비대칭 작업 스레드 HTTP 경쟁을 메서드·경로순으로 완료시킨다. M3에서 같은 연차 조정끼리의 경쟁은 함께 풀어 순차화하지 않는다. 기본 실행의 동시성 검증을 대신하지 않는다. 기록을 끈 전체 UTC 실행도 반드시 통과해야 한다. 실제 기본 테스트 본문과 기대값은 변경하지 않았다.

비교는 SQL 집합·테스트별 전체/SQL별 비교 횟수·workerSql 표시·응답 계약의 차이에서 종료 코드 1, 동일하면 0이다. 순서 경고 허용 여부는 후보와 OR하지 않고 기준선의 workerSql만 사용한다. 직렬 프레임 및 이름·이유가 명시된 STRICT_ORDER 7개 프레임은 workerSql=true라도 순서가 바뀌면 실패한다. 엄격 목록 밖의 기준선 workerSql=true 프레임은 순서만 바뀐 경우 경고로 남긴다.

M5ProductionTest#<before-all>의 예외는 workflow_app·workflow_migrator의 정확한 두 CREATE USER 문(각 count=1)만 서로 교환되고 나머지 원본 항목이 모두 동일한 경우로 한정한다. 프레임 전체의 순서 변경을 허용하지 않는다. 모든 순서 예외에서도 SQL별 횟수 차이는 실패한다. 원본 SQL은 삭제하지 않는다. 유일한 호출 수 제외는 정확히 일치하는 INNODB_LOCK_WAITS 진단 폴링문이다. 엄격 목록과 26개 프레임 분류는 보고서의 ‘2차 보완’ 절, 규칙과 재비교 결과는 manifest.json에 기록한다.

자세한 기록 방식·실험·한계는 docs/refactoring/R-01-review-hardening.md를 참조한다.
