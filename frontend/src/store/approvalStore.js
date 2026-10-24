import { create } from 'zustand';
import { approvalInboxApi, notificationApi } from '../api/m2';
import { MENU } from '../lib/permissions';
import { useAuthStore } from './authStore';

const valueOf = (result, fallback) => (result.status === 'fulfilled' && result.value ? result.value : fallback);

/**
 * 결재함 건수 · 결재 대기 목록 · 안 읽은 알림 수 (사이드바 배지, 상단 알림, 대시보드가 함께 씀)
 * 결재 처리·기안처럼 건수가 바뀌는 동작 뒤에는 refresh()를 불러 주세요.
 */
export const useApprovalStore = create((set) => ({
  counts: {},
  todoItems: [],
  unread: 0,

  refresh: async () => {
    const canInbox = useAuthStore.getState().can(MENU.APPROVAL_INBOX);
    const [counts, todo, unread] = await Promise.allSettled([
      canInbox ? approvalInboxApi.counts() : Promise.resolve({}),
      canInbox ? approvalInboxApi.list({ box: 'todo', page: 0, size: 5 }) : Promise.resolve({ content: [] }),
      notificationApi.unreadCount(),
    ]);
    set({
      counts: valueOf(counts, {}),
      todoItems: valueOf(todo, { content: [] }).content || [],
      unread: valueOf(unread, { count: 0 }).count ?? 0,
    });
  },
}));
