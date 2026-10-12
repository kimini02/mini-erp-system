-- M0: 현재 엔티티의 신규 설치용 스키마. 기존 V1~V5 사용 DB는 백업 후 재생성한다.
-- 스키마 변경은 Flyway만 수행한다. Hibernate는 validate로 검증한다.
-- 문자열 열거형은 기존 V5와 같이 VARCHAR로 저장하며 값은 애플리케이션에서 관리한다.
-- JPA cascade와 DB 삭제 연쇄는 별개이므로 외래키 삭제는 기본 RESTRICT를 유지한다.

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    user_name VARCHAR(50) NOT NULL,
    user_email VARCHAR(100) NOT NULL,
    user_pw VARCHAR(255) NOT NULL,
    department_code VARCHAR(2) NOT NULL,
    position_name VARCHAR(30) NOT NULL,
    assign_role VARCHAR(30) NULL,
    user_role VARCHAR(20) NOT NULL,
    is_active BIT NOT NULL,
    total_annual_leave DECIMAL(5,1) NOT NULL,
    used_annual_leave DECIMAL(5,1) NOT NULL,
    remaining_annual_leave DECIMAL(5,1) NOT NULL,
    login_id VARCHAR(50) NOT NULL,
    -- 사용자 상세 조회와 다른 테이블의 사용자 참조에 사용한다.
    PRIMARY KEY (id),
    -- 이메일 중복 확인 및 이메일로 계정을 찾는 조회에 사용한다.
    CONSTRAINT uk_users_email UNIQUE (user_email),
    -- 로그인 및 아이디 중복 확인에 사용한다.
    CONSTRAINT uk_users_login_id UNIQUE (login_id),
    -- 역할별 사용자 조회 및 최초 관리자 존재 확인에 사용한다.
    INDEX idx_users_role (user_role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE projects (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    title VARCHAR(100) NOT NULL,
    content VARCHAR(1000) NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(255) NOT NULL,
    priority VARCHAR(255) NOT NULL,
    leader_id BIGINT NULL,
    -- 프로젝트 상세 조회와 업무·멤버의 프로젝트 참조에 사용한다.
    PRIMARY KEY (id),
    -- 담당 팀장별 프로젝트 목록과 개수 조회에 사용한다.
    INDEX idx_projects_leader (leader_id),
    -- 상태별 프로젝트 목록과 개수 조회에 사용한다.
    INDEX idx_projects_status (status),
    CONSTRAINT fk_projects_leader FOREIGN KEY (leader_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE project_members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    -- 프로젝트 멤버 단건 조회·삭제에 사용한다.
    PRIMARY KEY (id),
    -- 프로젝트별 멤버 조회·집계 및 중복 멤버 확인에 사용한다.
    CONSTRAINT uk_project_members_project_user UNIQUE (project_id, user_id),
    -- 사용자가 참여한 프로젝트 목록 조회에 사용한다.
    INDEX idx_project_members_user (user_id),
    CONSTRAINT fk_project_members_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_project_members_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tasks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    task_title VARCHAR(100) NOT NULL,
    task_content VARCHAR(1000) NOT NULL,
    end_date DATE NOT NULL,
    task_status VARCHAR(255) NOT NULL,
    priority VARCHAR(255) NOT NULL,
    project_id BIGINT NOT NULL,
    -- 업무 상세 조회와 담당자 배정의 업무 참조에 사용한다.
    PRIMARY KEY (id),
    -- 프로젝트별 업무 목록 및 프로젝트·상태별 개수 집계에 사용한다.
    INDEX idx_tasks_project_status (project_id, task_status),
    -- 전체 대시보드의 업무 상태별 집계에 사용한다.
    INDEX idx_tasks_status (task_status),
    CONSTRAINT fk_tasks_project FOREIGN KEY (project_id) REFERENCES projects (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE task_assignments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    -- 담당자 배정 단건 조회·삭제에 사용한다.
    PRIMARY KEY (id),
    -- 업무별 담당자 목록 및 중복 배정 확인에 사용한다.
    CONSTRAINT uk_task_assignments_task_user UNIQUE (task_id, user_id),
    -- 사용자별 담당 업무 조회에 사용한다.
    INDEX idx_task_assignments_user (user_id),
    -- 전체 최근 배정 10건을 생성 시각 역순으로 조회할 때 사용한다.
    INDEX idx_task_assignments_created (created_at),
    CONSTRAINT fk_task_assignments_task FOREIGN KEY (task_id) REFERENCES tasks (id),
    CONSTRAINT fk_task_assignments_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE attendances (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    user_id BIGINT NOT NULL,
    work_date DATE NOT NULL,
    clock_in_time TIME(6) NULL,
    clock_out_time TIME(6) NULL,
    att_status VARCHAR(20) NOT NULL,
    -- 근태 기록 단건 조회·정정에 사용한다.
    PRIMARY KEY (id),
    -- 사용자별 당일 근태 및 기간별 근태 조회와 중복 출근 방지에 사용한다.
    CONSTRAINT uk_attendances_user_date UNIQUE (user_id, work_date),
    CONSTRAINT fk_attendances_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE leave_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    requester_id BIGINT NOT NULL,
    approver_id BIGINT NULL,
    app_type VARCHAR(50) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    used_days DECIMAL(4,1) NOT NULL,
    app_status VARCHAR(50) NOT NULL,
    reject_reason VARCHAR(500) NULL,
    request_reason VARCHAR(500) NULL,
    -- 연차 신청 상세 조회와 승인·반려 대상 조회에 사용한다.
    PRIMARY KEY (id),
    -- 신청자별 목록 및 상태·기간이 겹치는 연차 조회에 사용한다.
    INDEX idx_leave_requester_status_dates (requester_id, app_status, start_date, end_date),
    -- 전체 일정의 승인 연차 기간 조회 및 상태별 개수 조회에 사용한다.
    INDEX idx_leave_status_dates (app_status, start_date, end_date),
    -- 사용자 삭제 시 참조 중인 승인 기록을 확인하는 외래키 조회에 사용한다.
    INDEX idx_leave_approver (approver_id),
    CONSTRAINT fk_leave_requester FOREIGN KEY (requester_id) REFERENCES users (id),
    CONSTRAINT fk_leave_approver FOREIGN KEY (approver_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE overtime_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    requester_id BIGINT NOT NULL,
    approver_id BIGINT NULL,
    overtime_date DATE NOT NULL,
    start_time TIME(6) NOT NULL,
    end_time TIME(6) NOT NULL,
    reason VARCHAR(500) NULL,
    status VARCHAR(255) NOT NULL,
    -- 특근 신청 상세 조회와 승인·반려 대상 조회에 사용한다.
    PRIMARY KEY (id),
    -- 신청자별 목록 및 승인 상태·날짜 범위별 특근 조회에 사용한다.
    INDEX idx_overtime_requester_status_date (requester_id, status, overtime_date),
    -- 전체 일정의 승인 특근 날짜 범위 조회에 사용한다.
    INDEX idx_overtime_status_date (status, overtime_date),
    -- 사용자 삭제 시 참조 중인 승인 기록을 확인하는 외래키 조회에 사용한다.
    INDEX idx_overtime_approver (approver_id),
    CONSTRAINT fk_overtime_requester FOREIGN KEY (requester_id) REFERENCES users (id),
    CONSTRAINT fk_overtime_approver FOREIGN KEY (approver_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
