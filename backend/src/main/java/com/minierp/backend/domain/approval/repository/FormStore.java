package com.minierp.backend.domain.approval.repository;

import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.map;

@Repository
@RequiredArgsConstructor
public class FormStore {
    private final SqlStore db;

    public int updateOptions(Object id, Boolean dedupe, Boolean fallback, Boolean self, Boolean edit) {
        return db.update(
                "UPDATE approval_forms SET dedupe_approver=COALESCE(:dedupe,dedupe_approver),fallback_to_upper=COALESCE(:fallback,fallback_to_upper),skip_self=COALESCE(:self,skip_self),allow_line_edit=COALESCE(:edit,allow_line_edit) WHERE id=:id",
                map("id", id, "dedupe", dedupe, "fallback", fallback, "self", self, "edit", edit));
    }

    public Map<String, Object> lockForm(Object id) {
        return db.one("SELECT id FROM approval_forms WHERE id=:id FOR UPDATE", map("id", id), ErrorCode.FORM_NOT_FOUND);
    }

    public int deleteSteps(Object id) {
        return db.update("DELETE FROM approval_form_steps WHERE form_id=:id", map("id", id));
    }

    public int insertStep(Object form, String kind, String type, Long role, Long user, int order) {
        return db.update(
                "INSERT INTO approval_form_steps(form_id,step_order,line_kind,approver_type,role_id,user_id) VALUES(:form,:order,:kind,:type,:role,:user)",
                map("form", form, "kind", kind, "type", type, "role", role, "user", user, "order", order));
    }
}
