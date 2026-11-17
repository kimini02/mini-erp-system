import {
  Home,
  CalendarDays,
  CalendarOff,
  FilePlus2,
  FileCog,
  FileCheck2,
  NotebookText,
  Clock3,
  CalendarRange,
  Scale,
  FolderKanban,
  Network,
  Users,
  KeyRound,
} from 'lucide-react';
import { MENU } from '@/lib/permissions';

/**
 * 사이드바 메뉴 (경로는 역할과 상관없이 하나)
 * - visible(ctx): 이 메뉴를 보여 줄지. ctx = { can(menu, action), isChief, isTeamLeader }
 *   모든 메뉴는 메뉴 권한으로 판단합니다 (M4에서 프로젝트도 옮김).
 * - aliases: 같은 메뉴로 보는 다른 경로 (활성 표시·상단 경로 표시용)
 * - prefix: 이 경로로 시작하는 하위 화면도 같은 메뉴로 봄 (예: 결재 문서 상세)
 * - badge: 'approvals' 이면 결재 대기 건수 표시
 */
const NAVIGATION = [
  { items: [{ label: '홈', path: '/home', icon: Home, aliases: ['/dashboard'] }] },
  {
    title: '전자결재',
    items: [
      {
        label: '결재함',
        path: '/approvals',
        prefix: '/approvals/',
        icon: FileCheck2,
        badge: 'approvals',
        visible: (ctx) => ctx.can(MENU.APPROVAL_INBOX),
      },
      {
        label: '기안하기',
        path: '/approvals/new',
        icon: FilePlus2,
        visible: (ctx) => ctx.can(MENU.APPROVAL_DRAFT, 'C'),
      },
      { label: '결재 양식', path: '/approval-forms', icon: FileCog, visible: (ctx) => ctx.can(MENU.APPROVAL_FORM) },
    ],
  },
  {
    title: '근태·휴가',
    items: [
      { label: '내 근태', path: '/attendance', icon: Clock3, visible: (ctx) => ctx.can(MENU.MY_ATTENDANCE) },
      { label: '내 연차', path: '/leaves', icon: NotebookText },
      {
        label: '월간 근태표',
        path: '/attendance/monthly',
        icon: CalendarRange,
        visible: (ctx) => ctx.can(MENU.ATTENDANCE_MONTHLY),
      },
      { label: '연차 관리', path: '/leave/admin', icon: Scale, visible: (ctx) => ctx.can(MENU.LEAVE_ADMIN) },
      { label: '캘린더', path: '/calendar', icon: CalendarDays },
      { label: '공휴일', path: '/holidays', icon: CalendarOff, visible: (ctx) => ctx.can(MENU.HOLIDAY) },
    ],
  },
  {
    title: '업무',
    items: [
      {
        label: '프로젝트',
        path: '/projects',
        prefix: '/projects/',
        icon: FolderKanban,
        visible: (ctx) => ctx.can(MENU.PROJECT),
      },
    ],
  },
  {
    title: '조직 관리',
    items: [
      { label: '부서 관리', path: '/org/departments', icon: Network },
      { label: '사원 관리', path: '/org/employees', icon: Users, visible: (ctx) => ctx.can(MENU.EMPLOYEE) },
      { label: '역할·권한', path: '/org/roles', icon: KeyRound, visible: (ctx) => ctx.can(MENU.ROLE) },
    ],
  },
];

/** 권한에 맞는 메뉴만 남깁니다. 비어 있는 그룹은 뺍니다. */
export function getNavigation(ctx) {
  return NAVIGATION.map((group) => ({
    ...group,
    items: group.items.filter((item) => !item.visible || item.visible(ctx)),
  })).filter((group) => group.items.length > 0);
}

/** 현재 경로에 해당하는 메뉴 항목과 그룹 제목 */
export function findActiveItem(groups, pathname) {
  // 정확히 맞는 경로가 먼저 (예: /approvals/new 는 '기안하기'), 없으면 prefix 로 (예: /approvals/142 는 '결재함')
  for (const group of groups) {
    for (const item of group.items) {
      if (item.path === pathname || item.aliases?.includes(pathname)) {
        return { group, item };
      }
    }
  }
  for (const group of groups) {
    for (const item of group.items) {
      if (item.prefix && pathname.startsWith(item.prefix)) return { group, item };
    }
  }
  return null;
}
