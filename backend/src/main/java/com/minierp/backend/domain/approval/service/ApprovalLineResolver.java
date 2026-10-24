package com.minierp.backend.domain.approval.service;
import com.minierp.backend.domain.approval.dto.DocumentRequest.ManualStep;
import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;
@Service @RequiredArgsConstructor
public class ApprovalLineResolver {
    private final SqlStore db;
    public Map<String,Object> form(String code) { return db.one("SELECT * FROM approval_forms WHERE code=:code",map("code",code),ErrorCode.FORM_NOT_FOUND); }
    public Map<String,Object> person(long id) { return db.one("""
        SELECT u.id,u.user_name AS name,p.name AS title,d.name AS department,u.department_id,u.status
        FROM users u JOIN positions p ON p.id=u.position_id JOIN departments d ON d.id=u.department_id WHERE u.id=:id
        """,map("id",id),ErrorCode.APPROVER_NOT_FOUND); }
    public record Resolved(int order,String kind,Map<String,Object> person,String source,String rule,String skipReason,boolean fallback,String note) {}
    public List<Resolved> resolve(Map<String,Object> form,long drafter,long draftDepartment,List<ManualStep> manual) {
        if(manual!=null && !bool(form,"allow_line_edit")) throw new BusinessException(ErrorCode.LINE_EDIT_NOT_ALLOWED);
        List<Map<String,Object>> steps;
        if(manual!=null) {
            if(manual.stream().anyMatch(s->s==null || s.userId()==null || s.lineKind()==null || !Set.of("APPROVER","REFERENCE").contains(s.lineKind())) || manual.stream().noneMatch(s->"APPROVER".equals(s.lineKind()))) throw new BusinessException(ErrorCode.INVALID_APPROVAL_STEPS);
            steps=new ArrayList<>();int n=0;
            for(var s:manual) steps.add(map("step_order",++n,"line_kind",s.lineKind(),"approver_type","USER","user_id",s.userId()));
        } else steps=db.rows("SELECT * FROM approval_form_steps WHERE form_id=:id ORDER BY step_order",map("id",form.get("id")));
        if(steps.stream().noneMatch(s->"APPROVER".equals(s.get("line_kind")))) throw new BusinessException(ErrorCode.INVALID_APPROVAL_STEPS);
        List<Resolved> resolved=new ArrayList<>();Set<Long> seen=new HashSet<>();
        for(var step:steps) {
            String type=(String)step.get("approver_type");String kind=(String)step.get("line_kind");
            try {
            Map<String,Object> person;boolean fallback=false;String note=null;
            switch(type) {
                case "USER" -> person=person(number(step,"user_id"));
                case "ROLE" -> {
                    var rows=db.rows("SELECT u.id FROM users u JOIN user_roles ur ON ur.user_id=u.id WHERE ur.role_id=:role AND u.status='ACTIVE' ORDER BY u.id LIMIT 1",map("role",step.get("role_id")));
                    if(rows.isEmpty()) throw new BusinessException(ErrorCode.APPROVER_NOT_FOUND);
                    person=person(number(rows.get(0),"id"));
                }
                case "DEPT_HEAD", "UPPER_DEPT_HEAD" -> {
                    Long department=draftDepartment;
                    if(type.equals("UPPER_DEPT_HEAD")) department=parent(department);
                    String sourceDepartment=department==null?null:(String)db.one("SELECT name FROM departments WHERE id=:id",map("id",department),ErrorCode.APPROVER_NOT_FOUND).get("name");
                    person=null; Set<Long> visited=new HashSet<>();
                    while(department!=null && visited.add(department)) {
                        var d=db.one("SELECT parent_id,head_user_id FROM departments WHERE id=:id",map("id",department),ErrorCode.APPROVER_NOT_FOUND);
                        if(d.get("head_user_id")!=null) {
                            var candidate=person(number(d,"head_user_id"));
                            if("ACTIVE".equals(candidate.get("status"))) { person=candidate;break; }
                        }
                        if(!bool(form,"fallback_to_upper")) break;
                        department=d.get("parent_id")==null?null:number(d,"parent_id");fallback=true;
                    }
                    if(person==null) throw new BusinessException(ErrorCode.APPROVER_NOT_FOUND);
                    if(fallback) note=sourceDepartment+" 부서장이 비어 있어 "+person.get("department")+" 부서장에게 넘어갑니다.";
                }
                default -> throw new BusinessException(ErrorCode.INVALID_APPROVAL_STEPS);
            }
            if(!"ACTIVE".equals(person.get("status"))) throw new BusinessException(ErrorCode.APPROVER_NOT_FOUND);
            long approver=number(person,"id");String skip=null;
            if(kind.equals("APPROVER")) {
                if(bool(form,"skip_self") && approver==drafter) skip="SELF";
                else if(bool(form,"dedupe_approver") && seen.contains(approver)) skip="DUPLICATE";
                seen.add(approver);
            } else if(approver==drafter) skip="SELF";
            String label=manual!=null?"직접 지정":switch(type) {case "DEPT_HEAD"->"기안자의 부서장";case "UPPER_DEPT_HEAD"->"기안자의 상위 부서장";case "ROLE"->"역할 담당자";default->"지정 사원";};
            resolved.add(new Resolved(((Number)step.get("step_order")).intValue(),kind,person,manual==null?"RULE":"MANUAL",label,skip,fallback,note));
            } catch(BusinessException ex) {
                if(ex.getErrorCode()!=ErrorCode.APPROVER_NOT_FOUND) throw ex;
                if(kind.equals("REFERENCE")) {
                    resolved.add(new Resolved(((Number)step.get("step_order")).intValue(),kind,null,manual==null?"RULE":"MANUAL",label(type),"NO_REFERENCE",false,"참조할 사람이 없어 건너뜁니다."));
                    continue;
                }
                throw new ResolutionFailure(resolved,((Number)step.get("step_order")).intValue(),type,kind);
            }
        }
        return resolved;
    }
    private Long parent(long department) {
        var row=db.one("SELECT parent_id FROM departments WHERE id=:id",map("id",department),ErrorCode.APPROVER_NOT_FOUND);
        return row.get("parent_id")==null?null:number(row,"parent_id");
    }
    public Map<String,Object> preview(String code,long drafter,List<ManualStep> manual) {
        var form=form(code);var user=person(drafter);
        try {
            var resolved=resolve(form,drafter,number(user,"department_id"),manual);
            return map("rows",resolved.stream().map(this::previewRow).toList(),
                "finalLine",resolved.stream().filter(r->r.kind().equals("APPROVER") && r.skipReason()==null).map(r->publicPerson(r.person())).toList(),"references",references(resolved),"error",null);
        } catch(ResolutionFailure ex) {
            var rows=new ArrayList<>(ex.resolved.stream().map(this::previewRow).toList());
            String label=switch(ex.rule) { case "DEPT_HEAD"->"기안자의 부서장";case "UPPER_DEPT_HEAD"->"기안자의 상위 부서장";case "ROLE"->"역할 담당자";default->"지정 사원"; };
            rows.add(map("order",ex.order,"kind",ex.kind,"rule",label,"result",null,"status","ERROR","note",ex.getMessage()));
            return map("rows",rows,"finalLine",List.of(),"references",references(ex.resolved),"error",ex.getMessage());
        }
    }
    private static String label(String type) {
        return switch(type) {case "DEPT_HEAD"->"기안자의 부서장";case "UPPER_DEPT_HEAD"->"기안자의 상위 부서장";case "ROLE"->"역할 담당자";default->"지정 사원";};
    }
    private List<Map<String,Object>> references(List<Resolved> resolved) {
        return resolved.stream().filter(r->r.kind().equals("REFERENCE") && r.skipReason()==null).map(r->publicPerson(r.person())).toList();
    }
    private Map<String,Object> previewRow(Resolved r) {
        String note=r.note();
        if("SELF".equals(r.skipReason())) note="기안자 본인이라 이 단계는 건너뜁니다.";
        if("DUPLICATE".equals(r.skipReason())) note="앞 단계와 같은 결재자라 건너뜁니다.";
        return map("order",r.order(),"kind",r.kind(),"rule",r.rule(),"result",r.person()==null?null:publicPerson(r.person()),"status",r.skipReason()!=null?"SKIPPED":r.fallback()?"FALLBACK":"OK","note",note);
    }
    private static final class ResolutionFailure extends BusinessException {
        final List<Resolved> resolved;final int order;final String rule;final String kind;
        ResolutionFailure(List<Resolved> resolved,int order,String rule,String kind) {
            super(ErrorCode.APPROVER_NOT_FOUND);this.resolved=List.copyOf(resolved);this.order=order;this.rule=rule;this.kind=kind;
        }
    }
    public static Map<String,Object> publicPerson(Map<String,Object> p) { return map("id",p.get("id"),"name",p.get("name"),"title",p.get("title"),"department",p.get("department")); }
}
