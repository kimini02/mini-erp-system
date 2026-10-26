import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { CalendarRange, ChevronLeft, ChevronRight, Download, RefreshCcw } from 'lucide-react';
import { attendanceApi, saveBlob } from '@/api/m3';
import { collectSubtreeIds, departmentApi, flattenTree } from '@/api/m1';
import { useAuthStore } from '@/store/authStore';
import { MENU } from '@/lib/permissions';
import { CELL_META, LEGEND_CODES, WEEKDAY_SHORT, monthLabel, shiftDate, shiftMonth, thisMonth, timeOf, todayLocal } from '@/lib/attendance';
import { formatDateTime, formatDay } from '@/lib/approval';
import { getErrorMessage } from '@/lib/errors';
import { toast } from '@/lib/toast';
import { cn } from '@/lib/cn';
import { Badge, Button, Card, EmptyState, Field, Input, Modal, PageHeader, PageLoader, Select } from '@/components/ui';

/** 칸 상세: 그날 기록과 판정 근거 (명세 3.2) */
function CellDetail({ target, onClose }) {
  const [detail, setDetail] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!target) return undefined;
    let alive = true;
    setDetail(null);
    setError('');
    attendanceApi.record(target.user.id, target.date)
      .then((d) => alive && setDetail(d))
      .catch((err) => alive && setError(getErrorMessage(err)));
    return () => { alive = false; };
  }, [target]);

  if (!target) return null;
  const meta = CELL_META[detail?.code || target.code] || CELL_META.NONE;
  return (
    <Modal open onClose={onClose} title={`${target.user.name} · ${formatDay(target.date)}`} description={meta.label || '기록 없음'} footer={<Button variant="secondary" onClick={onClose}>닫기</Button>}>
      {error ? <p className="text-[13px] text-red-600">{error}</p> : !detail ? <PageLoader /> : (
        <dl className="divide-y divide-gray-100 text-sm">
          {[
            ['출근', timeOf(detail.clockInAt) || '-'],
            ['퇴근', timeOf(detail.clockOutAt) || '-'],
            ['기준 근무시간', `${detail.schedule.startTime} ~ ${detail.schedule.endTime} · 유예 ${detail.schedule.lateGraceMin}분 (반차 경계 ${detail.schedule.halfBoundary})`],
            ['연결 문서', detail.leaveDocument || detail.fixDocument ? (
              <span className="flex flex-wrap gap-2">
                {detail.leaveDocument && <Link to={`/approvals/${detail.leaveDocument.id}`} className="text-blue-700 hover:underline">휴가 {detail.leaveDocument.docNo}</Link>}
                {detail.fixDocument && <Link to={`/approvals/${detail.fixDocument.id}`} className="text-blue-700 hover:underline">정정 {detail.fixDocument.docNo}</Link>}
              </span>
            ) : '없음'],
            ['판정 근거', detail.reason],
            ['판정 시각', detail.judgedAt ? formatDateTime(detail.judgedAt) : '-'],
          ].map(([label, value]) => (
            <div key={label} className="grid grid-cols-[96px_1fr] gap-3 py-2.5">
              <dt className="text-gray-500">{label}</dt>
              <dd className="text-gray-900">{value}</dd>
            </div>
          ))}
        </dl>
      )}
    </Modal>
  );
}

export default function AttendanceMonthlyPage() {
  const can = useAuthStore((s) => s.can);
  const scope = useAuthStore((s) => s.scopeOf(MENU.ATTENDANCE_MONTHLY));
  const myDepartmentId = useAuthStore((s) => s.user?.department?.id);
  const canRejudge = can(MENU.ATTENDANCE_MONTHLY, 'U') && scope === 'ALL';

  const [month, setMonth] = useState(thisMonth());
  const [departmentId, setDepartmentId] = useState('');
  const [includeSub, setIncludeSub] = useState(true);
  const [tree, setTree] = useState(null);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState(null);
  const [rejudgeOpen, setRejudgeOpen] = useState(false);
  const [rejudgeDate, setRejudgeDate] = useState(shiftDate(todayLocal(), -1));
  const [busy, setBusy] = useState('');

  useEffect(() => {
    departmentApi.tree().then(setTree).catch(() => {});
  }, []);

  const departments = useMemo(() => flattenTree(tree), [tree]);
  // 내 데이터 범위 안의 부서만 고를 수 있음 (범위 밖은 서버가 403)
  const filterDepartments = useMemo(() => {
    if (!tree || scope === 'ALL' || !myDepartmentId) return departments;
    const visible = scope === 'DEPT_TREE' ? collectSubtreeIds(tree, myDepartmentId) : new Set([myDepartmentId]);
    return departments.filter((d) => visible.has(d.id));
  }, [tree, departments, scope, myDepartmentId]);
  // 고를 수 있는 가장 위 부서를 들여쓰기 없이 보여 줌
  const baseDepth = filterDepartments.length ? Math.min(...filterDepartments.map((d) => d.depth)) : 0;

  const params = useMemo(() => ({ month, departmentId: departmentId || undefined, includeSubDepartments: departmentId ? includeSub : undefined }), [month, departmentId, includeSub]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await attendanceApi.monthly(params));
      setError('');
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [params]);

  useEffect(() => { load(); }, [load]);

  const exportCsv = async () => {
    setBusy('csv');
    try {
      const { blob, filename } = await attendanceApi.exportCsv(params);
      saveBlob(blob, filename);
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally {
      setBusy('');
    }
  };

  const rejudge = async () => {
    setBusy('rejudge');
    try {
      const run = await attendanceApi.rejudge(rejudgeDate);
      toast.success(`${formatDay(rejudgeDate)}을 다시 판정했습니다 (새로 만든 기록 ${run.processedCount}건).`);
      setRejudgeOpen(false);
      load();
    } catch (err) {
      toast.error(getErrorMessage(err));
    } finally {
      setBusy('');
    }
  };

  const today = todayLocal();

  return (
    <>
      <PageHeader
        title="월간 근태표"
        description="한 달 근태를 사원 × 날짜로 봅니다. 칸을 누르면 그날 기록과 판정 근거가 나옵니다."
        actions={(
          <div className="flex gap-2">
            {canRejudge && <Button variant="secondary" icon={RefreshCcw} onClick={() => setRejudgeOpen(true)}>다시 판정</Button>}
            <Button variant="secondary" icon={Download} loading={busy === 'csv'} onClick={exportCsv}>엑셀(CSV) 내려받기</Button>
          </div>
        )}
      />

      <Card className="overflow-hidden">
        <div className="flex flex-col gap-3 border-b border-gray-100 p-4 lg:flex-row lg:items-center">
          <div className="flex items-center gap-2">
            <Button variant="secondary" size="sm" icon={ChevronLeft} onClick={() => setMonth((m) => shiftMonth(m, -1))} aria-label="이전 달" />
            <span className="w-28 text-center text-sm font-semibold text-gray-900">{monthLabel(month)}</span>
            <Button variant="secondary" size="sm" icon={ChevronRight} disabled={month >= thisMonth()} onClick={() => setMonth((m) => shiftMonth(m, 1))} aria-label="다음 달" />
          </div>
          <label className="sm:w-56">
            <span className="sr-only">부서</span>
            <Select value={departmentId} onChange={(e) => setDepartmentId(e.target.value)}>
              <option value="">{scope === 'ALL' ? '전체 부서' : '볼 수 있는 모든 부서'}</option>
              {filterDepartments.map((d) => <option key={d.id} value={d.id}>{'　'.repeat(d.depth - baseDepth)}{d.name}</option>)}
            </Select>
          </label>
          {departmentId && (
            <label className="flex items-center gap-2 text-[13px] text-gray-600">
              <input type="checkbox" checked={includeSub} onChange={(e) => setIncludeSub(e.target.checked)} className="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500" />
              하위 부서 포함
            </label>
          )}
          <ul className="flex flex-wrap gap-x-3 gap-y-1 lg:ml-auto" aria-label="범례">
            {LEGEND_CODES.map((code) => (
              <li key={code} className="flex items-center gap-1 text-xs text-gray-600">
                <span className={cn('flex h-4 w-4 items-center justify-center rounded text-[10px] font-semibold ring-1 ring-inset ring-gray-200', CELL_META[code].className)}>{CELL_META[code].short}</span>
                {CELL_META[code].label}
              </li>
            ))}
          </ul>
        </div>

        {error ? (
          <EmptyState icon={CalendarRange} title="근태표를 불러오지 못했습니다" description={error} />
        ) : !data ? (
          <PageLoader />
        ) : data.rows.length === 0 ? (
          <EmptyState icon={CalendarRange} title="이 조건에 맞는 사원이 없습니다" description="부서나 달을 바꿔 보세요." />
        ) : (
          <div className={cn('overflow-x-auto', loading && 'opacity-60')}>
            <table className="w-max min-w-full border-separate border-spacing-0 text-xs">
              <thead>
                <tr>
                  <th className="sticky left-0 z-10 w-36 border-b border-r border-gray-100 bg-white px-3 py-2 text-left font-medium text-gray-500">사원</th>
                  {data.days.map((d) => {
                    const off = d.dayType !== 'WORKDAY';
                    return (
                      <th key={d.date} title={d.holidayName || undefined} className={cn('w-7 border-b border-gray-100 px-0 py-1.5 text-center font-normal', d.date === today && 'bg-blue-50', off ? 'text-red-500' : 'text-gray-500')}>
                        <span className="block font-medium tabular-nums">{Number(d.date.slice(8))}</span>
                        <span className="block text-[10px]">{WEEKDAY_SHORT[d.weekday]}</span>
                      </th>
                    );
                  })}
                  {['지각', '조퇴', '결근', '누락', '휴가'].map((h) => (
                    <th key={h} className="w-10 border-b border-l border-gray-100 px-1 py-2 text-center font-medium text-gray-500">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {data.rows.map((r) => (
                  <tr key={r.user.id} className="group">
                    <td className="sticky left-0 z-10 border-b border-r border-gray-100 bg-white px-3 py-1.5 group-hover:bg-gray-50">
                      <span className="block truncate text-[13px] font-medium text-gray-900">{r.user.name}</span>
                      <span className="block truncate text-[11px] text-gray-500">{r.user.department} · {r.user.title}</span>
                    </td>
                    {r.cells.map((c) => {
                      const meta = CELL_META[c.code] || CELL_META.NONE;
                      const clickable = c.code !== 'FUTURE';
                      return (
                        <td key={c.date} className={cn('border-b border-gray-100 p-0.5', c.date === today && 'bg-blue-50/50')}>
                          <button
                            type="button"
                            disabled={!clickable}
                            onClick={() => setSelected({ user: r.user, date: c.date, code: c.code })}
                            aria-label={`${r.user.name} ${Number(c.date.slice(5, 7))}월 ${Number(c.date.slice(8))}일 ${meta.label || '기록 없음'}`}
                            className={cn('flex h-7 w-7 items-center justify-center rounded text-[11px] font-semibold transition hover:ring-2 hover:ring-blue-300 disabled:cursor-default disabled:hover:ring-0', meta.className)}
                          >
                            {meta.short}
                          </button>
                        </td>
                      );
                    })}
                    {[r.totals.late, r.totals.early, r.totals.absent, r.totals.missing, r.totals.leaveDays].map((v, i) => (
                      <td key={i} className={cn('border-b border-l border-gray-100 px-1 text-center tabular-nums', v > 0 && i < 4 ? 'font-semibold text-gray-900' : 'text-gray-400')}>{v}</td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {data?.rows.length > 0 && <p className="border-t border-gray-100 px-4 py-3 text-xs text-gray-500">총 {data.rows.length}명 · 결근과 퇴근 누락은 다음 날 새벽에 확정됩니다.</p>}
      </Card>

      <CellDetail target={selected} onClose={() => setSelected(null)} />

      <Modal
        open={rejudgeOpen}
        onClose={() => setRejudgeOpen(false)}
        title="그날을 다시 판정할까요?"
        description="공휴일을 늦게 등록했거나 휴가가 뒤늦게 승인된 날을 다시 계산합니다. 이미 정정 결재로 바뀐 기록은 그대로 둡니다."
        footer={<><Button variant="secondary" onClick={() => setRejudgeOpen(false)}>취소</Button><Button icon={RefreshCcw} loading={busy === 'rejudge'} onClick={rejudge}>다시 판정</Button></>}
      >
        <Field label="날짜" htmlFor="rejudge-date" required>
          <Input id="rejudge-date" type="date" max={shiftDate(today, -1)} value={rejudgeDate} onChange={(e) => setRejudgeDate(e.target.value)} />
        </Field>
      </Modal>
    </>
  );
}
