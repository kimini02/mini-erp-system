package com.minierp.backend.domain.attendance.service;
import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;
@Repository @RequiredArgsConstructor
public class AttendanceStore {
    private final SqlStore db;
    public record Schedule(LocalTime start,LocalTime end,int grace,String source,String department) {
        public Map<String,Object> view() {var f=DateTimeFormatter.ofPattern("HH:mm");return map("startTime",start.format(f),"endTime",end.format(f),"lateGraceMin",grace,"halfBoundary",start.plusHours(5).format(f),"source",source,"departmentName",department);}
    }
    public record Day(String type,String holiday,Map<String,Object> leave) {}
    public static LocalTime time(Object value) {return value instanceof java.sql.Time t?t.toLocalTime():value instanceof LocalTime t?t:LocalTime.parse(value.toString());}
    public Map<String,Object> lockUser(long id) {return db.one("SELECT id,status,hire_date,retired_date,created_at FROM users WHERE id=:id FOR UPDATE",map("id",id),ErrorCode.USER_NOT_FOUND);}
    public Schedule schedule(long user) {
        var s=db.one("""
          SELECT w.start_time,w.end_time,w.late_grace_min,d.name,IF(d.work_schedule_id IS NULL,'COMPANY','DEPARTMENT') AS source
          FROM users u JOIN departments d ON d.id=u.department_id JOIN work_schedules w ON
          (d.work_schedule_id=w.id OR (d.work_schedule_id IS NULL AND w.is_default=1)) WHERE u.id=:user
          """,map("user",user),ErrorCode.USER_NOT_FOUND);
        return new Schedule(time(s.get("start_time")),time(s.get("end_time")),((Number)s.get("late_grace_min")).intValue(),(String)s.get("source"),(String)s.get("name"));
    }
    public Schedule snapshot(Map<String,Object> row) {return new Schedule(time(row.get("sched_start")),time(row.get("sched_end")),((Number)row.get("sched_grace_min")).intValue(),(String)row.get("sched_source"),(String)row.get("sched_department_name"));}
    public Day day(long user,LocalDate date) {
        var holidays=db.rows("SELECT name FROM holidays WHERE holiday_date=:date",map("date",date));
        String holiday=holidays.isEmpty()?null:(String)holidays.get(0).get("name");
        boolean off=holiday!=null || date.getDayOfWeek()==DayOfWeek.SATURDAY || date.getDayOfWeek()==DayOfWeek.SUNDAY;
        var leaves=db.rows("""
          SELECT l.leave_type,d.id,d.doc_no FROM leave_requests l JOIN approval_documents d ON d.id=l.document_id
          WHERE d.drafter_id=:user AND d.status='APPROVED' AND l.start_date<=:date AND l.end_date>=:date ORDER BY d.id LIMIT 1
          """,map("user",user,"date",date));
        Map<String,Object> leave=leaves.isEmpty()?null:map("type",leaves.get(0).get("leave_type"),"documentId",leaves.get(0).get("id"),"docNo",leaves.get(0).get("doc_no"));
        String type=off?"HOLIDAY":leave==null?"WORKDAY":leave.get("type").toString().startsWith("HALF_")?leave.get("type").toString():"LEAVE";
        return new Day(type,holiday,leave);
    }
    public Map<String,Object> record(long user,LocalDate date,boolean lock) {
        var rows=db.rows("SELECT a.*,d.doc_no AS fix_doc_no FROM attendances a LEFT JOIN approval_documents d ON d.id=a.fix_document_id WHERE a.user_id=:user AND a.work_date=:date"+(lock?" FOR UPDATE":""),map("user",user,"date",date));return rows.isEmpty()?null:rows.get(0);
    }
    public void insert(long user,LocalDate date,Schedule schedule,String day,LocalDateTime in,LocalDateTime out,boolean finalized,LocalDateTime now) {
        var judged=AttendanceRules.judge(date,day,schedule.start(),schedule.end(),schedule.grace(),in,out,finalized);
        db.update("""
          INSERT INTO attendances(user_id,work_date,clock_in_at,clock_out_at,sched_start,sched_end,sched_grace_min,sched_source,sched_department_name,day_type,in_result,out_result,absent,judged_at,created_at,updated_at)
          VALUES(:user,:date,:in,:out,:start,:end,:grace,:source,:dept,:day,:inResult,:outResult,:absent,:now,:now,:now)
          """,map("user",user,"date",date,"in",in,"out",out,"start",schedule.start(),"end",schedule.end(),"grace",schedule.grace(),"source",schedule.source(),"dept",schedule.department(),"day",day,"inResult",judged.inResult(),"outResult",judged.outResult(),"absent",judged.absent(),"now",now));
    }
    public void judge(Map<String,Object> row,String day,LocalDateTime in,LocalDateTime out,boolean finalized,LocalDateTime now) {
        var s=snapshot(row);var judged=AttendanceRules.judge((LocalDate)row.get("work_date"),day,s.start(),s.end(),s.grace(),in,out,finalized);
        // A repeated batch changes neither business values nor audit times when the verdict is identical.
        db.update("""
          UPDATE attendances SET clock_in_at=:in,clock_out_at=:out,day_type=:day,in_result=:inResult,out_result=:outResult,absent=:absent,judged_at=:now,updated_at=:now
          WHERE id=:id AND NOT(clock_in_at<=>:in AND clock_out_at<=>:out AND day_type<=>:day AND in_result<=>:inResult AND out_result<=>:outResult AND absent<=>:absent)
          """,map("id",row.get("id"),"in",in,"out",out,"day",day,"inResult",judged.inResult(),"outResult",judged.outResult(),"absent",row.get("fix_document_id")!=null && (in!=null || out!=null)?false:judged.absent(),"now",now));
    }
}
