# M3 3차 리뷰 수정 결과

## 기준·범위
- 브랜치 `feat/m3-attendance`, 시작 `88d59f2`, 최종 소스 `6d1d062`.
- 기준은 `feat/m3-frontend`의 `de9d307:backend/md/14-rest-api-v2-m3.md`. git show로 읽기만 했다.
- 여섯 항목별 독립 커밋. frontend/, backend/md/, V1~V5, pom.xml, 미추적 .claude/, remainingAnnualLeave 호환 필드를 변경하지 않았다. 스키마 변경·새 라이브러리·병합·푸시는 없다.
- 검증은 Java 17 및 MariaDB 10.11 Testcontainers. 로컬 개발 DB는 접속·수정하지 않았다.

## 작업별 커밋·테스트·문서
| 작업 | 커밋 | 테스트 | 기록 |
| --- | --- | --- | --- |
| 결근 대상 | `b130515` | M3RetirementTest, M3BatchTest | [M3-11-absence-eligibility.md](M3-11-absence-eligibility.md) |
| 정정 상신 검사 | `9da9d04` | M3AttendanceFixTest | [M3-12-correction-submit-validation.md](M3-12-correction-submit-validation.md) |
| 미래 부여 날짜 차단 | `bd3879e` | M3GrantRunDateTest | [M3-13-grant-run-date.md](M3-13-grant-run-date.md) |
| 실행 연도·옛 GRANT 정책 | `f73cc0e` | M3LeaveGrantTest | [M3-14-current-year-legacy-grants.md](M3-14-current-year-legacy-grants.md) |
| 실행 API 응답 | `6d1d062` | M3BatchResponseTest, M3BatchTest | [M3-15-batch-run-response.md](M3-15-batch-run-response.md) |
| CSV 한글 칸 | `315fcce` | M3CsvLabelsTest | [M3-16-csv-korean-labels.md](M3-16-csv-korean-labels.md) |

## 수정 전 실패와 수정 후 검증
처음 추가한 회귀 묶음: **20 실행 / 9 실패 / 오류 0 / 건너뜀 0**. 운영 코드를 바꾸기 전에 `/private/tmp/m3-phase3-before.log`로 재현했다. 응답 두 API는 별도 테스트로 나눠 수정 전 **2/2 실패**를 추가 확인했다(`/private/tmp/m3-phase3-response-before.log`; 같은 실행의 연차 테스트 6개는 통과). 테스트 결과를 합산해 중복 재현을 별도 버그로 세지 않는다.

| 문제·검증 | 수정 전 | 수정 후 |
| --- | --- | --- |
| 퇴사일 NULL RETIRED | 0행 기대, 2행 생성 | 0행. 초대 대기 사원도 0행, 복직은 다시 대상 |
| 결근일 퇴근만 정정 상신 | 422 기대, 200 | 422 ATTENDANCE_FIX_CLOCK_IN_REQUIRED, 기록·문서·알림 유지 |
| 기존 퇴근 이후 출근 정정 상신 | 400 기대, 200 | 400 INVALID_ATTENDANCE_TIME |
| 상신 후 출근 기록이 사라진 문서 승인 | 422 기대, 200 | 승인 재검사 422, 상태·알림 유지 |
| 미래 날짜 부여 | 400 기대, 200 | 내일·다음 해 1/1 모두 400, 원장·실행 행 증가 없음 |
| 지난 해 자동 부여 | 0행 기대, 1행 | 지난 해 0행, 올해 놓친 부여는 생성 |
| 옛 GRANT 있는 해 자동 부여 | 0행 기대, 14행 | 그해 자동 0행, 다음 해 Y 16일, 옛 7일 보존 |
| 실행 API 응답 | grant/rejudge 각각 필드 계약 실패 | 9개 필드, 실제 ID 행·GET 목록과 일치 |
| CSV | 정상 기대, ON_TIME | 9종 한글, HOLIDAY/NONE/FUTURE 빈칸; JSON 코드 유지 |

수정 후 관련 묶음은 **23/23 통과**(`/private/tmp/m3-phase3-after-main.log`). 응답 수정 후 M3BatchTest 4개와 미래 날짜 1개 통과. 응답 검증 초안의 JSON 숫자 노드 타입 비교 문제를 바로잡은 최종 응답 회귀는 **2/2 통과**(`/private/tmp/m3-phase3-response-final.log`). 수정 전 제품 결함과 이 테스트 비교 문제를 구분한다.

## 전체 테스트
최종 소스 `6d1d062`, Java 17.0.16에서 완료했다. Maven 종료 코드와 XML 보고서 합계를 대조했다.

| 실행 | 총 실행 | 성공 | 실패 | 오류 | 건너뜀 | 소요 |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| 기본 JVM (Asia/Seoul) | 240 | 240 | 0 | 0 | 0 | 12분 05초 |
| UTC JVM | 240 | 240 | 0 | 0 | 0 | 12분 08초 |

원본과 임시 복사본의 추적된 backend 257개 파일이 동일함을 확인했다. 테스트 XML에서도 Java 버전과 user.timezone을 확인했다. 이후에는 보고 문서만 변경한다. 전체 로그: `/private/tmp/m3-phase3-final-default.log`, `/private/tmp/m3-phase3-final-utc.log`.

기존 동시 자동 부여 검증: 두 JVM 모두 2스레드 × 10회에서 중복 0건. 월간 표 50명 × 31일은 데이터 조회 1회, EXPLAIN의 `uk_attendances_user_date` 사용을 확인했다. 예열 3회·표본 9회 중앙값은 기본 JVM 인덱스 제외 392.937ms → 적용 28.865ms, UTC JVM 414.735ms → 17.672ms다. 같은 SQL에서 인덱스를 제외한 통제 실험이며 운영 성능 보장치가 아니다.

```sh
cd /Users/kimmini/sk-final-deepfake/mini-erp-system/backend
export JAVA_HOME=/Users/kimmini/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home
./mvnw test
./mvnw -Duser.timezone=UTC test
```

기본 JVM 실행은 최종 소스 커밋의 추적된 backend만 git archive로 임시 폴더에 추출해 UTC 실행과 빌드 디렉터리를 분리했다. 검증 후 직접 만든 임시 복사본은 삭제했다. 기존 초대·비밀번호 재설정 검증의 Redis 컨테이너 외 새 Redis 사용은 없다.

## 명세 차이·결정·남은 위험
- 새 기준 명세와 다른 구현은 없다. 이전 보고서의 ‘퇴사일 NULL이면 제한 없음’, ‘지난 해 부여도 소급’, ‘출근 없이 퇴근만 정정 허용’ 판단은 이번 확정 명세와 수정으로 대체된다.
- 입사일 없음과 옛 GRANT에 동시에 해당하는 사람은 입사일 없음으로 한 번 센다. message는 각 사유의 사원 ID 목록을 구분한다.
- 상태·계정 활성화는 배치 시점 기준이다. 과거 휴직·퇴사·비활성 기간을 복원할 상태 변경 이력과 과거 근무시간 이력은 기존 결정 필요 항목으로 남는다.
- 이미 잘못 저장된 결근·자동 부여 데이터를 자동 삭제하지 않는다. 개발 DB 데이터는 검증하거나 수정하지 않았다.
- remainingAnnualLeave의 전체 연도 합산 호환 동작은 사용자 지시대로 유지했다. 프론트의 /leave/balance 전환은 전달받은 정보이며 이번 작업에서 화면을 검증하지 않았다.
- 작업 트랜잭션과 실행 기록 트랜잭션은 기존 경계를 유지한다. 작업 커밋 뒤 실행 기록 저장이 실패하는 운영 장애의 원자성 개선은 범위 밖이다.
- 그 외 새로 재현한 미수정 버그는 없다.

## 변경 파일 전체

### b130515 — 결근 대상

- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AbsenceBatchWork.java`
- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceStore.java`
- `backend/src/test/java/com/minierp/backend/integration/M3RetirementTest.java`
- `docs/troubleshooting/M3-11-absence-eligibility.md`

### 9da9d04 — 정정 상신 검사

- `backend/src/main/java/com/minierp/backend/domain/attendance/handler/AttendanceFixFormHandler.java`
- `backend/src/main/java/com/minierp/backend/global/exception/ErrorCode.java`
- `backend/src/test/java/com/minierp/backend/integration/M3AttendanceFixTest.java`
- `docs/troubleshooting/M3-12-correction-submit-validation.md`

### bd3879e — 미래 부여 날짜 차단

- `backend/src/main/java/com/minierp/backend/domain/leave/service/LeaveGrantService.java`
- `backend/src/test/java/com/minierp/backend/integration/M3GrantRunDateTest.java`
- `docs/troubleshooting/M3-13-grant-run-date.md`

### f73cc0e — 실행 연도·옛 GRANT 정책

- `backend/src/main/java/com/minierp/backend/domain/leave/service/LeaveGrantRules.java`
- `backend/src/main/java/com/minierp/backend/domain/leave/service/LeaveGrantWork.java`
- `backend/src/test/java/com/minierp/backend/integration/M3LeaveGrantTest.java`
- `docs/troubleshooting/M3-14-current-year-legacy-grants.md`

### 6d1d062 — 실행 API 응답

- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AbsenceBatchService.java`
- `backend/src/main/java/com/minierp/backend/domain/leave/service/LeaveGrantService.java`
- `backend/src/main/java/com/minierp/backend/global/batch/BatchRunRecorder.java`
- `backend/src/test/java/com/minierp/backend/integration/M3BatchResponseTest.java`
- `docs/troubleshooting/M3-15-batch-run-response.md`

### 315fcce — CSV 한글 칸

- `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java`
- `backend/src/test/java/com/minierp/backend/integration/M3CsvLabelsTest.java`
- `docs/troubleshooting/M3-16-csv-korean-labels.md`

최종 보고 문서 커밋은 `docs/known-issues.md`와 이 보고서만 포함한다.
