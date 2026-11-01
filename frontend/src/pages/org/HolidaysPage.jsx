import { useCallback, useEffect, useState } from 'react';
import { CalendarOff, ChevronLeft, ChevronRight, Plus, Trash2 } from 'lucide-react';
import { holidayApi } from '@/api/org';
import { useAuthStore } from '@/store/authStore';
import { MENU } from '@/lib/permissions';
import { getErrorMessage } from '@/lib/errors';
import { toast } from '@/lib/toast';
import { confirm } from '@/lib/confirm';
import {
  Badge,
  Button,
  Card,
  EmptyState,
  Field,
  Input,
  Modal,
  PageHeader,
  PageLoader,
  Table,
  TableEmpty,
  Td,
  Th,
  Tr,
} from '@/components/ui';

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
const weekdayOf = (date) => WEEKDAYS[new Date(`${date}T00:00:00`).getDay()];

function HolidayModal({ open, year, onClose, onSaved }) {
  const [form, setForm] = useState({ date: '', name: '', substitute: false });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (open) {
      setForm({ date: `${year}-01-01`, name: '', substitute: false });
      setError('');
    }
  }, [open, year]);

  const submit = async (e) => {
    e.preventDefault();
    setSaving(true);
    setError('');
    try {
      await holidayApi.create({ ...form, name: form.name.trim() });
      toast.success('공휴일을 등록했습니다.');
      onSaved();
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="공휴일 등록"
      size="md"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            취소
          </Button>
          <Button type="submit" form="holiday-form" loading={saving} disabled={!form.date || !form.name.trim()}>
            등록
          </Button>
        </>
      }
    >
      <form id="holiday-form" onSubmit={submit} className="space-y-4">
        <Field label="날짜" htmlFor="hd-date" required>
          <Input
            id="hd-date"
            type="date"
            value={form.date}
            onChange={(e) => setForm((f) => ({ ...f, date: e.target.value }))}
          />
        </Field>
        <Field label="이름" htmlFor="hd-name" required>
          <Input
            id="hd-name"
            value={form.name}
            onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
            placeholder="예: 대체공휴일(개천절)"
          />
        </Field>
        <label className="flex items-center gap-2 text-sm text-gray-700">
          <input
            type="checkbox"
            checked={form.substitute}
            onChange={(e) => setForm((f) => ({ ...f, substitute: e.target.checked }))}
            className="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
          />
          대체공휴일
        </label>
        {error && (
          <p role="alert" className="text-[13px] text-red-600">
            {error}
          </p>
        )}
      </form>
    </Modal>
  );
}

export default function HolidaysPage() {
  const can = useAuthStore((s) => s.can);
  const canCreate = can(MENU.HOLIDAY, 'C');
  const canDelete = can(MENU.HOLIDAY, 'D');

  const [year, setYear] = useState(new Date().getFullYear());
  const [holidays, setHolidays] = useState(null);
  const [error, setError] = useState('');
  const [adding, setAdding] = useState(false);

  const load = useCallback(async () => {
    try {
      setHolidays(await holidayApi.list(year));
      setError('');
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }, [year]);

  useEffect(() => {
    load();
  }, [load]);

  const remove = async (h) => {
    const ok = await confirm({
      title: `${h.date} ${h.name}을(를) 삭제할까요?`,
      description: '이미 계산된 연차 일수와 근태 판정은 바뀌지 않습니다.',
      confirmText: '삭제',
      tone: 'danger',
    });
    if (!ok) return;
    try {
      await holidayApi.remove(h.id);
      toast.success('공휴일을 삭제했습니다.');
      load();
    } catch (err) {
      toast.error(getErrorMessage(err));
    }
  };

  return (
    <>
      <PageHeader
        title="공휴일"
        description="연차 일수 계산과 근태 판정에서 쉬는 날로 봅니다."
        actions={
          canCreate && (
            <Button icon={Plus} onClick={() => setAdding(true)}>
              공휴일 등록
            </Button>
          )
        }
      />

      <Card className="overflow-hidden">
        <div className="flex items-center gap-2 border-b border-gray-100 px-4 py-3">
          <Button
            variant="secondary"
            size="sm"
            icon={ChevronLeft}
            onClick={() => setYear((y) => y - 1)}
            aria-label="이전 해"
          />
          <span className="w-20 text-center text-[15px] font-semibold tabular-nums text-gray-900">{year}년</span>
          <Button
            variant="secondary"
            size="sm"
            icon={ChevronRight}
            onClick={() => setYear((y) => y + 1)}
            aria-label="다음 해"
          />
          {holidays && <span className="ml-auto text-[13px] text-gray-500">{holidays.length}일</span>}
        </div>

        {error ? (
          <EmptyState icon={CalendarOff} title="공휴일을 불러오지 못했습니다" description={error} />
        ) : !holidays ? (
          <PageLoader />
        ) : (
          <Table>
            <thead>
              <tr>
                <Th>날짜</Th>
                <Th>요일</Th>
                <Th>이름</Th>
                <Th>구분</Th>
                {canDelete && (
                  <Th align="right">
                    <span className="sr-only">삭제</span>
                  </Th>
                )}
              </tr>
            </thead>
            <tbody>
              {holidays.length === 0 ? (
                <TableEmpty colSpan={canDelete ? 5 : 4}>{year}년에 등록된 공휴일이 없습니다.</TableEmpty>
              ) : (
                holidays.map((h) => (
                  <Tr key={h.id}>
                    <Td className="tabular-nums">{h.date}</Td>
                    <Td className="text-gray-500">{weekdayOf(h.date)}</Td>
                    <Td className="font-medium text-gray-900">{h.name}</Td>
                    <Td>{h.substitute ? <Badge tone="blue">대체공휴일</Badge> : <Badge tone="gray">공휴일</Badge>}</Td>
                    {canDelete && (
                      <Td align="right">
                        <Button
                          variant="ghost"
                          size="sm"
                          icon={Trash2}
                          onClick={() => remove(h)}
                          aria-label={`${h.name} 삭제`}
                        />
                      </Td>
                    )}
                  </Tr>
                ))
              )}
            </tbody>
          </Table>
        )}
      </Card>

      <HolidayModal
        open={adding}
        year={year}
        onClose={() => setAdding(false)}
        onSaved={() => {
          setAdding(false);
          load();
        }}
      />
    </>
  );
}
