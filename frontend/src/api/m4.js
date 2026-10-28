import api from './axios';

/**
 * M4 홈 · 프로젝트 보드 API (backend/md/15-rest-api-v2-m4.md)
 * 모든 함수는 공통 응답의 data 만 돌려줍니다. 실패하면 axios 에러를 그대로 던집니다.
 */
const data = (promise) => promise.then((res) => res.data.data);

export const homeApi = {
  get: () => data(api.get('/home')),
};

export const projectApi = {
  list: (params) => data(api.get('/projects', { params })),
  create: (body) => data(api.post('/projects', body)),
  get: (id) => data(api.get(`/projects/${id}`)),
  update: (id, body) => data(api.patch(`/projects/${id}`, body)),
  replaceMembers: (id, userIds) => data(api.put(`/projects/${id}/members`, { userIds })),
};

export const boardApi = {
  board: (projectId) => data(api.get(`/projects/${projectId}/board`)),
  createTask: (projectId, body) => data(api.post(`/projects/${projectId}/tasks`, body)),
  task: (id) => data(api.get(`/tasks/${id}`)),
  updateTask: (id, body) => data(api.patch(`/tasks/${id}`, body)),
  /** 성공: { task, project } · 409 TASK_VERSION_CONFLICT 면 error.details.currentTask 에 최신 카드 */
  move: (id, { status, afterTaskId = null, version }) =>
    data(api.post(`/tasks/${id}/move`, { status, afterTaskId, version })),
};
