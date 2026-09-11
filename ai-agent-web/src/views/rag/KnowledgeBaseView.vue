<script setup lang="ts">
/**
 * 知识库管理页面
 *
 * 功能：
 * 1. 知识库列表（创建、查看）
 * 2. 文档上传（支持拖拽）
 * 3. 检索测试：输入 query，查看 topK 命中分片
 * 4. 一键跳转到对话页（带 kbId 启用 RAG）
 */
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElCard,
  ElButton,
  ElTable,
  ElTableColumn,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElUpload,
  ElTag,
  ElEmpty,
  ElIcon,
  ElTooltip,
  ElProgress,
  ElDrawer,
  ElDescriptions,
  ElDescriptionsItem,
  ElSelect,
  ElOption,
  ElDivider,
} from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { Plus, UploadFilled, Search, ChatLineSquare, Delete, Refresh, View, DataLine } from '@element-plus/icons-vue'
import {
  listKnowledgeBases,
  createKnowledgeBase,
  uploadDocument,
  searchKnowledge,
  listDocuments,
  getDocumentDetail,
  deleteDocument,
  reprocessDocument,
  listDocumentChunks,
  updateChunk,
  type KnowledgeBaseResp,
  type KnowledgeDocumentResp,
  type KnowledgeDocumentDetailResp,
  type KnowledgeChunkResp,
  type SearchHitItem,
} from '@/api/knowledge'
import {
  getRelatedChats,
  batchDeleteDocuments,
  batchReprocessDocuments,
  getBatchProgress,
  type RelatedChatResp,
} from '@/api/document'

const router = useRouter()

/** 知识库列表 */
const knowledgeBases = ref<KnowledgeBaseResp[]>([])
const listLoading = ref(false)

/** 选中的知识库 */
const selectedKb = ref<KnowledgeBaseResp | null>(null)

/** 创建对话框 */
const showCreate = ref(false)
const createForm = ref({ name: '', description: '' })
const creating = ref(false)

/** 上传对话框 */
const showUpload = ref(false)
const uploading = ref(false)
const lastUploadedDoc = ref<KnowledgeDocumentResp | null>(null)

/** 检索测试 */
const searchQuery = ref('')
const searchTopK = ref(5)
const searchResults = ref<SearchHitItem[]>([])
const searchLatency = ref(0)
const searching = ref(false)

/** ============ 文档管理（P0-6） ============ */
/** 文档列表 */
const documents = ref<KnowledgeDocumentResp[]>([])
const docsLoading = ref(false)
const docsKeyword = ref('')
const docsStatusFilter = ref('')

/** 文档详情抽屉 */
const showDocDetail = ref(false)
const docDetail = ref<KnowledgeDocumentDetailResp | null>(null)
const docDetailLoading = ref(false)

/** 删除/重处理按钮 loading（按 docId 索引） */
const docActionLoading = ref<Record<number, boolean>>({})

/** ============ 分片管理（B3） ============ */
/** 文档全部分片（点击"查看全部分片"后加载） */
const allChunks = ref<KnowledgeChunkResp[]>([])
const chunksLoading = ref(false)
const showAllChunks = ref(false)

/** 分片编辑对话框 */
const showChunkEdit = ref(false)
const editingChunk = ref<KnowledgeChunkResp | null>(null)
const chunkEditContent = ref('')
const chunkSaving = ref(false)

/** ============ 批量操作（add-p3-rag-feedback-ops） ============ */
/** 已选中的文档 ID 列表 */
const selectedDocIds = ref<number[]>([])
const batchDeleteLoading = ref(false)
const batchReprocessLoading = ref(false)

/** 关联问答记录（文档详情抽屉内展示） */
const relatedChats = ref<RelatedChatResp[]>([])
const relatedChatsLoading = ref(false)

/** 批量重处理进度轮询 */
let batchProgressTimer: ReturnType<typeof setInterval> | null = null
const batchProgress = ref<{ batchId: string; total: number; success: number; failed: number; done: boolean } | null>(null)

/** 表格选择变化 */
function handleDocSelectionChange(rows: KnowledgeDocumentResp[]) {
  selectedDocIds.value = rows.map((r) => r.id)
}

/** 批量删除（二次确认，add-p3-rag-feedback-ops） */
async function handleBatchDelete() {
  if (selectedDocIds.value.length === 0) {
    ElMessage.warning('请先勾选要删除的文档')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认批量删除 ${selectedDocIds.value.length} 个文档？此操作会同步删除 ES 向量数据，不可恢复。`,
      '批量删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  batchDeleteLoading.value = true
  try {
    const resp = await batchDeleteDocuments(selectedDocIds.value)
    if (resp.failures && resp.failures.length > 0) {
      ElMessage.warning(`删除完成：成功 ${resp.successCount} 个，失败 ${resp.failures.length} 个`)
    } else {
      ElMessage.success(`批量删除完成：成功 ${resp.successCount} 个`)
    }
    selectedDocIds.value = []
    await loadDocuments()
    await loadList()
  } catch (e) {
    console.error('批量删除失败', e)
  } finally {
    batchDeleteLoading.value = false
  }
}

/** 批量重处理（异步，轮询进度，add-p3-rag-feedback-ops） */
async function handleBatchReprocess() {
  if (selectedDocIds.value.length === 0) {
    ElMessage.warning('请先勾选要重处理的文档')
    return
  }
  batchReprocessLoading.value = true
  try {
    const resp = await batchReprocessDocuments(selectedDocIds.value)
    ElMessage.success(`已提交批量重处理：${resp.queuedCount} 个文档（批次 ${resp.batchId.slice(0, 8)}）`)
    selectedDocIds.value = []
    startBatchProgressPolling(resp.batchId)
  } catch (e) {
    console.error('批量重处理提交失败', e)
  } finally {
    batchReprocessLoading.value = false
  }
}

/** 启动批量重处理进度轮询 */
function startBatchProgressPolling(batchId: string) {
  stopBatchProgressPolling()
  batchProgress.value = { batchId, total: 0, success: 0, failed: 0, done: false }
  batchProgressTimer = setInterval(async () => {
    try {
      const progress = await getBatchProgress(batchId)
      batchProgress.value = {
        batchId: progress.batchId,
        total: progress.total,
        success: progress.success,
        failed: progress.failed,
        done: progress.done,
      }
      if (progress.done) {
        stopBatchProgressPolling()
        ElMessage.success(`批量重处理完成：成功 ${progress.success}，失败 ${progress.failed}`)
        await loadDocuments()
      }
    } catch (e) {
      console.error('查询批量进度失败', e)
    }
  }, 3000)
}

function stopBatchProgressPolling() {
  if (batchProgressTimer) {
    clearInterval(batchProgressTimer)
    batchProgressTimer = null
  }
}

/** 加载文档关联问答记录（详情抽屉内调用，add-p3-rag-feedback-ops） */
async function loadRelatedChats(docId: number) {
  relatedChats.value = []
  relatedChatsLoading.value = true
  try {
    relatedChats.value = (await getRelatedChats(docId)) || []
  } catch (e) {
    console.error('加载关联问答记录失败', e)
  } finally {
    relatedChatsLoading.value = false
  }
}

/** 加载文档列表：根据 selectedKb 触发 */
async function loadDocuments() {
  if (!selectedKb.value) {
    documents.value = []
    return
  }
  docsLoading.value = true
  try {
    documents.value =
      (await listDocuments(selectedKb.value.id, {
        page: 1,
        size: 50,
        keyword: docsKeyword.value || undefined,
        status: docsStatusFilter.value || undefined,
      })) || []
  } catch (e) {
    console.error('加载文档列表失败', e)
  } finally {
    docsLoading.value = false
  }
}

/** 搜索框防抖（简单实现：用户输入后 350ms 触发） */
let searchTimer: ReturnType<typeof setTimeout> | null = null
watch(docsKeyword, () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(loadDocuments, 350)
})

/** 状态过滤变化立即触发 */
watch(docsStatusFilter, loadDocuments)

/** selectedKb 变化时自动加载文档 */
watch(selectedKb, (kb) => {
  if (kb) loadDocuments()
  else documents.value = []
})

/** 打开文档详情抽屉 */
async function openDocDetail(doc: KnowledgeDocumentResp) {
  showDocDetail.value = true
  docDetail.value = null
  docDetailLoading.value = true
  allChunks.value = []
  showAllChunks.value = false
  try {
    docDetail.value = await getDocumentDetail(doc.id)
  } catch (e) {
    console.error('加载文档详情失败', e)
  } finally {
    docDetailLoading.value = false
  }
  // 关联问答记录异步加载（add-p3-rag-feedback-ops）
  loadRelatedChats(doc.id)
}

/** 加载文档全部分片（B3） */
async function loadAllChunks(docId: number) {
  if (showAllChunks.value) {
    showAllChunks.value = false
    return
  }
  if (allChunks.value.length > 0) {
    showAllChunks.value = true
    return
  }
  chunksLoading.value = true
  try {
    allChunks.value = (await listDocumentChunks(docId)) || []
    showAllChunks.value = true
  } catch (e) {
    console.error('加载全部分片失败', e)
  } finally {
    chunksLoading.value = false
  }
}

/** 打开分片编辑对话框（B3） */
function openChunkEdit(chunk: KnowledgeChunkResp) {
  editingChunk.value = chunk
  chunkEditContent.value = chunk.content
  showChunkEdit.value = true
}

/** 保存分片编辑（同步更新 MySQL + ES 向量） */
async function submitChunkEdit() {
  if (!editingChunk.value) return
  const content = chunkEditContent.value.trim()
  if (!content) {
    ElMessage.warning('分片内容不能为空')
    return
  }
  chunkSaving.value = true
  try {
    const updated = await updateChunk(editingChunk.value.id, content)
    ElMessage.success(`分片 #${updated.chunkIndex} 已更新，向量已同步重建`)
    // 同步更新本地预览与全部分片列表
    if (docDetail.value) {
      const preview = docDetail.value.chunkPreview.find((c) => c.id === updated.id)
      if (preview) {
        preview.content = updated.content
        preview.tokenCount = updated.tokenCount
        preview.vectorId = updated.vectorId
      }
    }
    const inAll = allChunks.value.find((c) => c.id === updated.id)
    if (inAll) {
      inAll.content = updated.content
      inAll.tokenCount = updated.tokenCount
      inAll.vectorId = updated.vectorId
    }
    showChunkEdit.value = false
  } catch (e) {
    console.error('更新分片失败', e)
  } finally {
    chunkSaving.value = false
  }
}

/** 删除文档（二次确认） */
async function handleDeleteDoc(doc: KnowledgeDocumentResp) {
  try {
    await ElMessageBox.confirm(
      `确认删除文档「${doc.fileName}」？此操作会同步删除 ES 向量数据，不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return // 用户取消
  }
  docActionLoading.value[doc.id] = true
  try {
    await deleteDocument(doc.id)
    ElMessage.success('文档已删除')
    await loadDocuments()
    await loadList() // 刷新 KB 统计
    const current = knowledgeBases.value.find((k) => k.id === selectedKb.value?.id)
    if (current) selectedKb.value = current
  } catch (e) {
    console.error(e)
  } finally {
    docActionLoading.value[doc.id] = false
  }
}

/** 重新处理文档（失败重试） */
async function handleReprocessDoc(doc: KnowledgeDocumentResp) {
  docActionLoading.value[doc.id] = true
  try {
    const updated = await reprocessDocument(doc.id)
    ElMessage.success(`重处理完成，分片数 ${updated.chunkCount}`)
    await loadDocuments()
  } catch (e) {
    console.error(e)
  } finally {
    docActionLoading.value[doc.id] = false
  }
}

/** 时间格式化 */
function formatTime(iso: string): string {
  if (!iso) return '-'
  try {
    const d = new Date(iso)
    return d.toLocaleString('zh-CN', { hour12: false })
  } catch {
    return iso
  }
}

/** 加载知识库列表 */
async function loadList() {
  listLoading.value = true
  try {
    knowledgeBases.value = (await listKnowledgeBases()) || []
    if (knowledgeBases.value.length > 0 && !selectedKb.value) {
      selectedKb.value = knowledgeBases.value[0] as KnowledgeBaseResp
    }
  } catch (e) {
    console.error('加载知识库列表失败', e)
  } finally {
    listLoading.value = false
  }
}

/** 提交创建 */
async function submitCreate() {
  if (!createForm.value.name.trim()) {
    ElMessage.warning('请输入知识库名称')
    return
  }
  creating.value = true
  try {
    const kb = await createKnowledgeBase({
      name: createForm.value.name.trim(),
      description: createForm.value.description?.trim() || undefined,
    })
    ElMessage.success('知识库创建成功')
    knowledgeBases.value.unshift(kb)
    selectedKb.value = kb
    showCreate.value = false
    createForm.value = { name: '', description: '' }
  } catch (e) {
    // 错误提示已由 request 拦截器统一处理
    console.error(e)
  } finally {
    creating.value = false
  }
}

/** 自定义上传：避免 el-upload 自动 axios 调用，使用我们封装的 API */
async function customUpload(file: File) {
  if (!selectedKb.value) {
    ElMessage.warning('请先选择知识库')
    return false
  }
  uploading.value = true
  try {
    const doc = await uploadDocument(selectedKb.value.id, file)
    lastUploadedDoc.value = doc
    ElMessage.success(`文档处理完成，分片数 ${doc.chunkCount}`)
    // 刷新知识库统计
    await loadList()
    // 重新选中刚操作的 kb（loadList 会重置 selectedKb，这里恢复）
    const current = knowledgeBases.value.find((k) => k.id === selectedKb.value?.id)
    if (current) selectedKb.value = current
    // 同步刷新文档列表，让新文档立刻在表格中可见
    await loadDocuments()
  } catch (e) {
    console.error(e)
    ElMessage.error('文档上传/处理失败')
  } finally {
    uploading.value = false
  }
  return false // 阻止 el-upload 默认上传
}

/** 执行检索测试 */
async function runSearch() {
  if (!searchQuery.value.trim()) {
    ElMessage.warning('请输入检索内容')
    return
  }
  searching.value = true
  searchResults.value = []
  searchLatency.value = 0
  try {
    const res = await searchKnowledge({
      query: searchQuery.value.trim(),
      topK: searchTopK.value,
    })
    searchResults.value = res?.hits ?? []
    searchLatency.value = res?.latencyMs ?? 0
    if (searchResults.value.length === 0) {
      ElMessage.info('未检索到相关内容')
    }
  } catch (e) {
    console.error(e)
  } finally {
    searching.value = false
  }
}

/** 跳转对话页（带 kbId 启用 RAG） */
function goToChatWithRag() {
  if (!selectedKb.value) {
    ElMessage.warning('请先选择知识库')
    return
  }
  router.push({
    path: '/chat',
    query: { kbId: String(selectedKb.value.id) },
  })
}

/** 跳转关联问答所在会话（add-p3-rag-feedback-ops） */
function jumpToRelatedSession(rel: RelatedChatResp) {
  router.push({ path: '/chat', query: { sessionId: String(rel.sessionId) } })
}

/** 文件大小格式化 */
function formatSize(bytes: number): string {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return `${(bytes / Math.pow(1024, i)).toFixed(2)} ${units[i]}`
}

/** 状态标签类型 */
function statusTagType(status: string): 'success' | 'warning' | 'info' | 'danger' {
  switch (status) {
    case 'completed':
    case 'active':
      return 'success'
    case 'pending':
    case 'parsing':
    case 'vectorizing':
      return 'warning'
    case 'failed':
    case 'disabled':
      return 'danger'
    default:
      return 'info'
  }
}

/** 状态文案 */
function statusText(status: string): string {
  const map: Record<string, string> = {
    active: '启用',
    pending: '待处理',
    parsing: '解析中',
    vectorizing: '向量化中',
    completed: '已完成',
    failed: '失败',
    disabled: '已禁用',
  }
  return map[status] || status
}

onMounted(loadList)

// 组件卸载时清理批量进度轮询（add-p3-rag-feedback-ops）
onUnmounted(stopBatchProgressPolling)
</script>

<template>
  <div class="rag-view">
    <!-- ============ 顶部操作栏 ============ -->
    <div class="page-header">
      <div class="header-title">
        <h2>知识库 RAG</h2>
        <p class="header-desc">
          上传文档自动分片向量化，对话时启用 RAG 检索增强生成
        </p>
      </div>
      <div class="header-actions">
        <el-tooltip
          :content="selectedKb ? `使用「${selectedKb.name}」开启对话` : '请先选择知识库'"
          placement="top"
        >
          <el-button type="primary" :icon="ChatLineSquare" @click="goToChatWithRag">
            RAG 对话
          </el-button>
        </el-tooltip>
        <el-button type="primary" :icon="Plus" @click="showCreate = true">
          新建知识库
        </el-button>
        <el-button :icon="DataLine" @click="$router.push('/rag/feedback')">
          反馈日志
        </el-button>
      </div>
    </div>

    <!-- ============ 主体两栏布局 ============ -->
    <div class="rag-body">
      <!-- 左侧：知识库列表 -->
      <el-card class="kb-list-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>知识库列表</span>
            <el-button text :icon="Plus" @click="showCreate = true" />
          </div>
        </template>
        <div v-loading="listLoading">
          <el-empty
            v-if="knowledgeBases.length === 0"
            description="暂无知识库，点击右上角创建"
            :image-size="80"
          />
          <div
            v-for="kb in knowledgeBases"
            :key="kb.id"
            class="kb-item"
            :class="{ active: selectedKb?.id === kb.id }"
            @click="selectedKb = kb"
          >
            <div class="kb-item-name">{{ kb.name }}</div>
            <div class="kb-item-meta">
              <el-tag size="small" :type="statusTagType(kb.status)">
                {{ statusText(kb.status) }}
              </el-tag>
              <span class="meta-text">{{ kb.documentCount }} 文档 / {{ kb.chunkCount }} 分片</span>
            </div>
            <div v-if="kb.description" class="kb-item-desc">{{ kb.description }}</div>
          </div>
        </div>
      </el-card>

      <!-- 右侧：详情面板 -->
      <div class="kb-detail">
        <el-card v-if="selectedKb" shadow="never">
          <template #header>
            <div class="card-header">
              <span>{{ selectedKb.name }}</span>
              <el-button type="primary" :icon="UploadFilled" @click="showUpload = true">
                上传文档
              </el-button>
            </div>
          </template>

          <div class="kb-stats">
            <div class="stat-item">
              <div class="stat-label">文档数</div>
              <div class="stat-value">{{ selectedKb.documentCount }}</div>
            </div>
            <div class="stat-item">
              <div class="stat-label">分片数</div>
              <div class="stat-value">{{ selectedKb.chunkCount }}</div>
            </div>
            <div class="stat-item">
              <div class="stat-label">分片大小</div>
              <div class="stat-value">{{ selectedKb.chunkSize }} 字符</div>
            </div>
            <div class="stat-item">
              <div class="stat-label">重叠</div>
              <div class="stat-value">{{ selectedKb.chunkOverlap }} 字符</div>
            </div>
            <div class="stat-item">
              <div class="stat-label">Embedding 模型</div>
              <div class="stat-value">{{ selectedKb.embeddingModel }}</div>
            </div>
          </div>

          <!-- 上传结果反馈 -->
          <div v-if="lastUploadedDoc" class="upload-result">
            <el-tag :type="statusTagType(lastUploadedDoc.status)">
              {{ statusText(lastUploadedDoc.status) }}
            </el-tag>
            <span class="upload-result-name">{{ lastUploadedDoc.fileName }}</span>
            <span class="upload-result-meta">
              {{ formatSize(lastUploadedDoc.fileSize) }} · {{ lastUploadedDoc.chunkCount }} 分片
            </span>
            <span v-if="lastUploadedDoc.errorMessage" class="upload-result-error">
              {{ lastUploadedDoc.errorMessage }}
            </span>
          </div>

          <!-- ============ 文档管理（P0-6） ============ -->
          <el-divider content-position="left">文档列表</el-divider>
          <div class="docs-toolbar">
            <el-input
              v-model="docsKeyword"
              placeholder="按文件名搜索"
              clearable
              :prefix-icon="Search"
              style="width: 240px"
            />
            <el-select
              v-model="docsStatusFilter"
              placeholder="状态筛选"
              clearable
              style="width: 140px"
            >
              <el-option label="已完成" value="completed" />
              <el-option label="待处理" value="pending" />
              <el-option label="解析中" value="parsing" />
              <el-option label="向量化中" value="vectorizing" />
              <el-option label="失败" value="failed" />
            </el-select>
            <el-button :icon="Refresh" text @click="loadDocuments">刷新</el-button>
            <!-- 批量操作（add-p3-rag-feedback-ops） -->
            <el-button
              type="danger"
              text
              :icon="Delete"
              :loading="batchDeleteLoading"
              :disabled="selectedDocIds.length === 0"
              @click="handleBatchDelete"
            >
              批量删除{{ selectedDocIds.length > 0 ? `(${selectedDocIds.length})` : '' }}
            </el-button>
            <el-button
              type="warning"
              text
              :icon="Refresh"
              :loading="batchReprocessLoading"
              :disabled="selectedDocIds.length === 0"
              @click="handleBatchReprocess"
            >
              批量重处理{{ selectedDocIds.length > 0 ? `(${selectedDocIds.length})` : '' }}
            </el-button>
          </div>

          <!-- 批量重处理进度条（add-p3-rag-feedback-ops） -->
          <el-alert
            v-if="batchProgress && !batchProgress.done"
            :title="`批量重处理进行中：成功 ${batchProgress.success} / 失败 ${batchProgress.failed} / 共 ${batchProgress.total}`"
            type="info"
            :closable="false"
            show-icon
            style="margin-top: 8px"
          />

          <el-table
            v-loading="docsLoading"
            :data="documents"
            empty-text="该知识库暂无文档"
            size="small"
            style="margin-top: 12px"
            @selection-change="handleDocSelectionChange"
          >
            <el-table-column type="selection" width="40" />
            <el-table-column prop="fileName" label="文件名" min-width="180" show-overflow-tooltip />
            <el-table-column label="大小" width="90">
              <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
            </el-table-column>
            <el-table-column label="分片数" width="80" prop="chunkCount" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="statusTagType(row.status)">
                  {{ statusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="上传时间" width="160">
              <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button
                  text
                  type="primary"
                  size="small"
                  :icon="View"
                  @click="openDocDetail(row as KnowledgeDocumentResp)"
                >
                  详情
                </el-button>
                <el-button
                  v-if="(row as KnowledgeDocumentResp).status === 'failed'"
                  text
                  type="warning"
                  size="small"
                  :icon="Refresh"
                  :loading="docActionLoading[(row as KnowledgeDocumentResp).id]"
                  @click="handleReprocessDoc(row as KnowledgeDocumentResp)"
                >
                  重试
                </el-button>
                <el-button
                  text
                  type="danger"
                  size="small"
                  :icon="Delete"
                  :loading="docActionLoading[(row as KnowledgeDocumentResp).id]"
                  @click="handleDeleteDoc(row as KnowledgeDocumentResp)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <!-- 检索测试 -->
          <el-divider content-position="left">检索测试</el-divider>
          <div class="search-box">
            <el-input
              v-model="searchQuery"
              placeholder="输入检索查询，验证 RAG 召回效果"
              type="textarea"
              :rows="2"
              @keydown.ctrl.enter="runSearch"
            />
            <div class="search-controls">
              <span class="topk-label">TopK：{{ searchTopK }}</span>
              <input
                type="range"
                min="1"
                max="10"
                v-model.number="searchTopK"
                class="topk-slider"
              />
              <el-button
                type="primary"
                :icon="Search"
                :loading="searching"
                @click="runSearch"
              >
                检索
              </el-button>
            </div>
          </div>

          <div v-if="searchResults.length > 0" class="search-results">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; font-size: 12px; color: var(--text-secondary);">
              <span>共 {{ searchResults.length }} 条命中</span>
              <span v-if="searchLatency">耗时 {{ searchLatency }}ms</span>
            </div>
            <div v-for="(hit, idx) in searchResults" :key="idx" class="result-item">
              <div class="result-header">
                <span class="result-index">#{{ idx + 1 }}</span>
                <el-tag v-if="hit.score" size="small" :type="hit.score >= 0.8 ? 'success' : hit.score >= 0.5 ? 'warning' : 'info'">
                  {{ (hit.score * 100).toFixed(1) }}%
                </el-tag>
                <span v-if="hit.documentName" style="font-size: 12px; color: var(--text-tertiary); margin-left: auto;">
                  {{ hit.documentName }}{{ hit.chunkIndex != null ? ` #${hit.chunkIndex}` : '' }}
                </span>
              </div>
              <div class="result-text">{{ hit.content }}</div>
            </div>
          </div>
        </el-card>

        <el-empty v-else description="请从左侧选择或创建知识库" />
      </div>
    </div>

    <!-- ============ 创建知识库对话框 ============ -->
    <el-dialog
      v-model="showCreate"
      title="新建知识库"
      width="460px"
      :close-on-click-modal="false"
      align-center
    >
      <el-form label-position="top">
        <el-form-item label="名称" required>
          <el-input
            v-model="createForm.name"
            placeholder="例如：产品文档知识库"
            maxlength="128"
            show-word-limit
            clearable
          />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="3"
            placeholder="可选，简要描述知识库内容范围"
            maxlength="512"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">
          创建
        </el-button>
      </template>
    </el-dialog>

    <!-- ============ 上传文档对话框 ============ -->
    <el-dialog
      v-model="showUpload"
      :title="`上传文档到「${selectedKb?.name || ''}」`"
      width="540px"
      :close-on-click-modal="false"
      align-center
    >
      <el-upload
        drag
        :auto-upload="true"
        :show-file-list="false"
        :http-request="(options: UploadRequestOptions) => customUpload(options.file)"
        accept=".pdf,.docx,.doc,.md,.html,.htm,.txt"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">
          拖拽文件到此处，或<em>点击选择</em>
        </div>
        <template #tip>
          <div class="upload-tip">
            支持 PDF / Word / Markdown / HTML / 纯文本，单文件 ≤ 50MB。
            上传后将自动解析、分片、向量化，请耐心等待。
          </div>
        </template>
      </el-upload>
      <el-progress
        v-if="uploading"
        :percentage="100"
        :status="uploading ? undefined : 'success'"
        :indeterminate="true"
        :show-text="true"
        :text="'向量化处理中…'"
      />
    </el-dialog>

    <!-- ============ 文档详情抽屉（P0-6） ============ -->
    <el-drawer
      v-model="showDocDetail"
      title="文档详情"
      direction="rtl"
      size="520px"
      :close-on-click-modal="true"
    >
      <div v-loading="docDetailLoading" class="doc-detail-body">
        <template v-if="docDetail">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="文件名">
              {{ docDetail.document.fileName }}
            </el-descriptions-item>
            <el-descriptions-item label="文件类型">
              {{ docDetail.document.fileType }}
            </el-descriptions-item>
            <el-descriptions-item label="文件大小">
              {{ formatSize(docDetail.document.fileSize) }}
            </el-descriptions-item>
            <el-descriptions-item label="分片数">
              {{ docDetail.document.chunkCount }}
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag size="small" :type="statusTagType(docDetail.document.status)">
                {{ statusText(docDetail.document.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="SHA-256">
              <span class="hash-text">{{ docDetail.document.contentHash || '-' }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="上传时间">
              {{ formatTime(docDetail.document.createTime) }}
            </el-descriptions-item>
            <el-descriptions-item v-if="docDetail.document.errorMessage" label="错误信息">
              <span class="error-text">{{ docDetail.document.errorMessage }}</span>
            </el-descriptions-item>
          </el-descriptions>

          <el-divider content-position="left">
            分片预览（前 5 条）
            <el-button
              text
              type="primary"
              size="small"
              class="divider-action"
              :loading="chunksLoading"
              @click="loadAllChunks(docDetail.document.id)"
            >
              {{ showAllChunks ? '收起全部分片' : '查看全部分片' }}
            </el-button>
          </el-divider>
          <div
            v-for="chunk in docDetail.chunkPreview"
            :key="chunk.id"
            class="chunk-item"
          >
            <div class="chunk-head">
              <el-tag size="small" type="info">#{{ chunk.chunkIndex }}</el-tag>
              <span class="chunk-tokens">{{ chunk.tokenCount }} tokens</span>
              <el-button
                text
                type="primary"
                size="small"
                class="chunk-edit-btn"
                @click="openChunkEdit(chunk)"
              >
                编辑
              </el-button>
            </div>
            <pre class="chunk-content">{{ chunk.content }}</pre>
          </div>

          <!-- 全部分片（B3：查看/编辑） -->
          <template v-if="showAllChunks">
            <el-divider content-position="left">全部分片（{{ allChunks.length }} 条）</el-divider>
            <div
              v-for="chunk in allChunks"
              :key="chunk.id"
              class="chunk-item"
            >
              <div class="chunk-head">
                <el-tag size="small" type="info">#{{ chunk.chunkIndex }}</el-tag>
                <span class="chunk-tokens">{{ chunk.tokenCount }} tokens</span>
                <el-button
                  text
                  type="primary"
                  size="small"
                  class="chunk-edit-btn"
                  @click="openChunkEdit(chunk)"
                >
                  编辑
                </el-button>
              </div>
              <pre class="chunk-content">{{ chunk.content }}</pre>
            </div>
          </template>

          <!-- 关联问答记录（add-p3-rag-feedback-ops：该文档被哪些会话命中引用） -->
          <el-divider content-position="left">关联问答记录（Top 20）</el-divider>
          <div v-loading="relatedChatsLoading">
            <el-empty
              v-if="!relatedChatsLoading && relatedChats.length === 0"
              description="暂无会话引用该文档"
              :image-size="50"
            />
            <div
              v-for="rel in relatedChats"
              :key="rel.messageId"
              class="related-chat-item clickable"
              title="点击跳转到该会话"
              @click="jumpToRelatedSession(rel)"
            >
              <div class="related-chat-head">
                <el-tag size="small" :type="rel.role === 'user' ? 'info' : 'success'">
                  {{ rel.role }}
                </el-tag>
                <span class="related-chat-session">#{{ rel.sessionId }} {{ rel.sessionTitle }}</span>
                <span class="related-chat-time">{{ formatTime(rel.hitTime) }}</span>
              </div>
              <div v-if="rel.querySnippet" class="related-chat-query">
                提问：{{ rel.querySnippet }}
              </div>
              <div class="related-chat-chunk">命中片段：{{ rel.chunkContent }}</div>
            </div>
          </div>
        </template>
        <el-empty
          v-else-if="!docDetailLoading"
          description="未加载到文档详情"
          :image-size="60"
        />
      </div>
    </el-drawer>

    <!-- 分片编辑对话框（B3） -->
    <el-dialog
      v-model="showChunkEdit"
      :title="editingChunk ? `编辑分片 #${editingChunk.chunkIndex}` : '编辑分片'"
      width="640px"
      :close-on-click-modal="false"
    >
      <div v-if="editingChunk" class="chunk-edit-meta">
        <el-tag size="small" type="info">#{{ editingChunk.chunkIndex }}</el-tag>
        <span class="chunk-edit-tip">
          保存后将同步重建 ES 向量并失效该知识库的 RAG 缓存。
        </span>
      </div>
      <el-input
        v-model="chunkEditContent"
        type="textarea"
        :rows="12"
        resize="vertical"
        placeholder="输入分片内容"
      />
      <template #footer>
        <el-button @click="showChunkEdit = false">取消</el-button>
        <el-button type="primary" :loading="chunkSaving" @click="submitChunkEdit">
          保存并重建向量
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.rag-view {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

/* 顶部 */
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
}
.header-title h2 {
  margin: 0;
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
}
.header-desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--text-tertiary);
}
.header-actions {
  display: flex;
  gap: var(--space-2);
  flex-shrink: 0;
}

/* 主体 */
.rag-body {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: var(--space-4);
}
@media (max-width: 1024px) {
  .rag-body {
    grid-template-columns: 1fr;
  }
}

/* 知识库列表 */
.kb-list-card :deep(.el-card__body) {
  padding: 8px;
  max-height: 600px;
  overflow-y: auto;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: var(--font-weight-semibold);
}
.kb-item {
  padding: 12px 14px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background-color var(--transition-fast);
  border: 1px solid transparent;
}
.kb-item:hover {
  background-color: var(--bg-subtle);
}
.kb-item.active {
  background-color: var(--color-primary-bg, #eef2ff);
  border-color: var(--color-primary);
}
.kb-item-name {
  font-size: 14px;
  font-weight: var(--font-weight-medium);
  color: var(--text-primary);
  margin-bottom: 4px;
}
.kb-item-meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 12px;
  color: var(--text-tertiary);
}
.meta-text {
  font-size: 12px;
}
.kb-item-desc {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.5;
  display: box;
  line-clamp: 2;
  box-orient: vertical;
  overflow: hidden;
}

/* 详情面板统计 */
.kb-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}
.stat-item {
  background: var(--bg-subtle);
  padding: 12px;
  border-radius: var(--radius-md);
  text-align: center;
}
.stat-label {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-bottom: 4px;
}
.stat-value {
  font-size: 18px;
  font-weight: var(--font-weight-semibold);
  color: var(--text-primary);
}

/* 上传结果 */
.upload-result {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 10px 12px;
  background: var(--bg-subtle);
  border-radius: var(--radius-md);
  font-size: 13px;
  margin-top: var(--space-2);
  flex-wrap: wrap;
}
.upload-result-name {
  font-weight: var(--font-weight-medium);
}
.upload-result-meta {
  color: var(--text-tertiary);
}
.upload-result-error {
  color: var(--color-danger);
  width: 100%;
}

/* 检索测试 */
.search-box {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}
.search-controls {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}
.topk-label {
  font-size: 13px;
  color: var(--text-secondary);
  white-space: nowrap;
}
.topk-slider {
  flex: 1;
  max-width: 200px;
}

/* 检索结果 */
.search-results {
  margin-top: var(--space-3);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}
.result-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--bg-card);
}
.result-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.result-index {
  font-size: 12px;
  font-weight: var(--font-weight-semibold);
  color: var(--color-primary);
  flex-shrink: 0;
  width: 28px;
}
.result-text {
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 180px;
  overflow-y: auto;
}

.upload-tip {
  font-size: 12px;
  color: var(--text-tertiary);
  line-height: 1.5;
}

/* 文档管理（P0-6） */
.docs-toolbar {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  flex-wrap: wrap;
}

.doc-detail-body {
  padding: 0 var(--space-3);
}

.hash-text {
  font-family: var(--font-mono, 'JetBrains Mono', Consolas, monospace);
  font-size: 11px;
  word-break: break-all;
  color: var(--text-tertiary);
}

.error-text {
  color: var(--el-color-danger);
  font-size: 12px;
  word-break: break-word;
}

.chunk-item {
  margin-bottom: 12px;
  border: 1px solid var(--border-light, #ebeef5);
  border-radius: 4px;
  overflow: hidden;
}

.chunk-head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 6px 10px;
  background: var(--bg-secondary, #f5f7fa);
  font-size: 12px;
  color: var(--text-tertiary);
}

.chunk-tokens {
  margin-left: auto;
}

/* B3 分片管理 */
.divider-action {
  margin-left: 8px;
}
.chunk-edit-btn {
  margin-left: 8px;
}
.chunk-edit-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 12px;
  color: var(--text-tertiary);
}
.chunk-edit-tip {
  line-height: 1.5;
}

.chunk-content {
  margin: 0;
  padding: 8px 10px;
  font-family: var(--font-mono, 'JetBrains Mono', Consolas, monospace);
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow-y: auto;
}

/* ============ 关联问答记录（add-p3-rag-feedback-ops） ============ */
.related-chat-item {
  border: 1px solid var(--border-light, #e4e7ed);
  border-radius: var(--radius-md, 6px);
  margin-bottom: 8px;
  overflow: hidden;
}

.related-chat-item.clickable {
  cursor: pointer;
  transition: border-color 0.2s;
}

.related-chat-item.clickable:hover {
  border-color: var(--color-primary, #409eff);
}

.related-chat-head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 6px 10px;
  background: var(--bg-secondary, #f5f7fa);
  font-size: 12px;
  color: var(--text-tertiary);
}

.related-chat-session {
  font-weight: 500;
  color: var(--text-primary);
}

.related-chat-time {
  margin-left: auto;
}

.related-chat-query,
.related-chat-chunk {
  padding: 6px 10px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-secondary);
  border-top: 1px dashed var(--border-light, #e4e7ed);
  word-break: break-word;
}
</style>
