import api, { unwrap } from './axios';

/** 홈 화면 한 번에 받기 (backend/md/15-rest-api-v2-m4.md) */
export const homeApi = {
  get: () => unwrap(api.get('/home')),
};
