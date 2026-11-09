package com.minierp.backend.integration;

import com.minierp.backend.support.ApprovalApiTestSupport;
import com.minierp.backend.global.service.InvitationTokenStore;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@TestPropertySource(properties = "app.mail.enabled=false")
class R04ContractProbe extends ApprovalApiTestSupport {
    @MockitoBean
    InvitationTokenStore invitationTokens;

    private com.fasterxml.jackson.databind.JsonNode request(
            MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header("Authorization", "Bearer " + chiefToken);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        return data(mvc.perform(request));
    }

    @Test
    void orgSuccessAndNonNullShapes() throws Exception {
        long child = db.insert(
                "INSERT INTO departments(name,code,parent_id,sort_order,active) VALUES('probe child','PROBE-CHILD',2,999,1)",
                Map.of());
        var detail = request(get("/api/v1/departments/" + child), null);
        assertThat(detail.path("fallbackHead").path("id").asLong()).isEqualTo(leader.getId());
        assertThat(detail.path("fallbackHead").has("departmentName")).isTrue();
        assertThat(detail.path("head").isNull()).isTrue();
        var headed = request(get("/api/v1/departments/2"), null);
        assertThat(headed.path("head").path("id").asLong()).isEqualTo(leader.getId());
        assertThat(headed.path("head").has("departmentName")).isFalse();
        var tree = request(get("/api/v1/departments/tree"), null);
        assertThat(tree.path("head").path("id").asLong()).isEqualTo(chief.getId());
        request(patch("/api/v1/departments/" + child + "/parent"), Map.of("parentId", 3));
        var schedule = request(get("/api/v1/work-schedules/default"), null);
        assertThat(schedule.has("useDefault")).isFalse();
        request(put("/api/v1/work-schedules/default"),
                Map.of("startTime", "09:15", "endTime", "18:15", "lateGraceMin", 5));
        request(get("/api/v1/positions"), null);
        LocalDate date = LocalDate.now(clock).plusDays(17);
        long holiday = db.insert("INSERT INTO holidays(holiday_date,name,substitute) VALUES(:date,'probe holiday',0)",
                Map.of("date", date));
        request(get("/api/v1/holidays").param("year", Integer.toString(date.getYear())), null);
        request(patch("/api/v1/holidays/" + holiday), Map.of("name", "probe changed", "substitute", true));
    }

    @Test
    void userSuccessAndRetiredDate() throws Exception {
        request(put("/api/v1/users/" + employee.getId() + "/roles"), Map.of("roleCodes", List.of("EMPLOYEE")));
        jdbc.update("UPDATE users SET user_pw=NULL WHERE id=?", outsider.getId());
        var invitation = request(post("/api/v1/users/" + outsider.getId() + "/invitation"), null);
        assertThat(invitation.path("invitationSent").asBoolean()).isFalse();
        LocalDate retired = LocalDate.now(clock).minusDays(1);
        jdbc.update("UPDATE users SET status='RETIRED',retired_date=? WHERE id=?", retired, employee.getId());
        var patched = request(patch("/api/v1/users/" + employee.getId()), Map.of("name", "retired probe"));
        assertThat(patched.path("retiredDate").asText()).isEqualTo(retired.toString());
    }

    @Test
    void permissionSuccessAndDescription() throws Exception {
        long role = db.insert("INSERT INTO roles(code,name,description,is_system) VALUES('PROBE','probe role','described',0)",
                Map.of());
        request(get("/api/v1/menus"), null);
        var roles = request(get("/api/v1/roles"), null);
        assertThat(roles.toString()).contains("described");
        var changed = request(patch("/api/v1/roles/" + role), Map.of("description", "changed description"));
        assertThat(changed.path("description").asText()).isEqualTo("changed description");
        request(get("/api/v1/roles/" + role + "/permissions"), null);
        jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES(?,?)", employee.getId(), role);
        request(get("/api/v1/roles/" + role + "/members"), null);
        request(delete("/api/v1/roles/" + role + "/members/" + employee.getId()), null);
        request(delete("/api/v1/roles/" + role), null);
    }
}
