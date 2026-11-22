import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { createRequire } from 'node:module';
import { installPositions } from '../src/api/mock/positions.js';
import { movePosition, positionOrder, POSITION_ORDER_NOTICE } from '../src/pages/org/departments/positionOrder.js';

const root = resolve(import.meta.dirname, '..');
const temporary = await mkdtemp(join(tmpdir(), 'position-check-'));
const require = createRequire(import.meta.url);
try {
  // 화면 경로는 DEPARTMENT V로 보호한다. GET /positions의 로그인 전용 권한과 구별한다.
  const appSource = await readFile(join(root, 'src/App.jsx'), 'utf8');
  assert.match(
    appSource,
    /path="\/org\/departments"\s+element=\{\s+<RequirePermission menu=\{MENU.DEPARTMENT\}>/,
    '/org/departments must require DEPARTMENT V',
  );
  const outfile = join(temporary, 'api.cjs');
  await build({
    entryPoints: [join(root, 'src/api/org.js')],
    outfile,
    bundle: true,
    platform: 'node',
    format: 'cjs',
    plugins: [
      {
        name: 'capture-http',
        setup(bundler) {
          bundler.onResolve({ filter: /^\.\/axios$/ }, () => ({ path: 'http', namespace: 'fixture' }));
          bundler.onLoad({ filter: /.*/, namespace: 'fixture' }, () => ({
            contents: `
        export const unwrap = promise => promise.then(response => response.data.data);
        const request = (method, path, body) => {
          globalThis.positionRequests.push({ method, path, body });
          return Promise.resolve({ data: { data: null } });
        };
        export default Object.fromEntries(['get','post','patch','put','delete'].map(method =>
          [method, (path, body) => request(method, path, body)]));
      `,
          }));
        },
      },
    ],
  });
  const { positionApi } = require(outfile);
  for (const name of ['list', 'create', 'update', 'saveOrder', 'remove']) {
    assert.equal(typeof positionApi[name], 'function', `positionApi.${name} must exist`);
  }
  globalThis.positionRequests = [];
  await positionApi.create({ name: '선임' });
  await positionApi.update(3, { name: '대표' });
  await positionApi.saveOrder([
    { id: 3, rankOrder: 1 },
    { id: 1, rankOrder: 2 },
  ]);
  await positionApi.remove(3);
  assert.deepEqual(globalThis.positionRequests, [
    { method: 'post', path: '/positions', body: { name: '선임' } },
    { method: 'patch', path: '/positions/3', body: { name: '대표' } },
    {
      method: 'put',
      path: '/positions/order',
      body: [
        { id: 3, rankOrder: 1 },
        { id: 1, rankOrder: 2 },
      ],
    },
    { method: 'delete', path: '/positions/3', body: undefined },
  ]);
  console.log('POSITION_API methods=5 order_body=PASS');
  const routes = new Map();
  const db = {
    positions: [
      { id: 1, name: '사원', rankOrder: 1 },
      { id: 2, name: '관리소장', rankOrder: 5 },
    ],
    users: [{ positionId: 1, status: 'RETIRED' }],
  };
  const fail = (status, code, message) => {
    throw Object.assign(new Error(message), { status, code });
  };
  installPositions({
    db,
    fail,
    created: (data) => ({ __status: 201, data }),
    route: (method, path, handler) => routes.set(`${method} ${path}`, handler),
    need: (ctx, menu, action) => {
      assert.equal(menu, 'DEPARTMENT');
      if (!ctx.actions.includes(action)) fail(403, 'ACCESS_DENIED', '접근 권한이 없습니다.');
    },
  });
  const call = (method, path, body = {}, id, actions = 'CUD') =>
    routes.get(`${method} ${path}`)({ body, path: { id }, actions });
  const list = () => call('GET', '/positions');
  for (const [method, path, body, id] of [
    ['POST', '/positions', { name: '거절' }],
    ['PATCH', '/positions/:id', { name: '거절' }, 1],
    ['PUT', '/positions/order', [{ id: 1, rankOrder: 99 }]],
    ['DELETE', '/positions/:id', {}, 2],
  ]) {
    const before = JSON.stringify(db);
    assert.throws(
      () => call(method, path, body, id, ''),
      (error) => error.status === 403,
    );
    assert.equal(JSON.stringify(db), before);
  }
  const added = call('POST', '/positions', { name: '선임' });
  assert.equal(added.__status, 201);
  assert.equal(added.data.rankOrder, 0);
  assert.equal(list()[0].name, '선임');
  assert.throws(
    () => call('POST', '/positions', { name: '선임' }),
    (error) => error.status === 409,
  );
  assert.throws(
    () => call('POST', '/positions', { name: ' ' }),
    (error) => error.status === 400,
  );
  const saved = list();
  const moved = movePosition(saved, 0, 1);
  assert.deepEqual(
    saved.map((item) => item.id),
    [3, 1, 2],
  );
  assert.deepEqual(
    moved.map((item) => item.id),
    [1, 3, 2],
  );
  assert.deepEqual(list(), saved); // 이동만으로 서버 데이터가 바뀌지 않는다.
  const order = positionOrder(moved);
  assert.deepEqual(order, [
    { id: 1, rankOrder: 1 },
    { id: 3, rankOrder: 2 },
    { id: 2, rankOrder: 3 },
  ]);
  assert.deepEqual(
    call('PUT', '/positions/order', order).map((item) => item.id),
    [1, 3, 2],
  );
  const beforeInvalid = list();
  assert.throws(
    () =>
      call('PUT', '/positions/order', [
        { id: 1, rankOrder: 99 },
        { id: 999, rankOrder: 1 },
      ]),
    (error) => error.status === 404,
  );
  assert.deepEqual(list(), beforeInvalid);
  assert.throws(
    () =>
      call('PUT', '/positions/order', [
        { id: 1, rankOrder: 99 },
        { id: 1, rankOrder: 1 },
      ]),
    (error) => error.status === 400,
  );
  assert.deepEqual(list(), beforeInvalid);
  call('PATCH', '/positions/:id', { name: '대표' }, 2);
  assert.equal(list()[2].name, '대표');
  let inUse;
  try {
    call('DELETE', '/positions/:id', {}, 1);
  } catch (error) {
    inUse = error;
  }
  assert.equal(inUse.status, 409);
  assert.equal(inUse.code, 'POSITION_IN_USE');
  call('DELETE', '/positions/:id', {}, 3);
  assert.deepEqual(
    list().map((item) => item.id),
    [1, 2],
  );
  console.log('POSITION_MOCK permissions=4/4 create_201_rank_zero=PASS rename=PASS order_atomic=PASS delete_409=PASS');

  const renderFile = join(temporary, 'render.cjs');
  await build({
    stdin: {
      contents: `export { default as View } from './src/pages/org/departments/PositionCardView.jsx';
      export { RequirePermission } from './src/components/layout/guards.jsx';
      export { getNavigation } from './src/components/layout/navigation.js';
      export { MENU } from './src/lib/permissions.js';
      export { createElement } from 'react'; export { renderToStaticMarkup } from 'react-dom/server';`,
      resolveDir: root,
      loader: 'jsx',
    },
    outfile: renderFile,
    bundle: true,
    platform: 'node',
    format: 'cjs',
    jsx: 'automatic',
    alias: { '@': join(root, 'src') },
    plugins: [
      {
        name: 'permission-fixture',
        setup(bundler) {
          bundler.onResolve({ filter: /\/store\/authStore(?:\.js)?$/ }, () => ({
            path: 'auth',
            namespace: 'permission-fixture',
          }));
          bundler.onLoad({ filter: /.*/, namespace: 'permission-fixture' }, () => ({
            contents: 'export const useAuthStore = selector => selector({ can: globalThis.positionCan });',
          }));
        },
      },
    ],
  });
  const { View, RequirePermission, getNavigation, MENU, createElement, renderToStaticMarkup } = require(renderFile);
  const view = (props) =>
    createElement(View, {
      positions: saved,
      editing: null,
      name: '',
      error: '',
      notice: '',
      dirty: false,
      onNameChange() {},
      onEditNameChange() {},
      ...props,
    });
  const render = (props) => renderToStaticMarkup(view(props));
  for (let mask = 0; mask < 16; mask += 1) {
    const canView = !!(mask & 1),
      canCreate = !!(mask & 2),
      canUpdate = !!(mask & 4),
      canDelete = !!(mask & 8);
    const actions = new Set(['V', 'C', 'U', 'D'].filter((_, index) => mask & (1 << index)));
    globalThis.positionCan = (menu, action = 'V') => menu === MENU.DEPARTMENT && actions.has(action);
    const navigation = getNavigation({ can: globalThis.positionCan });
    assert.equal(
      navigation.flatMap((group) => group.items).some((item) => item.path === '/org/departments'),
      canView,
    );
    const html = renderToStaticMarkup(
      createElement(RequirePermission, { menu: MENU.DEPARTMENT }, view({ canCreate, canUpdate, canDelete })),
    );
    assert.equal(html.includes('aria-label="직급 관리"'), canView);
    assert.equal(html.includes('직급 추가'), canView && canCreate);
    assert.equal(html.includes('이름 변경'), canView && canUpdate);
    assert.equal(html.includes('순서 저장'), canView && canUpdate);
    assert.equal(html.includes('위로 이동'), canView && canUpdate);
    assert.equal(html.includes('사원 삭제'), canView && canDelete);
    if (canView) {
      assert.match(html, /낮은 직급 → 높은 직급/);
      if (canUpdate) assert.match(html, /aria-label="사원 이름 변경"/);
    } else {
      assert.match(html, /이 화면을 볼 권한이 없습니다/);
    }
  }
  assert.match(render({ notice: POSITION_ORDER_NOTICE }), /새 직급은 맨 앞에 들어갑니다/);
  assert.match(render({ error: inUse.message }), /role="alert"[^>]*>이 직급을 쓰는 사원이 있어 삭제할 수 없습니다/);
  console.log(
    'POSITION_VIEW permission_combinations=16/16 menu_and_route=PASS rename_label=PASS addition_notice=PASS server_409_message=PASS',
  );
} finally {
  delete globalThis.positionRequests;
  delete globalThis.positionCan;
  await rm(temporary, { recursive: true, force: true });
}
