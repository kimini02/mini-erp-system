import api, { unwrap } from './axios';

/** 내 승인 휴가·특근 일정 (기존 GET /calendar/events, backend/md/03-rest-api.md 4.6) */
export const calendarApi = {
  /** [{ eventId, title, start, end, type: 'LEAVE' | 'OVERTIME' }] — start·end는 'YYYY-MM-DDTHH:mm:ss' */
  events: (year, month) => unwrap(api.get('/calendar/events', { params: { year, month } })),
};
