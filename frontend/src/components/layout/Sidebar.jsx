import { Link, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import { useApprovalStore } from '@/store/approvalStore';
import { getRoleInfo } from '@/lib/roles';
import { cn } from '@/lib/cn';
import Logo from './Logo';
import { findActiveItem } from './navigation';
import { useNavGroups } from './useNavGroups';

export default function Sidebar({ open, onClose }) {
  const { pathname } = useLocation();
  const { user } = useAuthStore();
  const pendingCount = useApprovalStore((s) => s.counts.todo || 0);
  const { workspace, homePath } = getRoleInfo(user);

  const groups = useNavGroups();
  const active = findActiveItem(groups, pathname)?.item;

  return (
    <>
      {open && <div className="fixed inset-0 z-30 bg-gray-900/30 lg:hidden" onClick={onClose} />}

      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-40 flex w-60 flex-col border-r border-gray-200 bg-white transition-transform duration-200 lg:translate-x-0',
          open ? 'translate-x-0' : '-translate-x-full'
        )}
      >
        <div className="flex h-14 shrink-0 items-center border-b border-gray-200 px-5">
          <Link to={homePath} onClick={onClose}>
            <Logo />
          </Link>
          <span className="ml-auto rounded bg-gray-100 px-1.5 py-0.5 text-xs font-medium text-gray-500">
            {workspace}
          </span>
        </div>

        <nav className="flex-1 space-y-5 overflow-y-auto px-3 py-4">
          {groups.map((group, index) => (
            <div key={group.title || index}>
              {group.title && <p className="mb-1 px-3 text-xs font-medium text-gray-400">{group.title}</p>}
              <ul className="space-y-0.5">
                {group.items.map((item) => {
                  const isActive = item === active;
                  const Icon = item.icon;
                  const count = item.badge === 'approvals' ? pendingCount : 0;
                  return (
                    <li key={item.path}>
                      <Link
                        to={item.path}
                        onClick={onClose}
                        className={cn(
                          'flex h-9 items-center gap-2.5 rounded-md px-3 text-sm transition-colors',
                          isActive
                            ? 'bg-blue-50 font-medium text-blue-700'
                            : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'
                        )}
                      >
                        <Icon size={18} strokeWidth={isActive ? 2 : 1.75} className={isActive ? 'text-blue-600' : 'text-gray-400'} />
                        <span className="flex-1">{item.label}</span>
                        {count > 0 && (
                          <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-red-500 px-1.5 text-xs font-medium tabular-nums text-white">
                            {count}
                          </span>
                        )}
                      </Link>
                    </li>
                  );
                })}
              </ul>
            </div>
          ))}
        </nav>
      </aside>
    </>
  );
}
