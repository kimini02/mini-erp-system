# M3 최종 리뷰 — 휴직을 거친 복직의 퇴사일 초기화

## 증상
`RETIRED → ON_LEAVE → ACTIVE`로 바꾸면 재직 상태인데 `retired_date`가 남는다. 결근 배치는 재직자로 판단해 결근 행을 만들지만 월간 표는 퇴사일 이후 칸을 `NONE`으로 표시해 결근과 합계가 숨겨진다.

## 재현 방법
Java 17 + MariaDB 10.11 Testcontainers의 `M3RetirementTest.retirementThroughLeaveToActiveClearsDateAndShowsAbsenceInMonthlyTable`로 재현했다. 주입된 한국 시계를 기준으로 상태 API를 RETIRED, ON_LEAVE, ACTIVE 순서로 호출하고, 복직한 근무일의 결근 배치를 다음 날 실행한 뒤 월간 표를 조회한다.

수정 전 **1개 실행 / 1개 실패 / 오류·건너뜀 0**. 실제 결근 행의 `absent=true`는 확인됐지만, 묶음 검증 5개가 실패했다: 휴직 응답 퇴사일 NULL, 복직 응답 퇴사일 NULL, 저장된 퇴사일 NULL, 칸 ABSENT, 결근 합계 1. 실제 칸은 NONE, 합계는 0이었다. 로그: `/private/tmp/m3-retirement-transition-before.log`.

## 원인
EmployeeService.status의 CASE 둘째 조건이 `:status='ACTIVE' AND status='RETIRED'`였다. RETIRED에서 ON_LEAVE로 바꿀 때 퇴사일이 유지되고, 이후 ACTIVE로 바꿀 때 이전 상태가 ON_LEAVE여서 다시 조건에서 빠졌다.

## 검토했지만 버린 방법과 이유
- 월간 표가 ACTIVE 상태의 퇴사일을 무시하도록 변경: 잘못된 사원 상태 데이터가 남고 다른 소비 경로와 불일치가 생긴다.
- ACTIVE 전환 때만 무조건 NULL로 변경: RETIRED에서 ON_LEAVE로 바뀐 직후에도 잘못된 퇴사일이 남는다.
- 기존 데이터 일괄 UPDATE: 개발 DB 변경 금지와 이번 수정 범위를 지키기 위해 수행하지 않았다.

## 선택한 방법
사용자 요청대로 CASE 둘째 조건만 `:status<>'RETIRED' AND status='RETIRED'`로 변경했다. 퇴사 상태에서 다른 상태로 바뀌면 퇴사일을 지우며, 퇴사 전환 시 Clock 기준 날짜 기록은 유지한다. 기존 권한 검사·잠금·토큰 버전 처리와 월간 표·결근 배치 구현은 바꾸지 않았다.

## 부작용·남은 위험
이미 비퇴사 상태에 퇴사일이 남아 있는 기존 행을 자동 보정하는 변경은 아니다. 그러한 운영 데이터가 있다면 별도 확인·정리가 필요하며 이번 작업에서는 개발 DB에 접속하지 않았다. 상태 변경 이력 테이블이 없다는 기존 한계도 그대로다.

## 결과
- 수정 후 `M3RetirementTest` **5/5 성공**, 실패·오류·건너뜀 0. 직접 복직, 휴직을 거친 복직, 기존 결근 대상·재판정 검증을 유지했다. 로그: `/private/tmp/m3-retirement-transition-after.log`.
- 전체 테스트: Java 17.0.16 + MariaDB 10.11 Testcontainers. Maven 종료 코드 0, BUILD SUCCESS와 XML 보고서 합계를 모두 대조했다.

| JVM | 실행 | 성공 | 실패 | 오류 | 건너뜀 | 소요 |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| 기본 (Asia/Seoul) | 241 | 241 | 0 | 0 | 0 | 15분 51초 |
| UTC | 241 | 241 | 0 | 0 | 0 | 15분 50초 |

두 JVM의 전체 실행에서도 M3RetirementTest 5개가 모두 통과했다. 테스트 XML의 java.version과 user.timezone으로 실행 환경을 확인했다. 전체 로그는 `/private/tmp/m3-retirement-transition-full-default.log`, `/private/tmp/m3-retirement-transition-full-utc.log`다.

실행 명령:
```sh
cd /Users/kimmini/sk-final-deepfake/mini-erp-system/backend
export JAVA_HOME=/Users/kimmini/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home
./mvnw test
./mvnw -Duser.timezone=UTC test
```

기본 JVM 검증은 수정된 추적 backend 257개 파일을 임시 폴더에 복사해 UTC 실행과 빌드 경로를 분리했다. 검증 전후 SHA-256으로 원본과 257개 파일의 동일성을 확인했고, 검증이 끝난 임시 복사본은 삭제했다. 소스 기준은 `ab6f267`에 이번 EmployeeService 조건 변경과 M3RetirementTest 회귀 1개를 추가한 상태이며, 전체 검증 중 코드·테스트는 변경하지 않았다. 이후에는 이 결과 문서만 갱신했다.

변경 파일:
- `backend/src/main/java/com/minierp/backend/domain/user/service/EmployeeService.java`
- `backend/src/test/java/com/minierp/backend/integration/M3RetirementTest.java`
- `docs/troubleshooting/M3-18-retirement-state-transition.md`

frontend/, backend/md/, V1~V5, 미추적 .claude/, 개발 DB는 변경하지 않았다. 새 라이브러리·스키마 변경·병합·푸시는 없다.
