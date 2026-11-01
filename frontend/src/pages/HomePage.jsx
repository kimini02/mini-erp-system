import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bell, FileCheck2, Home as HomeIcon, SquareKanban, Users } from 'lucide-react';
import { homeApi } from '@/api/home';
import { notificationApi } from '@/api/notification';
import { useAuthStore } from '@/store/authStore';
import { useApprovalStore } from '@/store/approvalStore';
import { getErrorMessage } from '@/lib/errors';
import { EmptyState, PageLoader } from '@/components/ui';
import homeBanner from '@/assets/home-banner.webp';
import { LEAVE_WORDS } from './home/helpers';
import ApprovalsPanel from './home/ApprovalsPanel';
import ProfileCard from './home/ProfileCard';
import MyTasksPanel from './home/MyTasksPanel';
import NoticesPanel from './home/NoticesPanel';
import TeamWeekPanel from './home/TeamWeekPanel';
import MiniCalendar from './home/MiniCalendar';

const COLLAPSE_KEY = 'home.collapsed';

function useCollapsed() {
  const [closed, setClosed] = useState(() => {
    try {
      return new Set(JSON.parse(localStorage.getItem(COLLAPSE_KEY)) || []);
    } catch {
      return new Set();
    }
  });
  const save = (next) => {
    setClosed(next);
    try {
      localStorage.setItem(COLLAPSE_KEY, JSON.stringify([...next]));
    } catch {
      // 저장하지 못해도 화면은 그대로 동작합니다.
    }
  };
  const toggle = (id) => {
    const next = new Set(closed);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    save(next);
  };
  const open = (id) => {
    if (!closed.has(id)) return;
    const next = new Set(closed);
    next.delete(id);
    save(next);
  };
  return { isClosed: (id) => closed.has(id), toggle, open };
}

/** 달력 점: 내 업무 마감(목록의 5건)과 이번 주 팀 일정 */
function buildMarks(data) {
  const map = new Map();
  const seen = new Set();
  const add = (date, item) => {
    if (!date || seen.has(item.id)) return;
    seen.add(item.id);
    if (!map.has(date)) map.set(date, []);
    map.get(date).push(item);
  };
  data.myTasks?.items.forEach((t) =>
    add(t.endDate, {
      id: `t${t.id}`,
      kind: 'task',
      label: `${t.key} ${t.title} 마감`,
      to: `/projects/${t.projectId}/board?taskId=${t.id}`,
    }),
  );
  data.teamWeek?.days.forEach((d) =>
    d.items.forEach((e) => {
      if (e.type === 'TASK_DUE') {
        add(d.date, {
          id: `t${e.task.id}`,
          kind: 'task',
          label: `${e.task.key} ${e.task.title} 마감`,
          to: `/projects/${e.project.id}/board?taskId=${e.task.id}`,
        });
      } else {
        add(d.date, {
          id: `l${d.date}${e.user.id}${e.type}`,
          kind: 'leave',
          label: `${e.user.name} ${LEAVE_WORDS[e.type]}${e.status === 'IN_PROGRESS' ? ' (결재 중)' : ''}`,
        });
      }
    }),
  );
  return map;
}

/* ───────── 홈 ───────── */

export default function HomePage() {
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const refreshCounts = useApprovalStore((s) => s.refresh);
  const collapse = useCollapsed();
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  const load = useCallback(
    () =>
      homeApi
        .get()
        .then((d) => {
          setData(d);
          setError('');
        })
        .catch((err) => setError(getErrorMessage(err))),
    [],
  );
  useEffect(() => {
    load();
  }, [load]);
  const marks = useMemo(() => (data ? buildMarks(data) : new Map()), [data]);

  const readAll = async () => {
    await notificationApi.readAll().catch(() => {});
    setData((d) => ({
      ...d,
      notifications: { unread: 0, items: d.notifications.items.map((n) => ({ ...n, read: true })) },
    }));
    refreshCounts();
  };
  const openNotice = async (n) => {
    if (!n.read) {
      await notificationApi.read(n.id).catch(() => {});
      refreshCounts();
    }
    navigate(n.link);
  };
  /** 숫자 칸에서 아래 패널로: 접혀 있으면 펼치고 그 위치로 */
  const reveal = (id) => {
    collapse.open(id);
    const smooth = !window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    requestAnimationFrame(() =>
      document.getElementById(id)?.scrollIntoView({ behavior: smooth ? 'smooth' : 'auto', block: 'start' }),
    );
  };

  if (error && !data) return <EmptyState icon={HomeIcon} title="홈을 불러오지 못했습니다" description={error} />;
  if (!data) return <PageLoader />;

  const teamLeaves = data.teamWeek
    ? data.teamWeek.days.reduce((sum, d) => sum + d.items.filter((e) => e.type !== 'TASK_DUE').length, 0)
    : null;
  const tiles = [
    data.approvals && {
      key: 'apv',
      icon: FileCheck2,
      label: '받은 결재',
      value: data.approvals.todoCount,
      strong: data.approvals.todoCount > 0,
      to: '/approvals',
    },
    data.myTasks && {
      key: 'task',
      icon: SquareKanban,
      label: '받은 업무',
      value: data.myTasks.count ?? data.myTasks.items.length,
      onClick: () => reveal('home-tasks'),
    },
    {
      key: 'ntf',
      icon: Bell,
      label: '새 알림',
      value: data.notifications.unread,
      onClick: () => reveal('home-notices'),
    },
    teamLeaves !== null && {
      key: 'team',
      icon: Users,
      label: '팀 휴가',
      value: teamLeaves,
      onClick: () => reveal('home-team'),
    },
  ].filter(Boolean);

  return (
    <div className="mx-auto flex max-w-[1280px] flex-col gap-4">
      <h1 className="sr-only">홈</h1>

      <div className="flex flex-wrap items-stretch gap-4">
        <ProfileCard user={user} initialToday={data.today} leave={data.leave} tiles={tiles} />
        {/* 배너 글씨가 잘리지 않게: 프로필 옆에 640px 이상 남을 때만 나란히, 아니면 한 줄 전체 */}
        <figure className="relative m-0 aspect-[1970/638] min-w-0 flex-[3_1_640px] overflow-hidden rounded-[14px] bg-gray-200">
          <img
            src={homeBanner}
            alt="Workflow 소개 배너: 더 빠르게 협업하고! 더 쉽게 연결하고! 업무 흐름을 한 번에 Workflow 하라!"
            className="absolute inset-0 h-full w-full object-cover"
          />
        </figure>
      </div>

      <div className="flex flex-wrap items-stretch gap-4">
        {data.approvals && <ApprovalsPanel approvals={data.approvals} collapse={collapse} />}
        {data.myTasks && <MyTasksPanel data={data.myTasks} collapse={collapse} />}
        <NoticesPanel notices={data.notifications} onReadAll={readAll} onOpen={openNotice} collapse={collapse} />
      </div>

      <div className="flex flex-wrap items-stretch gap-4">
        {data.teamWeek && <TeamWeekPanel week={data.teamWeek} collapse={collapse} />}
        <MiniCalendar marks={marks} />
      </div>
    </div>
  );
}
