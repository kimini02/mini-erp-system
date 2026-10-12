import { useEffect, useState } from 'react';
import { Outlet } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import { useApprovalStore } from '@/store/approvalStore';
import ProfilePage from '@/pages/ProfilePage';
import Sidebar from './Sidebar';
import Topbar from './Topbar';

/**
 * 로그인 후 모든 화면(관리소장/팀장/사원)이 공유하는 레이아웃
 * 좌측 사이드바 + 상단바 + 본문(Outlet)
 */
export default function AppLayout() {
  const { user } = useAuthStore();
  const fetchPending = useApprovalStore((s) => s.fetchPending);
  const [navOpen, setNavOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);

  // 결재 대기 건수 (상단 알림, 사이드바 배지)
  useEffect(() => {
    if (user) fetchPending(user);
  }, [user, fetchPending]);

  return (
    <div className="min-h-screen bg-gray-50">
      <Sidebar open={navOpen} onClose={() => setNavOpen(false)} />

      <div className="lg:pl-60">
        <Topbar onOpenNav={() => setNavOpen(true)} onOpenProfile={() => setProfileOpen(true)} />
        <main className="mx-auto w-full max-w-[1280px] px-4 py-6 sm:px-6 lg:px-8 lg:py-8">
          <Outlet />
        </main>
      </div>

      <ProfilePage isOpen={profileOpen} onClose={() => setProfileOpen(false)} user={user} />
    </div>
  );
}
