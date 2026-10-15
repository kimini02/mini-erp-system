import api from './axios';

/**
 * M1 조직·권한 API (backend/md/12-rest-api-v2-m1.md)
 * 모든 함수는 공통 응답의 data 만 돌려줍니다. 실패하면 axios 에러를 그대로 던집니다.
 */
const data = (promise) => promise.then((res) => res.data.data);

export const authApi = {
  login: (loginId, password) => data(api.post('/auth/login', { id: loginId, password })),
  setupPassword: (setupToken, newPassword) => data(api.post('/auth/password/setup', { setupToken, newPassword })),
  changePassword: (currentPassword, newPassword) => data(api.patch('/me/password', { currentPassword, newPassword })),
  me: () => data(api.get('/me')),
  myPermissions: () => data(api.get('/me/permissions')),
};

export const departmentApi = {
  tree: () => data(api.get('/departments/tree')),
  get: (id) => data(api.get(`/departments/${id}`)),
  create: (body) => data(api.post('/departments', body)),
  update: (id, body) => data(api.patch(`/departments/${id}`, body)),
  move: (id, parentId) => data(api.patch(`/departments/${id}/parent`, { parentId })),
  setHead: (id, userId) => data(api.put(`/departments/${id}/head`, { userId })),
  clearHead: (id) => data(api.delete(`/departments/${id}/head`)),
  setWorkSchedule: (id, body) => data(api.put(`/departments/${id}/work-schedule`, body)),
  remove: (id) => data(api.delete(`/departments/${id}`)),
};

export const workScheduleApi = {
  getDefault: () => data(api.get('/work-schedules/default')),
  updateDefault: (body) => data(api.put('/work-schedules/default', body)),
};

export const positionApi = {
  list: () => data(api.get('/positions')),
};

export const userApi = {
  list: (params) => data(api.get('/users', { params })),
  get: (id) => data(api.get(`/users/${id}`)),
  previewLoginId: (hireDate) => data(api.get('/users/login-id-preview', { params: { hireDate } })),
  create: (body) => data(api.post('/users', body)),
  update: (id, body) => data(api.patch(`/users/${id}`, body)),
  changeStatus: (id, status) => data(api.patch(`/users/${id}/status`, { status })),
  setRoles: (id, roleCodes) => data(api.put(`/users/${id}/roles`, { roleCodes })),
  resendInvitation: (id) => data(api.post(`/users/${id}/invitation`)),
};

export const roleApi = {
  menus: () => data(api.get('/menus')),
  list: () => data(api.get('/roles')),
  create: (body) => data(api.post('/roles', body)),
  update: (id, body) => data(api.patch(`/roles/${id}`, body)),
  remove: (id) => data(api.delete(`/roles/${id}`)),
  permissions: (id) => data(api.get(`/roles/${id}/permissions`)),
  savePermissions: (id, permissions) => data(api.put(`/roles/${id}/permissions`, permissions)),
  members: (id, params) => data(api.get(`/roles/${id}/members`, { params })),
  addMembers: (id, userIds) => data(api.post(`/roles/${id}/members`, { userIds })),
  removeMember: (id, userId) => data(api.delete(`/roles/${id}/members/${userId}`)),
};

export const holidayApi = {
  list: (year) => data(api.get('/holidays', { params: { year } })),
  create: (body) => data(api.post('/holidays', body)),
  update: (id, body) => data(api.patch(`/holidays/${id}`, body)),
  remove: (id) => data(api.delete(`/holidays/${id}`)),
};

/** 조직도 트리 → [{ id, name, depth }] (부서 선택 목록용) */
export function flattenTree(node, depth = 0, out = []) {
  if (!node) return out;
  out.push({ id: node.id, name: node.name, depth });
  (node.children || []).forEach((child) => flattenTree(child, depth + 1, out));
  return out;
}

/** 트리에서 id 부서와 그 하위 부서 id 전부 */
export function collectSubtreeIds(node, targetId) {
  const find = (n) => {
    if (!n) return null;
    if (n.id === targetId) return n;
    for (const c of n.children || []) {
      const hit = find(c);
      if (hit) return hit;
    }
    return null;
  };
  const ids = new Set();
  const walk = (n) => {
    ids.add(n.id);
    (n.children || []).forEach(walk);
  };
  const target = find(node);
  if (target) walk(target);
  return ids;
}
