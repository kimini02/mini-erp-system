import { ROLE_LABELS } from './labels';

/**
 * 기존 화면(연차·특근 결재, 대시보드)이 쓰는 역할 값.
 * 기준은 user.roles(역할 코드 목록)이고, 기존 role 값은 거기서 계산합니다.
 *   CHIEF → ADMIN(관리소장), LEADER → TEAM_LEADER(팀장), 그 외 → USER
 * 메뉴 표시와 화면 접근은 lib/permissions 의 메뉴 권한을 씁니다.
 */
export function legacyRoleOf(user) {
  if (Array.isArray(user?.roles)) {
    if (user.roles.includes('CHIEF')) return 'ADMIN';
    if (user.roles.includes('LEADER')) return 'TEAM_LEADER';
    return 'USER';
  }
  return user?.role || user?.user_role || 'USER';
}

export function getRoleInfo(user) {
  const role = legacyRoleOf(user);
  const position = user?.position || '';
  const isChief = role === 'ADMIN';
  const isTeamLeader = role === 'TEAM_LEADER' || role === 'MANAGER';

  return {
    role,
    position,
    isChief,
    isTeamLeader,
    workspace: isChief ? '관리자' : isTeamLeader ? '팀장' : '임직원',
    // 경로는 역할과 상관없이 하나입니다 (예: /approvals)
    basePath: '',
    homePath: '/dashboard',
    roleLabel: ROLE_LABELS[role] || position || '사원',
  };
}

export const getDisplayName = (user) => user?.name || user?.userName || '사용자';
