import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Bell, CheckCheck, ChevronDown, ChevronRight, KeyRound, LogOut, Menu, UserRound, Inbox } from 'lucide-react';
import { notificationApi } from '@/api/m2';
import { useAuthStore } from '@/store/authStore';
import { useApprovalStore } from '@/store/approvalStore';
import { getRoleInfo, getDisplayName } from '@/lib/roles';
import { MENU } from '@/lib/permissions';
import { NOTIFICATION_TYPE_META, formatDateTime } from '@/lib/approval';
import { confirm } from '@/lib/confirm';
import { cn } from '@/lib/cn';
import { Avatar } from '@/components/ui';
import { findActiveItem } from './navigation';
import { useNavGroups } from './useNavGroups';

/** 바깥 클릭 시 닫히는 드롭다운 상태 */
function useDropdown() {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    if (!open) return undefined;
    const onDown = (e) => ref.current && !ref.current.contains(e.target) && setOpen(false);
    const onKey = (e) => e.key === 'Escape' && setOpen(false);
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  return { open, setOpen, ref };
}

const panelClass =
  'absolute right-0 top-full z-50 mt-2 overflow-hidden rounded-xl border border-gray-200 bg-white shadow-overlay';

/** 앱 안 알림 (명세 13번 6장) */
function NotificationMenu() {
  const navigate = useNavigate();
  const canInbox = useAuthStore((s) => s.can(MENU.APPROVAL_INBOX));
  const unread = useApprovalStore((s) => s.unread);
  const todo = useApprovalStore((s) => s.counts.todo || 0);
  const refresh = useApprovalStore((s) => s.refresh);
  const { open, setOpen, ref } = useDropdown();
  const [items, setItems] = useState(null);

  useEffect(() => {
    if (!open) return undefined;
    let alive = true;
    notificationApi
      .list({ page: 0, size: 10 })
      .then((r) => alive && setItems(r.content || []))
      .catch(() => alive && setItems([]));
    return () => {
      alive = false;
    };
  }, [open]);

  const openItem = async (n) => {
    setOpen(false);
    if (!n.read) {
      await notificationApi.read(n.id).catch(() => {});
      refresh();
    }
    navigate(n.link);
  };

  const readAll = async () => {
    await notificationApi.readAll().catch(() => {});
    setItems((list) => list?.map((n) => ({ ...n, read: true })));
    refresh();
  };

  const goApprovals = () => {
    setOpen(false);
    navigate('/approvals');
  };

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className={cn(
          'relative flex h-9 w-9 items-center justify-center rounded-lg text-gray-500 transition-colors hover:bg-gray-100 hover:text-gray-700',
          open && 'bg-gray-100 text-gray-700',
        )}
        aria-label="알림"
      >
        <Bell size={18} />
        {unread > 0 && (
          <span className="absolute right-1 top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-red-500 px-1 text-[10px] font-semibold tabular-nums text-white ring-2 ring-white">
            {unread > 9 ? '9+' : unread}
          </span>
        )}
      </button>

      {open && (
        <div className={cn(panelClass, 'w-80')}>
          <div className="flex items-center justify-between border-b border-gray-100 px-4 py-3">
            <p className="text-sm font-semibold text-gray-900">알림</p>
            {unread > 0 && (
              <button
                type="button"
                onClick={readAll}
                className="flex items-center gap-1 text-xs text-gray-500 hover:text-gray-800"
              >
                <CheckCheck size={14} /> 모두 읽음
              </button>
            )}
          </div>

          {items === null ? (
            <p className="px-4 py-8 text-center text-[13px] text-gray-400">불러오는 중…</p>
          ) : items.length === 0 ? (
            <div className="flex flex-col items-center gap-2 px-4 py-10 text-center">
              <Inbox size={20} className="text-gray-300" />
              <p className="text-[13px] text-gray-500">새로운 알림이 없습니다.</p>
            </div>
          ) : (
            <ul className="max-h-80 overflow-y-auto py-1">
              {items.map((n) => {
                const meta = NOTIFICATION_TYPE_META[n.type] || { label: '알림', dot: 'bg-gray-400' };
                return (
                  <li key={n.id}>
                    <button
                      type="button"
                      onClick={() => openItem(n)}
                      className={cn(
                        'flex w-full items-start gap-3 px-4 py-2.5 text-left hover:bg-gray-50',
                        !n.read && 'bg-blue-50/40',
                      )}
                    >
                      <span className={cn('mt-1.5 h-2 w-2 shrink-0 rounded-full', n.read ? 'bg-gray-200' : meta.dot)} />
                      <span className="min-w-0 flex-1">
                        <span
                          className={cn('block text-[13px]', n.read ? 'text-gray-600' : 'font-medium text-gray-900')}
                        >
                          {meta.sentence ? meta.sentence(n.title) : n.title}
                        </span>
                        <span className="block text-xs text-gray-500">
                          {meta.label} · {formatDateTime(n.createdAt)}
                        </span>
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}

          {canInbox && (
            <button
              type="button"
              onClick={goApprovals}
              className="flex w-full items-center justify-center gap-1 border-t border-gray-100 py-2.5 text-[13px] font-medium text-gray-600 hover:bg-gray-50 hover:text-gray-900"
            >
              결재함 바로가기{todo > 0 && ` · 결재 대기 ${todo}건`} <ChevronRight size={14} />
            </button>
          )}
        </div>
      )}
    </div>
  );
}

function UserMenu({ onOpenProfile }) {
  const navigate = useNavigate();
  const { user, logout } = useAuthStore();
  const { roleLabel } = getRoleInfo(user);
  const { open, setOpen, ref } = useDropdown();
  const name = getDisplayName(user);

  const handleLogout = async () => {
    setOpen(false);
    const ok = await confirm({ title: '로그아웃 하시겠습니까?', confirmText: '로그아웃' });
    if (ok) logout();
  };

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className={cn(
          'flex h-9 items-center gap-2 rounded-lg pl-1 pr-2 transition-colors hover:bg-gray-100',
          open && 'bg-gray-100',
        )}
      >
        <Avatar name={name} size="sm" />
        <span className="hidden text-sm font-medium text-gray-800 sm:block">{name}</span>
        <ChevronDown size={14} className="text-gray-400" />
      </button>

      {open && (
        <div className={cn(panelClass, 'w-60')}>
          <div className="border-b border-gray-100 px-4 py-3">
            <p className="text-sm font-semibold text-gray-900">{name}</p>
            <p className="mt-0.5 text-xs text-gray-500">
              {[user?.department?.name || user?.departmentName, user?.position || roleLabel]
                .filter(Boolean)
                .join(' · ')}
            </p>
            {user?.email && <p className="mt-0.5 truncate text-xs text-gray-400">{user.email}</p>}
          </div>
          <div className="py-1">
            <button
              type="button"
              onClick={() => {
                setOpen(false);
                onOpenProfile();
              }}
              className="flex w-full items-center gap-2.5 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50"
            >
              <UserRound size={16} className="text-gray-400" /> 내 프로필
            </button>
            <button
              type="button"
              onClick={() => {
                setOpen(false);
                navigate('/me/password');
              }}
              className="flex w-full items-center gap-2.5 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50"
            >
              <KeyRound size={16} className="text-gray-400" /> 비밀번호 변경
            </button>
            <button
              type="button"
              onClick={handleLogout}
              className="flex w-full items-center gap-2.5 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50"
            >
              <LogOut size={16} className="text-gray-400" /> 로그아웃
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

const formatToday = () =>
  new Intl.DateTimeFormat('ko-KR', { month: 'long', day: 'numeric', weekday: 'short' }).format(new Date());

export default function Topbar({ onOpenNav, onOpenProfile }) {
  const { pathname } = useLocation();
  const active = findActiveItem(useNavGroups(), pathname);

  return (
    <header className="sticky top-0 z-20 flex h-14 items-center gap-3 border-b border-gray-200 bg-white px-4 sm:px-6 lg:px-8">
      <button
        type="button"
        onClick={onOpenNav}
        className="-ml-1 flex h-9 w-9 items-center justify-center rounded-lg text-gray-500 hover:bg-gray-100 lg:hidden"
        aria-label="메뉴 열기"
      >
        <Menu size={20} />
      </button>

      {active && (
        <nav className="flex min-w-0 items-center gap-1.5 text-sm">
          {active.group.title && (
            <>
              <span className="hidden text-gray-500 sm:inline">{active.group.title}</span>
              <ChevronRight size={14} className="hidden text-gray-300 sm:block" />
            </>
          )}
          <span className="truncate font-medium text-gray-900">{active.item.label}</span>
        </nav>
      )}

      <div className="ml-auto flex items-center gap-1">
        <span className="mr-2 hidden text-[13px] text-gray-500 md:inline">{formatToday()}</span>
        <NotificationMenu />
        <span className="mx-1.5 h-5 w-px bg-gray-200" />
        <UserMenu onOpenProfile={onOpenProfile} />
      </div>
    </header>
  );
}
