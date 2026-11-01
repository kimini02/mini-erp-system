import { useEffect, useMemo, useState } from 'react';
import { departmentApi } from '@/api/org';
import { flattenTree, collectSubtreeIds } from '@/lib/orgTree';
import { getErrorMessage } from '@/lib/errors';
import { toast } from '@/lib/toast';
import { Button, Field, Modal, Select } from '@/components/ui';

export default function DepartmentMoveModal({ open, onClose, tree, department, onMoved }) {
  const blocked = useMemo(
    () => (tree && department ? collectSubtreeIds(tree, department.id) : new Set()),
    [tree, department],
  );
  const options = useMemo(() => flattenTree(tree).filter((o) => !blocked.has(o.id)), [tree, blocked]);
  const [parentId, setParentId] = useState(department?.parentId);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (open) {
      setParentId(department?.parentId);
      setError('');
    }
  }, [open, department]);

  const submit = async () => {
    setSaving(true);
    setError('');
    try {
      await departmentApi.move(department.id, Number(parentId));
      toast.success('부서를 옮겼습니다.');
      onMoved();
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
      title={`'${department?.name}' 옮기기`}
      description="자기 자신과 하위 부서 아래로는 옮길 수 없습니다."
      size="md"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            취소
          </Button>
          <Button onClick={submit} loading={saving} disabled={Number(parentId) === department?.parentId}>
            옮기기
          </Button>
        </>
      }
    >
      <Field label="새 상위 부서" htmlFor="move-parent">
        <Select id="move-parent" value={parentId ?? ''} onChange={(e) => setParentId(Number(e.target.value))}>
          {options.map((o) => (
            <option key={o.id} value={o.id}>
              {'　'.repeat(o.depth)}
              {o.name}
            </option>
          ))}
        </Select>
      </Field>
      {error && (
        <p role="alert" className="mt-3 text-[13px] text-red-600">
          {error}
        </p>
      )}
    </Modal>
  );
}
