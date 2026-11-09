package com.minierp.backend.integration;

import com.minierp.backend.support.BoardTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class R04Group3Probe extends BoardTestSupport {
    private ResultActions change(String method, String url, String token, Object body) throws Exception {
        var request = method.equals("PUT") ? put(url) : patch(url);
        return mvc.perform(request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)));
    }

    private JsonNode approval(long document) throws Exception {
        submit(document);
        return data(postAs("/api/v1/approval-documents/" + document + "/approve", leaderToken, null));
    }

    @Test
    @Order(1)
    void attendanceHolidayDocumentsMissingDatesAndCsv() throws Exception {
        jdbc.update("DELETE FROM holidays WHERE holiday_date=?", date);
        jdbc.update("INSERT INTO holidays(name,holiday_date,substitute) VALUES('근태 비교 휴일',?,0)", date);
        assertThat(data(getAs("/api/v1/attendance/today", employeeToken)).path("holidayName").asText())
                .isEqualTo("근태 비교 휴일");
        assertThat(data(postAs("/api/v1/attendance/clock-in", employeeToken, null)).path("holidayName").asText())
                .isEqualTo("근태 비교 휴일");
        at(date, LocalTime.of(18, 5));
        data(postAs("/api/v1/attendance/clock-out", employeeToken, null));
        var month = YearMonth.from(date);
        var mine = data(getAs("/api/v1/attendance/me?month=" + month, employeeToken));
        assertThat(mine.path("days").findValuesAsText("clockOutAt")).contains(date + "T18:05:00");
        assertThat(mine.path("days").findValuesAsText("date")).hasSize(month.lengthOfMonth());
        var table = data(getAs("/api/v1/attendance/monthly?month=" + month, chiefToken));
        assertThat(table.path("days").findValuesAsText("holidayName")).contains("근태 비교 휴일");
        var correctedDay = date.minusWeeks(1);
        jdbc.update("INSERT INTO attendances(user_id,work_date,clock_in_at,sched_grace_min,in_result,created_at) VALUES(?,?,?,10,'LATE',?)",
                employee.getId(), correctedDay, correctedDay.atTime(9, 14), LocalDateTime.now(clock));
        long correction = draft("ATTENDANCE_FIX", Map.of("workDate", correctedDay.toString(), "clockIn", "09:02",
                "clockOut", "18:10", "reason", "근태 정정 비교"));
        approval(correction);
        var corrected = data(getAs("/api/v1/attendance/records/" + employee.getId() + "/" + correctedDay, chiefToken));
        assertThat(corrected.path("fixDocument").path("id").asLong()).isEqualTo(correction);
        var correctedMonth = data(getAs("/api/v1/attendance/me?month=" + YearMonth.from(correctedDay), employeeToken));
        assertThat(correctedMonth.path("days").findValues("fixDocument").stream()
                .anyMatch(value -> value.path("id").asLong() == correction)).isTrue();
        long leave = draft("LEAVE", leaveBody("ANNUAL", start, start));
        approval(leave);
        assertThat(data(getAs("/api/v1/attendance/records/" + employee.getId() + "/" + start, chiefToken))
                .path("leaveDocument").path("id").asLong()).isEqualTo(leave);
        var empty = data(getAs("/api/v1/attendance/records/" + outsider.getId() + "/" + date.plusDays(2), chiefToken));
        assertThat(empty.path("clockInAt").isNull()).isTrue();
        getAs("/api/v1/attendance/monthly/export?month=" + month, chiefToken).andExpect(status().isOk());
        jdbc.update("DELETE FROM holidays WHERE holiday_date=?", date);
    }

    @Test
    @Order(2)
    void projectAllResponseShapesAndNullableLeader() throws Exception {
        var created = data(postAs("/api/v1/projects", chiefToken,
                Map.of("key", "R4PROBE", "title", "응답 비교", "content", "프로젝트 내용", "leaderId", leader.getId(),
                        "startDate", date.toString(), "endDate", date.plusMonths(1).toString())));
        long project = created.path("id").asLong();
        assertThat(created.path("content").asText()).isEqualTo("프로젝트 내용");
        assertThat(created.path("myRole").isNull()).isTrue();
        assertThat(created.has("permissions")).isFalse();
        var changed = data(change("PATCH", "/api/v1/projects/" + project, chiefToken, Map.of("content", "바꾼 내용")));
        assertThat(changed.path("content").asText()).isEqualTo("바꾼 내용");
        assertThat(changed.has("permissions")).isFalse();
        var members = data(change("PUT", "/api/v1/projects/" + project + "/members", chiefToken,
                Map.of("userIds", List.of(leader.getId(), employee.getId()))));
        assertThat(members.path("permissions").path("canManage").asBoolean()).isTrue();
        var detail = data(getAs("/api/v1/projects/" + project, employeeToken));
        assertThat(detail.path("myRole").asText()).isEqualTo("MEMBER");
        assertThat(detail.path("content").asText()).isEqualTo("바꾼 내용");
        var list = data(getAs("/api/v1/projects?q=R4PROBE", employeeToken));
        assertThat(list.path("content").get(0).path("content").asText()).isEqualTo("바꾼 내용");
        assertThat(data(getAs("/api/v1/projects/" + project + "/board", employeeToken))
                .path("project").path("content").asText()).isEqualTo("바꾼 내용");
        jdbc.update("UPDATE projects SET leader_id=NULL WHERE id=?", project);
        assertThat(data(getAs("/api/v1/projects/" + project, chiefToken)).path("leader").isNull()).isTrue();
    }

    @Test
    @Order(3)
    void everyTaskHistoryShapeAndConflictCard() throws Exception {
        long project = project(leader, 0);
        jdbc.update("UPDATE projects SET project_key='R4TASK',content='보드 내용' WHERE id=?", project);
        var created = data(postAs("/api/v1/projects/" + project + "/tasks", leaderToken,
                Map.of("title", "업무 비교", "content", "업무 내용", "status", "TODO", "assigneeIds", List.of(employee.getId()),
                        "endDate", date.minusDays(2).toString())));
        long task = created.path("id").asLong();
        var changed = data(change("PATCH", "/api/v1/tasks/" + task, leaderToken,
                Map.of("title", "수정 업무", "content", "수정 내용", "assigneeIds", List.of(outsider.getId()))));
        var moved = data(postAs("/api/v1/tasks/" + task + "/move", leaderToken,
                Map.of("status", "DOING", "version", changed.path("version").asInt())));
        data(postAs("/api/v1/tasks/" + task + "/move", leaderToken,
                Map.of("status", "DOING", "version", moved.path("task").path("version").asInt())));
        jdbc.update("INSERT INTO task_histories(task_id,actor_id,action,from_value,to_value,created_at) VALUES(?,NULL,'OVERDUE_NOTIFIED',NULL,?,?)",
                task, outsider.getId().toString(), LocalDateTime.now(clock));
        var detail = data(getAs("/api/v1/tasks/" + task, employeeToken));
        assertThat(detail.path("history").findValuesAsText("action"))
                .contains("CREATED", "UPDATED", "STATUS", "MOVED", "ASSIGNED", "UNASSIGNED", "OVERDUE_NOTIFIED");
        for (var event : detail.path("history")) {
            boolean movedEvent = event.path("action").asText().equals("MOVED");
            assertThat(event.has("from")).isEqualTo(!movedEvent);
            assertThat(event.has("to")).isEqualTo(!movedEvent);
            if (List.of("ASSIGNED", "UNASSIGNED", "OVERDUE_NOTIFIED").contains(event.path("action").asText())) {
                assertThat(event.path("target").path("name").asText()).isNotBlank();
            }
        }
        postAs("/api/v1/tasks/" + task + "/move", leaderToken, Map.of("status", "DONE", "version", 0))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.details.currentTask.id").value(task));
    }

    @Test
    @Order(4)
    void homeBothTeamItemsPermissionNullsAndUnreadSecondPage() throws Exception {
        long project = project(leader, 1);
        jdbc.update("UPDATE projects SET project_key='R4HOME' WHERE id=?", project);
        long task = taskIds(project).get(0);
        jdbc.update("UPDATE tasks SET end_date=? WHERE id=?", date.plusDays(2), task);
        jdbc.update("INSERT INTO task_assignments(task_id,user_id,created_at) VALUES(?,?,?)", task, employee.getId(),
                LocalDateTime.now(clock));
        approval(draft("LEAVE", leaveBody("ANNUAL", date.plusDays(1), date.plusDays(1))));
        var home = data(getAs("/api/v1/home", employeeToken));
        assertThat(home.path("teamWeek").findValuesAsText("type")).contains("TASK_DUE", "LEAVE");
        jdbc.update("UPDATE role_permissions SET can_view=0 WHERE role_id=3 AND menu_id IN (SELECT id FROM menus WHERE code IN ('PROJECT','MY_ATTENDANCE','APPROVAL_INBOX'))");
        try {
            var hidden = data(getAs("/api/v1/home", employeeToken));
            for (String key : List.of("today", "approvals", "teamWeek", "myTasks")) {
                assertThat(hidden.has(key)).isTrue();
                assertThat(hidden.path(key).isNull()).isTrue();
            }
        } finally {
            jdbc.update("UPDATE role_permissions SET can_view=1 WHERE role_id=3 AND menu_id IN (SELECT id FROM menus WHERE code IN ('PROJECT','MY_ATTENDANCE','APPROVAL_INBOX'))");
        }
        jdbc.update("DELETE FROM notifications WHERE user_id=?", employee.getId());
        for (int i = 0; i < 5; i++) {
            jdbc.update("INSERT INTO notifications(user_id,type,title,link,read_at,created_at) VALUES(?,'TASK_ASSIGNED',?,'/projects/1',?,?)",
                    employee.getId(), "읽음 비교 " + i, i == 0 ? LocalDateTime.now(clock) : null,
                    LocalDateTime.now(clock).plusMinutes(i));
        }
        var page = data(getAs("/api/v1/notifications?unreadOnly=true&page=1&size=2", employeeToken));
        assertThat(page.path("content").size()).isEqualTo(2);
        assertThat(page.path("page").path("number").asInt()).isEqualTo(1);
        for (var item : page.path("content")) assertThat(item.path("read").asBoolean()).isFalse();
    }
}
