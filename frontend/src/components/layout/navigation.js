import {
  LayoutDashboard,
  ClipboardList,
  CalendarDays,
  CalendarOff,
  FilePlus2,
  FileCog,
  FileCheck2,
  NotebookText,
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
 *   조직·권한·결재 메뉴는 메뉴 권한으로, 아직 다시 만들지 않은 기존 기능(프로젝트)은 기존 역할로 판단합니다.
 * - aliases: 같은 메뉴로 보는 다른 경로 (활성 표시·상단 경로 표시용)
 * - prefix: 이 경로로 시작하는 하위 화면도 같은 메뉴로 봄 (예: 결재 문서 상세)
 * - badge: 'approvals' 이면 결재 대기 건수 표시
 */
const isApprover = (ctx) => ctx.isChief || ctx.isTeamLeader;

const NAVIGATION = [
  { items: [{ label: '대시보드', path: '/dashboard', icon: LayoutDashboard }] },
  {
    title: '전자결재',
    items: [
      { label: '결재함', path: '/approvals', prefix: '/approvals/', icon: FileCheck2, badge: 'approvals', visible: (ctx) => ctx.can(MENU.APPROVAL_INBOX) },
      { label: '기안하기', path: '/approvals/new', icon: FilePlus2, visible: (ctx) => ctx.can(MENU.APPROVAL_DRAFT, 'C') },
      { label: '결재 양식', path: '/approval-forms', icon: FileCog, visible: (ctx) => ctx.can(MENU.APPROVAL_FORM) },
    ],
  },
  {
    title: '근태·휴가',
    items: [
      { label: '내 연차', path: '/leaves', icon: NotebookText },
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
