import api, { unwrap } from './axios';

// 실제 서버는 메뉴 그룹을 menuGroup, 쓰는 동작을 문자열("VCUD")로 줍니다. 화면은 group·배열로 씁니다.
const toMenu = (m) => ({
  ...m,
  group: m.group ?? m.menuGroup,
  allowedActions: Array.isArray(m.allowedActions)
    ? m.allowedActions
    : (String(m.allowedActions ?? '').match(/[VCUD]/g) ?? []),
});
// 역할 목록에는 locked가 없어서, 명세대로 관리소장(CHIEF)만 잠긴 역할로 봅니다.
const toRole = (r) => ({ ...r, locked: r.locked ?? r.code === 'CHIEF' });

/** 역할·권한 (backend/md/12-rest-api-v2-m1.md) */
export const roleApi = {
  menus: () => unwrap(api.get('/menus')).then((menus) => menus.map(toMenu)),
  list: () => unwrap(api.get('/roles')).then((roles) => roles.map(toRole)),
  create: (body) => unwrap(api.post('/roles', body)),
  update: (id, body) => unwrap(api.patch(`/roles/${id}`, body)),
  remove: (id) => unwrap(api.delete(`/roles/${id}`)),
  permissions: (id) => unwrap(api.get(`/roles/${id}/permissions`)),
  savePermissions: (id, permissions) => unwrap(api.put(`/roles/${id}/permissions`, permissions)),
  members: (id, params) => unwrap(api.get(`/roles/${id}/members`, { params })),
  addMembers: (id, userIds) => unwrap(api.post(`/roles/${id}/members`, { userIds })),
  removeMember: (id, userId) => unwrap(api.delete(`/roles/${id}/members/${userId}`)),
};
