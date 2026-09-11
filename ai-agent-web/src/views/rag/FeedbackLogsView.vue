<script setup lang="ts">
/**
 * 知识库反馈日志页面
 *
 * 查看用户对知识库分片的点踩记录，辅助 RAG 效果评估。
 * 对应后端 ChunkFeedbackController /api/v1/knowledge/feedback
 */
import { ref, reactive, onMounted } from 'vue'
import {
  ElCard, ElTable, ElTableColumn, ElTag, ElInput, ElSelect, ElOption,
  ElButton, ElPagination, ElIcon, ElEmpty,
} from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { listFeedbackLogs, type ChunkFeedbackLogItem } from '@/api/knowledge'

const logs = ref<ChunkFeedbackLogItem[]>([])
const total = ref(0)
const loading = ref(false)
const page = ref(1)
const size = ref(20)

const filter = reactive({
  chunkId: '',
  userId: '',
  feedbackType: '',
})

async function fetchLogs() {
  loading.value = true
  try {
    const params: Record<string, unknown> = { page: page.value, size: size.value }
    if (filter.chunkId) params.chunkId = Number(filter.chunkId)
    if (filter.userId) params.userId = Number(filter.userId)
    if (filter.feedbackType) params.feedbackType = filter.feedbackType
    const res = await listFeedbackLogs(params)
    logs.value = res.list ?? []
    total.value = res.total ?? 0
  } catch { /* ignore */ }
  finally { loading.value = false }
}

function handleSearch() {
  page.value = 1
  fetchLogs()
}

function handleReset() {
  filter.chunkId = ''
  filter.userId = ''
  filter.feedbackType = ''
  page.value = 1
  fetchLogs()
}

function handlePageChange(p: number) {
  page.value = p
  fetchLogs()
}

onMounted(fetchLogs)
</script>

<template>
  <div class="feedback-logs">
    <h2 style="margin: 0 0 20px; font-size: 20px;">反馈日志</h2>

    <el-card shadow="never" style="margin-bottom: 16px;">
      <div style="display: flex; gap: 12px; flex-wrap: wrap; align-items: center;">
        <el-input v-model="filter.chunkId" placeholder="分片 ID" clearable style="width: 140px;" />
        <el-input v-model="filter.userId" placeholder="用户 ID" clearable style="width: 140px;" />
        <el-select v-model="filter.feedbackType" placeholder="反馈类型" clearable style="width: 140px;">
          <el-option label="点踩" value="negative" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-table :data="logs" v-loading="loading" stripe style="width: 100%;">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="chunkId" label="分片 ID" width="120" />
        <el-table-column prop="userId" label="用户 ID" width="120" />
        <el-table-column prop="feedbackType" label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.feedbackType === 'negative' ? 'danger' : 'info'" size="small">
              {{ row.feedbackType === 'negative' ? '点踩' : row.feedbackType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" min-width="170" />
      </el-table>
      <div v-if="total > size" style="margin-top: 12px; display: flex; justify-content: flex-end;">
        <el-pagination
          :current-page="page"
          :page-size="size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="handlePageChange"
        />
      </div>
      <el-empty v-if="!loading && logs.length === 0" description="暂无反馈记录" />
    </el-card>
  </div>
</template>

<script lang="ts">
export default { name: 'FeedbackLogsView' }
</script>

<style scoped>
.feedback-logs { padding: 4px; }
</style>
