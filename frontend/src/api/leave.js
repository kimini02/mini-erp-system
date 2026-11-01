import api, { unwrap } from './axios';

/** 내 연차 (backend/md/13-rest-api-v2-m2.md) */
export const leaveApi = {
  balance: (year) => unwrap(api.get('/leave/balance', { params: { year } })),
  ledger: (year, type) => unwrap(api.get('/leave/ledger', { params: { year, type: type || undefined } })),
  previewDays: (body) => unwrap(api.post('/leave/days-preview', body)),
};

/** 연차 관리 (backend/md/14-rest-api-v2-m3.md) */
export const leaveAdminApi = {
  balances: (params) => unwrap(api.get('/leave/admin/balances', { params })),
  ledger: (userId, year) => unwrap(api.get(`/leave/admin/ledger/${userId}`, { params: { year } })),
  adjust: (body) => unwrap(api.post('/leave/admin/adjustments', body)),
  grantRuns: (limit = 10) => unwrap(api.get('/leave/admin/grant-runs', { params: { limit } })),
  runGrant: (date) => unwrap(api.post('/leave/admin/grant-runs', { date })),
};
