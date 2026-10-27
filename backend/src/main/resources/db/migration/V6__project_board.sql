-- M4: 프로젝트 키, 프로젝트별 업무 번호와 보드 순서. 기존 V1~V5는 보존한다.
ALTER TABLE projects ADD project_key VARCHAR(10) NULL, ADD last_task_seq INT NOT NULL DEFAULT 0;
UPDATE projects SET project_key=CONCAT('P',id);
ALTER TABLE projects MODIFY project_key VARCHAR(10) NOT NULL,
  -- 등록 시 키 중복 검사와 프로젝트 키 식별에 사용한다.
  ADD CONSTRAINT uk_projects_key UNIQUE(project_key);
ALTER TABLE tasks ADD task_no INT NULL, ADD position BIGINT NOT NULL DEFAULT 0,
  ADD version INT NOT NULL DEFAULT 0, ADD created_by BIGINT NULL,
  -- 업무 작성자 참조에 사용한다.
  ADD INDEX idx_tasks_created_by(created_by),
  ADD CONSTRAINT fk_tasks_created_by FOREIGN KEY(created_by) REFERENCES users(id);
UPDATE tasks t JOIN (
  SELECT id,ROW_NUMBER() OVER(PARTITION BY project_id ORDER BY id) AS seq,
    ROW_NUMBER() OVER(PARTITION BY project_id,task_status ORDER BY id)*1024 AS pos FROM tasks
) ranked ON ranked.id=t.id SET t.task_no=ranked.seq,t.position=ranked.pos;
UPDATE projects p LEFT JOIN (SELECT project_id,MAX(task_no) AS seq FROM tasks GROUP BY project_id) t ON t.project_id=p.id
SET p.last_task_seq=COALESCE(t.seq,0);
ALTER TABLE tasks MODIFY task_no INT NOT NULL,
  -- 같은 프로젝트 안의 업무 번호 중복을 방지하고 번호로 찾을 때 사용한다.
  ADD CONSTRAINT uk_tasks_project_no UNIQUE(project_id,task_no),
  -- 보드 열의 순서 조회와 이동할 카드 위·아래 위치 탐색에 사용한다.
  ADD INDEX idx_tasks_board_position(project_id,task_status,position);
-- v1에서는 팀장과 멤버가 별개였으므로 기존 팀장도 보드 멤버에 포함한다.
INSERT INTO project_members(project_id,user_id,created_at)
SELECT p.id,p.leader_id,p.created_at FROM projects p WHERE p.leader_id IS NOT NULL
AND NOT EXISTS(SELECT 1 FROM project_members m WHERE m.project_id=p.id AND m.user_id=p.leader_id);
SELECT CONCAT('M4: migrated_leader_members=',ROW_COUNT()) AS migration_summary;
CREATE TABLE task_histories (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,task_id BIGINT NOT NULL,actor_id BIGINT NULL,
 action VARCHAR(30) NOT NULL,from_value VARCHAR(1000) NULL,to_value VARCHAR(1000) NULL,created_at DATETIME(6) NOT NULL,
 -- 업무 상세의 최근 50개 이력을 역순으로 읽는다.
 INDEX idx_task_histories_task_id(task_id,id),
 -- 이력 작성자 참조에 사용한다.
 INDEX idx_task_histories_actor(actor_id),
 CONSTRAINT fk_task_histories_task FOREIGN KEY(task_id) REFERENCES tasks(id),
 CONSTRAINT fk_task_histories_actor FOREIGN KEY(actor_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE task_due_alerts (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,task_id BIGINT NOT NULL,user_id BIGINT NOT NULL,
 due_date DATE NOT NULL,created_at DATETIME(6) NOT NULL,
 -- 같은 업무·담당자·마감일의 알림 중복을 막는다(실행 배치는 M4 2차).
 CONSTRAINT uk_task_due_alert UNIQUE(task_id,user_id,due_date),
 -- 알림 대상 사원 참조에 사용한다.
 INDEX idx_task_due_alert_user(user_id),
 CONSTRAINT fk_task_due_alert_task FOREIGN KEY(task_id) REFERENCES tasks(id),
 CONSTRAINT fk_task_due_alert_user FOREIGN KEY(user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- 업무 배정 알림에는 결재 문서가 없다. 기존 문서 FK와 결재 알림 데이터는 유지한다.
ALTER TABLE notifications MODIFY document_id BIGINT NULL;
SELECT CONCAT('M4: migrated_projects=',COUNT(*)) AS migration_summary FROM projects;
SELECT CONCAT('M4: migrated_tasks=',COUNT(*)) AS migration_summary FROM tasks;
