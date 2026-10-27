package com.minierp.backend.domain.project.service;

/** Select the authorized page first, then aggregate only its project ids. */
public final class ProjectSummaryQuery {
 private ProjectSummaryQuery() {}
 public static final String ROWS="SELECT p.*,u.user_name AS leader_name,pos.name AS leader_title FROM projects p LEFT JOIN users u ON u.id=p.leader_id LEFT JOIN positions pos ON pos.id=u.position_id";
 public static String sql(String where,boolean page) {
  String selected=ROWS+" WHERE "+where+(page?" ORDER BY COALESCE(p.updated_at,p.created_at) DESC,p.id DESC LIMIT :size OFFSET :offset":"");
  return "WITH selected AS ("+selected+"), "+"""
   task_counts AS (
    SELECT t.project_id,COUNT(*) AS total,SUM(t.task_status='DONE') AS done,
     SUM(t.task_status<>'DONE' AND t.end_date<:today) AS overdue
    FROM selected s STRAIGHT_JOIN tasks t ON t.project_id=s.id GROUP BY t.project_id
   ), member_counts AS (
    SELECT m.project_id,COUNT(*) AS members,SUM(m.user_id=:actor) AS mine
    FROM selected s STRAIGHT_JOIN project_members m ON m.project_id=s.id GROUP BY m.project_id
   )
   SELECT p.*,COALESCE(m.members,0) AS member_count,COALESCE(m.mine,0) AS is_member,
    COALESCE(t.total,0) AS task_count,COALESCE(t.done,0) AS done_count,COALESCE(t.overdue,0) AS overdue_count
   FROM selected p LEFT JOIN task_counts t ON t.project_id=p.id LEFT JOIN member_counts m ON m.project_id=p.id
   ORDER BY COALESCE(p.updated_at,p.created_at) DESC,p.id DESC
   """;
 }
}
