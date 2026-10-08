import test from 'node:test';
import assert from 'node:assert/strict';
import { useBlogLikes } from '../src/composables/useBlogLikes.js';

function deferred() {
  let resolve;
  const promise = new Promise((done) => { resolve = done; });
  return { promise, resolve };
}

function setup(overrides = {}) {
  let session = 'user-a';
  const calls = { toggle: [], detail: [], updates: [] };
  const api = {
    async toggleLike(id) { calls.toggle.push(id); },
    async getDetail(id) {
      calls.detail.push(id);
      return { id, liked: 120, isLike: true };
    },
    ...overrides
  };
  const likes = useBlogLikes({
    api,
    getSession: () => session,
    onUpdate: (detail) => calls.updates.push(detail)
  });
  return { likes, calls, api, changeUser: () => { session = 'user-b'; likes.reset(); } };
}

test('空的点赞成功响应后回查实时计数，取消点赞也以后端结果为准', async () => {
  const { likes, calls, api } = setup();
  assert.equal((await likes.toggle(1)).status, 'updated');
  assert.deepEqual(calls.updates, [{ id: 1, liked: 120, isLike: true }]);
  api.getDetail = async (id) => ({ id, liked: 145, isLike: false });
  assert.equal((await likes.toggle(1)).detail.isLike, false);
  assert.equal(calls.updates[1].liked, 145);
  assert.deepEqual(calls.toggle, [1, 1]);
  assert.equal(likes.pendingIds.value.size, 0);
});

test('列表和详情快速重复点击同一笔记，只提交一次切换', async () => {
  const put = deferred();
  let writes = 0;
  const { likes } = setup({ toggleLike: () => { writes += 1; return put.promise; } });
  const first = likes.toggle(1);
  assert.equal(likes.pendingIds.value.has('1'), true);
  assert.equal((await likes.toggle('1')).status, 'busy');
  assert.equal((await likes.refresh(1)).status, 'busy');
  assert.equal(writes, 1);
  put.resolve();
  await first;
  assert.equal(likes.pendingIds.value.size, 0);
});

test('提交成功后的详情查询完成前仍阻止重复切换', async () => {
  const read = deferred();
  const { likes, calls } = setup({ getDetail: () => read.promise });
  const first = likes.toggle(1);
  await Promise.resolve();
  assert.equal((await likes.toggle(1)).status, 'busy');
  read.resolve({ id: 1, liked: 5, isLike: true });
  await first;
  assert.deepEqual(calls.toggle, [1]);
});

test('不同笔记可以同时点赞', async () => {
  const put = deferred();
  const { likes } = setup({ toggleLike: (id) => id === 1 ? put.promise : Promise.resolve() });
  const first = likes.toggle(1);
  assert.equal((await likes.toggle(2)).status, 'updated');
  put.resolve();
  assert.equal((await first).status, 'updated');
});

test('点赞超时后下次点击只查询，恢复状态后才允许再次切换', async () => {
  let writes = 0;
  const { likes, calls, api } = setup({ toggleLike: async () => { writes += 1; throw new Error('timeout'); } });
  assert.equal((await likes.toggle(1)).status, 'unconfirmed');
  assert.equal(likes.uncertainIds.value.has('1'), true);
  assert.equal(calls.updates.length, 0);
  assert.equal((await likes.toggle(1)).status, 'refreshed');
  assert.equal(writes, 1);
  assert.equal(likes.uncertainIds.value.size, 0);
  api.toggleLike = async () => { writes += 1; };
  assert.equal((await likes.toggle(1)).status, 'updated');
  assert.equal(writes, 2);
});

test('点赞已成功但查询失败时，刷新失败也不重发切换', async () => {
  const { likes, calls, api } = setup({ getDetail: async () => { throw new Error('offline'); } });
  const result = await likes.toggle(1);
  assert.equal(result.status, 'unconfirmed');
  assert.equal(result.submitted, true);
  assert.equal((await likes.toggle(1)).status, 'unconfirmed');
  assert.deepEqual(calls.toggle, [1]);
  api.getDetail = async (id) => ({ id, liked: 7, isLike: false });
  assert.equal((await likes.refresh(1)).status, 'refreshed');
  assert.equal(likes.uncertainIds.value.size, 0);
});

test('明确的业务拒绝保持原状态，并释放按钮', async () => {
  const error = Object.assign(new Error('点赞计数异常'), { isBusinessError: true });
  const { likes, calls } = setup({ toggleLike: async () => { throw error; } });
  assert.equal((await likes.toggle(1)).status, 'failed');
  assert.equal(calls.detail.length, 0);
  assert.equal(calls.updates.length, 0);
  assert.equal(likes.uncertainIds.value.size, 0);
  assert.equal(likes.pendingIds.value.size, 0);
});

test('切换账号后旧点赞响应不回查或覆盖新账号状态', async () => {
  const put = deferred();
  const { likes, calls, changeUser } = setup({ toggleLike: () => put.promise });
  const first = likes.toggle(1);
  changeUser();
  put.resolve();
  assert.equal((await first).status, 'ignored');
  assert.equal(calls.detail.length, 0);
  assert.equal(calls.updates.length, 0);
  assert.equal(likes.pendingIds.value.size, 0);
});

test('切换账号后丢弃在途详情响应', async () => {
  const read = deferred();
  const { likes, calls, changeUser } = setup({ getDetail: () => read.promise });
  const first = likes.refresh(1);
  changeUser();
  read.resolve({ id: 1, liked: 30, isLike: true });
  assert.equal((await first).status, 'ignored');
  assert.equal(calls.updates.length, 0);
});

test('读详情与点赞共用笔记锁，普通查询失败不会改变状态', async () => {
  const read = deferred();
  const { likes, calls, api } = setup({ getDetail: () => read.promise });
  const first = likes.refresh(1);
  assert.equal((await likes.toggle(1)).status, 'busy');
  read.resolve({ id: 1, liked: 18, isLike: false });
  assert.equal((await first).status, 'refreshed');
  assert.equal(calls.toggle.length, 0);
  api.getDetail = async () => { throw new Error('offline'); };
  assert.equal((await likes.refresh(1)).status, 'failed');
  assert.equal(likes.uncertainIds.value.size, 0);
});

test('无效详情不覆盖界面，成功切换后保留待确认状态', async () => {
  for (const detail of [{ id: 2, liked: 1 }, { id: 1, liked: -1 }, null]) {
    const { likes, calls } = setup({ getDetail: async () => detail });
    assert.equal((await likes.toggle(1)).status, 'unconfirmed');
    assert.equal(calls.updates.length, 0);
  }
});
