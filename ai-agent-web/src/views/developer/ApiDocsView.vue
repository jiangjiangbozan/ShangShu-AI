<script setup lang="ts">
/**
 * API 调用示例页面（借鉴 Dify Embedded / API Reference）
 *
 * 功能：
 * 1. 展示各接口的调用示例代码（cURL / Python / JavaScript / Java）
 * 2. 一键复制代码片段
 * 3. 展示 API 密钥使用方式
 */
import { ref, computed } from 'vue'
import {
  ElCard, ElTabs, ElTabPane, ElButton, ElMessage, ElIcon, ElTooltip,
  ElDescriptions, ElDescriptionsItem, ElTag,
} from 'element-plus'
import { CopyDocument, Key } from '@element-plus/icons-vue'

const activeTab = ref('chat')
const codeTab = ref('curl')

const apiBase = computed(() => `${window.location.origin}/api/v1`)

/** 代码片段 */
function getCodeSnippets(endpoint: string) {
  const url = `${apiBase.value}${endpoint}`
  return {
    curl: `curl -X POST '${url}' \\
  -H 'Authorization: Bearer YOUR_API_KEY' \\
  -H 'Content-Type: application/json' \\
  -d '{
    "content": "你好，请介绍一下你自己"
  }'`,
    python: `import requests

url = "${url}"
headers = {
    "Authorization": "Bearer YOUR_API_KEY",
    "Content-Type": "application/json"
}
data = {"content": "你好，请介绍一下你自己"}

response = requests.post(url, json=data, headers=headers)
print(response.json())`,
    javascript: `const response = await fetch("${url}", {
  method: "POST",
  headers: {
    "Authorization": "Bearer YOUR_API_KEY",
    "Content-Type": "application/json",
  },
  body: JSON.stringify({
    content: "你好，请介绍一下你自己"
  }),
});

const data = await response.json();
console.log(data);`,
    java: `import java.net.http.*;
import java.net.URI;

HttpClient client = HttpClient.newHttpClient();
String body = """
    {"content": "你好，请介绍一下你自己"}
    """;

HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("${url}"))
    .header("Authorization", "Bearer YOUR_API_KEY")
    .header("Content-Type", "application/json")
    .POST(HttpRequest.BodyPublishers.ofString(body))
    .build();

HttpResponse<String> response = client.send(request,
    HttpResponse.BodyHandlers.ofString());
System.out.println(response.body());`,
  }
}

const chatSnippet = computed(() => getCodeSnippets('/chat/sessions'))
const chatStreamSnippet = computed(() => getCodeSnippets('/chat/sessions/1/messages/stream'))

/** 知识库检索 */
const knowledgeSnippet = computed(() => {
  const url = `${apiBase.value}/knowledge/search`
  return {
    curl: `curl -X POST '${url}' \\
  -H 'Authorization: Bearer YOUR_API_KEY' \\
  -H 'Content-Type: application/json' \\
  -d '{
    "query": "什么是机器学习？",
    "topK": 5
  }'`,
    python: `import requests

url = "${url}"
headers = {
    "Authorization": "Bearer YOUR_API_KEY",
    "Content-Type": "application/json"
}
data = {"query": "什么是机器学习？", "topK": 5}

response = requests.post(url, json=data, headers=headers)
print(response.json())`,
    javascript: `const response = await fetch("${url}", {
  method: "POST",
  headers: {
    "Authorization": "Bearer YOUR_API_KEY",
    "Content-Type": "application/json",
  },
  body: JSON.stringify({ query: "什么是机器学习？", topK: 5 }),
});
const data = await response.json();`,
    java: `// Java 示例同上，仅替换 URL 和 body 即可`,
  }
})

interface ApiEndpoint {
  key: string
  title: string
  method: string
  path: string
  description: string
  snippet: { value: Record<string, string> }
}

const endpoints = computed<ApiEndpoint[]>(() => [
  {
    key: 'chat-create',
    title: '创建会话',
    method: 'POST',
    path: '/chat/sessions',
    description: '创建新的对话会话，指定模型 ID',
    snippet: chatSnippet,
  },
  {
    key: 'chat-stream',
    title: '流式对话',
    method: 'POST',
    path: '/chat/sessions/{id}/messages/stream',
    description: '发送消息并以 SSE 流式接收回复',
    snippet: chatStreamSnippet,
  },
  {
    key: 'knowledge-search',
    title: '知识库检索',
    method: 'POST',
    path: '/knowledge/search',
    description: '对知识库执行语义检索，返回最相关的分片',
    snippet: knowledgeSnippet,
  },
])

const currentEndpoint = computed<ApiEndpoint>(() =>
  endpoints.value.find(e => e.key === activeTab.value) ?? endpoints.value[0]!,
)

const currentCode = computed(() => {
  const snippets = currentEndpoint.value.snippet.value
  return snippets[codeTab.value as keyof typeof snippets] || ''
})

async function copyCode(code: string) {
  try {
    await navigator.clipboard.writeText(code)
    ElMessage.success('已复制到剪贴板')
  } catch { ElMessage.error('复制失败') }
}

const methodColor: Record<string, string> = {
  GET: 'success',
  POST: 'primary',
  PUT: 'warning',
  DELETE: 'danger',
  PATCH: 'info',
}
</script>

<template>
  <div class="api-docs-page">
    <div class="page-header">
      <div>
        <h2 style="margin: 0; font-size: 20px;">API 调用示例</h2>
        <p style="margin: 4px 0 0; color: var(--text-secondary); font-size: 13px;">
          快速接入平台 API，支持 cURL / Python / JavaScript / Java 示例代码
        </p>
      </div>
    </div>

    <!-- API 密钥提示 -->
    <el-card shadow="never" style="margin-bottom: 16px;">
      <div style="display: flex; align-items: center; gap: 12px;">
        <el-icon style="font-size: 20px; color: var(--el-color-warning);"><Key /></el-icon>
        <div>
          <div style="font-weight: 500;">API 密钥认证</div>
          <div style="font-size: 13px; color: var(--text-secondary);">
            所有 API 请求需在 Header 中携带 <code style="background: var(--bg-subtle, #f5f7fa); padding: 2px 6px; border-radius: 4px;">Authorization: Bearer YOUR_API_KEY</code>
            ，可在 <router-link to="/system/api-keys" style="color: var(--el-color-primary);">API 密钥管理</router-link> 页面创建密钥。
          </div>
        </div>
      </div>
    </el-card>

    <!-- 接口选择 -->
    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane v-for="ep in endpoints" :key="ep.key" :label="ep.title" :name="ep.key" />
      </el-tabs>

      <!-- 接口描述 -->
      <el-descriptions :column="1" size="small" border style="margin-bottom: 16px;">
        <el-descriptions-item label="接口">
          <el-tag :type="(methodColor[currentEndpoint.method] as any)" size="small" effect="dark" style="margin-right: 8px;">
            {{ currentEndpoint.method }}
          </el-tag>
          <code>{{ currentEndpoint.path }}</code>
        </el-descriptions-item>
        <el-descriptions-item label="说明">{{ currentEndpoint.description }}</el-descriptions-item>
        <el-descriptions-item label="Base URL">
          <code>{{ apiBase }}</code>
        </el-descriptions-item>
      </el-descriptions>

      <!-- 代码语言选择 -->
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
        <div style="display: flex; gap: 8px;">
          <el-button v-for="lang in ['curl', 'python', 'javascript', 'java']" :key="lang"
            :type="codeTab === lang ? 'primary' : 'default'"
            size="small"
            @click="codeTab = lang"
          >
            {{ lang === 'curl' ? 'cURL' : lang === 'javascript' ? 'JavaScript' : lang.charAt(0).toUpperCase() + lang.slice(1) }}
          </el-button>
        </div>
        <el-tooltip content="复制代码" placement="top">
          <el-button :icon="CopyDocument" size="small" @click="copyCode(currentCode)">复制</el-button>
        </el-tooltip>
      </div>

      <!-- 代码展示 -->
      <div class="code-block">
        <pre><code>{{ currentCode }}</code></pre>
      </div>
    </el-card>
  </div>
</template>

<script lang="ts">
export default { name: 'ApiDocsView' }
</script>

<style scoped>
.api-docs-page { padding: 4px; }
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}
.code-block {
  background: #1e1e2e;
  border-radius: 8px;
  padding: 16px;
  overflow-x: auto;
}
.code-block pre {
  margin: 0;
}
.code-block code {
  font-family: 'Fira Code', 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.6;
  color: #cdd6f4;
  white-space: pre;
}
</style>
