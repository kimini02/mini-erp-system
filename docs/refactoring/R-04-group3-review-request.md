리팩토링 4단계 묶음 3 리뷰를 부탁합니다. 수정·커밋·병합·푸시는 하지 말고 검토만 해 주세요.

저장소: /Users/kimmini/sk-final-deepfake/mini-erp-system-backend-readability
브랜치: refactor/backend-readability
비교 BASE: d6938434a965788f29d4aaf15a2cdba84e529598
최종 코드: 3dbc348e59fa786e9aa58fa67a9e94e69474cb76
기준: backend/md/17-refactoring.md §2.3 11차, docs/refactoring/R-04-group3-attendance-home-project.md

가능하면 작업 트리 밖 archive에서 Java 17 + Testcontainers만 사용해 독립 검증해 주세요. 개발 DB/Redis·실제 서버는 사용하지 마세요.

- UTC 기록 ON/OFF 각각 231/0/0/0, differences=0, 순서 경고가 승인된 14개 안인지
- SQL·인자·호출 순서, 트랜잭션 83개와 API 98개, 테스트·기준선·금지 경로 보존
- 응답 구성요소 대조 269건의 타입/키/null·부재/숫자/날짜, 혼합 배열 3곳과 조건부 permissions·target
- CSV, myRole/actor·Clock·단락 평가 위치, 추가 probe 4개의 기준선 비교
- repository 예외와 남긴 Map, 전체 main 이름·형식·미사용 import 및 도구 판별력
- 프로젝트 첫 ON에서 격리 수준 조회 1회의 테스트 귀속 이동으로 differences=6이 나온 기록과, 기준 코드/후보 진단 및 재검증 결과(정책·기준선은 변경하지 않음)

발견 사항은 우선순위·파일:줄·재현 조건·영향과 함께 알려 주세요. 다음 단계는 아직 시작하지 않습니다.
