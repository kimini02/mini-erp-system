package com.minierp.backend.domain.home.service;
import com.minierp.backend.domain.attendance.service.AttendanceService;
import com.minierp.backend.domain.approval.service.FormService;
import com.minierp.backend.domain.leave.service.LeaveQueryService;
import com.minierp.backend.domain.project.service.*;
import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.permission.PermissionAccess;
import com.minierp.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.time.temporal.*;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;
@Service @RequiredArgsConstructor @Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED)
public class HomeService {
 private final SqlStore db;private final Clock clock;private final PermissionAccess access;
 private final AttendanceService attendance;private final LeaveQueryService leave;private final BoardProjectService projects;
 public Map<String,Object> home(){
  long actor=access.actor();var grants=access.grants(actor);
  boolean attendanceView=view(grants,"MY_ATTENDANCE"),approvalView=view(grants,"APPROVAL_INBOX"),projectView=view(grants,"PROJECT");
  LocalDate today=LocalDate.now(clock),from=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),to=from.plusDays(4);
  var args=map("actor",actor,"today",today,"from",from,"to",to);
  String scope=projectView?projects.visibilityPredicate(args):"0=1";
  return map("today",attendanceView?attendance.homeToday():null,"approvals",approvalView?approvals(args):null,
   "leave",leave.balance(null),"teamWeek",attendanceView||projectView?teamWeek(args,attendanceView,projectView,scope):null,
   "myTasks",projectView?myTasks(args,scope,today):null,"notifications",notifications(args));
 }
 private static boolean view(Map<String,PermissionAccess.Grant> grants,String menu){var g=grants.get(menu);return g!=null&&g.actions().contains("V");}
 private Map<String,Object> form(Map<String,Object> r){String code=(String)r.get("code");return map("code",code,"shortName",FormService.shortName(code));}
 private Map<String,Object> approvals(Map<String,Object> args){
  long count=db.count("SELECT COUNT(*) FROM approval_documents d WHERE "+HomeQueries.TODO,args);
  var todo=db.rows(HomeQueries.TODO_ROWS,args).stream().map(r->map("id",r.get("id"),"docNo",r.get("doc_no"),"form",form(r),"title",r.get("title"),"drafter",map("id",r.get("drafter_id"),"name",r.get("user_name"),"title",r.get("user_title")),"submittedAt",r.get("submitted_at"))).toList();
  var mine=db.rows(HomeQueries.MINE,args).stream().map(r->map("id",r.get("id"),"docNo",r.get("doc_no"),"form",form(r),"title",r.get("title"),"status",r.get("status"),"currentApprover",r.get("approver_id")==null?null:map("id",r.get("approver_id"),"name",r.get("approver_name"),"title",r.get("approver_title")),"submittedAt",r.get("submitted_at"))).toList();
  return map("todoCount",count,"todo",todo,"mine",mine);
 }
 private Map<String,Object> teamWeek(Map<String,Object> args,boolean leave,boolean tasks,String scope){
  var department=db.one("SELECT d.id,d.name FROM users u JOIN departments d ON d.id=u.department_id WHERE u.id=:actor",args,ErrorCode.USER_NOT_FOUND);args.put("department",department.get("id"));
  LocalDate from=(LocalDate)args.get("from"),to=(LocalDate)args.get("to");
  Map<LocalDate,List<Map<String,Object>>> days=new LinkedHashMap<>();for(int i=0;i<5;i++)days.put(from.plusDays(i),new ArrayList<>());
  for(var r:db.rows(HomeQueries.teamWeek(leave,tasks,scope),args)){
   Map<String,Object> item;
   if("TASK_DUE".equals(r.get("type")))item=map("type","TASK_DUE","task",map("id",r.get("task_id"),"key",r.get("project_key")+"-"+r.get("task_no"),"title",r.get("task_title")),"project",map("id",r.get("project_id"),"key",r.get("project_key")));
   else item=map("type",r.get("type").toString().startsWith("HALF_")?r.get("type"):"LEAVE","user",map("id",r.get("user_id"),"name",r.get("user_name")),"status",r.get("status"),"documentId",r.get("document_id"));
   LocalDate begin=(LocalDate)r.get("start_date"),end=(LocalDate)r.get("end_date");if(begin.isBefore(from))begin=from;if(end.isAfter(to))end=to;
   for(LocalDate d=begin;!d.isAfter(end);d=d.plusDays(1))days.get(d).add(item);
  }
  return map("department",department,"from",from,"to",to,"days",days.entrySet().stream().map(e->map("date",e.getKey(),"items",e.getValue())).toList());
 }
 private Map<String,Object> myTasks(Map<String,Object> args,String scope,LocalDate today){
  var summaries=db.rows(HomeQueries.mySummary(scope),args);Map<String,Object> project=null;
  if(!summaries.isEmpty()){var p=summaries.get(0);long total=number(p,"task_count"),done=number(p,"done_count");project=map("id",p.get("id"),"key",p.get("project_key"),"title",p.get("title"),"total",total,"done",done,"overdue",p.get("overdue_count"),"progressPct",total==0?0:done*100/total);}
  var rows=db.rows(HomeQueries.myItems(scope),args);
  var items=rows.stream().map(r->map("id",r.get("id"),"projectId",r.get("project_id"),"key",r.get("project_key")+"-"+r.get("task_no"),"title",r.get("task_title"),"status",r.get("task_status"),"endDate",r.get("end_date"),"overdueDays",Math.max(0,ChronoUnit.DAYS.between((LocalDate)r.get("end_date"),today)))).toList();
  return map("project",project,"count",rows.isEmpty()?0:number(rows.get(0),"matching_count"),"items",items);
 }
 private Map<String,Object> notifications(Map<String,Object> args){
  var rows=db.rows(HomeQueries.NOTIFICATIONS,args);
  return map("unread",rows.get(0).get("unread"),"items",rows.stream().filter(r->r.get("id")!=null).map(r->map("id",r.get("id"),"type",r.get("type"),"title",r.get("title"),"link",r.get("link"),"read",r.get("read_at")!=null,"createdAt",r.get("created_at"))).toList());
 }
}
