import api from './axios';

/**
 * M2 전자결재 API (backend/md/13-rest-api-v2-m2.md)
 * 모든 함수는 공통 응답의 data 만 돌려줍니다. 실패하면 axios 에러를 그대로 던집니다.
 */
const data = (promise) => promise.then((res) => res.data.data);

/** 인수자·결재자 선택용 사원 찾기 (로그인만, 재직자의 최소 정보) */
export const userLookupApi = {
  search: (params) => data(api.get('/users/lookup', { params })),
};

export const approvalFormApi = {
  list: () => data(api.get('/approval-forms')),
  get: (code) => data(api.get(`/approval-forms/${code}`)),
  updateOptions: (code, options) => data(api.patch(`/approval-forms/${code}`, options)),
  saveSteps: (code, steps) => data(api.put(`/approval-forms/${code}/steps`, steps)),
  preview: (code, drafterId) => data(api.post(`/approval-forms/${code}/line-preview`, { drafterId })),
};

export const approvalDocumentApi = {
  previewLine: (formCode, line = null) => data(api.post('/approval-documents/line-preview', { formCode, line })),
  createDraft: (body) => data(api.post('/approval-documents', body)),
  updateDraft: (id, body) => data(api.put(`/approval-documents/${id}`, body)),
  removeDraft: (id) => data(api.delete(`/approval-documents/${id}`)),
  submit: (id) => data(api.post(`/approval-documents/${id}/submit`)),
  get: (id) => data(api.get(`/approval-documents/${id}`)),
  approve: (id, comment) => data(api.post(`/approval-documents/${id}/approve`, { comment })),
  reject: (id, comment) => data(api.post(`/approval-documents/${id}/reject`, { comment })),
  withdraw: (id) => data(api.post(`/approval-documents/${id}/withdraw`)),
  resubmit: (id) => data(api.post(`/approval-documents/${id}/resubmit`)),
};

export const approvalInboxApi = {
  list: (params) => data(api.get('/approval-inbox', { params })),
  counts: () => data(api.get('/approval-inbox/counts')),
};

export const leaveApi = {
  balance: (year) => data(api.get('/leave/balance', { params: { year } })),
  ledger: (year, type) => data(api.get('/leave/ledger', { params: { year, type: type || undefined } })),
  previewDays: (body) => data(api.post('/leave/days-preview', body)),
};

export const notificationApi = {
  list: (params) => data(api.get('/notifications', { params })),
  unreadCount: () => data(api.get('/notifications/unread-count')),
  read: (id) => data(api.patch(`/notifications/${id}/read`)),
  readAll: () => data(api.post('/notifications/read-all')),
};
