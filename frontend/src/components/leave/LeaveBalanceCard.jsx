import React from 'react';
import { Card, Progress } from '@/components/ui';
import { cn } from '@/lib/cn';

const fmt = (n) => Number(n || 0).toLocaleString('ko-KR', { maximumFractionDigits: 1 });

const LeaveBalanceCard = ({ totalAnnualLeave = 0, usedAnnualLeave = 0, remainingAnnualLeave = 0 }) => {
  // 실제 잔여 연차 (승인된 것만 차감)
  const actualRemaining = totalAnnualLeave - usedAnnualLeave;
  // 사용률 계산
  const usageRate = totalAnnualLeave > 0 ? Math.round((usedAnnualLeave / totalAnnualLeave) * 1000) / 10 : 0;

  const metrics = [
    { label: '잔여 연차', value: actualRemaining, emphasis: true },
    { label: '사용 (승인 완료)', value: usedAnnualLeave },
    { label: '신청 가능', value: remainingAnnualLeave },
    { label: '연간 부여 (직급 기준)', value: totalAnnualLeave },
  ];

  return (
    <Card>
      <dl className="grid grid-cols-2 md:grid-cols-4">
        {metrics.map((m, i) => (
          <div
            key={m.label}
            className={cn('px-5 py-4', i > 0 && 'md:border-l md:border-gray-100', i % 2 === 1 && 'border-l border-gray-100', i > 1 && 'border-t border-gray-100 md:border-t-0')}
          >
            <dt className="text-[13px] text-gray-500">{m.label}</dt>
            <dd className={cn('mt-1 font-semibold tabular-nums tracking-tight', m.emphasis ? 'text-2xl text-blue-600' : 'text-2xl text-gray-900')}>
              {fmt(m.value)}
              <span className="ml-0.5 text-base font-medium text-gray-500">일</span>
            </dd>
          </div>
        ))}
      </dl>
      <div className="flex items-center gap-4 border-t border-gray-100 px-5 py-3">
        <span className="shrink-0 text-[13px] text-gray-500">사용률</span>
        <Progress value={usageRate} className="flex-1" />
        <span className="w-12 shrink-0 text-right text-[13px] font-medium tabular-nums text-gray-700">{usageRate}%</span>
      </div>
    </Card>
  );
};

export default LeaveBalanceCard;
