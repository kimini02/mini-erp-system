import { Link } from 'react-router-dom';
import { SquareKanban } from 'lucide-react';
import { TASK_STATUS_META, dueText } from '@/lib/project';
import { cn } from '@/lib/cn';
import { Progress } from '@/components/ui';
import Panel from './Panel';

export default function MyTasksPanel({ data, collapse }) {
  const { project, items } = data;
  return (
    <Panel
      id="home-tasks"
      icon={SquareKanban}
      tint="bg-violet-50 text-violet-700"
      title="내 업무"
      link={{
        to: project ? `/projects/${project.id}/board` : '/projects',
        label: project ? '보드 열기' : '프로젝트 열기',
      }}
      collapse={collapse}
      className="flex-[1_1_320px]"
    >
      {project && (
        <div className="px-4 pb-1.5 pt-3.5">
          <div className="flex justify-between gap-2 text-[13px]">
            <span className="truncate text-gray-600">{project.title}</span>
            <span className="font-bold tabular-nums text-gray-900">{project.progressPct}%</span>
          </div>
          <Progress value={project.progressPct} className="mt-1.5" />
          <p className="mt-1.5 text-xs text-gray-500">
            완료 {project.done} / 전체 {project.total}
            {project.overdue > 0 && (
              <>
                {' '}
                · <span className="font-semibold text-red-600">지연 {project.overdue}건</span>
              </>
            )}
          </p>
        </div>
      )}
      {items.length === 0 ? (
        <p className="px-4 py-8 text-center text-[13px] text-gray-500">맡은 업무가 없습니다.</p>
      ) : (
        <ul className="pb-1">
          {items.map((t) => {
            const due = dueText(t);
            const meta = TASK_STATUS_META[t.status];
            return (
              <li key={t.id} className="border-t border-gray-100">
                <Link
                  to={`/projects/${t.projectId}/board?taskId=${t.id}`}
                  className="flex min-h-12 items-center gap-2.5 px-4 text-gray-900 hover:bg-gray-50"
                >
                  <span className="w-11 shrink-0 text-xs tabular-nums text-gray-500">{t.key}</span>
                  <span className="min-w-0 flex-1 truncate font-medium">{t.title}</span>
                  <span
                    className={cn('shrink-0 whitespace-nowrap rounded-md px-2 py-px text-xs font-medium', meta.chip)}
                  >
                    {meta.label}
                  </span>
                  <span
                    className={cn(
                      'w-16 shrink-0 whitespace-nowrap text-right text-xs',
                      due.late ? 'font-semibold text-red-600' : 'text-gray-500',
                    )}
                  >
                    {due.text}
                  </span>
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </Panel>
  );
}

/* ───────── 알림 ───────── */
