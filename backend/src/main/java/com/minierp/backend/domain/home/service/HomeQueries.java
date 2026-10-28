package com.minierp.backend.domain.home.service;
/** Shared SQL text lets MariaDB ANALYZE tests measure the exact production queries. */
public final class HomeQueries {
 private HomeQueries() {}
 public static final String MY_FROM="""
   FROM task_assignments a JOIN tasks t ON t.id=a.task_id JOIN projects p ON p.id=t.project_id
   LEFT JOIN users u ON u.id=p.leader_id
   WHERE a.user_id=:actor AND t.task_status<>'DONE' AND
   """;
 public static String myItems(String scope){return "SELECT t.id,t.project_id,t.task_title,t.task_status,t.end_date,t.task_no,p.project_key,COUNT(*) OVER() AS matching_count "+MY_FROM+scope+" ORDER BY t.end_date,t.id LIMIT 5";}
 public static String myProject(String scope){return "SELECT p.id "+MY_FROM+scope+" GROUP BY p.id ORDER BY COUNT(*) DESC,COALESCE(p.updated_at,p.created_at) DESC,p.id DESC LIMIT 1";}
 public static String mySummary(String scope){return """
   SELECT p.id,p.project_key,p.title,COUNT(t.id) AS task_count,COALESCE(SUM(t.task_status='DONE'),0) AS done_count,
     COALESCE(SUM(t.task_status<>'DONE' AND t.end_date<:today),0) AS overdue_count
   FROM (
   """+myProject(scope)+"""
   ) chosen STRAIGHT_JOIN projects p ON p.id=chosen.id LEFT JOIN tasks t ON t.project_id=p.id GROUP BY p.id
   """;}
 public static String teamWeek(boolean leave,boolean tasks,String scope){
  String leaves="""
   SELECT l.start_date,l.end_date,l.leave_type AS type,d.status,d.id AS document_id,person.id AS user_id,person.user_name,
     NULL AS task_id,NULL AS task_no,NULL AS task_title,NULL AS project_id,NULL AS project_key
   FROM users person STRAIGHT_JOIN approval_documents d ON d.drafter_id=person.id STRAIGHT_JOIN leave_requests l ON l.document_id=d.id
   WHERE person.department_id=:department AND d.status IN ('APPROVED','IN_PROGRESS') AND l.start_date<=:to AND l.end_date>=:from
   """;
  String due="""
   SELECT DISTINCT t.end_date AS start_date,t.end_date,'TASK_DUE' AS type,NULL AS status,NULL AS document_id,NULL AS user_id,NULL AS user_name,
     t.id AS task_id,t.task_no,t.task_title,p.id AS project_id,p.project_key
   FROM users person STRAIGHT_JOIN task_assignments a ON a.user_id=person.id STRAIGHT_JOIN tasks t ON t.id=a.task_id
   STRAIGHT_JOIN projects p ON p.id=t.project_id LEFT JOIN users u ON u.id=p.leader_id
   WHERE person.department_id=:department AND t.task_status<>'DONE' AND t.end_date BETWEEN :from AND :to AND
   """+scope;
  return leave&&tasks?leaves+" UNION ALL "+due:leave?leaves:due;
 }
 public static final String TODO="d.status='IN_PROGRESS' AND EXISTS(SELECT 1 FROM approval_lines l WHERE l.document_id=d.id AND l.approver_id=:actor AND l.line_kind='APPROVER' AND l.status='PENDING')";
 public static final String TODO_ROWS="""
   SELECT d.id,d.doc_no,d.title,d.submitted_at,f.code,u.id AS drafter_id,u.user_name,pos.name AS user_title
   FROM approval_documents d JOIN approval_forms f ON f.id=d.form_id JOIN users u ON u.id=d.drafter_id
   LEFT JOIN positions pos ON pos.id=u.position_id WHERE
   """+TODO+" ORDER BY d.submitted_at DESC,d.id DESC LIMIT 3";
 public static final String MINE="""
   SELECT d.id,d.doc_no,d.title,d.status,d.submitted_at,f.code,l.approver_id,l.approver_name,l.approver_title
   FROM approval_documents d JOIN approval_forms f ON f.id=d.form_id
   LEFT JOIN approval_lines l ON l.id=(SELECT x.id FROM approval_lines x WHERE x.document_id=d.id AND x.line_kind='APPROVER' AND x.status='PENDING' ORDER BY x.step_order LIMIT 1)
   WHERE d.drafter_id=:actor AND d.status='IN_PROGRESS' ORDER BY d.submitted_at DESC,d.id DESC LIMIT 3
   """;
 public static final String NOTIFICATIONS="""
   SELECT c.unread,n.* FROM (SELECT COUNT(*) AS unread FROM notifications WHERE user_id=:actor AND read_at IS NULL) c
   LEFT JOIN (SELECT id,type,title,link,read_at,created_at FROM notifications WHERE user_id=:actor ORDER BY id DESC LIMIT 5) n ON 1=1 ORDER BY n.id DESC
   """;
}
