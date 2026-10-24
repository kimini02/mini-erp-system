package com.minierp.backend.domain.user.service;
import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class UserLookupService {
    private final SqlStore db;
    public List<Map<String,Object>> lookup(String q,Long departmentId,int size) {
        if(size<1 || size>100) throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        var args=map("size",size);String where="u.status='ACTIVE'";
        if(q!=null && !q.isBlank()) {where+=" AND (u.user_name LIKE :q ESCAPE '!' OR u.login_id LIKE :q ESCAPE '!')";args.put("q",q.replace("!","!!").replace("%","!%").replace("_","!_")+"%");}
        if(departmentId!=null) {where+=" AND u.department_id=:dept";args.put("dept",departmentId);}
        return db.rows("SELECT u.id,u.user_name AS name,p.name AS title,d.name AS department,d.id AS departmentId FROM users u JOIN positions p ON p.id=u.position_id JOIN departments d ON d.id=u.department_id WHERE "+where+" ORDER BY u.user_name,u.id LIMIT :size",args);
    }
}
