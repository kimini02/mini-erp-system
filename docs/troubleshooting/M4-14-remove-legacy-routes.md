# M4-14 — 구 API와 호출되지 않는 JPA 쓰기 제거

## 증상
구 조회는 PROJECT 메뉴 권한·데이터 범위 대신 옛 역할로 판단했다. 새 보드를 제한해도 다른 URL로 읽을 수 있었고, 사용하지 않는 JPA 쓰기를 남기면 프로젝트 잠금·번호·version을 우회하는 호출이 다시 생길 수 있다.

## 재현 조건
제거 전 `M4QueryGrowthTest`의 실제 GET /dashboard/projects가 200을 반환하며 13 SQL을 실행했다. 1차 임시 구 쓰기 연결 검증 4개도 당시 동작을 기록했다(M4-08). M4RemovedRoutesTest는 제거 후 구 읽기 12개·쓰기 10개가 404이며 업무 version·이력·알림이 바뀌지 않고 새 보드·상세는 200인지 확인한다.

## 원인
구 컨트롤러와 서비스가 새 컨트롤러와 함께 존재했다. 새 컨트롤러의 권한 검사만으로 별도 구 URL을 보호할 수 없다. 클래스 파일은 소스를 삭제해도 증분 빌드의 target에 남을 수 있다.

## 검토했지만 버린 방법과 이유
- 구 조회를 빈 배열로 응답: 삭제 계약이 아니고 호출자가 남는다.
- 구 JPA 쓰기를 public으로 남기고 호출하지 않기: 이후 우회 사용을 막지 못한다.
- 프론트만 숨김: HTTP 호출로 우회할 수 있다.
- 전체 잘못된 HTTP 메서드의 응답 수정: 이번 범위 밖이므로 삭제된 M4 메서드만 처리했다.

## 선택과 이유
원래 명세의 2차 전체 교체 기준으로 구 ProjectController/TaskController/DashboardController와 ProjectService/TaskService/DashboardService를 삭제한다. 1차에 잠시 연결한 구 쓰기 어댑터(changeMember/replaceUserProjects/changeAssignment)와 Project.updateStatusByTasks도 삭제한다. 새 계약과 URL이 같은 GET/POST /projects, GET/PATCH /projects/{id}, PUT /projects/{id}/members, GET/PATCH /tasks/{id} 등은 새 서비스로 유지한다. 경로 id는 숫자로 제한해 /leaders·/recent-assignments가 숫자 변환 오류로 잡히지 않게 한다.

새 URL과 메서드만 겹치는 구 GET/POST /projects/{id}/members, PUT /projects/{id}, PUT /tasks/{id}는 Spring의 메서드 불일치 예외가 발생한다. 기존 공통 catch-all이 이를 500으로 바꾸는 것을 새 테스트에서 **기대 404/실제 500**으로 먼저 확인했다. 삭제 대상인 이 메서드만 404 RESOURCE_NOT_FOUND로 분기한다. 별도 조회·쓰기 엔드포인트를 다시 등록하지 않았다.

`clean test`로 오래된 클래스 파일을 제거하고, 이후 Class.forName으로 구 서비스 3개를 로드할 수 없는지도 검증한다. 운영 소스에서 정확한 이름 및 어댑터 호출 검색 결과는 0개다:

```sh
rg -n '\b(ProjectService|TaskService|DashboardService|updateStatusByTasks)\b|changeAssignment\(|changeMember\(|replaceUserProjects\(' backend/src/main/java
```

## 전후 수치·결과
- 제거 전: 실제 dashboard/projects 200, 업무 COUNT 10번/HTTP SQL 13번.
- 경로 제거 직후: 삭제 계약 테스트 2개 중 1개 실패(겹치는 members GET 500), 클래스 부재 검증 1개 통과.
- 겹친 메서드 처리 후: 삭제 테스트 2/2, 관련 보드·M1/M3 호환·확장 검증 합계 18/18 통과. 구 읽기 12개·쓰기 10개 404, 새 상세·보드 200, 쓰기 흔적 증가 0.
- 삭제한 구 서비스 전용 테스트 67개와 임시 쓰기 어댑터 테스트 4개는 기능 폐기에 따라 제거했다. 새 MariaDB 보드·홈·권한·정합성·삭제 테스트가 최종 계약을 검증한다. 전체 테스트 수 감소는 이 변경 때문이며 실패 테스트를 건너뛴 것이 아니다.
- V6 이관 검증 M4DataMigrationTest는 그대로 유지한다.

## 부작용·남은 위험
구 화면이 호출하던 API는 더 이상 동작하지 않는다. 사용자 결정대로 M4 새 화면과 함께 병합해야 한다. 기본 로그인은 JWT 필터가 먼저 처리하므로 미인증 요청은 401이다. 캘린더는 이번 범위 밖이며 유지한다. 구 메서드 외 일반 HTTP 메서드 불일치는 기존 catch-all 때문에 500일 수 있으나 여기서는 고치지 않았다.

## 커밋
이 문서, 구 컨트롤러/서비스 삭제, 새 경로 제한·예외 처리 및 회귀 테스트를 함께 담은 독립 커밋(`git log -- docs/troubleshooting/M4-14-remove-legacy-routes.md`).
