import { create } from 'zustand';
import api from '../api/axios';
import { getRoleInfo } from '../lib/roles';

/**
 * 결재 대기함 (상단 알림 + 대시보드에서 공유)
 * - 관리소장: 팀장(TEAM_LEADER)이 올린 대기 건
 * - 팀장: 사원(USER)이 올린 대기 건
 */
export const useApprovalStore = create((set) => ({
  leave: [],
  overtime: [],
  loading: false,

  fetchPending: async (user) => {
    const { isChief, isTeamLeader } = getRoleInfo(user);
    if (!isChief && !isTeamLeader) {
      set({ leave: [], overtime: [] });
      return;
    }

    const targetRole = isChief ? 'TEAM_LEADER' : 'USER';
    set({ loading: true });

    const [leaveRes, overtimeRes] = await Promise.allSettled([
      api.get('/leave/all'),
      api.get('/overtime/all'),
    ]);

    const pick = (res, statusKey) =>
      res.status === 'fulfilled' && res.value?.data?.success
        ? (res.value.data.data || []).filter(
            (req) => (req[statusKey] ?? req.appStatus) === 'PENDING' && req.userRole === targetRole
          )
        : [];

    set({
      leave: pick(leaveRes, 'appStatus'),
      overtime: pick(overtimeRes, 'status'), // 특근 DTO는 status 필드 사용
      loading: false,
    });
  },
}));
