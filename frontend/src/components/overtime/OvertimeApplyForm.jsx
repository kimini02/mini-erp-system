import React, { useState } from 'react';
import api from '../../api/axios';
import { Button, Card, CardHeader, CardBody, Field, Input, Textarea } from '@/components/ui';
import { toast } from '@/lib/toast';

const INITIAL_FORM = { overtimeDate: '', startTime: '19:00', endTime: '21:00', reason: '' };

const OvertimeApplyForm = () => {
  const [formData, setFormData] = useState(INITIAL_FORM);
  const [submitting, setSubmitting] = useState(false);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    // 프론트엔드 방어 코드: 주말 체크 (0: 일요일, 6: 토요일)
    const selectedDate = new Date(formData.overtimeDate);
    const day = selectedDate.getDay();
    if (day !== 0 && day !== 6) {
      toast.error('특근은 주말(토, 일)에만 신청할 수 있습니다.');
      return;
    }

    // 백엔드 형식에 맞게 시간 뒤에 :00 초 추가
    const apiData = {
      overtimeDate: formData.overtimeDate,
      startTime: `${formData.startTime}:00`,
      endTime: `${formData.endTime}:00`,
      reason: formData.reason || '긴급 대응'
    };

    setSubmitting(true);
    try {
      const response = await api.post('/overtime', apiData);
      if (response.data.success) {
        toast.success('특근 신청이 완료되었습니다.');
        setFormData(INITIAL_FORM);
      }
    } catch (err) {
      const msg = err.response?.data?.message || '입력값을 확인해주세요.';
      toast.error(`신청 실패: ${msg}`);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Card className="flex h-full flex-col">
      <CardHeader title="특근 신청" description="주말(토·일) 근무만 신청할 수 있습니다." />
      <form onSubmit={handleSubmit} className="flex flex-1 flex-col">
        <CardBody className="flex-1 space-y-4">
          <Field label="특근 날짜" htmlFor="ot-date" required>
            <Input id="ot-date" type="date" name="overtimeDate" value={formData.overtimeDate} onChange={handleChange} required />
          </Field>

          <div className="grid grid-cols-2 gap-3">
            <Field label="시작 시간" htmlFor="ot-start" required>
              <Input id="ot-start" type="time" name="startTime" value={formData.startTime} onChange={handleChange} required />
            </Field>
            <Field label="종료 시간" htmlFor="ot-end" required>
              <Input id="ot-end" type="time" name="endTime" value={formData.endTime} onChange={handleChange} required />
            </Field>
          </div>

          <Field label="특근 사유" htmlFor="ot-reason">
            <Textarea id="ot-reason" name="reason" rows={3} value={formData.reason} onChange={handleChange} placeholder="사유를 입력하세요 (미입력 시 '긴급 대응')" />
          </Field>
        </CardBody>
        <div className="border-t border-gray-100 px-5 py-4">
          <Button type="submit" block loading={submitting}>
            특근 신청
          </Button>
        </div>
      </form>
    </Card>
  );
};

export default OvertimeApplyForm;
