import { useState } from 'react';
import { Link } from 'react-router-dom';
import { FileCheck2 } from 'lucide-react';
import { cn } from '@/lib/cn';
import { whenText } from './helpers';
import Panel from './Panel';

/** 결재 양식 표시 (색을 채우고 흰 글씨) */
const FORM_TAG = {
  LEAVE: 'bg-blue-600',
  OVERTIME: 'bg-violet-600',
  ATTENDANCE_FIX: 'bg-amber-700',
};

/* ───────── 패널 접기 (이 브라우저에만 기억) ───────── */

export default function ApprovalsPanel({ approvals, collapse }) {
  const [tab, setTab] = useState(() => (approvals.todoCount > 0 || approvals.mine.length === 0 ? 'todo' : 'mine'));
  const items = tab === 'todo' ? approvals.todo : approvals.mine;
  const tabs = [
    { key: 'todo', label: '결재 대기', count: approvals.todoCount },
    { key: 'mine', label: '내가 올린', count: approvals.mine.length },
  ];
  return (
    <Panel
      id="home-approvals"
      icon={FileCheck2}
      tint="bg-blue-50 text-blue-700"
      title="전자결재"
      link={{ to: tab === 'todo' ? '/approvals' : '/approvals?box=mine', label: '결재함 열기' }}
      collapse={collapse}
      className="flex-[1_1_320px]"
    >
      <div className="px-4 pb-1 pt-3">
        <div role="tablist" aria-label="결재 목록" className="inline-flex gap-0.5 rounded-[10px] bg-gray-100 p-[3px]">
          {tabs.map((t) => (
            <button
              key={t.key}
              type="button"
              role="tab"
              aria-selected={tab === t.key}
              onClick={() => setTab(t.key)}
              className={cn(
                'h-8 rounded-lg px-3 text-[13px]',
                tab === t.key
                  ? 'bg-white font-semibold text-gray-900 shadow-sm'
                  : 'font-medium text-gray-600 hover:text-gray-900',
              )}
            >
              {t.label}{' '}
              <span className={cn('tabular-nums', tab === t.key && t.key === 'todo' && t.count > 0 && 'text-blue-700')}>
                {t.count}
              </span>
            </button>
          ))}
        </div>
      </div>
      {items.length === 0 ? (
        <p className="px-4 py-8 text-center text-[13px] text-gray-500">
          {tab === 'todo' ? '결재할 문서가 없습니다.' : '진행 중인 문서가 없습니다.'}
        </p>
      ) : (
        <ul className="pb-1">
          {items.map((d, i) => (
            <li key={d.id} className={cn(i > 0 && 'border-t border-gray-100')}>
              <Link
                to={`/approvals/${d.id}`}
                className="flex items-start gap-2.5 px-4 py-3 text-gray-900 hover:bg-gray-50"
              >
                <span
                  className={cn(
                    'mt-px shrink-0 rounded-md px-2 py-px text-xs font-medium text-white',
                    FORM_TAG[d.form.code] || 'bg-gray-600',
                  )}
                >
                  {d.form.shortName}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-medium">{d.title}</span>
                  <span className="block truncate text-xs text-gray-500">
                    {tab === 'todo'
                      ? `${d.docNo} · ${d.drafter.name} ${d.drafter.title}`
                      : `${d.docNo} · ${d.currentApprover ? `${d.currentApprover.name} ${d.currentApprover.title} 차례` : '결재 중'}`}
                  </span>
                </span>
                <span className="shrink-0 text-xs text-gray-500">{whenText(d.submittedAt)}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  );
}

/* ───────── 내 업무 ───────── */
