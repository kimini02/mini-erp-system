import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ChevronLeft, ChevronRight, Pencil, Search, UserPlus, Users } from 'lucide-react';
import { collectSubtreeIds, flattenTree } from '@/lib/orgTree';
import { departmentApi, positionApi } from '@/api/org';
import { roleApi } from '@/api/roles';
import { userApi } from '@/api/users';
import { useAuthStore } from '@/store/authStore';
import { MENU, USER_STATUS_META } from '@/lib/permissions';
import { getErrorMessage } from '@/lib/errors';
import { cn } from '@/lib/cn';
import {
  Avatar,
  Badge,
  Button,
  Card,
  EmptyState,
  Input,
  PageHeader,
  PageLoader,
  Select,
  Table,
  TableEmpty,
  Td,
  Th,
  Tr,
} from '@/components/ui';
import { STATUS_TABS, roleTone } from './employees/helpers';
import EmployeeCreateModal from './employees/EmployeeCreateModal';
import EmployeeEditModal from './employees/EmployeeEditModal';

const PAGE_SIZE = 15;
function useDebounced(value, delay) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(id);
  }, [value, delay]);
  return debounced;
}

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
    departmentApi
      .tree()
      .then(setTree)
      .catch(() => {});
    positionApi
      .list()
      .then(setPositions)
      .catch(() => {});
    if (canAssignRoles)
      roleApi
        .list()
        .then(setRoles)
        .catch(() => {});
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
        actions={
          canCreate && (
            <Button icon={UserPlus} onClick={() => setCreating(true)}>
              사원 등록
            </Button>
          )
        }
      />

      <Card className="overflow-hidden">
        <div className="flex flex-col gap-3 border-b border-gray-100 p-4 lg:flex-row lg:items-center">
          <div className="flex flex-1 flex-col gap-2 sm:flex-row">
            <label className="sm:w-72">
              <span className="sr-only">이름 또는 아이디 검색</span>
              <Input
                icon={Search}
                type="search"
                value={q}
                onChange={(e) => setQ(e.target.value)}
                placeholder="이름 또는 아이디 앞부분"
              />
            </label>
            <label className="sm:w-56">
              <span className="sr-only">부서</span>
              <Select value={departmentId} onChange={(e) => setDepartmentId(e.target.value)}>
                <option value="">{scope === 'ALL' ? '전체 부서' : '볼 수 있는 모든 부서'}</option>
                {filterDepartments.map((d) => (
                  <option key={d.id} value={d.id}>
                    {'　'.repeat(d.depth)}
                    {d.name}
                  </option>
                ))}
              </Select>
            </label>
            {departmentId && (
              <label className="flex h-10 items-center gap-2 text-[13px] text-gray-600">
                <input
                  type="checkbox"
                  checked={includeSub}
                  onChange={(e) => setIncludeSub(e.target.checked)}
                  className="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                />
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
                    active
                      ? 'bg-white text-gray-900 shadow-sm ring-1 ring-gray-200'
                      : 'text-gray-600 hover:text-gray-900',
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
                {canUpdate && (
                  <Th align="right">
                    <span className="sr-only">수정</span>
                  </Th>
                )}
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
                        {u.roles.map((r) => (
                          <Badge key={r.code} tone={roleTone(r.code)}>
                            {r.name}
                          </Badge>
                        ))}
                      </span>
                    </Td>
                    <Td className="tabular-nums text-gray-500">
                      {u.hireDate || <span className="text-amber-700">미입력</span>}
                    </Td>
                    <Td>
                      <span className="flex flex-wrap gap-1">
                        <Badge tone={USER_STATUS_META[u.status].tone} dot>
                          {USER_STATUS_META[u.status].label}
                        </Badge>
                        {u.status === 'RETIRED' && u.retiredDate && (
                          <span className="text-xs tabular-nums text-gray-500">{u.retiredDate}</span>
                        )}
                        {u.invitationPending && <Badge tone="blue">첫 로그인 전</Badge>}
                      </span>
                    </Td>
                    {canUpdate && (
                      <Td align="right">
                        <Button
                          variant="ghost"
                          size="sm"
                          icon={Pencil}
                          onClick={() => setEditing(u)}
                          aria-label={`${u.name} 정보 수정`}
                        >
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
              총 <b className="font-semibold text-gray-900">{pageInfo.totalElements}</b>명 · {pageInfo.number + 1} /{' '}
              {pageInfo.totalPages} 쪽
            </span>
            <div className="flex gap-1">
              <Button
                variant="secondary"
                size="sm"
                icon={ChevronLeft}
                disabled={pageInfo.first}
                onClick={() => setPage((p) => p - 1)}
                aria-label="이전 쪽"
              />
              <Button
                variant="secondary"
                size="sm"
                icon={ChevronRight}
                disabled={pageInfo.last}
                onClick={() => setPage((p) => p + 1)}
                aria-label="다음 쪽"
              />
            </div>
          </div>
        )}
      </Card>

      <EmployeeCreateModal
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
      <EmployeeEditModal
        user={editing}
        onClose={() => setEditing(null)}
        departments={departments}
        positions={positions}
        roles={roles}
        canAssignRoles={canAssignRoles}
        canInvite={canCreate}
        canManagePrivileged={can(MENU.ROLE, 'U')}
        onSaved={(opts) => {
          if (!opts?.keepOpen) setEditing(null);
          load();
        }}
      />
    </>
  );
}
