import { create } from 'zustand';

/**
 * 화면 우측 상단 알림(토스트)
 * 사용: toast.success('저장되었습니다.') / toast.error('실패했습니다.') / toast.info('...')
 */
let seq = 0;

export const useToastStore = create((set) => ({
  toasts: [],
  push: (type, message) => {
    const id = ++seq;
    set((state) => ({ toasts: [...state.toasts, { id, type, message }] }));
    setTimeout(() => {
      set((state) => ({ toasts: state.toasts.filter((t) => t.id !== id) }));
    }, 3200);
  },
  dismiss: (id) => set((state) => ({ toasts: state.toasts.filter((t) => t.id !== id) })),
}));

export const toast = {
  success: (message) => useToastStore.getState().push('success', message),
  error: (message) => useToastStore.getState().push('error', message),
  info: (message) => useToastStore.getState().push('info', message),
};
