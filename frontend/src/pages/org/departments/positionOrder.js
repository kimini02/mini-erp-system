export const POSITION_ORDER_NOTICE = '새 직급은 맨 앞에 들어갑니다. 순서를 확인하고 저장하세요.';

export function movePosition(positions, index, direction) {
  const nextIndex = index + direction;
  if (index < 0 || index >= positions.length || nextIndex < 0 || nextIndex >= positions.length) return positions;
  const next = [...positions];
  [next[index], next[nextIndex]] = [next[nextIndex], next[index]];
  return next;
}

export const positionOrder = (positions) =>
  positions.map((position, index) => ({ id: position.id, rankOrder: index + 1 }));
