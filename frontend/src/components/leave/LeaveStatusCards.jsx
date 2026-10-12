import React from 'react';
import { FileText, CheckCircle2, Hourglass, XCircle } from 'lucide-react';
import { StatCard } from '@/components/ui';

const LeaveStatusCards = ({ leaveData = [], overtimeData = [] }) => {
  const count = (status) =>
    leaveData.filter(item => item.appStatus === status).length +
    overtimeData.filter(item => item.status === status).length;

  return (
    <div className="grid grid-cols-2 gap-4 xl:grid-cols-4">
      <StatCard label="전체 신청" value={leaveData.length + overtimeData.length} unit="건" icon={FileText} />
      <StatCard label="승인" value={count('APPROVED')} unit="건" icon={CheckCircle2} tone="green" />
      <StatCard label="대기" value={count('PENDING')} unit="건" icon={Hourglass} tone="amber" />
      <StatCard label="반려" value={count('REJECTED')} unit="건" icon={XCircle} tone="red" />
    </div>
  );
};

export default LeaveStatusCards;
