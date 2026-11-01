import { useEffect, useState } from 'react';
import { Scale } from 'lucide-react';
import { leaveAdminApi } from '@/api/leave';
import { formatDays, formatSignedDays } from '@/lib/approval';
import { getErrorMessage } from '@/lib/errors';
import { toast } from '@/lib/toast';
import { cn } from '@/lib/cn';
import { Button, Field, Input, Modal, Select } from '@/components/ui';

/** 수동 조정 (ADJUST 행) */
export default function LeaveAdjustModal({ row, year, onClose, onDone }) {
  const [sign, setSign] = useState('-');
  const [amount, setAmount] = useState('1');
  const [memo, setMemo] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    setSign('-');
    setAmount('1');
    setMemo('');
    setError('');
  }, [row]);

  if (!row) return null;
  const n = Number(amount);
  const days = sign === '-' ? -n : n;
  const amountOk = n > 0 && Math.round(n * 2) === n * 2;
  const valid = amountOk && memo.trim();
  // 미리보기는 일수만 맞으면 바로 보여 줌 (사유는 저장할 때만 필요)
  const after = Math.round((row.available + (amountOk ? days : 0)) * 10) / 10;

  const save = async () => {
    setSaving(true);
    setError('');
    try {
      const updated = await leaveAdminApi.adjust({ userId: row.user.id, year, days, memo: memo.trim() });
      toast.success(
        `${row.user.name}님의 연차를 ${formatSignedDays(days)}일 조정했습니다. 사용 가능 ${formatDays(updated.available)}`,
      );
      onDone();
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      title={`${row.user.name}의 연차 조정`}
      description="원장에 조정 기록을 한 줄 더합니다. 이미 있는 기록은 고치지 않습니다."
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            취소
          </Button>
          <Button icon={Scale} loading={saving} disabled={!valid || after < 0} onClick={save}>
            조정
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <div className="grid grid-cols-[120px_1fr] gap-3">
          <Field label="구분" htmlFor="adj-sign">
            <Select id="adj-sign" value={sign} onChange={(e) => setSign(e.target.value)}>
              <option value="-">빼기 (−)</option>
              <option value="+">더하기 (+)</option>
            </Select>
          </Field>
          <Field label="일수" htmlFor="adj-days" required hint="0.5일 단위">
            <Input
              id="adj-days"
              type="number"
              min="0.5"
              step="0.5"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              trailing="일"
            />
          </Field>
        </div>
        <Field label="사유" htmlFor="adj-memo" required hint="원장과 사원의 '내 연차' 화면에 그대로 보입니다.">
          <Input
            id="adj-memo"
            value={memo}
            onChange={(e) => setMemo(e.target.value)}
            placeholder="예: 근태 정정 반영, 포상 휴가"
            maxLength={200}
          />
        </Field>
        <p
          className={cn(
            'rounded-lg px-3.5 py-3 text-[13px]',
            after < 0 ? 'bg-red-50 text-red-700' : 'bg-gray-50 text-gray-700',
          )}
        >
          사용 가능 {formatDays(row.available)} → <b className="font-semibold">{formatDays(after)}</b>
          {after < 0 && ' · 0보다 작아지게 조정할 수 없습니다.'}
        </p>
        {error && (
          <p role="alert" className="text-[13px] text-red-600">
            {error}
          </p>
        )}
      </div>
    </Modal>
  );
}
