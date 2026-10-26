package com.minierp.backend.domain.user.service;
import com.minierp.backend.domain.user.dto.EmployeeRequests.*;
import com.minierp.backend.domain.user.dto.RegistrationResult;
import com.minierp.backend.domain.user.repository.EmployeeRepository;
import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.permission.*;
import com.minierp.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class EmployeeService {
    private final com.minierp.backend.domain.org.repository.DepartmentTreeLock treeLock;
    private final SqlStore db; private final EmployeeRepository repository; private final PermissionAccess access;
    private final ApplicationEventPublisher events; private final Clock clock;
    private final com.minierp.backend.global.service.InvitationTokenStore invitationTokens;
    public record InvitationEmailChanged(long userId,String oldEmail) {}
    public record InvitationRequested(long userId,String email,RegistrationResult result) {}
    public Map<String,Object> raw(long id) { return db.one("SELECT * FROM users WHERE id=:id",map("id",id),ErrorCode.USER_NOT_FOUND); }
    public void lockChief() { db.rows("SELECT id FROM roles WHERE code='CHIEF' FOR UPDATE",Map.of()); }
    public void protectLastChief(long id) {
        var chiefs=db.rows("SELECT u.id FROM users u JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id WHERE u.status='ACTIVE' AND r.code='CHIEF' FOR UPDATE",Map.of());
        if(chiefs.size()<=1 && chiefs.stream().anyMatch(r->number(r,"id")==id)) throw new BusinessException(ErrorCode.LAST_CHIEF);
    }
    private void notHead(long id) { if(!db.rows("SELECT id FROM departments WHERE head_user_id=:id FOR UPDATE",map("id",id)).isEmpty()) throw new BusinessException(ErrorCode.HEAD_MUST_BE_REASSIGNED); }
    private void requireRoleUpdateForPrivilegedTarget(long id) {
        if(db.count("SELECT COUNT(*) FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=:id AND r.code<>'EMPLOYEE'",map("id",id))>0)
            access.require("ROLE",Action.UPDATE);
    }
    public List<Map<String,Object>> views(List<Long> ids) {
        if(ids.isEmpty()) return List.of();
        var rows=db.rows("""
            SELECT u.id,u.login_id,u.user_name,u.user_email,u.department_id,u.position_id,u.hire_date,u.retired_date,u.status,u.user_pw,
              (SELECT COALESCE(SUM(l.days),0) FROM leave_ledger l WHERE l.user_id=u.id) AS remaining_annual_leave,u.created_at,u.updated_at,d.name AS department_name,d.code AS department_code,p.name AS position_name
            FROM users u JOIN departments d ON d.id=u.department_id JOIN positions p ON p.id=u.position_id WHERE u.id IN (:ids)
            """,map("ids",ids));
        Map<Long,List<Map<String,Object>>> roles=new HashMap<>();
        for(var row:db.rows("SELECT ur.user_id,r.code,r.name FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id IN (:ids) ORDER BY r.id",map("ids",ids))) roles.computeIfAbsent(number(row,"user_id"),k->new ArrayList<>()).add(map("code",row.get("code"),"name",row.get("name")));
        Map<Long,Map<String,Object>> byId=new HashMap<>();
        for(var row:rows) {
            long id=number(row,"id"); var userRoles=roles.getOrDefault(id,List.of());
            String legacy=userRoles.stream().anyMatch(r->"CHIEF".equals(r.get("code")))?"ADMIN":userRoles.stream().anyMatch(r->"LEADER".equals(r.get("code")))?"TEAM_LEADER":"USER";
            String code=(String)row.get("department_code"); code=switch(code) { case "D-0002"->"01";case "D-0003"->"02";case "D-0004"->"03";default->code; };
            // M1 프론트 작업 후 제거: departmentCode, departmentName, position, role aliases.
            byId.put(id,map("id",id,"loginId",row.get("login_id"),"name",row.get("user_name"),"email",row.get("user_email"),
                "department",map("id",row.get("department_id"),"name",row.get("department_name")),"positionId",row.get("position_id"),"position",row.get("position_name"),
                "departmentCode",code,"departmentName",row.get("department_name"),"role",legacy,"roles",userRoles,"hireDate",row.get("hire_date"),"retiredDate",row.get("retired_date"),"status",row.get("status"),
                "invitationPending",row.get("user_pw")==null,"remainingAnnualLeave",row.get("remaining_annual_leave"),"createdAt",row.get("created_at"),"updatedAt",row.get("updated_at")));
        }
        return ids.stream().map(byId::get).toList();
    }
    public static Map<String,Object> page(List<?> content,int page,int size,long total) {
        long pages=(total+size-1)/size;
        return map("content",content,"page",map("number",page,"size",size,"totalElements",total,"totalPages",pages,"first",page==0,"last",page+1>=pages,"numberOfElements",content.size()));
    }
    public Map<String,Object> list(String q,Long departmentId,boolean includeSub,String status,int page,int size,String sort) {
        if(page<0 || size<1 || size>200) throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        if(status!=null) try { com.minierp.backend.domain.user.entity.UserStatus.valueOf(status); } catch(IllegalArgumentException ex) { throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE); }
        var scope=access.scope("EMPLOYEE"); List<Long> departments=null;
        if(departmentId!=null) {
            if(!scope.includesDepartment(departmentId)) throw new BusinessException(ErrorCode.ACCESS_DENIED);
            db.one("SELECT id FROM departments WHERE id=:id",map("id",departmentId),ErrorCode.DEPARTMENT_NOT_FOUND);
            departments=includeSub?access.descendants(departmentId):List.of(departmentId);
        }
        String[] parts=sort.split(","); var columns=Map.of("userName","u.user_name","loginId","u.login_id","hireDate","u.hire_date","status","u.status");
        if(!columns.containsKey(parts[0]) || parts.length>2 || parts.length==2 && !Set.of("asc","desc").contains(parts[1].toLowerCase())) throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        String order=columns.get(parts[0])+(parts.length==2?" "+parts[1]:" ASC");
        var selected=repository.search(scope,q,departments,status,page,size,order);
        var result=page(views(selected.ids()),page,size,selected.total());result.put("statusCounts",selected.counts());return result;
    }
    public Map<String,Object> detail(long id) {
        if(id!=access.actor()) { access.require("EMPLOYEE",Action.VIEW);access.assertUserInScope("EMPLOYEE",id); }
        raw(id);return views(List.of(id)).get(0);
    }
    public String preview(LocalDate hireDate) {
        var rows=db.rows("SELECT last_seq FROM login_id_sequences WHERE hire_year=:year",map("year",hireDate.getYear()));
        long next=rows.isEmpty()?1:number(rows.get(0),"last_seq")+1;return String.format("%d%03d",hireDate.getYear(),next);
    }
    private List<Long> roleIds(List<String> codes) {
        if(codes==null || codes.isEmpty()) throw new BusinessException(ErrorCode.ROLE_REQUIRED);
        Set<String> unique=new HashSet<>(codes); var rows=db.rows("SELECT id,code FROM roles WHERE code IN (:codes)",map("codes",unique));
        if(rows.size()!=unique.size()) throw new BusinessException(ErrorCode.ROLE_NOT_FOUND);
        return rows.stream().map(r->number(r,"id")).toList();
    }
    @Transactional public RegistrationResult register(Create r) {
        // Creating an account must not bypass the separate permission to assign roles.
        if (r.roleCodes() != null && r.roleCodes().stream().anyMatch(code -> !"EMPLOYEE".equals(code))) {
            access.require("ROLE", Action.UPDATE);
        }
        var roles=roleIds(r.roleCodes());
        db.one("SELECT id FROM departments WHERE id=:id",map("id",r.departmentId()),ErrorCode.DEPARTMENT_NOT_FOUND);
        var position=db.one("SELECT name FROM positions WHERE id=:id",map("id",r.positionId()),ErrorCode.POSITION_NOT_FOUND);
        if(db.count("SELECT COUNT(*) FROM users WHERE user_email=:email",map("email",r.email()))>0) throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        int year=r.hireDate().getYear();
        db.update("INSERT INTO login_id_sequences(hire_year,last_seq) VALUES(:year,0) ON DUPLICATE KEY UPDATE hire_year=VALUES(hire_year)",map("year",year));
        long next=number(db.one("SELECT last_seq FROM login_id_sequences WHERE hire_year=:year FOR UPDATE",map("year",year),ErrorCode.RESOURCE_NOT_FOUND),"last_seq")+1;
        db.update("UPDATE login_id_sequences SET last_seq=:next WHERE hire_year=:year",map("year",year,"next",next));
        String loginId=String.format("%d%03d",year,next);
        long id;
        try { id=db.insert("""
            INSERT INTO users(login_id,user_name,user_email,user_pw,department_id,position_id,hire_date,status,must_change_pw,token_version,
              created_at,updated_at)
            VALUES(:login,:name,:email,NULL,:department,:position,:hire,'ACTIVE',0,0,:now,:now)
            """,map("login",loginId,"name",r.name(),"email",r.email(),"department",r.departmentId(),"position",r.positionId(),"hire",r.hireDate(),"now",LocalDateTime.now(clock))); }
        catch(org.springframework.dao.DuplicateKeyException ex) { throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS); }
        for(long role:roles) db.update("INSERT INTO user_roles(user_id,role_id) VALUES(:user,:role)",map("user",id,"role",role));
        var result=new RegistrationResult(id,loginId);events.publishEvent(new InvitationRequested(id,r.email(),result));return result;
    }
    @Transactional public Map<String,Object> patch(long id,Patch r) {
        // Same order as OrgService.setHead: tree first, then user/current head reads.
        if(r.departmentId()!=null) treeLock.lock();
        access.assertUserInScope("EMPLOYEE",id);
        var old=r.email()==null && r.departmentId()==null?raw(id):db.one("SELECT * FROM users WHERE id=:id FOR UPDATE",map("id",id),ErrorCode.USER_NOT_FOUND);
        if(r.email()!=null && !r.email().equals(old.get("user_email"))) requireRoleUpdateForPrivilegedTarget(id);
        if(r.departmentId()!=null) { if(number(old,"department_id")!=r.departmentId()) notHead(id); db.one("SELECT id FROM departments WHERE id=:id",map("id",r.departmentId()),ErrorCode.DEPARTMENT_NOT_FOUND); }
        if(r.positionId()!=null) db.one("SELECT id FROM positions WHERE id=:id",map("id",r.positionId()),ErrorCode.POSITION_NOT_FOUND);
        if(r.name()!=null && r.name().isBlank() || r.email()!=null && r.email().isBlank()) throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        try { db.update("UPDATE users SET user_name=COALESCE(:name,user_name),user_email=COALESCE(:email,user_email),department_id=COALESCE(:dept,department_id),position_id=COALESCE(:position,position_id),hire_date=COALESCE(:hire,hire_date),updated_at=:now WHERE id=:id",
            map("id",id,"name",r.name(),"email",r.email(),"dept",r.departmentId(),"position",r.positionId(),"hire",r.hireDate(),"now",LocalDateTime.now(clock))); }
        catch(org.springframework.dao.DuplicateKeyException ex) { throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS); }
        if(r.email()!=null && !r.email().equals(old.get("user_email"))) events.publishEvent(new InvitationEmailChanged(id,(String)old.get("user_email")));
        return views(List.of(id)).get(0);
    }
    @Transactional public Map<String,Object> status(long id,com.minierp.backend.domain.user.entity.UserStatus status) {
        if(status!=com.minierp.backend.domain.user.entity.UserStatus.ACTIVE) treeLock.lock();
        access.assertUserInScope("EMPLOYEE",id);lockChief();
        var user=db.one("SELECT * FROM users WHERE id=:id FOR UPDATE",map("id",id),ErrorCode.USER_NOT_FOUND);
        if(!status.name().equals(user.get("status"))) {
            if(status!=com.minierp.backend.domain.user.entity.UserStatus.ACTIVE) { notHead(id);protectLastChief(id); }
            db.update("UPDATE users SET retired_date=CASE WHEN :status='RETIRED' THEN :date WHEN :status='ACTIVE' AND status='RETIRED' THEN NULL ELSE retired_date END,status=:status,token_version=token_version+:increment,updated_at=:now WHERE id=:id",map("id",id,"status",status.name(),"date",LocalDate.now(clock),"increment",status==com.minierp.backend.domain.user.entity.UserStatus.ACTIVE?0:1,"now",LocalDateTime.now(clock)));
        } return views(List.of(id)).get(0);
    }
    @Transactional public void replaceRoles(long id,List<String> codes) {
        lockChief(); raw(id); var ids=roleIds(codes);
        if(!codes.contains("CHIEF")) protectLastChief(id);
        db.update("DELETE FROM user_roles WHERE user_id=:id",map("id",id));
        for(long role:ids) db.update("INSERT INTO user_roles(user_id,role_id) VALUES(:id,:role)",map("id",id,"role",role));
    }
    @Transactional public RegistrationResult invitation(long id) {
        var user=db.one("SELECT * FROM users WHERE id=:id FOR UPDATE",map("id",id),ErrorCode.USER_NOT_FOUND);
        requireRoleUpdateForPrivilegedTarget(id);
        if(user.get("user_pw")!=null) throw new BusinessException(ErrorCode.INVITATION_ALREADY_ACCEPTED);
        invitationTokens.revoke(id);
        var result=new RegistrationResult(id,(String)user.get("login_id"));events.publishEvent(new InvitationRequested(id,(String)user.get("user_email"),result));return result;
    }
}
