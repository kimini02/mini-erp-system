import { AxiosError } from 'axios';
import { createDb, menus } from './db';
import { installM2 } from './m2';

/**
 * 개발용 가짜 서버 (VITE_USE_MOCK=true).
 * backend/md/12-rest-api-v2-m1.md 의 요청·응답·에러 코드를 그대로 흉내 냅니다.
 * 데이터는 메모리에만 있어서 새로고침하면 처음 상태로 돌아갑니다.
 */
const db = createDb();

const SCOPE_RANK = { SELF: 0, DEPT: 1, DEPT_TREE: 2, ALL: 3 };
const ACTION_KEYS = { V: 'view', C: 'create', U: 'update', D: 'delete' };
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const timestamp = () => new Date().toISOString().slice(0, 19);

class ApiFail extends Error {
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}
const fail = (status, code, message) => {
  throw new ApiFail(status, code, message);
};
const created = (data) => ({ __status: 201, data });

/* ───────── 조회 도우미 ───────── */

const deptById = (id) => db.departments.find((d) => d.id === Number(id));
const userById = (id) => db.users.find((u) => u.id === Number(id));
const roleById = (id) => db.roles.find((r) => r.id === Number(id));
const roleByCode = (code) => db.roles.find((r) => r.code === code);
const positionById = (id) => db.positions.find((p) => p.id === Number(id));
const childrenOf = (id) => db.departments.filter((d) => d.parentId === id).sort((a, b) => a.sortOrder - b.sortOrder);

function subtreeIds(id) {
  const ids = [id];
  childrenOf(id).forEach((c) => ids.push(...subtreeIds(c.id)));
  return ids;
}

function deptPath(dept) {
  const path = [];
  let cur = dept.parentId ? deptById(dept.parentId) : null;
  while (cur) {
    path.unshift({ id: cur.id, name: cur.name });
    cur = cur.parentId ? deptById(cur.parentId) : null;
  }
  return path;
}

const isMember = (u) => u.status !== 'RETIRED';
const directMembers = (deptId) => db.users.filter((u) => u.departmentId === deptId && isMember(u));

function headView(userId) {
  const u = userId ? userById(userId) : null;
  return u ? { id: u.id, name: u.name, position: positionById(u.positionId)?.name } : null;
}

function legacyRole(roles) {
  if (roles.includes('CHIEF')) return 'ADMIN';
  if (roles.includes('LEADER')) return 'TEAM_LEADER';
  return 'USER';
}

function userView(u) {
  const dept = deptById(u.departmentId);
  return {
    id: u.id,
    loginId: u.loginId,
    name: u.name,
    email: u.email,
    department: dept ? { id: dept.id, name: dept.name } : null,
    position: positionById(u.positionId)?.name,
    roles: [...u.roles],
    hireDate: u.hireDate,
    status: u.status,
    // 기존 화면 호환 필드 (M1 프론트 작업 후 제거 예정)
    role: legacyRole(u.roles),
    departmentName: dept?.name,
  };
}

function userListView(u) {
  const dept = deptById(u.departmentId);
  return {
    id: u.id,
    loginId: u.loginId,
    name: u.name,
    email: u.email,
    department: dept ? { id: dept.id, name: dept.name } : null,
    position: positionById(u.positionId)?.name,
    roles: u.roles.map((code) => ({ code, name: roleByCode(code)?.name || code })),
    hireDate: u.hireDate,
    status: u.status,
    invitationPending: u.invitationPending,
  };
}

function page(items, params) {
  const number = Math.max(0, Number(params.page ?? 0));
  const size = Math.max(1, Number(params.size ?? 20));
  const totalElements = items.length;
  const totalPages = Math.max(1, Math.ceil(totalElements / size));
  const content = items.slice(number * size, number * size + size);
  return {
    content,
    page: { number, size, totalElements, totalPages, first: number === 0, last: number >= totalPages - 1, numberOfElements: content.length },
  };
}

/* ───────── 권한 ───────── */

function mergedPermissions(user) {
  const merged = {};
  for (const code of user.roles) {
    const perms = db.rolePermissions[code] || {};
    for (const [menu, p] of Object.entries(perms)) {
      const cur = merged[menu] || { view: false, create: false, update: false, delete: false, scope: null };
      ['view', 'create', 'update', 'delete'].forEach((k) => { cur[k] = cur[k] || p[k]; });
      if (p.scope && (!cur.scope || SCOPE_RANK[p.scope] > SCOPE_RANK[cur.scope])) cur.scope = p.scope;
      merged[menu] = cur;
    }
  }
  return merged;
}

function need(ctx, menu, action) {
  const p = mergedPermissions(ctx.user)[menu];
  if (!p || !p[ACTION_KEYS[action]]) fail(403, 'ACCESS_DENIED', '접근 권한이 없습니다.');
  return p;
}

/** 데이터 범위 → 볼 수 있는 부서 id 집합 (null = 전체) */
function visibleDeptIds(ctx, scope) {
  const me = ctx.user;
  if (scope === 'ALL') return null;
  if (scope === 'DEPT_TREE') return new Set(subtreeIds(me.departmentId));
  if (scope === 'DEPT') return new Set([me.departmentId]);
  return new Set();
}

function canSeeUser(ctx, scope, target) {
  if (target.id === ctx.user.id) return true;
  const ids = visibleDeptIds(ctx, scope);
  return ids === null || ids.has(target.departmentId);
}

const activeChiefs = () => db.users.filter((u) => u.roles.includes('CHIEF') && u.status === 'ACTIVE');
const isHeadSomewhere = (userId) => db.departments.find((d) => d.headUserId === userId);

function validatePassword(pw) {
  const ok = pw && pw.length >= 8 && pw.length <= 20 && /[A-Z]/.test(pw) && /[a-z]/.test(pw) && /\d/.test(pw)
    && /[!@#$%^&*()_+\-=[\]{};':"\\|,.<>/?]/.test(pw);
  if (!ok) fail(400, 'PASSWORD_POLICY_VIOLATION', '비밀번호는 8~20자이고 대문자, 소문자, 숫자, 특수문자를 모두 포함해야 합니다.');
}

const tokenOf = (u) => `mock.${u.id}.${u.tokenVersion}`;

function nextLoginId(year) {
  const used = db.users
    .filter((u) => /^\d{7}$/.test(u.loginId) && u.loginId.startsWith(String(year)))
    .map((u) => Number(u.loginId.slice(4)));
  const seq = (used.length ? Math.max(...used) : 0) + 1;
  return `${year}${String(seq).padStart(3, '0')}`;
}

/* ───────── 라우트 ───────── */

const routes = [];
const route = (method, pattern, handler, options = {}) => {
  const keys = [];
  // id 자리(:id, :userId)는 숫자만 — '/users/lookup'이 '/users/:id'에 걸리지 않게
  const regex = new RegExp(`^${pattern.replace(/:(\w+)/g, (_, k) => { keys.push(k); return /id$/i.test(k) ? '(\\d+)' : '([^/]+)'; })}$`);
  routes.push({ method, regex, keys, handler, auth: options.auth !== false, allowBeforePwChange: options.allowBeforePwChange });
};

// 인증 · 내 정보
route('POST', '/auth/login', ({ body }) => {
  const user = db.users.find((u) => u.loginId === body.id);
  if (!user || user.invitationPending || user.password !== body.password) {
    fail(401, 'INVALID_CREDENTIALS', '아이디 또는 비밀번호가 올바르지 않습니다.');
  }
  if (user.status !== 'ACTIVE') fail(403, 'ACCOUNT_INACTIVE', '재직 중인 계정이 아닙니다.');
  return { accessToken: tokenOf(user), tokenType: 'Bearer', expiresIn: 3600, mustChangePassword: user.mustChangePw, user: userView(user) };
}, { auth: false });

route('POST', '/auth/password/setup', ({ body }) => {
  const userId = db.setupTokens[body.setupToken];
  if (!userId) fail(401, 'SETUP_TOKEN_INVALID', '비밀번호 설정 링크가 만료되었거나 이미 사용되었습니다.');
  validatePassword(body.newPassword);
  const user = userById(userId);
  user.password = body.newPassword;
  user.invitationPending = false;
  delete db.setupTokens[body.setupToken];
  return null;
}, { auth: false });

route('PATCH', '/me/password', ({ body, user }) => {
  if (user.password !== body.currentPassword) fail(400, 'INVALID_INPUT_VALUE', '현재 비밀번호가 올바르지 않습니다.');
  validatePassword(body.newPassword);
  if (body.newPassword === user.password) fail(400, 'PASSWORD_POLICY_VIOLATION', '이전과 다른 비밀번호를 입력해주세요.');
  user.password = body.newPassword;
  user.mustChangePw = false;
  user.tokenVersion += 1;
  return { accessToken: tokenOf(user), tokenType: 'Bearer', expiresIn: 3600 };
}, { allowBeforePwChange: true });

route('GET', '/me', ({ user }) => userView(user), { allowBeforePwChange: true });

route('GET', '/me/permissions', ({ user }) => {
  const merged = mergedPermissions(user);
  return Object.entries(merged)
    .map(([menu, p]) => ({
      menu,
      actions: Object.entries(ACTION_KEYS).filter(([, key]) => p[key]).map(([a]) => a),
      scope: p.scope,
    }))
    .filter((p) => p.actions.length);
});

// 부서
function treeNode(dept) {
  const kids = childrenOf(dept.id).map(treeNode);
  const direct = directMembers(dept.id).length;
  return {
    id: dept.id,
    name: dept.name,
    code: dept.code,
    head: headView(dept.headUserId),
    directMemberCount: direct,
    totalMemberCount: direct + kids.reduce((s, k) => s + k.totalMemberCount, 0),
    children: kids,
  };
}

route('GET', '/departments/tree', () => treeNode(db.departments.find((d) => d.parentId === null)));

route('GET', '/departments/:id', ({ path }) => {
  const dept = deptById(path.id) || fail(404, 'DEPARTMENT_NOT_FOUND', '부서를 찾을 수 없습니다.');
  let up = dept.parentId ? deptById(dept.parentId) : null;
  while (up && !up.headUserId) up = up.parentId ? deptById(up.parentId) : null;
  const fallback = !dept.headUserId && up ? { ...headView(up.headUserId), departmentName: up.name } : null;
  return {
    id: dept.id,
    name: dept.name,
    code: dept.code,
    path: deptPath(dept),
    parentId: dept.parentId,
    head: headView(dept.headUserId),
    fallbackHead: fallback,
    workSchedule: dept.schedule ? { useDefault: false, ...dept.schedule } : { useDefault: true, ...db.defaultSchedule },
    directMemberCount: directMembers(dept.id).length,
    childCount: childrenOf(dept.id).length,
  };
});

route('POST', '/departments', (ctx) => {
  need(ctx, 'DEPARTMENT', 'C');
  const { name, parentId, sortOrder } = ctx.body;
  if (!name?.trim()) fail(400, 'INVALID_INPUT_VALUE', '부서명을 입력해 주세요.');
  if (!deptById(parentId)) fail(404, 'DEPARTMENT_NOT_FOUND', '부서를 찾을 수 없습니다.');
  const id = db.seq.department++;
  const dept = { id, name: name.trim(), code: `D-${String(id).padStart(4, '0')}`, parentId: Number(parentId), headUserId: null, sortOrder: sortOrder ?? 99, schedule: null };
  db.departments.push(dept);
  return created({ id: dept.id, code: dept.code });
});

route('PATCH', '/departments/:id', (ctx) => {
  need(ctx, 'DEPARTMENT', 'U');
  const dept = deptById(ctx.path.id) || fail(404, 'DEPARTMENT_NOT_FOUND', '부서를 찾을 수 없습니다.');
  if (ctx.body.name !== undefined) {
    if (!ctx.body.name.trim()) fail(400, 'INVALID_INPUT_VALUE', '부서명을 입력해 주세요.');
    dept.name = ctx.body.name.trim();
  }
  if (ctx.body.sortOrder !== undefined) dept.sortOrder = ctx.body.sortOrder;
  return null;
});

route('PATCH', '/departments/:id/parent', (ctx) => {
  need(ctx, 'DEPARTMENT', 'U');
  const dept = deptById(ctx.path.id) || fail(404, 'DEPARTMENT_NOT_FOUND', '부서를 찾을 수 없습니다.');
  const parentId = ctx.body.parentId;
  if (parentId === null || parentId === undefined || subtreeIds(dept.id).includes(Number(parentId))) {
    fail(422, 'INVALID_DEPARTMENT_MOVE', '자기 자신이나 하위 부서 아래로 옮길 수 없습니다.');
  }
  if (!deptById(parentId)) fail(404, 'DEPARTMENT_NOT_FOUND', '부서를 찾을 수 없습니다.');
  dept.parentId = Number(parentId);
  return null;
});

route('PUT', '/departments/:id/head', (ctx) => {
  need(ctx, 'DEPARTMENT', 'U');
  const dept = deptById(ctx.path.id) || fail(404, 'DEPARTMENT_NOT_FOUND', '부서를 찾을 수 없습니다.');
  const user = userById(ctx.body.userId) || fail(404, 'USER_NOT_FOUND', '사용자를 찾을 수 없습니다.');
  if (user.departmentId !== dept.id) fail(422, 'HEAD_NOT_IN_DEPARTMENT', '그 부서 소속 사원만 부서장이 될 수 있습니다.');
  if (user.status !== 'ACTIVE') fail(422, 'ACCOUNT_INACTIVE', '재직 중인 계정이 아닙니다.');
  dept.headUserId = user.id;
  return null;
});

route('DELETE', '/departments/:id/head', (ctx) => {
  need(ctx, 'DEPARTMENT', 'U');
  const dept = deptById(ctx.path.id) || fail(404, 'DEPARTMENT_NOT_FOUND', '부서를 찾을 수 없습니다.');
  dept.headUserId = null;
  return null;
});

function validateSchedule({ startTime, endTime, lateGraceMin }) {
  if (!startTime || !endTime || endTime <= startTime) fail(400, 'INVALID_WORK_SCHEDULE', '퇴근 시각은 출근 시각보다 늦어야 합니다.');
  if (lateGraceMin < 0 || lateGraceMin > 60) fail(400, 'INVALID_INPUT_VALUE', '지각 유예는 0~60분입니다.');
}

route('PUT', '/departments/:id/work-schedule', (ctx) => {
  need(ctx, 'DEPARTMENT', 'U');
  const dept = deptById(ctx.path.id) || fail(404, 'DEPARTMENT_NOT_FOUND', '부서를 찾을 수 없습니다.');
  if (ctx.body.useDefault) {
    dept.schedule = null;
  } else {
    const schedule = { startTime: ctx.body.startTime, endTime: ctx.body.endTime, lateGraceMin: Number(ctx.body.lateGraceMin) };
    validateSchedule(schedule);
    dept.schedule = schedule;
  }
  return null;
});

route('DELETE', '/departments/:id', (ctx) => {
  need(ctx, 'DEPARTMENT', 'D');
  const dept = deptById(ctx.path.id) || fail(404, 'DEPARTMENT_NOT_FOUND', '부서를 찾을 수 없습니다.');
  if (childrenOf(dept.id).length || db.users.some((u) => u.departmentId === dept.id)) {
    fail(409, 'DEPARTMENT_NOT_EMPTY', '소속 사원이나 하위 부서가 있어 삭제할 수 없습니다.');
  }
  db.departments = db.departments.filter((d) => d.id !== dept.id);
  return null;
});

route('GET', '/work-schedules/default', () => ({ ...db.defaultSchedule }));
route('PUT', '/work-schedules/default', (ctx) => {
  need(ctx, 'DEPARTMENT', 'U');
  const schedule = { startTime: ctx.body.startTime, endTime: ctx.body.endTime, lateGraceMin: Number(ctx.body.lateGraceMin) };
  validateSchedule(schedule);
  db.defaultSchedule = schedule;
  return null;
});

// 직급
route('GET', '/positions', () => [...db.positions].sort((a, b) => a.rankOrder - b.rankOrder));

// 사원
route('GET', '/users', (ctx) => {
  const perm = need(ctx, 'EMPLOYEE', 'V');
  const { q, departmentId, includeSubDepartments, status } = ctx.params;
  const visible = visibleDeptIds(ctx, perm.scope);

  let deptFilter = null;
  if (departmentId) {
    const ids = String(includeSubDepartments) === 'true' ? subtreeIds(Number(departmentId)) : [Number(departmentId)];
    if (visible && ids.some((id) => !visible.has(id))) fail(403, 'ACCESS_DENIED', '접근 권한이 없습니다.');
    deptFilter = new Set(ids);
  }

  const keyword = (q || '').trim();
  const base = db.users.filter((u) =>
    (visible === null || visible.has(u.departmentId) || u.id === ctx.user.id)
    && (!deptFilter || deptFilter.has(u.departmentId))
    && (!keyword || u.name.startsWith(keyword) || u.loginId.startsWith(keyword)));

  const statusCounts = { ACTIVE: 0, ON_LEAVE: 0, RETIRED: 0 };
  base.forEach((u) => { statusCounts[u.status] += 1; });

  const rows = base
    .filter((u) => !status || u.status === status)
    .sort((a, b) => a.name.localeCompare(b.name, 'ko'))
    .map(userListView);

  return { ...page(rows, ctx.params), statusCounts };
});

route('GET', '/users/login-id-preview', (ctx) => {
  need(ctx, 'EMPLOYEE', 'C');
  const year = (ctx.params.hireDate || '').slice(0, 4);
  if (!/^\d{4}$/.test(year)) fail(400, 'INVALID_INPUT_VALUE', '입사일을 확인해 주세요.');
  return { loginId: nextLoginId(year) };
});

route('GET', '/users/:id', (ctx) => {
  const target = userById(ctx.path.id) || fail(404, 'USER_NOT_FOUND', '사용자를 찾을 수 없습니다.');
  if (target.id !== ctx.user.id) {
    const perm = need(ctx, 'EMPLOYEE', 'V');
    if (!canSeeUser(ctx, perm.scope, target)) fail(403, 'ACCESS_DENIED', '접근 권한이 없습니다.');
  }
  return userListView(target);
});

// 사원 외 역할(팀장·관리소장·사용자 정의 역할)을 가졌는지
const hasPrivilegedRole = (user) => user.roles.some((c) => c !== 'EMPLOYEE');

// 사원마다 유효한 초대 링크는 하나. 다시 보내거나 이메일이 바뀌면 이전 링크는 바로 무효
function revokeInvitations(userId) {
  for (const [token, id] of Object.entries(db.setupTokens)) if (id === userId) delete db.setupTokens[token];
}
function issueInvitation(user) {
  revokeInvitations(user.id);
  user.invitationVersion = (user.invitationVersion || 0) + 1;
  const token = user.invitationVersion === 1 ? `invite-${user.id}` : `invite-${user.id}-${user.invitationVersion}`;
  db.setupTokens[token] = user.id;
}

function checkRoleCodes(codes) {
  if (!Array.isArray(codes) || codes.length === 0) fail(422, 'ROLE_REQUIRED', '역할이 하나 이상 있어야 합니다.');
  codes.forEach((c) => roleByCode(c) || fail(404, 'ROLE_NOT_FOUND', '역할을 찾을 수 없습니다.'));
}

route('POST', '/users', (ctx) => {
  need(ctx, 'EMPLOYEE', 'C');
  const { name, email, hireDate, departmentId, positionId, roleCodes } = ctx.body;
  if (!name?.trim() || !email?.includes('@') || !hireDate) fail(400, 'INVALID_INPUT_VALUE', '잘못된 요청 값입니다.');
  if (db.users.some((u) => u.email === email)) fail(409, 'EMAIL_ALREADY_EXISTS', '이미 사용 중인 이메일입니다.');
  if (!deptById(departmentId)) fail(404, 'DEPARTMENT_NOT_FOUND', '부서를 찾을 수 없습니다.');
  if (!positionById(positionId)) fail(404, 'POSITION_NOT_FOUND', '직급을 찾을 수 없습니다.');
  checkRoleCodes(roleCodes);
  // 권한 상승 방지: 사원 외 역할을 함께 주려면 역할 수정 권한도 필요
  if (roleCodes.some((c) => c !== 'EMPLOYEE')) need(ctx, 'ROLE', 'U');

  const id = db.seq.user++;
  const loginId = nextLoginId(hireDate.slice(0, 4));
  const user = {
    id, loginId, name: name.trim(), email, departmentId: Number(departmentId), positionId: Number(positionId),
    roles: [...roleCodes], hireDate, status: 'ACTIVE', invitationPending: true, mustChangePw: false, tokenVersion: 0, password: null,
  };
  db.users.push(user);
  issueInvitation(user);
  return created({ id, loginId, invitationSent: db.mailEnabled });
});

route('PATCH', '/users/:id', (ctx) => {
  const perm = need(ctx, 'EMPLOYEE', 'U');
  const target = userById(ctx.path.id) || fail(404, 'USER_NOT_FOUND', '사용자를 찾을 수 없습니다.');
  if (!canSeeUser(ctx, perm.scope, target)) fail(403, 'ACCESS_DENIED', '접근 권한이 없습니다.');
  const { name, email, departmentId, positionId } = ctx.body;
  const emailChanged = email !== undefined && email !== target.email;
  // 권한 상승 방지: 관리 역할을 가진 사원의 이메일을 바꾸려면 역할 수정 권한도 필요
  // (이메일로 비밀번호 재설정·초대 링크가 가므로, 바꾸면 그 계정을 가져갈 수 있음)
  if (emailChanged && hasPrivilegedRole(target)) need(ctx, 'ROLE', 'U');
  if (emailChanged && db.users.some((u) => u.email === email && u.id !== target.id)) {
    fail(409, 'EMAIL_ALREADY_EXISTS', '이미 사용 중인 이메일입니다.');
  }
  if (departmentId !== undefined && Number(departmentId) !== target.departmentId) {
    if (!deptById(departmentId)) fail(404, 'DEPARTMENT_NOT_FOUND', '부서를 찾을 수 없습니다.');
    if (isHeadSomewhere(target.id)) fail(422, 'HEAD_MUST_BE_REASSIGNED', '부서장을 먼저 다른 사람으로 바꿔 주세요.');
    target.departmentId = Number(departmentId);
  }
  if (positionId !== undefined) {
    if (!positionById(positionId)) fail(404, 'POSITION_NOT_FOUND', '직급을 찾을 수 없습니다.');
    target.positionId = Number(positionId);
  }
  if (ctx.body.hireDate !== undefined) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(ctx.body.hireDate)) fail(400, 'INVALID_INPUT_VALUE', '입사일을 확인해 주세요.');
    target.hireDate = ctx.body.hireDate;
  }
  if (name !== undefined) target.name = name.trim();
  if (emailChanged) {
    target.email = email;
    revokeInvitations(target.id);
  }
  return userListView(target);
});

route('PATCH', '/users/:id/status', (ctx) => {
  const perm = need(ctx, 'EMPLOYEE', 'U');
  const target = userById(ctx.path.id) || fail(404, 'USER_NOT_FOUND', '사용자를 찾을 수 없습니다.');
  if (!canSeeUser(ctx, perm.scope, target)) fail(403, 'ACCESS_DENIED', '접근 권한이 없습니다.');
  const next = ctx.body.status;
  if (!['ACTIVE', 'ON_LEAVE', 'RETIRED'].includes(next)) fail(400, 'INVALID_INPUT_VALUE', '잘못된 요청 값입니다.');
  if (next !== 'ACTIVE' && target.status === 'ACTIVE') {
    if (isHeadSomewhere(target.id)) fail(422, 'HEAD_MUST_BE_REASSIGNED', '부서장을 먼저 다른 사람으로 바꿔 주세요.');
    if (target.roles.includes('CHIEF') && activeChiefs().length <= 1) fail(409, 'LAST_CHIEF', '관리소장이 한 명 이상 있어야 합니다.');
    target.tokenVersion += 1; // 기존 토큰 즉시 무효
  }
  target.status = next;
  return null;
});

route('PUT', '/users/:id/roles', (ctx) => {
  need(ctx, 'ROLE', 'U');
  const target = userById(ctx.path.id) || fail(404, 'USER_NOT_FOUND', '사용자를 찾을 수 없습니다.');
  const codes = ctx.body.roleCodes;
  checkRoleCodes(codes);
  if (target.roles.includes('CHIEF') && !codes.includes('CHIEF') && activeChiefs().length <= 1) {
    fail(409, 'LAST_CHIEF', '관리소장이 한 명 이상 있어야 합니다.');
  }
  target.roles = [...new Set(codes)];
  return null;
});

route('POST', '/users/:id/invitation', (ctx) => {
  need(ctx, 'EMPLOYEE', 'C');
  const target = userById(ctx.path.id) || fail(404, 'USER_NOT_FOUND', '사용자를 찾을 수 없습니다.');
  if (hasPrivilegedRole(target)) need(ctx, 'ROLE', 'U');
  if (!target.invitationPending) fail(409, 'INVITATION_ALREADY_ACCEPTED', '이미 비밀번호를 설정한 사원입니다.');
  issueInvitation(target);
  return { invitationSent: db.mailEnabled };
});

// 역할·권한
route('GET', '/menus', (ctx) => {
  need(ctx, 'ROLE', 'V');
  return menus;
});

const roleView = (r) => ({ ...r, memberCount: db.users.filter((u) => u.roles.includes(r.code) && isMember(u)).length });

route('GET', '/roles', (ctx) => {
  need(ctx, 'ROLE', 'V');
  return db.roles.map(roleView);
});

route('POST', '/roles', (ctx) => {
  need(ctx, 'ROLE', 'C');
  const name = ctx.body.name?.trim();
  if (!name) fail(400, 'INVALID_INPUT_VALUE', '역할 이름을 입력해 주세요.');
  if (db.roles.some((r) => r.name === name)) fail(409, 'DUPLICATE_RESOURCE', '이미 같은 이름의 역할이 있습니다.');
  const id = db.seq.role++;
  const role = { id, code: `CUSTOM_${id}`, name, description: ctx.body.description?.trim() || '', system: false, locked: false };
  db.roles.push(role);
  db.rolePermissions[role.code] = {};
  return created(roleView(role));
});

route('PATCH', '/roles/:id', (ctx) => {
  need(ctx, 'ROLE', 'U');
  const role = roleById(ctx.path.id) || fail(404, 'ROLE_NOT_FOUND', '역할을 찾을 수 없습니다.');
  if (role.system) fail(403, 'SYSTEM_ROLE_LOCKED', '기본 역할은 바꾸거나 지울 수 없습니다.');
  if (ctx.body.name !== undefined) {
    if (!ctx.body.name.trim()) fail(400, 'INVALID_INPUT_VALUE', '역할 이름을 입력해 주세요.');
    role.name = ctx.body.name.trim();
  }
  if (ctx.body.description !== undefined) role.description = ctx.body.description.trim();
  return roleView(role);
});

route('DELETE', '/roles/:id', (ctx) => {
  need(ctx, 'ROLE', 'D');
  const role = roleById(ctx.path.id) || fail(404, 'ROLE_NOT_FOUND', '역할을 찾을 수 없습니다.');
  if (role.system) fail(403, 'SYSTEM_ROLE_LOCKED', '기본 역할은 바꾸거나 지울 수 없습니다.');
  if (db.users.some((u) => u.roles.includes(role.code))) fail(409, 'ROLE_IN_USE', '이 역할을 쓰는 사원이나 결재 규칙이 있습니다.');
  db.roles = db.roles.filter((r) => r.id !== role.id);
  delete db.rolePermissions[role.code];
  return null;
});

route('GET', '/roles/:id/permissions', (ctx) => {
  need(ctx, 'ROLE', 'V');
  const role = roleById(ctx.path.id) || fail(404, 'ROLE_NOT_FOUND', '역할을 찾을 수 없습니다.');
  const perms = db.rolePermissions[role.code] || {};
  return {
    role: { id: role.id, code: role.code, name: role.name, system: role.system, locked: role.locked },
    permissions: menus.map((m) => ({
      menu: m.code,
      view: false, create: false, update: false, delete: false, scope: null,
      ...(perms[m.code] || {}),
    })),
  };
});

route('PUT', '/roles/:id/permissions', (ctx) => {
  need(ctx, 'ROLE', 'U');
  const role = roleById(ctx.path.id) || fail(404, 'ROLE_NOT_FOUND', '역할을 찾을 수 없습니다.');
  if (role.locked) fail(403, 'SYSTEM_ROLE_LOCKED', '기본 역할은 바꾸거나 지울 수 없습니다.');
  const next = {};
  for (const p of ctx.body || []) {
    const menu = menus.find((m) => m.code === p.menu) || fail(400, 'INVALID_PERMISSION', '이 메뉴에서 쓸 수 없는 권한입니다.');
    const on = Object.entries(ACTION_KEYS).filter(([, key]) => p[key]).map(([a]) => a);
    if (on.some((a) => !menu.allowedActions.includes(a))) fail(400, 'INVALID_PERMISSION', '이 메뉴에서 쓸 수 없는 권한입니다.');
    if (!menu.scopeApplicable && p.scope) fail(400, 'INVALID_PERMISSION', '이 메뉴에서 쓸 수 없는 권한입니다.');
    if (menu.scopeApplicable && on.length && !p.scope) fail(400, 'INVALID_PERMISSION', '이 메뉴에서 쓸 수 없는 권한입니다.');
    if (on.length) next[menu.code] = { view: !!p.view, create: !!p.create, update: !!p.update, delete: !!p.delete, scope: p.scope || null };
  }
  db.rolePermissions[role.code] = next;
  return null;
});

route('GET', '/roles/:id/members', (ctx) => {
  need(ctx, 'ROLE', 'V');
  const role = roleById(ctx.path.id) || fail(404, 'ROLE_NOT_FOUND', '역할을 찾을 수 없습니다.');
  const rows = db.users.filter((u) => u.roles.includes(role.code) && isMember(u)).map(userListView);
  return page(rows, ctx.params);
});

route('POST', '/roles/:id/members', (ctx) => {
  need(ctx, 'ROLE', 'U');
  const role = roleById(ctx.path.id) || fail(404, 'ROLE_NOT_FOUND', '역할을 찾을 수 없습니다.');
  (ctx.body.userIds || []).forEach((id) => {
    const user = userById(id) || fail(404, 'USER_NOT_FOUND', '사용자를 찾을 수 없습니다.');
    if (!user.roles.includes(role.code)) user.roles.push(role.code);
  });
  return null;
});

route('DELETE', '/roles/:id/members/:userId', (ctx) => {
  need(ctx, 'ROLE', 'U');
  const role = roleById(ctx.path.id) || fail(404, 'ROLE_NOT_FOUND', '역할을 찾을 수 없습니다.');
  const user = userById(ctx.path.userId) || fail(404, 'USER_NOT_FOUND', '사용자를 찾을 수 없습니다.');
  if (role.code === 'CHIEF' && activeChiefs().length <= 1 && user.roles.includes('CHIEF')) {
    fail(409, 'LAST_CHIEF', '관리소장이 한 명 이상 있어야 합니다.');
  }
  if (user.roles.length <= 1) fail(422, 'ROLE_REQUIRED', '역할이 하나 이상 있어야 합니다.');
  user.roles = user.roles.filter((c) => c !== role.code);
  return null;
});

// 공휴일
route('GET', '/holidays', ({ params }) => {
  const year = String(params.year || new Date().getFullYear());
  return db.holidays.filter((h) => h.date.startsWith(year)).sort((a, b) => a.date.localeCompare(b.date));
});

route('POST', '/holidays', (ctx) => {
  need(ctx, 'HOLIDAY', 'C');
  const { date, name, substitute } = ctx.body;
  if (!date || !name?.trim()) fail(400, 'INVALID_INPUT_VALUE', '날짜와 이름을 입력해 주세요.');
  if (db.holidays.some((h) => h.date === date)) fail(409, 'HOLIDAY_ALREADY_EXISTS', '이미 등록된 날짜입니다.');
  const holiday = { id: db.seq.holiday++, date, name: name.trim(), substitute: !!substitute };
  db.holidays.push(holiday);
  return created(holiday);
});

route('PATCH', '/holidays/:id', (ctx) => {
  need(ctx, 'HOLIDAY', 'U');
  const holiday = db.holidays.find((h) => h.id === Number(ctx.path.id)) || fail(404, 'RESOURCE_NOT_FOUND', '요청한 리소스를 찾을 수 없습니다.');
  if (ctx.body.name !== undefined) holiday.name = ctx.body.name.trim();
  if (ctx.body.substitute !== undefined) holiday.substitute = !!ctx.body.substitute;
  return holiday;
});

route('DELETE', '/holidays/:id', (ctx) => {
  need(ctx, 'HOLIDAY', 'D');
  db.holidays = db.holidays.filter((h) => h.id !== Number(ctx.path.id));
  return null;
});

// M2 전자결재 (backend/md/13-rest-api-v2-m2.md)
installM2({ route, db, fail, created, need, userById, deptById, roleByCode, positionById, page });

/* ───────── 어댑터 ───────── */

function currentUser(config) {
  const header = config.headers?.Authorization || config.headers?.authorization || '';
  const [, id, ver] = /^Bearer mock\.(\d+)\.(\d+)$/.exec(header) || [];
  const user = id ? userById(id) : null;
  if (!user) fail(401, 'UNAUTHORIZED', '인증이 필요합니다.');
  if (Number(ver) !== user.tokenVersion || user.status !== 'ACTIVE') {
    fail(401, 'TOKEN_REVOKED', '로그인이 만료되었습니다. 다시 로그인해 주세요.');
  }
  return user;
}

function toResponse(config, status, body) {
  return { data: body, status, statusText: String(status), headers: {}, config, request: {} };
}

export async function handleMock(config, realAdapter) {
  const method = (config.method || 'get').toUpperCase();
  const path = (config.url || '').split('?')[0];

  let matched = null;
  for (const r of routes) {
    if (r.method !== method) continue;
    const m = r.regex.exec(path);
    if (m) {
      matched = { route: r, path: Object.fromEntries(r.keys.map((k, i) => [k, decodeURIComponent(m[i + 1])])) };
      break;
    }
  }
  // 명세에 없는 요청(연차·프로젝트 등 기존 API)은 실제 서버로 보냅니다.
  if (!matched) return realAdapter(config);

  await sleep(200);
  try {
    const body = typeof config.data === 'string' && config.data ? JSON.parse(config.data) : config.data || {};
    const ctx = { body, params: config.params || {}, path: matched.path, user: null };
    if (matched.route.auth) {
      ctx.user = currentUser(config);
      if (ctx.user.mustChangePw && !matched.route.allowBeforePwChange) {
        fail(403, 'PASSWORD_CHANGE_REQUIRED', '비밀번호를 먼저 변경해 주세요.');
      }
    }
    const result = matched.route.handler(ctx);
    const status = result?.__status || 200;
    const data = result?.__status ? result.data : result ?? null;
    return toResponse(config, status, { success: true, data, message: '요청이 성공적으로 처리되었습니다.', timestamp: timestamp() });
  } catch (err) {
    if (!(err instanceof ApiFail)) throw err;
    const response = toResponse(config, err.status, { success: false, error: { code: err.code, message: err.message }, timestamp: timestamp() });
    throw new AxiosError(err.message, err.status >= 500 ? 'ERR_BAD_RESPONSE' : 'ERR_BAD_REQUEST', config, {}, response);
  }
}
