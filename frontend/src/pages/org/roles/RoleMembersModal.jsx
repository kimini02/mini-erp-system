import { useEffect, useState } from 'react';
import { roleApi } from '@/api/roles';
import { userApi } from '@/api/users';
import { getErrorMessage } from '@/lib/errors';
import { toast } from '@/lib/toast';
import { cn } from '@/lib/cn';
import { Avatar, Button, Input, Modal } from '@/components/ui';

export default function RoleMembersModal({ open, role, existingIds, onClose, onAdded }) {
  const [q, setQ] = useState('');
  const [results, setResults] = useState([]);
  const [picked, setPicked] = useState([]);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return undefined;
    setPicked([]);
    let alive = true;
    const id = setTimeout(() => {
      userApi
        .list({ q: q.trim() || undefined, status: 'ACTIVE', size: 20 })
        .then((r) => alive && setResults(r.content))
        .catch(() => alive && setResults([]));
    }, 250);
    return () => {
      alive = false;
      clearTimeout(id);
    };
  }, [open, q]);

  const toggle = (id) => setPicked((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));

  const submit = async () => {
    setSaving(true);
    try {
      await roleApi.addMembers(role.id, picked);
      toast.success(`${picked.length}명에게 '${role.name}' 역할을 줬습니다.`);
      onAdded();
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={`'${role?.name}' 역할에 사원 추가`}
      size="lg"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            취소
          </Button>
          <Button onClick={submit} loading={saving} disabled={!picked.length}>
            {picked.length}명 추가
          </Button>
        </>
      }
    >
      <Input
        type="search"
        value={q}
        onChange={(e) => setQ(e.target.value)}
        placeholder="이름 또는 아이디 앞부분"
        aria-label="사원 검색"
      />
      <ul className="mt-3 divide-y divide-gray-100">
        {results.length === 0 && <li className="py-8 text-center text-sm text-gray-500">검색 결과가 없습니다.</li>}
        {results.map((u) => {
          const already = existingIds.has(u.id);
          return (
            <li key={u.id}>
              <label className={cn('flex items-center gap-3 py-2.5', already ? 'opacity-50' : 'cursor-pointer')}>
                <input
                  type="checkbox"
                  disabled={already}
                  checked={already || picked.includes(u.id)}
                  onChange={() => toggle(u.id)}
                  className="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                />
                <Avatar name={u.name} size="sm" />
                <span className="flex-1 text-sm">
                  <span className="font-medium text-gray-900">{u.name}</span>{' '}
                  <span className="text-gray-500">
                    {u.position} · {u.department?.name}
                  </span>
                </span>
                {already && <span className="text-xs text-gray-500">이미 있음</span>}
              </label>
            </li>
          );
        })}
      </ul>
    </Modal>
  );
}

/* ───────── 화면 ───────── */
