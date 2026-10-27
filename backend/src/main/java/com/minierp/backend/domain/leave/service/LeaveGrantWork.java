package com.minierp.backend.domain.leave.service;
import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;
@Service @RequiredArgsConstructor
public class LeaveGrantWork {
    private final SqlStore db;private final Clock clock;
    public record Counts(int processed,int skipped,String message) {}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Counts run(LocalDate through) {
        int processed=0;List<Long> missing=new ArrayList<>(),legacy=new ArrayList<>();var now=LocalDateTime.now(clock);
        for(var candidate:db.rows("SELECT id FROM users WHERE status IN ('ACTIVE','ON_LEAVE') ORDER BY id",Map.of())) {
            long id=number(candidate,"id");var user=db.one("SELECT id,status,hire_date FROM users WHERE id=:id FOR UPDATE",map("id",id),ErrorCode.USER_NOT_FOUND);
            if(!Set.of("ACTIVE","ON_LEAVE").contains(user.get("status")))continue;
            if(user.get("hire_date")==null){missing.add(id);continue;}
            if(db.count("SELECT COUNT(*) FROM leave_ledger WHERE user_id=:id AND leave_year=:year AND entry_type='GRANT' AND grant_key IS NULL",map("id",id,"year",through.getYear()))>0){legacy.add(id);continue;}
            var existing=new HashSet<>(db.rows("SELECT grant_key FROM leave_ledger WHERE user_id=:id AND grant_key IS NOT NULL",map("id",id)).stream().map(r->(String)r.get("grant_key")).toList());
            for(var grant:LeaveGrantRules.due(id,(LocalDate)user.get("hire_date"),through)) {
                if(existing.contains(grant.key()))continue;
                processed+=db.update("INSERT INTO leave_ledger(user_id,leave_year,entry_type,days,grant_key,memo,created_at) VALUES(:id,:year,'GRANT',:days,:key,:memo,:now)",map("id",id,"year",grant.date().getYear(),"days",grant.days(),"key",grant.key(),"memo",grant.memo(),"now",now));
            }
        }
        List<String> messages=new ArrayList<>();
        if(!missing.isEmpty())messages.add("입사일 없음: "+missing);
        if(!legacy.isEmpty())messages.add("옛 부여 있음: "+legacy);
        return new Counts(processed,missing.size()+legacy.size(),messages.isEmpty()?null:String.join(" · ",messages));
    }
}
