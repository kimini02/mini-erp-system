// 조건부 className 조합용 헬퍼: cn('a', cond && 'b') → 'a b'
export const cn = (...classes) => classes.filter(Boolean).join(' ');
