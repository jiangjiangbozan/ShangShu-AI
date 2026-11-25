<template>
  <div class="run-detail">
    <!-- 顶部信息栏 -->
    <div class="run-detail__top">
      <div class="run-detail__top-left">
        <button class="run-detail__back" @click="goBack">
          <el-icon :size="16"><ArrowLeft /></el-icon>
        </button>
        <div class="run-detail__meta">
          <div class="run-detail__runid">
            执行记录 <code>{{ runId }}</code>
          </div>
          <div class="run-detail__sub">
            <span class="run-detail__chip" :class="'is-' + (run?.status || '').toLowerCase()">
              <span class="run-detail__chip-dot" />
              {{ statusLabel(run?.status || '') }}
            </span>
            <span v-if="run?.elapsedMs" class="run-detail__sub-item"
              >耗时 {{ (run.elapsedMs / 1000).toFixed(1) }}s</span
            >
            <span v-if="run?.totalTokens" class="run-detail__sub-item"
              >Token {{ run.totalTokens }}</span
            >
            <span v-if="run?.createdAt" class="run-detail__sub-item">开始 {{ run.createdAt }}</span>
          </div>
        </div>
      </div>
      <el-button :icon="Refresh" @click="loadData" :loading="loading">刷新</el-button>
    </div>

    <div class="run-detail__body">
      <!-- 中间 DAG 画布 -->
      <div class="run-detail__canvas">
        <VueFlow
          v-model:nodes="flowNodes"
          v-model:edges="flowEdges"
          :fit-view-on-init="true"
          :min-zoom="0.1"
          :max-zoom="2"
          :nodes-draggable="false"
          :nodes-connectable="false"
          :elements-selectable="true"
          @node-click="onNodeClick"
        >
          <Background :gap="16" :size="1.5" pattern-color="#d0d5dd" />
          <MiniMap position="bottom-left" :pannable="true" :zoomable="true" />
          <Controls position="bottom-right" />
        </VueFlow>
      </div>

      <!-- 右侧节点详情面板 -->
      <el-drawer v-model="drawerVisible" title="节点详情" direction="rtl" size="420px">
        <template v-if="selectedNode">
          <el-descriptions :column="1" border>
            <el-descriptions-item label="节点 ID">{{ selectedNode.nodeId }}</el-descriptions-item>
            <el-descriptions-item label="节点类型">{{
              selectedNode.nodeType
            }}</el-descriptions-item>
            <el-descriptions-item label="标题">{{
              selectedNode.title || '-'
            }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <span
                class="run-detail__chip"
                :class="'is-' + (selectedNode.status || '').toLowerCase()"
              >
                <span class="run-detail__chip-dot" />
                {{ statusLabel(selectedNode.status) }}
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="耗时">
              {{ selectedNode.elapsedMs != null ? selectedNode.elapsedMs + ' ms' : '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="Token">
              {{ selectedNode.tokens ?? '-' }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="run-detail__section">
            <div class="run-detail__section-title">输入 (Inputs)</div>
            <pre class="run-detail__code">{{ formatJson(selectedNode.inputs) }}</pre>
          </div>

          <div class="run-detail__section">
            <div class="run-detail__section-title">输出 (Outputs)</div>
            <pre class="run-detail__code">{{ formatJson(selectedNode.outputs) }}</pre>
          </div>

          <div v-if="selectedNode.errorMessage" class="run-detail__section">
            <div class="run-detail__section-title run-detail__error">错误信息</div>
            <pre class="run-detail__code run-detail__error-code">{{
              selectedNode.errorMessage
            }}</pre>
          </div>
        </template>
      </el-drawer>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Refresh } from '@element-plus/icons-vue'
import { VueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import { getWorkflowRunDetail, type NodeRunItem, type WorkflowRunItem } from '@/api/workflow-runs'
import { getWorkflowVersion } from '@/api/workflow'

const route = useRoute()
const router = useRouter()
const runId = computed(() => route.params.runId as string)

const loading = ref(false)
const run = ref<WorkflowRunItem | null>(null)
const nodes = ref<NodeRunItem[]>([])
const drawerVisible = ref(false)
const selectedNode = ref<NodeRunItem | null>(null)

/** 编排器保存的 DSL 节点定义（position 为画布原始坐标） */
interface GraphNodeDef {
  id: string
  type?: string
  position?: { x: number; y: number }
  data?: { type?: string; title?: string; desc?: string }
}

/** 编排器保存的 DSL 边定义 */
interface GraphEdgeDef {
  id: string
  source: string
  target: string
}

/** 运行版本对应的发布快照 graph（无快照/解析失败为 null，回退按执行顺序布局） */
const graphDsl = ref<{ nodes: GraphNodeDef[]; edges: GraphEdgeDef[] } | null>(null)

interface FlowNode {
  id: string
  label: string
  position: { x: number; y: number }
  style?: Record<string, string>
  data: NodeRunItem | null
}

interface FlowEdge {
  id: string
  source: string
  target: string
}

/** nodeId → 节点执行记录索引（快照模式下画布包含未执行节点） */
const statusMap = computed<Map<string, NodeRunItem>>(() => {
  const map = new Map<string, NodeRunItem>()
  for (const item of nodes.value) map.set(item.nodeId, item)
  return map
})

/**
 * 把画布节点集合转换为 Vue Flow nodes。
 *
 * 有版本快照时按原始画布坐标与连线渲染（与编排视图一致）；否则回退为按执行顺序两列布局。
 */
const flowNodes = computed<FlowNode[]>(() => {
  if (graphDsl.value) {
    return graphDsl.value.nodes.map((n) => {
      const record = statusMap.value.get(n.id)
      const colors = nodeColors(record?.status ?? '')
      const title = n.data?.title || n.type || n.id
      const elapsed = record?.elapsedMs != null ? `\n${record.elapsedMs}ms` : ''
      return {
        id: n.id,
        label: title + elapsed,
        position: n.position ?? { x: 0, y: 0 },
        style: {
          border: `2px solid ${colors.border}`,
          background: colors.bg,
          borderRadius: '8px',
          padding: '10px 14px',
          fontSize: '13px',
          whiteSpace: 'pre-line',
        },
        data: record ?? null,
      }
    })
  }
  // 回退布局：按执行顺序从上到下排列
  const NODE_H = 60
  const GAP = 30
  const startY = 50
  return nodes.value.map((n, idx) => {
    const colors = nodeColors(n.status)
    return {
      id: n.nodeId,
      label: (n.title || n.nodeType) + '\n' + (n.elapsedMs ?? 0) + 'ms',
      position: { x: 120 + (idx % 2) * 260, y: startY + Math.floor(idx / 2) * (NODE_H + GAP) },
      style: {
        border: `2px solid ${colors.border}`,
        background: colors.bg,
        borderRadius: '8px',
        padding: '10px 14px',
        fontSize: '13px',
        whiteSpace: 'pre-line',
      },
      data: n,
    }
  })
})

const flowEdges = computed<FlowEdge[]>(() => {
  if (graphDsl.value) {
    return graphDsl.value.edges.map((e) => ({
      id: e.id || `e-${e.source}-${e.target}`,
      source: e.source,
      target: e.target,
    }))
  }
  // 回退：按执行顺序连线 node[i] → node[i+1]
  const edges: FlowEdge[] = []
  for (let i = 0; i < nodes.value.length - 1; i++) {
    const a = nodes.value[i]!
    const b = nodes.value[i + 1]!
    edges.push({
      id: `e-${a.nodeId}-${b.nodeId}`,
      source: a.nodeId,
      target: b.nodeId,
    })
  }
  return edges
})

function nodeColors(status: string) {
  switch (status?.toUpperCase()) {
    case 'SUCCEEDED':
      return { border: '#22c55e', bg: '#f0fdf4' }
    case 'FAILED':
      return { border: '#ef4444', bg: '#fef2f2' }
    case 'RUNNING':
      return { border: '#f59e0b', bg: '#fffbeb' }
    case 'SKIPPED':
      return { border: '#cbd5e1', bg: '#f8fafc' }
    default:
      return { border: '#94a3b8', bg: '#f8fafc' }
  }
}

function statusLabel(status: string) {
  switch (status?.toUpperCase()) {
    case 'SUCCEEDED':
      return '成功'
    case 'FAILED':
      return '失败'
    case 'RUNNING':
      return '运行中'
    case 'SKIPPED':
      return '已跳过'
    default:
      return status || '未执行'
  }
}

function formatJson(raw: unknown) {
  if (raw == null || raw === '') return '(空)'
  try {
    const obj = typeof raw === 'string' ? JSON.parse(raw) : raw
    return JSON.stringify(obj, null, 2)
  } catch {
    return String(raw)
  }
}

function onNodeClick({ node }: { node: { data: unknown } }) {
  const record = node.data as NodeRunItem | null
  if (!record) return
  selectedNode.value = record
  drawerVisible.value = true
}

function goBack() {
  router.push('/workflow/runs')
}

async function loadData() {
  if (!runId.value) return
  loading.value = true
  try {
    const res = await getWorkflowRunDetail(runId.value)
    run.value = res.run
    nodes.value = res.nodes || []
    await loadGraphSnapshot(res.run)
  } catch (err) {
    ElMessage.error('加载执行详情失败: ' + (err instanceof Error ? err.message : String(err)))
  } finally {
    loading.value = false
  }
}

/** 加载运行版本的发布快照 graph；无快照（历史数据）或解析失败时保持 null 走回退布局。 */
async function loadGraphSnapshot(runItem: WorkflowRunItem) {
  graphDsl.value = null
  if (!runItem?.workflowId || !runItem?.version) return
  try {
    const version = await getWorkflowVersion(runItem.workflowId, runItem.version)
    const dsl = JSON.parse(version.graph) as { nodes?: GraphNodeDef[]; edges?: GraphEdgeDef[] }
    if (Array.isArray(dsl?.nodes) && Array.isArray(dsl?.edges)) {
      graphDsl.value = { nodes: dsl.nodes, edges: dsl.edges }
    }
  } catch {
    // 静默回退：V20261122 之前的历史 run 没有版本快照
  }
}

watch(runId, () => {
  if (runId.value) loadData()
})

onMounted(loadData)
</script>

<style scoped>
.run-detail {
  display: flex;
  flex-direction: column;
  /* 布局：顶栏 --header-height + 内容区上下各 --space-6 内边距 */
  height: calc(100vh - var(--header-height) - var(--space-6) * 2);
  background: #f7f8fc;
}
.run-detail__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 24px;
  background: #fff;
  border-bottom: 1px solid #eef0f4;
  flex-shrink: 0;
}
.run-detail__top-left {
  display: flex;
  align-items: center;
  gap: 14px;
}
.run-detail__back {
  width: 34px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #e5e7eb;
  background: #fff;
  border-radius: 10px;
  color: #4b5563;
  cursor: pointer;
  transition:
    border-color 0.15s,
    color 0.15s,
    background 0.15s;
}
.run-detail__back:hover {
  border-color: #c7d2fe;
  color: #4f46e5;
  background: #eef2ff;
}
.run-detail__meta {
  flex: 1;
}
.run-detail__runid {
  font-size: 14px;
  font-weight: 700;
  color: #111827;
}
.run-detail__runid code {
  background: #f4f5fb;
  border: 1px solid #eef0f4;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 12px;
  color: #4f46e5;
  font-family: var(--font-mono);
}
.run-detail__sub {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 6px;
  font-size: 12px;
  color: #6b7280;
}
.run-detail__sub-item {
  font-variant-numeric: tabular-nums;
}
.run-detail__chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 9px;
  border-radius: 999px;
  line-height: 18px;
}
.run-detail__chip-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}
.run-detail__chip.is-succeeded {
  color: #059669;
  background: #ecfdf5;
}
.run-detail__chip.is-failed {
  color: #dc2626;
  background: #fef2f2;
}
.run-detail__chip.is-running {
  color: #d97706;
  background: #fffbeb;
}
.run-detail__chip.is-paused {
  color: #6b7280;
  background: #f3f4f6;
}
.run-detail__chip:not(.is-succeeded):not(.is-failed):not(.is-running):not(.is-paused) {
  color: #6b7280;
  background: #f3f4f6;
}
.run-detail__body {
  flex: 1;
  position: relative;
  padding: 16px;
}
.run-detail__canvas {
  width: 100%;
  height: 100%;
  background: #fff;
  border: 1px solid #eef0f4;
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(17, 24, 39, 0.04);
}
.run-detail__canvas :deep(.vue-flow__minimap) {
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 4px 16px -2px rgba(17, 24, 39, 0.1);
  border: 1px solid #e8eaf2;
}
.run-detail__section {
  margin-top: 16px;
}
.run-detail__section-title {
  font-weight: 700;
  font-size: 13px;
  margin-bottom: 6px;
  color: #374151;
}
.run-detail__section-title.run-detail__error {
  color: #dc2626;
}
.run-detail__code {
  background: #111827;
  color: #e5e7eb;
  padding: 12px 14px;
  border-radius: 10px;
  font-size: 12px;
  line-height: 1.6;
  max-height: 200px;
  overflow: auto;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}
.run-detail__error-code {
  background: #fef2f2;
  color: #991b1b;
  border: 1px solid #fecaca;
}
</style>
