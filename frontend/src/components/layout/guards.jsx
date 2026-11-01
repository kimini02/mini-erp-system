import { useEffect } from 'react';
import { Navigate, useLocation, useParams } from 'react-router-dom';
import { Lock, RefreshCw } from 'lucide-react';
import { useAuthStore } from '@/store/authStore';
import { Button, EmptyState, PageLoader } from '@/components/ui';

/**
 * 로그인한 사람만. 첫 로그인 비밀번호 변경이 남았으면 변경 화면으로 보내고,
 * 새로고침 뒤에는 내 정보·권한을 다시 불러온 다음 화면을 그립니다.
 */
export function RequireAuth({ children }) {
  const { isAuthenticated, mustChangePassword, permissions, sessionError, loadSession, logout } = useAuthStore();
  const location = useLocation();

  useEffect(() => {
    if (isAuthenticated && !mustChangePassword && permissions === null && !sessionError) loadSession();
  }, [isAuthenticated, mustChangePassword, permissions, sessionError, loadSession]);

  if (!isAuthenticated) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (mustChangePassword) return <Navigate to="/me/password" replace />;

  if (sessionError) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-50">
        <EmptyState
          icon={RefreshCw}
          title="로그인 정보를 불러오지 못했습니다"
          description={sessionError}
          action={
            <div className="flex gap-2">
              <Button variant="secondary" onClick={logout}>
                로그아웃
              </Button>
              <Button onClick={() => useAuthStore.setState({ sessionError: null })}>다시 시도</Button>
            </div>
          }
        />
      </div>
    );
  }
  if (permissions === null) return <PageLoader className="min-h-screen" />;

  return children;
}

function NoAccess() {
  return (
    <EmptyState
      icon={Lock}
      title="이 화면을 볼 권한이 없습니다"
      description="필요하면 관리자에게 역할 권한을 요청하세요."
      className="py-24"
    />
  );
}

/** 메뉴 권한이 있어야 보이는 화면 (조직·권한) */
export function RequirePermission({ menu, action = 'V', children }) {
  const can = useAuthStore((s) => s.can);
  return can(menu, action) ? children : <NoAccess />;
}

/** 예전 역할별 경로(/admin/…, /manager/…, /user/…)를 새 경로로 */
const LEGACY_PATHS = {
  dashboard: '/home',
  approvals: '/approvals',
  'leave-approval': '/approvals',
  leaves: '/leaves',
  'leaves/new': '/leaves/new',
  calendar: '/calendar',
  'task-create': '/projects',
  'task-edit': '/projects',
  'project-auth': '/projects',
};

export function LegacyRedirect() {
  const rest = useParams()['*'] || '';
  let target = LEGACY_PATHS[rest] || '/home';
  if (rest === 'projects') target = '/projects';
  return <Navigate to={target} replace />;
}
