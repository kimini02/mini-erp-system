/** 조직도 트리 → [{ id, name, depth }] (부서 선택 목록용) */
export function flattenTree(node, depth = 0, out = []) {
  if (!node) return out;
  out.push({ id: node.id, name: node.name, depth });
  (node.children || []).forEach((child) => flattenTree(child, depth + 1, out));
  return out;
}

/** 트리에서 id 부서와 그 하위 부서 id 전부 */
export function collectSubtreeIds(node, targetId) {
  const find = (n) => {
    if (!n) return null;
    if (n.id === targetId) return n;
    for (const c of n.children || []) {
      const hit = find(c);
      if (hit) return hit;
    }
    return null;
  };
  const ids = new Set();
  const walk = (n) => {
    ids.add(n.id);
    (n.children || []).forEach(walk);
  };
  const target = find(node);
  if (target) walk(target);
  return ids;
}
