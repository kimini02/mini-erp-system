import { Fragment, useCallback, useEffect, useMemo, useState } from 'react';
import { KeyRound, Lock, Pencil, Plus, Trash2, UserMinus, UserPlus } from 'lucide-react';
import { roleApi, userApi } from '@/api/m1';
import { useAuthStore } from '@/store/authStore';
import { MENU, SCOPES, SCOPE_LABELS } from '@/lib/permissions';
import { getErrorMessage } from '@/lib/errors';
import { toast } from '@/lib/toast';
import { confirm } from '@/lib/confirm';
import { cn } from '@/lib/cn';
import {
  Avatar, Badge, Button, Card, CardHeader, EmptyState, Field, Input, Modal, PageHeader, PageLoader, Select, Table, TableEmpty, Td, Textarea, Th, Tr,
} from '@/components/ui';

const ACTION_COLUMNS = [
  { key: 'view', action: 'V', label: '보기' },
  { key: 'create', action: 'C', label: '등록' },
  { key: 'update', action: 'U', label: '수정' },
  { key: 'delete', action: 'D', label: '삭제' },
];

const permKey = (list) => JSON.stringify(list.map((p) => [p.menu, p.view, p.create, p.update, p.delete, p.scope]));

/* ───────── 권한표 ───────── */

function PermissionMatrix({ menus, permissions, locked, editable, onChange }) {
  const byMenu = useMemo(() => Object.fromEntries(permissions.map((p) => [p.menu, p])), [permissions]);
  const groups = useMemo(() => {
    const out = [];
    menus.forEach((m) => {
      const last = out[out.length - 1];
      if (last && last.name === m.group) last.items.push(m);
      else out.push({ name: m.group, items: [m] });
    });
    return out;
  }, [menus]);

  const update = (menu, patch) => {
    onChange(permissions.map((p) => {
      if (p.menu !== menu.code) return p;
      const next = { ...p, ...patch };
      const anyOn = ACTION_COLUMNS.some((c) => next[c.key]);
      // 범위가 있는 메뉴: 처음 켜면 '본인'으로 시작, 모두 끄면 범위도 비움 (서버 검증 규칙과 같음)
      if (menu.scopeApplicable) next.scope = anyOn ? next.scope || 'SELF' : null;
      return next;
    }));
  };

  const disabled = locked || !editable;

  return (
    <Table>
      <thead>
        <tr>
          <Th className="min-w-[200px]">메뉴</Th>
          {ACTION_COLUMNS.map((c) => <Th key={c.key} align="center" className="w-16">{c.label}</Th>)}
          <Th className="w-52">데이터 범위</Th>
        </tr>
      </thead>
      <tbody>
        {groups.map((g) => (
          <Fragment key={g.name}>
            <tr className="bg-gray-50/60">
              <td colSpan={6} className="px-4 pb-1.5 pt-3 text-xs font-semibold text-gray-500">{g.name}</td>
            </tr>
            {g.items.map((m) => {
              const p = byMenu[m.code];
              const anyOn = ACTION_COLUMNS.some((c) => p[c.key]);
              return (
                <Tr key={m.code}>
                  <Td className={cn('font-medium', anyOn ? 'text-gray-900' : 'text-gray-500')}>{m.name}</Td>
                  {ACTION_COLUMNS.map((c) => (
                    <Td key={c.key} align="center">
                      {m.allowedActions.includes(c.action) ? (
                        <input
                          type="checkbox"
                          checked={!!p[c.key]}
                          disabled={disabled}
                          onChange={(e) => update(m, { [c.key]: e.target.checked })}
                          aria-label={`${m.name} ${c.label}`}
                          className="h-4 w-4 cursor-pointer rounded border-gray-300 text-blue-600 focus:ring-blue-500 disabled:cursor-not-allowed disabled:opacity-60"
                        />
                      ) : (
                        <span className="text-gray-300" aria-label="해당 없음">—</span>
                      )}
                    </Td>
                  ))}
                  <Td>
                    {m.scopeApplicable ? (
                      <Select
                        value={p.scope || ''}
                        disabled={disabled || !anyOn}
                        onChange={(e) => update(m, { scope: e.target.value })}
                        aria-label={`${m.name} 데이터 범위`}
                        className="h-8 text-[13px]"
                      >
                        {!anyOn && <option value="">—</option>}
                        {SCOPES.map((s) => <option key={s} value={s}>{SCOPE_LABELS[s]}</option>)}
                      </Select>
                    ) : (
                      <span className="text-[13px] text-gray-300">—</span>
                    )}
                  </Td>
                </Tr>
              );
            })}
          </Fragment>
        ))}
      </tbody>
    </Table>
  );
}

/* ───────── 역할 만들기 · 이름 수정 ───────── */

function RoleFormModal({ open, role, onClose, onSaved }) {
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
    <Modal open={open} onClose={onClose} title={role ? '역할 정보 수정' : '역할 만들기'} size="md"
      footer={<><Button variant="secondary" onClick={onClose}>취소</Button><Button type="submit" form="role-form" loading={saving} disabled={!name.trim()}>{role ? '저장' : '만들기'}</Button></>}>
      <form id="role-form" onSubmit={submit} className="space-y-4">
        <Field label="역할 이름" htmlFor="role-name" required>
          <Input id="role-name" value={name} onChange={(e) => setName(e.target.value)} placeholder="예: 인사 담당" autoFocus />
        </Field>
        <Field label="설명" htmlFor="role-desc" hint="어떤 사람에게 주는 역할인지 적어 두면 나중에 헷갈리지 않습니다.">
          <Textarea id="role-desc" rows={3} value={description} onChange={(e) => setDescription(e.target.value)} />
        </Field>
        {error && <p role="alert" className="text-[13px] text-red-600">{error}</p>}
      </form>
    </Modal>
  );
}

/* ───────── 사원 추가 ───────── */

function AddMembersModal({ open, role, existingIds, onClose, onAdded }) {
  const [q, setQ] = useState('');
  const [results, setResults] = useState([]);
  const [picked, setPicked] = useState([]);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return undefined;
    setPicked([]);
    let alive = true;
    const id = setTimeout(() => {
      userApi.list({ q: q.trim() || undefined, status: 'ACTIVE', size: 20 })
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
    <Modal open={open} onClose={onClose} title={`'${role?.name}' 역할에 사원 추가`} size="lg"
      footer={<><Button variant="secondary" onClick={onClose}>취소</Button><Button onClick={submit} loading={saving} disabled={!picked.length}>{picked.length}명 추가</Button></>}>
      <Input type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="이름 또는 아이디 앞부분" aria-label="사원 검색" />
      <ul className="mt-3 divide-y divide-gray-100">
        {results.length === 0 && <li className="py-8 text-center text-sm text-gray-500">검색 결과가 없습니다.</li>}
        {results.map((u) => {
          const already = existingIds.has(u.id);
          return (
            <li key={u.id}>
              <label className={cn('flex items-center gap-3 py-2.5', already ? 'opacity-50' : 'cursor-pointer')}>
                <input type="checkbox" disabled={already} checked={already || picked.includes(u.id)} onChange={() => toggle(u.id)}
                  className="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500" />
                <Avatar name={u.name} size="sm" />
                <span className="flex-1 text-sm"><span className="font-medium text-gray-900">{u.name}</span> <span className="text-gray-500">{u.position} · {u.department?.name}</span></span>
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

export default function RolesPage() {
  const can = useAuthStore((s) => s.can);
  const canCreate = can(MENU.ROLE, 'C');
  const canUpdate = can(MENU.ROLE, 'U');
  const canDelete = can(MENU.ROLE, 'D');

  const [menus, setMenus] = useState([]);
  const [roles, setRoles] = useState(null);
  const [selectedId, setSelectedId] = useState(null);
  const [tab, setTab] = useState('perm');
  const [detail, setDetail] = useState(null); // { role, permissions }
  const [draft, setDraft] = useState([]);
  const [members, setMembers] = useState(null);
  const [saving, setSaving] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [modal, setModal] = useState(null); // 'create' | 'edit' | 'members'

  const loadRoles = useCallback(async () => {
    const list = await roleApi.list();
    setRoles(list);
    return list;
  }, []);

  useEffect(() => {
    Promise.all([roleApi.menus(), loadRoles()])
      .then(([m, list]) => {
        setMenus(m);
        setSelectedId((prev) => prev ?? list.find((r) => r.code === 'LEADER')?.id ?? list[0]?.id);
      })
      .catch((err) => setLoadError(getErrorMessage(err)));
  }, [loadRoles]);

  const loadDetail = useCallback(async (id) => {
    if (!id) return;
    const [d, m] = await Promise.all([roleApi.permissions(id), roleApi.members(id, { size: 100 })]);
    setDetail(d);
    setDraft(d.permissions);
    setMembers(m);
  }, []);

  useEffect(() => {
    loadDetail(selectedId).catch((err) => toast.error(getErrorMessage(err)));
  }, [selectedId, loadDetail]);

  const role = roles?.find((r) => r.id === selectedId);
  const dirty = detail && permKey(draft) !== permKey(detail.permissions);
  const changedCount = detail
    ? draft.filter((p, i) => permKey([p]) !== permKey([detail.permissions[i]])).length
    : 0;

  const selectRole = async (id) => {
    if (id === selectedId) return;
    if (dirty) {
      const ok = await confirm({ title: '저장하지 않은 변경이 있습니다', description: '다른 역할로 옮기면 바꾼 권한이 사라집니다.', confirmText: '버리고 이동', tone: 'danger' });
      if (!ok) return;
    }
    setDetail(null);
    setMembers(null);
    setTab('perm');
    setSelectedId(id);
  };

  const save = async () => {
    setSaving(true);
    try {
      await roleApi.savePermissions(selectedId, draft);
      toast.success(`'${role.name}' 권한을 저장했습니다. 이 역할의 사원에게 바로 적용됩니다.`);
      await loadDetail(selectedId);
      useAuthStore.getState().loadSession();
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  const removeRole = async () => {
    const ok = await confirm({ title: `'${role.name}' 역할을 삭제할까요?`, description: '이 역할을 가진 사원이 있으면 삭제할 수 없습니다.', confirmText: '삭제', tone: 'danger' });
    if (!ok) return;
    try {
      await roleApi.remove(role.id);
      toast.success('역할을 삭제했습니다.');
      const list = await loadRoles();
      setDetail(null);
      setSelectedId(list[0]?.id);
    } catch (err) {
      toast.error(getErrorMessage(err));
    }
  };

  const removeMember = async (user) => {
    const ok = await confirm({ title: `${user.name}님에게서 '${role.name}' 역할을 뺄까요?`, confirmText: '빼기', tone: 'danger' });
    if (!ok) return;
    try {
      await roleApi.removeMember(role.id, user.id);
      toast.success('역할을 뺐습니다.');
      await Promise.all([loadDetail(selectedId), loadRoles()]);
    } catch (err) {
      toast.error(getErrorMessage(err));
    }
  };

  if (loadError) return <EmptyState icon={KeyRound} title="역할을 불러오지 못했습니다" description={loadError} className="py-24" />;
  if (!roles) return <PageLoader />;

  const systemRoles = roles.filter((r) => r.system);
  const customRoles = roles.filter((r) => !r.system);
  const locked = detail?.role.locked;
  const memberIds = new Set((members?.content || []).map((m) => m.id));

  const roleButton = (r) => {
    const active = r.id === selectedId;
    return (
      <li key={r.id}>
        <button
          type="button"
          onClick={() => selectRole(r.id)}
          aria-current={active ? 'true' : undefined}
          className={cn(
            'flex h-10 w-full items-center gap-2 rounded-lg px-3 text-left text-sm transition-colors',
            active ? 'bg-blue-50 font-semibold text-blue-700' : 'font-medium text-gray-700 hover:bg-gray-50'
          )}
        >
          <span className="flex-1 truncate">{r.name}</span>
          {r.locked && <Lock size={13} className="text-gray-400" aria-label="수정 불가" />}
          <span className="text-xs tabular-nums text-gray-500">{r.memberCount}명</span>
        </button>
      </li>
    );
  };

  return (
    <>
      <PageHeader
        title="역할·권한"
        description="역할을 만들고 메뉴마다 할 수 있는 일과 볼 수 있는 데이터 범위를 정합니다. 사원은 역할을 여러 개 가질 수 있습니다."
        actions={canCreate && <Button variant="secondary" icon={Plus} onClick={() => setModal('create')}>역할 만들기</Button>}
      />

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-12">
        <Card className="h-fit lg:col-span-3">
          <CardHeader title={`역할 ${roles.length}`} />
          <div className="space-y-3 p-2">
            <div>
              <p className="px-3 pb-1 pt-1 text-xs font-medium text-gray-500">기본 역할</p>
              <ul className="space-y-0.5">{systemRoles.map(roleButton)}</ul>
            </div>
            <div>
              <p className="px-3 pb-1 pt-1 text-xs font-medium text-gray-500">직접 만든 역할</p>
              {customRoles.length ? (
                <ul className="space-y-0.5">{customRoles.map(roleButton)}</ul>
              ) : (
                <p className="px-3 py-2 text-[13px] text-gray-500">아직 없습니다.</p>
              )}
            </div>
          </div>
        </Card>

        <Card className="overflow-hidden lg:col-span-9">
          {!detail || !role ? (
            <PageLoader />
          ) : (
            <>
              <div className="flex flex-col gap-3 px-5 pt-5 sm:flex-row sm:items-start sm:justify-between">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <h2 className="text-lg font-semibold text-gray-900">{role.name}</h2>
                    <Badge tone="gray">{role.system ? '기본 역할' : '직접 만든 역할'}</Badge>
                  </div>
                  <p className="mt-1 text-sm text-gray-500">{role.description || '설명이 없습니다.'}</p>
                </div>
                <div className="flex shrink-0 flex-wrap items-center gap-2">
                  {dirty && <span className="text-[13px] font-medium text-amber-700">저장 안 된 변경 {changedCount}건</span>}
                  {!role.system && canUpdate && <Button variant="ghost" size="sm" icon={Pencil} onClick={() => setModal('edit')}>정보 수정</Button>}
                  {!role.system && canDelete && <Button variant="danger-outline" size="sm" icon={Trash2} onClick={removeRole}>삭제</Button>}
                  {canUpdate && !locked && (
                    <>
                      {dirty && <Button variant="secondary" size="sm" onClick={() => setDraft(detail.permissions)}>되돌리기</Button>}
                      <Button size="sm" onClick={save} loading={saving} disabled={!dirty}>저장</Button>
                    </>
                  )}
                </div>
              </div>

              <div role="tablist" aria-label="역할 정보" className="mt-4 flex gap-1 border-b border-gray-200 px-5">
                {[
                  { id: 'perm', label: '권한' },
                  { id: 'members', label: `사원 ${members?.page.totalElements ?? 0}` },
                ].map((t) => (
                  <button
                    key={t.id}
                    type="button"
                    role="tab"
                    aria-selected={tab === t.id}
                    onClick={() => setTab(t.id)}
                    className={cn(
                      '-mb-px h-11 border-b-2 px-3 text-sm font-semibold transition-colors',
                      tab === t.id ? 'border-blue-600 text-gray-900' : 'border-transparent text-gray-500 hover:text-gray-800'
                    )}
                  >
                    {t.label}
                  </button>
                ))}
              </div>

              {tab === 'perm' ? (
                <div>
                  {locked && (
                    <p className="mx-5 mt-4 flex items-start gap-2 rounded-lg border border-gray-200 bg-gray-50 px-3.5 py-3 text-[13px] text-gray-700">
                      <Lock size={15} className="mt-px shrink-0 text-gray-500" />
                      관리소장은 모든 권한을 가지며 바꿀 수 없습니다. 관리소장이 한 명도 없게 되는 변경(역할 빼기, 퇴사 처리)은 막힙니다.
                    </p>
                  )}
                  <p className="px-5 pb-3 pt-4 text-[13px] text-gray-500">
                    메뉴 권한은 화면 메뉴와 서버 API에 똑같이 적용됩니다. <b className="font-semibold text-gray-700">데이터 범위</b>는 같은 메뉴에서 누구의 데이터까지 보이는지를 정합니다.
                  </p>
                  <PermissionMatrix menus={menus} permissions={draft} locked={locked} editable={canUpdate} onChange={setDraft} />
                </div>
              ) : (
                <div>
                  {canUpdate && (
                    <div className="flex justify-end px-5 py-3">
                      <Button variant="secondary" size="sm" icon={UserPlus} onClick={() => setModal('members')}>사원 추가</Button>
                    </div>
                  )}
                  <Table>
                    <thead>
                      <tr>
                        <Th>이름</Th>
                        <Th>아이디</Th>
                        <Th>부서</Th>
                        <Th>직급</Th>
                        {canUpdate && <Th align="right"><span className="sr-only">빼기</span></Th>}
                      </tr>
                    </thead>
                    <tbody>
                      {members.content.length === 0 ? (
                        <TableEmpty colSpan={canUpdate ? 5 : 4}>이 역할을 가진 사원이 없습니다.</TableEmpty>
                      ) : (
                        members.content.map((m) => (
                          <Tr key={m.id}>
                            <Td>
                              <span className="flex items-center gap-2.5">
                                <Avatar name={m.name} size="sm" />
                                <span className="font-medium text-gray-900">{m.name}</span>
                              </span>
                            </Td>
                            <Td className="tabular-nums text-gray-500">{m.loginId}</Td>
                            <Td>{m.department?.name}</Td>
                            <Td className="text-gray-600">{m.position}</Td>
                            {canUpdate && (
                              <Td align="right">
                                <Button variant="ghost" size="sm" icon={UserMinus} onClick={() => removeMember(m)}>빼기</Button>
                              </Td>
                            )}
                          </Tr>
                        ))
                      )}
                    </tbody>
                  </Table>
                </div>
              )}
            </>
          )}
        </Card>
      </div>

      <RoleFormModal
        open={modal === 'create' || modal === 'edit'}
        role={modal === 'edit' ? role : null}
        onClose={() => setModal(null)}
        onSaved={async (saved) => {
          setModal(null);
          await loadRoles();
          if (saved?.id) setSelectedId(saved.id);
        }}
      />
      <AddMembersModal
        open={modal === 'members'}
        role={role}
        existingIds={memberIds}
        onClose={() => setModal(null)}
        onAdded={async () => {
          setModal(null);
          await Promise.all([loadDetail(selectedId), loadRoles()]);
        }}
      />
    </>
  );
}
