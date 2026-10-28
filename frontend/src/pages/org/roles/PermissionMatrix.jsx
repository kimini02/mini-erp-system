import { Fragment, useMemo } from 'react';
import { SCOPES, SCOPE_LABELS } from '@/lib/permissions';
import { cn } from '@/lib/cn';
import { Select, Table, Td, Th, Tr } from '@/components/ui';

const ACTION_COLUMNS = [
  { key: 'view', action: 'V', label: '보기' },
  { key: 'create', action: 'C', label: '등록' },
  { key: 'update', action: 'U', label: '수정' },
  { key: 'delete', action: 'D', label: '삭제' },
];

export default function PermissionMatrix({ menus, permissions, locked, editable, onChange }) {
  const byMenu = useMemo(() => Object.fromEntries(permissions.map((p) => [p.menu, p])), [permissions]);
  const groups = useMemo(() => {
    const out = [];
    menus.forEach((m) => {
      const last = out[out.length - 1];
      if (last && last.name === m.group) last.items.push(m);
      else out.push({ name: m.group, items: [m] });
    });
    return out;
  }, [menus]);

  const update = (menu, patch) => {
    onChange(
      permissions.map((p) => {
        if (p.menu !== menu.code) return p;
        const next = { ...p, ...patch };
        const anyOn = ACTION_COLUMNS.some((c) => next[c.key]);
        // 범위가 있는 메뉴: 처음 켜면 '본인'으로 시작, 모두 끄면 범위도 비움 (서버 검증 규칙과 같음)
        if (menu.scopeApplicable) next.scope = anyOn ? next.scope || 'SELF' : null;
        return next;
      }),
    );
  };

  const disabled = locked || !editable;

  return (
    <Table>
      <thead>
        <tr>
          <Th className="min-w-[200px]">메뉴</Th>
          {ACTION_COLUMNS.map((c) => (
            <Th key={c.key} align="center" className="w-16">
              {c.label}
            </Th>
          ))}
          <Th className="w-52">데이터 범위</Th>
        </tr>
      </thead>
      <tbody>
        {groups.map((g) => (
          <Fragment key={g.name}>
            <tr className="bg-gray-50/60">
              <td colSpan={6} className="px-4 pb-1.5 pt-3 text-xs font-semibold text-gray-500">
                {g.name}
              </td>
            </tr>
            {g.items.map((m) => {
              const p = byMenu[m.code];
              const anyOn = ACTION_COLUMNS.some((c) => p[c.key]);
              return (
                <Tr key={m.code}>
                  <Td className={cn('font-medium', anyOn ? 'text-gray-900' : 'text-gray-500')}>{m.name}</Td>
                  {ACTION_COLUMNS.map((c) => (
                    <Td key={c.key} align="center">
                      {m.allowedActions.includes(c.action) ? (
                        <input
                          type="checkbox"
                          checked={!!p[c.key]}
                          disabled={disabled}
                          onChange={(e) => update(m, { [c.key]: e.target.checked })}
                          aria-label={`${m.name} ${c.label}`}
                          className="h-4 w-4 cursor-pointer rounded border-gray-300 text-blue-600 focus:ring-blue-500 disabled:cursor-not-allowed disabled:opacity-60"
                        />
                      ) : (
                        <span className="text-gray-300" aria-label="해당 없음">
                          —
                        </span>
                      )}
                    </Td>
                  ))}
                  <Td>
                    {m.scopeApplicable ? (
                      <Select
                        value={p.scope || ''}
                        disabled={disabled || !anyOn}
                        onChange={(e) => update(m, { scope: e.target.value })}
                        aria-label={`${m.name} 데이터 범위`}
                        className="h-8 text-[13px]"
                      >
                        {!anyOn && <option value="">—</option>}
                        {SCOPES.map((s) => (
                          <option key={s} value={s}>
                            {SCOPE_LABELS[s]}
                          </option>
                        ))}
                      </Select>
                    ) : (
                      <span className="text-[13px] text-gray-300">—</span>
                    )}
                  </Td>
                </Tr>
              );
            })}
          </Fragment>
        ))}
      </tbody>
    </Table>
  );
}

/* ───────── 역할 만들기 · 이름 수정 ───────── */
