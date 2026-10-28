import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ChevronLeft, ChevronRight, Clock3, FilePen, LogIn, LogOut } from 'lucide-react';
import { attendanceApi } from '@/api/m3';
import { useAuthStore } from '@/store/authStore';
import { MENU } from '@/lib/permissions';
import {
  CELL_META,
  DAY_TYPE_LABELS,
  FIXABLE_CODES,
  dayCode,
  IN_RESULT_LABELS,
  OUT_RESULT_LABELS,
  monthLabel,
  shiftDate,
  shiftMonth,
  thisMonth,
  timeOf,
  todayLocal,
} from '@/lib/attendance';
import { formatDay } from '@/lib/approval';
import { getErrorMessage } from '@/lib/errors';
import { toast } from '@/lib/toast';
import { cn } from '@/lib/cn';
import {
  Badge,
  Button,
  Card,
  CardHeader,
  EmptyState,
  Modal,
  PageHeader,
  PageLoader,
  Table,
  TableEmpty,
  Td,
  Th,
  Tr,
} from '@/components/ui';

const resultTone = (r) => (r === 'ON_TIME' ? 'green' : r === 'NONE' || !r ? 'gray' : 'amber');

/** 오늘 근무 카드 (홈 위젯과 같은 내용) */
function TodayCard({ today, onClockIn, onClockOut, busy }) {
  const s = today.schedule;
  const dayBadge = today.dayType !== 'WORKDAY' && (
    <Badge tone={today.dayType === 'HOLIDAY' ? 'gray' : 'blue'}>
      {today.holidayName || DAY_TYPE_LABELS[today.dayType]}
    </Badge>
  );
  return (
    <Card>
      <CardHeader title="오늘 근무" description={formatDay(today.workDate)} action={dayBadge} />
      <div className="grid grid-cols-2 divide-x divide-gray-100 border-b border-gray-100">
        {[
          { label: '출근', value: timeOf(today.clockInAt), result: today.inResult, labels: IN_RESULT_LABELS },
          { label: '퇴근', value: timeOf(today.clockOutAt), result: today.outResult, labels: OUT_RESULT_LABELS },
        ].map((x) => (
          <div key={x.label} className="px-5 py-4">
            <p className="text-xs text-gray-500">{x.label}</p>
            <p className={cn('mt-1 text-2xl font-semibold tabular-nums', x.value ? 'text-gray-900' : 'text-gray-300')}>
              {x.value || '--:--'}
            </p>
            {x.result && (
              <Badge tone={resultTone(x.result)} className="mt-1.5">
                {x.labels[x.result] || x.result}
              </Badge>
            )}
          </div>
        ))}
      </div>
      <div className="space-y-3 px-5 py-4">
        {today.leave && (
          <p className="rounded-lg bg-blue-50 px-3 py-2 text-[13px] text-blue-800">
            오늘은 승인된{' '}
            {
              DAY_TYPE_LABELS[
                today.leave.type === 'HALF_AM' || today.leave.type === 'HALF_PM' ? today.leave.type : 'LEAVE'
              ]
            }
            입니다 (
            <Link to={`/approvals/${today.leave.documentId}`} className="underline">
              {today.leave.docNo}
            </Link>
            ).
          </p>
        )}
        <div className="grid grid-cols-2 gap-2">
          <Button block icon={LogIn} disabled={!today.canClockIn} loading={busy === 'in'} onClick={onClockIn}>
            출근하기
          </Button>
          <Button
            block
            variant="secondary"
            icon={LogOut}
            disabled={!today.canClockOut}
            loading={busy === 'out'}
            onClick={onClockOut}
          >
            {today.clockOutAt ? '퇴근 다시 찍기' : '퇴근하기'}
          </Button>
        </div>
        <p className="flex items-center gap-1.5 text-xs text-gray-500">
          <Clock3 size={13} />
          {s.departmentName || '회사'} 근무시간 {s.startTime} ~ {s.endTime} · 유예 {s.lateGraceMin}분 · 이번 달 지각{' '}
          {today.monthLateCount}회
        </p>
        <p className="text-xs text-gray-400">출퇴근 시각은 버튼을 누른 순간의 서버 시각으로 기록됩니다.</p>
      </div>
    </Card>
  );
}

export default function MyAttendancePage() {
  const navigate = useNavigate();
  const canClock = useAuthStore((s) => s.can(MENU.MY_ATTENDANCE, 'C'));
  const canDraft = useAuthStore((s) => s.can(MENU.APPROVAL_DRAFT, 'C'));
  const [today, setToday] = useState(null);
  const [month, setMonth] = useState(thisMonth());
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState('');
  const [confirmEarly, setConfirmEarly] = useState(false);

  const loadToday = useCallback(
    () =>
      attendanceApi
        .today()
        .then(setToday)
        .catch((err) => setError(getErrorMessage(err))),
    [],
  );
  const loadMonth = useCallback(
    () =>
      attendanceApi
        .me(month)
        .then(setData)
        .catch((err) => setError(getErrorMessage(err))),
    [month],
  );

  useEffect(() => {
    loadToday();
  }, [loadToday]);
  useEffect(() => {
    loadMonth();
  }, [loadMonth]);

  const act = async (kind) => {
    setBusy(kind);
    try {
      const updated = kind === 'in' ? await attendanceApi.clockIn() : await attendanceApi.clockOut();
      setToday(updated);
      const result = kind === 'in' ? updated.inResult : updated.outResult;
      const labels = kind === 'in' ? IN_RESULT_LABELS : OUT_RESULT_LABELS;
      toast.success(
        `${kind === 'in' ? '출근' : '퇴근'}했습니다 (${timeOf(kind === 'in' ? updated.clockInAt : updated.clockOutAt)} · ${labels[result] || '기록'})`,
      );
      if (month === thisMonth()) loadMonth();
    } catch (err) {
      toast.error(getErrorMessage(err));
      loadToday();
    } finally {
      setBusy('');
      setConfirmEarly(false);
    }
  };

  const onClockOut = () => (today?.earlyIfNow && !today.clockOutAt ? setConfirmEarly(true) : act('out'));

  if (error && !today) return <EmptyState icon={Clock3} title="근태를 불러오지 못했습니다" description={error} />;
  if (!today) return <PageLoader />;

  const limit = shiftDate(todayLocal(), -30);
  const canFix = (d) =>
    canDraft && d.date < todayLocal() && d.date >= limit && FIXABLE_CODES.has(dayCode(d)) && !d.fixDocument;
  const s = data?.summary;
  const outBase = today.dayType === 'HALF_PM' ? today.schedule.halfBoundary : today.schedule.endTime;

  return (
    <>
      <PageHeader
        title="내 근태"
        description="출퇴근은 버튼을 누른 서버 시각으로 기록됩니다. 지난 기록은 근태 정정 결재로 고칩니다."
        actions={
          canDraft && (
            <Button variant="secondary" icon={FilePen} onClick={() => navigate('/approvals/new?form=ATTENDANCE_FIX')}>
              근태 정정 신청
            </Button>
          )
        }
      />

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-12">
        <div className="lg:col-span-4">
          {canClock ? (
            <TodayCard today={today} onClockIn={() => act('in')} onClockOut={onClockOut} busy={busy} />
          ) : (
            <Card className="p-5 text-[13px] text-gray-500">출퇴근 기록 권한이 없습니다.</Card>
          )}
        </div>

        <Card className="overflow-hidden lg:col-span-8">
          <CardHeader
            title={monthLabel(month)}
            description={
              s
                ? `근무일 ${s.workDays}일 · 출근 ${s.present} · 지각 ${s.late} · 조퇴 ${s.early} · 결근 ${s.absent} · 퇴근 누락 ${s.missing} · 휴가 ${s.leaveDays}일`
                : undefined
            }
            action={
              <span className="flex gap-1">
                <Button
                  variant="secondary"
                  size="sm"
                  icon={ChevronLeft}
                  onClick={() => setMonth((m) => shiftMonth(m, -1))}
                  aria-label="이전 달"
                />
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={month >= thisMonth()}
                  icon={ChevronRight}
                  onClick={() => setMonth((m) => shiftMonth(m, 1))}
                  aria-label="다음 달"
                />
              </span>
            }
          />
          {!data ? (
            <PageLoader />
          ) : (
            <Table>
              <thead>
                <tr>
                  <Th>날짜</Th>
                  <Th>구분</Th>
                  <Th>출근</Th>
                  <Th>퇴근</Th>
                  <Th align="right">
                    <span className="sr-only">정정</span>
                  </Th>
                </tr>
              </thead>
              <tbody>
                {data.days.filter((d) => d.date <= todayLocal()).length === 0 ? (
                  <TableEmpty colSpan={5}>이 달 기록이 없습니다.</TableEmpty>
                ) : (
                  data.days
                    .filter((d) => d.date <= todayLocal())
                    .reverse()
                    .map((d) => {
                      const code = dayCode(d);
                      const meta = CELL_META[code] || CELL_META.NONE;
                      const off = code === 'HOLIDAY';
                      return (
                        <Tr key={d.date} className={cn(off && 'bg-gray-50/60')}>
                          <Td className={cn('tabular-nums', off ? 'text-gray-400' : 'text-gray-700')}>
                            {formatDay(d.date)}
                            {d.holidayName && <span className="ml-1.5 text-xs text-red-500">{d.holidayName}</span>}
                          </Td>
                          <Td>
                            {meta.label ? (
                              <Badge tone={meta.tone}>{meta.label}</Badge>
                            ) : (
                              <span className="text-xs text-gray-400">{d.date === todayLocal() ? '오늘' : '-'}</span>
                            )}
                          </Td>
                          <Td className="tabular-nums text-gray-900">{timeOf(d.clockInAt) || '-'}</Td>
                          <Td className="tabular-nums text-gray-900">{timeOf(d.clockOutAt) || '-'}</Td>
                          <Td align="right">
                            {d.fixDocument ? (
                              <Link
                                to={`/approvals/${d.fixDocument.id}`}
                                className="text-xs text-blue-700 hover:underline"
                              >
                                정정됨 · {d.fixDocument.docNo}
                              </Link>
                            ) : canFix(d) ? (
                              <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => navigate(`/approvals/new?form=ATTENDANCE_FIX&date=${d.date}`)}
                              >
                                정정 신청
                              </Button>
                            ) : null}
                          </Td>
                        </Tr>
                      );
                    })
                )}
              </tbody>
            </Table>
          )}
          <p className="border-t border-gray-100 px-5 py-3 text-xs text-gray-500">
            최근 30일 안의 지각·조퇴·결근·퇴근 누락은 근태 정정 결재로 고칠 수 있습니다.
          </p>
        </Card>
      </div>

      <Modal
        open={confirmEarly}
        onClose={() => setConfirmEarly(false)}
        title="지금 퇴근할까요?"
        description={`기준 퇴근 ${outBase} 전입니다. 지금 퇴근하면 조퇴로 기록됩니다. 반차라면 휴가 신청을 먼저 해 주세요.`}
        footer={
          <>
            <Button variant="secondary" onClick={() => setConfirmEarly(false)}>
              취소
            </Button>
            <Button variant="danger" loading={busy === 'out'} onClick={() => act('out')}>
              그래도 퇴근
            </Button>
          </>
        }
      />
    </>
  );
}
