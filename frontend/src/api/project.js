import api, { unwrap } from './axios';

/** 프로젝트·칸반 보드 (backend/md/15-rest-api-v2-m4.md) */
export const projectApi = {
  list: (params) => unwrap(api.get('/projects', { params })),
  create: (body) => unwrap(api.post('/projects', body)),
  get: (id) => unwrap(api.get(`/projects/${id}`)),
  update: (id, body) => unwrap(api.patch(`/projects/${id}`, body)),
  replaceMembers: (id, userIds) => unwrap(api.put(`/projects/${id}/members`, { userIds })),
};

export const boardApi = {
  board: (projectId) => unwrap(api.get(`/projects/${projectId}/board`)),
  createTask: (projectId, body) => unwrap(api.post(`/projects/${projectId}/tasks`, body)),
  task: (id) => unwrap(api.get(`/tasks/${id}`)),
  updateTask: (id, body) => unwrap(api.patch(`/tasks/${id}`, body)),
  /** 성공: { task, project } · 409 TASK_VERSION_CONFLICT 면 error.details.currentTask 에 최신 카드 */
  move: (id, { status, afterTaskId = null, version }) =>
    unwrap(api.post(`/tasks/${id}/move`, { status, afterTaskId, version })),
};
