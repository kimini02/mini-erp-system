import { Link } from 'react-router-dom';
import { Users } from 'lucide-react';
import { cn } from '@/lib/cn';
import { WEEKDAYS, md, todayStr, LEAVE_WORDS } from './helpers';
import Panel from './Panel';

export default function TeamWeekPanel({ week, collapse }) {
  const today = todayStr();
  return (
    <Panel
      id="home-team"
      icon={Users}
      tint="bg-emerald-50 text-emerald-700"
      title={`이번 주 ${week.department?.name || '우리 팀'}`}
      extra={
        <span className="text-xs tabular-nums text-gray-500">
          {md(week.from)} ~ {md(week.to)}
        </span>
      }
      collapse={collapse}
      className="flex-[999_1_560px]"
    >
      <div className="flex flex-1 flex-wrap gap-2 px-4 pb-4 pt-3.5">
        {week.days.map((d) => {
          const isToday = d.date === today;
          return (
            <div
              key={d.date}
              className={cn(
                'flex min-h-[116px] flex-[1_1_120px] flex-col gap-1.5 rounded-[10px] border p-2.5',
                isToday ? 'border-blue-200 bg-blue-50/70' : 'border-gray-100',
              )}
            >
              <p className={cn('text-xs', isToday ? 'font-bold text-blue-700' : 'font-semibold text-gray-700')}>
                {WEEKDAYS[new Date(`${d.date}T00:00:00`).getDay()]} {Number(d.date.slice(8, 10))}
                {isToday && ' · 오늘'}
              </p>
              {d.items.length === 0 && <span className="text-xs text-gray-500">모두 근무</span>}
              {d.items.map((e) =>
                e.type === 'TASK_DUE' ? (
                  <Link
                    key={`t${e.task.id}`}
                    to={`/projects/${e.project.id}/board?taskId=${e.task.id}`}
                    className={cn(
                      'rounded-md px-1.5 py-1 text-xs leading-snug text-gray-700 hover:bg-gray-200',
                      isToday ? 'border border-gray-200 bg-white' : 'bg-gray-100',
                    )}
                  >
                    {e.task.key} 마감
                  </Link>
                ) : (
                  <span
                    key={`l${e.user.id}${e.type}`}
                    className={cn(
                      'rounded-md px-1.5 py-1 text-xs leading-snug',
                      e.status === 'IN_PROGRESS'
                        ? 'border border-dashed border-blue-300 bg-white text-blue-700'
                        : 'bg-blue-100 text-blue-800',
                    )}
                  >
                    {e.user.name} {LEAVE_WORDS[e.type]}
                    {e.status === 'IN_PROGRESS' && ' (결재 중)'}
                  </span>
                ),
              )}
            </div>
          );
        })}
      </div>
    </Panel>
  );
}

/* ───────── 달력 ───────── */
