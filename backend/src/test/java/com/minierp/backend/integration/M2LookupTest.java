package com.minierp.backend.integration;
import com.minierp.backend.support.ApprovalApiTestSupport;
import com.minierp.backend.domain.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
public class M2LookupTest extends ApprovalApiTestSupport {

    @Test void ordinaryEmployeeCanSelectActivePeopleWithoutSensitiveFields() throws Exception {
        String prefix="lookup"+UUID.randomUUID();var a=user(prefix+"A",UserRole.USER);var b=user(prefix+"B",UserRole.USER);var retired=user(prefix+"C",UserRole.USER);var absent=user(prefix+"D",UserRole.USER);
        jdbc.update("UPDATE users SET status='RETIRED' WHERE id=?",retired.getId());jdbc.update("UPDATE users SET status='ON_LEAVE' WHERE id=?",absent.getId());
        getAs("/api/v1/users",employeeToken).andExpect(status().isForbidden());
        var result=data(getAs("/api/v1/users/lookup?q="+prefix+"&departmentId=2&size=100",employeeToken));assertThat(result.size()).isEqualTo(2);assertThat(result.get(0).path("id").asLong()).isEqualTo(a.getId());
        assertThat(result.get(0).properties().stream().map(Map.Entry::getKey).toList()).containsExactlyInAnyOrder("id","name","title","department","departmentId");
        assertThat(data(getAs("/api/v1/users/lookup?q="+b.getLoginId(),employeeToken)).get(0).path("id").asLong()).isEqualTo(b.getId());
        assertThat(data(getAs("/api/v1/users/lookup?q="+prefix+"&departmentId=1",employeeToken)).size()).isZero();
        assertThat(data(getAs("/api/v1/users/lookup?q=%25",employeeToken)).size()).isZero();
        getAs("/api/v1/users/lookup?size=101",employeeToken).andExpect(status().isBadRequest());mvc.perform(get("/api/v1/users/lookup")).andExpect(status().isUnauthorized());
    }

}
