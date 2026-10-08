import { ref } from 'vue';

// 切换接口不是幂等的：同一笔记串行处理，结果不确定时只查询状态。
export function useBlogLikes({ api, getSession, onUpdate }) {
  const pendingIds = ref(new Set());
  const uncertainIds = ref(new Set());
  let generation = 0;

  function reset() {
    generation += 1;
    uncertainIds.value.clear();
  }

  async function run(id, toggle) {
    const key = String(id);
    if (pendingIds.value.has(key)) return { status: 'busy' };
    const session = getSession();
    const requestGeneration = generation;
    const isCurrent = () => session === getSession() && requestGeneration === generation;
    pendingIds.value.add(key);
    let submitted = false;
    try {
      if (toggle) {
        await api.toggleLike(id);
        submitted = true;
      }
      if (!isCurrent()) return { status: 'ignored' };
      const detail = await api.getDetail(id);
      if (!isCurrent()) return { status: 'ignored' };
      if (String(detail?.id) !== key || !Number.isInteger(detail?.liked) || detail.liked < 0) {
        throw new Error('笔记状态数据异常');
      }
      uncertainIds.value.delete(key);
      onUpdate(detail);
      return { status: toggle ? 'updated' : 'refreshed', detail };
    } catch (error) {
      if (!isCurrent()) return { status: 'ignored' };
      // Result.fail 明确拒绝了切换；传输失败无法判断服务器是否已执行。
      if (toggle && !submitted && error?.isBusinessError) return { status: 'failed', error };
      if (toggle || uncertainIds.value.has(key)) {
        uncertainIds.value.add(key);
        return { status: 'unconfirmed', submitted, error };
      }
      return { status: 'failed', error };
    } finally {
      pendingIds.value.delete(key);
    }
  }

  return {
    pendingIds,
    uncertainIds,
    reset,
    toggle: (id) => run(id, !uncertainIds.value.has(String(id))),
    refresh: (id) => run(id, false)
  };
}
