package com.minierp.backend.domain.attendance.service;
import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.exception.*;
import com.minierp.backend.global.permission.PermissionAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;
@Service @RequiredArgsConstructor @Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED)
public class AttendanceService {
    private final AttendanceStore records;private final SqlStore db;private final Clock clock;private final PermissionAccess access;
    public Map<String,Object> today() {return view(access.actor(),LocalDate.now(clock));}
    private Map<String,Object> view(long user,LocalDate date) {
        var row=records.record(user,date,false);var context=records.day(user,date);var schedule=row==null?records.schedule(user):records.snapshot(row);
        var in=row==null?null:(LocalDateTime)row.get("clock_in_at");var out=row==null?null:(LocalDateTime)row.get("clock_out_at");
        String day=row==null?context.type():(String)row.get("day_type");
        var now=LocalDateTime.now(clock);var hypothetical=AttendanceRules.judge(date,day,schedule.start(),schedule.end(),schedule.grace(),in,now,false);
        var month=YearMonth.from(date);
        long late=db.count("SELECT COUNT(*) FROM attendances WHERE user_id=:user AND work_date BETWEEN :begin AND :end AND in_result='LATE'",map("user",user,"begin",month.atDay(1),"end",month.atEndOfMonth()));
        return map("workDate",date,"dayType",day,"holidayName",context.holiday(),"schedule",schedule.view(),"clockInAt",in,"clockOutAt",out,
          "inResult",row==null?null:row.get("in_result"),"outResult",row==null?null:row.get("out_result"),"leave",context.leave(),
          "canClockIn",in==null && !context.type().equals("LEAVE") && date.equals(now.toLocalDate()),"canClockOut",in!=null,
          "earlyIfNow","EARLY".equals(hypothetical.outResult()),"monthLateCount",late);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) public Map<String,Object> clockIn() {
        long user=access.actor();records.lockUser(user);var now=LocalDateTime.now(clock);var date=now.toLocalDate();
        var existing=records.record(user,date,true);
        if(existing!=null && existing.get("clock_in_at")!=null) throw new BusinessException(ErrorCode.ALREADY_CLOCKED_IN);
        var day=records.day(user,date);
        if(day.type().equals("LEAVE")) throw new BusinessException(ErrorCode.ATTENDANCE_ON_LEAVE);
        if(existing==null) records.insert(user,date,records.schedule(user),day.type(),now,null,false,now);
        else records.judge(existing,day.type(),now,null,false,now);
        return view(user,date);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) public Map<String,Object> clockOut() {
        long user=access.actor();records.lockUser(user);var now=LocalDateTime.now(clock);var date=now.toLocalDate();var row=records.record(user,date,true);
        if(row==null || row.get("clock_in_at")==null) {
            date=date.minusDays(1);row=records.record(user,date,true);
            if(row==null || row.get("clock_in_at")==null || (row.get("clock_out_at")!=null && !((LocalDateTime)row.get("clock_out_at")).toLocalDate().equals(now.toLocalDate()))) throw new BusinessException(ErrorCode.NOT_CLOCKED_IN);
        }
        records.judge(row,(String)row.get("day_type"),(LocalDateTime)row.get("clock_in_at"),now,false,now);
        return view(user,date);
    }
    public static YearMonth month(String text) {
        try {if(text==null || !text.matches("\\d{4}-\\d{2}")) throw new IllegalArgumentException();return YearMonth.parse(text);}
        catch(java.time.DateTimeException | IllegalArgumentException e) {throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);}
    }
    public Map<String,Object> me(String text) {
        var month=month(text);long user=access.actor();var today=LocalDate.now(clock);
        Map<LocalDate,Map<String,Object>> found=new HashMap<>();db.rows("SELECT a.*,d.doc_no AS fix_doc_no FROM attendances a LEFT JOIN approval_documents d ON d.id=a.fix_document_id WHERE a.user_id=:user AND a.work_date BETWEEN :start AND :end",map("user",user,"start",month.atDay(1),"end",month.atEndOfMonth())).forEach(r->found.put((LocalDate)r.get("work_date"),r));
        List<Map<String,Object>> days=new ArrayList<>();int work=0,present=0,late=0,early=0,absent=0,missing=0;double leave=0;
        for(LocalDate date=month.atDay(1);!date.isAfter(month.atEndOfMonth());date=date.plusDays(1)) {
            var row=found.get(date);var context=records.day(user,date);String type=row==null?context.type():(String)row.get("day_type");
            if(!type.equals("HOLIDAY")) work++;
            if(type.equals("LEAVE")) leave++;else if(type.startsWith("HALF_")) leave+=.5;
            if(row==null) {days.add(map("date",date,"dayType",type));continue;}
            if(row.get("clock_in_at")!=null) present++;if("LATE".equals(row.get("in_result"))) late++;if("EARLY".equals(row.get("out_result"))) early++;if(bool(row,"absent")) absent++;if("MISSING".equals(row.get("out_result"))) missing++;
            days.add(map("date",date,"dayType",type,"clockInAt",row.get("clock_in_at"),"clockOutAt",row.get("clock_out_at"),"inResult",row.get("in_result"),"outResult",row.get("out_result"),"absent",bool(row,"absent"),"fixDocument",row.get("fix_document_id")==null?null:map("id",row.get("fix_document_id"),"docNo",row.get("fix_doc_no"))));
        }
        return map("month",month.toString(),"summary",map("workDays",work,"present",present,"late",late,"early",early,"absent",absent,"missing",missing,"leaveDays",leave),"days",days);
    }
}
