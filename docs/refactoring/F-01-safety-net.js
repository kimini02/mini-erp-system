/*
 * F-01 프론트 리팩토링 안전망 — 브라우저 콘솔용 비교 스크립트 (빌드에 포함되지 않음)
 *
 * 쓰는 법 (목 모드, 관리소장 계정):
 * 1. `npm run dev:mock`으로 띄우고, 콘솔에서 localStorage.setItem('__MOCK_NOW__', '2026-10-28T10:00:00+09:00') 뒤 새로고침.
 *    (frontend/src/api/mock/clock.js가 이 시각으로 시계를 멈춰, 날짜가 바뀌어도 같은 목 데이터가 나옴)
 * 2. 관리소장 목 계정으로 로그인한 뒤 이 파일 전체를 콘솔에 붙여 넣는다.
 * 3. 기준 만들기: await captureBaseline(); await modalRun('capture');
 *    비교하기:   await compareRun(); await modalRun('compare');
 *    결과는 window.__RUN__, window.__MODAL__ (rows: SAME/DIFF, diffs: 차이 위치)
 *
 * 비교 대상: 화면 글자(innerText), DOM 전체(innerHTML), 그 화면이 부른 목 API 목록과 순서.
 * 실행마다 달라지는 값만 가린다: React useId(:rN:), FullCalendar 요소 id(fc-dom-N),
 * AppLayout의 주기 새로고침 요청 묶음(60초·창 포커스마다 보냄).
 * 브라우저가 이 탭의 타이머를 늦추면 한 번에 화면 3~4분, 모달 5~8분이 걸린다.
 */

const ROUTES = [
  '/home',
  '/approvals',
  '/approvals/new?form=LEAVE',
  '/approvals/new?form=OVERTIME',
  '/approvals/new?form=ATTENDANCE_FIX',
  '/approvals/new?form=GENERAL',
  '/approvals/140',
  '/approval-forms',
  '/leaves',
  '/attendance',
  '/attendance/monthly',
  '/leave/admin',
  '/calendar',
  '/projects',
  '/projects/1/board',
  '/holidays',
  '/org/departments',
  '/org/employees',
  '/org/roles',
  '/me/password',
];

// [이름, 화면, 차례로 누를 버튼(라벨 조건), 모달이 아닐 때 기록할 요소]
const MODALS = [
  ['dept-add', '/org/departments', [(l) => l === '부서 추가']],
  ['dept-move', '/org/departments', [(l) => l.startsWith('개발1팀'), (l) => l.includes('옮기기')]],
  ['dept-head', '/org/departments', [(l) => l.startsWith('개발1팀'), (l) => l === '변경']],
  ['emp-create', '/org/employees', [(l) => l === '사원 등록']],
  ['emp-edit', '/org/employees', [(l) => l.endsWith('정보 수정')]],
  ['role-create', '/org/roles', [(l) => l === '역할 만들기']],
  ['role-edit', '/org/roles', [(l) => l.startsWith('인사 담당'), (l) => l.includes('수정')]],
  ['role-members', '/org/roles', [(l) => l.startsWith('인사 담당'), (l) => /^사원 \d+$/.test(l), (l) => l.includes('추가')]],
  ['holiday-add', '/holidays', [(l) => l === '공휴일 등록']],
  ['leave-history', '/leave/admin', [(l) => l === '기록 보기']],
  ['project-create', '/projects', [(l) => l === '프로젝트 등록']],
  [
    'task-add',
    '/projects/1/board',
    [(l) => l === '업무 추가'],
    // 업무 추가는 모달이 아니라 보드 안에 펼쳐지는 입력 줄이라 그 영역을 기록한다.
    () => {
      const input = document.querySelector('main input[placeholder="업무 제목"]');
      return input?.closest('form') || input?.parentElement?.parentElement;
    },
  ],
  ['task-drawer', '/projects/1/board', [(l) => l.startsWith('GW-21')]],
];

const POLL = ['GET /approval-inbox/counts', 'GET /approval-inbox?box=todo&page=0&size=5', 'GET /notifications/unread-count'];
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const norm = (html) => html.replace(/:r[0-9a-z]+:/g, ':rID:').replace(/fc-dom-\d+/g, 'fc-dom-ID');
const stripPolling = (calls) => {
  const out = [];
  for (let i = 0; i < calls.length; i++) {
    if (calls[i] === POLL[0] && calls[i + 1] === POLL[1] && calls[i + 2] === POLL[2]) {
      i += 2;
      continue;
    }
    out.push(calls[i]);
  }
  return out;
};

// 목 API 호출 수와 DOM 길이가 0.5초 동안 그대로면 화면이 다 그려진 것으로 본다.
async function settle() {
  let last = -1;
  let stable = 0;
  for (let i = 0; i < 40; i++) {
    await sleep(100);
    const now = (window.__MOCK_CALLS__ || []).length * 1e7 + document.body.innerHTML.length;
    if (now === last) {
      if (++stable >= 5) break;
    } else {
      stable = 0;
      last = now;
    }
  }
}

// 같은 화면에서 주소만 바꾸면 다시 그리지 않으므로, 매번 다른 화면을 한 번 거친다.
async function visit(path) {
  for (const step of [path === '/me/password' ? '/projects' : '/me/password', path]) {
    window.__MOCK_CALLS__ = [];
    history.pushState({}, '', step);
    dispatchEvent(new PopStateEvent('popstate'));
    await settle();
  }
}

const firstDiff = (a, b) => {
  let i = 0;
  while (i < a.length && a[i] === b[i]) i++;
  if (i >= a.length && a.length === b.length) return null;
  return { at: i, before: a.slice(Math.max(0, i - 60), i + 60), after: b.slice(Math.max(0, i - 60), i + 60) };
};

function snapshot() {
  const root = document.getElementById('root');
  return { text: root.innerText, html: norm(root.innerHTML), calls: stripPolling([...window.__MOCK_CALLS__]) };
}

async function warmUp() {
  // 새로고침 직후의 로그인 정보 요청(/me, /me/permissions)이 첫 화면 기록에 섞이지 않게 한다.
  await visit('/home');
  await sleep(1500);
}

async function captureBaseline() {
  await warmUp();
  const store = {};
  for (const route of ROUTES) {
    await visit(route);
    store[route] = snapshot();
  }
  localStorage.setItem('__refactor_baseline__', JSON.stringify({ mockNow: localStorage.getItem('__MOCK_NOW__'), store }));
}

async function compareRun() {
  const base = JSON.parse(localStorage.getItem('__refactor_baseline__')).store;
  await warmUp();
  const result = { done: false, rows: [], diffs: {} };
  window.__RUN__ = result;
  for (const route of Object.keys(base)) {
    await visit(route);
    const now = snapshot();
    const was = base[route];
    const diff = {
      text: firstDiff(was.text, now.text),
      html: firstDiff(norm(was.html), now.html),
      calls: JSON.stringify(stripPolling(was.calls)) === JSON.stringify(now.calls) ? null : { before: was.calls, after: now.calls },
    };
    const same = !diff.text && !diff.html && !diff.calls;
    result.rows.push(`${same ? 'SAME' : 'DIFF'} ${route}`);
    if (!same) result.diffs[route] = diff;
  }
  result.done = true;
  return result;
}

async function modalRun(mode) {
  const label = (el) => (el.getAttribute('aria-label') || el.textContent || '').trim();
  const click = async (match) => {
    const target = [...document.querySelectorAll('button, [role=button]')].find((el) => match(label(el)));
    if (!target) return false;
    target.click();
    await settle();
    return true;
  };
  const closeAll = async () => {
    const event = { key: 'Escape', bubbles: true };
    window.dispatchEvent(new KeyboardEvent('keydown', event));
    document.dispatchEvent(new KeyboardEvent('keydown', event));
    await settle();
  };
  const key = '__refactor_modal_baseline__';
  const base = mode === 'compare' ? JSON.parse(localStorage.getItem(key)) : null;
  const state = { mode, done: false, rows: [], diffs: {} };
  window.__MODAL__ = state;
  const store = {};
  await warmUp();
  for (const [name, route, steps, pick] of MODALS) {
    await visit(route);
    window.__MOCK_CALLS__ = [];
    let opened = true;
    for (const step of steps) opened = opened && (await click(step));
    const target = pick ? pick() : [...document.querySelectorAll('[role=dialog]')].pop();
    const record = {
      ok: opened && !!target,
      text: target ? target.innerText : '',
      html: target ? norm(target.outerHTML) : '',
      calls: stripPolling([...window.__MOCK_CALLS__]),
    };
    await closeAll();
    await closeAll();
    store[name] = record;
    if (!base) {
      state.rows.push(`${record.ok ? 'OK  ' : 'MISS'} ${name}`);
      continue;
    }
    const was = base[name];
    const same = was && JSON.stringify(was) === JSON.stringify(record);
    state.rows.push(`${same ? 'SAME' : 'DIFF'} ${name}`);
    if (!same) state.diffs[name] = { okBefore: was?.ok, okAfter: record.ok, text: was?.text === record.text, html: was?.html === record.html };
  }
  if (!base) localStorage.setItem(key, JSON.stringify(store));
  state.done = true;
  return state;
}
