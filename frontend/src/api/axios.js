import axios from 'axios';

export const TOKEN_KEY = 'user_token';
export const USER_KEY = 'user_info';

/**
 * 모든 API 요청이 쓰는 axios 인스턴스.
 * - baseURL: VITE_API_BASE_URL (기본 http://localhost:8080/api/v1)
 * - VITE_USE_MOCK=true 이면 M1 API(조직·권한)는 명세와 같은 모양의 가짜 응답을 씁니다.
 */
const instance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
});

// 가짜 서버: `npm run dev:mock` 또는 VITE_USE_MOCK=true 일 때만 불러옵니다(배포 빌드에는 포함되지 않음).
// 명세에 없는 요청(연차·프로젝트 등 기존 API)은 실제 서버로 보냅니다.
if (import.meta.env.MODE === 'mock' || import.meta.env.VITE_USE_MOCK === 'true') {
  const realAdapter = axios.getAdapter(instance.defaults.adapter);
  instance.defaults.adapter = async (config) => {
    const { handleMock } = await import('./mock');
    return handleMock(config, realAdapter);
  };
}

const isAuthRequest = (config) => (config?.url || '').startsWith('/auth/');

// 로그인·초대 링크·비밀번호 찾기(/auth/)에는 토큰을 붙이지 않습니다.
// 남아 있던 만료 토큰 때문에 로그인이나 첫 비밀번호 설정이 막히지 않게 합니다.
instance.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token && !isAuthRequest(config)) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

instance.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    const code = error.response?.data?.error?.code;

    // 토큰 만료·무효(퇴사 처리, 비밀번호 변경 등): 저장된 로그인 정보를 지우고 로그인 화면으로
    if (status === 401 && !isAuthRequest(error.config)) {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
      if (window.location.pathname !== '/login') {
        window.location.assign(`/login?reason=${code === 'TOKEN_REVOKED' ? 'revoked' : 'expired'}`);
      }
    }

    // 첫 로그인 비밀번호 변경 전에는 다른 API를 쓸 수 없습니다.
    if (status === 403 && code === 'PASSWORD_CHANGE_REQUIRED' && window.location.pathname !== '/me/password') {
      window.location.assign('/me/password');
    }

    return Promise.reject(error);
  }
);

export default instance;
