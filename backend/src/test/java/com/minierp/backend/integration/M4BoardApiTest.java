package com.minierp.backend.integration;

import com.minierp.backend.support.*;
import com.minierp.backend.domain.notification.service.NotificationService;
import com.minierp.backend.domain.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;
import java.time.*;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(JdbcProbe.Configuration.class)
class M4BoardApiTest extends BoardTestSupport {
    @MockitoSpyBean
    NotificationService notifications;
    ResultActions patchAs(String url, String token, Object body) throws Exception {
        return mvc.perform(
                patch(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)));
    }

    ResultActions members(long p, String token, List<Long> ids) throws Exception {
        return mvc.perform(
                put("/api/v1/projects/" + p + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("userIds", ids))));
    }

    ResultActions move(long id, String token, String state, Long after, int version) throws Exception {
        return postAs("/api/v1/tasks/" + id + "/move", token,
                map("status", state, "afterTaskId", after, "version", version));
    }

    long create(long p, String token, Object body) throws Exception {
        return data(postAs("/api/v1/projects/" + p + "/tasks", token, body)).path("id").asLong();
    }

    long history(long p) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM task_histories h JOIN tasks t ON t.id=h.task_id WHERE t.project_id=?", Long.class,
                p);
    }

    void grant(long user, String scope, boolean update) {
        long role = db.insert("INSERT INTO roles(code,name,is_system) VALUES(:code,'검증',0)",
                map("code", UUID.randomUUID().toString()));
        jdbc.update("DELETE FROM user_roles WHERE user_id=?", user);
        jdbc.update("INSERT INTO user_roles VALUES(?,?)", user, role);
        jdbc.update("INSERT INTO role_permissions SELECT ?,id,1,1,?,0,? FROM menus WHERE code='PROJECT'", role, update,
                scope);
    }

    @Test
    void projectLifecycleKeepsKeyLeaderAndMemberContract() throws Exception {
        String key = "X" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        var body = map("key", key, "title", "새 프로젝트", "startDate", date, "endDate", date.plusMonths(1),
                "leaderId", employee.getId());
        var created = data(postAs("/api/v1/projects", chiefToken, body).andExpect(status().isCreated()));
        long p = created.path("id").asLong();
        assertThat(created.path("status").asText()).isEqualTo("READY");
        assertThat(created.path("members").size()).isEqualTo(1);
        postAs("/api/v1/projects", chiefToken, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PROJECT_KEY_DUPLICATE"));
        postAs("/api/v1/projects", employeeToken, body).andExpect(status().isForbidden());
        patchAs("/api/v1/projects/" + p, employeeToken, Map.of("key", "ZZ")).andExpect(status().isBadRequest());
        patchAs("/api/v1/projects/" + p, employeeToken, Map.of("title", "팀장이 수정")).andExpect(status().isOk());
        var detail = data(getAs("/api/v1/projects/" + p, employeeToken));
        assertThat(detail.path("permissions").path("canManage").asBoolean()).isTrue();
        members(p, employeeToken, List.of()).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project_members WHERE project_id=?", Integer.class, p))
                .isEqualTo(1);
        long task = create(p, employeeToken, Map.of("title", "빠른 추가"));
        var card = data(getAs("/api/v1/tasks/" + task, employeeToken));
        assertThat(card.path("status").asText()).isEqualTo("TODO");
        assertThat(card.path("priority").asText()).isEqualTo("MEDIUM");
        assertThat(card.path("endDate").asText()).isEqualTo(date.plusMonths(1).toString());
        assertThat(card.path("key").asText()).isEqualTo(key + "-1");
        assertThat(data(getAs("/api/v1/projects/" + p + "/board", employeeToken)).path("columns").size()).isEqualTo(3);
    }

    @Test
    void visibilityAndMovePermissionsRejectBeforeAnyWrite() throws Exception {
        long p = project(leader, 1);
        long task = taskIds(p).get(0);
        jdbc.update("DELETE FROM project_members WHERE project_id=? AND user_id=?", p, employee.getId());
        getAs("/api/v1/projects/" + p, employeeToken).andExpect(status().isForbidden());
        getAs("/api/v1/projects/" + p + "/board", employeeToken).andExpect(status().isForbidden());
        getAs("/api/v1/tasks/" + task, employeeToken).andExpect(status().isForbidden());
        move(task, employeeToken, "DONE", null, 0).andExpect(status().isForbidden());
        var list = data(getAs("/api/v1/projects", employeeToken));
        assertThat(list.path("content").findValuesAsText("id")).doesNotContain(Long.toString(p));
        move(task, outsideToken, "DONE", null, 0).andExpect(status().isForbidden());
        long other = project(chief, 1);
        getAs("/api/v1/projects/" + other, leaderToken).andExpect(status().isForbidden());
        move(taskIds(other).get(0), leaderToken, "DONE", null, 0).andExpect(status().isForbidden());
        postAs("/api/v1/projects/" + p + "/tasks", outsideToken,
                Map.of("title", "금지", "assigneeIds", List.of(employee.getId())))
                .andExpect(status().isForbidden());
        assertThat(history(p)).isZero();
        assertThat(jdbc.queryForObject("SELECT version FROM tasks WHERE id=?", Integer.class, task)).isZero();
        assertThat(
                jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id=?", Integer.class,
                        employee.getId()))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT last_task_seq FROM projects WHERE id=?", Integer.class, p)).isEqualTo(1);
    }

    @Test
    void departmentTreeAndAllScopesAreSqlPredicates() throws Exception {
        long p = project(leader, 1);
        jdbc.update("DELETE FROM project_members WHERE project_id=? AND user_id=?", p, employee.getId());
        jdbc.update("UPDATE users SET department_id=1 WHERE id=?", employee.getId());
        grant(employee.getId(), "DEPT", false);
        getAs("/api/v1/projects/" + p, employeeToken).andExpect(status().isForbidden());
        grant(employee.getId(), "DEPT_TREE", false);
        try (var trace = JdbcProbe.start()) {
            getAs("/api/v1/projects/" + p, employeeToken).andExpect(status().isOk());
            assertThat(trace.queries.stream().anyMatch(q -> q.sql.contains("with recursive"))).isTrue();
            assertThat(trace.queries.stream().anyMatch(q -> q.sql.contains("u.department_id in"))).isTrue();
        }
        grant(employee.getId(), "ALL", true);
        patchAs("/api/v1/projects/" + p, employeeToken, Map.of("title", "범위 수정")).andExpect(status().isOk());
    }

    @Test
    void assigneeCanMoveOwnCardButCannotManageOrMoveOthers() throws Exception {
        long p = project(leader, 0);
        long own = create(p, leaderToken, Map.of("title", "배정", "assigneeIds", List.of(employee.getId())));
        long other = create(p, leaderToken, Map.of("title", "다른 업무"));
        move(own, employeeToken, "DONE", null, 0).andExpect(status().isOk());
        move(other, employeeToken, "DONE", null, 0).andExpect(status().isForbidden());
        patchAs("/api/v1/tasks/" + own, employeeToken, Map.of("title", "변경 금지")).andExpect(status().isForbidden());
        assertThat(data(getAs("/api/v1/tasks/" + own, employeeToken)).path("canManage").asBoolean()).isFalse();
    }

    @Test
    void memberRemovalAndAssignmentsAreValidatedAtomically() throws Exception {
        long p = project(leader, 0);
        long task = create(p, leaderToken, Map.of("title", "배정", "assigneeIds", List.of(employee.getId())));
        members(p, leaderToken, List.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PROJECT_MEMBER_HAS_TASKS"));
        assertThat(
                jdbc.queryForObject("SELECT COUNT(*) FROM project_members WHERE project_id=? AND user_id=?",
                        Integer.class, p, employee.getId()))
                .isEqualTo(1);
        long previous = history(p);
        patchAs("/api/v1/tasks/" + task, leaderToken, Map.of("title", "롤백", "assigneeIds", List.of(chief.getId())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("TASK_ASSIGNEE_NOT_MEMBER"));
        assertThat(history(p)).isEqualTo(previous);
        assertThat(jdbc.queryForObject("SELECT task_title FROM tasks WHERE id=?", String.class, task)).isEqualTo("배정");
        patchAs("/api/v1/tasks/" + task, leaderToken, Map.of("assigneeIds", List.of(outsider.getId())))
                .andExpect(status().isOk());
        members(p, leaderToken, List.of(outsider.getId())).andExpect(status().isOk());
        assertThat(
                jdbc.queryForObject("SELECT COUNT(*) FROM task_histories WHERE task_id=? AND action='UNASSIGNED'",
                        Integer.class, task))
                .isEqualTo(1);
    }

    @Test
    void notificationFailureRollsBackTaskSequenceHistoryAndAssignment() throws Exception {
        long p = project(leader, 0);
        doThrow(new IllegalStateException("test notification failure"))
                .when(
                        org.springframework.test.util.AopTestUtils.< NotificationService > getUltimateTargetObject(
                                notifications))
                .storeTask(eq(employee.getId()), anyLong(), anyString(), anyString());
        postAs("/api/v1/projects/" + p + "/tasks", leaderToken,
                Map.of("title", "실패", "assigneeIds", List.of(employee.getId())))
                .andExpect(status().isInternalServerError());
        assertThat(taskIds(p)).isEmpty();
        assertThat(history(p)).isZero();
        assertThat(jdbc.queryForObject("SELECT last_task_seq FROM projects WHERE id=?", Integer.class, p)).isZero();
        assertThat(
                jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id=?", Integer.class,
                        employee.getId()))
                .isZero();
        doCallRealMethod()
                .when(
                        org.springframework.test.util.AopTestUtils.< NotificationService > getUltimateTargetObject(
                                notifications))
                .storeTask(anyLong(), anyLong(), anyString(), anyString());
        long task = create(p, leaderToken, Map.of("title", "성공", "assigneeIds", List.of(employee.getId())));
        assertThat(jdbc.queryForObject("SELECT task_no FROM tasks WHERE id=?", Integer.class, task)).isEqualTo(1);
        assertThat(
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM notifications WHERE user_id=? AND type='TASK_ASSIGNED' AND document_id IS NULL",
                        Integer.class, employee.getId()))
                .isEqualTo(1);
    }

    @Test
    void staleVersionAndInvalidAnchorsLeaveOrderAndHistoryUnchanged() throws Exception {
        long p = project(leader, 3);
        var ids = taskIds(p);
        move(ids.get(2), leaderToken, "DOING", ids.get(0), 0).andExpect(status().isOk());
        var order = jdbc.queryForList("SELECT id,position,version FROM tasks WHERE project_id=? ORDER BY position", p);
        long events = history(p);
        move(ids.get(2), leaderToken, "TODO", null, 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TASK_VERSION_CONFLICT"))
                .andExpect(jsonPath("$.error.details.currentTask.version").value(1));
        move(ids.get(2), leaderToken, "TODO", ids.get(0), 1).andExpect(status().isUnprocessableEntity());
        move(ids.get(2), leaderToken, "DOING", ids.get(2), 1).andExpect(status().isUnprocessableEntity());
        long foreign = taskIds(project(chief, 1)).get(0);
        move(ids.get(2), leaderToken, "DOING", foreign, 1).andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForList("SELECT id,position,version FROM tasks WHERE project_id=? ORDER BY position", p))
                .isEqualTo(order);
        assertThat(history(p)).isEqualTo(events);
        System.out.println("M4_VERSION stale_http=409 order_changes=0 history_changes=0");
    }

    @Test
    void stateTransitionsHistoryLimitAndKoreanDateAreConsistent() throws Exception {
        long p = project(leader, 0);
        long id = create(p, leaderToken, Map.of("title", "업무", "endDate", date.minusDays(1)));
        at(date, LocalTime.of(0, 30));
        assertThat(data(getAs("/api/v1/tasks/" + id, leaderToken)).path("overdueDays").asInt()).isEqualTo(1);
        assertThat(data(move(id, leaderToken, "DONE", null, 0)).path("project").path("status").asText())
                .isEqualTo("DONE");
        assertThat(data(move(id, leaderToken, "TODO", null, 1)).path("project").path("status").asText())
                .isEqualTo("READY");
        assertThat(data(move(id, leaderToken, "DOING", null, 2)).path("project").path("status").asText())
                .isEqualTo("PROGRESS");
        for (int i = 0; i < 55; i++) {
            jdbc.update("INSERT INTO task_histories(task_id,actor_id,action,created_at) VALUES(?,?,'UPDATED',?)", id,
                    leader.getId(), date.atStartOfDay());
        }
        var detail = data(getAs("/api/v1/tasks/" + id, leaderToken));
        assertThat(detail.path("history").size()).isEqualTo(50);
        assertThat(detail.path("history").get(0).path("at").asText()).isEqualTo(date + "T00:00:00");
    }

    @Test
    void projectListAndBoardQueryCountsStayConstant() throws Exception {
        long first = project(leader, 1);
        long before;
        long after;
        long boardBefore;
        long boardAfter;
        try (var trace = JdbcProbe.start()) {
            getAs("/api/v1/projects?size=100", chiefToken).andExpect(status().isOk());
            before = trace.queries.size();
            assertThat(trace.projectReads()).isEqualTo(2);
        }
        try (var trace = JdbcProbe.start()) {
            getAs("/api/v1/projects/" + first + "/board", chiefToken).andExpect(status().isOk());
            boardBefore = trace.queries.size();
            assertThat(trace.projectReads()).isEqualTo(3);
        }
        long last = first;
        for (int i = 0; i < 20; i++) {
            last = project(leader, 100);
        }
        try (var trace = JdbcProbe.start()) {
            getAs("/api/v1/projects?size=100", chiefToken).andExpect(status().isOk());
            after = trace.queries.size();
            assertThat(trace.projectReads()).isEqualTo(2);
        }
        try (var trace = JdbcProbe.start()) {
            getAs("/api/v1/projects/" + last + "/board", chiefToken).andExpect(status().isOk());
            boardAfter = trace.queries.size();
            assertThat(trace.projectReads()).isEqualTo(3);
        }
        assertThat(after).isEqualTo(before);
        assertThat(boardAfter).isEqualTo(boardBefore);
        System.out.printf(
                "M4_QUERY_COUNT list_projects_added=20 data_queries=2 http_before=%d http_after=%d board_cards=1/100 data_queries=3 http_before=%d http_after=%d%n",
                before, after, boardBefore, boardAfter);
    }
}
