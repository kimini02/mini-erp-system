import {
  LayoutDashboard,
  ClipboardList,
  CalendarDays,
  FilePlus2,
  FileText,
  FileCheck2,
  FolderKanban,
  ShieldCheck,
  ListTodo,
} from 'lucide-react';

/**
 * 권한별 사이드바 메뉴
 * - aliases: 같은 메뉴로 간주할 다른 경로 (활성 표시·상단 경로 표시용)
 * - badge: 'approvals' 이면 결재 대기 건수 표시
 */
const NAVIGATION = {
  admin: [
    { items: [{ label: '대시보드', path: '/admin/dashboard', icon: LayoutDashboard }] },
    {
      title: '결재',
      items: [
        { label: '결재함', path: '/admin/approvals', icon: FileCheck2, badge: 'approvals', aliases: ['/admin/leave-approval'] },
      ],
    },
    {
      title: '업무',
      items: [
        { label: '프로젝트 관리', path: '/admin/projects', icon: FolderKanban, aliases: ['/admin/task-create'] },
        { label: '업무 배정', path: '/admin/task-edit', icon: ListTodo },
      ],
    },
    {
      title: '관리',
      items: [{ label: '프로젝트 권한', path: '/admin/project-auth', icon: ShieldCheck }],
    },
  ],

  manager: [
    { items: [{ label: '대시보드', path: '/manager/dashboard', icon: LayoutDashboard }] },
    {
      title: '결재',
      items: [
        { label: '결재함', path: '/manager/approvals', icon: FileCheck2, badge: 'approvals', aliases: ['/manager/leave-approval'] },
      ],
    },
    {
      title: '업무',
      items: [
        { label: '프로젝트 관리', path: '/manager/projects', icon: FolderKanban },
        { label: '업무 배정', path: '/manager/task-edit', icon: ListTodo },
      ],
    },
    {
      title: '근태·휴가',
      items: [
        { label: '연차/특근 신청', path: '/manager/leaves/new', icon: FilePlus2 },
        { label: '신청 내역', path: '/manager/leaves', icon: FileText },
      ],
    },
  ],

  user: [
    { items: [{ label: '대시보드', path: '/user/dashboard', icon: LayoutDashboard }] },
    {
      title: '업무',
      items: [
        { label: '내 업무', path: '/user/projects', icon: ClipboardList },
        { label: '캘린더', path: '/user/calendar', icon: CalendarDays },
      ],
    },
    {
      title: '근태·휴가',
      items: [
        { label: '연차/특근 신청', path: '/user/leaves/new', icon: FilePlus2 },
        { label: '신청 내역', path: '/user/leaves', icon: FileText },
      ],
    },
  ],
};

export const getNavigation = (routeRole) => NAVIGATION[routeRole] || NAVIGATION.user;

/** 현재 경로에 해당하는 메뉴 항목과 그룹 제목 */
export function findActiveItem(groups, pathname) {
  for (const group of groups) {
    for (const item of group.items) {
      if (item.path === pathname || item.aliases?.includes(pathname)) {
        return { group, item };
      }
    }
  }
  return null;
}
