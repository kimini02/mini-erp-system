import { create } from 'zustand';

/**
 * window.confirm 대체 확인 창
 * 사용: if (!(await confirm({ title: '승인하시겠습니까?' }))) return;
 * 옵션: title, description, confirmText, cancelText, tone('primary' | 'danger')
 */
export const useConfirmStore = create((set, get) => ({
  request: null,
  open: (options) =>
    new Promise((resolve) => {
      set({ request: { ...options, resolve } });
    }),
  close: (result) => {
    get().request?.resolve(result);
    set({ request: null });
  },
}));

export const confirm = (options) => useConfirmStore.getState().open(options);
