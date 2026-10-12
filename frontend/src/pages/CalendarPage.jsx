import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import FullCalendar from '@fullcalendar/react';
import daygridPlugin from '@fullcalendar/daygrid';
import interactionPlugin from '@fullcalendar/interaction';
import { LogIn, LogOut, Briefcase, Palmtree, FilePlus2, CalendarDays } from 'lucide-react';
import axios from '../api/axios';
import { LEAVE_TYPE_LABELS } from '@/lib/labels';
import { toast } from '@/lib/toast';
import { cn } from '@/lib/cn';
import {
  Badge,
  Button,
  Card,
  CardHeader,
  CardBody,
  EmptyState,
  Input,
  PageHeader,
  PageLoader,
  StatCard,
  StatusBadge,
  Table,
  TableEmpty,
  Td,
  Th,
  Tr,
} from '@/components/ui';

const HOLIDAYS_2026 = [
  { title: '신정', start: '2026-01-01', type: 'HOLIDAY' },
  { title: '설날', start: '2026-02-16', end: '2026-02-19', type: 'HOLIDAY' },
  { title: '삼일절', start: '2026-03-01', type: 'HOLIDAY' },
  { title: '대체공휴일', start: '2026-03-02', type: 'HOLIDAY' },
  { title: '어린이날', start: '2026-05-05', type: 'HOLIDAY' },
  { title: '부처님오신날', start: '2026-05-24', type: 'HOLIDAY' },
  { title: '대체공휴일', start: '2026-05-25', type: 'HOLIDAY' },
  { title: '현충일', start: '2026-06-06', type: 'HOLIDAY' },
  { title: '광복절', start: '2026-08-15', type: 'HOLIDAY' },
  { title: '대체공휴일', start: '2026-08-17', type: 'HOLIDAY' },
  { title: '추석', start: '2026-09-24', end: '2026-09-27', type: 'HOLIDAY' },
  { title: '개천절', start: '2026-10-03', type: 'HOLIDAY' },
  { title: '한글날', start: '2026-10-09', type: 'HOLIDAY' },
  { title: '성탄절', start: '2026-12-25', type: 'HOLIDAY' },
];

// 일정 종류별 표시 (색상은 index.css .evt-* 참고)
const EVENT_TYPES = {
  PROJECT: { label: '프로젝트', className: 'evt-project', dot: 'bg-blue-500' },
  LEAVE: { label: '연차', className: 'evt-leave', dot: 'bg-emerald-500' },
  OVERTIME: { label: '특근', className: 'evt-overtime', dot: 'bg-amber-500' },
  HOLIDAY: { label: '공휴일', className: 'evt-holiday', dot: 'bg-red-500' },
};
const ETC_TYPE = { label: '일정', className: 'evt-etc', dot: 'bg-gray-400' };
const typeOf = (type) => EVENT_TYPES[type] || ETC_TYPE;

const CalendarPage = () => {
  const navigate = useNavigate();
  const [events, setEvents] = useState([]);
  const [originalLeaveRequests, setOriginalLeaveRequests] = useState([]);
  const [filteredLeaveRequests, setFilteredLeaveRequests] = useState([]);
  const [loading, setLoading] = useState(true);

  const [selectedDate, setSelectedDate] = useState(null); // null이면 이번 달 출근 기록 전체
  const [displayRecords, setDisplayRecords] = useState([]); // 출근 기록 + 이벤트 통합 리스트

  const [summary, setSummary] = useState({
    workDaysCount: 0,
    leaveUsedTotal: 0,
    attendanceRecords: [],
  });

  const [dateRange, setDateRange] = useState({ start: '', end: '' });

  const fetchCalendarData = useCallback(async () => {
    setLoading(true);
    try {
      const now = new Date();
      const currentYear = now.getFullYear();
      const currentMonthInt = now.getMonth() + 1;
      const formattedMonth = `${currentYear}-${String(currentMonthInt).padStart(2, '0')}`;

      const results = await Promise.allSettled([
        axios.get('/leave/my'),
        axios.get('/calendar/events', { params: { year: currentYear, month: currentMonthInt } }),
        axios.get('/attendance/summary', { params: { month: formattedMonth } }),
        axios.get('/projects'),
        axios.get('/overtime/list')
      ]);

      const getSafeData = (res) => (res.status === 'fulfilled' && res.value.data) ? (res.value.data.data || res.value.data || []) : [];

      const lDataRaw = getSafeData(results[0]);
      const cEventsRaw = getSafeData(results[1]);
      const sData = (results[2].status === 'fulfilled' && results[2].value.data?.success) ? (results[2].value.data.data || {}) : {};
      const pData = getSafeData(results[3]);
      const oData = getSafeData(results[4]);

      // 1. 연차 데이터 가공
      const mappedLeaveData = lDataRaw.map(l => {
        const type = l.app_type || l.appType;
        let typePrefix = '[연차]';
        if (type === 'HALF_MORNING') typePrefix = '[오전반차]';
        if (type === 'HALF_AFTERNOON') typePrefix = '[오후반차]';

        return {
          ...l,
          appId: l.id || l.appId,
          appType: type,
          appStatus: l.app_status || l.appStatus,
          startDate: l.start_date || l.startDate,
          endDate: l.end_date || l.endDate,
          usedDays: Number(l.usedDays || l.used_days || 0),
          displayTitle: `${typePrefix} ${l.request_reason || l.requestReason || '개인 사유'}`,
          createdAt: l.created_at || l.createdAt
        };
      });

      const myApprovedLeaveTotal = mappedLeaveData
        .filter(l => l.appStatus === 'APPROVED')
        .reduce((acc, cur) => acc + (cur.usedDays || 0), 0);

      // 2. 출근 기록 가공
      const attendanceRecords = (sData.attendanceRecords || []).map((record, index) => ({
        id: `att-${index}`,
        work_date: record.workDate,
        clock_in_time: record.clockInTime || '--:--',
        clock_out_time: record.clockOutTime || '--:--',
        status: record.status || '정상',
        recordType: 'ATTENDANCE' // 타입 구분자
      }));

      // 3. 이벤트 가공 (캘린더 표시용)
      const leaveEvents = mappedLeaveData
        .filter(l => l.appStatus === 'APPROVED')
        .map(l => ({
          id: `lv-${l.appId}`,
          title: l.displayTitle,
          start: l.startDate,
          end: l.endDate,
          type: 'LEAVE',
        }));

      const overtimeEvents = oData
        .filter(o => (o.status || o.appStatus) === 'APPROVED')
        .map(o => ({
          id: `ot-${o.id}`,
          title: `[특근] ${o.reason || '주말특근'}`,
          start: o.overtime_date || o.overtimeDate,
          end: o.overtime_date || o.overtimeDate,
          type: 'OVERTIME'
        }));

      const projectEvents = pData.map((p, idx) => ({
        id: `pj-${p.projectId || p.id || idx}`,
        title: `[P] ${p.title || p.projectName || '무제 프로젝트'}`,
        start: p.startDate || p.start_date,
        end: p.endDate || p.end_date,
        type: 'PROJECT'
      }));

      const rawEvents = [
        ...HOLIDAYS_2026.map((h, idx) => ({ ...h, id: `hld-${idx}` })),
        ...cEventsRaw.map((e, idx) => ({ ...e, id: e.eventId ? `srv-${e.eventId}` : `ce-${idx}` })),
        ...leaveEvents,
        ...projectEvents,
        ...overtimeEvents
      ];

      const uniqueEventsMap = new Map();
      rawEvents.forEach(evt => { if (evt.start) uniqueEventsMap.set(evt.id, evt); });

      const finalEvents = Array.from(uniqueEventsMap.values()).map(event => ({
        ...event,
        classNames: [typeOf(event.type).className],
        allDay: true,
      }));

      setEvents(finalEvents);
      setOriginalLeaveRequests(mappedLeaveData);
      setFilteredLeaveRequests(mappedLeaveData);
      setDisplayRecords(attendanceRecords);

      setSummary({
        workDaysCount: sData.workDaysCount || 0,
        leaveUsedTotal: sData.leaveUsedCount || myApprovedLeaveTotal,
        attendanceRecords: attendanceRecords,
      });

    } catch (error) {
      console.error('데이터 가공 에러:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchCalendarData();
  }, [fetchCalendarData]);

  // 날짜 클릭: 해당 날짜의 출근 기록 + 일정
  const handleDateClick = (arg) => {
    const clickedDate = arg.dateStr;

    const attendanceOnDate = summary.attendanceRecords.filter(r => r.work_date === clickedDate);

    const eventsOnDate = events.filter(evt => {
      const start = evt.start;
      const end = evt.end || evt.start; // 종료일이 없으면 시작일과 동일하게 처리
      return clickedDate >= start && clickedDate <= end;
    }).map(evt => ({
      id: evt.id,
      title: evt.title,
      type: evt.type,
      recordType: 'EVENT'
    }));

    setDisplayRecords([...attendanceOnDate, ...eventsOnDate]);
    setSelectedDate(clickedDate);
  };

  const handleAttendance = async (type) => {
    try {
      const now = new Date();
      const workDate = now.toLocaleDateString('en-CA');
      const currentTime = now.toTimeString().split(' ')[0];

      if (type === 'IN') {
        await axios.post('/attendance/check-in', {
          workDate: workDate,
          clockInTime: currentTime
        });
        toast.success(`출근이 기록되었습니다. (${currentTime.slice(0, 5)})`);
      } else {
        await axios.patch('/attendance/check-out', {
          clockOutTime: currentTime
        }, {
          params: { workDate: workDate }
        });
        toast.success(`퇴근이 기록되었습니다. (${currentTime.slice(0, 5)})`);
      }

      fetchCalendarData();
    } catch (error) {
      console.error('근태 에러 상세:', error.response?.data);
      const serverMessage = error.response?.data?.error?.message || '데이터 형식이 올바르지 않습니다.';
      toast.error(`근태 처리 실패: ${serverMessage}`);
    }
  };

  const handleSearch = () => {
    const { start, end } = dateRange;
    if (!start || !end) {
      toast.error('검색할 기간을 선택해주세요.');
      return;
    }
    const filtered = originalLeaveRequests.filter(req =>
      (req.startDate >= start && req.startDate <= end) || (req.endDate >= start && req.endDate <= end)
    );
    setFilteredLeaveRequests(filtered);
  };

  const handleReset = () => {
    setFilteredLeaveRequests(originalLeaveRequests);
    setDateRange({ start: '', end: '' });
    setDisplayRecords(summary.attendanceRecords);
    setSelectedDate(null);
  };

  if (loading) return <PageLoader />;

  return (
    <>
      <PageHeader
        title="캘린더"
        description="출퇴근 기록과 연차·특근·프로젝트 일정을 확인하세요."
        actions={
          <>
            <Button variant="secondary" icon={LogOut} onClick={() => handleAttendance('OUT')}>퇴근</Button>
            <Button icon={LogIn} onClick={() => handleAttendance('IN')}>출근</Button>
          </>
        }
      />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <StatCard label="이번 달 출근일" value={summary.workDaysCount} unit="일" icon={Briefcase} tone="blue" />
        <StatCard label="사용 승인 연차" value={summary.leaveUsedTotal} unit="일" icon={Palmtree} tone="green" />
      </div>

      <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-12">
        {/* 월간 캘린더 */}
        <Card className="xl:col-span-8">
          <CardHeader
            title="월간 일정"
            action={
              <div className="hidden items-center gap-3 sm:flex">
                {Object.values(EVENT_TYPES).map((t) => (
                  <span key={t.label} className="flex items-center gap-1.5 text-xs text-gray-500">
                    <span className={cn('h-2 w-2 rounded-full', t.dot)} />
                    {t.label}
                  </span>
                ))}
              </div>
            }
          />
          <CardBody className="wf-calendar">
            <FullCalendar
              plugins={[daygridPlugin, interactionPlugin]}
              initialView="dayGridMonth"
              locale="ko"
              height="auto"
              events={events}
              dateClick={handleDateClick}
              headerToolbar={{ left: 'title', center: '', right: 'today prev,next' }}
              buttonText={{ today: '오늘' }}
              dayCellContent={(arg) => arg.dayNumberText.replace('일', '')}
              dayMaxEvents={3}
              moreLinkText={(n) => `+${n}개`}
            />
          </CardBody>
        </Card>

        {/* 선택한 날짜 상세 */}
        <Card className="flex flex-col xl:col-span-4">
          <CardHeader
            title={selectedDate ? `${selectedDate} 상세` : '이번 달 출근 기록'}
            description={selectedDate ? '선택한 날짜의 기록과 일정' : '날짜를 누르면 해당 일의 상세를 볼 수 있습니다'}
            action={selectedDate && <Button variant="ghost" size="sm" onClick={handleReset}>전체</Button>}
          />
          <div className="max-h-[560px] flex-1 overflow-y-auto">
            {displayRecords.length > 0 ? (
              <ul className="divide-y divide-gray-100">
                {displayRecords.map((item) =>
                  item.recordType === 'ATTENDANCE' ? (
                    <li key={item.id} className="px-5 py-3.5">
                      <div className="flex items-center justify-between">
                        <span className="text-[13px] font-medium tabular-nums text-gray-900">{item.work_date}</span>
                        <Badge tone={item.status === '정상' ? 'green' : 'amber'}>{item.status}</Badge>
                      </div>
                      <div className="mt-2 grid grid-cols-2 gap-2">
                        <div className="rounded-lg bg-gray-50 px-3 py-2">
                          <p className="text-xs text-gray-500">출근</p>
                          <p className="text-[15px] font-semibold tabular-nums text-gray-900">{item.clock_in_time}</p>
                        </div>
                        <div className="rounded-lg bg-gray-50 px-3 py-2">
                          <p className="text-xs text-gray-500">퇴근</p>
                          <p className="text-[15px] font-semibold tabular-nums text-gray-900">{item.clock_out_time}</p>
                        </div>
                      </div>
                    </li>
                  ) : (
                    <li key={item.id} className="flex items-center gap-3 px-5 py-3">
                      <span className={cn('h-8 w-1 shrink-0 rounded-full', typeOf(item.type).dot)} />
                      <div className="min-w-0">
                        <p className="text-xs text-gray-500">{typeOf(item.type).label}</p>
                        <p className="truncate text-sm font-medium text-gray-900">{item.title}</p>
                      </div>
                    </li>
                  )
                )}
              </ul>
            ) : (
              <EmptyState icon={CalendarDays} title="기록이 없습니다" />
            )}
          </div>
        </Card>
      </div>

      {/* 연차 신청 내역 */}
      <Card className="mt-4 overflow-hidden">
        <CardHeader
          title="연차 신청 내역"
          action={
            <Button size="sm" icon={FilePlus2} onClick={() => navigate('/user/leaves/new')}>
              연차 신청
            </Button>
          }
        />
        <div className="flex flex-wrap items-center gap-2 border-b border-gray-100 px-5 py-3">
          <span className="mr-1 text-[13px] text-gray-500">기간</span>
          <div className="w-40">
            <Input type="date" value={dateRange.start} className="h-8" onChange={(e) => setDateRange({ ...dateRange, start: e.target.value })} />
          </div>
          <span className="text-gray-400">~</span>
          <div className="w-40">
            <Input type="date" value={dateRange.end} min={dateRange.start} className="h-8" onChange={(e) => setDateRange({ ...dateRange, end: e.target.value })} />
          </div>
          <Button size="sm" variant="secondary" onClick={handleSearch}>조회</Button>
          <Button size="sm" variant="ghost" onClick={handleReset}>초기화</Button>
        </div>
        <Table>
          <thead>
            <tr>
              <Th>신청일</Th>
              <Th>유형</Th>
              <Th>기간</Th>
              <Th align="right">일수</Th>
              <Th align="center">상태</Th>
            </tr>
          </thead>
          <tbody>
            {filteredLeaveRequests.length > 0 ? (
              filteredLeaveRequests.map((req, index) => (
                <Tr key={req.appId || index}>
                  <Td className="tabular-nums text-gray-500">{req.createdAt?.split('T')[0] || '-'}</Td>
                  <Td className="font-medium text-gray-900">{LEAVE_TYPE_LABELS[req.appType] || req.appType}</Td>
                  <Td className="whitespace-nowrap tabular-nums">{req.startDate} ~ {req.endDate}</Td>
                  <Td align="right" className="tabular-nums">{req.usedDays}일</Td>
                  <Td align="center"><StatusBadge status={req.appStatus} /></Td>
                </Tr>
              ))
            ) : (
              <TableEmpty colSpan={5}>신청 내역이 없습니다.</TableEmpty>
            )}
          </tbody>
        </Table>
      </Card>
    </>
  );
};

export default CalendarPage;
