import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ChevronLeft, ChevronRight, History, NotebookText, Play, Scale, Search } from 'lucide-react';
import { leaveAdminApi } from '@/api/m3';
import { collectSubtreeIds, departmentApi, flattenTree } from '@/api/m1';
import { useAuthStore } from '@/store/authStore';
import { MENU } from '@/lib/permissions';
import { LEDGER_TYPE_META, formatDate, formatDateTime, formatDays, formatSignedDays } from '@/lib/approval';
import { todayLocal } from '@/lib/attendance';
import { getErrorMessage } from '@/lib/errors';
import { toast } from '@/lib/toast';
import { cn } from '@/lib/cn';
import {
  Badge, Button, Card, CardHeader, EmptyState, Field, Input, Modal, PageHeader, PageLoader, Select, Table, TableEmpty, Td, Th, Tr,
} from '@/components/ui';

const PAGE_SIZE = 15;

/** 원장 보기 */
function LedgerModal({ row, year, onClose }) {
  const [rows, setRows] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    if (!row) return undefined;
    let alive = true;
    setRows(null);
    leaveAdminApi.ledger(row.user.id, year).then((r) => alive && setRows(r)).catch((err) => alive && setError(getErrorMessage(err)));
    return () => { alive = false; };
  }, [row, year]);
  if (!row) return null;
  return (
    <Modal open onClose={onClose} size="xl" title={`${row.user.name}의 ${year}년 연차 원장`} description={`사용 가능 ${formatDays(row.available)} · 부여 ${formatDays(row.granted)} · 사용 ${formatDays(row.used)} · 결재 중 ${formatDays(row.reserved)}`} footer={<Button variant="secondary" onClick={onClose}>닫기</Button>}>
      {error ? <p className="text-[13px] text-red-600">{error}</p> : !rows ? <PageLoader /> : (
        <div className="max-h-[60vh] overflow-y-auto">
          <Table>
            <thead><tr><Th>날짜</Th><Th>구분</Th><Th>내용</Th><Th>문서</Th><Th align="right">증감</Th><Th align="right">잔여</Th></tr></thead>
            <tbody>
              {rows.length === 0 ? <TableEmpty colSpan={6}>기록이 없습니다.</TableEmpty> : rows.map((l, i) => {
                const meta = LEDGER_TYPE_META[l.type] || { label: l.type, tone: 'gray' };
                return (
                  <Tr key={l.id ?? i}>
                    <Td className="tabular-nums text-gray-500">{formatDate(l.date)}</Td>
                    <Td><Badge tone={meta.tone}>{meta.label}</Badge></Td>
                    <Td className="text-gray-900">{l.memo || '-'}</Td>
                    <Td>{l.document ? <Link to={`/approvals/${l.document.id}`} className="tabular-nums text-blue-700 hover:underline">{l.document.docNo}</Link> : <span className="text-xs text-gray-400">-</span>}</Td>
                    <Td align="right" className={cn('tabular-nums font-medium', l.days > 0 ? 'text-emerald-700' : 'text-red-600')}>{formatSignedDays(l.days)}</Td>
                    <Td align="right" className="tabular-nums">{formatDays(l.balance).replace('일', '')}</Td>
                  </Tr>
                );
              })}
            </tbody>
          </Table>
        </div>
      )}
    </Modal>
  );
}

/** 수동 조정 (ADJUST 행) */
function AdjustModal({ row, year, onClose, onDone }) {
  const [sign, setSign] = useState('-');
  const [amount, setAmount] = useState('1');
  const [memo, setMemo] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    setSign('-');
    setAmount('1');
    setMemo('');
    setError('');
  }, [row]);

  if (!row) return null;
  const n = Number(amount);
  const days = sign === '-' ? -n : n;
  const amountOk = n > 0 && Math.round(n * 2) === n * 2;
  const valid = amountOk && memo.trim();
  // 미리보기는 일수만 맞으면 바로 보여 줌 (사유는 저장할 때만 필요)
  const after = Math.round((row.available + (amountOk ? days : 0)) * 10) / 10;

  const save = async () => {
    setSaving(true);
    setError('');
    try {
      const updated = await leaveAdminApi.adjust({ userId: row.user.id, year, days, memo: memo.trim() });
      toast.success(`${row.user.name}님의 연차를 ${formatSignedDays(days)}일 조정했습니다. 사용 가능 ${formatDays(updated.available)}`);
      onDone();
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      title={`${row.user.name}의 연차 조정`}
      description="원장에 조정 기록을 한 줄 더합니다. 이미 있는 기록은 고치지 않습니다."
      footer={<><Button variant="secondary" onClick={onClose}>취소</Button><Button icon={Scale} loading={saving} disabled={!valid || after < 0} onClick={save}>조정</Button></>}
    >
      <div className="space-y-4">
        <div className="grid grid-cols-[120px_1fr] gap-3">
          <Field label="구분" htmlFor="adj-sign">
            <Select id="adj-sign" value={sign} onChange={(e) => setSign(e.target.value)}>
              <option value="-">빼기 (−)</option>
              <option value="+">더하기 (+)</option>
            </Select>
          </Field>
          <Field label="일수" htmlFor="adj-days" required hint="0.5일 단위">
            <Input id="adj-days" type="number" min="0.5" step="0.5" value={amount} onChange={(e) => setAmount(e.target.value)} trailing="일" />
          </Field>
        </div>
        <Field label="사유" htmlFor="adj-memo" required hint="원장과 사원의 '내 연차' 화면에 그대로 보입니다.">
          <Input id="adj-memo" value={memo} onChange={(e) => setMemo(e.target.value)} placeholder="예: 근태 정정 반영, 포상 휴가" maxLength={200} />
        </Field>
        <p className={cn('rounded-lg px-3.5 py-3 text-[13px]', after < 0 ? 'bg-red-50 text-red-700' : 'bg-gray-50 text-gray-700')}>
          사용 가능 {formatDays(row.available)} → <b className="font-semibold">{formatDays(after)}</b>
          {after < 0 && ' · 0보다 작아지게 조정할 수 없습니다.'}
        </p>
        {error && <p role="alert" className="text-[13px] text-red-600">{error}</p>}
      </div>
    </Modal>
  );
}

/** 연차 부여 배치: 마지막 실행 한 줄 + 지금 실행 (범위 ALL + 수정 권한). 지난 기록은 창으로 */
function GrantRunsBar() {
  const [runs, setRuns] = useState(null);
  const [date, setDate] = useState(todayLocal());
  const [running, setRunning] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);

  const load = useCallback(() => leaveAdminApi.grantRuns(10).then(setRuns).catch(() => setRuns([])), []);
  useEffect(() => { load(); }, [load]);

  const run = async () => {
    setRunning(true);
    try {
      const r = await leaveAdminApi.runGrant(date);
      toast.success(`부여 ${r.processedCount}건${r.skippedCount ? ` · 건너뜀 ${r.skippedCount}건` : ''}. 같은 날짜로 다시 실행해도 두 번 부여되지 않습니다.`);
      load();
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally {
      setRunning(false);
    }
  };

  const last = runs?.[0];
  const statusBadge = (r) => <Badge tone={r.status === 'SUCCESS' ? 'green' : 'red'}>{r.status === 'SUCCESS' ? '성공' : '실패'}</Badge>;

  return (
    <Card className="mb-4 flex flex-col gap-3 px-5 py-3.5 lg:flex-row lg:items-center">
      <div className="min-w-0 flex-1">
        <p className="text-[13px] font-semibold text-gray-900">
          연차 자동 부여 <span className="font-normal text-gray-500">· 매일 00:30에 자동으로 돌고, 놓친 날짜는 다음 실행이 채웁니다.</span>
        </p>
        <p className="mt-1 flex flex-wrap items-center gap-x-1.5 gap-y-1 text-xs text-gray-500">
          {!runs ? '불러오는 중…' : !last ? '실행 기록이 없습니다.' : (
            <>
              마지막 실행 <span className="tabular-nums">{formatDateTime(last.finishedAt)}</span> {statusBadge(last)}
              <span>부여 {last.processedCount}건 · 건너뜀 {last.skippedCount}건</span>
              {last.message && <span className="text-amber-700">· {last.message}</span>}
            </>
          )}
        </p>
      </div>
      <div className="flex flex-wrap gap-2 lg:shrink-0">
        <Button variant="ghost" icon={History} disabled={!runs?.length} onClick={() => setHistoryOpen(true)}>기록 보기</Button>
        <Input type="date" aria-label="부여 기준 날짜" max={todayLocal()} value={date} onChange={(e) => setDate(e.target.value)} />
        <Button variant="secondary" icon={Play} loading={running} onClick={run}>지금 실행</Button>
      </div>

      <Modal open={historyOpen} onClose={() => setHistoryOpen(false)} title="연차 자동 부여 기록" description="최근 10번의 실행입니다." footer={<Button variant="secondary" onClick={() => setHistoryOpen(false)}>닫기</Button>}>
        <ul className="-mx-1 divide-y divide-gray-100">
          {(runs || []).map((r) => (
            <li key={r.id} className="px-1 py-3 text-[13px]">
              <p className="flex items-center justify-between gap-2">
                <span className="font-medium text-gray-900">{formatDate(r.targetDate)} 기준</span>
                {statusBadge(r)}
              </p>
              <p className="mt-0.5 text-xs text-gray-500">부여 {r.processedCount}건 · 건너뜀 {r.skippedCount}건 · {formatDateTime(r.finishedAt)}</p>
              {r.message && <p className="mt-0.5 text-xs text-amber-700">{r.message}</p>}
            </li>
          ))}
        </ul>
      </Modal>
    </Card>
  );
}

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

  useEffect(() => { departmentApi.tree().then(setTree).catch(() => {}); }, []);
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
      setResult(await leaveAdminApi.balances({ year, q: q.trim() || undefined, departmentId: departmentId || undefined, page, size: PAGE_SIZE }));
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
        actions={(
          <label>
            <span className="sr-only">연도</span>
            <Select value={year} onChange={(e) => setYear(Number(e.target.value))}>
              {[thisYear, thisYear - 1, thisYear - 2].map((y) => <option key={y} value={y}>{y}년</option>)}
            </Select>
          </label>
        )}
      />

      {canRun && <GrantRunsBar />}

      <Card className="overflow-hidden">
        <div className="flex flex-col gap-2 border-b border-gray-100 p-4 sm:flex-row">
          <label className="sm:w-64">
            <span className="sr-only">이름 또는 아이디 검색</span>
            <Input icon={Search} type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="이름 또는 아이디 앞부분" />
          </label>
          <label className="sm:w-56">
            <span className="sr-only">부서</span>
            <Select value={departmentId} onChange={(e) => setDepartmentId(e.target.value)}>
              <option value="">{scope === 'ALL' ? '전체 부서' : '볼 수 있는 모든 부서'}</option>
              {filterDepartments.map((d) => <option key={d.id} value={d.id}>{'　'.repeat(d.depth - baseDepth)}{d.name}</option>)}
            </Select>
          </label>
        </div>

        {missing > 0 && (
          <p className="border-b border-amber-100 bg-amber-50 px-4 py-2.5 text-[13px] text-amber-800">
            입사일이 없는 사원 {missing}명은 연차 자동 부여에서 빠집니다. <Link to="/org/employees" className="underline">사원 관리</Link>에서 입사일을 채워 주세요.
          </p>
        )}

        {error ? <EmptyState icon={NotebookText} title="연차 정보를 불러오지 못했습니다" description={error} /> : !result ? <PageLoader /> : (
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
                <Th align="right"><span className="sr-only">동작</span></Th>
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 ? <TableEmpty colSpan={8}>조건에 맞는 사원이 없습니다.</TableEmpty> : rows.map((r) => (
                <Tr key={r.user.id}>
                  <Td>
                    <span className="block font-medium text-gray-900">{r.user.name} <span className="font-normal text-gray-500">{r.user.title}</span></span>
                    <span className="block text-xs text-gray-500">{r.user.department}</span>
                  </Td>
                  <Td className="tabular-nums text-gray-500">{r.hireDateMissing ? <Badge tone="amber">입사일 없음</Badge> : formatDate(r.hireDate)}</Td>
                  <Td align="right" className="tabular-nums">{r.granted}</Td>
                  <Td align="right" className="tabular-nums">{r.used}</Td>
                  <Td align="right" className={cn('tabular-nums', r.reserved > 0 ? 'text-amber-700' : 'text-gray-400')}>{r.reserved}</Td>
                  <Td align="right" className={cn('tabular-nums', r.adjusted ? 'text-gray-900' : 'text-gray-400')}>{r.adjusted > 0 ? `+${r.adjusted}` : r.adjusted}</Td>
                  <Td align="right" className="font-semibold tabular-nums text-gray-900">{r.available}</Td>
                  <Td align="right">
                    <span className="flex justify-end gap-1">
                      <Button variant="ghost" size="sm" onClick={() => setLedgerRow(r)}>원장</Button>
                      {canAdjust && <Button variant="ghost" size="sm" onClick={() => setAdjustRow(r)}>조정</Button>}
                    </span>
                  </Td>
                </Tr>
              ))}
            </tbody>
          </Table>
        )}

        {pageInfo && pageInfo.totalElements > 0 && (
          <div className="flex items-center justify-between border-t border-gray-100 px-4 py-3 text-[13px] text-gray-500">
            <span>총 <b className="font-semibold text-gray-900">{pageInfo.totalElements}</b>명 · {pageInfo.number + 1} / {pageInfo.totalPages} 쪽</span>
            <div className="flex gap-1">
              <Button variant="secondary" size="sm" icon={ChevronLeft} disabled={pageInfo.first} onClick={() => setPage((p) => p - 1)} aria-label="이전 쪽" />
              <Button variant="secondary" size="sm" icon={ChevronRight} disabled={pageInfo.last} onClick={() => setPage((p) => p + 1)} aria-label="다음 쪽" />
            </div>
          </div>
        )}
      </Card>

      <LedgerModal row={ledgerRow} year={year} onClose={() => setLedgerRow(null)} />
      <AdjustModal row={adjustRow} year={year} onClose={() => setAdjustRow(null)} onDone={() => { setAdjustRow(null); load(); }} />
    </>
  );
}
