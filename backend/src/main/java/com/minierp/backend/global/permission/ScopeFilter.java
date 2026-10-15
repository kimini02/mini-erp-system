package com.minierp.backend.global.permission;
import java.util.*;
public record ScopeFilter(DataScope scope, long userId, List<Long> departmentIds) {
    public String predicate(String alias, Map<String,Object> parameters) {
        if (scope == DataScope.ALL) return "1=1";
        if (scope == DataScope.SELF) { parameters.put("scopeUser",userId); return alias+".id=:scopeUser"; }
        parameters.put("scopeDepartments",departmentIds);
        return departmentIds.isEmpty() ? "1=0" : alias+".department_id IN (:scopeDepartments)";
    }
    public boolean includesDepartment(long id) { return scope==DataScope.ALL || departmentIds.contains(id); }
}
