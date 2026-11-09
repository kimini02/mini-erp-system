package com.minierp.backend.support;

/** Unchanged phase-one query, retained only as a measurement comparator. */
public final class LegacyProjectSummary {
    public static final String SELECT = """
 SELECT p.*,u.user_name AS leader_name,pos.name AS leader_title,
 COALESCE(m.members,0) AS member_count,COALESCE(m.mine,0) AS is_member,COALESCE(t.total,0) AS task_count,
 COALESCE(t.done,0) AS done_count,COALESCE(t.overdue,0) AS overdue_count
 FROM projects p LEFT JOIN users u ON u.id=p.leader_id LEFT JOIN positions pos ON pos.id=u.position_id
 LEFT JOIN (SELECT project_id,COUNT(*) AS members,SUM(user_id=:actor) AS mine FROM project_members GROUP BY project_id) m ON m.project_id=p.id
 LEFT JOIN (SELECT project_id,COUNT(*) AS total,SUM(task_status='DONE') AS done,
 SUM(task_status<>'DONE' AND end_date<:today) AS overdue FROM tasks GROUP BY project_id) t ON t.project_id=p.id
 """;
}
