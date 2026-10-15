package com.minierp.backend.global.permission;

import com.minierp.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.*;
import java.util.*;

@Component @RequiredArgsConstructor
public class PermissionAccess {
    private final NamedParameterJdbcTemplate jdbc;
    public record Grant(String menu, Set<String> actions, DataScope scope) {}

    public long actor() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || !auth.isAuthenticated()) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        try { return Long.parseLong(auth.getName()); }
        catch(NumberFormatException ex) { throw new BusinessException(ErrorCode.UNAUTHORIZED); }
    }
    public void require(String menu, Action action) { require(actor(),menu,action); }
    public void require(long userId,String menu,Action action) {
        Grant grant=grants(userId).get(menu);
        if(grant==null || !grant.actions().contains(action.code)) throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }

    @SuppressWarnings("unchecked")
    public Map<String,Grant> grants(long userId) {
        // Request-local reuse only; no application/session/Redis permission cache.
        RequestAttributes request=RequestContextHolder.getRequestAttributes();
        String key=PermissionAccess.class.getName()+userId;
        if(request!=null && request.getAttribute(key,RequestAttributes.SCOPE_REQUEST)!=null)
            return (Map<String,Grant>)request.getAttribute(key,RequestAttributes.SCOPE_REQUEST);
        Map<String,Grant> result=new LinkedHashMap<>();
        jdbc.query("""
            SELECT m.code,p.can_view,p.can_create,p.can_update,p.can_delete,p.data_scope
            FROM user_roles ur JOIN role_permissions p ON p.role_id=ur.role_id JOIN menus m ON m.id=p.menu_id
            WHERE ur.user_id=:id ORDER BY m.sort_order,m.id
            """,Map.of("id",userId),rs -> {
                String menu=rs.getString(1); Grant previous=result.get(menu);
                Set<String> actions=new LinkedHashSet<>(previous==null?Set.of():previous.actions());
                for(int i=0;i<4;i++) if(rs.getBoolean(i+2)) actions.add("VCUD".substring(i,i+1));
                boolean enabled = rs.getBoolean(2) || rs.getBoolean(3) || rs.getBoolean(4) || rs.getBoolean(5);
                String raw=rs.getString(6); DataScope scope=!enabled || raw==null?null:DataScope.valueOf(raw);
                if(previous!=null && previous.scope()!=null && (scope==null || previous.scope().ordinal()>scope.ordinal())) scope=previous.scope();
                result.put(menu,new Grant(menu,actions,scope));
            });
        if(request!=null) request.setAttribute(key,result,RequestAttributes.SCOPE_REQUEST);
        return result;
    }
    public List<Long> descendants(long departmentId) {
        return jdbc.queryForList("""
            WITH RECURSIVE subtree(id) AS (
              SELECT id FROM departments WHERE id=:id
              UNION ALL SELECT d.id FROM departments d JOIN subtree s ON d.parent_id=s.id
            ) SELECT id FROM subtree
            """,Map.of("id",departmentId),Long.class);
    }
    public ScopeFilter scope(String menu) { return scope(menu,actor()); }
    public ScopeFilter scope(String menu,long userId) {
        Grant grant=grants(userId).get(menu);
        if(grant==null || grant.actions().isEmpty() || grant.scope()==null) throw new BusinessException(ErrorCode.ACCESS_DENIED);
        Long department=jdbc.queryForObject("SELECT department_id FROM users WHERE id=:id",Map.of("id",userId),Long.class);
        List<Long> ids=grant.scope()==DataScope.DEPT_TREE?descendants(department):List.of(department);
        return new ScopeFilter(grant.scope(),userId,ids);
    }
    public void assertUserInScope(String menu,long userId) { assertUserInScope(menu,actor(),userId); }
    public void assertUserInScope(String menu,long actorId,long userId) {
        Map<String,Object> args=new HashMap<>(); args.put("target",userId);
        String where=scope(menu,actorId).predicate("u",args);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM users u WHERE u.id=:target AND "+where,args,Long.class)==0)
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }
}
