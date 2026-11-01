import { Bell, CheckCheck } from 'lucide-react';
import { NOTIFICATION_TYPE_META } from '@/lib/approval';
import { cn } from '@/lib/cn';
import { whenText } from './helpers';
import Panel from './Panel';

export default function NoticesPanel({ notices, onReadAll, onOpen, collapse }) {
  return (
    <Panel
      id="home-notices"
      icon={Bell}
      tint="bg-rose-50 text-rose-600"
      title="알림"
      extra={notices.unread > 0 && <span className="font-bold tabular-nums text-red-600">{notices.unread}</span>}
      actions={
        notices.unread > 0 && (
          <button
            type="button"
            onClick={onReadAll}
            className="flex h-9 items-center gap-1 rounded-lg px-2.5 text-[13px] font-semibold text-blue-700 hover:bg-blue-50"
          >
            <CheckCheck size={14} aria-hidden="true" /> 모두 읽음
          </button>
        )
      }
      collapse={collapse}
      className="flex-[1_1_320px]"
    >
      {notices.items.length === 0 ? (
        <p className="px-4 py-8 text-center text-[13px] text-gray-500">새로운 알림이 없습니다.</p>
      ) : (
        <ul className="py-1">
          {notices.items.map((n) => {
            const meta = NOTIFICATION_TYPE_META[n.type] || { dot: 'bg-gray-400' };
            return (
              <li key={n.id}>
                <button
                  type="button"
                  onClick={() => onOpen(n)}
                  className="flex w-full items-start gap-2.5 px-4 py-2.5 text-left hover:bg-gray-50"
                >
                  <span className={cn('mt-[7px] h-2 w-2 shrink-0 rounded-full', n.read ? 'bg-gray-200' : meta.dot)} />
                  <span className="min-w-0 flex-1 leading-snug">
                    <span className={cn('block text-[13px]', n.read ? 'text-gray-600' : 'font-medium text-gray-900')}>
                      {meta.sentence ? meta.sentence(n.title) : n.title}
                    </span>
                    <span className="text-xs text-gray-500">{whenText(n.createdAt)}</span>
                  </span>
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </Panel>
  );
}

/* ───────── 이번 주 팀 ───────── */
