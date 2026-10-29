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

기록 정책 `ordered-outcome-dependent-http-races-v1`은 사용자 승인에 따라 결과가 순서에 의존하는 5개 테스트의 작업 스레드 HTTP 요청만 경로순으로 완료시킨다. 기본 실행의 동시성 검증을 대신하지 않는다. 기록을 끈 전체 UTC 실행도 반드시 통과해야 한다. 실제 기본 테스트 본문과 기대값은 변경하지 않았다.

비교는 SQL 집합·테스트별 비교 횟수·응답 계약의 차이에서 종료 코드 1, 동일하면 0이다. 명세에 따라 SQL 순서 차이는 별도 경고로 출력하며 원인을 보고한다. 원본 SQL은 삭제하지 않는다. 유일한 호출 수 제외는 정확히 일치하는 INNODB_LOCK_WAITS 진단 폴링문이다.

자세한 기록 방식·실험·한계는 docs/refactoring/R-01-safety-net.md를 참조한다.
