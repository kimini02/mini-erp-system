import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ChevronLeft, ChevronRight, NotebookText, Search } from 'lucide-react';
import { leaveAdminApi } from '@/api/leave';
import { collectSubtreeIds, flattenTree } from '@/lib/orgTree';
import { departmentApi } from '@/api/org';
import { useAuthStore } from '@/store/authStore';
import { MENU } from '@/lib/permissions';
import { formatDate } from '@/lib/approval';
import { getErrorMessage } from '@/lib/errors';
import { cn } from '@/lib/cn';
import {
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
import LeaveLedgerModal from './admin/LeaveLedgerModal';
import LeaveAdjustModal from './admin/LeaveAdjustModal';
import GrantRunsBar from './admin/GrantRunsBar';

const PAGE_SIZE = 15;

export default function LeaveAdminPage() {
  const can = useAuthStore((s) => s.can);
  const scope = useAuthStore((s) => s.scopeOf(MENU.LEAVE_ADMIN));
  const myDepartmentId = useAuthStore((s) => s.user?.department?.id);
  const canAdjust = can(MENU.LEAVE_ADMIN, 'C');
  const canRun = can(MENU.LEAVE_ADMIN, 'U') && scope === 'ALL';
  const thisYear = new Date().getFullYear();

  const [year, setYear] = useState(thisYear);
  const [q, setQ] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [page, setPage] = useState(0);
  const [tree, setTree] = useState(null);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [ledgerRow, setLedgerRow] = useState(null);
  const [adjustRow, setAdjustRow] = useState(null);

  useEffect(() => {
    departmentApi
      .tree()
      .then(setTree)
      .catch(() => {});
  }, []);
  const departments = useMemo(() => flattenTree(tree), [tree]);
  const filterDepartments = useMemo(() => {
    if (!tree || scope === 'ALL' || !myDepartmentId) return departments;
    const visible = scope === 'DEPT_TREE' ? collectSubtreeIds(tree, myDepartmentId) : new Set([myDepartmentId]);
    return departments.filter((d) => visible.has(d.id));
  }, [tree, departments, scope, myDepartmentId]);
  const baseDepth = filterDepartments.length ? Math.min(...filterDepartments.map((d) => d.depth)) : 0;

  useEffect(() => setPage(0), [year, q, departmentId]);

  const load = useCallback(async () => {
    try {
      setResult(
        await leaveAdminApi.balances({
          year,
          q: q.trim() || undefined,
          departmentId: departmentId || undefined,
          page,
          size: PAGE_SIZE,
        }),
      );
      setError('');
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }, [year, q, departmentId, page]);

  useEffect(() => {
    const id = setTimeout(load, 250);
    return () => clearTimeout(id);
  }, [load]);

  const rows = result?.content || [];
  const pageInfo = result?.page;
  const missing = rows.filter((r) => r.hireDateMissing).length;

  return (
    <>
      <PageHeader
        title="연차 관리"
        description="사원별 연차를 보고, 원장을 확인하고, 필요하면 조정합니다. 모든 변경은 원장에 한 줄씩 쌓입니다."
        actions={
          <label>
            <span className="sr-only">연도</span>
            <Select value={year} onChange={(e) => setYear(Number(e.target.value))}>
              {[thisYear, thisYear - 1, thisYear - 2].map((y) => (
                <option key={y} value={y}>
                  {y}년
                </option>
              ))}
            </Select>
          </label>
        }
      />

      {canRun && <GrantRunsBar />}

      <Card className="overflow-hidden">
        <div className="flex flex-col gap-2 border-b border-gray-100 p-4 sm:flex-row">
          <label className="sm:w-64">
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
                  {'　'.repeat(d.depth - baseDepth)}
                  {d.name}
                </option>
              ))}
            </Select>
          </label>
        </div>

        {missing > 0 && (
          <p className="border-b border-amber-100 bg-amber-50 px-4 py-2.5 text-[13px] text-amber-800">
            입사일이 없는 사원 {missing}명은 연차 자동 부여에서 빠집니다.{' '}
            <Link to="/org/employees" className="underline">
              사원 관리
            </Link>
            에서 입사일을 채워 주세요.
          </p>
        )}

        {error ? (
          <EmptyState icon={NotebookText} title="연차 정보를 불러오지 못했습니다" description={error} />
        ) : !result ? (
          <PageLoader />
        ) : (
          <Table>
            <thead>
              <tr>
                <Th>사원</Th>
                <Th>입사일</Th>
                <Th align="right">부여</Th>
                <Th align="right">사용</Th>
                <Th align="right">결재 중</Th>
                <Th align="right">조정</Th>
                <Th align="right">사용 가능</Th>
                <Th align="right">
                  <span className="sr-only">동작</span>
                </Th>
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 ? (
                <TableEmpty colSpan={8}>조건에 맞는 사원이 없습니다.</TableEmpty>
              ) : (
                rows.map((r) => (
                  <Tr key={r.user.id}>
                    <Td>
                      <span className="block font-medium text-gray-900">
                        {r.user.name} <span className="font-normal text-gray-500">{r.user.title}</span>
                      </span>
                      <span className="block text-xs text-gray-500">{r.user.department}</span>
                    </Td>
                    <Td className="tabular-nums text-gray-500">
                      {r.hireDateMissing ? <Badge tone="amber">입사일 없음</Badge> : formatDate(r.hireDate)}
                    </Td>
                    <Td align="right" className="tabular-nums">
                      {r.granted}
                    </Td>
                    <Td align="right" className="tabular-nums">
                      {r.used}
                    </Td>
                    <Td
                      align="right"
                      className={cn('tabular-nums', r.reserved > 0 ? 'text-amber-700' : 'text-gray-400')}
                    >
                      {r.reserved}
                    </Td>
                    <Td align="right" className={cn('tabular-nums', r.adjusted ? 'text-gray-900' : 'text-gray-400')}>
                      {r.adjusted > 0 ? `+${r.adjusted}` : r.adjusted}
                    </Td>
                    <Td align="right" className="font-semibold tabular-nums text-gray-900">
                      {r.available}
                    </Td>
                    <Td align="right">
                      <span className="flex justify-end gap-1">
                        <Button variant="ghost" size="sm" onClick={() => setLedgerRow(r)}>
                          원장
                        </Button>
                        {canAdjust && (
                          <Button variant="ghost" size="sm" onClick={() => setAdjustRow(r)}>
                            조정
                          </Button>
                        )}
                      </span>
                    </Td>
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

      <LeaveLedgerModal row={ledgerRow} year={year} onClose={() => setLedgerRow(null)} />
      <LeaveAdjustModal
        row={adjustRow}
        year={year}
        onClose={() => setAdjustRow(null)}
        onDone={() => {
          setAdjustRow(null);
          load();
        }}
      />
    </>
  );
}
