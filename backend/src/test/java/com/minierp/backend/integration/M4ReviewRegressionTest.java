package com.minierp.backend.integration;

import com.minierp.backend.support.BoardTestSupport;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class M4ReviewRegressionTest extends BoardTestSupport {
    @org.springframework.beans.factory.annotation.Autowired
    com.minierp.backend.domain.task.service.TaskOverdueService overdue;
    @Test
    void overdueHistoryResolvesRecipientEvenAfterMemberRemoval() throws Exception {
        long p = project(leader, 1);
        long task = taskIds(p).get(0);
        jdbc.update("UPDATE tasks SET end_date=? WHERE id=?", date.minusDays(1), task);
        jdbc.update("INSERT INTO task_assignments(task_id,user_id,created_at) VALUES(?,?,?)", task, employee.getId(),
                java.time.LocalDateTime.now(clock));
        overdue.runToday();
        jdbc.update("DELETE FROM task_assignments WHERE task_id=?", task);
        jdbc
                .update(
                "DELETE FROM project_members WHERE project_id=(SELECT project_id FROM tasks WHERE id=?) AND user_id=?",
                task, employee.getId());
        var event = data(getAs("/api/v1/tasks/" + task, leaderToken)).path("history").get(0);
        assertThat(event.path("action").asText()).isEqualTo("OVERDUE_NOTIFIED");
        assertThat(event.path("actor").isNull()).isTrue();
        assertThat(event.path("target").path("id").asLong()).isEqualTo(employee.getId());
        assertThat(event.path("target").path("name").asText()).isEqualTo(employee.getUserName());
    }

    @Test
    void movedHistoryHidesStoragePositions() throws Exception {
        long p = project(leader, 2);
        long task = taskIds(p).get(1);
        data(postAs("/api/v1/tasks/" + task + "/move", leaderToken,
                map("status", "DOING", "afterTaskId", null, "version", 0)));
        var entry = data(getAs("/api/v1/tasks/" + task, leaderToken)).path("history").get(0);
        assertThat(entry.path("action").asText()).isEqualTo("MOVED");
        assertThat(entry.has("from")).isFalse();
        assertThat(entry.has("to")).isFalse();
        assertThat(jdbc.queryForObject("SELECT from_value FROM task_histories WHERE task_id=?", String.class, task))
                .isEqualTo("2048");
    }

    @Test
    void assignmentHistoryResolvesTargetEvenAfterMemberRemoval() throws Exception {
        long p = project(leader, 0);
        long task = data(postAs("/api/v1/projects/" + p + "/tasks", leaderToken, Map
                .of("title", "이력", "assigneeIds", List.of(employee.getId())))).path("id").asLong();
        mvc.perform(patch("/api/v1/tasks/" + task).header("Authorization", "Bearer " + leaderToken)
                .contentType("application/json").content("{\"assigneeIds\":[]}")).andExpect(status().isOk());
        mvc.perform(put("/api/v1/projects/" + p + "/members").header("Authorization", "Bearer " + leaderToken)
                .contentType("application/json").content("{\"userIds\":[]}")).andExpect(status().isOk());
        var history = data(getAs("/api/v1/tasks/" + task, leaderToken)).path("history");
        int seen = 0;
        for (var entry : history) {
            if (Set.of("ASSIGNED", "UNASSIGNED").contains(entry.path("action").asText())) {
                assertThat(entry.path("target").path("id").asLong()).isEqualTo(employee.getId());
                assertThat(entry.path("target").path("name").asText()).isEqualTo(employee.getUserName());
                seen++;
            }
        }
        assertThat(seen).isEqualTo(2);
    }

    @Test
    void assignmentTitleIsTaskKeyAndNameOnly() throws Exception {
        long p = project(leader, 0);
        jdbc.update("UPDATE projects SET project_key=? WHERE id=?", "R" + p, p);
        data(postAs("/api/v1/projects/" + p + "/tasks", leaderToken, Map
                .of("title", "결재선 규칙", "assigneeIds", List.of(employee.getId()))));
        assertThat(jdbc.queryForObject("SELECT title FROM notifications WHERE user_id=? AND type='TASK_ASSIGNED'",
                String.class, employee.getId())).isEqualTo("R" + p + "-1 결재선 규칙");
    }

    @Test
    void projectSearchTreatsPercentUnderscoreAndEscapeLiterally() throws Exception {
        String marker = UUID.randomUUID().toString();
        long literal = project(leader, 0);
        long other = project(leader, 0);
        jdbc.update("UPDATE projects SET title=? WHERE id=?", marker + "%_!", literal);
        jdbc.update("UPDATE projects SET title=? WHERE id=?", marker + "abc!", other);
        var result = data(mvc.perform(get("/api/v1/projects").param("q", marker + "%_!")
                .header("Authorization", "Bearer " + leaderToken)));
        assertThat(result.path("content").size()).isEqualTo(1);
        assertThat(result.path("content").get(0).path("id").asLong()).isEqualTo(literal);
    }

    @Test
    void existingRetiredMemberCanBeRetainedOrRemovedButNotNewlyAdded() throws Exception {
        long p = project(leader, 0);
        jdbc.update("UPDATE users SET status='RETIRED' WHERE id=?", employee.getId());
        var request = Map.of("userIds", List.of(employee.getId()));
        mvc.perform(put("/api/v1/projects/" + p + "/members").header("Authorization", "Bearer " + leaderToken)
                .contentType("application/json").content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/projects/" + p + "/members").header("Authorization", "Bearer " + leaderToken)
                .contentType("application/json").content("{\"userIds\":[]}")).andExpect(status().isOk());
        mvc.perform(put("/api/v1/projects/" + p + "/members").header("Authorization", "Bearer " + leaderToken)
                .contentType("application/json").content(mapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void projectCreationAlsoRequiresViewPermission() throws Exception {
        long role = db.insert("INSERT INTO roles(code,name,is_system) VALUES(:code,'C only',0)",
                map("code", UUID.randomUUID().toString()));
        jdbc.update("DELETE FROM user_roles WHERE user_id=?", employee.getId());
        jdbc.update("INSERT INTO user_roles VALUES(?,?)", employee.getId(), role);
        jdbc.update("INSERT INTO role_permissions SELECT ?,id,0,1,0,0,'SELF' FROM menus WHERE code='PROJECT'", role);
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM projects", Long.class);
        postAs("/api/v1/projects", employeeToken,
                map("key", "CV", "title", "권한", "startDate", date, "endDate", date.plusMonths(1), "leaderId", employee
                .getId())).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM projects", Long.class)).isEqualTo(before);
    }
}
