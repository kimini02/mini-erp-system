-- M2: v1 신청을 공통 결재 문서와 양식 본문으로 한 번에 이관한다.
-- 운영 적용 전 백업 필요. V1/V2는 변경하지 않는다.
CREATE TABLE approval_forms (
 id BIGINT NOT NULL AUTO_INCREMENT, code VARCHAR(30) NOT NULL, name VARCHAR(100) NOT NULL,
 doc_prefix VARCHAR(10) NOT NULL, allow_line_edit BOOLEAN NOT NULL, skip_self BOOLEAN NOT NULL,
 dedupe_approver BOOLEAN NOT NULL, fallback_to_upper BOOLEAN NOT NULL,
 -- 양식 상세와 문서의 양식 참조에 사용한다.
 PRIMARY KEY(id),
 -- 양식 코드로 처리기와 설정을 조회한다.
 UNIQUE KEY uk_approval_forms_code(code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE approval_form_steps (
 id BIGINT NOT NULL AUTO_INCREMENT, form_id BIGINT NOT NULL, step_order INT NOT NULL,
 line_kind VARCHAR(20) NOT NULL, approver_type VARCHAR(30) NOT NULL, role_id BIGINT NULL, user_id BIGINT NULL,
 -- 단계 단건 식별에 사용한다.
 PRIMARY KEY(id),
 -- 양식별 단계 순서 조회 및 중복 순서 방지에 사용한다.
 UNIQUE KEY uk_form_steps_order(form_id,step_order),
 -- 역할 삭제 시 사용 중인 규칙을 확인한다.
 INDEX idx_form_steps_role(role_id),
 -- 지정 사원의 결재 규칙 참조를 확인한다.
 INDEX idx_form_steps_user(user_id),
 FOREIGN KEY(form_id) REFERENCES approval_forms(id),
 FOREIGN KEY(role_id) REFERENCES roles(id), FOREIGN KEY(user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE approval_documents (
 id BIGINT NOT NULL AUTO_INCREMENT, doc_no VARCHAR(50) NULL, form_id BIGINT NOT NULL,
 drafter_id BIGINT NOT NULL, drafter_dept_id BIGINT NOT NULL, title VARCHAR(255) NOT NULL,
 status VARCHAR(20) NOT NULL, current_step INT NULL, resubmitted_from_id BIGINT NULL,
 submitted_at DATETIME(6) NULL, completed_at DATETIME(6) NULL, version BIGINT NOT NULL DEFAULT 0,
 manual_line BOOLEAN NOT NULL DEFAULT 0, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NULL,
 legacy_id BIGINT NULL,
 -- 문서 상세 조회와 상태 전이 잠금에 사용한다.
 PRIMARY KEY(id),
 -- 문서번호 중복 발급을 방지한다. 임시 저장은 NULL이다.
 UNIQUE KEY uk_approval_documents_no(doc_no),
 -- 내 기안·임시 보관함을 상태와 작성 순서로 조회한다.
 INDEX idx_documents_drafter_status(drafter_id,status,id),
 -- 양식별 결재함 필터에 사용한다.
 INDEX idx_documents_form_status(form_id,status,id),
 -- 기안 당시 부서 참조에 사용한다.
 INDEX idx_documents_department(drafter_dept_id),
 -- 재기안 원문서 참조에 사용한다.
 INDEX idx_documents_original(resubmitted_from_id),
 FOREIGN KEY(form_id) REFERENCES approval_forms(id), FOREIGN KEY(drafter_id) REFERENCES users(id),
 FOREIGN KEY(drafter_dept_id) REFERENCES departments(id), FOREIGN KEY(resubmitted_from_id) REFERENCES approval_documents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE approval_lines (
 id BIGINT NOT NULL AUTO_INCREMENT, document_id BIGINT NOT NULL, step_order INT NOT NULL,
 line_kind VARCHAR(20) NOT NULL, approver_id BIGINT NOT NULL, approver_name VARCHAR(50) NOT NULL,
 approver_title VARCHAR(100) NOT NULL, approver_dept VARCHAR(100) NOT NULL,
 source VARCHAR(20) NOT NULL, rule_label VARCHAR(255) NULL, status VARCHAR(20) NULL,
 skip_reason VARCHAR(20) NULL, comment VARCHAR(1000) NULL, acted_at DATETIME(6) NULL,
 -- 결재선 조건부 갱신에 사용한다.
 PRIMARY KEY(id),
 -- 문서별 결재선 순서 조회와 일괄 조회에 사용한다.
 UNIQUE KEY uk_approval_lines_order(document_id,step_order),
 -- 내 차례 결재함에서 사원·PENDING 상태로 문서를 바로 조회한다.
 INDEX idx_approval_lines_turn(approver_id,status,document_id),
 FOREIGN KEY(document_id) REFERENCES approval_documents(id), FOREIGN KEY(approver_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE approval_histories (
 id BIGINT NOT NULL AUTO_INCREMENT, document_id BIGINT NOT NULL, actor_id BIGINT NULL,
 action VARCHAR(30) NOT NULL, comment VARCHAR(1000) NULL, created_at DATETIME(6) NOT NULL,
 -- 이력 행 식별에 사용한다.
 PRIMARY KEY(id),
 -- 문서별 이력을 발생 순서로 조회한다.
 INDEX idx_approval_histories_document(document_id,id),
 -- 이력 행위자 참조에 사용한다.
 INDEX idx_approval_histories_actor(actor_id),
 FOREIGN KEY(document_id) REFERENCES approval_documents(id), FOREIGN KEY(actor_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE doc_no_sequences (
 prefix VARCHAR(10) NOT NULL, doc_year INT NOT NULL, last_seq BIGINT NOT NULL,
 -- 양식 접두어·연도별 발급 행을 잠그고 다음 번호를 발급한다.
 PRIMARY KEY(prefix,doc_year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE general_requests (
 id BIGINT NOT NULL AUTO_INCREMENT, document_id BIGINT NOT NULL, content TEXT NOT NULL, amount BIGINT NULL,
 -- 품의 본문 행 식별에 사용한다.
 PRIMARY KEY(id),
 -- 문서별 품의 본문 단건 조회와 중복 본문 방지에 사용한다.
 UNIQUE KEY uk_general_document(document_id), FOREIGN KEY(document_id) REFERENCES approval_documents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE notifications (
 id BIGINT NOT NULL AUTO_INCREMENT, user_id BIGINT NOT NULL, type VARCHAR(30) NOT NULL,
 title VARCHAR(255) NOT NULL, link VARCHAR(255) NOT NULL, document_id BIGINT NOT NULL,
 read_at DATETIME(6) NULL, created_at DATETIME(6) NOT NULL,
 -- 단건 읽음 처리에 사용한다.
 PRIMARY KEY(id),
 -- 내 알림을 최신순으로 조회한다.
 INDEX idx_notifications_user_id(user_id,id),
 -- 내 미확인 알림 개수와 목록을 조회한다.
 INDEX idx_notifications_unread(user_id,read_at,id),
 -- 알림의 근거 문서 참조에 사용한다.
 INDEX idx_notifications_document(document_id),
 FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(document_id) REFERENCES approval_documents(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE leave_ledger (
 id BIGINT NOT NULL AUTO_INCREMENT, user_id BIGINT NOT NULL, leave_year INT NOT NULL,
 entry_type VARCHAR(20) NOT NULL, days DECIMAL(8,1) NOT NULL, document_id BIGINT NULL,
 grant_key VARCHAR(100) NULL, memo VARCHAR(500) NULL, created_by BIGINT NULL, created_at DATETIME(6) NOT NULL,
 -- 원장 행 식별과 누적 순서에 사용한다.
 PRIMARY KEY(id),
 -- 향후 정기 부여의 중복 실행을 방지한다.
 UNIQUE KEY uk_leave_ledger_grant(grant_key),
 -- 사원·연도별 잔여 합계 및 원장 최신순 조회에 사용한다.
 INDEX idx_leave_ledger_user_year(user_id,leave_year,id),
 -- 문서의 예약·해제 원장 조회에 사용한다.
 INDEX idx_leave_ledger_document(document_id,entry_type),
 -- 원장 작성자 참조에 사용한다.
 INDEX idx_leave_ledger_creator(created_by),
 FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(document_id) REFERENCES approval_documents(id),
 FOREIGN KEY(created_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO approval_forms(id,code,name,doc_prefix,allow_line_edit,skip_self,dedupe_approver,fallback_to_upper)
 VALUES (1,'LEAVE','휴가 신청','HR',0,1,1,1),(2,'OVERTIME','특근 신청','OT',0,1,1,1),(3,'GENERAL','일반 품의','GN',1,1,1,1);
INSERT INTO approval_form_steps(form_id,step_order,line_kind,approver_type) VALUES
 (1,1,'APPROVER','DEPT_HEAD'),(1,2,'APPROVER','UPPER_DEPT_HEAD'),(2,1,'APPROVER','DEPT_HEAD'),
 (3,1,'APPROVER','DEPT_HEAD'),(3,2,'APPROVER','UPPER_DEPT_HEAD');
INSERT INTO approval_form_steps(form_id,step_order,line_kind,approver_type,role_id)
 SELECT 1,3,'REFERENCE','ROLE',id FROM roles WHERE name='인사 담당' ORDER BY id LIMIT 1;

INSERT INTO approval_documents(doc_no,form_id,drafter_id,drafter_dept_id,title,status,current_step,submitted_at,completed_at,created_at,updated_at,legacy_id)
 SELECT CONCAT('HR-',YEAR(r.created_at),'-',LPAD(ROW_NUMBER() OVER(PARTITION BY YEAR(r.created_at) ORDER BY r.created_at,r.id),
 GREATEST(4,CHAR_LENGTH(ROW_NUMBER() OVER(PARTITION BY YEAR(r.created_at) ORDER BY r.created_at,r.id))),'0')),
 1,r.requester_id,u.department_id,'휴가 신청 (v1 이관)',
 CASE r.app_status WHEN 'PENDING' THEN 'IN_PROGRESS' WHEN 'CANCELLED' THEN 'WITHDRAWN' ELSE r.app_status END,
 CASE WHEN r.app_status='PENDING' AND r.approver_id IS NOT NULL THEN 1 ELSE NULL END,
 r.created_at,CASE WHEN r.app_status='PENDING' THEN NULL ELSE COALESCE(r.updated_at,r.created_at) END,r.created_at,r.updated_at,r.id
 FROM leave_requests r JOIN users u ON u.id=r.requester_id ORDER BY r.created_at,r.id;
INSERT INTO approval_lines(document_id,step_order,line_kind,approver_id,approver_name,approver_title,approver_dept,source,rule_label,status,acted_at)
 SELECT d.id,1,'APPROVER',u.id,u.user_name,p.name,dept.name,'RULE','v1 이관',
 CASE r.app_status WHEN 'CANCELLED' THEN 'WAITING' ELSE r.app_status END,
 CASE WHEN r.app_status IN ('APPROVED','REJECTED') THEN COALESCE(r.updated_at,r.created_at) ELSE NULL END
 FROM leave_requests r JOIN approval_documents d ON d.form_id=1 AND d.legacy_id=r.id
 JOIN users u ON u.id=r.approver_id JOIN positions p ON p.id=u.position_id JOIN departments dept ON dept.id=u.department_id;
ALTER TABLE leave_requests ADD COLUMN document_id BIGINT NULL;
UPDATE leave_requests r JOIN approval_documents d ON d.form_id=1 AND d.legacy_id=r.id SET r.document_id=d.id;
ALTER TABLE leave_requests MODIFY document_id BIGINT NOT NULL,
 -- 문서별 본문 단건 조회와 중복 본문 방지에 사용한다.
 ADD UNIQUE KEY uk_leave_requests_document(document_id),
 ADD CONSTRAINT fk_leave_requests_document FOREIGN KEY(document_id) REFERENCES approval_documents(id);

INSERT INTO approval_documents(doc_no,form_id,drafter_id,drafter_dept_id,title,status,current_step,submitted_at,completed_at,created_at,updated_at,legacy_id)
 SELECT CONCAT('OT-',YEAR(r.created_at),'-',LPAD(ROW_NUMBER() OVER(PARTITION BY YEAR(r.created_at) ORDER BY r.created_at,r.id),
 GREATEST(4,CHAR_LENGTH(ROW_NUMBER() OVER(PARTITION BY YEAR(r.created_at) ORDER BY r.created_at,r.id))),'0')),
 2,r.requester_id,u.department_id,'특근 신청 (v1 이관)',
 CASE r.status WHEN 'PENDING' THEN 'IN_PROGRESS' WHEN 'CANCELLED' THEN 'WITHDRAWN' ELSE r.status END,
 CASE WHEN r.status='PENDING' AND r.approver_id IS NOT NULL THEN 1 ELSE NULL END,
 r.created_at,CASE WHEN r.status='PENDING' THEN NULL ELSE COALESCE(r.updated_at,r.created_at) END,r.created_at,r.updated_at,r.id
 FROM overtime_requests r JOIN users u ON u.id=r.requester_id ORDER BY r.created_at,r.id;
INSERT INTO approval_lines(document_id,step_order,line_kind,approver_id,approver_name,approver_title,approver_dept,source,rule_label,status,acted_at)
 SELECT d.id,1,'APPROVER',u.id,u.user_name,p.name,dept.name,'RULE','v1 이관',
 CASE r.status WHEN 'CANCELLED' THEN 'WAITING' ELSE r.status END,
 CASE WHEN r.status IN ('APPROVED','REJECTED') THEN COALESCE(r.updated_at,r.created_at) ELSE NULL END
 FROM overtime_requests r JOIN approval_documents d ON d.form_id=2 AND d.legacy_id=r.id
 JOIN users u ON u.id=r.approver_id JOIN positions p ON p.id=u.position_id JOIN departments dept ON dept.id=u.department_id;
ALTER TABLE overtime_requests ADD COLUMN document_id BIGINT NULL;
UPDATE overtime_requests r JOIN approval_documents d ON d.form_id=2 AND d.legacy_id=r.id SET r.document_id=d.id;
ALTER TABLE overtime_requests MODIFY document_id BIGINT NOT NULL,
 -- 문서별 본문 단건 조회와 중복 본문 방지에 사용한다.
 ADD UNIQUE KEY uk_overtime_requests_document(document_id),
 ADD CONSTRAINT fk_overtime_requests_document FOREIGN KEY(document_id) REFERENCES approval_documents(id);

INSERT INTO doc_no_sequences(prefix,doc_year,last_seq)
 SELECT f.doc_prefix,YEAR(d.created_at),COUNT(*) FROM approval_documents d JOIN approval_forms f ON f.id=d.form_id GROUP BY f.doc_prefix,YEAR(d.created_at);
INSERT INTO approval_histories(document_id,actor_id,action,comment,created_at)
 SELECT id,drafter_id,'SUBMIT','v1 이관',submitted_at FROM approval_documents;
INSERT INTO approval_histories(document_id,actor_id,action,comment,created_at)
 SELECT d.id,r.approver_id,'REJECT',r.reject_reason,COALESCE(d.completed_at,d.created_at)
 FROM leave_requests r JOIN approval_documents d ON d.id=r.document_id WHERE r.app_status='REJECTED';

-- v1 숫자에는 귀속 연도가 없으므로 이관 당시 한국 연도에 보존한다.
SET @m2_now = DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 9 HOUR);
SET @m2_year = YEAR(@m2_now);
INSERT INTO leave_ledger(user_id,leave_year,entry_type,days,memo,created_at)
 SELECT id,@m2_year,'GRANT',total_annual_leave,'v1 이관',@m2_now FROM users;
INSERT INTO leave_ledger(user_id,leave_year,entry_type,days,memo,created_at)
 SELECT id,@m2_year,'USE',-used_annual_leave,'v1 이관',@m2_now FROM users WHERE used_annual_leave<>0;
INSERT INTO leave_ledger(user_id,leave_year,entry_type,days,document_id,memo,created_at)
 SELECT requester_id,@m2_year,'RESERVE',-used_days,document_id,'v1 이관',@m2_now FROM leave_requests WHERE app_status='PENDING';

ALTER TABLE leave_requests DROP FOREIGN KEY fk_leave_requester, DROP FOREIGN KEY fk_leave_approver,
 DROP INDEX idx_leave_requester_status_dates,DROP INDEX idx_leave_status_dates,DROP INDEX idx_leave_approver,
 DROP COLUMN requester_id,DROP COLUMN approver_id,DROP COLUMN app_status,DROP COLUMN reject_reason,
 CHANGE COLUMN app_type leave_type VARCHAR(50) NOT NULL, CHANGE COLUMN request_reason reason VARCHAR(500) NULL,
 ADD COLUMN handover_user_id BIGINT NULL,
 -- 승인 휴가의 기간 조회·기간 겹침 검사에 사용한다.
 ADD INDEX idx_leave_period(start_date,end_date,document_id),
 -- 인수인계 사원 참조에 사용한다.
 ADD INDEX idx_leave_handover(handover_user_id),
 ADD CONSTRAINT fk_leave_handover FOREIGN KEY(handover_user_id) REFERENCES users(id);
UPDATE leave_requests SET leave_type=CASE leave_type WHEN 'HALF_MORNING' THEN 'HALF_AM' WHEN 'HALF_AFTERNOON' THEN 'HALF_PM' ELSE leave_type END;
ALTER TABLE overtime_requests DROP FOREIGN KEY fk_overtime_requester,DROP FOREIGN KEY fk_overtime_approver,
 DROP INDEX idx_overtime_requester_status_date,DROP INDEX idx_overtime_status_date,DROP INDEX idx_overtime_approver,
 DROP COLUMN requester_id,DROP COLUMN approver_id,DROP COLUMN status,
 -- 날짜별 승인 특근의 일정·근태 조회에 사용한다.
 ADD INDEX idx_overtime_date(overtime_date,document_id);
ALTER TABLE users DROP COLUMN total_annual_leave,DROP COLUMN used_annual_leave,DROP COLUMN remaining_annual_leave;
ALTER TABLE approval_documents DROP COLUMN legacy_id;
