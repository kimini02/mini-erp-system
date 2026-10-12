package com.minierp.backend.global.permission;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;
@Repository @RequiredArgsConstructor
public class LegacyRequestScopeRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final PermissionAccess access;
    public List<Long> leaves(long actor,boolean includeCancelled) { return ids("leave_requests","app_status",actor,includeCancelled); }
    public List<Long> overtime(long actor,boolean includeCancelled) { return ids("overtime_requests","status",actor,includeCancelled); }
    private List<Long> ids(String table,String status,long actor,boolean includeCancelled) {
        access.require(actor,"LEAVE_ADMIN",Action.VIEW);
        Map<String,Object> args=new HashMap<>();
        String scope=access.scope("LEAVE_ADMIN",actor).predicate("u",args);
        return jdbc.queryForList("SELECT r.id FROM "+table+" r JOIN users u ON u.id=r.requester_id WHERE "+scope
            +(includeCancelled?"":" AND r."+status+"<>'CANCELLED'")+" ORDER BY r.id",args,Long.class);
    }
}
