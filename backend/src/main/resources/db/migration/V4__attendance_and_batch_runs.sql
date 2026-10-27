-- M3: keep user/date uniqueness; preserve timestamps as Korean local DATETIME values.
ALTER TABLE attendances
 ADD clock_in_at DATETIME(6) NULL, ADD clock_out_at DATETIME(6) NULL,
 ADD sched_start TIME NOT NULL DEFAULT '09:00:00', ADD sched_end TIME NOT NULL DEFAULT '18:00:00',
 ADD sched_grace_min INT NOT NULL DEFAULT 0,
 ADD sched_source VARCHAR(20) NOT NULL DEFAULT 'COMPANY',
 ADD sched_department_name VARCHAR(100) NOT NULL DEFAULT '',
 ADD day_type VARCHAR(20) NOT NULL DEFAULT 'WORKDAY',
 ADD in_result VARCHAR(20) NOT NULL DEFAULT 'NONE', ADD out_result VARCHAR(20) NULL,
 ADD absent BIT NOT NULL DEFAULT 0, ADD judged_at DATETIME(6) NULL,
 ADD fix_document_id BIGINT NULL,
 -- Resolve the correction document linked from a day detail.
 ADD INDEX idx_attendance_fix_document(fix_document_id),
 ADD CONSTRAINT fk_attendance_fix_document FOREIGN KEY(fix_document_id) REFERENCES approval_documents(id);
SET @m3_now=UTC_TIMESTAMP(6)+INTERVAL 9 HOUR;
UPDATE attendances a JOIN users u ON u.id=a.user_id JOIN departments d ON d.id=u.department_id
 JOIN work_schedules w ON w.is_default=1
 SET a.clock_in_at=IF(a.clock_in_time IS NULL,NULL,TIMESTAMP(a.work_date,a.clock_in_time)),
 a.clock_out_at=IF(a.clock_out_time IS NULL,NULL,TIMESTAMP(a.work_date,a.clock_out_time)),
 a.sched_start=w.start_time,a.sched_end=w.end_time,a.sched_grace_min=w.late_grace_min,
 a.sched_department_name=d.name,a.absent=(a.att_status='ABSENT'),
 a.day_type=CASE WHEN a.att_status='LEAVE' THEN 'LEAVE'
 WHEN DAYOFWEEK(a.work_date) IN (1,7) OR EXISTS(SELECT 1 FROM holidays h WHERE h.holiday_date=a.work_date) THEN 'HOLIDAY' ELSE 'WORKDAY' END;
UPDATE attendances SET
 in_result=CASE WHEN day_type IN ('HOLIDAY','LEAVE') OR clock_in_at IS NULL THEN 'NONE'
 WHEN DATE_FORMAT(clock_in_at,'%H:%i') <= DATE_FORMAT(ADDTIME(sched_start,SEC_TO_TIME(sched_grace_min*60)),'%H:%i') THEN 'ON_TIME' ELSE 'LATE' END,
 out_result=CASE WHEN day_type IN ('HOLIDAY','LEAVE') THEN 'NONE'
 WHEN clock_in_at IS NULL THEN 'NONE'
 WHEN clock_out_at IS NULL THEN IF(work_date<DATE(@m3_now),'MISSING',NULL)
 WHEN TIME(clock_out_at)>=sched_end THEN 'ON_TIME' ELSE 'EARLY' END,
 judged_at=@m3_now;
SELECT CONCAT('M3: migrated_attendances=',COUNT(*)) AS migration_summary FROM attendances;
ALTER TABLE attendances DROP clock_in_time,DROP clock_out_time,DROP att_status;

CREATE TABLE batch_runs (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 job_name VARCHAR(50) NOT NULL,target_date DATE NOT NULL,status VARCHAR(20) NOT NULL,
 processed_count INT NOT NULL,skipped_count INT NOT NULL,message TEXT NULL,
 started_at DATETIME(6) NOT NULL,finished_at DATETIME(6) NOT NULL,
 -- Recent executions for a batch job, including repeated/manual runs.
 INDEX idx_batch_runs_job(job_name,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO approval_forms(code,name,doc_prefix,allow_line_edit,skip_self,dedupe_approver,fallback_to_upper)
 VALUES('ATTENDANCE_FIX','근태 정정','AT',0,1,1,1);
INSERT INTO approval_form_steps(form_id,step_order,line_kind,approver_type)
 SELECT id,1,'APPROVER','DEPT_HEAD' FROM approval_forms WHERE code='ATTENDANCE_FIX';
CREATE TABLE attendance_fix_requests (
 document_id BIGINT NOT NULL PRIMARY KEY,work_date DATE NOT NULL,clock_in TIME NULL,clock_out TIME NULL,
 reason VARCHAR(500) NOT NULL,before_json TEXT NULL,
 -- Find correction requests for a date; document joins supply drafter and live status.
 INDEX idx_attendance_fix_date(work_date,document_id),
 CONSTRAINT fk_attendance_fix_request_document FOREIGN KEY(document_id) REFERENCES approval_documents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
