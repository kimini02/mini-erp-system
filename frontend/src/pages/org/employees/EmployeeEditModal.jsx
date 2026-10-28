import { useEffect, useMemo, useState } from 'react';
import { Mail } from 'lucide-react';
import { userApi } from '@/api/users';
import { USER_STATUS_META } from '@/lib/permissions';
import { getErrorMessage } from '@/lib/errors';
import { toast } from '@/lib/toast';
import { Badge, Button, Field, Input, Modal, Select } from '@/components/ui';
import { STATUS_TABS, roleTone } from './helpers';
import RoleChips from './RoleChips';

export default function EmployeeEditModal({
  user,
  onClose,
  departments,
  positions,
  roles,
  canAssignRoles,
  canInvite,
  canManagePrivileged,
  onSaved,
}) {
  const initial = useMemo(
    () =>
      user && {
        name: user.name,
        email: user.email,
        departmentId: String(user.department?.id ?? ''),
        positionId: String(positions.find((p) => p.name === user.position)?.id ?? ''),
        hireDate: user.hireDate || '',
        status: user.status,
        roleCodes: user.roles.map((r) => r.code),
      },
    [user, positions],
  );

  const [form, setForm] = useState(initial);
  const [saving, setSaving] = useState(false);
  const [inviting, setInviting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    setForm(initial);
    setError('');
  }, [initial]);

  if (!user || !form) return null;

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }));
  const profileChanged =
    form.name !== initial.name ||
    form.email !== initial.email ||
    form.departmentId !== initial.departmentId ||
    form.positionId !== initial.positionId ||
    form.hireDate !== initial.hireDate;
  const statusChanged = form.status !== initial.status;
  const rolesChanged = [...form.roleCodes].sort().join() !== [...initial.roleCodes].sort().join();
  const leaving = statusChanged && form.status !== 'ACTIVE';
  // 관리 역할을 가진 사원의 이메일·초대 메일은 역할 수정 권한이 있어야 다룰 수 있습니다(권한 상승 방지).
  const privileged = user.roles.some((r) => r.code !== 'EMPLOYEE');
  const emailLocked = privileged && !canManagePrivileged;
  const emailHint = emailLocked
    ? '관리 역할을 가진 사원의 이메일은 역할·권한을 바꿀 수 있는 사람만 바꿀 수 있습니다.'
    : user.invitationPending && form.email !== initial.email
      ? '저장하면 이전에 보낸 초대 링크는 더 이상 쓸 수 없습니다. 초대 메일을 다시 보내 주세요.'
      : undefined;

  const save = async () => {
    setSaving(true);
    setError('');
    try {
      if (profileChanged) {
        const body = {};
        if (form.name !== initial.name) body.name = form.name.trim();
        if (form.email !== initial.email) body.email = form.email.trim();
        if (form.departmentId !== initial.departmentId) body.departmentId = Number(form.departmentId);
        if (form.positionId !== initial.positionId) body.positionId = Number(form.positionId);
        if (form.hireDate !== initial.hireDate) body.hireDate = form.hireDate;
        await userApi.update(user.id, body);
      }
      if (rolesChanged) await userApi.setRoles(user.id, form.roleCodes);
      if (statusChanged) await userApi.changeStatus(user.id, form.status);
      toast.success(`${form.name} 사원 정보를 저장했습니다.`);
      onSaved();
    } catch (err) {
      setError(getErrorMessage(err));
      onSaved({ keepOpen: true });
    } finally {
      setSaving(false);
    }
  };

  const resend = async () => {
    setInviting(true);
    try {
      const r = await userApi.resendInvitation(user.id);
      if (r?.invitationSent === false) toast.info('메일 발송이 꺼져 있어 보내지 못했습니다.');
      else toast.success(`${user.email} 로 초대 메일을 다시 보냈습니다.`);
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally {
      setInviting(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      title={`${user.name} · ${user.loginId}`}
      description={user.hireDate ? `${user.hireDate} 입사` : '입사일 미입력 (v1에서 옮겨 온 사원)'}
      size="xl"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            닫기
          </Button>
          <Button
            onClick={save}
            loading={saving}
            disabled={!(profileChanged || statusChanged || rolesChanged) || !form.name.trim()}
          >
            저장
          </Button>
        </>
      }
    >
      <div className="space-y-5">
        {user.invitationPending && canInvite && !emailLocked && (
          <div className="flex flex-wrap items-center gap-3 rounded-lg bg-blue-50 px-3.5 py-3 text-[13px] text-blue-900">
            <Mail size={16} className="shrink-0 text-blue-600" />
            <span className="flex-1">아직 비밀번호를 설정하지 않았습니다 (첫 로그인 전).</span>
            <Button size="sm" variant="secondary" onClick={resend} loading={inviting}>
              초대 메일 다시 보내기
            </Button>
          </div>
        )}

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Field label="이름" htmlFor="eu-name" required>
            <Input id="eu-name" value={form.name} onChange={set('name')} />
          </Field>
          <Field label="회사 이메일" htmlFor="eu-email" required hint={emailHint}>
            <Input id="eu-email" type="email" value={form.email} onChange={set('email')} disabled={emailLocked} />
          </Field>
          <Field
            label="부서"
            htmlFor="eu-dept"
            hint="부서장인 사람은 먼저 부서장을 다른 사람으로 바꿔야 옮길 수 있습니다."
          >
            <Select id="eu-dept" value={form.departmentId} onChange={set('departmentId')}>
              {departments.map((d) => (
                <option key={d.id} value={d.id}>
                  {'　'.repeat(d.depth)}
                  {d.name}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="직급" htmlFor="eu-pos">
            <Select id="eu-pos" value={form.positionId} onChange={set('positionId')}>
              {positions.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </Select>
          </Field>
          <Field
            label="입사일"
            htmlFor="eu-hire"
            hint={user.hireDate ? undefined : '연차 자동 부여에 필요합니다. 채워 주세요.'}
          >
            <Input id="eu-hire" type="date" value={form.hireDate} onChange={set('hireDate')} invalid={!form.hireDate} />
          </Field>
          <Field
            label="재직 상태"
            htmlFor="eu-status"
            error={leaving ? '저장하면 이 사람의 로그인이 바로 끊깁니다.' : undefined}
          >
            <Select id="eu-status" value={form.status} onChange={set('status')} invalid={leaving}>
              {STATUS_TABS.map((s) => (
                <option key={s} value={s}>
                  {USER_STATUS_META[s].label}
                </option>
              ))}
            </Select>
          </Field>
        </div>

        <Field
          label="역할"
          hint={canAssignRoles ? undefined : '역할은 역할·권한을 바꿀 수 있는 사람만 바꿀 수 있습니다.'}
        >
          {canAssignRoles ? (
            <RoleChips
              roles={roles}
              value={form.roleCodes}
              onChange={(roleCodes) => setForm((f) => ({ ...f, roleCodes }))}
            />
          ) : (
            <div className="flex flex-wrap gap-1">
              {user.roles.map((r) => (
                <Badge key={r.code} tone={roleTone(r.code)}>
                  {r.name}
                </Badge>
              ))}
            </div>
          )}
        </Field>

        {error && (
          <p role="alert" className="text-[13px] text-red-600">
            {error}
          </p>
        )}
      </div>
    </Modal>
  );
}

/* ───────── 화면 ───────── */
