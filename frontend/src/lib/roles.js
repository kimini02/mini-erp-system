import { ROLE_LABELS } from './labels';

/**
 * 로그인 사용자의 권한/직급으로 화면 분기 정보를 계산합니다.
 * - isChief: 관리소장 (ADMIN 또는 직급 '관리소장')
 * - isTeamLeader: 팀장 (TEAM_LEADER/MANAGER 또는 직급 '팀장')
 * - basePath/homePath: 라우트 권한(role) 기준 메뉴 경로
 */
export function getRoleInfo(user) {
  const role = user?.role || user?.user_role || '';
  const position = user?.position || '';

  const isChief = role === 'ADMIN' || position === '관리소장';
  const isTeamLeader = role === 'MANAGER' || role === 'TEAM_LEADER' || position === '팀장';

  // 메뉴 경로는 ProtectedRoute와 같은 role 기준으로 결정 (직급만으로는 /admin, /manager 접근 불가)
  const routeRole =
    role === 'ADMIN' || (position === '관리소장' && role !== 'USER')
      ? 'admin'
      : role === 'TEAM_LEADER' || role === 'MANAGER'
        ? 'manager'
        : 'user';
  const basePath = `/${routeRole}`;

  return {
    role,
    position,
    isChief,
    isTeamLeader,
    routeRole,
    basePath,
    homePath: `${basePath}/dashboard`,
    roleLabel: ROLE_LABELS[role] || position || '사원',
  };
}

export const getDisplayName = (user) => user?.name || user?.userName || '사용자';
