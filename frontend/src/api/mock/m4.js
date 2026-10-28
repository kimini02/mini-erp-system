/**
 * M4 홈 · 프로젝트 보드 가짜 서버 (backend/md/15-rest-api-v2-m4.md)
 * 프로젝트 권한(PROJECT 메뉴 + 데이터 범위), 간격을 둔 카드 위치, version 409, 업무 번호, 변경 이력,
 * 홈 위젯 모음을 명세대로 흉내 냅니다. 가짜 서버라 '프로젝트 행 잠금'은 한 번에 하나씩 처리되는 것으로 대신합니다.
 */

const todayStr = () => new Date().toLocaleDateString('sv-SE');
const addDays = (date, n) => {
  const d = new Date(`${date}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
};
const daysBetween = (a, b) => Math.round((new Date(`${b}T00:00:00Z`) - new Date(`${a}T00:00:00Z`)) / 86400000);
const GAP = 1024;
const STATUSES = ['TODO', 'DOING', 'DONE'];
const PRIORITIES = ['HIGH', 'MEDIUM', 'LOW'];

export function installM4({
  route,
  db,
  fail,
  created,
  need,
  mergedPermissions,
  userById,
  positionById,
  subtreeIds,
  m2,
  callRoute,
}) {
  const { state: m2state, helpers } = m2;
  const { nowLocal } = helpers;
  const st = { projects: [], tasks: [], histories: [], seq: { project: 1, task: 1, history: 1 } };
  db.m4 = st;

  /* ───────── 권한 (명세 1장) ───────── */

  const grantOf = (user) => mergedPermissions(user).PROJECT || null;
  const isMember = (p, userId) => p.leaderId === userId || p.memberIds.includes(userId);
  function visibleTo(user, p) {
    const g = grantOf(user);
    if (!g?.view) return false;
    if (g.scope === 'ALL' || isMember(p, user.id)) return true;
    if (g.scope !== 'DEPT' && g.scope !== 'DEPT_TREE') return false;
    const depts = g.scope === 'DEPT_TREE' ? new Set(subtreeIds(user.departmentId)) : new Set([user.departmentId]);
    return depts.has(userById(p.leaderId)?.departmentId);
  }
  /** 보기 범위 확인 (쓰기도 V가 먼저 필요) */
  function visibleProject(ctx, id) {
    need(ctx, 'PROJECT', 'V');
    const p =
      st.projects.find((x) => x.id === Number(id)) || fail(404, 'PROJECT_NOT_FOUND', '프로젝트를 찾을 수 없습니다.');
    if (!visibleTo(ctx.user, p)) fail(403, 'ACCESS_DENIED', '접근 권한이 없습니다.');
    return p;
  }
  const canManage = (user, p) => p.leaderId === user.id || Boolean(grantOf(user)?.update);
  const requireManage = (ctx, p) => {
    if (!canManage(ctx.user, p)) fail(403, 'ACCESS_DENIED', '프로젝트 담당 팀장만 할 수 있습니다.');
  };
  const taskOf = (id) =>
    st.tasks.find((t) => t.id === Number(id)) || fail(404, 'TASK_NOT_FOUND', '업무를 찾을 수 없습니다.');
  const projectOfTask = (t) => st.projects.find((p) => p.id === t.projectId);

  /* ───────── 보기 모양 ───────── */

  const brief = (u) => (u ? { id: u.id, name: u.name, title: positionById(u.positionId)?.name || '' } : null);
  const tasksIn = (p) => st.tasks.filter((t) => t.projectId === p.id);
  const overdueDays = (t) => (t.status === 'DONE' ? 0 : Math.max(0, daysBetween(t.endDate, todayStr())));
  const keyOf = (t, p = projectOfTask(t)) => `${p.key}-${t.no}`;

  function summary(p) {
    const ts = tasksIn(p);
    const done = ts.filter((t) => t.status === 'DONE').length;
    return {
      taskCount: ts.length,
      doneCount: done,
      progressPct: ts.length ? Math.floor((done * 100) / ts.length) : 0,
      overdueCount: ts.filter((t) => overdueDays(t) > 0).length,
    };
  }
  function projectView(p, user) {
    const s = summary(p);
    return {
      id: p.id,
      key: p.key,
      title: p.title,
      content: p.content,
      status: p.status,
      priority: p.priority,
      startDate: p.startDate,
      endDate: p.endDate,
      leader: brief(userById(p.leaderId)),
      memberCount: new Set([p.leaderId, ...p.memberIds]).size,
      ...s,
      myRole: p.leaderId === user.id ? 'LEADER' : p.memberIds.includes(user.id) ? 'MEMBER' : null,
    };
  }
  const membersOf = (p) =>
    [...new Set([p.leaderId, ...p.memberIds])]
      .map(userById)
      .filter(Boolean)
      .sort((a, b) => a.name.localeCompare(b.name, 'ko'))
      .map(brief);
  function cardView(t, p, user) {
    const assignees = t.assigneeIds
      .map(userById)
      .filter(Boolean)
      .map((u) => ({ id: u.id, name: u.name }));
    return {
      id: t.id,
      key: keyOf(t, p),
      title: t.title,
      status: t.status,
      priority: t.priority,
      endDate: t.endDate,
      overdueDays: overdueDays(t),
      assignees,
      position: t.position,
      version: t.version,
      canMove: canManage(user, p) || t.assigneeIds.includes(user.id),
    };
  }
  function historyView(h) {
    const actor = userById(h.actorId);
    const target = h.targetId ? userById(h.targetId) : null;
    return {
      action: h.action,
      ...(h.action === 'MOVED' ? {} : { from: h.from ?? null, to: h.to ?? null }),
      ...(target ? { target: { id: target.id, name: target.name } } : {}),
      actor: actor ? { id: actor.id, name: actor.name } : null,
      at: h.at,
    };
  }
  function taskDetail(t, p, user) {
    return {
      ...cardView(t, p, user),
      projectId: p.id,
      content: t.content,
      canManage: canManage(user, p),
      assignees: t.assigneeIds.map(userById).filter(Boolean).map(brief),
      history: st.histories
        .filter((h) => h.taskId === t.id)
        .sort((a, b) => b.id - a.id)
        .slice(0, 50)
        .map(historyView),
    };
  }

  /* ───────── 쓰기 도우미 ───────── */

  function addHistory(task, actorId, action, from = null, to = null, targetId = null, at = nowLocal()) {
    st.histories.push({ id: st.seq.history++, taskId: task.id, actorId, action, from, to, targetId, at });
  }
  // 알림 title 은 키 + 제목, 문장은 화면이 type 으로 만듦 (명세 4.5)
  function notifyTask(userId, type, task, p, at = nowLocal()) {
    m2state.notifications.push({
      id: m2state.seq.notification++,
      userId,
      type,
      title: `${keyOf(task, p)} ${task.title}`,
      link: `/projects/${p.id}/board?taskId=${task.id}`,
      read: false,
      createdAt: at,
    });
  }
  /** 명세 4.4: 업무가 없거나 모두 TODO → READY, 모두 DONE → DONE, 그 밖 PROGRESS */
  function recalc(p) {
    const ts = tasksIn(p);
    const todo = ts.filter((t) => t.status === 'TODO').length;
    const done = ts.filter((t) => t.status === 'DONE').length;
    p.status = ts.length === 0 || todo === ts.length ? 'READY' : done === ts.length ? 'DONE' : 'PROGRESS';
    const s = summary(p);
    return { id: p.id, status: p.status, taskCount: s.taskCount, doneCount: s.doneCount, progressPct: s.progressPct };
  }
  /** 명세 4.3: 위 카드와 아래 카드의 중간값, 간격이 없으면 그 열만 다시 번호 매김 */
  function positionFor(p, status, movingId, afterId) {
    const column = () =>
      tasksIn(p)
        .filter((t) => t.status === status && t.id !== movingId)
        .sort((a, b) => a.position - b.position || a.id - b.id);
    if (afterId != null) {
      const after = st.tasks.find((t) => t.id === Number(afterId));
      if (!after || after.id === movingId || after.projectId !== p.id || after.status !== status) {
        fail(422, 'INVALID_TASK_POSITION', '옮길 위치가 올바르지 않습니다.');
      }
    }
    for (let attempt = 0; attempt < 2; attempt += 1) {
      const cards = column();
      const index = afterId == null ? -1 : cards.findIndex((t) => t.id === Number(afterId));
      const lower = index < 0 ? 0 : cards[index].position;
      const upper = cards[index + 1]?.position;
      if (upper == null) return lower + GAP;
      if (upper - lower > 1) return lower + Math.floor((upper - lower) / 2);
      cards.forEach((t, i) => {
        t.position = (i + 1) * GAP;
      });
    }
    return fail(500, 'INTERNAL_SERVER_ERROR', '위치를 정하지 못했습니다.');
  }
  const appendPosition = (p, status) =>
    Math.max(
      0,
      ...tasksIn(p)
        .filter((t) => t.status === status)
        .map((t) => t.position),
    ) + GAP;
  function validateAssignees(p, ids) {
    const unique = [...new Set((ids || []).map(Number))];
    if (unique.some((id) => !isMember(p, id)))
      fail(422, 'TASK_ASSIGNEE_NOT_MEMBER', '프로젝트 멤버만 담당자로 지정할 수 있습니다.');
    return unique;
  }
  function assign(task, p, ids, actorId) {
    const at = nowLocal();
    task.assigneeIds
      .filter((id) => !ids.includes(id))
      .forEach((id) => addHistory(task, actorId, 'UNASSIGNED', null, null, id, at));
    ids
      .filter((id) => !task.assigneeIds.includes(id))
      .forEach((id) => {
        addHistory(task, actorId, 'ASSIGNED', null, null, id, at);
        notifyTask(id, 'TASK_ASSIGNED', task, p, at);
      });
    task.assigneeIds = ids;
  }

  /* ───────── 라우트: 프로젝트 (명세 3장) ───────── */

  route('GET', '/projects', (ctx) => {
    need(ctx, 'PROJECT', 'V');
    const q = (ctx.params.q || '').trim().toLowerCase();
    const rows = st.projects
      .filter((p) => visibleTo(ctx.user, p))
      .filter((p) => !ctx.params.status || p.status === ctx.params.status)
      .filter((p) => !q || p.title.toLowerCase().includes(q) || p.key.toLowerCase().includes(q))
      .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt) || b.id - a.id)
      .map((p) => projectView(p, ctx.user));
    const number = Math.max(0, Number(ctx.params.page ?? 0));
    const size = Math.max(1, Number(ctx.params.size ?? 20));
    const content = rows.slice(number * size, number * size + size);
    const totalPages = Math.max(1, Math.ceil(rows.length / size));
    return {
      content,
      page: {
        number,
        size,
        totalElements: rows.length,
        totalPages,
        first: number === 0,
        last: number >= totalPages - 1,
        numberOfElements: content.length,
      },
    };
  });

  route('POST', '/projects', (ctx) => {
    need(ctx, 'PROJECT', 'C');
    const b = ctx.body;
    if (!/^[A-Z0-9]{2,10}$/.test(b.key || '') || !b.title?.trim() || !b.startDate || !b.endDate || !b.leaderId) {
      fail(400, 'INVALID_INPUT_VALUE', '키(영문 대문자·숫자 2~10자), 이름, 기간, 담당 팀장을 입력해 주세요.');
    }
    if (b.endDate < b.startDate) fail(400, 'INVALID_PROJECT_PERIOD', '종료일은 시작일보다 빠를 수 없습니다.');
    if (st.projects.some((p) => p.key === b.key)) fail(409, 'PROJECT_KEY_DUPLICATE', '이미 쓰는 프로젝트 키입니다.');
    const leader = userById(b.leaderId);
    if (!leader || leader.status !== 'ACTIVE') fail(400, 'INVALID_INPUT_VALUE', '재직 중인 사원을 지정해 주세요.');
    const now = nowLocal();
    const p = {
      id: st.seq.project++,
      key: b.key,
      title: b.title.trim(),
      content: b.content || '',
      status: 'READY',
      priority: b.priority || 'MEDIUM',
      startDate: b.startDate,
      endDate: b.endDate,
      leaderId: leader.id,
      memberIds: [],
      lastTaskSeq: 0,
      createdAt: now,
      updatedAt: now,
    };
    st.projects.push(p);
    return created({ ...projectView(p, ctx.user), members: membersOf(p) });
  });

  route('GET', '/projects/:id', (ctx) => {
    const p = visibleProject(ctx, ctx.path.id);
    return {
      ...projectView(p, ctx.user),
      members: membersOf(p),
      permissions: { canManage: canManage(ctx.user, p), canCreateTask: canManage(ctx.user, p) },
    };
  });

  route('PATCH', '/projects/:id', (ctx) => {
    const p = visibleProject(ctx, ctx.path.id);
    requireManage(ctx, p);
    const b = ctx.body;
    if (b.key !== undefined) fail(400, 'INVALID_INPUT_VALUE', '프로젝트 키는 바꿀 수 없습니다.');
    const start = b.startDate || p.startDate;
    const end = b.endDate || p.endDate;
    if (end < start) fail(400, 'INVALID_PROJECT_PERIOD', '종료일은 시작일보다 빠를 수 없습니다.');
    if (b.title !== undefined && !b.title.trim()) fail(400, 'INVALID_INPUT_VALUE', '이름을 입력해 주세요.');
    if (b.leaderId) {
      const leader = userById(b.leaderId);
      if (!leader || leader.status !== 'ACTIVE') fail(400, 'INVALID_INPUT_VALUE', '재직 중인 사원을 지정해 주세요.');
      if (p.leaderId !== leader.id && !p.memberIds.includes(p.leaderId)) p.memberIds.push(p.leaderId);
      p.leaderId = leader.id;
    }
    Object.assign(p, {
      title: b.title?.trim() ?? p.title,
      content: b.content ?? p.content,
      priority: b.priority ?? p.priority,
      startDate: start,
      endDate: end,
      updatedAt: nowLocal(),
    });
    return { ...projectView(p, ctx.user), members: membersOf(p) };
  });

  route('PUT', '/projects/:id/members', (ctx) => {
    const p = visibleProject(ctx, ctx.path.id);
    requireManage(ctx, p);
    const requested = [...new Set((ctx.body.userIds || []).map(Number))].filter((id) => id !== p.leaderId);
    const current = new Set(p.memberIds);
    // 재직 검사는 새로 넣는 사람에게만 (기존 퇴사자 멤버는 그대로 두거나 뺄 수 있음)
    requested
      .filter((id) => !current.has(id))
      .forEach((id) => {
        if (userById(id)?.status !== 'ACTIVE')
          fail(400, 'INVALID_INPUT_VALUE', '재직 중인 사원만 멤버로 추가할 수 있습니다.');
      });
    const removed = p.memberIds.filter((id) => !requested.includes(id));
    if (tasksIn(p).some((t) => t.status !== 'DONE' && t.assigneeIds.some((id) => removed.includes(id)))) {
      fail(
        409,
        'PROJECT_MEMBER_HAS_TASKS',
        '담당 중인 미완료 업무가 있어 멤버에서 뺄 수 없습니다. 담당자를 먼저 바꿔 주세요.',
      );
    }
    p.memberIds = requested;
    p.updatedAt = nowLocal();
    return {
      ...projectView(p, ctx.user),
      members: membersOf(p),
      permissions: { canManage: canManage(ctx.user, p), canCreateTask: canManage(ctx.user, p) },
    };
  });

  /* ───────── 라우트: 보드 · 업무 (명세 4장) ───────── */

  route('GET', '/projects/:id/board', (ctx) => {
    const p = visibleProject(ctx, ctx.path.id);
    const cards = tasksIn(p).sort((a, b) => a.position - b.position || a.id - b.id);
    return {
      project: projectView(p, ctx.user),
      members: membersOf(p),
      permissions: { canManage: canManage(ctx.user, p), canCreateTask: canManage(ctx.user, p) },
      columns: STATUSES.map((status) => ({
        status,
        tasks: cards.filter((t) => t.status === status).map((t) => cardView(t, p, ctx.user)),
      })),
    };
  });

  route('POST', '/projects/:id/tasks', (ctx) => {
    const p = visibleProject(ctx, ctx.path.id);
    requireManage(ctx, p);
    const b = ctx.body;
    if (!b.title?.trim()) fail(400, 'INVALID_INPUT_VALUE', '업무 제목을 입력해 주세요.');
    const status = b.status || 'TODO';
    if (!STATUSES.includes(status) || (b.priority && !PRIORITIES.includes(b.priority)))
      fail(400, 'INVALID_INPUT_VALUE', '잘못된 요청 값입니다.');
    const assignees = validateAssignees(p, b.assigneeIds);
    const now = nowLocal();
    p.lastTaskSeq += 1;
    const t = {
      id: st.seq.task++,
      projectId: p.id,
      no: p.lastTaskSeq,
      title: b.title.trim(),
      content: b.content || '',
      status,
      priority: b.priority || 'MEDIUM',
      endDate: b.endDate || p.endDate,
      position: appendPosition(p, status),
      version: 0,
      assigneeIds: [],
      createdBy: ctx.user.id,
      createdAt: now,
    };
    st.tasks.push(t);
    addHistory(t, ctx.user.id, 'CREATED', null, t.title, null, now);
    assign(t, p, assignees, ctx.user.id);
    recalc(p);
    p.updatedAt = now;
    return created(taskDetail(t, p, ctx.user));
  });

  route('GET', '/tasks/:id', (ctx) => {
    const t = taskOf(ctx.path.id);
    const p = visibleProject(ctx, t.projectId);
    return taskDetail(t, p, ctx.user);
  });

  route('PATCH', '/tasks/:id', (ctx) => {
    const t = taskOf(ctx.path.id);
    const p = visibleProject(ctx, t.projectId);
    requireManage(ctx, p);
    const b = ctx.body;
    if (b.title !== undefined && !String(b.title).trim())
      fail(400, 'INVALID_INPUT_VALUE', '업무 제목을 입력해 주세요.');
    if (b.priority && !PRIORITIES.includes(b.priority)) fail(400, 'INVALID_INPUT_VALUE', '잘못된 요청 값입니다.');
    const assignees = b.assigneeIds ? validateAssignees(p, b.assigneeIds) : null;
    const changed = [];
    if (b.title !== undefined && b.title.trim() !== t.title) {
      t.title = b.title.trim();
      changed.push('제목');
    }
    if (b.content !== undefined && b.content !== t.content) {
      t.content = b.content;
      changed.push('설명');
    }
    if (b.endDate && b.endDate !== t.endDate) {
      t.endDate = b.endDate;
      changed.push('마감일');
    }
    if (b.priority && b.priority !== t.priority) {
      t.priority = b.priority;
      changed.push('우선순위');
    }
    if (changed.length) addHistory(t, ctx.user.id, 'UPDATED', null, changed.join(', '));
    if (assignees) assign(t, p, assignees, ctx.user.id);
    t.version += 1;
    recalc(p);
    p.updatedAt = nowLocal();
    return taskDetail(t, p, ctx.user);
  });

  route('POST', '/tasks/:id/move', (ctx) => {
    const t = taskOf(ctx.path.id);
    const p = visibleProject(ctx, t.projectId);
    if (!canManage(ctx.user, p) && !t.assigneeIds.includes(ctx.user.id)) {
      fail(403, 'ACCESS_DENIED', '담당자와 프로젝트 담당 팀장만 옮길 수 있습니다.');
    }
    const { status, afterTaskId = null, version } = ctx.body;
    if (!STATUSES.includes(status) || !Number.isInteger(version))
      fail(400, 'INVALID_INPUT_VALUE', '잘못된 요청 값입니다.');
    if (version !== t.version) {
      fail(409, 'TASK_VERSION_CONFLICT', '다른 사람이 먼저 이 업무를 옮겼습니다. 보드를 새로 고칩니다.', {
        currentTask: cardView(t, p, ctx.user),
      });
    }
    const position = positionFor(p, status, t.id, afterTaskId);
    const from = t.status;
    t.status = status;
    t.position = position;
    t.version += 1;
    if (from !== status) addHistory(t, ctx.user.id, 'STATUS', from, status);
    else addHistory(t, ctx.user.id, 'MOVED');
    p.updatedAt = nowLocal();
    return { task: cardView(t, p, ctx.user), project: recalc(p) };
  });

  /* ───────── 라우트: 홈 (명세 2장) ───────── */

  function weekRange() {
    const today = todayStr();
    const dow = new Date(`${today}T00:00:00Z`).getUTCDay(); // 0 일요일
    const monday = addDays(today, dow === 0 ? -6 : 1 - dow);
    return { from: monday, to: addDays(monday, 4) };
  }

  function teamWeek(user) {
    const { from, to } = weekRange();
    const dept = db.departments.find((d) => d.id === user.departmentId);
    const teamIds = new Set(
      db.users.filter((u) => u.departmentId === user.departmentId && u.status !== 'RETIRED').map((u) => u.id),
    );
    const days = [];
    for (let date = from; date <= to; date = addDays(date, 1)) days.push({ date, items: [] });
    const dayOf = (date) => days.find((d) => d.date === date);
    m2state.documents
      .filter(
        (d) => d.formCode === 'LEAVE' && ['APPROVED', 'IN_PROGRESS'].includes(d.status) && teamIds.has(d.drafterId),
      )
      .forEach((d) => {
        const start = d.body.startDate;
        const end = d.body.endDate || start;
        for (let date = start < from ? from : start; date <= end && date <= to; date = addDays(date, 1)) {
          const type = ['HALF_AM', 'HALF_PM'].includes(d.body.leaveType) ? d.body.leaveType : 'LEAVE';
          dayOf(date)?.items.push({
            type,
            user: { id: d.drafterId, name: userById(d.drafterId)?.name },
            status: d.status,
            documentId: d.id,
          });
        }
      });
    st.tasks
      .filter(
        (t) =>
          t.status !== 'DONE' && t.endDate >= from && t.endDate <= to && t.assigneeIds.some((id) => teamIds.has(id)),
      )
      .forEach((t) => {
        const p = projectOfTask(t);
        dayOf(t.endDate)?.items.push({
          type: 'TASK_DUE',
          task: { id: t.id, key: keyOf(t, p), title: t.title },
          project: { id: p.id, key: p.key },
        });
      });
    return { department: dept ? { id: dept.id, name: dept.name } : null, from, to, days };
  }

  function myTasks(user) {
    if (!grantOf(user)?.view) return null;
    const open = st.tasks.filter((t) => t.status !== 'DONE' && t.assigneeIds.includes(user.id));
    const byProject = new Map();
    open.forEach((t) => byProject.set(t.projectId, (byProject.get(t.projectId) || 0) + 1));
    const top = [...byProject.entries()].sort(
      (a, b) =>
        b[1] - a[1] ||
        (projectOfTask({ projectId: b[0] }).updatedAt || '').localeCompare(
          projectOfTask({ projectId: a[0] }).updatedAt || '',
        ),
    )[0];
    const p = top ? st.projects.find((x) => x.id === top[0]) : null;
    const items = open
      .sort((a, b) => (overdueDays(b) > 0) - (overdueDays(a) > 0) || a.endDate.localeCompare(b.endDate))
      .slice(0, 5)
      .map((t) => ({
        id: t.id,
        projectId: t.projectId,
        key: keyOf(t),
        title: t.title,
        status: t.status,
        endDate: t.endDate,
        overdueDays: overdueDays(t),
      }));
    if (!p) return { project: null, count: open.length, items };
    const s = summary(p);
    return {
      project: {
        id: p.id,
        key: p.key,
        title: p.title,
        total: s.taskCount,
        done: s.doneCount,
        overdue: s.overdueCount,
        progressPct: s.progressPct,
      },
      count: open.length,
      items,
    };
  }

  route('GET', '/home', (ctx) => {
    const user = ctx.user;
    const inbox = (box, size) => callRoute('GET', '/approval-inbox', ctx, { box, page: 0, size });
    const todo = inbox('todo', 3);
    const mine = inbox('mine', 10);
    const docView = (r) => ({
      id: r.id,
      docNo: r.docNo,
      form: { code: r.formCode, shortName: r.formName },
      title: r.title,
      status: r.status,
      drafter: r.drafter,
      currentApprover: r.currentApprover,
      submittedAt: r.submittedAt,
    });
    const notifications = callRoute('GET', '/notifications', ctx, { page: 0, size: 5 });
    const unread = callRoute('GET', '/notifications/unread-count', ctx);
    return {
      today: callRoute('GET', '/attendance/today', ctx),
      approvals: todo
        ? {
            todoCount: todo.page.totalElements,
            todo: todo.content.map(docView),
            mine: (mine?.content || [])
              .filter((r) => r.status === 'IN_PROGRESS')
              .slice(0, 3)
              .map(docView),
          }
        : null,
      leave: callRoute('GET', '/leave/balance', ctx, { year: Number(todayStr().slice(0, 4)) }),
      teamWeek: teamWeek(user),
      myTasks: myTasks(user),
      notifications: { unread: unread?.count ?? 0, items: notifications?.content || [] },
    };
  });

  /* ───────── 처음 데이터 (시안의 '사내 그룹웨어 개발' 보드) ───────── */

  function seed() {
    const today = todayStr();
    const at = (offset, time = '09:30:00') => `${addDays(today, offset)}T${time}`;
    const project = (key, title, leaderId, memberIds, startOffset, endOffset, priority, content) => {
      const p = {
        id: st.seq.project++,
        key,
        title,
        content,
        status: 'READY',
        priority,
        startDate: addDays(today, startOffset),
        endDate: addDays(today, endOffset),
        leaderId,
        memberIds,
        lastTaskSeq: 0,
        createdAt: at(startOffset),
        updatedAt: at(startOffset),
      };
      st.projects.push(p);
      return p;
    };
    const task = (p, no, title, status, assigneeId, priority, dueOffset, content, story = []) => {
      const t = {
        id: st.seq.task++,
        projectId: p.id,
        no,
        title,
        content,
        status,
        priority,
        endDate: addDays(today, dueOffset),
        position: appendPosition(p, status),
        version: 0,
        assigneeIds: assigneeId ? [assigneeId] : [],
        createdBy: p.leaderId,
        createdAt: at(-40),
      };
      st.tasks.push(t);
      p.lastTaskSeq = Math.max(p.lastTaskSeq, no);
      addHistory(t, p.leaderId, 'CREATED', null, title, null, at(-40, '10:00:00'));
      if (assigneeId) addHistory(t, p.leaderId, 'ASSIGNED', null, null, assigneeId, at(-40, '10:01:00'));
      story.forEach(([action, from, to, actorId, offset]) => {
        addHistory(t, actorId, action, from, to, null, at(offset, '11:20:00'));
        t.version += 1;
      });
      return t;
    };

    const gw = project(
      'GW',
      '사내 그룹웨어 개발',
      8,
      [9, 10, 11, 12, 13, 14],
      -56,
      65,
      'HIGH',
      '전자결재 · 근태 · 휴가 · 조직 · 프로젝트를 한 곳에서 쓰는 사내 그룹웨어를 만듭니다.',
    );
    task(
      gw,
      21,
      '결재선 규칙 해석 API',
      'TODO',
      10,
      'HIGH',
      8,
      '양식의 결재선 규칙(부서장, 상위 부서장, 역할, 특정 사원)을 기안자 기준으로 실제 결재자 목록으로 바꿉니다.\n중복 결재자 생략, 부서장 공석 시 상위 부서장으로 넘기기 포함.',
    );
    task(
      gw,
      22,
      '월간 근태표 조회 쿼리',
      'TODO',
      12,
      'MEDIUM',
      11,
      '사원 × 날짜 근태를 한 번에 조회합니다. 부서 범위 권한을 쿼리 조건에 넣습니다.',
    );
    task(
      gw,
      23,
      '연차 자동 부여 배치',
      'TODO',
      11,
      'MEDIUM',
      18,
      '매년 1월 1일 근속 연수에 맞춰 연차를 부여하고 원장에 기록합니다. 두 번 실행돼도 중복 부여되지 않아야 합니다.',
    );
    task(
      gw,
      24,
      '공휴일 관리 화면',
      'TODO',
      13,
      'LOW',
      21,
      '공휴일과 대체공휴일을 등록합니다. 연차 일수 계산과 근태 판정에 쓰입니다.',
    );
    task(
      gw,
      17,
      '부서 트리 API',
      'DOING',
      9,
      'HIGH',
      2,
      '부서 추가·이동·조회. 자기 하위 부서 밑으로 옮기는 순환 이동은 막습니다.',
      [['STATUS', 'TODO', 'DOING', 9, -9]],
    );
    task(
      gw,
      18,
      '사원 등록·아이디 생성',
      'DOING',
      12,
      'HIGH',
      -3,
      '입사연도 + 순번으로 아이디를 만듭니다. 동시에 두 명을 등록해도 같은 아이디가 나오면 안 됩니다.',
      [['STATUS', 'TODO', 'DOING', 12, -19]],
    );
    task(
      gw,
      19,
      '역할·권한 매트릭스',
      'DOING',
      8,
      'MEDIUM',
      4,
      '메뉴별 보기·등록·수정·삭제와 데이터 범위를 저장하고, API에서 같은 기준으로 검사합니다.',
      [['STATUS', 'TODO', 'DOING', 8, -6]],
    );
    task(
      gw,
      12,
      '로그인·토큰 재발급',
      'DONE',
      8,
      'HIGH',
      -25,
      '로그인과 토큰 재발급. 퇴사 처리된 계정은 바로 막습니다.',
      [
        ['STATUS', 'TODO', 'DOING', 8, -32],
        ['STATUS', 'DOING', 'DONE', 8, -25],
      ],
    );
    task(gw, 13, '결재함 화면', 'DONE', 13, 'MEDIUM', -20, '결재 대기·진행·완료·내 기안·임시 보관 탭.', [
      ['STATUS', 'TODO', 'DOING', 13, -27],
      ['STATUS', 'DOING', 'DONE', 13, -20],
    ]);
    task(
      gw,
      15,
      '초기 스키마 마이그레이션 정리',
      'DONE',
      11,
      'HIGH',
      -22,
      '빈 DB에서 처음부터 실행되도록 마이그레이션 파일을 정리했습니다.',
      [['STATUS', 'TODO', 'DONE', 11, -22]],
    );
    gw.lastTaskSeq = 24;

    const qa = project(
      'QA',
      '릴리스 품질 점검',
      18,
      [19, 10],
      -14,
      30,
      'MEDIUM',
      '11월 릴리스 전 회귀 테스트와 성능 점검.',
    );
    task(
      qa,
      1,
      '결재 회귀 시나리오 작성',
      'DOING',
      19,
      'HIGH',
      5,
      '상신·승인·반려·회수·재기안 경로를 시나리오로 정리합니다.',
      [['STATUS', 'TODO', 'DOING', 19, -4]],
    );
    task(qa, 2, '월간 근태표 부하 테스트', 'TODO', 10, 'MEDIUM', 12, '50명 × 31일 표 조회 시간을 측정합니다.');
    task(qa, 3, '모바일 화면 점검', 'TODO', 19, 'LOW', 20, '');

    const hr = project('HR', '2026 하반기 공채', 2, [3, 4], -3, 40, 'MEDIUM', '하반기 신입 공채 일정과 면접 운영.');
    task(hr, 1, '채용 공고 문안 확정', 'TODO', 3, 'HIGH', 6, '');
    task(hr, 2, '면접관 일정 조율', 'TODO', 4, 'MEDIUM', 14, '');

    const mig = project(
      'MIG',
      'v1 데이터 이관',
      7,
      [15, 16],
      -60,
      -10,
      'HIGH',
      'v1 연차·근태 데이터를 새 원장 구조로 옮겼습니다.',
    );
    task(mig, 1, '연차 원장 이관 스크립트', 'DONE', 15, 'HIGH', -20, '', [['STATUS', 'TODO', 'DONE', 15, -20]]);
    task(mig, 2, '이관 결과 검증', 'DONE', 16, 'MEDIUM', -12, '', [['STATUS', 'TODO', 'DONE', 16, -12]]);

    [gw, qa, hr, mig].forEach(recalc);
    gw.updatedAt = at(-1, '17:40:00');

    // 처음 데이터의 업무 배정 알림 몇 개 (홈·알림 메뉴에서 보이도록)
    const gw22 = st.tasks.find((t) => t.projectId === gw.id && t.no === 22);
    notifyTask(12, 'TASK_ASSIGNED', gw22, gw, at(-19, '11:20:00'));
    const gw18 = st.tasks.find((t) => t.projectId === gw.id && t.no === 18);
    notifyTask(12, 'TASK_OVERDUE', gw18, gw, at(0, '09:00:00')); // 마감 알림은 담당자에게만 (명세 5장)
  }
  seed();
}
