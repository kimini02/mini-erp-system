import { useMemo } from 'react';
import { useAuthStore } from '@/store/authStore';
import { getRoleInfo } from '@/lib/roles';
import { hasPermission } from '@/lib/permissions';
import { getNavigation } from './navigation';

/** 로그인한 사람의 권한에 맞는 사이드바 메뉴 그룹 */
export function useNavGroups() {
  const user = useAuthStore((s) => s.user);
  const permissions = useAuthStore((s) => s.permissions);
  const { isChief, isTeamLeader } = getRoleInfo(user);

  return useMemo(
    () => getNavigation({ can: (menu, action = 'V') => hasPermission(permissions, menu, action), isChief, isTeamLeader }),
    [permissions, isChief, isTeamLeader]
  );
}
