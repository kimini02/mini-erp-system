import { useEffect, useMemo, useState } from 'react';
import { departmentApi } from '@/api/org';
import { flattenTree } from '@/lib/orgTree';
import { getErrorMessage } from '@/lib/errors';
import { toast } from '@/lib/toast';
import { Button, Field, Input, Modal, Select } from '@/components/ui';

export default function DepartmentFormModal({ open, onClose, tree, defaultParentId, onCreated }) {
  const [name, setName] = useState('');
  const [parentId, setParentId] = useState(defaultParentId);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const options = useMemo(() => flattenTree(tree), [tree]);

  useEffect(() => {
    if (open) {
      setName('');
      setParentId(defaultParentId);
      setError('');
    }
  }, [open, defaultParentId]);

  const submit = async (e) => {
    e.preventDefault();
    setSaving(true);
    setError('');
    try {
      const created = await departmentApi.create({ name: name.trim(), parentId: Number(parentId) });
      toast.success(`'${name.trim()}' 부서를 만들었습니다.`);
      onCreated(created.id);
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
      title="부서 추가"
      size="md"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            취소
          </Button>
          <Button type="submit" form="dept-form" loading={saving} disabled={!name.trim()}>
            추가
          </Button>
        </>
      }
    >
      <form id="dept-form" onSubmit={submit} className="space-y-4">
        <Field label="상위 부서" htmlFor="dept-parent" required>
          <Select id="dept-parent" value={parentId ?? ''} onChange={(e) => setParentId(Number(e.target.value))}>
            {options.map((o) => (
              <option key={o.id} value={o.id}>
                {'　'.repeat(o.depth)}
                {o.name}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="부서명" htmlFor="dept-name" required hint="부서 코드는 저장할 때 자동으로 정해집니다.">
          <Input
            id="dept-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="예: QA2팀"
            autoFocus
          />
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
