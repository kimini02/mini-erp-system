/**
 * M3 근태·휴가 가짜 서버 (backend/md/14-rest-api-v2-m3.md)
 * 출퇴근은 서버 시각(이 가짜 서버에선 브라우저 시계)으로만, 판정 기준은 기록에 함께 저장,
 * 결근·퇴근 누락은 '전날 확정' 처리로, 근태 정정은 M2 결재 엔진에 양식을 하나 더 꽂아서 흉내 냅니다.
 */

import { grantsDue } from './grant';

const pad = (n) => String(n).padStart(2, '0');
const todayStr = () => new Date().toLocaleDateString('sv-SE');
const WEEKDAYS = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'];
const toMin = (hhmm) => Number(hhmm.slice(0, 2)) * 60 + Number(hhmm.slice(3, 5));
const hhmm = (dateTime) => (dateTime ? dateTime.slice(11, 16) : null);
const addDays = (date, n) => {
  const d = new Date(`${date}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
};
const daysBetween = (a, b) => Math.round((new Date(`${b}T00:00:00Z`) - new Date(`${a}T00:00:00Z`)) / 86400000);
const monthDates = (month) => {
  const [y, m] = month.split('-').map(Number);
  const last = new Date(Date.UTC(y, m, 0)).getUTCDate();
  return Array.from({ length: last }, (_, i) => `${month}-${pad(i + 1)}`);
};
/** (사원, 날짜)마다 늘 같은 0~99 값 — 처음 데이터를 매번 같게 만들기 위해 */
const hash = (userId, date) => {
  let h = userId * 2654435761;
  for (const c of date) h = Math.imul(h ^ c.charCodeAt(0), 16777619);
  return Math.abs(h) % 100;
};

const CODE_LABELS = {
  ON_TIME: '정상',
  LATE: '지각',
  EARLY: '조퇴',
  MISSING: '퇴근 누락',
  ABSENT: '결근',
  LEAVE: '휴가',
  HALF_AM: '오전 반차',
  HALF_PM: '오후 반차',
  HOLIDAY: '',
  HOLIDAY_WORK: '휴일 근무',
  NONE: '',
  FUTURE: '',
};

export function installM3({
  route,
  db,
  fail,
  need,
  mergedPermissions,
  userById,
  deptById,
  positionById,
  subtreeIds,
  page,
  m2,
}) {
  const { state: m2state, registerForm, helpers } = m2;
  const { person, nowLocal, addLedger, balance, ledgerOf, sumDays, holidaySet } = helpers;
  const st = { records: new Map(), runs: [], grantKeys: new Set(), seq: { run: 1 } };
  db.m3 = st;
  const keyOf = (userId, date) => `${userId}|${date}`;
  const getRec = (userId, date) => st.records.get(keyOf(userId, date));

  /* ───────── 기준·근무일·휴가 ───────── */

  const holidayName = (date) => db.holidays.find((h) => h.date === date)?.name || null;
  const isWorkday = (date) => {
    const dow = new Date(`${date}T00:00:00Z`).getUTCDay();
    return dow !== 0 && dow !== 6 && !holidaySet().has(date);
  };

  function currentSchedule(user) {
    const dept = deptById(user.departmentId);
    const s = dept?.schedule || db.defaultSchedule;
    return {
      start: s.startTime,
      end: s.endTime,
      grace: s.lateGraceMin,
      source: dept?.schedule ? 'DEPARTMENT' : 'COMPANY',
      departmentName: dept?.name || '',
    };
  }
  const halfBoundary = (sched) => {
    const m = toMin(sched.start) + 300;
    return `${pad(Math.floor(m / 60))}:${pad(m % 60)}`;
  };
  const scheduleView = (sched) => ({
    startTime: sched.start,
    endTime: sched.end,
    lateGraceMin: sched.grace,
    halfBoundary: halfBoundary(sched),
    ...(sched.source ? { source: sched.source, departmentName: sched.departmentName } : {}),
  });

  /** 그날 승인된 휴가 (M2 결재 문서에서) */
  function approvedLeave(userId, date) {
    const doc = m2state.documents.find(
      (d) =>
        d.drafterId === userId &&
        d.formCode === 'LEAVE' &&
        d.status === 'APPROVED' &&
        d.body.startDate <= date &&
        date <= d.body.endDate,
    );
    if (!doc) return null;
    const kind = doc.body.leaveType === 'HALF_AM' || doc.body.leaveType === 'HALF_PM' ? doc.body.leaveType : 'LEAVE';
    return { type: doc.body.leaveType, kind, documentId: doc.id, docNo: doc.docNo };
  }

  const dayTypeFor = (user, date) => (!isWorkday(date) ? 'HOLIDAY' : approvedLeave(user.id, date)?.kind || 'WORKDAY');

  /** 그날 재직자: 입사일(없으면 2026-01-01) ≤ 날짜 < 퇴사일 */
  const employed = (user, date) =>
    date >= (user.hireDate || '2026-01-01') && (!user.retiredDate || date < user.retiredDate);

  /* ───────── 판정 (명세 1장) ───────── */

  const minutesOf = (rec, dateTime) =>
    daysBetween(rec.workDate, dateTime.slice(0, 10)) * 1440 + toMin(dateTime.slice(11, 16));

  function judge(rec) {
    if (rec.dayType === 'HOLIDAY' || rec.dayType === 'LEAVE') {
      rec.inResult = rec.clockInAt ? 'NONE' : null;
      rec.outResult = rec.clockOutAt ? 'NONE' : null;
      return rec;
    }
    const boundary = toMin(halfBoundary(rec.sched));
    const inLimit = (rec.dayType === 'HALF_AM' ? boundary : toMin(rec.sched.start)) + rec.sched.grace;
    const outLimit = rec.dayType === 'HALF_PM' ? boundary : toMin(rec.sched.end);
    rec.inResult = rec.clockInAt ? (minutesOf(rec, rec.clockInAt) <= inLimit ? 'ON_TIME' : 'LATE') : null;
    if (rec.clockOutAt) rec.outResult = minutesOf(rec, rec.clockOutAt) >= outLimit ? 'ON_TIME' : 'EARLY';
    else if (rec.outResult !== 'MISSING') rec.outResult = null;
    return rec;
  }

  function newRecord(user, date, extra = {}) {
    const rec = {
      userId: user.id,
      workDate: date,
      sched: currentSchedule(user),
      dayType: dayTypeFor(user, date),
      clockInAt: null,
      clockOutAt: null,
      inResult: null,
      outResult: null,
      absent: false,
      judgedAt: null,
      fixDocumentId: null,
      fixDocNo: null,
      ...extra,
    };
    st.records.set(keyOf(user.id, date), rec);
    return rec;
  }

  function cellCode(user, date, rec, today = todayStr()) {
    if (!employed(user, date)) return 'NONE';
    if (rec) {
      if (rec.absent) return 'ABSENT';
      if (rec.dayType === 'LEAVE') return 'LEAVE';
      if (rec.outResult === 'MISSING') return 'MISSING';
      if (rec.inResult === 'LATE') return 'LATE';
      if (rec.outResult === 'EARLY') return 'EARLY';
      if (rec.dayType === 'HALF_AM' || rec.dayType === 'HALF_PM') return rec.dayType;
      if (rec.dayType === 'HOLIDAY') return rec.clockInAt ? 'HOLIDAY_WORK' : 'HOLIDAY';
      if (rec.clockInAt) return 'ON_TIME';
    }
    if (!isWorkday(date)) return 'HOLIDAY';
    if (date > today) return 'FUTURE';
    return 'NONE';
  }

  function reasonOf(user, date, rec) {
    if (!rec) return isWorkday(date) ? '아직 기록이 없습니다.' : '주말·공휴일입니다.';
    const s = rec.sched;
    if (rec.absent) return '근무일에 출근 기록이 없고 승인된 휴가도 없어 결근으로 확정했습니다.';
    if (rec.dayType === 'LEAVE') return '승인된 종일 휴가입니다.';
    const parts = [];
    if (rec.clockInAt) {
      const base = rec.dayType === 'HALF_AM' ? halfBoundary(s) : s.start;
      parts.push(`출근 ${hhmm(rec.clockInAt)} ${rec.inResult === 'LATE' ? '>' : '≤'} 기준 ${base} + 유예 ${s.grace}분`);
    }
    if (rec.outResult === 'MISSING') parts.push('퇴근 기록이 없어 다음 날 퇴근 누락으로 확정');
    else if (rec.clockOutAt) {
      const base = rec.dayType === 'HALF_PM' ? halfBoundary(s) : s.end;
      parts.push(`퇴근 ${hhmm(rec.clockOutAt)} ${rec.outResult === 'EARLY' ? '<' : '≥'} 기준 ${base}`);
    }
    if (rec.dayType === 'HOLIDAY') parts.push('주말·공휴일 근무라 지각·조퇴를 판정하지 않습니다');
    return parts.join(' · ') || '판정할 기록이 없습니다.';
  }

  /* ───────── 전날 확정 (결근 배치, 명세 4.2) ───────── */

  function finalize(date, at = nowLocal()) {
    const workday = isWorkday(date);
    let processed = 0;
    for (const user of db.users) {
      // 대상(명세 4.2): 그날 재직 · 휴직 아님 · 퇴사일 없는 퇴사자 아님 · 계정 활성화함. 기록이 이미 있으면 다시 판정만
      const target =
        employed(user, date) &&
        user.status !== 'ON_LEAVE' &&
        !(user.status === 'RETIRED' && !user.retiredDate) &&
        !user.invitationPending;
      let rec = getRec(user.id, date);
      if (!target && !rec) continue;
      if (!rec && workday) {
        const leave = approvedLeave(user.id, date);
        rec = newRecord(user, date, { absent: !leave || leave.kind !== 'LEAVE' });
        processed += 1;
      }
      if (!rec) continue;
      if (!rec.fixDocumentId) rec.dayType = dayTypeFor(user, date);
      if (rec.clockInAt && !rec.clockOutAt) rec.outResult = 'MISSING';
      if (rec.clockInAt || rec.dayType === 'LEAVE') rec.absent = false;
      judge(rec);
      rec.judgedAt = at;
    }
    return processed;
  }

  function recordRun(jobName, targetDate, processedCount, skippedCount = 0, message = null, at = nowLocal()) {
    const run = {
      id: st.seq.run++,
      jobName,
      targetDate,
      status: 'SUCCESS',
      processedCount,
      skippedCount,
      message,
      startedAt: at,
      finishedAt: at,
    };
    st.runs.push(run);
    return run;
  }

  /* ───────── 연차 자동 부여 (명세 4.1) ───────── */

  function runGrant(date, at = nowLocal()) {
    let processed = 0;
    const skipped = [];
    const legacy = [];
    const year = Number(date.slice(0, 4));
    for (const user of db.users) {
      if (!['ACTIVE', 'ON_LEAVE'].includes(user.status)) continue;
      if (!user.hireDate) {
        skipped.push(user.name);
        continue;
      }
      // 그해 키 없는 옛 부여(v1 이관 등)가 있으면 그해 몫으로 인정하고 건너뜀 (명세 4.1)
      if (m2state.ledger.some((l) => l.userId === user.id && l.year === year && l.type === 'GRANT' && !l.grantKey)) {
        legacy.push(user.name);
        continue;
      }
      // 실행 시점까지 받아야 할 부여를 모두 계산하고, 이미 받은 키는 건너뜀 (grant_key 유니크)
      for (const g of grantsDue(user, date)) {
        if (st.grantKeys.has(g.key)) continue;
        st.grantKeys.add(g.key);
        addLedger(user.id, Number(g.date.slice(0, 4)), g.date, 'GRANT', g.days, g.memo, null, { grantKey: g.key });
        processed += 1;
      }
    }
    const message =
      [skipped.length && `입사일 없음: ${skipped.join(', ')}`, legacy.length && `옛 부여 있음: ${legacy.join(', ')}`]
        .filter(Boolean)
        .join(' · ') || null;
    return recordRun('LEAVE_GRANT', date, processed, skipped.length + legacy.length, message, at);
  }

  /* ───────── 처음 데이터 ───────── */

  function seed() {
    const today = todayStr();
    const nowMin = new Date().getHours() * 60 + new Date().getMinutes();
    const start = '2026-09-01';
    for (let date = start; date <= today; date = addDays(date, 1)) {
      for (const user of db.users) {
        if (!employed(user, date) || user.status === 'ON_LEAVE' || user.invitationPending) continue;
        const dayType = dayTypeFor(user, date);
        if (dayType === 'LEAVE') continue;
        const h = hash(user.id, date);
        if (dayType === 'HOLIDAY' && h > 3) continue; // 가끔 휴일 근무
        if (dayType !== 'HOLIDAY' && h >= 96) continue; // 결근 (전날 확정에서 결근 행)
        const sched = currentSchedule(user);
        const inBase = toMin(dayType === 'HALF_AM' ? halfBoundary(sched) : sched.start);
        const outBase = toMin(dayType === 'HALF_PM' ? halfBoundary(sched) : sched.end);
        const inMin = h >= 80 && h < 88 ? inBase + sched.grace + 2 + (h % 7) * 4 : inBase - 20 + (h % 25);
        const isToday = date === today;
        if (isToday && ([1, 8, 12].includes(user.id) || nowMin < inMin)) continue; // 데모 계정은 오늘 출근 전
        const at = (m) => `${date}T${pad(Math.floor(m / 60))}:${pad(m % 60)}:${pad(h % 60)}`;
        const rec = newRecord(user, date, { clockInAt: at(inMin) });
        if (!isToday && !(h >= 92 && h < 96)) {
          const outMin = h >= 88 && h < 92 ? outBase - 15 - (h % 4) * 12 : outBase + (h % 40);
          rec.clockOutAt = at(outMin);
        }
        judge(rec);
        rec.judgedAt = rec.clockInAt;
      }
    }
    // 오늘 전까지는 전날 확정까지 끝난 상태
    for (let date = start; date < today; date = addDays(date, 1)) finalize(date, `${addDays(date, 1)}T00:10:02`);
    for (let i = 3; i >= 1; i -= 1)
      recordRun('ATTENDANCE_FINALIZE', addDays(today, -i), 0, 0, null, `${addDays(today, -i + 1)}T00:10:02`);
    // 올해 부여는 M2 처음 데이터가 같은 규칙으로 이미 넣었으므로 그 키를 이어받음
    m2state.ledger.forEach((l) => l.grantKey && st.grantKeys.add(l.grantKey));
    runGrant(today, `${today}T00:30:01`);
  }
  seed();

  /* ───────── 근태 정정 양식 (명세 5장) ───────── */

  registerForm(
    {
      code: 'ATTENDANCE_FIX',
      name: '근태 정정',
      shortName: '근태',
      prefix: 'AT',
      ruleName: '근태 정정 기본',
      description: '지난 출퇴근 기록 정정',
      options: { dedupeApprover: true, fallbackToUpper: true, skipSelf: true, allowLineEdit: false },
      steps: [{ lineKind: 'APPROVER', approverType: 'DEPT_HEAD' }],
    },
    {
      validateDraft(body) {
        if (
          !/^\d{4}-\d{2}-\d{2}$/.test(body?.workDate || '') ||
          (!body.clockIn && !body.clockOut) ||
          !body.reason?.trim()
        ) {
          fail(400, 'INVALID_INPUT_VALUE', '정정할 날짜·시각과 사유를 입력해 주세요.');
        }
        if (body.clockIn && body.clockOut && body.clockOut <= body.clockIn)
          fail(400, 'INVALID_ATTENDANCE_TIME', '퇴근 시각은 출근 시각보다 늦어야 합니다.');
      },
      validate(doc, drafter) {
        const date = doc.body.workDate;
        const today = todayStr();
        if (date >= today || daysBetween(date, today) > 30)
          fail(422, 'ATTENDANCE_FIX_OUT_OF_RANGE', '최근 30일 안의 지난 날짜만 정정할 수 있습니다.');
        const rec = getRec(drafter.id, date);
        if (!isWorkday(date) && !rec)
          fail(422, 'ATTENDANCE_FIX_OUT_OF_RANGE', '근무일이거나 기록이 있는 날만 정정할 수 있습니다.');
        const duplicate = m2state.documents.some(
          (d) =>
            d.id !== doc.id &&
            d.formCode === 'ATTENDANCE_FIX' &&
            d.drafterId === drafter.id &&
            d.status === 'IN_PROGRESS' &&
            d.body.workDate === date,
        );
        if (duplicate) fail(409, 'ATTENDANCE_FIX_DUPLICATE', '같은 날짜로 진행 중인 정정 문서가 있습니다.');
        // 지금 기록과 합친 결과로 검사 (승인 때 실패하지 않게 상신 때 막음)
        const inAt = doc.body.clockIn ? `${date}T${doc.body.clockIn}` : rec?.clockInAt?.slice(0, 16);
        const outAt = doc.body.clockOut ? `${date}T${doc.body.clockOut}` : rec?.clockOutAt?.slice(0, 16);
        if (!inAt)
          fail(422, 'ATTENDANCE_FIX_CLOCK_IN_REQUIRED', '출근 기록이 없는 날은 출근 시각을 함께 정정해야 합니다.');
        if (outAt && outAt <= inAt) fail(400, 'INVALID_ATTENDANCE_TIME', '퇴근 시각은 출근 시각보다 늦어야 합니다.');
        doc.body.before = {
          clockIn: hhmm(rec?.clockInAt),
          clockOut: hhmm(rec?.clockOutAt),
          code: cellCode(drafter, date, rec),
        };
      },
      onSubmitted() {},
      onApproved(doc, at) {
        const user = userById(doc.drafterId);
        const { workDate, clockIn, clockOut } = doc.body;
        const rec = getRec(user.id, workDate) || newRecord(user, workDate);
        if (clockIn) rec.clockInAt = `${workDate}T${clockIn}:00`;
        if (clockOut) rec.clockOutAt = `${workDate}T${clockOut}:00`;
        rec.absent = false;
        if (!rec.clockOutAt && workDate < todayStr()) rec.outResult = 'MISSING';
        else rec.outResult = null;
        judge(rec);
        rec.fixDocumentId = doc.id;
        rec.fixDocNo = doc.docNo;
        rec.judgedAt = at;
      },
      onClosed() {},
    },
  );

  /* ───────── 권한·범위 도우미 ───────── */

  function scopeFilter(ctx, menu, action = 'V') {
    const p = need(ctx, menu, action);
    if (p.scope === 'ALL') return () => true;
    if (p.scope === 'DEPT_TREE') {
      const ids = new Set(subtreeIds(ctx.user.departmentId));
      return (u) => ids.has(u.departmentId);
    }
    if (p.scope === 'DEPT') return (u) => u.departmentId === ctx.user.departmentId;
    return (u) => u.id === ctx.user.id;
  }
  const requireAllScope = (ctx, menu, action) => {
    const p = need(ctx, menu, action);
    if (p.scope !== 'ALL') fail(403, 'ACCESS_DENIED', '접근 권한이 없습니다.');
  };

  /** 부서 필터 (범위 밖 부서면 403, 명세 3.1) */
  function targetUsers(ctx, menu, params) {
    const inScope = scopeFilter(ctx, menu);
    let deptIds = null;
    if (params.departmentId) {
      const dept = deptById(params.departmentId) || fail(404, 'DEPARTMENT_NOT_FOUND', '부서를 찾을 수 없습니다.');
      const probe = { id: -1, departmentId: dept.id };
      if (!inScope(probe) && mergedPermissions(ctx.user)[menu].scope !== 'SELF')
        fail(403, 'ACCESS_DENIED', '접근 권한이 없습니다.');
      deptIds = new Set(String(params.includeSubDepartments) === 'false' ? [dept.id] : subtreeIds(dept.id));
    }
    return db.users.filter((u) => inScope(u) && (!deptIds || deptIds.has(u.departmentId)));
  }

  const sortUsers = (users) =>
    users.slice().sort((a, b) => {
      const da = deptById(a.departmentId);
      const dbb = deptById(b.departmentId);
      return (
        (da?.id ?? 0) - (dbb?.id ?? 0) ||
        (positionById(b.positionId)?.rankOrder ?? 0) - (positionById(a.positionId)?.rankOrder ?? 0) ||
        a.name.localeCompare(b.name, 'ko')
      );
    });

  /* ───────── 라우트: 내 근태 (명세 2장) ───────── */

  function todayView(user) {
    const date = todayStr();
    const rec = getRec(user.id, date);
    const leave = approvedLeave(user.id, date);
    const dayType = rec?.dayType || dayTypeFor(user, date);
    const sched = rec?.sched || currentSchedule(user);
    const now = new Date();
    const nowMin = now.getHours() * 60 + now.getMinutes();
    const outLimit = toMin(dayType === 'HALF_PM' ? halfBoundary(sched) : sched.end);
    const month = date.slice(0, 7);
    return {
      workDate: date,
      dayType,
      holidayName: holidayName(date),
      schedule: scheduleView(rec ? { ...sched, source: sched.source, departmentName: sched.departmentName } : sched),
      clockInAt: rec?.clockInAt || null,
      clockOutAt: rec?.clockOutAt || null,
      inResult: rec?.inResult || null,
      outResult: rec?.outResult || null,
      leave: leave ? { type: leave.type, documentId: leave.documentId, docNo: leave.docNo } : null,
      canClockIn: !rec?.clockInAt && dayType !== 'LEAVE',
      canClockOut: Boolean(rec?.clockInAt),
      earlyIfNow: Boolean(rec?.clockInAt) && ['WORKDAY', 'HALF_AM', 'HALF_PM'].includes(dayType) && nowMin < outLimit,
      monthLateCount: [...st.records.values()].filter(
        (r) => r.userId === user.id && r.workDate.startsWith(month) && r.inResult === 'LATE',
      ).length,
      monthAbsentCount: [...st.records.values()].filter(
        (r) => r.userId === user.id && r.workDate.startsWith(month) && r.absent,
      ).length,
    };
  }

  route('GET', '/attendance/today', (ctx) => {
    need(ctx, 'MY_ATTENDANCE', 'V');
    return todayView(ctx.user);
  });

  route('POST', '/attendance/clock-in', (ctx) => {
    need(ctx, 'MY_ATTENDANCE', 'C');
    const date = todayStr();
    const existing = getRec(ctx.user.id, date);
    if (existing?.clockInAt) fail(409, 'ALREADY_CLOCKED_IN', '오늘은 이미 출근했습니다.');
    if (dayTypeFor(ctx.user, date) === 'LEAVE')
      fail(409, 'ATTENDANCE_ON_LEAVE', '오늘은 휴가입니다. 휴가를 회수한 뒤 출근하세요.');
    const rec = existing || newRecord(ctx.user, date);
    rec.clockInAt = nowLocal();
    judge(rec);
    rec.judgedAt = rec.clockInAt;
    return todayView(ctx.user);
  });

  route('POST', '/attendance/clock-out', (ctx) => {
    need(ctx, 'MY_ATTENDANCE', 'C');
    const rec = getRec(ctx.user.id, todayStr());
    if (!rec?.clockInAt) fail(409, 'NOT_CLOCKED_IN', '출근 기록이 없습니다. 출근부터 해 주세요.');
    rec.clockOutAt = nowLocal();
    judge(rec);
    rec.judgedAt = rec.clockOutAt;
    return todayView(ctx.user);
  });

  const dayRecordView = (date, rec, user) => ({
    date,
    dayType: rec?.dayType || dayTypeFor(user, date),
    holidayName: holidayName(date),
    clockInAt: rec?.clockInAt || null,
    clockOutAt: rec?.clockOutAt || null,
    inResult: rec?.inResult || null,
    outResult: rec?.outResult || null,
    absent: Boolean(rec?.absent),
    fixDocument: rec?.fixDocumentId ? { id: rec.fixDocumentId, docNo: rec.fixDocNo } : null,
  });

  function summaryOf(user, dates) {
    const today = todayStr();
    const recs = dates.map((d) => getRec(user.id, d)).filter(Boolean);
    return {
      workDays: dates.filter((d) => isWorkday(d) && employed(user, d)).length,
      present: recs.filter((r) => r.clockInAt).length,
      late: recs.filter((r) => r.inResult === 'LATE').length,
      early: recs.filter((r) => r.outResult === 'EARLY').length,
      absent: recs.filter((r) => r.absent).length,
      missing: recs.filter((r) => r.outResult === 'MISSING').length,
      leaveDays: recs
        .filter((r) => r.workDate <= today)
        .reduce((s, r) => s + (r.dayType === 'LEAVE' ? 1 : r.dayType.startsWith('HALF') ? 0.5 : 0), 0),
    };
  }

  route('GET', '/attendance/me', (ctx) => {
    need(ctx, 'MY_ATTENDANCE', 'V');
    const month = /^\d{4}-\d{2}$/.test(ctx.params.month || '') ? ctx.params.month : todayStr().slice(0, 7);
    const dates = monthDates(month);
    return {
      month,
      summary: summaryOf(ctx.user, dates),
      days: dates.map((d) => dayRecordView(d, getRec(ctx.user.id, d), ctx.user)),
    };
  });

  /* ───────── 라우트: 월간 근태표 (명세 3장) ───────── */

  function monthlyRows(ctx) {
    const month = /^\d{4}-\d{2}$/.test(ctx.params.month || '') ? ctx.params.month : todayStr().slice(0, 7);
    const dates = monthDates(month);
    const first = dates[0];
    const last = dates[dates.length - 1];
    const users = sortUsers(
      targetUsers(ctx, 'ATTENDANCE_MONTHLY', ctx.params).filter((u) => {
        if ((u.hireDate || '2026-01-01') > last) return false;
        if (u.status !== 'RETIRED') return true;
        if (u.retiredDate) return u.retiredDate >= first;
        return dates.some((d) => getRec(u.id, d));
      }),
    );
    const rows = users.map((u) => {
      const recs = dates.map((d) => getRec(u.id, d));
      const s = summaryOf(u, dates);
      return {
        user: { id: u.id, ...person(u) },
        cells: dates.map((d, i) => ({ date: d, code: cellCode(u, d, recs[i]) })),
        totals: { late: s.late, early: s.early, absent: s.absent, missing: s.missing, leaveDays: s.leaveDays },
      };
    });
    return { month, dates, rows };
  }

  route('GET', '/attendance/monthly', (ctx) => {
    const { month, dates, rows } = monthlyRows(ctx);
    return {
      month,
      days: dates.map((d) => ({
        date: d,
        weekday: WEEKDAYS[new Date(`${d}T00:00:00Z`).getUTCDay()],
        dayType: isWorkday(d) ? 'WORKDAY' : 'HOLIDAY',
        holidayName: holidayName(d),
      })),
      rows,
      legend: Object.keys(CODE_LABELS),
    };
  });

  route('GET', '/attendance/monthly/export', (ctx) => {
    const { month, dates, rows } = monthlyRows(ctx);
    const header = [
      '사원',
      '직급',
      '부서',
      ...dates.map((d) => String(Number(d.slice(8)))),
      '지각',
      '조퇴',
      '결근',
      '퇴근 누락',
      '휴가(일)',
    ];
    const lines = rows.map((r) => [
      r.user.name,
      r.user.title,
      r.user.department,
      ...r.cells.map((c) => CODE_LABELS[c.code]),
      r.totals.late,
      r.totals.early,
      r.totals.absent,
      r.totals.missing,
      r.totals.leaveDays,
    ]);
    const csv = `\uFEFF${[header, ...lines].map((row) => row.map((v) => `"${String(v ?? '').replaceAll('"', '""')}"`).join(',')).join('\r\n')}`;
    return { __raw: csv, contentType: 'text/csv; charset=utf-8', filename: `attendance-${month}.csv` };
  });

  route('GET', '/attendance/records/:userId/:date', (ctx) => {
    const user = userById(ctx.path.userId) || fail(404, 'USER_NOT_FOUND', '사용자를 찾을 수 없습니다.');
    if (!scopeFilter(ctx, 'ATTENDANCE_MONTHLY')(user)) fail(403, 'ACCESS_DENIED', '접근 권한이 없습니다.');
    const { date } = ctx.path;
    const rec = getRec(user.id, date);
    const leave = approvedLeave(user.id, date);
    return {
      user: { id: user.id, name: user.name },
      date,
      code: cellCode(user, date, rec),
      clockInAt: rec?.clockInAt || null,
      clockOutAt: rec?.clockOutAt || null,
      schedule: scheduleView(rec?.sched || currentSchedule(user)),
      reason: reasonOf(user, date, rec),
      leaveDocument: leave ? { id: leave.documentId, docNo: leave.docNo } : null,
      fixDocument: rec?.fixDocumentId ? { id: rec.fixDocumentId, docNo: rec.fixDocNo } : null,
      judgedAt: rec?.judgedAt || null,
    };
  });

  route('POST', '/attendance/rejudge', (ctx) => {
    requireAllScope(ctx, 'ATTENDANCE_MONTHLY', 'U');
    const date = ctx.body.date;
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date || '') || date >= todayStr())
      fail(400, 'INVALID_INPUT_VALUE', '오늘 이전 날짜를 골라 주세요.');
    const processed = finalize(date);
    return recordRun('ATTENDANCE_REJUDGE', date, processed);
  });

  /* ───────── 라우트: 연차 관리 (명세 4.4) ───────── */

  function balanceRow(u, year) {
    const rows = ledgerOf(u.id, year);
    const b = balance(u.id, year);
    return {
      user: { id: u.id, ...person(u) },
      hireDate: u.hireDate || null,
      hireDateMissing: !u.hireDate,
      granted: b.granted,
      used: b.used,
      reserved: b.reserved,
      adjusted: sumDays(rows.filter((l) => l.type === 'ADJUST')),
      available: b.available,
    };
  }

  route('GET', '/leave/admin/balances', (ctx) => {
    const year = Number(ctx.params.year) || new Date().getFullYear();
    const q = (ctx.params.q || '').trim();
    const users = sortUsers(
      targetUsers(ctx, 'LEAVE_ADMIN', ctx.params).filter(
        (u) => u.status !== 'RETIRED' && (!q || u.name.startsWith(q) || u.loginId.startsWith(q)),
      ),
    );
    return page(
      users.map((u) => balanceRow(u, year)),
      ctx.params,
    );
  });

  route('GET', '/leave/admin/ledger/:userId', (ctx) => {
    const user = userById(ctx.path.userId) || fail(404, 'USER_NOT_FOUND', '사용자를 찾을 수 없습니다.');
    if (!scopeFilter(ctx, 'LEAVE_ADMIN')(user)) fail(403, 'ACCESS_DENIED', '접근 권한이 없습니다.');
    const year = Number(ctx.params.year) || new Date().getFullYear();
    let running = 0;
    return ledgerOf(user.id, year)
      .slice()
      .sort((a, b) => a.date.localeCompare(b.date) || a.id - b.id)
      .map((l) => {
        running = Math.round((running + l.days) * 10) / 10;
        const doc = l.documentId ? m2state.documents.find((d) => d.id === l.documentId) : null;
        return {
          id: l.id,
          date: l.date,
          type: l.type,
          days: l.days,
          balance: running,
          memo: l.memo,
          document: doc ? { id: doc.id, docNo: doc.docNo, status: doc.status } : null,
        };
      })
      .reverse();
  });

  route('POST', '/leave/admin/adjustments', (ctx) => {
    const inScope = scopeFilter(ctx, 'LEAVE_ADMIN', 'C');
    const { userId, year, days, memo } = ctx.body;
    const user = userById(userId) || fail(404, 'USER_NOT_FOUND', '사용자를 찾을 수 없습니다.');
    if (!inScope(user)) fail(403, 'ACCESS_DENIED', '접근 권한이 없습니다.');
    const n = Number(days);
    if (!n || Math.round(n * 2) !== n * 2 || !memo?.trim())
      fail(400, 'INVALID_INPUT_VALUE', '조정 일수(0.5일 단위)와 사유를 입력해 주세요.');
    const y = Number(year) || new Date().getFullYear();
    if (balance(user.id, y).available + n < 0)
      fail(422, 'LEAVE_BALANCE_NEGATIVE', '조정하면 사용 가능 연차가 0보다 작아집니다.');
    addLedger(user.id, y, todayStr(), 'ADJUST', n, memo.trim(), null, { createdBy: ctx.user.id });
    return balanceRow(user, y);
  });

  route('GET', '/leave/admin/grant-runs', (ctx) => {
    requireAllScope(ctx, 'LEAVE_ADMIN', 'U');
    const limit = Number(ctx.params.limit) || 10;
    return st.runs
      .filter((r) => r.jobName === 'LEAVE_GRANT')
      .slice()
      .reverse()
      .slice(0, limit);
  });

  route('POST', '/leave/admin/grant-runs', (ctx) => {
    requireAllScope(ctx, 'LEAVE_ADMIN', 'U');
    const date = ctx.body.date || todayStr();
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || date > todayStr())
      fail(400, 'INVALID_INPUT_VALUE', '오늘까지의 날짜만 실행할 수 있습니다.');
    return runGrant(date);
  });
}
