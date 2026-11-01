/**
 * M2 전자결재 가짜 서버 (backend/md/13-rest-api-v2-m2.md)
 * 결재선 해석 · 상신 · 승인 · 반려 · 회수 · 재기안 · 결재함 · 연차 원장 · 앱 안 알림을 명세대로 흉내 냅니다.
 * 처음 데이터도 같은 처리 함수로 만들어서 원장·알림·결재선이 서로 맞습니다.
 */

import { grantsDue } from './grant';

const FORMS = [
  {
    code: 'LEAVE',
    name: '휴가 신청',
    shortName: '휴가',
    prefix: 'HR',
    ruleName: '휴가 기본',
    description: '연차 · 반차 · 병가 · 경조 휴가',
    options: { dedupeApprover: true, fallbackToUpper: true, skipSelf: true, allowLineEdit: false },
    steps: [
      { lineKind: 'APPROVER', approverType: 'DEPT_HEAD' },
      { lineKind: 'APPROVER', approverType: 'UPPER_DEPT_HEAD' },
      { lineKind: 'REFERENCE', approverType: 'ROLE', roleCode: 'CUSTOM_4' },
    ],
  },
  {
    code: 'OVERTIME',
    name: '특근 신청',
    shortName: '특근',
    prefix: 'OT',
    ruleName: '특근 기본',
    description: '주말 · 공휴일 · 연장 근무',
    options: { dedupeApprover: true, fallbackToUpper: true, skipSelf: true, allowLineEdit: false },
    steps: [{ lineKind: 'APPROVER', approverType: 'DEPT_HEAD' }],
  },
  {
    code: 'GENERAL',
    name: '일반 품의',
    shortName: '품의',
    prefix: 'GN',
    ruleName: '품의 기본',
    description: '구매 · 비용 · 기타 결재',
    options: { dedupeApprover: true, fallbackToUpper: true, skipSelf: true, allowLineEdit: true },
    steps: [
      { lineKind: 'APPROVER', approverType: 'DEPT_HEAD' },
      { lineKind: 'APPROVER', approverType: 'UPPER_DEPT_HEAD' },
      { lineKind: 'APPROVER', approverType: 'ROLE', roleCode: 'CUSTOM_6' },
    ],
  },
];

export const LEAVE_TYPES = {
  ANNUAL: '연차',
  HALF_AM: '오전 반차',
  HALF_PM: '오후 반차',
  SICK: '병가',
  FAMILY: '경조 휴가',
};

const pad = (n) => String(n).padStart(2, '0');
const nowLocal = () => {
  const d = new Date();
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
};
const md = (date) => `${Number(date.slice(5, 7))}/${Number(date.slice(8, 10))}`;

export function installM2({ route, db, fail, created, need, userById, deptById, roleByCode, positionById, page }) {
  const state = {
    forms: FORMS.map((f) => ({ ...f, options: { ...f.options }, steps: f.steps.map((s) => ({ ...s })) })),
    documents: [],
    ledger: [],
    notifications: [],
    seq: { document: 140, ledger: 1, notification: 1 },
    docNo: { HR: 139, OT: 57, GN: 23 },
  };
  db.m2 = state;

  /* ───────── 도우미 ───────── */

  const formOf = (code) =>
    state.forms.find((f) => f.code === code) || fail(404, 'FORM_NOT_FOUND', '결재 양식을 찾을 수 없습니다.');
  const docOf = (id) =>
    state.documents.find((d) => d.id === Number(id)) ||
    fail(404, 'DOCUMENT_NOT_FOUND', '결재 문서를 찾을 수 없습니다.');
  const active = (u) => u && u.status === 'ACTIVE';
  const isChief = (u) => u.roles.includes('CHIEF');

  const person = (u) => {
    const dept = deptById(u.departmentId);
    return { id: u.id, name: u.name, title: positionById(u.positionId)?.name || '', department: dept?.name || '' };
  };

  const ruleLabel = (step) => {
    if (step.approverType === 'DEPT_HEAD') return '기안자의 부서장';
    if (step.approverType === 'UPPER_DEPT_HEAD') return '기안자의 상위 부서장';
    if (step.approverType === 'ROLE') return `'${roleByCode(step.roleCode)?.name || step.roleCode}' 역할`;
    const u = userById(step.userId);
    return u ? `${u.name} ${positionById(u.positionId)?.name || ''}`.trim() : '지정한 사원';
  };

  const headOf = (dept) => {
    const u = dept?.headUserId ? userById(dept.headUserId) : null;
    return active(u) ? u : null;
  };

  /** 부서장을 찾고, 비어 있으면 (옵션에 따라) 위로 올라갑니다. */
  function headFrom(dept, fallbackToUpper) {
    let cur = dept;
    while (cur) {
      const head = headOf(cur);
      if (head)
        return {
          user: head,
          fallback: cur.id === dept.id ? null : `${dept.name} 부서장이 비어 있어 ${cur.name} 부서장에게 넘어갑니다.`,
        };
      if (!fallbackToUpper) return { user: null };
      cur = cur.parentId ? deptById(cur.parentId) : null;
    }
    return { user: null };
  }

  function pickApprover(step, drafter, options) {
    const dept = deptById(drafter.departmentId);
    if (step.approverType === 'DEPT_HEAD') return headFrom(dept, options.fallbackToUpper);
    if (step.approverType === 'UPPER_DEPT_HEAD') {
      const upper = dept?.parentId ? deptById(dept.parentId) : null;
      return upper ? headFrom(upper, options.fallbackToUpper) : { user: null };
    }
    if (step.approverType === 'ROLE') {
      // 여러 명이면 사용자 번호(id) 오름차순 첫 사람 (명세 1.3)
      const u = db.users.filter((x) => x.roles.includes(step.roleCode) && active(x)).sort((a, b) => a.id - b.id)[0];
      return { user: u || null };
    }
    const u = userById(step.userId);
    return { user: active(u) ? u : null };
  }

  const errorNote = (step) => {
    if (step.approverType === 'ROLE') return '이 역할을 가진 재직 사원이 없습니다.';
    if (step.approverType === 'USER') return '지정한 사원이 재직 중이 아닙니다.';
    return '부서장이 지정되지 않아 결재선을 만들 수 없습니다. 관리자에게 문의하세요.';
  };

  /** 명세 1.3 — 규칙(또는 기안자가 바꾼 결재선)을 사람으로 풉니다. */
  function resolveLine(form, drafter, customLine) {
    const rows = [];
    const finalLine = [];
    const references = [];
    let error = null;

    const entries = customLine
      ? customLine.map((l) => ({
          lineKind: l.lineKind || 'APPROVER',
          approverType: 'USER',
          userId: l.userId,
          custom: true,
        }))
      : form.steps;

    entries.forEach((step, index) => {
      const order = index + 1;
      const rule = step.custom ? '기안자 지정' : ruleLabel(step);
      if (step.lineKind === 'REFERENCE') {
        // 참조는 대상이 없어도 오류 없이 건너뜀 (명세 1.3)
        const { user } = pickApprover(step, drafter, form.options);
        const row = {
          kind: 'REFERENCE',
          order,
          rule,
          result: user ? person(user) : null,
          status: 'OK',
          note: null,
          skipReason: null,
        };
        if (!user) {
          row.status = 'SKIPPED';
          row.note = '참조할 사람이 없어 건너뜁니다.';
        } else if (user.id === drafter.id || references.some((r) => r.id === user.id)) {
          row.status = 'SKIPPED';
          row.note = user.id === drafter.id ? '기안자 본인이라 참조에서 뺍니다.' : '이미 참조에 있는 사람입니다.';
        } else {
          references.push(person(user));
        }
        rows.push(row);
        return;
      }
      const { user, fallback } = pickApprover(step, drafter, form.options);
      const row = {
        kind: 'APPROVER',
        order,
        rule,
        result: user ? person(user) : null,
        status: 'OK',
        note: null,
        skipReason: null,
      };
      if (!user) {
        row.status = 'ERROR';
        row.note = errorNote(step);
        error = error || row.note;
      } else if (form.options.skipSelf && user.id === drafter.id) {
        row.status = 'SKIPPED';
        row.skipReason = 'SELF';
        row.note = '기안자 본인이라 이 단계는 건너뜁니다.';
      } else if (form.options.dedupeApprover && finalLine.some((p) => p.id === user.id)) {
        row.status = 'SKIPPED';
        row.skipReason = 'DUPLICATE';
        row.note = '앞 단계와 같은 사람이라 한 번만 결재합니다.';
      } else {
        if (fallback) {
          row.status = 'FALLBACK';
          row.note = fallback;
        }
        finalLine.push(person(user));
      }
      rows.push(row);
    });
    if (!error && !rows.some((r) => r.kind === 'APPROVER')) error = '결재자가 한 명 이상 있어야 합니다.';
    return { rows, finalLine, references, error };
  }

  const previewView = ({ rows, finalLine, references, error }) => ({
    rows: rows.map(({ kind, order, rule, result, status, note }) => ({ kind, order, rule, result, status, note })),
    finalLine,
    references,
    error,
  });

  /* ───────── 연차 ───────── */

  const holidaySet = () => new Set(db.holidays.map((h) => h.date));

  /** 기간의 근무일·휴일 수 (주말과 공휴일 제외) */
  function countDays(startDate, endDate) {
    const holidays = holidaySet();
    let work = 0;
    let off = 0;
    const end = new Date(`${endDate}T00:00:00Z`);
    for (let d = new Date(`${startDate}T00:00:00Z`); d <= end; d.setUTCDate(d.getUTCDate() + 1)) {
      const iso = d.toISOString().slice(0, 10);
      const dow = d.getUTCDay();
      if (dow === 0 || dow === 6 || holidays.has(iso)) off += 1;
      else work += 1;
    }
    return { work, off };
  }

  const ledgerOf = (userId, year) => state.ledger.filter((l) => l.userId === userId && l.year === year);
  const sumDays = (rows) => Math.round(rows.reduce((s, l) => s + l.days, 0) * 10) / 10;

  function balance(userId, year) {
    const rows = ledgerOf(userId, year);
    const by = (type) => sumDays(rows.filter((l) => l.type === type));
    return {
      year,
      granted: by('GRANT'),
      used: Math.abs(by('USE')),
      reserved: Math.abs(by('RESERVE') + by('RELEASE')),
      available: sumDays(rows),
    };
  }

  /** 휴가 일수 미리 계산 (명세 5장) */
  function leaveDays(body) {
    const { leaveType, startDate, endDate } = body;
    if (
      !LEAVE_TYPES[leaveType] ||
      !/^\d{4}-\d{2}-\d{2}$/.test(startDate || '') ||
      !/^\d{4}-\d{2}-\d{2}$/.test(endDate || '')
    ) {
      fail(400, 'INVALID_INPUT_VALUE', '휴가 종류와 기간을 확인해 주세요.');
    }
    if (endDate < startDate) fail(400, 'INVALID_INPUT_VALUE', '종료일이 시작일보다 빠를 수 없습니다.');
    const half = leaveType === 'HALF_AM' || leaveType === 'HALF_PM';
    if (half && startDate !== endDate) fail(400, 'INVALID_INPUT_VALUE', '반차는 하루만 신청할 수 있습니다.');
    const { work, off } = countDays(startDate, endDate);
    let days = 0;
    if (leaveType === 'ANNUAL') days = work;
    else if (half) days = work > 0 ? 0.5 : 0;
    return { days, workDays: work, offDays: off };
  }

  function addLedger(userId, year, date, type, days, memo, documentId = null, extra = {}) {
    state.ledger.push({ id: state.seq.ledger++, userId, year, date, type, days, memo, documentId, ...extra });
  }

  /* ───────── 알림 ───────── */

  // 실제 서버처럼 title에는 문서 제목만 (문장은 화면이 종류에 맞춰 만듦)
  function notify(userId, type, title, docId, at) {
    state.notifications.push({
      id: state.seq.notification++,
      userId,
      type,
      title,
      link: `/approvals/${docId}`,
      read: false,
      createdAt: at,
    });
  }

  /* ───────── 양식별 처리기 (아키텍처 4장의 ApprovalFormHandler 흉내) ───────── */

  const handlers = {
    LEAVE: {
      validateDraft(body) {
        if (!LEAVE_TYPES[body?.leaveType] || !body.startDate || !body.endDate)
          fail(400, 'INVALID_INPUT_VALUE', '휴가 종류와 기간을 입력해 주세요.');
      },
      validate(doc, drafter) {
        if (doc.body.startDate.slice(0, 4) !== doc.body.endDate.slice(0, 4))
          fail(422, 'LEAVE_CROSS_YEAR', '연도가 바뀌는 휴가는 연도별로 나눠 신청해 주세요.');
        const { days, workDays } = leaveDays(doc.body);
        if (workDays === 0) fail(422, 'LEAVE_NO_WORKING_DAY', '선택한 기간이 모두 휴일입니다.');
        const year = Number(doc.body.startDate.slice(0, 4));
        if (days > balance(drafter.id, year).available)
          fail(422, 'LEAVE_BALANCE_EXCEEDED', '사용 가능한 연차보다 많이 신청할 수 없습니다.');
        const mine = doc.body;
        const overlap = state.documents.some(
          (d) =>
            d.id !== doc.id &&
            d.drafterId === drafter.id &&
            d.formCode === 'LEAVE' &&
            ['IN_PROGRESS', 'APPROVED'].includes(d.status) &&
            d.body.startDate <= mine.endDate &&
            mine.startDate <= d.body.endDate &&
            !(
              d.body.startDate === d.body.endDate &&
              mine.startDate === mine.endDate &&
              [d.body.leaveType, mine.leaveType].sort().join() === 'HALF_AM,HALF_PM'
            ),
        );
        if (overlap) fail(409, 'LEAVE_PERIOD_OVERLAP', '같은 기간에 이미 신청한 휴가가 있습니다.');
        doc.usedDays = days;
      },
      memo: (doc) =>
        `${LEAVE_TYPES[doc.body.leaveType]} ${md(doc.body.startDate)}${doc.body.endDate !== doc.body.startDate ? `~${md(doc.body.endDate)}` : ''}`,
      onSubmitted(doc, at) {
        if (doc.usedDays > 0)
          addLedger(
            doc.drafterId,
            Number(doc.body.startDate.slice(0, 4)),
            at.slice(0, 10),
            'RESERVE',
            -doc.usedDays,
            this.memo(doc),
            doc.id,
          );
      },
      onApproved(doc, at) {
        if (doc.usedDays > 0) {
          const year = Number(doc.body.startDate.slice(0, 4));
          addLedger(doc.drafterId, year, at.slice(0, 10), 'USE', -doc.usedDays, this.memo(doc), doc.id);
          addLedger(doc.drafterId, year, at.slice(0, 10), 'RELEASE', doc.usedDays, '예약 해제 (승인)', doc.id);
        }
      },
      onClosed(doc, at, reason) {
        if (doc.usedDays > 0)
          addLedger(
            doc.drafterId,
            Number(doc.body.startDate.slice(0, 4)),
            at.slice(0, 10),
            'RELEASE',
            doc.usedDays,
            `예약 해제 (${reason})`,
            doc.id,
          );
      },
    },
    OVERTIME: {
      validateDraft(body) {
        if (!body?.overtimeDate || !body.startTime || !body.endTime)
          fail(400, 'INVALID_INPUT_VALUE', '근무일과 시간을 입력해 주세요.');
      },
      validate(doc) {
        if (doc.body.endTime <= doc.body.startTime)
          fail(400, 'INVALID_OVERTIME_TIME', '종료 시각은 시작 시각보다 늦어야 합니다.');
      },
      onSubmitted() {},
      onApproved() {},
      onClosed() {},
    },
    GENERAL: {
      validateDraft(body) {
        if (!body?.content?.trim()) fail(400, 'INVALID_INPUT_VALUE', '내용을 입력해 주세요.');
      },
      validate() {},
      onSubmitted() {},
      onApproved() {},
      onClosed() {},
    },
  };

  /* ───────── 문서 처리 ───────── */

  const pendingLine = (doc) => doc.lines.find((l) => l.status === 'PENDING');
  const history = (doc, action, actor, comment, at) =>
    doc.history.push({
      action,
      actor: actor ? { id: actor.id, name: actor.name } : null,
      comment: comment || null,
      at,
    });

  function createDraft(drafter, { formCode, title, body, line }, at) {
    const form = formOf(formCode);
    if (!title?.trim()) fail(400, 'INVALID_INPUT_VALUE', '제목을 입력해 주세요.');
    handlers[form.code].validateDraft(body);
    if (line && !form.options.allowLineEdit) fail(403, 'LINE_EDIT_NOT_ALLOWED', '이 양식은 결재선을 바꿀 수 없습니다.');
    const doc = {
      id: state.seq.document++,
      formCode: form.code,
      title: title.trim(),
      body: { ...body },
      customLine: line ? line.map((l) => ({ ...l })) : null,
      status: 'DRAFT',
      drafterId: drafter.id,
      drafter: person(drafter),
      docNo: null,
      createdAt: at,
      updatedAt: at,
      submittedAt: null,
      completedAt: null,
      version: 0,
      lines: [],
      references: [],
      history: [],
      resubmittedFromId: null,
      usedDays: null,
    };
    state.documents.push(doc);
    return doc;
  }

  function complete(doc, at) {
    doc.status = 'APPROVED';
    doc.completedAt = at;
    handlers[doc.formCode].onApproved(doc, at);
    notify(doc.drafterId, 'APPROVED', doc.title, doc.id, at);
  }

  function submit(doc, drafter, at) {
    if (doc.drafterId !== drafter.id) fail(403, 'ACCESS_DENIED', '접근 권한이 없습니다.');
    if (doc.status !== 'DRAFT')
      fail(409, 'DOCUMENT_STATE_CONFLICT', '이미 처리되었거나 지금 처리할 수 없는 문서입니다. 새로고침해 주세요.');
    const form = formOf(doc.formCode);
    handlers[form.code].validate(doc, drafter);
    const resolved = resolveLine(form, drafter, doc.customLine);
    if (resolved.error)
      fail(422, 'APPROVER_NOT_FOUND', '결재자를 찾을 수 없습니다. 결재 양식이나 부서장 설정을 확인해 주세요.');

    state.docNo[form.prefix] += 1;
    doc.docNo = `${form.prefix}-${at.slice(0, 4)}-${String(state.docNo[form.prefix]).padStart(4, '0')}`;
    doc.drafter = person(drafter); // 상신 순간의 스냅샷
    doc.lines = resolved.rows
      .filter((r) => r.kind === 'APPROVER')
      .map((r) => ({
        order: r.order,
        kind: 'APPROVER',
        approver: r.result,
        source: doc.customLine ? 'DRAFTER' : 'RULE',
        rule: r.rule,
        status: r.status === 'SKIPPED' ? 'SKIPPED' : 'WAITING',
        skipReason: r.skipReason,
        actedAt: null,
        comment: null,
      }));
    doc.references = resolved.references;
    doc.status = 'IN_PROGRESS';
    doc.submittedAt = at;
    doc.updatedAt = at;
    doc.version += 1;

    handlers[form.code].onSubmitted(doc, at);
    history(doc, 'SUBMIT', drafter, null, at);
    history(doc, 'LINE_GENERATED', null, doc.customLine ? '기안자가 지정한 결재선' : `'${form.ruleName}' 규칙`, at);

    const first = doc.lines.find((l) => l.status === 'WAITING');
    if (first) {
      first.status = 'PENDING';
      notify(first.approver.id, 'APPROVAL_REQUESTED', doc.title, doc.id, at);
    } else {
      history(doc, 'AUTO_APPROVE', null, '결재할 사람이 남지 않아 바로 승인되었습니다.', at);
      complete(doc, at);
    }
    doc.references.forEach((r) => notify(r.id, 'REFERENCED', doc.title, doc.id, at));
    return doc;
  }

  function requireTurn(doc, actor) {
    const line = pendingLine(doc);
    if (doc.status !== 'IN_PROGRESS' || !line || line.approver.id !== actor.id) {
      fail(409, 'DOCUMENT_STATE_CONFLICT', '이미 처리되었거나 지금 처리할 수 없는 문서입니다. 새로고침해 주세요.');
    }
    return line;
  }

  function approve(doc, actor, comment, at) {
    const line = requireTurn(doc, actor);
    line.status = 'APPROVED';
    line.actedAt = at;
    line.comment = comment?.trim() || null;
    doc.version += 1;
    doc.updatedAt = at;
    history(doc, 'APPROVE', actor, line.comment, at);
    const next = doc.lines.find((l) => l.status === 'WAITING');
    if (next) {
      next.status = 'PENDING';
      notify(next.approver.id, 'APPROVAL_REQUESTED', doc.title, doc.id, at);
    } else {
      complete(doc, at);
    }
    return doc;
  }

  function reject(doc, actor, comment, at) {
    if (!comment?.trim()) fail(400, 'REJECT_REASON_REQUIRED', '반려 사유를 입력해 주세요.');
    const line = requireTurn(doc, actor);
    line.status = 'REJECTED';
    line.actedAt = at;
    line.comment = comment.trim();
    doc.status = 'REJECTED';
    doc.completedAt = at;
    doc.version += 1;
    doc.updatedAt = at;
    history(doc, 'REJECT', actor, line.comment, at);
    handlers[doc.formCode].onClosed(doc, at, '반려');
    notify(doc.drafterId, 'REJECTED', doc.title, doc.id, at);
    return doc;
  }

  function withdraw(doc, actor, at) {
    if (doc.drafterId !== actor.id) fail(403, 'ACCESS_DENIED', '접근 권한이 없습니다.');
    if (doc.status !== 'IN_PROGRESS')
      fail(409, 'DOCUMENT_STATE_CONFLICT', '이미 처리되었거나 지금 처리할 수 없는 문서입니다. 새로고침해 주세요.');
    if (doc.lines.some((l) => l.status === 'APPROVED'))
      fail(409, 'WITHDRAW_NOT_ALLOWED', '이미 승인한 사람이 있어 회수할 수 없습니다.');
    const current = pendingLine(doc);
    doc.status = 'WITHDRAWN';
    doc.completedAt = at;
    doc.version += 1;
    doc.updatedAt = at;
    history(doc, 'WITHDRAW', actor, null, at);
    handlers[doc.formCode].onClosed(doc, at, '회수');
    if (current) notify(current.approver.id, 'WITHDRAWN', doc.title, doc.id, at);
    return doc;
  }

  function resubmit(doc, actor, at) {
    if (doc.drafterId !== actor.id) fail(403, 'ACCESS_DENIED', '접근 권한이 없습니다.');
    if (!['REJECTED', 'WITHDRAWN'].includes(doc.status))
      fail(409, 'DOCUMENT_STATE_CONFLICT', '반려되거나 회수한 문서만 다시 올릴 수 있습니다.');
    const copy = createDraft(
      actor,
      { formCode: doc.formCode, title: doc.title, body: doc.body, line: doc.customLine },
      at,
    );
    copy.resubmittedFromId = doc.id;
    return copy;
  }

  /* ───────── 응답 모양 ───────── */

  function canRead(doc, user) {
    if (doc.drafterId === user.id) return true;
    if (doc.status === 'DRAFT') return false;
    return (
      isChief(user) || doc.lines.some((l) => l.approver?.id === user.id) || doc.references.some((r) => r.id === user.id)
    );
  }

  function bodyView(doc) {
    if (doc.formCode === 'LEAVE') {
      const handover = doc.body.handoverUserId ? userById(doc.body.handoverUserId) : null;
      const usedDays = doc.usedDays ?? leaveDays(doc.body).days;
      return {
        ...doc.body,
        usedDays,
        handover: handover
          ? { id: handover.id, name: handover.name, title: positionById(handover.positionId)?.name || '' }
          : null,
      };
    }
    return { ...doc.body };
  }

  function detailView(doc, user) {
    const form = formOf(doc.formCode);
    const mine = doc.drafterId === user.id;
    const turn = doc.status === 'IN_PROGRESS' && pendingLine(doc)?.approver.id === user.id;
    return {
      id: doc.id,
      docNo: doc.docNo,
      form: { code: form.code, name: form.name },
      title: doc.title,
      status: doc.status,
      drafter: doc.drafter,
      submittedAt: doc.submittedAt,
      completedAt: doc.completedAt,
      updatedAt: doc.updatedAt,
      body: bodyView(doc),
      line: doc.lines.map(({ order, kind, approver, source, rule, status, skipReason, actedAt, comment }) => ({
        order,
        kind,
        approver,
        source,
        rule,
        status,
        skipReason,
        actedAt,
        comment,
      })),
      customLine: doc.status === 'DRAFT' ? doc.customLine : undefined,
      references: doc.references,
      history: doc.history,
      myActions: {
        canApprove: turn,
        canReject: turn,
        canWithdraw: mine && doc.status === 'IN_PROGRESS' && !doc.lines.some((l) => l.status === 'APPROVED'),
        canResubmit: mine && ['REJECTED', 'WITHDRAWN'].includes(doc.status),
      },
      resubmittedFromId: doc.resubmittedFromId,
    };
  }

  function stepsOf(doc) {
    if (doc.status === 'DRAFT') return [];
    return [
      'DONE',
      ...doc.lines.map((l) => {
        if (l.status === 'APPROVED') return 'DONE';
        if (l.status === 'REJECTED') return 'REJECTED';
        if (l.status === 'SKIPPED') return 'SKIPPED';
        if (l.status === 'PENDING') return doc.status === 'WITHDRAWN' ? 'WITHDRAWN' : 'CURRENT';
        return 'TODO';
      }),
    ];
  }

  function rowView(doc) {
    const form = formOf(doc.formCode);
    const current = doc.status === 'IN_PROGRESS' ? pendingLine(doc) : null;
    return {
      id: doc.id,
      docNo: doc.docNo,
      formCode: form.code,
      formName: form.shortName,
      title: doc.title,
      drafter: { name: doc.drafter.name, title: doc.drafter.title, department: doc.drafter.department },
      submittedAt: doc.submittedAt,
      updatedAt: doc.updatedAt,
      status: doc.status,
      steps: stepsOf(doc),
      currentApprover: current ? { name: current.approver.name, title: current.approver.title } : null,
    };
  }

  const inMyLine = (doc, user) => doc.lines.some((l) => l.approver?.id === user.id && l.status !== 'SKIPPED');
  const BOXES = {
    todo: (d, u) => d.status === 'IN_PROGRESS' && pendingLine(d)?.approver.id === u.id,
    'in-progress': (d, u) =>
      d.status === 'IN_PROGRESS' && d.lines.some((l) => l.approver?.id === u.id && l.status === 'APPROVED'),
    done: (d, u) => ['APPROVED', 'REJECTED'].includes(d.status) && inMyLine(d, u),
    mine: (d, u) => d.drafterId === u.id && d.status !== 'DRAFT',
    drafts: (d, u) => d.drafterId === u.id && d.status === 'DRAFT',
    reference: (d, u) => d.status !== 'DRAFT' && d.references.some((r) => r.id === u.id),
  };

  /* ───────── 처음 데이터 ───────── */

  function seed() {
    // 올해 오늘까지 받았어야 할 부여 (M3 부여 배치와 같은 규칙, 같은 키)
    const today = new Date().toLocaleDateString('sv-SE');
    db.users.forEach((u) => {
      grantsDue(u, today).forEach((g) =>
        addLedger(u.id, Number(g.date.slice(0, 4)), g.date, 'GRANT', g.days, g.memo, null, { grantKey: g.key }),
      );
    });
    const u = (id) => userById(id);
    const run = (drafterId, form, title, body, at, actions = [], line = null) => {
      const doc = createDraft(u(drafterId), { formCode: form, title, body, line }, at);
      if (actions === 'draft') return doc;
      submit(doc, u(drafterId), at);
      actions.forEach(([kind, actorId, when, comment]) => {
        if (kind === 'approve') approve(doc, u(actorId), comment, when);
        if (kind === 'reject') reject(doc, u(actorId), comment, when);
        if (kind === 'withdraw') withdraw(doc, u(actorId), when);
      });
      return doc;
    };

    // 김서연(개발1팀 사원): 지난 휴가 2건 승인, 이번 연차는 박지훈 팀장 차례
    run(
      12,
      'LEAVE',
      '연차 · 3/9~3/10',
      { leaveType: 'ANNUAL', startDate: '2026-03-09', endDate: '2026-03-10', handoverUserId: 10, reason: '개인 사유' },
      '2026-03-02T09:30:00',
      [
        ['approve', 8, '2026-03-02T11:02:00'],
        ['approve', 7, '2026-03-02T15:40:00'],
      ],
    );
    run(
      12,
      'LEAVE',
      '오후 반차 · 6/12',
      { leaveType: 'HALF_PM', startDate: '2026-06-12', endDate: '2026-06-12', reason: '병원 진료' },
      '2026-06-08T10:12:00',
      [
        ['approve', 8, '2026-06-08T13:20:00'],
        ['approve', 7, '2026-06-08T17:05:00'],
      ],
    );
    run(
      9,
      'LEAVE',
      '오후 반차 · 10/20',
      { leaveType: 'HALF_PM', startDate: '2026-10-20', endDate: '2026-10-20', reason: '은행 업무' },
      '2026-10-08T14:00:00',
      [
        ['approve', 8, '2026-10-08T16:30:00'],
        ['approve', 7, '2026-10-09T09:10:00'],
      ],
    );
    run(
      13,
      'LEAVE',
      '연차 · 10/26',
      { leaveType: 'ANNUAL', startDate: '2026-10-26', endDate: '2026-10-26', reason: '이사' },
      '2026-10-09T10:00:00',
      [['reject', 8, '2026-10-09T13:45:00', '프로젝트 마감 주간입니다. 다음 주로 조정해 주세요.']],
    );
    run(
      6,
      'OVERTIME',
      '특근 · 10/10 (토)',
      { overtimeDate: '2026-10-10', startTime: '10:00', endTime: '15:00', reason: '분기 결산 마감' },
      '2026-10-07T17:20:00',
      [['approve', 5, '2026-10-08T09:00:00']],
    );
    run(
      11,
      'GENERAL',
      '개발용 모니터 2대 구매',
      { content: '신규 입사자 개발 환경용 27인치 모니터 2대 구매를 요청합니다.', amount: 640000 },
      '2026-10-13T11:05:00',
      [['approve', 8, '2026-10-13T14:30:00']],
    );
    run(
      12,
      'LEAVE',
      '연차 · 10/19~10/20',
      {
        leaveType: 'ANNUAL',
        startDate: '2026-10-19',
        endDate: '2026-10-20',
        handoverUserId: 10,
        reason: '가족 행사 참석',
      },
      '2026-10-12T09:14:00',
    );
    run(
      10,
      'OVERTIME',
      '특근 · 10/17 (토)',
      { overtimeDate: '2026-10-17', startTime: '10:00', endTime: '16:00', reason: '배포 작업 대응' },
      '2026-10-14T18:02:00',
    );
    run(
      8,
      'LEAVE',
      '연차 · 11/2~11/3',
      { leaveType: 'ANNUAL', startDate: '2026-11-02', endDate: '2026-11-03', handoverUserId: 9, reason: '휴식' },
      '2026-10-15T08:50:00',
    );
    run(
      15,
      'LEAVE',
      '연차 · 10/30',
      { leaveType: 'ANNUAL', startDate: '2026-10-30', endDate: '2026-10-30', reason: '개인 사유' },
      '2026-10-16T09:40:00',
    );
    run(
      12,
      'GENERAL',
      '외부 세미나 참가비',
      { content: '11월 데이터베이스 성능 세미나 참가비 지원을 요청합니다.', amount: 150000 },
      '2026-10-16T17:30:00',
      'draft',
    );
  }
  seed();

  /* ───────── 라우트: 사원 찾기 (인수자·결재자 선택용 최소 정보) ───────── */

  route('GET', '/users/lookup', (ctx) => {
    const q = (ctx.params.q || '').trim();
    const deptId = ctx.params.departmentId ? Number(ctx.params.departmentId) : null;
    const size = Math.min(Number(ctx.params.size) || 20, 100);
    return db.users
      .filter(
        (x) =>
          active(x) &&
          (!deptId || x.departmentId === deptId) &&
          (!q || x.name.startsWith(q) || x.loginId.startsWith(q)),
      )
      .sort((a, b) => a.name.localeCompare(b.name, 'ko'))
      .slice(0, size)
      .map((x) => ({ ...person(x), departmentId: x.departmentId }));
  });

  /* ───────── 라우트: 양식 ───────── */

  route('GET', '/approval-forms', () =>
    state.forms.map((f) => ({
      code: f.code,
      name: f.name,
      shortName: f.shortName,
      prefix: f.prefix,
      description: f.description,
      allowLineEdit: f.options.allowLineEdit,
      approverStepCount: f.steps.filter((s) => s.lineKind === 'APPROVER').length,
      referenceStepCount: f.steps.filter((s) => s.lineKind === 'REFERENCE').length,
    })),
  );

  const formDetail = (f) => ({
    code: f.code,
    name: f.name,
    shortName: f.shortName,
    prefix: f.prefix,
    description: f.description,
    options: { ...f.options },
    steps: f.steps.map((s, i) => ({
      order: i + 1,
      lineKind: s.lineKind,
      approverType: s.approverType,
      roleCode: s.roleCode ?? null,
      userId: s.userId ?? null,
      label: ruleLabel(s),
    })),
  });

  route('GET', '/approval-forms/:code', (ctx) => {
    need(ctx, 'APPROVAL_FORM', 'V');
    return formDetail(formOf(ctx.path.code));
  });

  route('PATCH', '/approval-forms/:code', (ctx) => {
    need(ctx, 'APPROVAL_FORM', 'U');
    const form = formOf(ctx.path.code);
    for (const key of ['dedupeApprover', 'fallbackToUpper', 'skipSelf', 'allowLineEdit']) {
      if (typeof ctx.body[key] === 'boolean') form.options[key] = ctx.body[key];
    }
    return formDetail(form);
  });

  route('PUT', '/approval-forms/:code/steps', (ctx) => {
    need(ctx, 'APPROVAL_FORM', 'U');
    const form = formOf(ctx.path.code);
    const steps = ctx.body;
    if (!Array.isArray(steps) || !steps.some((s) => s.lineKind === 'APPROVER'))
      fail(400, 'INVALID_APPROVAL_STEPS', '결재자 단계가 하나 이상 있어야 합니다.');
    for (const s of steps) {
      if (
        !['APPROVER', 'REFERENCE'].includes(s.lineKind) ||
        !['DEPT_HEAD', 'UPPER_DEPT_HEAD', 'ROLE', 'USER'].includes(s.approverType)
      )
        fail(400, 'INVALID_INPUT_VALUE', '잘못된 요청 값입니다.');
      if (s.approverType === 'ROLE' && !roleByCode(s.roleCode)) fail(404, 'ROLE_NOT_FOUND', '역할을 찾을 수 없습니다.');
      if (s.approverType === 'USER' && !userById(s.userId)) fail(404, 'USER_NOT_FOUND', '사용자를 찾을 수 없습니다.');
    }
    form.steps = steps.map((s) => ({
      lineKind: s.lineKind,
      approverType: s.approverType,
      roleCode: s.approverType === 'ROLE' ? s.roleCode : undefined,
      userId: s.approverType === 'USER' ? Number(s.userId) : undefined,
    }));
    return formDetail(form);
  });

  route('POST', '/approval-forms/:code/line-preview', (ctx) => {
    need(ctx, 'APPROVAL_FORM', 'V');
    const form = formOf(ctx.path.code);
    const drafter = userById(ctx.body.drafterId) || fail(404, 'USER_NOT_FOUND', '사용자를 찾을 수 없습니다.');
    return previewView(resolveLine(form, drafter, null));
  });

  /* ───────── 라우트: 문서 ───────── */

  route('POST', '/approval-documents/line-preview', (ctx) => {
    need(ctx, 'APPROVAL_DRAFT', 'C');
    const form = formOf(ctx.body.formCode);
    if (ctx.body.line && !form.options.allowLineEdit)
      fail(403, 'LINE_EDIT_NOT_ALLOWED', '이 양식은 결재선을 바꿀 수 없습니다.');
    return previewView(resolveLine(form, ctx.user, ctx.body.line || null));
  });

  route('POST', '/approval-documents', (ctx) => {
    need(ctx, 'APPROVAL_DRAFT', 'C');
    const doc = createDraft(ctx.user, ctx.body, nowLocal());
    return created({ id: doc.id, status: doc.status });
  });

  const ownDraft = (ctx) => {
    const doc = docOf(ctx.path.id);
    if (doc.drafterId !== ctx.user.id) fail(403, 'ACCESS_DENIED', '접근 권한이 없습니다.');
    if (doc.status !== 'DRAFT')
      fail(409, 'DOCUMENT_STATE_CONFLICT', '이미 처리되었거나 지금 처리할 수 없는 문서입니다. 새로고침해 주세요.');
    return doc;
  };

  route('PUT', '/approval-documents/:id', (ctx) => {
    const doc = ownDraft(ctx);
    const form = formOf(doc.formCode);
    const { title, body, line } = ctx.body;
    if (!title?.trim()) fail(400, 'INVALID_INPUT_VALUE', '제목을 입력해 주세요.');
    handlers[form.code].validateDraft(body);
    if (line && !form.options.allowLineEdit) fail(403, 'LINE_EDIT_NOT_ALLOWED', '이 양식은 결재선을 바꿀 수 없습니다.');
    Object.assign(doc, {
      title: title.trim(),
      body: { ...body },
      customLine: line ? line.map((l) => ({ ...l })) : null,
      updatedAt: nowLocal(),
    });
    return { id: doc.id, status: doc.status };
  });

  route('DELETE', '/approval-documents/:id', (ctx) => {
    const doc = ownDraft(ctx);
    state.documents = state.documents.filter((d) => d !== doc);
    return null;
  });

  route('GET', '/approval-documents/:id', (ctx) => {
    const doc = docOf(ctx.path.id);
    if (!canRead(doc, ctx.user)) fail(403, 'ACCESS_DENIED', '접근 권한이 없습니다.');
    return detailView(doc, ctx.user);
  });

  route('POST', '/approval-documents/:id/submit', (ctx) =>
    detailView(submit(docOf(ctx.path.id), ctx.user, nowLocal()), ctx.user),
  );
  route('POST', '/approval-documents/:id/approve', (ctx) =>
    detailView(approve(docOf(ctx.path.id), ctx.user, ctx.body.comment, nowLocal()), ctx.user),
  );
  route('POST', '/approval-documents/:id/reject', (ctx) =>
    detailView(reject(docOf(ctx.path.id), ctx.user, ctx.body.comment, nowLocal()), ctx.user),
  );
  route('POST', '/approval-documents/:id/withdraw', (ctx) =>
    detailView(withdraw(docOf(ctx.path.id), ctx.user, nowLocal()), ctx.user),
  );
  route('POST', '/approval-documents/:id/resubmit', (ctx) => {
    const copy = resubmit(docOf(ctx.path.id), ctx.user, nowLocal());
    return created({ id: copy.id, status: copy.status });
  });

  /* ───────── 라우트: 결재함 ───────── */

  route('GET', '/approval-inbox', (ctx) => {
    need(ctx, 'APPROVAL_INBOX', 'V');
    const box = ctx.params.box || 'todo';
    const match = BOXES[box] || fail(400, 'INVALID_INPUT_VALUE', '잘못된 요청 값입니다.');
    const q = (ctx.params.q || '').trim();
    const sortKey = box === 'drafts' ? 'updatedAt' : 'submittedAt';
    const rows = state.documents
      .filter((d) => match(d, ctx.user))
      .filter((d) => !ctx.params.formCode || d.formCode === ctx.params.formCode)
      .filter((d) => !q || d.title.includes(q) || d.docNo?.includes(q))
      .sort((a, b) => (b[sortKey] || '').localeCompare(a[sortKey] || ''))
      .map(rowView);
    return page(rows, ctx.params);
  });

  route('GET', '/approval-inbox/counts', (ctx) => {
    need(ctx, 'APPROVAL_INBOX', 'V');
    return Object.fromEntries(
      Object.entries(BOXES).map(([key, match]) => [key, state.documents.filter((d) => match(d, ctx.user)).length]),
    );
  });

  /* ───────── 라우트: 캘린더 (기존 GET /calendar/events, 내 승인 휴가·특근) ───────── */

  // 실제 서버(CalendarService)의 제목 규칙을 따릅니다: "연차 - {휴가 종류}", "특근 - {사유}"
  const SERVER_LEAVE_NAMES = { ANNUAL: '연차', HALF_AM: '오전반차', HALF_PM: '오후반차', SICK: '병가', FAMILY: '경조' };

  route('GET', '/calendar/events', (ctx) => {
    const year = Number(ctx.params.year);
    const month = Number(ctx.params.month);
    if (!year || !(month >= 1 && month <= 12)) fail(400, 'INVALID_INPUT_VALUE', 'year/month 값이 유효하지 않습니다.');
    const first = `${year}-${pad(month)}-01`;
    const last = `${year}-${pad(month)}-${pad(new Date(year, month, 0).getDate())}`;
    const mine = state.documents.filter((d) => d.drafterId === ctx.user.id && d.status === 'APPROVED');
    const leaves = mine
      .filter((d) => d.formCode === 'LEAVE' && d.body.startDate <= last && d.body.endDate >= first)
      .map((d) => ({
        eventId: d.id,
        title: `연차 - ${SERVER_LEAVE_NAMES[d.body.leaveType] || d.body.leaveType}`,
        start: `${d.body.startDate}T00:00:00`,
        end: `${d.body.endDate}T23:59:59`,
        type: 'LEAVE',
      }));
    const overtimes = mine
      .filter((d) => d.formCode === 'OVERTIME' && d.body.overtimeDate >= first && d.body.overtimeDate <= last)
      .map((d) => ({
        eventId: d.id,
        title: `특근 - ${d.body.reason || '특근 업무'}`,
        start: `${d.body.overtimeDate}T${d.body.startTime}:00`,
        end: `${d.body.overtimeDate}T${d.body.endTime}:00`,
        type: 'OVERTIME',
      }));
    return [...leaves, ...overtimes].sort((a, b) => a.start.localeCompare(b.start));
  });

  /* ───────── 라우트: 연차 ───────── */

  const yearOf = (ctx) => Number(ctx.params.year) || new Date().getFullYear();

  route('GET', '/leave/balance', (ctx) => balance(ctx.user.id, yearOf(ctx)));

  route('GET', '/leave/ledger', (ctx) => {
    const year = yearOf(ctx);
    const rows = ledgerOf(ctx.user.id, year)
      .slice()
      .sort((a, b) => a.date.localeCompare(b.date) || a.id - b.id);
    let running = 0;
    const withBalance = rows.map((l) => {
      running = Math.round((running + l.days) * 10) / 10;
      const doc = l.documentId ? state.documents.find((d) => d.id === l.documentId) : null;
      return {
        id: l.id,
        date: l.date,
        type: l.type,
        days: l.days,
        balance: running,
        memo: l.memo,
        document: doc ? { id: doc.id, docNo: doc.docNo, status: doc.status } : null,
      };
    });
    return withBalance.filter((l) => !ctx.params.type || l.type === ctx.params.type).reverse();
  });

  route('POST', '/leave/days-preview', (ctx) => {
    need(ctx, 'APPROVAL_DRAFT', 'C');
    const { days, workDays, offDays } = leaveDays(ctx.body);
    const available = balance(ctx.user.id, Number(ctx.body.startDate.slice(0, 4))).available;
    const after = Math.round((available - days) * 10) / 10;
    let error = null;
    if (ctx.body.startDate.slice(0, 4) !== ctx.body.endDate.slice(0, 4))
      error = '연도가 바뀌는 휴가는 연도별로 나눠 신청해 주세요.';
    else if (workDays === 0) error = '선택한 기간이 모두 휴일입니다.';
    else if (after < 0) error = '사용 가능한 연차보다 많이 신청할 수 없습니다.';
    return { days, offDays, available, after, error };
  });

  /* ───────── 라우트: 알림 ───────── */

  const myNotifications = (ctx) => state.notifications.filter((n) => n.userId === ctx.user.id);

  route('GET', '/notifications', (ctx) => {
    const unreadOnly = String(ctx.params.unreadOnly) === 'true';
    const rows = myNotifications(ctx)
      .filter((n) => !unreadOnly || !n.read)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id)
      .map(({ id, type, title, link, read, createdAt }) => ({ id, type, title, link, read, createdAt }));
    return page(rows, ctx.params);
  });

  route('GET', '/notifications/unread-count', (ctx) => ({ count: myNotifications(ctx).filter((n) => !n.read).length }));

  route('PATCH', '/notifications/:id/read', (ctx) => {
    const n = state.notifications.find((x) => x.id === Number(ctx.path.id));
    if (!n || n.userId !== ctx.user.id) fail(404, 'RESOURCE_NOT_FOUND', '알림을 찾을 수 없습니다.');
    n.read = true;
    return null;
  });

  route('POST', '/notifications/read-all', (ctx) => {
    myNotifications(ctx).forEach((n) => {
      n.read = true;
    });
    return null;
  });

  /** 다른 단계(M3 근태 정정 등)가 양식을 하나 더 꽂을 때 씁니다. 결재 흐름·결재함·알림은 그대로 공유 */
  function registerForm(def, handler) {
    state.forms.push({ ...def, options: { ...def.options }, steps: def.steps.map((s) => ({ ...s })) });
    handlers[def.code] = handler;
    if (!(def.prefix in state.docNo)) state.docNo[def.prefix] = 0;
  }

  return {
    state,
    registerForm,
    helpers: { person, nowLocal, addLedger, balance, ledgerOf, sumDays, countDays, holidaySet, notify, docOf, formOf },
  };
}
