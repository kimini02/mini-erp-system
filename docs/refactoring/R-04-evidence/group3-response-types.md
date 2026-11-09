# 묶음 3 응답 구성요소 대조

기준 d693843의 실제 map 인자·put 값·SELECT 별칭을 새 record 구성요소와 대조한다. 행의 정수는 `SqlStore.number`의 long, SUM은 BigDecimal, 날짜는 SqlStore가 변환한 LocalDate/LocalDateTime이다. 여러 출처가 같은 record를 만드는 경우 모두 남겼다. 원문 식의 SQL/행 키는 수정하지 않았다.

| record · 구성요소 | 타입 | 시작 코드의 실제 식/열 | 근거 위치 |
|---|---|---|---|
| `AttendanceScheduleResponseDto.startTime` | `String` | `start.format(f)` | `backend/src/main/java/com/minierp/backend/domain/attendance/repository/AttendanceStore.java:20` |
| `AttendanceScheduleResponseDto.endTime` | `String` | `end.format(f)` | `backend/src/main/java/com/minierp/backend/domain/attendance/repository/AttendanceStore.java:20` |
| `AttendanceScheduleResponseDto.lateGraceMin` | `int` | `grace` | `backend/src/main/java/com/minierp/backend/domain/attendance/repository/AttendanceStore.java:20` |
| `AttendanceScheduleResponseDto.halfBoundary` | `String` | `start.plusHours(5).format(f)` | `backend/src/main/java/com/minierp/backend/domain/attendance/repository/AttendanceStore.java:21` |
| `AttendanceScheduleResponseDto.source` | `String` | `source` | `backend/src/main/java/com/minierp/backend/domain/attendance/repository/AttendanceStore.java:21` |
| `AttendanceScheduleResponseDto.departmentName` | `String` | `department` | `backend/src/main/java/com/minierp/backend/domain/attendance/repository/AttendanceStore.java:21` |
| `AttendanceScheduleDetailResponseDto.startTime` | `String` | `start.format(f)` | `backend/src/main/java/com/minierp/backend/domain/attendance/repository/AttendanceStore.java:20` |
| `AttendanceScheduleDetailResponseDto.endTime` | `String` | `end.format(f)` | `backend/src/main/java/com/minierp/backend/domain/attendance/repository/AttendanceStore.java:20` |
| `AttendanceScheduleDetailResponseDto.lateGraceMin` | `int` | `grace` | `backend/src/main/java/com/minierp/backend/domain/attendance/repository/AttendanceStore.java:20` |
| `AttendanceScheduleDetailResponseDto.halfBoundary` | `String` | `start.plusHours(5).format(f)` | `backend/src/main/java/com/minierp/backend/domain/attendance/repository/AttendanceStore.java:21` |
| `AttendanceTodayResponseDto.workDate` | `LocalDate` | `date` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:67` |
| `AttendanceTodayResponseDto.dayType` | `String` | `day` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:67` |
| `AttendanceTodayResponseDto.holidayName` | `String` | `context.holiday()` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:67` |
| `AttendanceTodayResponseDto.schedule` | `attendance.dto.AttendanceScheduleResponseDto` | `schedule.view()` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:67` |
| `AttendanceTodayResponseDto.clockInAt` | `LocalDateTime` | `in` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:68` |
| `AttendanceTodayResponseDto.clockOutAt` | `LocalDateTime` | `out` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:68` |
| `AttendanceTodayResponseDto.inResult` | `String` | `row == null ? null : row.get("in_result")` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:68` |
| `AttendanceTodayResponseDto.outResult` | `String` | `row == null ? null : row.get("out_result")` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:69` |
| `AttendanceTodayResponseDto.leave` | `attendance.dto.AttendanceTodayResponseDto.Leave` | `context.leave()` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:69` |
| `AttendanceTodayResponseDto.canClockIn` | `boolean` | `in == null && !context.type().equals("LEAVE") && date.equals(now.toLocalDate())` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:70` |
| `AttendanceTodayResponseDto.canClockOut` | `boolean` | `in != null` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:71` |
| `AttendanceTodayResponseDto.earlyIfNow` | `boolean` | `"EARLY".equals(hypothetical.outResult())` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:71` |
| `AttendanceTodayResponseDto.monthLateCount` | `long` | `late` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:72` |
| `AttendanceTodayResponseDto.monthAbsentCount` | `long` | `absent` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:72` |
| `AttendanceTodayResponseDto.Leave.type` | `String` | `r.get("leave_type")` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:41` |
| `AttendanceTodayResponseDto.Leave.documentId` | `long` | `r.get("leave_id")` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:41` |
| `AttendanceTodayResponseDto.Leave.docNo` | `String` | `r.get("leave_no")` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:41` |
| `AttendanceMyMonthResponseDto.EmptyDay.date` | `LocalDate` | `date` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:156` |
| `AttendanceMyMonthResponseDto.EmptyDay.dayType` | `String` | `type` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:156` |
| `AttendanceMyMonthResponseDto.RecordedDay.date` | `LocalDate` | `date` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:175` |
| `AttendanceMyMonthResponseDto.RecordedDay.dayType` | `String` | `type` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:175` |
| `AttendanceMyMonthResponseDto.RecordedDay.clockInAt` | `LocalDateTime` | `row.get("clock_in_at")` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:175` |
| `AttendanceMyMonthResponseDto.RecordedDay.clockOutAt` | `LocalDateTime` | `row.get("clock_out_at")` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:176` |
| `AttendanceMyMonthResponseDto.RecordedDay.inResult` | `String` | `row.get("in_result")` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:176` |
| `AttendanceMyMonthResponseDto.RecordedDay.outResult` | `String` | `row.get("out_result")` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:177` |
| `AttendanceMyMonthResponseDto.RecordedDay.absent` | `boolean` | `bool(row, "absent")` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:177` |
| `AttendanceMyMonthResponseDto.RecordedDay.fixDocument` | `attendance.dto.AttendanceDocumentResponseDto` | `row.get("fix_document_id") == null ? null : map("id", row.get("fix_document_id"), "docNo", row.get("fix_doc_no"))` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:178` |
| `AttendanceMyMonthResponseDto.month` | `String` | `month.toString()` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:182` |
| `AttendanceMyMonthResponseDto.summary` | `attendance.dto.AttendanceMyMonthResponseDto.Summary` | `map("workDays", work, "present", present, "late", late, "early", early, "absent", absent, "missing", missing, "leaveDays", leave)` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:183` |
| `AttendanceMyMonthResponseDto.days` | `List<attendance.dto.AttendanceMyMonthResponseDto.Day>` | `days` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:184` |
| `AttendanceMyMonthResponseDto.Summary.workDays` | `int` | `work` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:183` |
| `AttendanceMyMonthResponseDto.Summary.present` | `int` | `present` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:183` |
| `AttendanceMyMonthResponseDto.Summary.late` | `int` | `late` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:183` |
| `AttendanceMyMonthResponseDto.Summary.early` | `int` | `early` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:183` |
| `AttendanceMyMonthResponseDto.Summary.absent` | `int` | `absent` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:183` |
| `AttendanceMyMonthResponseDto.Summary.missing` | `int` | `missing` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:184` |
| `AttendanceMyMonthResponseDto.Summary.leaveDays` | `double` | `leave` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:184` |
| `AttendanceDocumentResponseDto.id` | `long` | `row.get("fix_document_id")` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:180` |
| `AttendanceDocumentResponseDto.docNo` | `String` | `row.get("fix_doc_no")` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceService.java:180` |
| `AttendanceMonthlyResponseDto.Day.date` | `LocalDate` | `date` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:58` |
| `AttendanceMonthlyResponseDto.Day.weekday` | `String` | `date.getDayOfWeek().toString().substring(0, 3)` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:58` |
| `AttendanceMonthlyResponseDto.Day.dayType` | `String` | `r.get("calendar_type")` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:59` |
| `AttendanceMonthlyResponseDto.Day.holidayName` | `String` | `r.get("holiday_name")` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:59` |
| `AttendanceMonthlyResponseDto.Row.user` | `attendance.dto.AttendanceMonthlyResponseDto.User` | `map("id", id, "name", r.get("user_name"), "title", r.get("title"), "department", r.get("department"))` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:66` |
| `AttendanceMonthlyResponseDto.Row.cells` | `List<attendance.dto.AttendanceMonthlyResponseDto.Cell>` | `new ArrayList<Map<String, Object>>()` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:67` |
| `AttendanceMonthlyResponseDto.Row.totals` | `attendance.dto.AttendanceMonthlyResponseDto.Totals` | `map("late", 0, "early", 0, "absent", 0, "missing", 0, "leaveDays", 0.0)` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:68` |
| `AttendanceMonthlyResponseDto.User.id` | `long` | `id` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:66` |
| `AttendanceMonthlyResponseDto.User.name` | `String` | `r.get("user_name")` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:66` |
| `AttendanceMonthlyResponseDto.User.title` | `String` | `r.get("title")` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:66` |
| `AttendanceMonthlyResponseDto.User.department` | `String` | `r.get("department")` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:67` |
| `AttendanceMonthlyResponseDto.Totals.late` | `int` | `0` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:68` |
| `AttendanceMonthlyResponseDto.Totals.early` | `int` | `0` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:68` |
| `AttendanceMonthlyResponseDto.Totals.absent` | `int` | `0` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:68` |
| `AttendanceMonthlyResponseDto.Totals.missing` | `int` | `0` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:68` |
| `AttendanceMonthlyResponseDto.Totals.leaveDays` | `double` | `0.0` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:68` |
| `AttendanceMonthlyResponseDto.Cell.date` | `LocalDate` | `date` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:76` |
| `AttendanceMonthlyResponseDto.Cell.code` | `String` | `code` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:76` |
| `AttendanceMonthlyResponseDto.month` | `String` | `month.toString()` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:88` |
| `AttendanceMonthlyResponseDto.days` | `Collection<attendance.dto.AttendanceMonthlyResponseDto.Day>` | `days.values()` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:88` |
| `AttendanceMonthlyResponseDto.rows` | `List<attendance.dto.AttendanceMonthlyResponseDto.Row>` | `rows.values()` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:88` |
| `AttendanceMonthlyResponseDto.legend` | `List<String>` | `LEGEND` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:88` |
| `AttendanceRecordResponseDto.user` | `attendance.dto.AttendanceRecordResponseDto.User` | `map("id", user, "name", u.get("user_name"))` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:128` |
| `AttendanceRecordResponseDto.date` | `LocalDate` | `date` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:128` |
| `AttendanceRecordResponseDto.code` | `String` | `code` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:128` |
| `AttendanceRecordResponseDto.clockInAt` | `LocalDateTime` | `in` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:128` |
| `AttendanceRecordResponseDto.clockOutAt` | `LocalDateTime` | `out` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:129` |
| `AttendanceRecordResponseDto.schedule` | `attendance.dto.AttendanceScheduleDetailResponseDto` | `schedule` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:129` |
| `AttendanceRecordResponseDto.reason` | `String` | `reason` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:129` |
| `AttendanceRecordResponseDto.leaveDocument` | `attendance.dto.AttendanceDocumentResponseDto` | `day.leave() == null ? null : map("id", day.leave().get("documentId"), "docNo", day.leave().get("docNo"))` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:130` |
| `AttendanceRecordResponseDto.fixDocument` | `attendance.dto.AttendanceDocumentResponseDto` | `row == null \|\| row.get("fix_document_id") == null ? null : map("id", row.get("fix_document_id"), "docNo", row.get("fix_doc_no"))` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:133` |
| `AttendanceRecordResponseDto.judgedAt` | `LocalDateTime` | `row == null ? null : row.get("judged_at")` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:136` |
| `AttendanceRecordResponseDto.User.id` | `long` | `user` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:128` |
| `AttendanceRecordResponseDto.User.name` | `String` | `u.get("user_name")` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:128` |
| `AttendanceDocumentResponseDto.id` | `long` | `day.leave().get("documentId")` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:132` |
| `AttendanceDocumentResponseDto.docNo` | `String` | `day.leave().get("docNo")` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:132` |
| `AttendanceDocumentResponseDto.id` | `long` | `row.get("fix_document_id")` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:135` |
| `AttendanceDocumentResponseDto.docNo` | `String` | `row.get("fix_doc_no")` | `backend/src/main/java/com/minierp/backend/domain/attendance/service/AttendanceAdminService.java:135` |
| `AttendanceBatchRunResponseDto.id` | `long` | `SELECT id — SELECT id,job_name AS jobName,target_date AS targetDate,status,processed_count AS processedCount,skipped_count AS skippedCount,message,started_at AS startedAt,finished_at AS finishedAt FROM batch_runs WHERE id=:id` | `backend/src/main/java/com/minierp/backend/global/batch/BatchRunRecorder.java:17` |
| `AttendanceBatchRunResponseDto.jobName` | `String` | `SELECT jobName — SELECT id,job_name AS jobName,target_date AS targetDate,status,processed_count AS processedCount,skipped_count AS skippedCount,message,started_at AS startedAt,finished_at AS finishedAt FROM batch_runs WHERE id=:id` | `backend/src/main/java/com/minierp/backend/global/batch/BatchRunRecorder.java:17` |
| `AttendanceBatchRunResponseDto.targetDate` | `LocalDate` | `SELECT targetDate — SELECT id,job_name AS jobName,target_date AS targetDate,status,processed_count AS processedCount,skipped_count AS skippedCount,message,started_at AS startedAt,finished_at AS finishedAt FROM batch_runs WHERE id=:id` | `backend/src/main/java/com/minierp/backend/global/batch/BatchRunRecorder.java:17` |
| `AttendanceBatchRunResponseDto.status` | `String` | `SELECT status — SELECT id,job_name AS jobName,target_date AS targetDate,status,processed_count AS processedCount,skipped_count AS skippedCount,message,started_at AS startedAt,finished_at AS finishedAt FROM batch_runs WHERE id=:id` | `backend/src/main/java/com/minierp/backend/global/batch/BatchRunRecorder.java:17` |
| `AttendanceBatchRunResponseDto.processedCount` | `long` | `SELECT processedCount — SELECT id,job_name AS jobName,target_date AS targetDate,status,processed_count AS processedCount,skipped_count AS skippedCount,message,started_at AS startedAt,finished_at AS finishedAt FROM batch_runs WHERE id=:id` | `backend/src/main/java/com/minierp/backend/global/batch/BatchRunRecorder.java:17` |
| `AttendanceBatchRunResponseDto.skippedCount` | `long` | `SELECT skippedCount — SELECT id,job_name AS jobName,target_date AS targetDate,status,processed_count AS processedCount,skipped_count AS skippedCount,message,started_at AS startedAt,finished_at AS finishedAt FROM batch_runs WHERE id=:id` | `backend/src/main/java/com/minierp/backend/global/batch/BatchRunRecorder.java:17` |
| `AttendanceBatchRunResponseDto.message` | `String` | `SELECT message — SELECT id,job_name AS jobName,target_date AS targetDate,status,processed_count AS processedCount,skipped_count AS skippedCount,message,started_at AS startedAt,finished_at AS finishedAt FROM batch_runs WHERE id=:id` | `backend/src/main/java/com/minierp/backend/global/batch/BatchRunRecorder.java:17` |
| `AttendanceBatchRunResponseDto.startedAt` | `LocalDateTime` | `SELECT startedAt — SELECT id,job_name AS jobName,target_date AS targetDate,status,processed_count AS processedCount,skipped_count AS skippedCount,message,started_at AS startedAt,finished_at AS finishedAt FROM batch_runs WHERE id=:id` | `backend/src/main/java/com/minierp/backend/global/batch/BatchRunRecorder.java:17` |
| `AttendanceBatchRunResponseDto.finishedAt` | `LocalDateTime` | `SELECT finishedAt — SELECT id,job_name AS jobName,target_date AS targetDate,status,processed_count AS processedCount,skipped_count AS skippedCount,message,started_at AS startedAt,finished_at AS finishedAt FROM batch_runs WHERE id=:id` | `backend/src/main/java/com/minierp/backend/global/batch/BatchRunRecorder.java:17` |
| `NotificationItemResponseDto.id` | `long` | `n.get("id")` | `backend/src/main/java/com/minierp/backend/domain/notification/service/NotificationQueryService.java:32` |
| `NotificationItemResponseDto.type` | `String` | `n.get("type")` | `backend/src/main/java/com/minierp/backend/domain/notification/service/NotificationQueryService.java:32` |
| `NotificationItemResponseDto.title` | `String` | `n.get("title")` | `backend/src/main/java/com/minierp/backend/domain/notification/service/NotificationQueryService.java:32` |
| `NotificationItemResponseDto.link` | `String` | `n.get("link")` | `backend/src/main/java/com/minierp/backend/domain/notification/service/NotificationQueryService.java:32` |
| `NotificationItemResponseDto.read` | `boolean` | `n.get("read_at") != null` | `backend/src/main/java/com/minierp/backend/domain/notification/service/NotificationQueryService.java:33` |
| `NotificationItemResponseDto.createdAt` | `LocalDateTime` | `n.get("created_at")` | `backend/src/main/java/com/minierp/backend/domain/notification/service/NotificationQueryService.java:33` |
| `NotificationUnreadCountResponseDto.count` | `long` | `queries.countUnread(access.actor())` | `backend/src/main/java/com/minierp/backend/domain/notification/service/NotificationQueryService.java:39` |
| `ProjectSummaryResponseDto.id` | `long` | `p.get("id")` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:77` |
| `ProjectSummaryResponseDto.key` | `String` | `p.get("project_key")` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:77` |
| `ProjectSummaryResponseDto.title` | `String` | `p.get("title")` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:77` |
| `ProjectSummaryResponseDto.content` | `String` | `p.get("content")` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:77` |
| `ProjectSummaryResponseDto.status` | `String` | `p.get("status")` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:78` |
| `ProjectSummaryResponseDto.priority` | `String` | `p.get("priority")` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:78` |
| `ProjectSummaryResponseDto.startDate` | `LocalDate` | `p.get("start_date")` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:78` |
| `ProjectSummaryResponseDto.endDate` | `LocalDate` | `p.get("end_date")` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:79` |
| `ProjectSummaryResponseDto.leader` | `project.dto.ProjectPersonResponseDto` | `p.get("leader_id") == null ? null : map("id", p.get("leader_id"), "name", p.get("leader_name"), "title", p.get("leader_title"))` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:80` |
| `ProjectSummaryResponseDto.memberCount` | `long` | `p.get("member_count")` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:83` |
| `ProjectSummaryResponseDto.taskCount` | `long` | `total` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:83` |
| `ProjectSummaryResponseDto.doneCount` | `long` | `done` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:83` |
| `ProjectSummaryResponseDto.overdueCount` | `java.math.BigDecimal` | `p.get("overdue_count")` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:84` |
| `ProjectSummaryResponseDto.progressPct` | `long` | `total == 0 ? 0 : done * 100 / total` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:84` |
| `ProjectSummaryResponseDto.myRole` | `String` | `p.get("leader_id") != null && number(p, "leader_id") == actor() ? "LEADER" : number(p, "is_member") > 0 ? "MEMBER" : null` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:85` |
| `ProjectPersonResponseDto.id` | `long` | `p.get("leader_id")` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:82` |
| `ProjectPersonResponseDto.name` | `String` | `p.get("leader_name")` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:82` |
| `ProjectPersonResponseDto.title` | `String` | `p.get("leader_title")` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:82` |
| `ProjectPermissionsResponseDto.canManage` | `boolean` | `manage` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:92` |
| `ProjectPermissionsResponseDto.canCreateTask` | `boolean` | `manage` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:92` |
| `ProjectPersonResponseDto.id` | `long` | `SELECT id — SELECT u.id,u.user_name AS name,pos.name AS title FROM project_members m JOIN users u ON u.id=m.user_id LEFT JOIN positions pos ON pos.id=u.position_id WHERE m.project_id=:id ORDER BY u.user_name,u.id` | `backend/src/main/java/com/minierp/backend/domain/project/repository/ProjectQueries.java:21` |
| `ProjectPersonResponseDto.name` | `String` | `SELECT name — SELECT u.id,u.user_name AS name,pos.name AS title FROM project_members m JOIN users u ON u.id=m.user_id LEFT JOIN positions pos ON pos.id=u.position_id WHERE m.project_id=:id ORDER BY u.user_name,u.id` | `backend/src/main/java/com/minierp/backend/domain/project/repository/ProjectQueries.java:21` |
| `ProjectPersonResponseDto.title` | `String` | `SELECT title — SELECT u.id,u.user_name AS name,pos.name AS title FROM project_members m JOIN users u ON u.id=m.user_id LEFT JOIN positions pos ON pos.id=u.position_id WHERE m.project_id=:id ORDER BY u.user_name,u.id` | `backend/src/main/java/com/minierp/backend/domain/project/repository/ProjectQueries.java:21` |
| `ProjectDetailResponseDto.id` | `long` | `p.get("id")` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:77` |
| `ProjectDetailResponseDto.key` | `String` | `p.get("project_key")` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:77` |
| `ProjectDetailResponseDto.title` | `String` | `p.get("title")` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:77` |
| `ProjectDetailResponseDto.content` | `String` | `p.get("content")` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:77` |
| `ProjectDetailResponseDto.status` | `String` | `p.get("status")` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:78` |
| `ProjectDetailResponseDto.priority` | `String` | `p.get("priority")` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:78` |
| `ProjectDetailResponseDto.startDate` | `LocalDate` | `p.get("start_date")` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:78` |
| `ProjectDetailResponseDto.endDate` | `LocalDate` | `p.get("end_date")` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:79` |
| `ProjectDetailResponseDto.leader` | `project.dto.ProjectPersonResponseDto` | `p.get("leader_id") == null ? null : map("id", p.get("leader_id"), "name", p.get("leader_name"), "title", p.get("leader_title"))` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:80` |
| `ProjectDetailResponseDto.memberCount` | `long` | `p.get("member_count")` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:83` |
| `ProjectDetailResponseDto.taskCount` | `long` | `total` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:83` |
| `ProjectDetailResponseDto.doneCount` | `long` | `done` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:83` |
| `ProjectDetailResponseDto.overdueCount` | `java.math.BigDecimal` | `p.get("overdue_count")` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:84` |
| `ProjectDetailResponseDto.progressPct` | `long` | `total == 0 ? 0 : done * 100 / total` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:84` |
| `ProjectDetailResponseDto.myRole` | `String` | `p.get("leader_id") != null && number(p, "leader_id") == actor() ? "LEADER" : number(p, "is_member") > 0 ? "MEMBER" : null` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:85` |
| `ProjectDetailResponseDto.members` | `List<project.dto.ProjectPersonResponseDto>` | `members(id)` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:120` |
| `ProjectDetailResponseDto.permissions` | `project.dto.ProjectPermissionsResponseDto` | `permissions(p)` | `backend/src/main/java/com/minierp/backend/domain/project/service/BoardProjectService.java:121` |
| `TaskCardResponseDto.id` | `long` | `task.get("id")` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:52` |
| `TaskCardResponseDto.key` | `String` | `project.get("project_key") + "-" + task.get("task_no")` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:52` |
| `TaskCardResponseDto.title` | `String` | `task.get("task_title")` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:53` |
| `TaskCardResponseDto.status` | `String` | `task.get("task_status")` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:53` |
| `TaskCardResponseDto.priority` | `String` | `task.get("priority")` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:53` |
| `TaskCardResponseDto.endDate` | `LocalDate` | `end` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:54` |
| `TaskCardResponseDto.overdueDays` | `long` | `overdue` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:54` |
| `TaskCardResponseDto.assignees` | `List<project.dto.ProjectPersonResponseDto>` | `people` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:54` |
| `TaskCardResponseDto.position` | `long` | `task.get("position")` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:54` |
| `TaskCardResponseDto.version` | `long` | `task.get("version")` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:55` |
| `TaskCardResponseDto.canMove` | `boolean` | `projects.canManage(project) \|\| assigned(people)` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:55` |
| `TaskDetailResponseDto.id` | `long` | `task.get("id")` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:52` |
| `TaskDetailResponseDto.key` | `String` | `project.get("project_key") + "-" + task.get("task_no")` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:52` |
| `TaskDetailResponseDto.title` | `String` | `task.get("task_title")` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:53` |
| `TaskDetailResponseDto.status` | `String` | `task.get("task_status")` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:53` |
| `TaskDetailResponseDto.priority` | `String` | `task.get("priority")` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:53` |
| `TaskDetailResponseDto.endDate` | `LocalDate` | `end` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:54` |
| `TaskDetailResponseDto.overdueDays` | `long` | `overdue` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:54` |
| `TaskDetailResponseDto.assignees` | `List<project.dto.ProjectPersonResponseDto>` | `people` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:54` |
| `TaskDetailResponseDto.position` | `long` | `task.get("position")` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:54` |
| `TaskDetailResponseDto.version` | `long` | `task.get("version")` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:55` |
| `TaskDetailResponseDto.canMove` | `boolean` | `projects.canManage(project) \|\| assigned(people)` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:55` |
| `TaskDetailResponseDto.projectId` | `long` | `task.get("project_id")` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:93` |
| `TaskDetailResponseDto.content` | `String` | `task.get("task_content")` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:94` |
| `TaskDetailResponseDto.canManage` | `boolean` | `projects.canManage(p)` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:95` |
| `TaskDetailResponseDto.history` | `List<task.dto.TaskDetailResponseDto.History>` | `history.stream().map(h -> { var event = map("action", h.get("action"), "actor", h.get("actor_id") == null ? null : map("id", h.get("actor_id"), "name", h.get("actor_name")), "at", h.get("created_at")); if (!"MOVED".equals(h.get("action"))) { event.put("from", h.get("from_value")); event.put("to", h.get("to_value")); } if (h.get("target_id") != null) { event.put("target", map("id", h.get("target_id"), "name", h.get("target_name"))); } return event; }).toList()` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:97` |
| `ProjectPersonResponseDto.id` | `long` | `row.get("id")` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:66` |
| `ProjectPersonResponseDto.name` | `String` | `row.get("name")` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:66` |
| `ProjectPersonResponseDto.title` | `String` | `row.get("title")` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:66` |
| `TaskBoardResponseDto.Column.status` | `String` | `status` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:75` |
| `TaskBoardResponseDto.Column.tasks` | `List<task.dto.TaskCardResponseDto>` | `tasks.stream() .filter(t -> status.equals(t.get("task_status"))) .map(t -> card(t, p, assignments.getOrDefault(number(t, "id"), List.of()))) .toList()` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:76` |
| `TaskBoardResponseDto.project` | `project.dto.ProjectSummaryResponseDto` | `projects.view(p)` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:81` |
| `TaskBoardResponseDto.members` | `List<project.dto.ProjectPersonResponseDto>` | `members` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:81` |
| `TaskBoardResponseDto.permissions` | `project.dto.ProjectPermissionsResponseDto` | `projects.permissions(p)` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:81` |
| `TaskBoardResponseDto.columns` | `List<task.dto.TaskBoardResponseDto.Column>` | `columns` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:82` |
| `TaskDetailResponseDto.Moved.action` | `String` | `h.get("action")` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:98` |
| `TaskDetailResponseDto.Moved.actor` | `task.dto.TaskDetailResponseDto.Person` | `h.get("actor_id") == null ? null : map("id", h.get("actor_id"), "name", h.get("actor_name"))` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:99` |
| `TaskDetailResponseDto.Moved.at` | `LocalDateTime` | `h.get("created_at")` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:102` |
| `TaskDetailResponseDto.Event.action` | `String` | `h.get("action")` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:98` |
| `TaskDetailResponseDto.Event.actor` | `task.dto.TaskDetailResponseDto.Person` | `h.get("actor_id") == null ? null : map("id", h.get("actor_id"), "name", h.get("actor_name"))` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:99` |
| `TaskDetailResponseDto.Event.at` | `LocalDateTime` | `h.get("created_at")` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:102` |
| `TaskDetailResponseDto.Event.from` | `String` | `h.get("from_value")` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:104` |
| `TaskDetailResponseDto.Event.to` | `String` | `h.get("to_value")` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:105` |
| `TaskDetailResponseDto.Event.target` | `task.dto.TaskDetailResponseDto.Person` | `map("id", h.get("target_id"), "name", h.get("target_name"))` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:108` |
| `TaskDetailResponseDto.Person.id` | `long` | `h.get("actor_id")` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:101` |
| `TaskDetailResponseDto.Person.name` | `String` | `h.get("actor_name")` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:101` |
| `TaskDetailResponseDto.Person.id` | `long` | `h.get("target_id")` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:108` |
| `TaskDetailResponseDto.Person.name` | `String` | `h.get("target_name")` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:108` |
| `TaskMoveResponseDto.task` | `task.dto.TaskCardResponseDto` | `card(raw(id), p, people)` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:223` |
| `TaskMoveResponseDto.project` | `task.dto.TaskMoveResponseDto.Project` | `summary` | `backend/src/main/java/com/minierp/backend/domain/task/service/BoardTaskService.java:223` |
| `TaskMoveResponseDto.Project.id` | `long` | `project` | `backend/src/main/java/com/minierp/backend/domain/project/repository/ProjectStateStore.java:37` |
| `TaskMoveResponseDto.Project.status` | `String` | `status` | `backend/src/main/java/com/minierp/backend/domain/project/repository/ProjectStateStore.java:37` |
| `TaskMoveResponseDto.Project.taskCount` | `long` | `total` | `backend/src/main/java/com/minierp/backend/domain/project/repository/ProjectStateStore.java:37` |
| `TaskMoveResponseDto.Project.doneCount` | `long` | `done` | `backend/src/main/java/com/minierp/backend/domain/project/repository/ProjectStateStore.java:37` |
| `TaskMoveResponseDto.Project.progressPct` | `long` | `total == 0 ? 0 : done * 100 / total` | `backend/src/main/java/com/minierp/backend/domain/project/repository/ProjectStateStore.java:38` |
| `HomeResponseDto.today` | `attendance.dto.AttendanceTodayResponseDto` | `attendanceView ? attendance.homeToday() : null` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:38` |
| `HomeResponseDto.approvals` | `home.dto.HomeResponseDto.Approvals` | `approvalView ? approvals(args) : null` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:39` |
| `HomeResponseDto.leave` | `leave.dto.LeaveBalanceResponseDto` | `leave.getBalance(null)` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:39` |
| `HomeResponseDto.teamWeek` | `home.dto.HomeResponseDto.TeamWeek` | `attendanceView \|\| projectView ? teamWeek(args, attendanceView, projectView, scope) : null` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:40` |
| `HomeResponseDto.myTasks` | `home.dto.HomeResponseDto.MyTasks` | `projectView ? myTasks(args, scope, today) : null` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:41` |
| `HomeResponseDto.notifications` | `home.dto.HomeResponseDto.Notifications` | `notifications(args)` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:41` |
| `HomeResponseDto.Form.code` | `String` | `code` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:51` |
| `HomeResponseDto.Form.shortName` | `String` | `FormService.shortName(code)` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:51` |
| `HomeResponseDto.Todo.id` | `long` | `r.get("id")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:58` |
| `HomeResponseDto.Todo.docNo` | `String` | `r.get("doc_no")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:58` |
| `HomeResponseDto.Todo.form` | `home.dto.HomeResponseDto.Form` | `form(r)` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:58` |
| `HomeResponseDto.Todo.title` | `String` | `r.get("title")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:58` |
| `HomeResponseDto.Todo.drafter` | `home.dto.HomeResponseDto.Person` | `map("id", r.get("drafter_id"), "name", r.get("user_name"), "title", r.get("user_title"))` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:59` |
| `HomeResponseDto.Todo.submittedAt` | `LocalDateTime` | `r.get("submitted_at")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:60` |
| `HomeResponseDto.Mine.id` | `long` | `r.get("id")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:64` |
| `HomeResponseDto.Mine.docNo` | `String` | `r.get("doc_no")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:64` |
| `HomeResponseDto.Mine.form` | `home.dto.HomeResponseDto.Form` | `form(r)` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:64` |
| `HomeResponseDto.Mine.title` | `String` | `r.get("title")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:64` |
| `HomeResponseDto.Mine.status` | `String` | `r.get("status")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:65` |
| `HomeResponseDto.Mine.currentApprover` | `home.dto.HomeResponseDto.Person` | `r.get("approver_id") == null ? null : map("id", r.get("approver_id"), "name", r.get("approver_name"), "title", r.get("approver_title"))` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:66` |
| `HomeResponseDto.Mine.submittedAt` | `LocalDateTime` | `r.get("submitted_at")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:70` |
| `HomeResponseDto.Approvals.todoCount` | `long` | `count` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:72` |
| `HomeResponseDto.Approvals.todo` | `List<home.dto.HomeResponseDto.Todo>` | `todo` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:72` |
| `HomeResponseDto.Approvals.mine` | `List<home.dto.HomeResponseDto.Mine>` | `mine` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:72` |
| `HomeResponseDto.TaskDue.type` | `String` | `"TASK_DUE"` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:87` |
| `HomeResponseDto.TaskDue.task` | `home.dto.HomeResponseDto.Task` | `map("id", r.get("task_id"), "key", r.get("project_key") + "-" + r.get("task_no"), "title", r.get("task_title"))` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:88` |
| `HomeResponseDto.TaskDue.project` | `home.dto.HomeResponseDto.ProjectKey` | `map("id", r.get("project_id"), "key", r.get("project_key"))` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:90` |
| `HomeResponseDto.Task.id` | `long` | `r.get("task_id")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:88` |
| `HomeResponseDto.Task.key` | `String` | `r.get("project_key") + "-" + r.get("task_no")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:88` |
| `HomeResponseDto.Task.title` | `String` | `r.get("task_title")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:89` |
| `HomeResponseDto.ProjectKey.id` | `long` | `r.get("project_id")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:90` |
| `HomeResponseDto.ProjectKey.key` | `String` | `r.get("project_key")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:90` |
| `HomeResponseDto.LeaveItem.type` | `String` | `r.get("type").toString().startsWith("HALF_") ? r.get("type") : "LEAVE"` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:92` |
| `HomeResponseDto.LeaveItem.user` | `home.dto.HomeResponseDto.User` | `map("id", r.get("user_id"), "name", r.get("user_name"))` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:93` |
| `HomeResponseDto.LeaveItem.status` | `String` | `r.get("status")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:93` |
| `HomeResponseDto.LeaveItem.documentId` | `long` | `r.get("document_id")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:94` |
| `HomeResponseDto.User.id` | `long` | `r.get("user_id")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:93` |
| `HomeResponseDto.User.name` | `String` | `r.get("user_name")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:93` |
| `HomeResponseDto.TeamWeek.department` | `home.dto.HomeResponseDto.Department` | `department` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:108` |
| `HomeResponseDto.TeamWeek.from` | `LocalDate` | `from` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:108` |
| `HomeResponseDto.TeamWeek.to` | `LocalDate` | `to` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:108` |
| `HomeResponseDto.TeamWeek.days` | `List<home.dto.HomeResponseDto.Day>` | `days.entrySet().stream().map(e -> map("date", e.getKey(), "items", e.getValue())).toList()` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:109` |
| `HomeResponseDto.Day.date` | `LocalDate` | `e.getKey()` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:109` |
| `HomeResponseDto.Day.items` | `List<home.dto.HomeResponseDto.WeekItem>` | `e.getValue()` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:109` |
| `HomeResponseDto.Project.id` | `long` | `p.get("id")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:119` |
| `HomeResponseDto.Project.key` | `String` | `p.get("project_key")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:119` |
| `HomeResponseDto.Project.title` | `String` | `p.get("title")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:119` |
| `HomeResponseDto.Project.total` | `long` | `total` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:119` |
| `HomeResponseDto.Project.done` | `long` | `done` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:120` |
| `HomeResponseDto.Project.overdue` | `java.math.BigDecimal` | `p.get("overdue_count")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:120` |
| `HomeResponseDto.Project.progressPct` | `long` | `total == 0 ? 0 : done * 100 / total` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:121` |
| `HomeResponseDto.TaskItem.id` | `long` | `r.get("id")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:125` |
| `HomeResponseDto.TaskItem.projectId` | `long` | `r.get("project_id")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:125` |
| `HomeResponseDto.TaskItem.key` | `String` | `r.get("project_key") + "-" + r.get("task_no")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:126` |
| `HomeResponseDto.TaskItem.title` | `String` | `r.get("task_title")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:126` |
| `HomeResponseDto.TaskItem.status` | `String` | `r.get("task_status")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:127` |
| `HomeResponseDto.TaskItem.endDate` | `LocalDate` | `r.get("end_date")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:127` |
| `HomeResponseDto.TaskItem.overdueDays` | `long` | `Math.max(0, ChronoUnit.DAYS.between((LocalDate) r.get("end_date"), today))` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:128` |
| `HomeResponseDto.MyTasks.project` | `home.dto.HomeResponseDto.Project` | `project` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:130` |
| `HomeResponseDto.MyTasks.count` | `long` | `rows.isEmpty() ? 0 : number(rows.get(0), "matching_count")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:130` |
| `HomeResponseDto.MyTasks.items` | `List<home.dto.HomeResponseDto.TaskItem>` | `items` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:131` |
| `HomeResponseDto.Notifications.unread` | `long` | `rows.get(0).get("unread")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:136` |
| `HomeResponseDto.Notifications.items` | `List<notification.dto.NotificationItemResponseDto>` | `rows.stream() .filter(r -> r.get("id") != null) .map(r -> map("id", r.get("id"), "type", r.get("type"), "title", r.get("title"), "link", r.get("link"), "read", r.get("read_at") != null, "createdAt", r.get("created_at"))) .toList()` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:137` |
| `NotificationItemResponseDto.id` | `long` | `r.get("id")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:139` |
| `NotificationItemResponseDto.type` | `String` | `r.get("type")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:139` |
| `NotificationItemResponseDto.title` | `String` | `r.get("title")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:139` |
| `NotificationItemResponseDto.link` | `String` | `r.get("link")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:140` |
| `NotificationItemResponseDto.read` | `boolean` | `r.get("read_at") != null` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:140` |
| `NotificationItemResponseDto.createdAt` | `LocalDateTime` | `r.get("created_at")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:141` |
| `HomeResponseDto.Department.id` | `long` | `SELECT id — SELECT d.id,d.name FROM users u JOIN departments d ON d.id=u.department_id WHERE u.id=:actor` | `backend/src/main/java/com/minierp/backend/domain/home/repository/HomeQueries.java:89` |
| `HomeResponseDto.Department.name` | `String` | `SELECT name — SELECT d.id,d.name FROM users u JOIN departments d ON d.id=u.department_id WHERE u.id=:actor` | `backend/src/main/java/com/minierp/backend/domain/home/repository/HomeQueries.java:89` |
| `HomeResponseDto.Person.id` | `long` | `r.get("drafter_id")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:59` |
| `HomeResponseDto.Person.name` | `String` | `r.get("user_name")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:59` |
| `HomeResponseDto.Person.title` | `String` | `r.get("user_title")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:60` |
| `HomeResponseDto.Person.id` | `long` | `r.get("approver_id")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:68` |
| `HomeResponseDto.Person.name` | `String` | `r.get("approver_name")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:68` |
| `HomeResponseDto.Person.title` | `String` | `r.get("approver_title")` | `backend/src/main/java/com/minierp/backend/domain/home/service/HomeService.java:69` |
