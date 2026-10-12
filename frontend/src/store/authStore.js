import { create } from 'zustand';
import { TOKEN_KEY, USER_KEY } from '@/api/axios';
import { authApi } from '@/api/m1';
import { getErrorMessage } from '@/lib/errors';
import { hasPermission, toPermissionMap } from '@/lib/permissions';

const MUST_CHANGE_KEY = 'must_change_password';

const readJson = (key) => {
  try {
    return JSON.parse(localStorage.getItem(key));
  } catch {
    return null;
  }
};

/**
 * 로그인 상태
 * - user: /me 응답 (역할 코드 목록 roles 포함)
 * - permissions: /me/permissions 를 메뉴별로 정리한 것. null 이면 아직 불러오지 않음
 * - 권한은 화면에서 메뉴를 숨기는 용도이고, 최종 판단은 서버가 합니다.
 */
export const useAuthStore = create((set, get) => ({
  user: readJson(USER_KEY),
  isAuthenticated: !!localStorage.getItem(TOKEN_KEY),
  mustChangePassword: localStorage.getItem(MUST_CHANGE_KEY) === 'true',
  permissions: null,
  sessionError: null,

  login: async ({ userId, password }) => {
    try {
      const result = await authApi.login(userId, password);
      localStorage.setItem(TOKEN_KEY, result.accessToken);
      localStorage.setItem(USER_KEY, JSON.stringify(result.user));
      localStorage.setItem(MUST_CHANGE_KEY, String(!!result.mustChangePassword));
      set({ user: result.user, isAuthenticated: true, mustChangePassword: !!result.mustChangePassword, permissions: null });

      if (!result.mustChangePassword) await get().loadSession();
      return { success: true, mustChangePassword: !!result.mustChangePassword };
    } catch (error) {
      return { success: false, message: getErrorMessage(error, '아이디 또는 비밀번호를 확인해주세요.') };
    }
  },

  /** 새로고침 뒤나 로그인 직후: 내 정보와 권한을 서버에서 다시 읽습니다. */
  loadSession: async () => {
    try {
      const [user, permissions] = await Promise.all([authApi.me(), authApi.myPermissions()]);
      localStorage.setItem(USER_KEY, JSON.stringify(user));
      set({ user, permissions: toPermissionMap(permissions), sessionError: null });
    } catch (error) {
      set({ sessionError: getErrorMessage(error, '로그인 정보를 불러오지 못했습니다.') });
    }
  },

  /** 비밀번호를 바꾸면 서버가 새 토큰을 줍니다(다른 기기의 로그인은 끊김). */
  applyNewToken: (accessToken) => {
    localStorage.setItem(TOKEN_KEY, accessToken);
    localStorage.setItem(MUST_CHANGE_KEY, 'false');
    set({ mustChangePassword: false, permissions: null });
  },

  logout: () => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    localStorage.removeItem(MUST_CHANGE_KEY);
    set({ user: null, isAuthenticated: false, permissions: null, mustChangePassword: false });
    window.location.href = '/login';
  },

  can: (menu, action = 'V') => hasPermission(get().permissions, menu, action),

  scopeOf: (menu) => get().permissions?.[menu]?.scope ?? null,
}));
