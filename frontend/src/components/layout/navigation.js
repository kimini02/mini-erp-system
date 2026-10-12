import {
  LayoutDashboard,
  ClipboardList,
  CalendarDays,
  CalendarOff,
  FilePlus2,
  FileText,
  FileCheck2,
  FolderKanban,
  ShieldCheck,
  ListTodo,
  Network,
  Users,
  KeyRound,
} from 'lucide-react';
import { MENU } from '@/lib/permissions';

/**
 * 사이드바 메뉴 (경로는 역할과 상관없이 하나)
 * - visible(ctx): 이 메뉴를 보여 줄지. ctx = { can(menu, action), isChief, isTeamLeader }
 *   조직·권한 메뉴는 메뉴 권한으로, 아직 다시 만들지 않은 기존 기능(연차 결재·프로젝트)은 기존 역할로 판단합니다.
 * - aliases: 같은 메뉴로 보는 다른 경로 (활성 표시·상단 경로 표시용)
 * - badge: 'approvals' 이면 결재 대기 건수 표시
 */
const isApprover = (ctx) => ctx.isChief || ctx.isTeamLeader;

const NAVIGATION = [
  { items: [{ label: '대시보드', path: '/dashboard', icon: LayoutDashboard }] },
  {
    title: '결재',
    items: [
      { label: '결재함', path: '/approvals', icon: FileCheck2, badge: 'approvals', visible: isApprover },
    ],
  },
  {
    title: '근태·휴가',
    items: [
      { label: '연차/특근 신청', path: '/leaves/new', icon: FilePlus2 },
      { label: '신청 내역', path: '/leaves', icon: FileText },
      { label: '캘린더', path: '/calendar', icon: CalendarDays },
      { label: '공휴일', path: '/holidays', icon: CalendarOff, visible: (ctx) => ctx.can(MENU.HOLIDAY) },
    ],
  },
  {
    title: '업무',
    items: [
      { label: '프로젝트 관리', path: '/projects', icon: FolderKanban, aliases: ['/projects/new'], visible: isApprover },
      { label: '업무 배정', path: '/tasks/assign', icon: ListTodo, visible: isApprover },
      { label: '내 업무', path: '/my-tasks', icon: ClipboardList, visible: (ctx) => !isApprover(ctx) },
    ],
  },
  {
    title: '조직 관리',
    items: [
      { label: '부서 관리', path: '/org/departments', icon: Network, visible: (ctx) => ctx.can(MENU.DEPARTMENT) },
      { label: '사원 관리', path: '/org/employees', icon: Users, visible: (ctx) => ctx.can(MENU.EMPLOYEE) },
      { label: '역할·권한', path: '/org/roles', icon: KeyRound, visible: (ctx) => ctx.can(MENU.ROLE) },
      { label: '프로젝트 권한', path: '/project-auth', icon: ShieldCheck, visible: (ctx) => ctx.isChief },
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
  for (const group of groups) {
    for (const item of group.items) {
      if (item.path === pathname || item.aliases?.includes(pathname)) {
        return { group, item };
      }
    }
  }
  return null;
}
