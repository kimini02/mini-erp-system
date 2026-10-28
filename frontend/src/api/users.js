import api, { unwrap } from './axios';

/** 사원 관리 (backend/md/12-rest-api-v2-m1.md) */
export const userApi = {
  list: (params) => unwrap(api.get('/users', { params })),
  get: (id) => unwrap(api.get(`/users/${id}`)),
  previewLoginId: (hireDate) => unwrap(api.get('/users/login-id-preview', { params: { hireDate } })),
  create: (body) => unwrap(api.post('/users', body)),
  update: (id, body) => unwrap(api.patch(`/users/${id}`, body)),
  changeStatus: (id, status) => unwrap(api.patch(`/users/${id}/status`, { status })),
  setRoles: (id, roleCodes) => unwrap(api.put(`/users/${id}/roles`, { roleCodes })),
  resendInvitation: (id) => unwrap(api.post(`/users/${id}/invitation`)),
};

/** 인수자·결재자 선택용 사원 찾기 (로그인만, 재직자의 최소 정보 · backend/md/13-rest-api-v2-m2.md) */
export const userLookupApi = {
  search: (params) => unwrap(api.get('/users/lookup', { params })),
};
