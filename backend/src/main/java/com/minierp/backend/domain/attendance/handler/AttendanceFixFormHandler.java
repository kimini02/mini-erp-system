package com.minierp.backend.domain.attendance.handler;
import com.fasterxml.jackson.databind.*;
import com.minierp.backend.domain.approval.handler.*;
import com.minierp.backend.domain.approval.model.*;
import com.minierp.backend.domain.attendance.service.*;
import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;
@Component @RequiredArgsConstructor
public class AttendanceFixFormHandler implements ApprovalFormHandler {
    private final SqlStore db;private final AttendanceStore records;private final Clock clock;private final ObjectMapper mapper;
    public String formCode() {return "ATTENDANCE_FIX";}
    private LocalTime optionalTime(JsonNode body,String field) {
        if(!body.hasNonNull(field))return null;String v=BodyValues.text(body,field,5);
        if(!v.matches("[0-9]{2}:[0-9]{2}"))throw BodyValues.invalid();try{return LocalTime.parse(v);}catch(DateTimeException ex){throw BodyValues.invalid();}
    }
    public void checkFormat(JsonNode b) {
        BodyValues.date(b,"workDate");BodyValues.text(b,"reason",500);var in=optionalTime(b,"clockIn");var out=optionalTime(b,"clockOut");
        if(in==null && out==null)throw BodyValues.invalid();if(in!=null && out!=null && !out.isAfter(in))throw new BusinessException(ErrorCode.INVALID_ATTENDANCE_TIME);
    }
    public void saveBody(ApprovalDocument d,JsonNode b) {
        checkFormat(b);db.update("""
          INSERT INTO attendance_fix_requests(document_id,work_date,clock_in,clock_out,reason) VALUES(:id,:date,:in,:out,:reason)
          ON DUPLICATE KEY UPDATE work_date=VALUES(work_date),clock_in=VALUES(clock_in),clock_out=VALUES(clock_out),reason=VALUES(reason),before_json=NULL
          """,map("id",d.id(),"date",BodyValues.date(b,"workDate"),"in",optionalTime(b,"clockIn"),"out",optionalTime(b,"clockOut"),"reason",b.path("reason").textValue()));
    }
    private Map<String,Object> raw(long id) {return db.one("SELECT * FROM attendance_fix_requests WHERE document_id=:id",map("id",id),ErrorCode.DOCUMENT_NOT_FOUND);}
    private Object json(String text) {try{return text==null?null:mapper.readTree(text);}catch(com.fasterxml.jackson.core.JsonProcessingException ex){throw new IllegalStateException("Invalid stored attendance snapshot",ex);}}
    private String hh(Object value) {return value==null?null:AttendanceStore.time(value).format(DateTimeFormatter.ofPattern("HH:mm"));}
    public Map<String,Object> body(long id) {var r=raw(id);return map("workDate",r.get("work_date"),"clockIn",hh(r.get("clock_in")),"clockOut",hh(r.get("clock_out")),"reason",r.get("reason"),"before",json((String)r.get("before_json")));}
    public void copyBody(long id,ApprovalDocument target) {db.update("INSERT INTO attendance_fix_requests(document_id,work_date,clock_in,clock_out,reason) SELECT :target,work_date,clock_in,clock_out,reason FROM attendance_fix_requests WHERE document_id=:id",map("id",id,"target",target.id()));}
    public void deleteBody(long id) {db.update("DELETE FROM attendance_fix_requests WHERE document_id=:id",map("id",id));}
    public void validate(ApprovalDraft draft) {
        var d=draft.document();var r=raw(d.id());var date=(LocalDate)r.get("work_date");var today=LocalDate.now(clock);
        if(!date.isBefore(today) || date.isBefore(today.minusDays(30)))throw new BusinessException(ErrorCode.ATTENDANCE_FIX_OUT_OF_RANGE);
        var old=records.record(d.drafterId(),date,false);
        if(records.day(d.drafterId(),date).type().equals("HOLIDAY") && old==null)throw BodyValues.invalid();
        mergedTimes(r,old,date);
        if(db.count("SELECT COUNT(*) FROM attendance_fix_requests f JOIN approval_documents d ON d.id=f.document_id WHERE d.drafter_id=:user AND d.status='IN_PROGRESS' AND f.work_date=:date AND d.id<>:id",map("user",d.drafterId(),"date",date,"id",d.id()))>0)throw new BusinessException(ErrorCode.ATTENDANCE_FIX_DUPLICATE);
    }
    public void onSubmitted(ApprovalDocument d) {
        var r=raw(d.id());var date=(LocalDate)r.get("work_date");var old=records.record(d.drafterId(),date,false);var day=records.day(d.drafterId(),date);
        var in=old==null?null:(LocalDateTime)old.get("clock_in_at");var out=old==null?null:(LocalDateTime)old.get("clock_out_at");
        var verdict=old==null?new AttendanceRules.Result(null,null,false):new AttendanceRules.Result((String)old.get("in_result"),(String)old.get("out_result"),bool(old,"absent"));
        String code=AttendanceRules.code(date,LocalDate.now(clock),old==null?day.type():(String)old.get("day_type"),verdict,in);
        try {db.update("UPDATE attendance_fix_requests SET before_json=:json WHERE document_id=:id",map("id",d.id(),"json",mapper.writeValueAsString(map("clockIn",in==null?null:hh(in.toLocalTime()),"clockOut",out==null?null:hh(out.toLocalTime()),"code",code))));}
        catch(com.fasterxml.jackson.core.JsonProcessingException ex){throw new IllegalStateException(ex);}
    }
    private record Times(LocalDateTime in,LocalDateTime out) {}
    private Times mergedTimes(Map<String,Object> correction,Map<String,Object> old,LocalDate date) {
        LocalDateTime in=correction.get("clock_in")==null?(old==null?null:(LocalDateTime)old.get("clock_in_at")):date.atTime(AttendanceStore.time(correction.get("clock_in")));
        LocalDateTime out=correction.get("clock_out")==null?(old==null?null:(LocalDateTime)old.get("clock_out_at")):date.atTime(AttendanceStore.time(correction.get("clock_out")));
        if(in==null)throw new BusinessException(ErrorCode.ATTENDANCE_FIX_CLOCK_IN_REQUIRED);
        if(out!=null && !out.isAfter(in))throw new BusinessException(ErrorCode.INVALID_ATTENDANCE_TIME);
        return new Times(in,out);
    }
    public void onApproved(ApprovalDocument d) {
        var r=raw(d.id());var date=(LocalDate)r.get("work_date");var old=records.record(d.drafterId(),date,true);var day=records.day(d.drafterId(),date);var now=LocalDateTime.now(clock);
        var times=mergedTimes(r,old,date);var in=times.in();var out=times.out();
        if(old==null)records.insert(d.drafterId(),date,records.schedule(d.drafterId()),day.type(),in,out,true,now);
        else records.judge(old,day.type(),in,out,true,now);
        db.update("UPDATE attendances SET absent=0,fix_document_id=:doc,judged_at=:now,updated_at=:now WHERE user_id=:user AND work_date=:date",map("doc",d.id(),"user",d.drafterId(),"date",date,"now",now));
    }
    public void onRejected(ApprovalDocument d) {}
    public void onWithdrawn(ApprovalDocument d) {}
}
