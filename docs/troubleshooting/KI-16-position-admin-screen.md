# KI-16 — 직급 관리 화면

## 증상·원인

새 설치에서 v1 시드의 `관리소장` 이름을 화면으로 바꿀 수 없었다. 새 직급은 기존 API 계약에 따라 rankOrder=0으로 만들어지지만 순서를 조정할 화면도 없었다. 백엔드 GET·POST·PATCH·PUT order·DELETE는 이미 있었고 프론트 positionApi에는 목록 조회만 있었다.

## 고른 방법·버린 방법

- 기존 부서 관리 화면에 WorkScheduleCard와 같은 Card·Field·Input·Button을 사용하는 회사 공통 직급 카드를 추가했다. 부서 선택과 무관하게 한 번 로드하며 서버 목록 순서를 그대로 표시한다.
- 추가는 DEPARTMENT C, 이름 변경·순서 저장은 U, 삭제는 D일 때만 버튼을 표시한다. 최종 권한은 서버가 검사한다. 목 서버도 쓰기 전에 같은 권한을 확인한다.
- 기존 부서 관리 경로와 메뉴에는 DEPARTMENT V 검사가 있어 로그인만으로 직급을 보라는 이번 지시와 달랐다. **새 메뉴·라우트는 만들지 않고** 기존 경로의 외부 V 검사를 카드 안쪽으로 옮겼다. 로그인은 기존 RequireAuth가 보장한다. DEPARTMENT V가 없으면 직급 카드만 표시하고 부서 트리·상세·근무시간 요청 및 표시를 생략한다. 부서 정보의 기존 보기 범위는 넓히지 않는다. 기존 메뉴 항목은 로그인 사용자에게 표시한다.
- 순서 이동은 위·아래 버튼으로 화면 상태만 바꾼다. 순서 저장 시 전체 목록을 `[{id, rankOrder: 1}, …]`로 PUT한다. 취소는 서버에서 받은 순서로 돌리고, 페이지를 떠나면 로컬 상태가 사라진다. 추가·이름 변경·삭제는 각각 즉시 저장되는 동작임을 카드에 표시한다. 미저장 순서가 있을 때 다른 편집을 막아 변경을 조용히 덮어쓰지 않는다.
- 추가 성공 후 rankOrder=0 목록을 다시 조회하고 순서 확인 안내를 표시한다. C만 있는 사람에게는 순서 권한이 있는 관리자에게 요청하라고 안내한다. 이름은 인라인으로 수정하며 삭제는 공용 확인창을 거친다. 오류는 서버 메시지를 카드 안 role=alert로 표시한다.
- API 쓰기 뒤 목록 재조회에 실패하면 옛 목록으로 다시 쓰지 않도록 비활성화하고 재조회 버튼을 제공한다. 새 캐시·폴링은 추가하지 않았다.
- **새 메뉴 추가 → 버림**: 권한표·메뉴 시드 변경이 필요하다.
- **백엔드에서 새 직급을 맨 뒤로 → 이번에는 하지 않음**: 명세 12의 rankOrder=0 계약을 바꾸는 일이다.
- 드래그 라이브러리 → 버림: 적은 수의 직급 순서는 키보드로도 누를 수 있는 위·아래 버튼으로 충분하다.

## 확인한 rankOrder 사용처

검색: `rg -n 'rank_order|rankOrder' backend/src/main/java frontend/src` 및 관련 SELECT/ORDER BY 호출 검토.

| 사용처 | 확인 내용 |
| --- | --- |
| OrgQueries.findPositions | `ORDER BY rank_order,id` 오름차순. 동순위는 id 순서다. 프론트는 응답 배열을 재정렬하지 않는다. |
| OrgStore.insertPosition / updatePositionOrder | 새 직급은 0, 순서 저장은 전달한 정수. 계약 변경 없음. |
| V2 초기 데이터(읽기만) | 사원 1, 대리 2, 과장 3, 팀장 4, 관리소장 5. 숫자가 클수록 높은 직급이라 화면에 `위에서부터 낮은 직급 → 높은 직급`을 표시한다. 기존 이름 이관은 rank 100도 있을 수 있다. |
| Position 엔티티·OrgPosition DTO | 순위 값을 저장·반환한다. 별도의 결재 권한 계산은 없다. |
| EmployeesPage·등록/수정 모달 | GET /positions 응답 순서로 직급 선택지를 만든다. 사원 페이지에 들어올 때 다시 조회하므로 같은 탭에서 이름·순서·삭제가 반영된다. 사원 목록 자체의 정렬은 EmployeeQueries.sortColumns의 userName/loginId/hireDate/status와 id이며 rankOrder를 쓰지 않는다. |
| ApprovalLineQueries / Resolver | 직급 이름은 표시용 title. 결재자는 부서장·상위 부서장·특정 사원·역할로 결정한다. 역할 보유자는 user id 순, 단계는 step_order 순이며 직급 순위로 정하지 않는다. 이미 상신한 문서의 결재자 직급 스냅샷은 유지된다. |
| 실제 월간 근태표 | AttendanceMonthlyQuery는 이름·id·날짜 순서이며 직급 순위를 쓰지 않는다. |
| 기존 목 월간 근태표 | mock/m3.js는 직급 rankOrder 내림차순을 보조 정렬로 사용한다. 실제 API와 이 정렬 차이는 기존 상태이며 이번에는 수정하지 않았다. |

다른 탭에서 이미 열어 둔 사원 화면을 실시간 갱신하는 기능은 추가하지 않았다. 해당 화면 재진입 시 최신 목록을 받는다.

## 수정 전 재현

9dd304d의 frontend를 작업 트리 밖에 풀고 새 점검 스크립트의 API 어댑터 검사부터 실행했다.

```text
node scripts/check-positions.mjs
AssertionError [ERR_ASSERTION]: positionApi.create must exist
+ actual - expected
+ 'undefined'
- 'function'
exit 1
```

화면 자체가 없어 API 연결부터 실패한 것이다. 기존 backend 동작을 실패로 바꾸지는 않았다.

## 검증 결과

검증 사본: `/private/tmp/ki16-position-screen/frontend`. lockfile 그대로 `npm ci --offline --ignore-scripts --no-audit --no-fund`로 기존 의존성 244개를 설치했다. 새 라이브러리와 lockfile 변경은 없다.

- `npm run build`: exit 0, 2.86초.
- `npm run lint`: exit 0, 오류·경고 출력 없음.
- `node scripts/check-positions.mjs`: exit 0. 기존 Node assert 방식에 이미 Vite가 쓰는 esbuild와 React 서버 렌더링을 사용했다. API 요청을 캡처하며 실제 서버에 연결하지 않는다.

```text
POSITION_API methods=5 order_body=PASS
POSITION_MOCK permissions=4/4 create_201_rank_zero=PASS rename=PASS order_atomic=PASS delete_409=PASS
POSITION_VIEW permission_combinations=8/8 addition_notice=PASS server_409_message=PASS
```

쓰기 거절 후 데이터 무변경, 201·rank 0, 중복/빈 이름, 목록 이동 전 서버 순서 유지, 전체 순서 본문, 순서 중 잘못된 id/중복 id의 원자적 거절, 이름 변경, 퇴사자를 포함한 사용 중 직급 409, 미사용 직급 삭제를 검사한다. 렌더링은 C/U/D 8개 조합에서 추가·이름·이동·저장·삭제 버튼 표시와 안내·오류 메시지를 확인한다.

### 목 브라우저

별도 임시 Chrome 프로필과 외부 사본의 `npm run dev:mock`(127.0.0.1:5189)에서 확인했다. API 기본 주소를 같은 목 개발 서버의 `/api/v1`로 지정해 실제 localhost:8080 서버에 가지 않도록 했다. 등록된 가상 계정·목 데이터만 사용했다.

```text
POSITION_BROWSER create_notice=PASS rename=PASS cancel=PASS leave_unsaved=PASS save_order=PASS
POSITION_BROWSER delete_409=PASS delete_unused=PASS employee_options=PASS login_only=PASS
```

- 새 직급 생성 뒤 맨 앞과 안내 확인, 관리소장 → 대표 이름 변경, 전체 순서 저장, 취소, 이동 후 다른 페이지로 떠났다 돌아와 저장된 순서 확인.
- 사용 중 사원 직급은 확인창 뒤 409 메시지, 미사용 직급은 삭제.
- 별도 새로고침 없이 사원 등록 모달의 직급 선택지에서 대표 이름·새 순서·삭제 결과 확인.
- DEPARTMENT V/C/U/D 모두 없는 로그인 상태에서 기존 메뉴로 진입해 직급은 보이고 부서 정보·쓰기 버튼은 없는지 확인.
- 첫 브라우저 점검은 사원 페이지의 목록 로딩 전에 옵션을 단언해 실패했다. 하네스에서 옵션 응답 완료를 기다리도록 고친 뒤 위 결과가 모두 통과했다. 제품 코드 수정은 없었다.

| 뷰포트 너비 | 문서 scrollWidth | 직급 카드 scrollWidth / clientWidth |
| ---: | ---: | ---: |
| 320 | 320 | 286 / 286 |
| 375 | 375 | 341 / 341 |
| 768 | 768 | 718 / 718 |
| 1280 | 1280 | 643 / 643 |

기본·모바일 스크린샷도 확인했다. UI는 줄바꿈·min-w-0·버튼 묶음 wrap으로 가로 넘침을 방지한다. 브라우저 로그·스크린샷은 외부 검증 폴더에 있고, 실제 API에서의 화면 확인은 요청대로 Claude 리뷰에 남긴다.

### 백엔드·범위

이번 추가는 frontend와 이 문서뿐이다. 9dd304d 이후 backend 변경 0이며 앞 세 건의 최종 Java 17·UTC·Testcontainers 전체 **370/0/0/0**, 67개 클래스, 10:15분 결과를 함께 사용한다. 기준선 3개·backend/md·마이그레이션·시드·포트폴리오 재현 항목은 변경하지 않았다. 별도로 변경된 KI-10 문서는 이번 커밋에 포함하지 않는다.

커밋: 이 문서와 같은 `feat(frontend): 직급 관리 화면` 커밋. 앞 세 건은 9b77385·e8e5eb2·9dd304d다. 병합·푸시·배포와 다음 단계는 진행하지 않는다.

## 남은 한계

실제 API를 붙인 화면 확인은 리뷰에서 수행한다. 동시에 다른 관리자가 직급을 편집하는 경우 서버의 현재 계약을 그대로 따른다(새 버전 검증이나 잠금 API를 추가하지 않음). 목의 이름 중복 검사는 MariaDB 문자 비교를 완전히 구현한 것은 아니다. 알림·폴링·토큰 동작은 이번 범위 밖이다.
