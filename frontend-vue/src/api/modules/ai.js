import request from '@/api/request';

function buildApiPath(path) {
  return import.meta.env.DEV ? `/api${path}` : path;
}

function parseSseBlock(block) {
  const lines = block.split(/\r?\n/);
  let event = 'message';
  const dataLines = [];

  for (const line of lines) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim();
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart());
    }
  }

  if (!dataLines.length) {
    return null;
  }

  const raw = dataLines.join('\n');
  try {
    return {
      event,
      data: JSON.parse(raw)
    };
  } catch {
    return {
      event,
      data: raw
    };
  }
}

export const aiApi = {
  createConversation() {
    return request({
      url: '/ai/agent/conversations',
      method: 'post'
    });
  },
  listConversations() {
    return request({
      url: '/ai/agent/conversations',
      method: 'get'
    });
  },
  getMessages(conversationId) {
    return request({
      url: `/ai/agent/conversations/${conversationId}/messages`,
      method: 'get'
    });
  },
  chat(data) {
    return request({
      url: '/ai/agent/chat',
      method: 'post',
      data
    });
  },
  recommend(data) {
    return request({
      url: '/ai/agent/recommend',
      method: 'post',
      data
    });
  },
  analyzeShop(shopId) {
    return request({
      url: `/ai/agent/analyze/shop/${shopId}`,
      method: 'get'
    });
  },
  async streamConversation(conversationId, { signal, onEvent } = {}) {
    const token = localStorage.getItem('hm-dianping-token');
    const response = await fetch(buildApiPath(`/ai/agent/conversations/${conversationId}/stream`), {
      method: 'GET',
      headers: {
        Accept: 'text/event-stream',
        ...(token ? { authorization: token } : {})
      },
      signal
    });

    if (!response.ok || !response.body) {
      throw new Error('建立 AI 流式连接失败');
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) {
        break;
      }

      buffer += decoder.decode(value, { stream: true });
      const blocks = buffer.split(/\r?\n\r?\n/g);
      buffer = blocks.pop() || '';

      blocks
        .map((item) => item.trim())
        .filter(Boolean)
        .forEach((item) => {
          const parsed = parseSseBlock(item);
          if (parsed) {
            onEvent?.(parsed);
          }
        });
    }
  }
};