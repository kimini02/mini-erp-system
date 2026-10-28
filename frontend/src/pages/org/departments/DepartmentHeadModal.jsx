import { useEffect, useState } from 'react';
import { departmentApi } from '@/api/org';
import { getErrorMessage } from '@/lib/errors';
import { toast } from '@/lib/toast';
import { Button, Field, Modal, Select } from '@/components/ui';

export default function DepartmentHeadModal({ open, onClose, department, members, onSaved }) {
  const candidates = members.filter((m) => m.status === 'ACTIVE');
  const [userId, setUserId] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (open) {
      setUserId(department?.head?.id ?? '');
      setError('');
    }
  }, [open, department]);

  const submit = async () => {
    setSaving(true);
    setError('');
    try {
      await departmentApi.setHead(department.id, Number(userId));
      toast.success('부서장을 지정했습니다. 새로 상신하는 문서부터 결재자로 들어갑니다.');
      onSaved();
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="부서장 지정"
      description="그 부서에 소속된 재직 사원만 부서장이 될 수 있습니다."
      size="md"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            취소
          </Button>
          <Button onClick={submit} loading={saving} disabled={!userId || Number(userId) === department?.head?.id}>
            지정
          </Button>
        </>
      }
    >
      {candidates.length === 0 ? (
        <p className="text-sm text-gray-600">
          이 부서에 재직 중인 사원이 없습니다. 사원 관리에서 먼저 사원을 이 부서로 옮겨 주세요.
        </p>
      ) : (
        <Field label="부서장" htmlFor="head-user">
          <Select id="head-user" value={userId} onChange={(e) => setUserId(e.target.value)}>
            <option value="">사원 선택</option>
            {candidates.map((m) => (
              <option key={m.id} value={m.id}>
                {m.name} {m.position} ({m.loginId})
              </option>
            ))}
          </Select>
        </Field>
      )}
      {error && (
        <p role="alert" className="mt-3 text-[13px] text-red-600">
          {error}
        </p>
      )}
    </Modal>
  );
}

/* ───────── 화면 ───────── */
