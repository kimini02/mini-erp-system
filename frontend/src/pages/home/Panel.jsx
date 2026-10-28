import { Link } from 'react-router-dom';
import { ArrowUpRight, ChevronDown, ChevronUp } from 'lucide-react';
import { cn } from '@/lib/cn';

const iconButton =
  'flex h-9 w-9 items-center justify-center rounded-lg text-gray-500 hover:bg-gray-100 hover:text-gray-800';

export default function Panel({ id, icon: Icon, tint, title, extra, link, actions, collapse, className, children }) {
  const closed = collapse.isClosed(id);
  return (
    <section
      id={id}
      aria-labelledby={`${id}-title`}
      className={cn('flex min-w-0 scroll-mt-4 flex-col rounded-[14px] border border-gray-200 bg-white', className)}
    >
      <div className={cn('flex h-14 items-center gap-2.5 pl-4 pr-3', !closed && 'border-b border-gray-100')}>
        <span className={cn('flex h-8 w-8 shrink-0 items-center justify-center rounded-full', tint)}>
          {Icon && <Icon size={16} aria-hidden="true" />}
        </span>
        <h2 id={`${id}-title`} className="whitespace-nowrap text-[15px] font-bold text-gray-900">
          {title}
        </h2>
        {extra}
        <div className="ml-auto flex items-center">
          {actions}
          {link && (
            <Link to={link.to} aria-label={link.label} title={link.label} className={iconButton}>
              <ArrowUpRight size={16} />
            </Link>
          )}
          <button
            type="button"
            onClick={() => collapse.toggle(id)}
            aria-expanded={!closed}
            aria-controls={`${id}-body`}
            aria-label={`${title} ${closed ? '펼치기' : '접기'}`}
            className={iconButton}
          >
            {closed ? <ChevronDown size={16} /> : <ChevronUp size={16} />}
          </button>
        </div>
      </div>
      <div id={`${id}-body`} hidden={closed} className={closed ? 'hidden' : 'flex flex-1 flex-col'}>
        {children}
      </div>
    </section>
  );
}

/* ───────── 내 정보 (프로필 · 출퇴근 · 숫자 칸) ───────── */
