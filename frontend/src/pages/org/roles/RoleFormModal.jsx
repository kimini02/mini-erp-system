import { useEffect, useState } from 'react';
import { roleApi } from '@/api/roles';
import { getErrorMessage } from '@/lib/errors';
import { toast } from '@/lib/toast';
import { Button, Field, Input, Modal, Textarea } from '@/components/ui';

export default function RoleFormModal({ open, role, onClose, onSaved }) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (open) {
      setName(role?.name || '');
      setDescription(role?.description || '');
      setError('');
    }
  }, [open, role]);

  const submit = async (e) => {
    e.preventDefault();
    setSaving(true);
    setError('');
    try {
      const saved = role
        ? await roleApi.update(role.id, { name: name.trim(), description: description.trim() })
        : await roleApi.create({ name: name.trim(), description: description.trim() });
      toast.success(role ? '역할 정보를 바꿨습니다.' : `'${name.trim()}' 역할을 만들었습니다. 권한을 정해 주세요.`);
      onSaved(saved);
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
      title={role ? '역할 정보 수정' : '역할 만들기'}
      size="md"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            취소
          </Button>
          <Button type="submit" form="role-form" loading={saving} disabled={!name.trim()}>
            {role ? '저장' : '만들기'}
          </Button>
        </>
      }
    >
      <form id="role-form" onSubmit={submit} className="space-y-4">
        <Field label="역할 이름" htmlFor="role-name" required>
          <Input
            id="role-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="예: 인사 담당"
            autoFocus
          />
        </Field>
        <Field label="설명" htmlFor="role-desc" hint="어떤 사람에게 주는 역할인지 적어 두면 나중에 헷갈리지 않습니다.">
          <Textarea id="role-desc" rows={3} value={description} onChange={(e) => setDescription(e.target.value)} />
        </Field>
        {error && (
          <p role="alert" className="text-[13px] text-red-600">
            {error}
          </p>
        )}
      </form>
    </Modal>
  );
}

/* ───────── 사원 추가 ───────── */
