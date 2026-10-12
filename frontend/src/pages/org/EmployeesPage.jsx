import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Check, ChevronLeft, ChevronRight, Mail, Pencil, Search, UserPlus, Users } from 'lucide-react';
import { collectSubtreeIds, departmentApi, flattenTree, positionApi, roleApi, userApi } from '@/api/m1';
import { useAuthStore } from '@/store/authStore';
import { MENU, USER_STATUS_META } from '@/lib/permissions';
import { getErrorMessage } from '@/lib/errors';
import { toast } from '@/lib/toast';
import { cn } from '@/lib/cn';
import {
  Avatar, Badge, Button, Card, EmptyState, Field, Input, Modal, PageHeader, PageLoader, Select, Table, TableEmpty, Td, Th, Tr,
} from '@/components/ui';

const PAGE_SIZE = 15;
const STATUS_TABS = ['ACTIVE', 'ON_LEAVE', 'RETIRED'];
const roleTone = (code) => (code === 'CHIEF' ? 'red' : code === 'LEADER' ? 'blue' : code === 'EMPLOYEE' ? 'gray' : 'green');
const today = () => new Date().toLocaleDateString('sv-SE');

function useDebounced(value, delay) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(id);
  }, [value, delay]);
  return debounced;
}

/** 역할 선택 칩 (역할을 바꿀 권한이 있을 때만) */
function RoleChips({ roles, value, onChange }) {
  const toggle = (code) => onChange(value.includes(code) ? value.filter((c) => c !== code) : [...value, code]);
  return (
    <div className="flex flex-wrap gap-2" role="group" aria-label="역할">
      {roles.map((r) => {
        const on = value.includes(r.code);
        return (
          <button
            key={r.code}
            type="button"
            role="checkbox"
            aria-checked={on}
            onClick={() => toggle(r.code)}
            className={cn(
              'inline-flex h-8 items-center gap-1.5 rounded-full border px-3 text-[13px] font-medium transition-colors',
              on ? 'border-blue-500 bg-blue-50 text-blue-700' : 'border-gray-300 text-gray-600 hover:border-gray-400'
            )}
          >
            {on && <Check size={13} strokeWidth={2.5} />}
            {r.name}
          </button>
        );
      })}
    </div>
  );
}

/* ───────── 등록 ───────── */

function CreateModal({ open, onClose, departments, positions, roles, canAssignRoles, onCreated }) {
  const blank = { name: '', email: '', hireDate: today(), departmentId: '', positionId: '', roleCodes: ['EMPLOYEE'] };
  const [form, setForm] = useState(blank);
  const [preview, setPreview] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (open) {
      setForm({ ...blank, departmentId: departments[1]?.id ?? departments[0]?.id ?? '', positionId: positions[0]?.id ?? '' });
      setError('');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  useEffect(() => {
    if (!open || !/^\d{4}-\d{2}-\d{2}$/.test(form.hireDate)) return undefined;
    let alive = true;
    userApi.previewLoginId(form.hireDate)
      .then((r) => alive && setPreview(r.loginId))
      .catch(() => alive && setPreview(''));
    return () => { alive = false; };
  }, [open, form.hireDate]);

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }));
  const valid = form.name.trim() && form.email.includes('@') && form.hireDate && form.departmentId && form.positionId && form.roleCodes.length;

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
      if (!result.invitationSent) toast.info('메일 발송이 꺼져 있어 초대 메일을 보내지 못했습니다. 나중에 "초대 메일 다시 보내기"를 눌러 주세요.');
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
      footer={<><Button variant="secondary" onClick={onClose}>취소</Button><Button type="submit" form="create-user" loading={saving} disabled={!valid}>등록하고 초대 메일 보내기</Button></>}
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
              {departments.map((d) => <option key={d.id} value={d.id}>{'　'.repeat(d.depth)}{d.name}</option>)}
            </Select>
          </Field>
          <Field label="직급" htmlFor="cu-pos" required>
            <Select id="cu-pos" value={form.positionId} onChange={set('positionId')}>
              {positions.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
            </Select>
          </Field>
          <Field label="회사 이메일" htmlFor="cu-email" required hint="초대 메일과 비밀번호 재설정 메일이 이 주소로 갑니다." className="sm:col-span-2">
            <Input id="cu-email" type="email" value={form.email} onChange={set('email')} placeholder="name@company.com" />
          </Field>
        </div>

        <Field label="역할" hint={canAssignRoles ? '역할마다 볼 수 있는 메뉴와 데이터 범위가 다릅니다.' : '역할 변경 권한이 없어 기본 역할(사원)로 등록합니다.'}>
          {canAssignRoles ? (
            <RoleChips roles={roles} value={form.roleCodes} onChange={(roleCodes) => setForm((f) => ({ ...f, roleCodes }))} />
          ) : (
            <div><Badge tone="gray">사원</Badge></div>
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

        {error && <p role="alert" className="text-[13px] text-red-600">{error}</p>}
      </form>
    </Modal>
  );
}

/* ───────── 수정 ───────── */

function EditModal({ user, onClose, departments, positions, roles, canAssignRoles, canInvite, onSaved }) {
  const initial = useMemo(() => user && {
    name: user.name,
    email: user.email,
    departmentId: String(user.department?.id ?? ''),
    positionId: String(positions.find((p) => p.name === user.position)?.id ?? ''),
    status: user.status,
    roleCodes: user.roles.map((r) => r.code),
  }, [user, positions]);

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
  const profileChanged = form.name !== initial.name || form.email !== initial.email
    || form.departmentId !== initial.departmentId || form.positionId !== initial.positionId;
  const statusChanged = form.status !== initial.status;
  const rolesChanged = [...form.roleCodes].sort().join() !== [...initial.roleCodes].sort().join();
  const leaving = statusChanged && form.status !== 'ACTIVE';

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
      description={`${user.hireDate} 입사`}
      size="xl"
      footer={<><Button variant="secondary" onClick={onClose}>닫기</Button><Button onClick={save} loading={saving} disabled={!(profileChanged || statusChanged || rolesChanged) || !form.name.trim()}>저장</Button></>}
    >
      <div className="space-y-5">
        {user.invitationPending && canInvite && (
          <div className="flex flex-wrap items-center gap-3 rounded-lg bg-blue-50 px-3.5 py-3 text-[13px] text-blue-900">
            <Mail size={16} className="shrink-0 text-blue-600" />
            <span className="flex-1">아직 비밀번호를 설정하지 않았습니다 (첫 로그인 전).</span>
            <Button size="sm" variant="secondary" onClick={resend} loading={inviting}>초대 메일 다시 보내기</Button>
          </div>
        )}

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Field label="이름" htmlFor="eu-name" required>
            <Input id="eu-name" value={form.name} onChange={set('name')} />
          </Field>
          <Field label="회사 이메일" htmlFor="eu-email" required>
            <Input id="eu-email" type="email" value={form.email} onChange={set('email')} />
          </Field>
          <Field label="부서" htmlFor="eu-dept" hint="부서장인 사람은 먼저 부서장을 다른 사람으로 바꿔야 옮길 수 있습니다.">
            <Select id="eu-dept" value={form.departmentId} onChange={set('departmentId')}>
              {departments.map((d) => <option key={d.id} value={d.id}>{'　'.repeat(d.depth)}{d.name}</option>)}
            </Select>
          </Field>
          <Field label="직급" htmlFor="eu-pos">
            <Select id="eu-pos" value={form.positionId} onChange={set('positionId')}>
              {positions.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
            </Select>
          </Field>
          <Field
            label="재직 상태"
            htmlFor="eu-status"
            error={leaving ? '저장하면 이 사람의 로그인이 바로 끊깁니다.' : undefined}
          >
            <Select id="eu-status" value={form.status} onChange={set('status')} invalid={leaving}>
              {STATUS_TABS.map((s) => <option key={s} value={s}>{USER_STATUS_META[s].label}</option>)}
            </Select>
          </Field>
        </div>

        <Field label="역할" hint={canAssignRoles ? undefined : '역할은 역할·권한을 바꿀 수 있는 사람만 바꿀 수 있습니다.'}>
          {canAssignRoles ? (
            <RoleChips roles={roles} value={form.roleCodes} onChange={(roleCodes) => setForm((f) => ({ ...f, roleCodes }))} />
          ) : (
            <div className="flex flex-wrap gap-1">{user.roles.map((r) => <Badge key={r.code} tone={roleTone(r.code)}>{r.name}</Badge>)}</div>
          )}
        </Field>

        {error && <p role="alert" className="text-[13px] text-red-600">{error}</p>}
      </div>
    </Modal>
  );
}

/* ───────── 화면 ───────── */

export default function EmployeesPage() {
  const [searchParams] = useSearchParams();
  const can = useAuthStore((s) => s.can);
  const scope = useAuthStore((s) => s.scopeOf(MENU.EMPLOYEE));
  const myDepartmentId = useAuthStore((s) => s.user?.department?.id);
  const canCreate = can(MENU.EMPLOYEE, 'C');
  const canUpdate = can(MENU.EMPLOYEE, 'U');
  const canAssignRoles = can(MENU.ROLE, 'V') && can(MENU.ROLE, 'U');

  const [q, setQ] = useState('');
  const debouncedQ = useDebounced(q.trim(), 300);
  const [departmentId, setDepartmentId] = useState(searchParams.get('departmentId') || '');
  const [includeSub, setIncludeSub] = useState(true);
  const [status, setStatus] = useState('ACTIVE');
  const [page, setPage] = useState(0);

  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [tree, setTree] = useState(null);
  const [positions, setPositions] = useState([]);
  const [roles, setRoles] = useState([]);
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState(null);

  const departments = useMemo(() => flattenTree(tree), [tree]);
  // 검색용 부서 목록은 내 데이터 범위 안의 부서만 (범위 밖을 고르면 서버가 403)
  const filterDepartments = useMemo(() => {
    if (!tree || scope === 'ALL' || !myDepartmentId) return departments;
    const visible = scope === 'DEPT_TREE' ? collectSubtreeIds(tree, myDepartmentId) : new Set([myDepartmentId]);
    return departments.filter((d) => visible.has(d.id));
  }, [tree, departments, scope, myDepartmentId]);

  useEffect(() => {
    departmentApi.tree().then(setTree).catch(() => {});
    positionApi.list().then(setPositions).catch(() => {});
    if (canAssignRoles) roleApi.list().then(setRoles).catch(() => {});
  }, [canAssignRoles]);

  useEffect(() => setPage(0), [debouncedQ, departmentId, includeSub, status]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await userApi.list({
        q: debouncedQ || undefined,
        departmentId: departmentId || undefined,
        includeSubDepartments: departmentId ? includeSub : undefined,
        status,
        page,
        size: PAGE_SIZE,
        sort: 'userName,asc',
      });
      setResult(data);
      setError('');
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [debouncedQ, departmentId, includeSub, status, page]);

  useEffect(() => {
    load();
  }, [load]);

  const counts = result?.statusCounts || {};
  const rows = result?.content || [];
  const pageInfo = result?.page;

  return (
    <>
      <PageHeader
        title="사원 관리"
        description="회원가입 없이 관리자가 사원을 등록합니다. 아이디는 자동으로 만들어지고 초대 메일로 첫 로그인을 돕습니다."
        actions={canCreate && <Button icon={UserPlus} onClick={() => setCreating(true)}>사원 등록</Button>}
      />

      <Card className="overflow-hidden">
        <div className="flex flex-col gap-3 border-b border-gray-100 p-4 lg:flex-row lg:items-center">
          <div className="flex flex-1 flex-col gap-2 sm:flex-row">
            <label className="sm:w-72">
              <span className="sr-only">이름 또는 아이디 검색</span>
              <Input icon={Search} type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="이름 또는 아이디 앞부분" />
            </label>
            <label className="sm:w-56">
              <span className="sr-only">부서</span>
              <Select value={departmentId} onChange={(e) => setDepartmentId(e.target.value)}>
                <option value="">{scope === 'ALL' ? '전체 부서' : '볼 수 있는 모든 부서'}</option>
                {filterDepartments.map((d) => <option key={d.id} value={d.id}>{'　'.repeat(d.depth)}{d.name}</option>)}
              </Select>
            </label>
            {departmentId && (
              <label className="flex h-10 items-center gap-2 text-[13px] text-gray-600">
                <input type="checkbox" checked={includeSub} onChange={(e) => setIncludeSub(e.target.checked)} className="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500" />
                하위 부서 포함
              </label>
            )}
          </div>

          <div role="tablist" aria-label="재직 상태" className="inline-flex shrink-0 rounded-lg bg-gray-100 p-1">
            {STATUS_TABS.map((s) => {
              const active = s === status;
              return (
                <button
                  key={s}
                  type="button"
                  role="tab"
                  aria-selected={active}
                  onClick={() => setStatus(s)}
                  className={cn(
                    'h-8 rounded-md px-3 text-[13px] font-medium transition-colors',
                    active ? 'bg-white text-gray-900 shadow-sm ring-1 ring-gray-200' : 'text-gray-600 hover:text-gray-900'
                  )}
                >
                  {USER_STATUS_META[s].label} <span className="tabular-nums text-gray-500">{counts[s] ?? 0}</span>
                </button>
              );
            })}
          </div>
        </div>

        {error ? (
          <EmptyState icon={Users} title="사원 목록을 불러오지 못했습니다" description={error} />
        ) : !result ? (
          <PageLoader />
        ) : (
          <Table className={cn(loading && 'opacity-60')}>
            <thead>
              <tr>
                <Th>아이디</Th>
                <Th>이름</Th>
                <Th>부서</Th>
                <Th>직급</Th>
                <Th>역할</Th>
                <Th>입사일</Th>
                <Th>상태</Th>
                {canUpdate && <Th align="right"><span className="sr-only">수정</span></Th>}
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 ? (
                <TableEmpty colSpan={canUpdate ? 8 : 7}>조건에 맞는 사원이 없습니다.</TableEmpty>
              ) : (
                rows.map((u) => (
                  <Tr key={u.id}>
                    <Td className="tabular-nums text-gray-500">{u.loginId}</Td>
                    <Td>
                      <span className="flex items-center gap-2.5">
                        <Avatar name={u.name} size="sm" />
                        <span className="min-w-0">
                          <span className="block font-medium text-gray-900">{u.name}</span>
                          <span className="block max-w-[200px] truncate text-xs text-gray-500">{u.email}</span>
                        </span>
                      </span>
                    </Td>
                    <Td>{u.department?.name}</Td>
                    <Td className="text-gray-600">{u.position}</Td>
                    <Td>
                      <span className="flex flex-wrap gap-1">
                        {u.roles.map((r) => <Badge key={r.code} tone={roleTone(r.code)}>{r.name}</Badge>)}
                      </span>
                    </Td>
                    <Td className="tabular-nums text-gray-500">{u.hireDate}</Td>
                    <Td>
                      <span className="flex flex-wrap gap-1">
                        <Badge tone={USER_STATUS_META[u.status].tone} dot>{USER_STATUS_META[u.status].label}</Badge>
                        {u.invitationPending && <Badge tone="blue">첫 로그인 전</Badge>}
                      </span>
                    </Td>
                    {canUpdate && (
                      <Td align="right">
                        <Button variant="ghost" size="sm" icon={Pencil} onClick={() => setEditing(u)} aria-label={`${u.name} 정보 수정`}>
                          수정
                        </Button>
                      </Td>
                    )}
                  </Tr>
                ))
              )}
            </tbody>
          </Table>
        )}

        {pageInfo && pageInfo.totalElements > 0 && (
          <div className="flex items-center justify-between border-t border-gray-100 px-4 py-3 text-[13px] text-gray-500">
            <span>
              총 <b className="font-semibold text-gray-900">{pageInfo.totalElements}</b>명 · {pageInfo.number + 1} / {pageInfo.totalPages} 쪽
            </span>
            <div className="flex gap-1">
              <Button variant="secondary" size="sm" icon={ChevronLeft} disabled={pageInfo.first} onClick={() => setPage((p) => p - 1)} aria-label="이전 쪽" />
              <Button variant="secondary" size="sm" icon={ChevronRight} disabled={pageInfo.last} onClick={() => setPage((p) => p + 1)} aria-label="다음 쪽" />
            </div>
          </div>
        )}
      </Card>

      <CreateModal
        open={creating}
        onClose={() => setCreating(false)}
        departments={departments}
        positions={positions}
        roles={roles}
        canAssignRoles={canAssignRoles}
        onCreated={() => {
          setCreating(false);
          setStatus('ACTIVE');
          load();
        }}
      />
      <EditModal
        user={editing}
        onClose={() => setEditing(null)}
        departments={departments}
        positions={positions}
        roles={roles}
        canAssignRoles={canAssignRoles}
        canInvite={canCreate}
        onSaved={(opts) => {
          if (!opts?.keepOpen) setEditing(null);
          load();
        }}
      />
    </>
  );
}
