import api, { unwrap } from './axios';

/** 부서·근무시간·직급·공휴일 (backend/md/12-rest-api-v2-m1.md) */
export const departmentApi = {
  tree: () => unwrap(api.get('/departments/tree')),
  get: (id) => unwrap(api.get(`/departments/${id}`)),
  create: (body) => unwrap(api.post('/departments', body)),
  update: (id, body) => unwrap(api.patch(`/departments/${id}`, body)),
  move: (id, parentId) => unwrap(api.patch(`/departments/${id}/parent`, { parentId })),
  setHead: (id, userId) => unwrap(api.put(`/departments/${id}/head`, { userId })),
  clearHead: (id) => unwrap(api.delete(`/departments/${id}/head`)),
  setWorkSchedule: (id, body) => unwrap(api.put(`/departments/${id}/work-schedule`, body)),
  remove: (id) => unwrap(api.delete(`/departments/${id}`)),
};

export const workScheduleApi = {
  getDefault: () => unwrap(api.get('/work-schedules/default')),
  updateDefault: (body) => unwrap(api.put('/work-schedules/default', body)),
};

export const positionApi = {
  list: () => unwrap(api.get('/positions')),
};

export const holidayApi = {
  list: (year) => unwrap(api.get('/holidays', { params: { year } })),
  create: (body) => unwrap(api.post('/holidays', body)),
  update: (id, body) => unwrap(api.patch(`/holidays/${id}`, body)),
  remove: (id) => unwrap(api.delete(`/holidays/${id}`)),
};
