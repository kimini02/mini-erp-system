import { Field, Input, Textarea } from '@/components/ui';

export default function GeneralFields({ title, setTitle, body, set }) {
  return (
    <div className="space-y-4">
      <Field label="제목" htmlFor="gn-title" required>
        <Input id="gn-title" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} />
      </Field>
      <Field label="금액" htmlFor="gn-amount" hint="비용이 없는 품의는 비워 두세요.">
        <Input
          id="gn-amount"
          type="number"
          min="0"
          step="1000"
          value={body.amount}
          onChange={(e) => set('amount', e.target.value)}
          trailing="원"
        />
      </Field>
      <Field label="내용" htmlFor="gn-content" required>
        <Textarea id="gn-content" rows={6} value={body.content} onChange={(e) => set('content', e.target.value)} />
      </Field>
    </div>
  );
}

/* ───────── 결재선 ───────── */
