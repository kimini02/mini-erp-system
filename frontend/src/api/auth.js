import api, { unwrap } from './axios';

/** 로그인·비밀번호·내 정보 (backend/md/12-rest-api-v2-m1.md) */
export const authApi = {
  login: (loginId, password) => unwrap(api.post('/auth/login', { id: loginId, password })),
  setupPassword: (setupToken, newPassword) => unwrap(api.post('/auth/password/setup', { setupToken, newPassword })),
  changePassword: (currentPassword, newPassword) => unwrap(api.patch('/me/password', { currentPassword, newPassword })),
  me: () => unwrap(api.get('/me')),
  myPermissions: () => unwrap(api.get('/me/permissions')),
};
