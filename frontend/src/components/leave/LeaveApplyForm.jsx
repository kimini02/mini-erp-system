import React, { useState } from 'react';
import { ArrowRight } from 'lucide-react';
import api from '../../api/axios';
import { Button, Card, CardHeader, CardBody, Field, Input, Segmented, Textarea } from '@/components/ui';
import { toast } from '@/lib/toast';

const LEAVE_TYPE_OPTIONS = [
  { value: 'ANNUAL', label: '연차' },
  { value: 'HALF_MORNING', label: '오전 반차' },
  { value: 'HALF_AFTERNOON', label: '오후 반차' },
];

const INITIAL_FORM = { leaveType: 'ANNUAL', startDate: '', endDate: '', reason: '' };

const LeaveApplyForm = ({ remainingBalance = 0 }) => {
  // 입력 폼 데이터
  const [formData, setFormData] = useState(INITIAL_FORM);
  const [submitting, setSubmitting] = useState(false);

  // 사용 일수 계산 (주말/공휴일 제외)
  const calculateUsedDays = () => {
    const { startDate, endDate, leaveType } = formData;
    if (!startDate || !endDate) return 0;

    if (leaveType.includes('HALF')) return 0.5;

    const holidays = [
      '2026-01-01', '2026-02-16', '2026-02-17', '2026-02-18',
      '2026-03-01', '2026-05-05', '2026-05-24', '2026-06-06',
      '2026-08-15', '2026-09-24', '2026-09-25', '2026-09-26',
      '2026-10-03', '2026-10-09', '2026-12-25',
    ];

    const start = new Date(startDate);
    const end = new Date(endDate);
    let count = 0;

    const current = new Date(start);
    while (current <= end) {
      const day = current.getDay();
      const dateString = current.toISOString().split('T')[0];
      const isWeekend = (day === 0 || day === 6);
      const isHoliday = holidays.includes(dateString);

      if (!isWeekend && !isHoliday) {
        count++;
      }
      current.setDate(current.getDate() + 1);
    }
    return count;
  };

  const calculatedDays = calculateUsedDays();
  const isBalanceExceeded = calculatedDays > remainingBalance;

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (isBalanceExceeded) {
      toast.error('잔여 연차가 부족하여 신청할 수 없습니다.');
      return;
    }

    const apiData = {
      appType: formData.leaveType, // ANNUAL, HALF_MORNING 등
      startDate: formData.startDate,
      endDate: formData.endDate,
      requestReason: formData.reason || '개인 사유'
    };

    setSubmitting(true);
    try {
      const response = await api.post('/leave', apiData);
      if (response.data.success) {
        toast.success('연차 신청이 완료되었습니다.');
      }
      setFormData(INITIAL_FORM);
    } catch (err) {
      console.error('신청 중 에러 발생:', err);
      const serverErrorMessage = err.response?.data?.error?.message;
      toast.error(`신청 실패: ${serverErrorMessage || '입력값이 올바르지 않습니다.'}`);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Card className="flex h-full flex-col">
      <CardHeader title="연차 신청" description="승인되면 잔여 연차에서 차감됩니다." />
      <form onSubmit={handleSubmit} className="flex flex-1 flex-col">
        <CardBody className="flex-1 space-y-4">
          <Field label="연차 유형" required>
            <Segmented
              options={LEAVE_TYPE_OPTIONS}
              value={formData.leaveType}
              onChange={(value) => setFormData(prev => ({ ...prev, leaveType: value }))}
            />
          </Field>

          <div className="grid grid-cols-2 gap-3">
            <Field label="시작일" htmlFor="leave-start" required>
              <Input id="leave-start" type="date" name="startDate" value={formData.startDate} onChange={handleChange} required />
            </Field>
            <Field label="종료일" htmlFor="leave-end" required>
              <Input
                id="leave-end"
                type="date"
                name="endDate"
                value={formData.endDate}
                min={formData.startDate || undefined}
                onChange={handleChange}
                required
              />
            </Field>
          </div>

          <Field label="신청 사유" htmlFor="leave-reason">
            <Textarea id="leave-reason" name="reason" rows={3} value={formData.reason} onChange={handleChange} placeholder="사유를 입력하세요 (미입력 시 '개인 사유')" />
          </Field>

          {calculatedDays > 0 && (
            <div className={isBalanceExceeded ? 'rounded-lg bg-red-50 px-4 py-3' : 'rounded-lg bg-gray-50 px-4 py-3'}>
              <div className="flex items-center gap-2 text-sm">
                <span className="text-gray-500">차감 예정</span>
                <span className="font-semibold tabular-nums text-gray-900">{calculatedDays.toFixed(1)}일</span>
                <ArrowRight size={14} className="text-gray-400" />
                <span className="text-gray-500">신청 후 잔여</span>
                <span className={isBalanceExceeded ? 'font-semibold tabular-nums text-red-600' : 'font-semibold tabular-nums text-blue-600'}>
                  {(remainingBalance - calculatedDays).toFixed(1)}일
                </span>
              </div>
              {isBalanceExceeded && <p className="mt-1 text-xs text-red-600">잔여 연차가 부족합니다.</p>}
            </div>
          )}
        </CardBody>
        <div className="border-t border-gray-100 px-5 py-4">
          <Button type="submit" block disabled={isBalanceExceeded} loading={submitting}>
            연차 신청
          </Button>
        </div>
      </form>
    </Card>
  );
};

export default LeaveApplyForm;
