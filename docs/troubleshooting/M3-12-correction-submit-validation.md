# M3 정정 상신 시 기존 기록과 합친 시각 검사

기준: `de9d307:backend/md/14-rest-api-v2-m3.md` (읽기 전용). Java 17 + MariaDB 10.11 Testcontainers.

## 증상
출근 없는 날 퇴근만 정정하거나 현재 퇴근 이후로 출근만 정정해도 상신된다.

## 재현 방법
M3AttendanceFixTest의 absentDayCheckoutOnlyIsRejectedAtSubmission, clockInAfterExistingClockOutIsRejectedAtSubmission, approvalRechecksMergedTimesWhenAttendanceChangedSinceSubmission. 수정 전 각각 422/400/422 기대 대신 200으로 3/3 실패, 수정 후 3/3 성공.

## 원인
본문에 두 시각이 함께 있으면 형식 검사했지만 validate는 현재 기록과 병합한 결과를 검사하지 않았다. 승인에서도 출근 누락은 거부하지 않았다.

## 검토했지만 버린 방법과 이유
승인 때만 검사하면 결재자가 처리할 수 없는 문서가 올라온다. 클라이언트 검사만으로 제한하면 우회되므로 서버 검사로 정했다.

## 선택한 방법
공통 mergedTimes가 정정값 우선으로 현재 값과 합치고 출근 필수(422 ATTENDANCE_FIX_CLOCK_IN_REQUIRED), 퇴근 > 출근(400 INVALID_ATTENDANCE_TIME)을 검사한다. validate와 onApproved가 같은 함수를 사용한다. 기존 퇴근만 정정 성공 테스트는 출근이 존재하는 정상 시나리오로 바꿨다.

## 부작용·남은 위험
상신 이후 실제 기록이 달라질 수 있으므로 승인 시 잠금 읽기와 재검사를 유지한다. 거부 시 문서 상태·근태·알림이 유지되고 상신 before 스냅샷도 남지 않음을 검사한다.

## 결과
M3AttendanceFixTest 7/7 성공. 정상 승인, 반려·회수, 알림 실패 롤백, 중복 승인, 상신 검사와 승인 안전장치 포함.

검증 로그: `/private/tmp/m3-phase3-before.log`, `/private/tmp/m3-phase3-after-main.log`. 전체 기본·UTC 결과는 최종 3차 보고서에 기록한다.
