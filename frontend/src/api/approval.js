import api, { unwrap } from './axios';

/** 전자결재 양식·문서·결재함 (backend/md/13-rest-api-v2-m2.md) */
export const approvalFormApi = {
  list: () => unwrap(api.get('/approval-forms')),
  get: (code) => unwrap(api.get(`/approval-forms/${code}`)),
  updateOptions: (code, options) => unwrap(api.patch(`/approval-forms/${code}`, options)),
  saveSteps: (code, steps) => unwrap(api.put(`/approval-forms/${code}/steps`, steps)),
  preview: (code, drafterId) => unwrap(api.post(`/approval-forms/${code}/line-preview`, { drafterId })),
};

export const approvalDocumentApi = {
  previewLine: (formCode, line = null) => unwrap(api.post('/approval-documents/line-preview', { formCode, line })),
  createDraft: (body) => unwrap(api.post('/approval-documents', body)),
  updateDraft: (id, body) => unwrap(api.put(`/approval-documents/${id}`, body)),
  removeDraft: (id) => unwrap(api.delete(`/approval-documents/${id}`)),
  submit: (id) => unwrap(api.post(`/approval-documents/${id}/submit`)),
  get: (id) => unwrap(api.get(`/approval-documents/${id}`)),
  approve: (id, comment) => unwrap(api.post(`/approval-documents/${id}/approve`, { comment })),
  reject: (id, comment) => unwrap(api.post(`/approval-documents/${id}/reject`, { comment })),
  withdraw: (id) => unwrap(api.post(`/approval-documents/${id}/withdraw`)),
  resubmit: (id) => unwrap(api.post(`/approval-documents/${id}/resubmit`)),
};

export const approvalInboxApi = {
  list: (params) => unwrap(api.get('/approval-inbox', { params })),
  counts: () => unwrap(api.get('/approval-inbox/counts')),
};
