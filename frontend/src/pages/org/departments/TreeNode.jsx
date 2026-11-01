import { ChevronRight } from 'lucide-react';
import { cn } from '@/lib/cn';
import { Badge } from '@/components/ui';

export default function TreeNode({ node, depth, selectedId, collapsed, onSelect, onToggle }) {
  const hasChildren = node.children.length > 0;
  const open = !collapsed.has(node.id);
  const selected = node.id === selectedId;

  return (
    <li role="treeitem" aria-expanded={hasChildren ? open : undefined} aria-selected={selected}>
      <div
        className={cn('flex items-center rounded-lg pr-2', selected ? 'bg-blue-50' : 'hover:bg-gray-50')}
        style={{ paddingLeft: depth * 16 }}
      >
        {hasChildren ? (
          <button
            type="button"
            onClick={() => onToggle(node.id)}
            className="flex h-9 w-7 shrink-0 items-center justify-center rounded-md text-gray-400 hover:text-gray-700"
            aria-label={`${node.name} ${open ? '접기' : '펼치기'}`}
          >
            <ChevronRight size={14} className={cn('transition-transform', open && 'rotate-90')} />
          </button>
        ) : (
          <span className="w-7 shrink-0" />
        )}
        <button
          type="button"
          onClick={() => onSelect(node.id)}
          className="flex h-9 min-w-0 flex-1 items-center gap-2 text-left text-sm"
        >
          <span className={cn('truncate', selected ? 'font-semibold text-blue-700' : 'font-medium text-gray-800')}>
            {node.name}
          </span>
          {!node.head && (
            <Badge tone="amber" className="!px-1.5 text-[11px]">
              부서장 공석
            </Badge>
          )}
          <span className="ml-auto text-xs tabular-nums text-gray-500">{node.totalMemberCount}</span>
        </button>
      </div>
      {hasChildren && open && (
        <ul role="group">
          {node.children.map((child) => (
            <TreeNode
              key={child.id}
              node={child}
              depth={depth + 1}
              selectedId={selectedId}
              collapsed={collapsed}
              onSelect={onSelect}
              onToggle={onToggle}
            />
          ))}
        </ul>
      )}
    </li>
  );
}

/* ───────── 근무시간 ───────── */
