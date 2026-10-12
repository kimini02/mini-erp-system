// 화면 전반에서 공통으로 쓰는 코드 → 표시 이름 매핑

export const STATUS_META = {
  PENDING: { tone: 'amber', label: '대기' },
  APPROVED: { tone: 'green', label: '승인' },
  REJECTED: { tone: 'red', label: '반려' },
};

export const LEAVE_TYPE_LABELS = {
  ANNUAL: '연차',
  HALF_MORNING: '오전 반차',
  HALF_AFTERNOON: '오후 반차',
};

export const DEPARTMENT_LABELS = {
  '01': '개발팀',
  '02': '유지보수팀',
  '03': '모바일개발팀',
};

export const ROLE_LABELS = {
  ADMIN: '관리소장',
  TEAM_LEADER: '팀장',
  MANAGER: '팀장',
  USER: '사원',
};

// 프로젝트 상태 (백엔드 ProjectStatus)
export const PROJECT_STATUS_LABELS = {
  READY: '준비',
  PROGRESS: '진행 중',
  DONE: '완료',
};

// 업무(Task) 상태 / 우선순위
export const TASK_STATUS_META = {
  완료: { tone: 'green' },
  진행중: { tone: 'blue' },
  대기: { tone: 'gray' },
  지연: { tone: 'red' },
};

export const PRIORITY_META = {
  높음: { tone: 'red' },
  중간: { tone: 'amber' },
  낮음: { tone: 'gray' },
};
