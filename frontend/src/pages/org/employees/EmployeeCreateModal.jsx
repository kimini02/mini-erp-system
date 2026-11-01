import { useEffect, useState } from 'react';
import { userApi } from '@/api/users';
import { getErrorMessage } from '@/lib/errors';
import { toast } from '@/lib/toast';
import { Badge, Button, Field, Input, Modal, Select } from '@/components/ui';
import RoleChips from './RoleChips';

const today = () => new Date().toLocaleDateString('sv-SE');

export default function EmployeeCreateModal({
  open,
  onClose,
  departments,
  positions,
  roles,
  canAssignRoles,
  onCreated,
}) {
  const blank = { name: '', email: '', hireDate: today(), departmentId: '', positionId: '', roleCodes: ['EMPLOYEE'] };
  const [form, setForm] = useState(blank);
  const [preview, setPreview] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (open) {
      setForm({
        ...blank,
        departmentId: departments[1]?.id ?? departments[0]?.id ?? '',
        positionId: positions[0]?.id ?? '',
      });
      setError('');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  useEffect(() => {
    if (!open || !/^\d{4}-\d{2}-\d{2}$/.test(form.hireDate)) return undefined;
    let alive = true;
    userApi
      .previewLoginId(form.hireDate)
      .then((r) => alive && setPreview(r.loginId))
      .catch(() => alive && setPreview(''));
    return () => {
      alive = false;
    };
  }, [open, form.hireDate]);

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }));
  const valid =
    form.name.trim() &&
    form.email.includes('@') &&
    form.hireDate &&
    form.departmentId &&
    form.positionId &&
    form.roleCodes.length;

  const submit = async (e) => {
    e.preventDefault();
    if (!valid) return;
    setSaving(true);
    setError('');
    try {
      const result = await userApi.create({
        name: form.name.trim(),
        email: form.email.trim(),
        hireDate: form.hireDate,
        departmentId: Number(form.departmentId),
        positionId: Number(form.positionId),
        roleCodes: form.roleCodes,
      });
      toast.success(`${form.name.trim()} 사원을 등록했습니다. 아이디 ${result.loginId}`);
      if (!result.invitationSent)
        toast.info(
          '메일 발송이 꺼져 있어 초대 메일을 보내지 못했습니다. 나중에 "초대 메일 다시 보내기"를 눌러 주세요.',
        );
      onCreated();
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
      title="사원 등록"
      description="회원가입 없이 관리자가 등록합니다. 등록하면 아이디가 정해지고 초대 메일이 갑니다."
      size="xl"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            취소
          </Button>
          <Button type="submit" form="create-user" loading={saving} disabled={!valid}>
            등록하고 초대 메일 보내기
          </Button>
        </>
      }
    >
      <form id="create-user" onSubmit={submit} className="space-y-5">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Field label="이름" htmlFor="cu-name" required>
            <Input id="cu-name" value={form.name} onChange={set('name')} placeholder="예: 홍길동" autoFocus />
          </Field>
          <Field label="입사일" htmlFor="cu-hire" required>
            <Input id="cu-hire" type="date" value={form.hireDate} onChange={set('hireDate')} />
          </Field>
          <Field label="부서" htmlFor="cu-dept" required>
            <Select id="cu-dept" value={form.departmentId} onChange={set('departmentId')}>
              {departments.map((d) => (
                <option key={d.id} value={d.id}>
                  {'　'.repeat(d.depth)}
                  {d.name}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="직급" htmlFor="cu-pos" required>
            <Select id="cu-pos" value={form.positionId} onChange={set('positionId')}>
              {positions.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </Select>
          </Field>
          <Field
            label="회사 이메일"
            htmlFor="cu-email"
            required
            hint="초대 메일과 비밀번호 재설정 메일이 이 주소로 갑니다."
            className="sm:col-span-2"
          >
            <Input
              id="cu-email"
              type="email"
              value={form.email}
              onChange={set('email')}
              placeholder="name@company.com"
            />
          </Field>
        </div>

        <Field
          label="역할"
          hint={
            canAssignRoles
              ? '역할마다 볼 수 있는 메뉴와 데이터 범위가 다릅니다.'
              : '역할 변경 권한이 없어 기본 역할(사원)로 등록합니다.'
          }
        >
          {canAssignRoles ? (
            <RoleChips
              roles={roles}
              value={form.roleCodes}
              onChange={(roleCodes) => setForm((f) => ({ ...f, roleCodes }))}
            />
          ) : (
            <div>
              <Badge tone="gray">사원</Badge>
            </div>
          )}
        </Field>

        <div className="grid grid-cols-1 gap-3 rounded-xl border border-gray-200 bg-gray-50 p-4 sm:grid-cols-2">
          <div>
            <p className="text-xs text-gray-500">아이디 (자동 생성)</p>
            <p className="mt-0.5 text-lg font-semibold tabular-nums tracking-wide text-gray-900">{preview || '—'}</p>
            <p className="text-xs text-gray-500">입사연도 + 그 해 입사 순번 · 미리보기이며 등록할 때 확정됩니다</p>
          </div>
          <div>
            <p className="text-xs text-gray-500">비밀번호</p>
            <p className="mt-0.5 text-sm font-medium text-gray-900">초대 메일의 링크로 직접 설정</p>
            <p className="text-xs text-gray-500">링크는 24시간 동안 한 번만 쓸 수 있습니다</p>
          </div>
        </div>

        {error && (
          <p role="alert" className="text-[13px] text-red-600">
            {error}
          </p>
        )}
      </form>
    </Modal>
  );
}

/* ───────── 수정 ───────── */
