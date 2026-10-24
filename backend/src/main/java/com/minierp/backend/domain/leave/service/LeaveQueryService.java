package com.minierp.backend.domain.leave.service;
import com.fasterxml.jackson.databind.JsonNode;
import com.minierp.backend.domain.approval.handler.BodyValues;
import com.minierp.backend.domain.leave.handler.LeaveFormHandler;
import com.minierp.backend.domain.org.service.WorkingDays;
import com.minierp.backend.global.exception.*;
import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.permission.PermissionAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class LeaveQueryService {
    private final SqlStore db;private final PermissionAccess access;private final Clock clock;
    private final LeaveFormHandler handler;private final WorkingDays workingDays;
    private int year(Integer year) {int y=year==null?LocalDate.now(clock).getYear():year;if(y<1 || y>9999) throw BodyValues.invalid();return y;}
    public Map<String,Object> balance(Integer requestedYear) {
        int y=year(requestedYear);
        var result=db.one("""
            SELECT COALESCE(SUM(CASE WHEN entry_type='GRANT' THEN days ELSE 0 END),0) AS granted,
            -COALESCE(SUM(CASE WHEN entry_type='USE' THEN days ELSE 0 END),0) AS used,
            -COALESCE(SUM(CASE WHEN entry_type IN ('RESERVE','RELEASE') THEN days ELSE 0 END),0) AS reserved,
            COALESCE(SUM(days),0) AS available FROM leave_ledger WHERE user_id=:user AND leave_year=:year
            """,map("user",access.actor(),"year",y),ErrorCode.USER_NOT_FOUND);
        result.put("year",y);return result;
    }
    public List<Map<String,Object>> ledger(Integer requestedYear,String type) {
        if(type!=null && !type.isBlank() && !Set.of("GRANT","USE","RESERVE","RELEASE","EXPIRE","ADJUST").contains(type)) throw BodyValues.invalid();
        // Calculate balance before filtering: a USE-only view must still include earlier GRANTs in its balance.
        var args=map("user",access.actor(),"year",year(requestedYear));String filter="";
        if(type!=null && !type.isBlank()) {args.put("type",type);filter=" WHERE l.entry_type=:type";}
        return db.rows("""
            SELECT l.*,d.doc_no,d.status FROM (
              SELECT x.*,SUM(x.days) OVER(ORDER BY x.created_at,x.id ROWS UNBOUNDED PRECEDING) AS balance
              FROM leave_ledger x WHERE x.user_id=:user AND x.leave_year=:year
            ) l LEFT JOIN approval_documents d ON d.id=l.document_id
            """+filter+" ORDER BY l.created_at DESC,l.id DESC",args).stream().map(l->map("date",((LocalDateTime)l.get("created_at")).toLocalDate(),"type",l.get("entry_type"),"days",l.get("days"),"balance",l.get("balance"),"memo",l.get("memo"),"document",l.get("document_id")==null?null:map("id",l.get("document_id"),"docNo",l.get("doc_no"),"status",l.get("status")))).toList();
    }
    public Map<String,Object> preview(JsonNode body) {
        String type=BodyValues.text(body,"leaveType",20);
        if(!Set.of("ANNUAL","HALF_AM","HALF_PM","SICK","FAMILY").contains(type)) throw BodyValues.invalid();
        LocalDate start=BodyValues.date(body,"startDate"),end=BodyValues.date(body,"endDate");
        BigDecimal available=handler.available(access.actor(),start.getYear()),days=BigDecimal.ZERO;long offDays=0;String error=null;
        try {
            days=handler.days(type,start,end);
            offDays=ChronoUnit.DAYS.between(start,end)+1-workingDays.count(start,end);
            if(days.signum()>0 && days.compareTo(available)>0) error=ErrorCode.LEAVE_BALANCE_EXCEEDED.getMessage();
        } catch(BusinessException ex) {
            if(ex.getErrorCode()==ErrorCode.INVALID_INPUT_VALUE) throw ex;
            error=ex.getMessage();
            if(ex.getErrorCode()==ErrorCode.LEAVE_NO_WORKING_DAY) offDays=ChronoUnit.DAYS.between(start,end)+1;
        }
        return map("days",days,"offDays",offDays,"available",available,"after",available.subtract(days),"error",error);
    }
}
