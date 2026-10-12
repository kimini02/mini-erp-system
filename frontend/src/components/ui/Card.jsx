import { cn } from '@/lib/cn';

export function Card({ className, children, ...props }) {
  return (
    <section className={cn('rounded-xl border border-gray-200 bg-white', className)} {...props}>
      {children}
    </section>
  );
}

/** 카드 상단 제목줄: 왼쪽 제목/설명, 오른쪽 액션 */
export function CardHeader({ title, description, action, className }) {
  return (
    <div className={cn('flex items-center justify-between gap-4 border-b border-gray-100 px-5 py-4', className)}>
      <div className="min-w-0">
        <h3 className="text-[15px] font-semibold text-gray-900">{title}</h3>
        {description && <p className="mt-0.5 text-[13px] text-gray-500">{description}</p>}
      </div>
      {action && <div className="flex shrink-0 items-center gap-2">{action}</div>}
    </div>
  );
}

export function CardBody({ className, children }) {
  return <div className={cn('p-5', className)}>{children}</div>;
}

export function CardFooter({ className, children }) {
  return (
    <div className={cn('flex items-center justify-end gap-2 border-t border-gray-100 px-5 py-3', className)}>
      {children}
    </div>
  );
}
