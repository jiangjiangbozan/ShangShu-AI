<script setup lang="ts">
/**
 * 全局对话搜索页面
 *
 * 功能：关键词搜索全部对话历史，支持角色/时间筛选，点击跳转到会话。
 * 对应后端 ChatSearchController GET /api/v1/chat/search
 */
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElCard, ElInput, ElButton, ElDatePicker, ElTag,
  ElTable, ElTableColumn, ElEmpty, ElIcon, ElPagination, ElMessage, ElRadioGroup, ElRadioButton,
} from 'element-plus'
import { Search, Refresh, ChatLineSquare } from '@element-plus/icons-vue'
import { searchChatHistory, type ChatSearchHit } from '@/api/chat'

const router = useRouter()

const query = ref('')
const loading = ref(false)
const hits = ref<ChatSearchHit[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const hasMore = ref(false)

const roleFilter = ref<'' | 'user' | 'assistant'>('')
const dateRange = ref<[Date, Date] | null>(null)

async function handleSearch() {
  if (!query.value.trim()) {
    ElMessage.warning('请输入搜索关键词')
    return
  }
  loading.value = true
  try {
    const res = await searchChatHistory(query.value.trim(), page.value, size.value, {
      role: roleFilter.value || undefined,
      startTime: dateRange.value?.[0].getTime(),
      endTime: dateRange.value?.[1].getTime(),
    })
    hits.value = res.hits ?? []
    total.value = res.total ?? 0
    hasMore.value = res.hasMore ?? false
  } catch (e: any) {
    ElMessage.error(e?.message || '搜索失败')
  } finally {
    loading.value = false
  }
}

function handleReset() {
  query.value = ''
  roleFilter.value = ''
  dateRange.value = null
  page.value = 1
  hits.value = []
  total.value = 0
}

function handlePageChange(p: number) {
  page.value = p
  handleSearch()
}

function goToSession(sessionId: number, messageId?: string) {
  const query: Record<string, string> = { sessionId: String(sessionId) }
  if (messageId) query.highlightMessageId = messageId
  router.push({ path: '/chat', query })
}

function roleTagType(role: string) {
  return role === 'user' ? 'primary' : 'success'
}

function formatDate(ts: number | string | undefined) {
  if (!ts) return '-'
  const d = new Date(typeof ts === 'number' ? ts : ts)
  return d.toLocaleString('zh-CN')
}
</script>

<template>
  <div class="chat-search">
    <h2 style="margin: 0 0 20px; font-size: 20px;">全局搜索</h2>

    <!-- 搜索栏 -->
    <el-card shadow="never" style="margin-bottom: 16px;">
      <div style="display: flex; gap: 12px; flex-wrap: wrap; align-items: center;">
        <el-input
          v-model="query"
          placeholder="搜索对话内容..."
          clearable
          style="width: 360px;"
          @keyup.enter="handleSearch"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-radio-group v-model="roleFilter" size="default">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="user">用户</el-radio-button>
          <el-radio-button value="assistant">助手</el-radio-button>
        </el-radio-group>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 260px;"
        />
        <el-button type="primary" :icon="Search" :loading="loading" @click="handleSearch">搜索</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </div>
    </el-card>

    <!-- 搜索结果 -->
    <el-card shadow="never">
      <div v-if="total > 0" style="margin-bottom: 12px; color: #909399; font-size: 13px;">
        共找到 {{ total }} 条结果
      </div>

      <el-table v-if="hits.length" :data="hits" stripe style="width: 100%;" @row-click="(row: any) => goToSession(row.sessionId, row.messageId)">
        <el-table-column prop="role" label="角色" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="roleTagType(row.role)" size="small">{{ row.role === 'user' ? '用户' : '助手' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="内容" min-width="400">
          <template #default="{ row }">
            <div v-if="row.highlight" v-html="row.highlight" style="line-height: 1.5;" />
            <div v-else style="line-height: 1.5;">{{ (row.content || '').substring(0, 200) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="会话" width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.titleHighlight" v-html="row.titleHighlight" />
            <span v-else>{{ row.title || '未命名会话' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="modelId" label="模型" width="130" show-overflow-tooltip />
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default>
            <el-button type="primary" link size="small" :icon="ChatLineSquare">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-else-if="!loading" description="输入关键词搜索对话历史" />

      <div v-if="total > size" style="margin-top: 12px; display: flex; justify-content: flex-end;">
        <el-pagination
          :current-page="page"
          :page-size="size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script lang="ts">
export default { name: 'ChatSearchView' }
</script>

<style scoped>
.chat-search { padding: 4px; }
.chat-search :deep(em) {
  font-style: normal;
  background: #fff3b0;
  color: inherit;
  font-weight: 600;
  padding: 0 1px;
  border-radius: 2px;
}
</style>
