import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { holidayApi } from '@/api/org';
import { cn } from '@/lib/cn';
import { WEEKDAYS, todayStr } from './helpers';

export default function MiniCalendar({ marks }) {
  const today = todayStr();
  const [cursor, setCursor] = useState(today.slice(0, 7));
  const [picked, setPicked] = useState(today);
  const [holidays, setHolidays] = useState({});
  const year = Number(cursor.slice(0, 4));
  const month = Number(cursor.slice(5, 7));

  useEffect(() => {
    let alive = true;
    holidayApi
      .list(year)
      .then(
        (list) => alive && setHolidays((h) => ({ ...h, [year]: new Map((list || []).map((x) => [x.date, x.name])) })),
      )
      .catch(() => alive && setHolidays((h) => ({ ...h, [year]: new Map() })));
    return () => {
      alive = false;
    };
  }, [year]);

  const move = (delta) => {
    const d = new Date(year, month - 1 + delta, 1);
    setCursor(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`);
  };
  const lead = new Date(year, month - 1, 1).getDay();
  const days = new Date(year, month, 0).getDate();
  const names = holidays[year];
  const cells = [
    ...Array(lead).fill(null),
    ...Array.from({ length: days }, (_, i) => `${cursor}-${String(i + 1).padStart(2, '0')}`),
  ];
  const pickedItems = marks.get(picked) || [];
  const pickedHoliday = holidays[Number(picked.slice(0, 4))]?.get(picked);

  return (
    <section
      aria-labelledby="home-cal-title"
      className="flex flex-[1_1_300px] flex-col gap-2 rounded-[14px] border border-gray-200 bg-white px-4 py-3.5"
    >
      <div className="flex items-center gap-1">
        <h2 id="home-cal-title" className="text-[15px] font-bold text-gray-900">
          {year}년 {month}월
        </h2>
        <button
          type="button"
          onClick={() => {
            setCursor(today.slice(0, 7));
            setPicked(today);
          }}
          className="ml-auto h-[30px] rounded-full border border-gray-200 px-2.5 text-xs font-medium text-gray-700 hover:bg-gray-50"
        >
          오늘
        </button>
        <button
          type="button"
          aria-label="이전 달"
          onClick={() => move(-1)}
          className="flex h-8 w-8 items-center justify-center rounded-lg text-gray-600 hover:bg-gray-100"
        >
          <ChevronLeft size={16} />
        </button>
        <button
          type="button"
          aria-label="다음 달"
          onClick={() => move(1)}
          className="flex h-8 w-8 items-center justify-center rounded-lg text-gray-600 hover:bg-gray-100"
        >
          <ChevronRight size={16} />
        </button>
      </div>
      <div className="grid grid-cols-7 text-center text-[13px] tabular-nums">
        {WEEKDAYS.map((w, i) => (
          <span
            key={w}
            className={cn(
              'pb-1 text-xs font-semibold',
              i === 0 ? 'text-red-600' : i === 6 ? 'text-blue-600' : 'text-gray-500',
            )}
          >
            {w}
          </span>
        ))}
        {cells.map((date, i) => {
          if (!date) return <span key={`e${i}`} className="h-8" />;
          const dow = i % 7;
          const holiday = names?.get(date);
          const items = marks.get(date) || [];
          const hasTask = items.some((x) => x.kind === 'task');
          const hasLeave = items.some((x) => x.kind === 'leave');
          const isToday = date === today;
          const label = [
            `${month}월 ${Number(date.slice(8))}일`,
            holiday,
            hasTask && '업무 마감',
            hasLeave && '팀 휴가',
          ]
            .filter(Boolean)
            .join(', ');
          return (
            <button
              key={date}
              type="button"
              aria-label={label}
              aria-pressed={picked === date}
              onClick={() => setPicked(date)}
              className="flex h-8 flex-col items-center rounded-md hover:bg-gray-50"
            >
              <span
                className={cn(
                  'flex h-[26px] w-[26px] items-center justify-center rounded-full',
                  isToday ? 'bg-blue-600 font-bold text-white' : picked === date ? 'ring-1 ring-blue-300' : '',
                  !isToday && (dow === 0 || holiday) && 'text-red-600',
                  !isToday && dow === 6 && !holiday && 'text-blue-600',
                )}
              >
                {Number(date.slice(8))}
              </span>
              <span className="flex h-[5px] gap-[3px]" aria-hidden="true">
                {hasLeave && <span className="h-[5px] w-[5px] rounded-full bg-blue-700" />}
                {hasTask && <span className="h-[5px] w-[5px] rounded-full bg-amber-500" />}
              </span>
            </button>
          );
        })}
      </div>
      <div className="border-t border-gray-100 pt-2">
        <p className="text-xs font-semibold text-gray-500">
          {Number(picked.slice(5, 7))}월 {Number(picked.slice(8))}일 {WEEKDAYS[new Date(`${picked}T00:00:00`).getDay()]}
          요일
          {pickedHoliday && <span className="ml-1.5 text-red-600">{pickedHoliday}</span>}
        </p>
        {pickedItems.length === 0 ? (
          <p className="mt-1 text-[13px] text-gray-500">표시할 일정이 없습니다.</p>
        ) : (
          <ul className="mt-1 flex flex-col gap-1">
            {pickedItems.slice(0, 3).map((m) => (
              <li key={m.id} className="flex items-center gap-2 text-[13px]">
                <span
                  className={cn(
                    'h-1.5 w-1.5 shrink-0 rounded-full',
                    m.kind === 'task' ? 'bg-amber-500' : 'bg-blue-700',
                  )}
                />
                {m.to ? (
                  <Link to={m.to} className="truncate text-gray-900 hover:text-blue-700">
                    {m.label}
                  </Link>
                ) : (
                  <span className="truncate text-gray-900">{m.label}</span>
                )}
              </li>
            ))}
            {pickedItems.length > 3 && <li className="text-xs text-gray-500">외 {pickedItems.length - 3}건</li>}
          </ul>
        )}
      </div>
      <div className="mt-auto flex gap-3.5 text-xs text-gray-500">
        <span className="inline-flex items-center gap-1.5">
          <span className="h-1.5 w-1.5 rounded-full bg-amber-500" />
          업무 마감
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span className="h-1.5 w-1.5 rounded-full bg-blue-700" />팀 휴가
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span className="font-semibold text-red-600">9</span>공휴일
        </span>
      </div>
    </section>
  );
}
