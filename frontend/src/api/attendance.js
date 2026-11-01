import api, { unwrap } from './axios';

/** 출퇴근·월간 근태표 (backend/md/14-rest-api-v2-m3.md) */
export const attendanceApi = {
  today: () => unwrap(api.get('/attendance/today')),
  clockIn: () => unwrap(api.post('/attendance/clock-in')),
  clockOut: () => unwrap(api.post('/attendance/clock-out')),
  me: (month) => unwrap(api.get('/attendance/me', { params: { month } })),
  monthly: (params) => unwrap(api.get('/attendance/monthly', { params })),
  record: (userId, date) => unwrap(api.get(`/attendance/records/${userId}/${date}`)),
  /** CSV 파일(Blob)과 파일 이름 */
  exportCsv: (params) =>
    api.get('/attendance/monthly/export', { params, responseType: 'blob' }).then((res) => {
      const disposition = res.headers?.['content-disposition'] || '';
      const filename = /filename="?([^";]+)"?/.exec(disposition)?.[1] || `attendance-${params.month}.csv`;
      return { blob: res.data, filename };
    }),
  rejudge: (date) => unwrap(api.post('/attendance/rejudge', { date })),
};
