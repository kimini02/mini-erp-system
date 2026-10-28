import api from './axios';

/**
 * M3 근태·휴가 API (backend/md/14-rest-api-v2-m3.md)
 * 모든 함수는 공통 응답의 data 만 돌려줍니다. 실패하면 axios 에러를 그대로 던집니다.
 */
const data = (promise) => promise.then((res) => res.data.data);

export const attendanceApi = {
  today: () => data(api.get('/attendance/today')),
  clockIn: () => data(api.post('/attendance/clock-in')),
  clockOut: () => data(api.post('/attendance/clock-out')),
  me: (month) => data(api.get('/attendance/me', { params: { month } })),
  monthly: (params) => data(api.get('/attendance/monthly', { params })),
  record: (userId, date) => data(api.get(`/attendance/records/${userId}/${date}`)),
  /** CSV 파일(Blob)과 파일 이름 */
  exportCsv: (params) =>
    api.get('/attendance/monthly/export', { params, responseType: 'blob' }).then((res) => {
      const disposition = res.headers?.['content-disposition'] || '';
      const filename = /filename="?([^";]+)"?/.exec(disposition)?.[1] || `attendance-${params.month}.csv`;
      return { blob: res.data, filename };
    }),
  rejudge: (date) => data(api.post('/attendance/rejudge', { date })),
};

export const leaveAdminApi = {
  balances: (params) => data(api.get('/leave/admin/balances', { params })),
  ledger: (userId, year) => data(api.get(`/leave/admin/ledger/${userId}`, { params: { year } })),
  adjust: (body) => data(api.post('/leave/admin/adjustments', body)),
  grantRuns: (limit = 10) => data(api.get('/leave/admin/grant-runs', { params: { limit } })),
  runGrant: (date) => data(api.post('/leave/admin/grant-runs', { date })),
};

/** 받은 Blob을 파일로 내려받게 합니다. */
export function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
