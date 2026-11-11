# KI-09 — 목 모드 내 프로필 흰 화면 수정

## 증상과 재현

목 모드 사원 계정으로 로그인 → 우측 상단 사용자 메뉴 → 내 프로필. 수정 전 실제 브라우저에서 페이지가 빈 화면이 되고 다음 콘솔 오류를 확인했다.

```text
Objects are not valid as a React child (found: object with keys {id, name}).
```

검증에는 작업 트리 밖 프론트 사본과 `npm run dev:mock -- --host 127.0.0.1 --port ... --strictPort`를 사용했다. 서버·개발 DB/Redis 호출 없이 가짜 사원(김서연, 개발1팀)을 사용했다.

## 원인

로그인용 `userView`에는 `departmentName`이 있지만 사원 목록·상세의 `userListView`에는 없었다. 상세 응답을 받은 `ProfileModal`이 `departmentName || department`로 고르면서 `{id, name}` 객체를 React 자식에 전달했다.

## 검토했지만 버린 방법과 이유

- 목 응답만 보완: `departmentName` 없는 객체 응답에서 화면이 다시 실패할 수 있으므로 객체의 `name`을 선택하는 화면 처리도 함께 수정했다.
- 객체를 `String(...)`으로 변환: `[object Object]`가 표시되므로 사용하지 않았다.
- 프로필·인증 흐름 전체 재설계: 이번 버그 수정 범위를 넘으므로 하지 않았다.

## 선택한 방법

- `userListView`에 `departmentName: dept?.name ?? null`을 추가했다.
- `ProfileModal`은 부서가 문자열이면 그대로, 객체이면 `department?.name`을 사용한다. 기존 `departmentName` 우선순위와 부서 코드·미지정 표시를 유지한다.
- 수정한 프론트 소스는 `src/api/mock/index.js`, `src/components/layout/ProfileModal.jsx` 두 파일뿐이다.

## 전후 결과

| 검증 | 수정 전 | 수정 후 |
| --- | --- | --- |
| 실제 목 모드에서 내 프로필 열기 | 화면 전체 흰 화면, React 오류 | 모달 표시, 김서연·개발1팀·잔여 연차 표시, 콘솔 오류 0 |
| 실제 컴포넌트 렌더 — `departmentName` 우선 | 통과 | 통과 |
| `departmentName` 없이 `{id, name}` 부서 | React 자식 오류 | 부서 이름 표시 |
| 문자열 부서 / 코드만 / null 부서 | 각 표시 정상 | 각 표시 정상 |
| `npm run build` | 이번 작업에서 미실행 | 종료 코드 0, Vite 빌드 4.80초 |

컴포넌트 확인은 기존 Vite SSR·React DOM의 `renderToStaticMarkup`으로 실제 `ProfileModal`을 렌더링한 일회성 검사다. 새 패키지·프로젝트 테스트 도구를 추가하지 않았다. 효과 함수가 실행되지 않으므로 서버를 호출하지 않는다. 최초 검사 도구 종료 중 Vite 의존성 탐색이 남아 경고를 출력했으며, 도구의 불필요한 의존성 탐색을 끄고 다시 실행해 위 5개 경우를 확인했다. 제품 코드는 바꾸지 않았다.

## 부작용·남은 위험 / 결과

- 실제 서버 화면은 이번 작업에서 검증하지 않았다. 실제 응답의 `departmentName`을 우선하는 동작은 유지한다.
- 목·화면 관련 기준선은 변경하지 않았다. 기존 화면 폴링·인증·프로필 비밀번호 변경 흐름은 수정하지 않았다.
- 백엔드 전체 UTC 테스트 결과는 [KI-10 기록](KI-10-client-error-status.md)을 따른다.

## 커밋

`fix(frontend): 목 모드 내 프로필 흰 화면 수정`. 이 문서의 최초 커밋은 `git log --diff-filter=A --format='%h %s' -- docs/troubleshooting/KI-09-mock-profile.md`로 확인한다.
