package com.minierp.backend.domain.approval.service;
import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class FormService {
    private final SqlStore db;
    private final ApprovalLineResolver resolver;
    public record Options(Boolean dedupeApprover,Boolean fallbackToUpper,Boolean skipSelf,Boolean allowLineEdit) {}
    public record Step(String lineKind,String approverType,String roleCode,Long userId) {}
    public static String shortName(String code) { return switch(code) {case "LEAVE"->"휴가";case "OVERTIME"->"특근";case "GENERAL"->"품의";case "ATTENDANCE_FIX"->"근태 정정";default->throw new BusinessException(ErrorCode.FORM_NOT_FOUND);}; }
    private Map<String,Object> presentation(Map<String,Object> f) {
        String code=(String)f.get("code");
        String description=switch(code) {case "LEAVE"->"연차·반차·병가·경조 휴가를 신청합니다.";case "OVERTIME"->"하루 단위의 특근을 신청합니다.";case "ATTENDANCE_FIX"->"지난 근무일의 출퇴근 기록 정정을 신청합니다.";default->"업무에 필요한 일반 품의를 올립니다.";};
        return map("code",code,"name",f.get("name"),"shortName",shortName(code),"prefix",f.get("doc_prefix"),"description",description);
    }
    public List<Map<String,Object>> list() {
        return db.rows("""
          SELECT f.*,COUNT(CASE WHEN s.line_kind='APPROVER' THEN 1 END) AS approvers,
          COUNT(CASE WHEN s.line_kind='REFERENCE' THEN 1 END) AS refs
          FROM approval_forms f LEFT JOIN approval_form_steps s ON s.form_id=f.id GROUP BY f.id ORDER BY f.id
          """,Map.of()).stream().map(f->{var out=presentation(f);out.put("allowLineEdit",bool(f,"allow_line_edit"));out.put("approverStepCount",f.get("approvers"));out.put("referenceStepCount",f.get("refs"));return out;}).toList();
    }
    public Map<String,Object> detail(String code) {
        var f=resolver.form(code);var out=presentation(f);
        out.put("options",map("dedupeApprover",bool(f,"dedupe_approver"),"fallbackToUpper",bool(f,"fallback_to_upper"),"skipSelf",bool(f,"skip_self"),"allowLineEdit",bool(f,"allow_line_edit")));
        out.put("steps",db.rows("SELECT s.*,r.code AS role_code,r.name AS role_name,u.user_name FROM approval_form_steps s LEFT JOIN roles r ON r.id=s.role_id LEFT JOIN users u ON u.id=s.user_id WHERE s.form_id=:id ORDER BY s.step_order",map("id",f.get("id"))).stream().map(s->map("order",s.get("step_order"),"lineKind",s.get("line_kind"),"approverType",s.get("approver_type"),"roleCode",s.get("role_code"),"userId",s.get("user_id"),"label",switch((String)s.get("approver_type")) {case "DEPT_HEAD"->"기안자의 부서장";case "UPPER_DEPT_HEAD"->"기안자의 상위 부서장";case "ROLE"->s.get("role_name");default->s.get("user_name");})).toList());
        return out;
    }
    @Transactional public Map<String,Object> patch(String code,Options o) {
        var f=resolver.form(code);
        db.update("UPDATE approval_forms SET dedupe_approver=COALESCE(:dedupe,dedupe_approver),fallback_to_upper=COALESCE(:fallback,fallback_to_upper),skip_self=COALESCE(:self,skip_self),allow_line_edit=COALESCE(:edit,allow_line_edit) WHERE id=:id",map("id",f.get("id"),"dedupe",o.dedupeApprover(),"fallback",o.fallbackToUpper(),"self",o.skipSelf(),"edit",o.allowLineEdit()));
        return detail(code);
    }
    @Transactional public Map<String,Object> steps(String code,List<Step> steps) {
        var f=resolver.form(code);
        if(steps==null || steps.isEmpty() || steps.stream().anyMatch(s->s==null || s.lineKind()==null || !Set.of("APPROVER","REFERENCE").contains(s.lineKind()) || s.approverType()==null || !Set.of("DEPT_HEAD","UPPER_DEPT_HEAD","ROLE","USER").contains(s.approverType())) || steps.stream().noneMatch(s->"APPROVER".equals(s.lineKind()))) throw new BusinessException(ErrorCode.INVALID_APPROVAL_STEPS);
        List<Map<String,Object>> resolved=new ArrayList<>();
        for(var step:steps) {
            Long role=null,user=null;
            if(step.approverType().equals("ROLE")) {
                if(step.roleCode()==null || step.roleCode().isBlank()) throw new BusinessException(ErrorCode.INVALID_APPROVAL_STEPS);
                role=number(db.one("SELECT id FROM roles WHERE code=:code",map("code",step.roleCode()),ErrorCode.ROLE_NOT_FOUND),"id");
            }
            if(step.approverType().equals("USER")) {
                if(step.userId()==null) throw new BusinessException(ErrorCode.INVALID_APPROVAL_STEPS);
                user=number(db.one("SELECT id FROM users WHERE id=:id",map("id",step.userId()),ErrorCode.USER_NOT_FOUND),"id");
            }
            resolved.add(map("form",f.get("id"),"kind",step.lineKind(),"type",step.approverType(),"role",role,"user",user,"order",resolved.size()+1));
        }
        db.one("SELECT id FROM approval_forms WHERE id=:id FOR UPDATE",map("id",f.get("id")),ErrorCode.FORM_NOT_FOUND);
        db.update("DELETE FROM approval_form_steps WHERE form_id=:id",map("id",f.get("id")));
        for(var step:resolved) db.update("INSERT INTO approval_form_steps(form_id,step_order,line_kind,approver_type,role_id,user_id) VALUES(:form,:order,:kind,:type,:role,:user)",step);
        return detail(code);
    }
}
