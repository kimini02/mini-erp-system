import { CheckCircle2, AlertCircle, Info, X } from 'lucide-react';
import { useToastStore } from '@/lib/toast';
import { useConfirmStore } from '@/lib/confirm';
import Modal from './Modal';
import Button from './Button';

const toastIcons = {
  success: <CheckCircle2 size={18} className="text-emerald-500" />,
  error: <AlertCircle size={18} className="text-red-500" />,
  info: <Info size={18} className="text-blue-500" />,
};

/** 앱 루트에 한 번 배치 */
export function Toaster() {
  const { toasts, dismiss } = useToastStore();

  return (
    <div className="pointer-events-none fixed right-6 top-[68px] z-[60] flex w-[340px] flex-col gap-2">
      {toasts.map((t) => (
        <div
          key={t.id}
          role="status"
          className="pointer-events-auto flex items-start gap-3 rounded-lg border border-gray-200 bg-white px-4 py-3 shadow-overlay"
        >
          <span className="mt-px">{toastIcons[t.type]}</span>
          <p className="flex-1 text-sm text-gray-800">{t.message}</p>
          <button type="button" onClick={() => dismiss(t.id)} className="text-gray-400 hover:text-gray-600" aria-label="닫기">
            <X size={16} />
          </button>
        </div>
      ))}
    </div>
  );
}

/** 앱 루트에 한 번 배치 */
export function ConfirmDialog() {
  const { request, close } = useConfirmStore();
  if (!request) return null;

  const danger = request.tone === 'danger';

  return (
    <Modal
      open
      size="sm"
      onClose={() => close(false)}
      footer={
        <>
          <Button variant="secondary" onClick={() => close(false)}>
            {request.cancelText || '취소'}
          </Button>
          <Button variant={danger ? 'danger' : 'primary'} onClick={() => close(true)} autoFocus>
            {request.confirmText || '확인'}
          </Button>
        </>
      }
    >
      <div className="pt-2">
        <h2 className="text-base font-semibold text-gray-900">{request.title}</h2>
        {request.description && <p className="mt-1.5 text-sm text-gray-500">{request.description}</p>}
      </div>
    </Modal>
  );
}
