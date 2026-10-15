// 메뉴 코드 · 데이터 범위 · 역할 표시 (backend/md/12-rest-api-v2-m1.md 1장)

export const MENU = {
  APPROVAL_INBOX: 'APPROVAL_INBOX',
  APPROVAL_DRAFT: 'APPROVAL_DRAFT',
  APPROVAL_FORM: 'APPROVAL_FORM',
  MY_ATTENDANCE: 'MY_ATTENDANCE',
  ATTENDANCE_MONTHLY: 'ATTENDANCE_MONTHLY',
  LEAVE_ADMIN: 'LEAVE_ADMIN',
  DEPARTMENT: 'DEPARTMENT',
  EMPLOYEE: 'EMPLOYEE',
  ROLE: 'ROLE',
  PROJECT: 'PROJECT',
  HOLIDAY: 'HOLIDAY',
};

/** 동작: V 보기 · C 등록 · U 수정 · D 삭제 */
export const ACTION_LABELS = { V: '보기', C: '등록', U: '수정', D: '삭제' };
export const ACTIONS = ['V', 'C', 'U', 'D'];

export const SCOPE_LABELS = {
  SELF: '본인',
  DEPT: '소속 부서',
  DEPT_TREE: '소속 + 하위 부서',
  ALL: '전체',
};
export const SCOPES = ['SELF', 'DEPT', 'DEPT_TREE', 'ALL'];

export const SYSTEM_ROLE_LABELS = {
  CHIEF: '관리소장',
  LEADER: '팀장',
  EMPLOYEE: '사원',
};

export const USER_STATUS_META = {
  ACTIVE: { label: '재직', tone: 'green' },
  ON_LEAVE: { label: '휴직', tone: 'amber' },
  RETIRED: { label: '퇴사', tone: 'gray' },
};

/** /me/permissions 응답 → { [menu]: { actions: Set, scope } } */
export function toPermissionMap(list = []) {
  const map = {};
  for (const p of list) {
    map[p.menu] = { actions: new Set(p.actions || []), scope: p.scope ?? null };
  }
  return map;
}

export function hasPermission(map, menu, action = 'V') {
  return Boolean(map?.[menu]?.actions.has(action));
}
