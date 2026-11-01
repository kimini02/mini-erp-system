import { cn } from '@/lib/cn';

const alignClass = { left: 'text-left', center: 'text-center', right: 'text-right' };

export function Table({ className, children }) {
  return (
    // relative: 머리칸의 sr-only(절대 위치) 글자가 스크롤 영역 밖으로 나가 페이지를 가로로 넓히지 않게
    <div className="relative overflow-x-auto">
      <table className={cn('w-full border-collapse text-sm', className)}>{children}</table>
    </div>
  );
}

export function Th({ align = 'left', className, children }) {
  return (
    <th
      className={cn(
        'h-10 whitespace-nowrap border-b border-gray-200 bg-gray-50 px-4 text-xs font-medium text-gray-500',
        alignClass[align],
        className,
      )}
    >
      {children}
    </th>
  );
}

export function Tr({ className, children, ...props }) {
  return (
    <tr className={cn('border-b border-gray-100 last:border-0 hover:bg-gray-50/70', className)} {...props}>
      {children}
    </tr>
  );
}

export function Td({ align = 'left', className, children, ...props }) {
  return (
    <td className={cn('h-12 whitespace-nowrap px-4 py-2.5 text-gray-700', alignClass[align], className)} {...props}>
      {children}
    </td>
  );
}

/** 데이터가 없을 때 표 본문에 표시 */
export function TableEmpty({ colSpan, children = '표시할 내역이 없습니다.' }) {
  return (
    <tr>
      <td colSpan={colSpan} className="py-16 text-center text-sm text-gray-500">
        {children}
      </td>
    </tr>
  );
}
