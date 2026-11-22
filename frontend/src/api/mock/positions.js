/** 직급: 인증은 공통 어댑터, 쓰기 권한은 실제 API와 같은 DEPARTMENT C/U/D. */
export function installPositions({ route, db, need, fail, created }) {
  let nextId = Math.max(0, ...db.positions.map((position) => position.id)) + 1;
  const list = () =>
    db.positions.map((position) => ({ ...position })).sort((a, b) => a.rankOrder - b.rankOrder || a.id - b.id);
  const find = (id) => {
    const position = db.positions.find((item) => item.id === Number(id));
    if (!position) fail(404, 'POSITION_NOT_FOUND', '직급을 찾을 수 없습니다.');
    return position;
  };
  const validateName = (name, id) => {
    if (typeof name !== 'string' || !name.trim() || name.length > 100) {
      fail(400, 'INVALID_INPUT_VALUE', '입력값을 확인해 주세요.');
    }
    if (
      db.positions.some((item) => item.id !== id && item.name.localeCompare(name, 'ko', { sensitivity: 'base' }) === 0)
    ) {
      fail(409, 'DUPLICATE_RESOURCE', '이미 존재하는 데이터입니다.');
    }
  };

  route('GET', '/positions', list);
  route('POST', '/positions', (ctx) => {
    need(ctx, 'DEPARTMENT', 'C');
    validateName(ctx.body.name);
    const position = { id: nextId++, name: ctx.body.name, rankOrder: 0 };
    db.positions.push(position);
    return created({ ...position });
  });
  route('PATCH', '/positions/:id', (ctx) => {
    need(ctx, 'DEPARTMENT', 'U');
    const position = find(ctx.path.id);
    validateName(ctx.body.name, position.id);
    position.name = ctx.body.name;
    return { id: position.id, name: position.name };
  });
  route('PUT', '/positions/order', (ctx) => {
    need(ctx, 'DEPARTMENT', 'U');
    const order = ctx.body;
    if (!Array.isArray(order) || new Set(order.map((item) => item?.id)).size !== order.length) {
      fail(400, 'INVALID_INPUT_VALUE', '입력값을 확인해 주세요.');
    }
    // 실제 API의 트랜잭션처럼 모두 검사한 후 바꾼다.
    for (const item of order) {
      if (
        !item ||
        !Number.isInteger(item.id) ||
        !Number.isInteger(item.rankOrder) ||
        item.rankOrder < -2147483648 ||
        item.rankOrder > 2147483647
      ) {
        fail(400, 'INVALID_INPUT_VALUE', '입력값을 확인해 주세요.');
      }
      find(item.id);
    }
    for (const item of order) find(item.id).rankOrder = item.rankOrder;
    return list();
  });
  route('DELETE', '/positions/:id', (ctx) => {
    need(ctx, 'DEPARTMENT', 'D');
    const position = find(ctx.path.id);
    if (db.users.some((user) => user.positionId === position.id)) {
      fail(409, 'POSITION_IN_USE', '이 직급을 쓰는 사원이 있어 삭제할 수 없습니다.');
    }
    db.positions = db.positions.filter((item) => item.id !== position.id);
    return null;
  });
}
