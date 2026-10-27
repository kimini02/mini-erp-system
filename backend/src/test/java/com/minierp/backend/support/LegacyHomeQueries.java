package com.minierp.backend.support;
/** Initial M4 home query, measured before scoped join ordering was introduced. */
public final class LegacyHomeQueries {
 private LegacyHomeQueries(){}
 public static String teamWeek(boolean leave,boolean tasks,String scope){
  String leaves="""
   SELECT l.start_date,l.end_date,l.leave_type AS type,d.status,d.id AS document_id,person.id AS user_id,person.user_name,
     NULL AS task_id,NULL AS task_no,NULL AS task_title,NULL AS project_id,NULL AS project_key
   FROM users person JOIN approval_documents d ON d.drafter_id=person.id JOIN leave_requests l ON l.document_id=d.id
   WHERE person.department_id=:department AND d.status IN ('APPROVED','IN_PROGRESS') AND l.start_date<=:to AND l.end_date>=:from
   """;
  String due="""
   SELECT DISTINCT t.end_date AS start_date,t.end_date,'TASK_DUE' AS type,NULL AS status,NULL AS document_id,NULL AS user_id,NULL AS user_name,
     t.id AS task_id,t.task_no,t.task_title,p.id AS project_id,p.project_key
   FROM users person JOIN task_assignments a ON a.user_id=person.id JOIN tasks t ON t.id=a.task_id
   JOIN projects p ON p.id=t.project_id LEFT JOIN users u ON u.id=p.leader_id
   WHERE person.department_id=:department AND t.task_status<>'DONE' AND t.end_date BETWEEN :from AND :to AND
   """+scope;
  return leave&&tasks?leaves+" UNION ALL "+due:leave?leaves:due;
 }
}
