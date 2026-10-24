package com.minierp.backend.integration;
import com.minierp.backend.support.ApprovalApiTestSupport;
import com.minierp.backend.domain.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
public class M2FormApiTest extends ApprovalApiTestSupport {

    @Test void listDetailAndPartialOptionsHaveFrontendShape() throws Exception {
        var all=data(getAs("/api/v1/approval-forms",employeeToken));assertThat(all.size()).isEqualTo(3);
        assertThat(all.get(0).path("shortName").asText()).isEqualTo("휴가");assertThat(all.get(0).path("approverStepCount").asInt()).isEqualTo(1);
        var changed=data(mvc.perform(patch("/api/v1/approval-forms/LEAVE").header("Authorization","Bearer "+chiefToken).contentType(MediaType.APPLICATION_JSON).content("{\"skipSelf\":false}")));
        assertThat(changed.path("options").path("skipSelf").asBoolean()).isFalse();assertThat(changed.path("options").path("dedupeApprover").asBoolean()).isTrue();
        assertThat(changed.path("steps").get(0).path("label").asText()).isNotBlank();
        getAs("/api/v1/approval-forms/LEAVE",employeeToken).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/approval-forms/LEAVE").header("Authorization","Bearer "+employeeToken).contentType(MediaType.APPLICATION_JSON).content("{\"skipSelf\":true}")).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT skip_self FROM approval_forms WHERE code='LEAVE'",Boolean.class)).isFalse();
    }
    @Test void wholeStepReplacementDoesNotChangeSubmittedSnapshot() throws Exception {
        long id=submittedLeave();
        var steps=List.of(Map.of("lineKind","APPROVER","approverType","USER","userId",chief.getId()),Map.of("lineKind","REFERENCE","approverType","ROLE","roleCode","EMPLOYEE"));
        data(mvc.perform(put("/api/v1/approval-forms/LEAVE/steps").header("Authorization","Bearer "+chiefToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(steps))));
        var preview=data(postAs("/api/v1/approval-forms/LEAVE/line-preview",chiefToken,Map.of("drafterId",employee.getId())));
        assertThat(preview.path("rows").get(0).path("kind").asText()).isEqualTo("APPROVER");assertThat(preview.path("rows").get(0).path("result").path("department").asText()).isNotBlank();assertThat(preview.has("references")).isTrue();
        assertThat(data(getAs("/api/v1/approval-documents/"+id,employeeToken)).path("line").get(0).path("approver").path("id").asLong()).isEqualTo(leader.getId());
        mvc.perform(put("/api/v1/approval-forms/LEAVE/steps").header("Authorization","Bearer "+chiefToken).contentType(MediaType.APPLICATION_JSON).content("[]")).andExpect(status().isBadRequest()).andExpect(jsonPath("error.code").value("INVALID_APPROVAL_STEPS"));
        assertThat(data(getAs("/api/v1/approval-forms/LEAVE",chiefToken)).path("steps").size()).isEqualTo(2);
    }
    @Test void roleReferencedOnlyByFormCannotBeDeleted() throws Exception {
        long role=data(postAs("/api/v1/roles",chiefToken,Map.of("name","양식 전용 역할"))).path("id").asLong();
        String code=jdbc.queryForObject("SELECT code FROM roles WHERE id=?",String.class,role);
        data(mvc.perform(put("/api/v1/approval-forms/LEAVE/steps").header("Authorization","Bearer "+chiefToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(List.of(Map.of("lineKind","APPROVER","approverType","ROLE","roleCode",code))))));
        mvc.perform(delete("/api/v1/roles/"+role).header("Authorization","Bearer "+chiefToken)).andExpect(status().isConflict()).andExpect(jsonPath("error.code").value("ROLE_IN_USE"));
    }


    @Test void roleSelectionUsesNumericIdAndDuplicateDetailHasReason() throws Exception {
        var first=user("역할 첫째",UserRole.USER);var second=user("역할 둘째",UserRole.USER);
        jdbc.update("UPDATE users SET login_id=? WHERE id=?","zzz-"+first.getId(),first.getId());jdbc.update("UPDATE users SET login_id=? WHERE id=?","aaa-"+second.getId(),second.getId());
        long role=data(postAs("/api/v1/roles",chiefToken,Map.of("name","선택 순서 역할"))).path("id").asLong();
        jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES(?,?),(?,?)",first.getId(),role,second.getId(),role);
        jdbc.update("DELETE FROM approval_form_steps WHERE form_id=1");jdbc.update("INSERT INTO approval_form_steps(form_id,step_order,line_kind,approver_type,role_id) VALUES(1,1,'APPROVER','ROLE',?),(1,2,'APPROVER','ROLE',?)",role,role);
        var doc=submit(draft("LEAVE",leaveBody("ANNUAL",start,start)));
        assertThat(doc.path("line").get(0).path("approver").path("id").asLong()).isEqualTo(first.getId());
        assertThat(doc.path("line").get(1).path("skipReason").asText()).isEqualTo("DUPLICATE");
    }
}
