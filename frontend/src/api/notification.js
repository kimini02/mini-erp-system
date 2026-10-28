import api, { unwrap } from './axios';

/** 알림 (backend/md/13-rest-api-v2-m2.md) */
export const notificationApi = {
  list: (params) => unwrap(api.get('/notifications', { params })),
  unreadCount: () => unwrap(api.get('/notifications/unread-count')),
  read: (id) => unwrap(api.patch(`/notifications/${id}/read`)),
  readAll: () => unwrap(api.post('/notifications/read-all')),
};
