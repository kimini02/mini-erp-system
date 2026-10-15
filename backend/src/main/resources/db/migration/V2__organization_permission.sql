-- M1 조직·권한: 기존 V1 데이터 보존. V1은 변경하지 않는다.
CREATE TABLE work_schedules (
id BIGINT NOT NULL AUTO_INCREMENT, name VARCHAR(100) NOT NULL,
start_time TIME(6) NOT NULL, end_time TIME(6) NOT NULL, late_grace_min INT NOT NULL, is_default BIT NOT NULL,
default_marker INT GENERATED ALWAYS AS (CASE WHEN is_default THEN 1 ELSE NULL END) VIRTUAL,
-- 근무시간 단건 조회에 사용한다.
PRIMARY KEY(id),
-- 회사 기본 근무시간 한 건 조회 및 중복 기본 설정 방지에 사용한다.
UNIQUE KEY uk_work_schedule_default(default_marker)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE positions (
id BIGINT NOT NULL AUTO_INCREMENT, name VARCHAR(100) NOT NULL, rank_order INT NOT NULL,
-- 직급 상세 조회에 사용한다.
PRIMARY KEY(id),
-- 직급 이름 중복 확인과 기존 이름 이관 조회에 사용한다.
UNIQUE KEY uk_positions_name(name),
-- 직급 순서대로 목록 조회에 사용한다.
INDEX idx_positions_rank(rank_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE departments (
id BIGINT NOT NULL AUTO_INCREMENT, name VARCHAR(100) NOT NULL, code VARCHAR(40) NOT NULL,
parent_id BIGINT NULL, head_user_id BIGINT NULL, work_schedule_id BIGINT NULL, sort_order INT NOT NULL, active BIT NOT NULL,
root_marker INT GENERATED ALWAYS AS (CASE WHEN parent_id IS NULL THEN 1 ELSE NULL END) VIRTUAL,
-- 부서 단건 조회에 사용한다.
PRIMARY KEY(id),
-- 부서 코드 중복 확인에 사용한다.
UNIQUE KEY uk_departments_code(code),
-- 최상위 부서가 한 개만 존재하도록 확인한다.
UNIQUE KEY uk_departments_root(root_marker),
-- 재귀 하위 부서 조회와 형제 정렬에 사용한다.
INDEX idx_departments_parent_order(parent_id, sort_order),
-- 이동·퇴사 시 부서장으로 지정된 부서 조회에 사용한다.
INDEX idx_departments_head(head_user_id),
-- 근무시간 참조 검증에 사용한다.
INDEX idx_departments_schedule(work_schedule_id),
CONSTRAINT fk_department_parent FOREIGN KEY(parent_id) REFERENCES departments(id),
CONSTRAINT fk_department_head FOREIGN KEY(head_user_id) REFERENCES users(id),
CONSTRAINT fk_department_schedule FOREIGN KEY(work_schedule_id) REFERENCES work_schedules(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE roles (
id BIGINT NOT NULL AUTO_INCREMENT, code VARCHAR(40) NOT NULL, name VARCHAR(100) NOT NULL,
description VARCHAR(500) NULL, is_system BIT NOT NULL,
-- 역할 단건 조회에 사용한다.
PRIMARY KEY(id),
-- 역할 코드 지정 및 기본 역할 조회에 사용한다.
UNIQUE KEY uk_roles_code(code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE menus (
id BIGINT NOT NULL AUTO_INCREMENT, code VARCHAR(40) NOT NULL, name VARCHAR(100) NOT NULL,
menu_group VARCHAR(100) NOT NULL, sort_order INT NOT NULL, allowed_actions VARCHAR(10) NOT NULL, scope_applicable BIT NOT NULL,
-- 권한표 메뉴 참조에 사용한다.
PRIMARY KEY(id),
-- 권한 어노테이션의 메뉴 코드 조회에 사용한다.
UNIQUE KEY uk_menus_code(code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_roles (
user_id BIGINT NOT NULL, role_id BIGINT NOT NULL,
-- 사용자별 역할 조회 및 중복 지정 방지에 사용한다.
PRIMARY KEY(user_id,role_id),
-- 역할별 사원 목록·인원수 조회에 사용한다.
INDEX idx_user_roles_role(role_id,user_id),
CONSTRAINT fk_user_roles_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
CONSTRAINT fk_user_roles_role FOREIGN KEY(role_id) REFERENCES roles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE role_permissions (
role_id BIGINT NOT NULL, menu_id BIGINT NOT NULL, can_view BIT NOT NULL,
can_create BIT NOT NULL, can_update BIT NOT NULL, can_delete BIT NOT NULL, data_scope VARCHAR(20) NULL,
-- 역할의 메뉴별 권한 조회·교체에 사용한다.
PRIMARY KEY(role_id,menu_id),
-- 메뉴 참조 검증 및 메뉴별 권한 조회에 사용한다.
INDEX idx_role_permissions_menu(menu_id),
CONSTRAINT fk_role_permissions_role FOREIGN KEY(role_id) REFERENCES roles(id) ON DELETE CASCADE,
CONSTRAINT fk_role_permissions_menu FOREIGN KEY(menu_id) REFERENCES menus(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE login_id_sequences (
hire_year INT NOT NULL, last_seq INT NOT NULL,
-- 입사연도별 번호 발급 행을 조회·잠그는 데 사용한다.
PRIMARY KEY(hire_year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE holidays (
id BIGINT NOT NULL AUTO_INCREMENT, holiday_date DATE NOT NULL, name VARCHAR(100) NOT NULL, substitute BIT NOT NULL,
-- 공휴일 단건 수정·삭제에 사용한다.
PRIMARY KEY(id),
-- 날짜 중복 확인과 연도별 날짜 범위 조회에 사용한다.
UNIQUE KEY uk_holidays_date(holiday_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO work_schedules(id,name,start_time,end_time,late_grace_min,is_default) VALUES (1,'회사 기본','09:00','18:00',10,1);
INSERT INTO departments(id,name,code,parent_id,sort_order,active) VALUES
(1,'회사','D-0001',NULL,0,1),(2,'개발팀','D-0002',1,1,1),(3,'유지보수팀','D-0003',1,2,1),(4,'모바일개발팀','D-0004',1,3,1);
INSERT INTO positions(id,name,rank_order) VALUES (1,'사원',1),(2,'대리',2),(3,'과장',3),(4,'팀장',4),(5,'관리소장',5);
INSERT INTO roles(id,code,name,description,is_system) VALUES (1,'CHIEF','관리소장',NULL,1),(2,'LEADER','팀장',NULL,1),(3,'EMPLOYEE','사원',NULL,1);
INSERT INTO menus VALUES (1,'APPROVAL_INBOX','결재함','전자결재',1,'V',0);
INSERT INTO role_permissions VALUES (1,1,1,0,0,0,NULL);
INSERT INTO role_permissions VALUES (2,1,1,0,0,0,NULL);
INSERT INTO role_permissions VALUES (3,1,1,0,0,0,NULL);
INSERT INTO menus VALUES (2,'APPROVAL_DRAFT','기안하기','전자결재',2,'C',0);
INSERT INTO role_permissions VALUES (1,2,0,1,0,0,NULL);
INSERT INTO role_permissions VALUES (2,2,0,1,0,0,NULL);
INSERT INTO role_permissions VALUES (3,2,0,1,0,0,NULL);
INSERT INTO menus VALUES (3,'APPROVAL_FORM','결재 양식 관리','전자결재',3,'V,U',0);
INSERT INTO role_permissions VALUES (1,3,1,0,1,0,NULL);
INSERT INTO menus VALUES (4,'MY_ATTENDANCE','내 근태','근태·휴가',4,'V,C',0);
INSERT INTO role_permissions VALUES (1,4,1,1,0,0,NULL);
INSERT INTO role_permissions VALUES (2,4,1,1,0,0,NULL);
INSERT INTO role_permissions VALUES (3,4,1,1,0,0,NULL);
INSERT INTO menus VALUES (5,'ATTENDANCE_MONTHLY','월간 근태표','근태·휴가',5,'V,U',1);
INSERT INTO role_permissions VALUES (1,5,1,0,1,0,'ALL');
INSERT INTO role_permissions VALUES (2,5,1,0,0,0,'DEPT_TREE');
INSERT INTO menus VALUES (6,'LEAVE_ADMIN','연차 관리','근태·휴가',6,'V,C,U',1);
INSERT INTO role_permissions VALUES (1,6,1,1,1,0,'ALL');
INSERT INTO role_permissions VALUES (2,6,1,0,0,0,'DEPT_TREE');
INSERT INTO menus VALUES (7,'DEPARTMENT','부서 관리','조직',7,'V,C,U,D',0);
INSERT INTO role_permissions VALUES (1,7,1,1,1,1,NULL);
INSERT INTO role_permissions VALUES (2,7,1,0,0,0,NULL);
INSERT INTO menus VALUES (8,'EMPLOYEE','사원 관리','조직',8,'V,C,U,D',1);
INSERT INTO role_permissions VALUES (1,8,1,1,1,1,'ALL');
INSERT INTO role_permissions VALUES (2,8,1,0,0,0,'DEPT_TREE');
INSERT INTO menus VALUES (9,'ROLE','역할·권한','조직',9,'V,C,U,D',0);
INSERT INTO role_permissions VALUES (1,9,1,1,1,1,NULL);
INSERT INTO menus VALUES (10,'PROJECT','프로젝트','업무·시스템',10,'V,C,U,D',1);
INSERT INTO role_permissions VALUES (1,10,1,1,1,1,'ALL');
INSERT INTO role_permissions VALUES (2,10,1,1,1,0,'DEPT');
INSERT INTO role_permissions VALUES (3,10,1,0,0,0,'SELF');
INSERT INTO menus VALUES (11,'HOLIDAY','공휴일','업무·시스템',11,'V,C,U,D',0);
INSERT INTO role_permissions VALUES (1,11,1,1,1,1,NULL);
INSERT INTO role_permissions VALUES (2,11,1,0,0,0,NULL);
INSERT INTO role_permissions VALUES (3,11,1,0,0,0,NULL);

INSERT INTO positions(name,rank_order)
SELECT DISTINCT u.position_name,100 FROM users u WHERE NOT EXISTS(SELECT 1 FROM positions p WHERE p.name=u.position_name);
ALTER TABLE users ADD department_id BIGINT NULL, ADD position_id BIGINT NULL, ADD hire_date DATE NULL,
ADD status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', ADD must_change_pw BIT NOT NULL DEFAULT 0,
ADD token_version INT NOT NULL DEFAULT 0, MODIFY user_pw VARCHAR(255) NULL;
UPDATE users u JOIN positions p ON p.name=u.position_name
SET u.department_id=CASE u.department_code WHEN '01' THEN 2 WHEN '02' THEN 3 WHEN '03' THEN 4 END,
u.position_id=p.id,u.status=CASE WHEN u.is_active THEN 'ACTIVE' ELSE 'RETIRED' END;
-- v1 비활성 사유를 알 수 없으므로 RETIRED로 닫고, 입사일은 NULL로 보존한다.
-- Flyway가 이 결과를 실행 로그에 남긴다. 계정/비밀번호 없이 이관 건수만 기록한다.
SELECT CONCAT('M1: migrated_inactive_to_retired=', COUNT(*)) AS migration_summary
FROM users WHERE is_active=0 AND status='RETIRED';
INSERT INTO user_roles(user_id,role_id)
SELECT id,CASE user_role WHEN 'ADMIN' THEN 1 WHEN 'TEAM_LEADER' THEN 2 ELSE 3 END FROM users;
-- 기존 숫자형 로그인 아이디는 그대로 유지하고 해당 연도 시퀀스 충돌을 피한다.
INSERT INTO login_id_sequences(hire_year,last_seq)
SELECT CAST(LEFT(login_id,4) AS UNSIGNED),MAX(CAST(SUBSTRING(login_id,5) AS UNSIGNED))
FROM users WHERE login_id REGEXP '^[0-9]{4}[0-9]{3,}$' GROUP BY LEFT(login_id,4);
ALTER TABLE users MODIFY department_id BIGINT NOT NULL, MODIFY position_id BIGINT NOT NULL;
-- 부서 범위·상태별 사원 목록과 인원 집계에 사용한다.
CREATE INDEX idx_users_department_status ON users(department_id,status);
-- 직급 사용 여부 조회에 사용한다.
CREATE INDEX idx_users_position ON users(position_id);
-- 이름 앞부분 일치 검색에 사용한다.
CREATE INDEX idx_users_name ON users(user_name);
ALTER TABLE users ADD CONSTRAINT fk_users_department FOREIGN KEY(department_id) REFERENCES departments(id),
ADD CONSTRAINT fk_users_position FOREIGN KEY(position_id) REFERENCES positions(id);
ALTER TABLE users DROP INDEX idx_users_role, DROP department_code, DROP position_name, DROP user_role, DROP is_active;
