<template>
  <div class="wf-editor">
    <!-- 左侧节点面板 -->
    <NodePanel />

    <!-- 中间画布 -->
    <div class="wf-editor__canvas" @dragover="onDragOver" @drop="onDrop">
      <VueFlow
        v-model:nodes="nodes"
        v-model:edges="edges"
        :node-types="nodeTypes"
        :default-edge-options="defaultEdgeOptions"
        :edge-types="edgeTypes"
        :fit-view-on-init="true"
        :snap-to-grid="true"
        :snap-grid="[16, 16]"
        :min-zoom="0.1"
        :max-zoom="2"
        :delete-key-code="'Delete'"
        @node-click="onNodeClick"
        @pane-click="onPaneClick"
        @connect="onConnect"
        @nodes-change="onNodesChange"
        @viewport-change="onViewportChange"
      >
        <Background :gap="16" :size="1.6" pattern-color="#d4d9ec" />
        <MiniMap position="bottom-left" :pannable="true" :zoomable="true" />
      </VueFlow>

      <!-- 顶部左侧：返回 + 名称（悬浮玻璃卡片） -->
      <div class="wf-toolbar wf-toolbar--left">
        <el-tooltip content="返回列表" placement="bottom">
          <button class="wf-toolbar__icon-btn" @click="goBack">
            <el-icon :size="16"><ArrowLeft /></el-icon>
          </button>
        </el-tooltip>
        <div class="wf-toolbar__divider" />
        <div class="wf-toolbar__name-wrap">
          <input
            v-model="store.workflowName"
            class="wf-toolbar__name-input"
            placeholder="未命名工作流"
            @change="store.dirty = true"
          />
          <span v-if="store.dirty" class="wf-toolbar__dirty">
            <span class="wf-toolbar__dirty-dot" />未保存
          </span>
        </div>
      </div>

      <!-- 顶部右侧：操作区（悬浮玻璃卡片） -->
      <div class="wf-toolbar wf-toolbar--right">
        <div class="wf-toolbar__group">
          <el-tooltip content="撤销" placement="bottom">
            <button class="wf-toolbar__icon-btn" :disabled="!canUndo" @click="handleUndo">
              <el-icon :size="16"><RefreshLeft /></el-icon>
            </button>
          </el-tooltip>
          <el-tooltip content="重做" placement="bottom">
            <button class="wf-toolbar__icon-btn" :disabled="!canRedo" @click="handleRedo">
              <el-icon :size="16"><RefreshRight /></el-icon>
            </button>
          </el-tooltip>
        </div>
        <div class="wf-toolbar__divider" />
        <div class="wf-toolbar__group">
          <el-tooltip content="运行历史" placement="bottom">
            <button class="wf-toolbar__icon-btn" @click="showRunHistory">
              <el-icon :size="16"><Clock /></el-icon>
            </button>
          </el-tooltip>
        </div>
        <div class="wf-toolbar__divider" />
        <div class="wf-toolbar__group wf-toolbar__group--actions">
          <el-button class="wf-toolbar__btn" @click="handleSave" :loading="saving">
            <el-icon v-if="!saving"><Check /></el-icon>保存
          </el-button>
          <el-button class="wf-toolbar__btn wf-toolbar__btn--publish" @click="handlePublish" :loading="publishing">
            <el-icon v-if="!publishing"><Upload /></el-icon>发布
          </el-button>
          <el-button class="wf-toolbar__btn wf-toolbar__btn--run" @click="handleRun" :loading="store.running">
            <el-icon v-if="!store.running"><VideoPlay /></el-icon>
            <span>{{ store.running ? '运行中' : '运行' }}</span>
          </el-button>
        </div>
      </div>

      <!-- 缩放控制（右下角胶囊） -->
      <div class="wf-editor__zoom-controls">
        <button class="wf-zoom__btn" @click="zoomOut" title="缩小">
          <el-icon :size="15"><ZoomOut /></el-icon>
        </button>
        <button class="wf-zoom__percent" @click="fitView" title="适应画布">{{ zoomPercent }}%</button>
        <button class="wf-zoom__btn" @click="zoomIn" title="放大">
          <el-icon :size="15"><ZoomIn /></el-icon>
        </button>
        <div class="wf-toolbar__divider" />
        <button class="wf-zoom__btn" @click="fitView" title="适应画布">
          <el-icon :size="15"><FullScreen /></el-icon>
        </button>
      </div>
    </div>

    <!-- 运行输入对话框 -->
    <el-dialog
      v-model="runInputVisible"
      title="运行工作流"
      width="520px"
      append-to-body
      class="wf-run-dialog"
      :close-on-click-modal="false"
    >
      <div class="wf-run-dialog__desc">
        <el-icon><InfoFilled /></el-icon>
        以下变量将作为工作流运行的输入参数
      </div>
      <el-form label-position="top" class="wf-run-dialog__form">
        <template v-if="runInputVars.length">
          <el-form-item
            v-for="v in runInputVars"
            :key="v.variable"
            :label="v.label || v.variable"
            :required="v.required"
          >
            <el-input
              v-model="runInputValues[v.variable]"
              :placeholder="v.required ? '必填' : '选填'"
            />
          </el-form-item>
        </template>
        <el-alert v-else type="info" :closable="false" description="该工作流无输入变量，可直接运行" />
      </el-form>
      <template #footer>
        <el-button @click="runInputVisible = false">取消</el-button>
        <el-button type="primary" class="wf-run-dialog__submit" @click="confirmRun" :loading="store.running">
          <el-icon v-if="!store.running"><VideoPlay /></el-icon>开始运行
        </el-button>
      </template>
    </el-dialog>

    <!-- 右侧配置面板（固定面板，非 Drawer） -->
    <Transition name="slide-right">
      <NodeConfigPanel v-if="store.selectedNodeId" />
    </Transition>

    <!-- 运行历史面板 -->
    <RunHistoryPanel ref="runHistoryPanel" />

    <!-- 运行结果面板（底部） -->
    <Transition name="slide-up">
      <div v-if="store.runEvents.length || store.running" class="wf-run-panel">
        <div class="wf-run-panel__header">
          <div class="wf-run-panel__header-left">
            <span class="wf-run-panel__title">运行日志</span>
            <span v-if="store.running" class="wf-run-panel__chip is-running">
              <span class="wf-run-panel__chip-dot" />运行中
            </span>
            <span v-else-if="lastEventIsSuccess" class="wf-run-panel__chip is-success">
              <el-icon :size="11"><Check /></el-icon>完成
            </span>
            <span v-else-if="lastEventIsFailed" class="wf-run-panel__chip is-failed">
              <el-icon :size="11"><CloseBold /></el-icon>失败
            </span>
          </div>
          <div class="wf-run-panel__header-right">
            <button class="wf-run-panel__icon-btn" @click="store.runEvents = []" title="清除日志">
              <el-icon :size="14"><Delete /></el-icon>
            </button>
            <button class="wf-run-panel__icon-btn" @click="runPanelCollapsed = !runPanelCollapsed" :title="runPanelCollapsed ? '展开' : '折叠'">
              <el-icon :size="14"><component :is="runPanelCollapsed ? ArrowUp : ArrowDown" /></el-icon>
            </button>
          </div>
        </div>
        <div v-show="!runPanelCollapsed" class="wf-run-panel__body">
          <div
            v-for="(evt, i) in store.runEvents"
            :key="i"
            class="wf-run-panel__event"
            :class="'is-' + evt.eventType"
          >
            <span class="wf-run-panel__dot" />
            <span v-if="evt.nodeType" class="wf-run-panel__node-type">{{ evt.nodeType }}</span>
            <span class="wf-run-panel__event-title">{{ evt.title || evt.eventType }}</span>
            <span v-if="evt.data?.error" class="wf-run-panel__error" :title="String(evt.data.error)">{{ evt.data.error }}</span>
            <span v-if="evt.data?.elapsedMs" class="wf-run-panel__elapsed">{{ (evt.data.elapsedMs as number / 1000).toFixed(1) }}s</span>
          </div>
          <div v-if="store.running" class="wf-run-panel__running">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>执行中...</span>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { markRaw, ref, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { VueFlow, useVueFlow, type Connection, type NodeTypesObject, type EdgeTypesObject } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { MiniMap } from '@vue-flow/minimap'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import '@vue-flow/minimap/dist/style.css'

import {
  ArrowLeft, RefreshLeft, RefreshRight, Check, VideoPlay, Clock, Upload,
  ZoomIn, ZoomOut, FullScreen, Delete, ArrowUp, ArrowDown, Loading,
  InfoFilled, CloseBold,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { NodeType, type CommonNodeData, type StartNodeData, type VarDefinition, type WorkflowNode, type WorkflowEdge, type WorkflowDSL, type WorkflowNodeData } from './types'
import { useWorkflowStore } from './store'
import { publishWorkflow } from '@/api/workflow'
import NodePanel from './NodePanel.vue'
import NodeConfigPanel from './NodeConfigPanel.vue'
import RunHistoryPanel from './RunHistoryPanel.vue'
import CustomEdge from './CustomEdge.vue'
import StartNode from './nodes/StartNode.vue'
import EndNode from './nodes/EndNode.vue'
import LLMNode from './nodes/LLMNode.vue'
import IfElseNode from './nodes/IfElseNode.vue'
import CodeNode from './nodes/CodeNode.vue'
import HttpRequestNode from './nodes/HttpRequestNode.vue'
import KnowledgeRetrievalNode from './nodes/KnowledgeRetrievalNode.vue'
import VariableAssignerNode from './nodes/VariableAssignerNode.vue'
import VariableAggregatorNode from './nodes/VariableAggregatorNode.vue'
import AnswerNode from './nodes/AnswerNode.vue'
import TemplateTransformNode from './nodes/TemplateTransformNode.vue'
import IterationNode from './nodes/IterationNode.vue'
import ParameterExtractorNode from './nodes/ParameterExtractorNode.vue'
import QuestionClassifierNode from './nodes/QuestionClassifierNode.vue'
import ToolNode from './nodes/ToolNode.vue'
import DocExtractorNode from './nodes/DocExtractorNode.vue'
import ListFilterNode from './nodes/ListFilterNode.vue'
import AgentNode from './nodes/AgentNode.vue'
import LoopNode from './nodes/LoopNode.vue'
import HumanInputNode from './nodes/HumanInputNode.vue'

const route = useRoute()
const router = useRouter()
const store = useWorkflowStore()

const {
  addNodes, addEdges, findNode, setViewport, getViewport, project,
  zoomIn: vfZoomIn, zoomOut: vfZoomOut, fitView: vfFitView,
} = useVueFlow({ id: 'workflow-canvas' })

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const nodes = ref<any[]>([])
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const edges = ref<any[]>([])

// 撤销/重做
const undoStack = ref<string[]>([])
const redoStack = ref<string[]>([])
const canUndo = computed(() => undoStack.value.length > 1)
const canRedo = computed(() => redoStack.value.length > 0)
let snapshotTimer: ReturnType<typeof setTimeout> | null = null

function pushSnapshot() {
  clearTimeout(snapshotTimer!)
  snapshotTimer = setTimeout(() => {
    const snap = JSON.stringify({ nodes: nodes.value, edges: edges.value })
    undoStack.value.push(snap)
    if (undoStack.value.length > 50) undoStack.value.shift()
    redoStack.value = []
  }, 300)
}

function handleUndo() {
  if (undoStack.value.length <= 1) return
  redoStack.value.push(undoStack.value.pop()!)
  const prev = JSON.parse(undoStack.value[undoStack.value.length - 1]!)
  nodes.value = prev.nodes
  edges.value = prev.edges
  store.dirty = true
}

function handleRedo() {
  if (!redoStack.value.length) return
  const next = redoStack.value.pop()!
  undoStack.value.push(next)
  const parsed = JSON.parse(next)
  nodes.value = parsed.nodes
  edges.value = parsed.edges
  store.dirty = true
}

// 注册节点类型
const nodeTypes = {
  [NodeType.Start]: markRaw(StartNode),
  [NodeType.End]: markRaw(EndNode),
  [NodeType.LLM]: markRaw(LLMNode),
  [NodeType.IfElse]: markRaw(IfElseNode),
  [NodeType.Code]: markRaw(CodeNode),
  [NodeType.HttpRequest]: markRaw(HttpRequestNode),
  [NodeType.KnowledgeRetrieval]: markRaw(KnowledgeRetrievalNode),
  [NodeType.VariableAssigner]: markRaw(VariableAssignerNode),
  [NodeType.VariableAggregator]: markRaw(VariableAggregatorNode),
  [NodeType.Answer]: markRaw(AnswerNode),
  [NodeType.TemplateTransform]: markRaw(TemplateTransformNode),
  [NodeType.Iteration]: markRaw(IterationNode),
  [NodeType.ParameterExtractor]: markRaw(ParameterExtractorNode),
  [NodeType.QuestionClassifier]: markRaw(QuestionClassifierNode),
  [NodeType.Tool]: markRaw(ToolNode),
  [NodeType.DocExtractor]: markRaw(DocExtractorNode),
  [NodeType.ListFilter]: markRaw(ListFilterNode),
  [NodeType.Agent]: markRaw(AgentNode),
  [NodeType.Loop]: markRaw(LoopNode),
  [NodeType.HumanInput]: markRaw(HumanInputNode),
} as unknown as NodeTypesObject

// 注册自定义边类型
const edgeTypes: EdgeTypesObject = {
  custom: markRaw(CustomEdge),
}

// 默认边选项
const defaultEdgeOptions = {
  type: 'custom',
  animated: true,
}

// 运行面板折叠
const runPanelCollapsed = ref(false)

// 画布缩放百分比显示
const zoomPercent = ref(100)

function onViewportChange(vp: { zoom: number }) {
  zoomPercent.value = Math.round(vp.zoom * 100)
}

// 运行状态标签
const lastEventIsSuccess = computed(() => {
  if (!store.runEvents.length) return false
  return store.runEvents[store.runEvents.length - 1]!.eventType === 'workflow_succeeded'
})
const lastEventIsFailed = computed(() => {
  if (!store.runEvents.length) return false
  return store.runEvents[store.runEvents.length - 1]!.eventType === 'workflow_failed'
})

// 事件处理
// eslint-disable-next-line @typescript-eslint/no-explicit-any
function onNodeClick({ node }: { node: any }) {
  store.selectNode(node.id)
}

function onPaneClick() {
  store.selectNode(null)
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function onNodesChange(_changes: any[]) {
  pushSnapshot()
  store.dirty = true
}

function onConnect(params: Connection) {
  addEdges([{
    id: `e_${params.source}-${params.target}`,
    source: params.source,
    target: params.target,
    sourceHandle: params.sourceHandle,
    targetHandle: params.targetHandle,
    type: 'custom',
    animated: true,
  }])
  pushSnapshot()
  store.dirty = true
}

// 拖拽添加节点
function onDragOver(event: DragEvent) {
  event.preventDefault()
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
}

function onDrop(event: DragEvent) {
  const type = event.dataTransfer?.getData('application/vueflow') as NodeType
  if (!type) return

  const position = project({ x: event.clientX - 300, y: event.clientY - 60 })
  const id = store.genNodeId(type)
  const data = store.createNodeData(type)

  addNodes([{
    id,
    type,
    position,
    data,
  }])
  pushSnapshot()
  store.dirty = true
  store.selectNode(id)
}

// 缩放控制
function zoomIn() { vfZoomIn({ duration: 200 }) }
function zoomOut() { vfZoomOut({ duration: 200 }) }
function fitView() { vfFitView({ padding: 0.2, duration: 300 }) }

// 工具栏操作
const saving = ref(false)
const publishing = ref(false)
const runHistoryPanel = ref<InstanceType<typeof RunHistoryPanel> | null>(null)

/** 运行输入对话框状态 */
const runInputVisible = ref(false)
const runInputVars = ref<VarDefinition[]>([])
const runInputValues = ref<Record<string, string>>({})

function goBack() {
  router.push({ name: 'workflow-list' })
}

function showRunHistory() {
  if (runHistoryPanel.value) {
    runHistoryPanel.value.visible = true
  }
}

function toDSL(): WorkflowDSL {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const dslNodes: WorkflowNode[] = nodes.value.map((n: any) => ({
    id: n.id as string,
    type: (n.data as CommonNodeData).type as NodeType,
    position: n.position as { x: number; y: number },
    data: (n.data as CommonNodeData) as unknown as WorkflowNodeData,
  }))
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const dslEdges: WorkflowEdge[] = edges.value.map((e: any) => ({
    id: e.id as string,
    source: e.source as string,
    target: e.target as string,
    sourceHandle: (e.sourceHandle ?? undefined) as string | undefined,
    targetHandle: (e.targetHandle ?? undefined) as string | undefined,
  }))
  const vp = getViewport()
  return {
    version: '1.0',
    viewport: { x: vp.x, y: vp.y, zoom: vp.zoom },
    nodes: dslNodes,
    edges: dslEdges,
    environmentVariables: [],
    conversationVariables: [],
  }
}

async function handleSave() {
  saving.value = true
  try {
    const dsl = toDSL()
    await store.saveWorkflow(JSON.stringify(dsl))
    ElMessage.success('保存成功')
  } catch (e: any) {
    console.error('[Workflow] Save failed:', e)
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

async function handleRun() {
  if (!store.workflowId) {
    await handleSave()
  }
  if (!store.workflowId) return

  // 同步节点运行状态
  syncNodeRunningStatus(null)
  const vars = getStartNodeVariables()
  if (vars.length) {
    runInputVars.value = vars
    runInputValues.value = {}
    vars.forEach((v) => {
      if (v.default !== undefined) {
        runInputValues.value[v.variable] = v.default as string
      }
    })
    runInputVisible.value = true
  } else {
    store.runWorkflow({})
  }
  watchRunEvents()
}

/** 从画布开始节点提取输入变量定义 */
function getStartNodeVariables(): VarDefinition[] {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const start = nodes.value.find((n: any) => (n.data as CommonNodeData).type === NodeType.Start)
  if (!start) return []
  const data = start.data as StartNodeData
  return data.variables ?? []
}

function confirmRun() {
  runInputVisible.value = false
  const inputs: Record<string, unknown> = {}
  runInputVars.value.forEach((v) => {
    const val = runInputValues.value[v.variable]
    if (val !== undefined && val !== '') {
      inputs[v.variable] = val
    } else if (v.default !== undefined) {
      inputs[v.variable] = v.default
    }
  })
  store.runWorkflow(inputs)
  watchRunEvents()
}

/** 监听运行事件，实时更新节点状态高亮 */
function watchRunEvents() {
  const unwatch = setInterval(() => {
    for (const evt of store.runEvents) {
      if (evt.nodeId) {
        const status = evt.eventType === 'node_started' ? 'running'
          : evt.eventType === 'node_succeeded' ? 'succeeded'
          : evt.eventType === 'node_failed' ? 'failed' : null
        if (status) syncNodeRunningStatus(evt.nodeId, status as any)
      }
    }
    if (!store.running) {
      clearInterval(unwatch)
    }
  }, 500)
}

/** 同步节点运行状态到 VueFlow 节点 data */
function syncNodeRunningStatus(nodeId: string | null, status?: 'running' | 'succeeded' | 'failed') {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  nodes.value = nodes.value.map((n: any) => {
    if (nodeId === null) {
      // 清除所有状态
      return { ...n, data: { ...n.data, _runningStatus: undefined } }
    }
    if (n.id === nodeId) {
      return { ...n, data: { ...n.data, _runningStatus: status } }
    }
    return n
  })
}

/** 发布工作流（先保存，再发布） */
async function handlePublish() {
  if (!store.workflowId) {
    await handleSave()
  }
  if (!store.workflowId) {
    ElMessage.error('保存失败，无法发布')
    return
  }
  publishing.value = true
  try {
    const dsl = toDSL()
    await store.saveWorkflow(JSON.stringify(dsl))
    await publishWorkflow(store.workflowId)
    ElMessage.success('发布成功')
    await store.loadList()
  } catch (e: any) {
    console.error('[Workflow] Publish failed:', e)
    ElMessage.error('发布失败')
  } finally {
    publishing.value = false
  }
}

/** 从 DSL 填充画布节点与边 */
function applyDSL(dsl: WorkflowDSL) {
  nodes.value = dsl.nodes.map((n: WorkflowNode) => ({
    id: n.id,
    type: n.type,
    position: n.position,
    data: n.data,
  }))
  edges.value = dsl.edges.map((e: WorkflowEdge) => ({
    id: e.id,
    source: e.source,
    target: e.target,
    sourceHandle: e.sourceHandle,
    targetHandle: e.targetHandle,
    type: 'custom',
    animated: true,
  }))
  if (dsl.viewport) {
    setViewport(dsl.viewport)
  }
  // 初始快照
  undoStack.value = [JSON.stringify({ nodes: nodes.value, edges: edges.value })]
  redoStack.value = []
}

// 初始化
onMounted(async () => {
  const workflowId = route.params.id as string
  if (workflowId) {
    try {
      const result = await store.openWorkflow(workflowId)
      if (result?.graph) {
        const dsl = JSON.parse(result.graph) as WorkflowDSL
        applyDSL(dsl)
      } else {
        applyDSL(store.createBlankDSL())
      }
    } catch {
      applyDSL(store.createBlankDSL())
    }
  } else {
    applyDSL(store.createBlankDSL())
  }
})


</script>

<style scoped>
/* ============ 编辑器容器 ============ */
.wf-editor {
  display: flex;
  /* 布局：顶栏 --header-height + 内容区上下各 --space-6 内边距，刚好填满不出现页面滚动条 */
  height: calc(100vh - var(--header-height) - var(--space-6) * 2);
  overflow: hidden;
  background: #f4f5fb;
}

/* ============ 画布 ============ */
.wf-editor__canvas {
  flex: 1;
  position: relative;
  background:
    radial-gradient(circle at 20% 10%, rgba(99, 102, 241, 0.04) 0%, transparent 40%),
    radial-gradient(circle at 85% 80%, rgba(139, 92, 246, 0.04) 0%, transparent 40%),
    #f4f5fb;
}

/* ============ 顶部悬浮工具栏（公共） ============ */
.wf-toolbar {
  position: absolute;
  top: 16px;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: 14px;
  box-shadow: 0 4px 16px -2px rgba(17, 24, 39, 0.08), 0 1px 3px rgba(17, 24, 39, 0.05);
}
.wf-toolbar--left {
  left: 16px;
  min-width: 280px;
}
.wf-toolbar--right {
  right: 16px;
}
.wf-toolbar__divider {
  width: 1px;
  height: 18px;
  background: #e5e7eb;
  flex-shrink: 0;
}
.wf-toolbar__group {
  display: flex;
  align-items: center;
  gap: 2px;
}
.wf-toolbar__group--actions {
  gap: 8px;
}

/* 图标按钮（幽灵样式） */
.wf-toolbar__icon-btn {
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  border-radius: 8px;
  color: #4b5563;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.wf-toolbar__icon-btn:hover:not(:disabled) {
  background: #eef0f7;
  color: #111827;
}
.wf-toolbar__icon-btn:disabled {
  color: #d1d5db;
  cursor: not-allowed;
}

/* 名称区 */
.wf-toolbar__name-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
}
.wf-toolbar__name-input {
  border: 1px solid transparent;
  outline: none;
  font-size: 14px;
  font-weight: 600;
  color: #111827;
  background: transparent;
  width: 180px;
  padding: 4px 8px;
  border-radius: 8px;
  transition: border-color 0.15s, background 0.15s;
}
.wf-toolbar__name-input::placeholder {
  color: #c0c6d4;
  font-weight: 500;
}
.wf-toolbar__name-input:hover {
  border-color: #e5e7eb;
}
.wf-toolbar__name-input:focus {
  border-color: #a5b4fc;
  background: #fff;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.12);
}
.wf-toolbar__dirty {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 500;
  color: #d97706;
  background: #fffbeb;
  border: 1px solid #fde68a;
  padding: 1px 8px;
  border-radius: 999px;
  flex-shrink: 0;
}
.wf-toolbar__dirty-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #f59e0b;
  animation: wf-pulse 1.6s ease-in-out infinite;
}
@keyframes wf-pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.45; transform: scale(0.8); }
}

/* 文字按钮（保存 / 发布 / 运行） */
.wf-toolbar__btn {
  height: 32px;
  border-radius: 9px;
  font-weight: 600;
  font-size: 13px;
  padding: 0 14px;
}
.wf-toolbar__btn :deep(.el-icon) {
  margin-right: 4px;
}

/* 发布按钮：白底 + 绿色强调 */
.wf-toolbar__btn--publish {
  border: 1px solid #d1d5db;
  color: #111827;
  transition: border-color 0.15s, color 0.15s, box-shadow 0.15s;
}
.wf-toolbar__btn--publish:hover:not(:disabled) {
  border-color: #10b981;
  color: #059669;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.12);
}

/* 运行按钮：品牌渐变 */
.wf-toolbar__btn--run {
  border: none;
  color: #fff;
  background: linear-gradient(135deg, #6366f1 0%, #7c3aed 100%);
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.4), inset 0 1px 0 rgba(255, 255, 255, 0.2);
  min-width: 88px;
  transition: box-shadow 0.2s, transform 0.15s, filter 0.15s;
}
.wf-toolbar__btn--run:hover:not(:disabled) {
  filter: brightness(1.06);
  transform: translateY(-1px);
  box-shadow: 0 4px 14px rgba(99, 102, 241, 0.5), inset 0 1px 0 rgba(255, 255, 255, 0.2);
}
.wf-toolbar__btn--run:active:not(:disabled) {
  transform: translateY(0);
}

/* ============ 缩放控制（右下角胶囊） ============ */
.wf-editor__zoom-controls {
  position: absolute;
  bottom: 20px;
  right: 20px;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 4px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: 12px;
  box-shadow: 0 4px 16px -2px rgba(17, 24, 39, 0.1), 0 1px 3px rgba(17, 24, 39, 0.05);
}
.wf-zoom__btn {
  width: 30px;
  height: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  border-radius: 8px;
  color: #4b5563;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.wf-zoom__btn:hover {
  background: #eef0f7;
  color: #111827;
}
.wf-zoom__percent {
  min-width: 46px;
  height: 30px;
  border: none;
  background: transparent;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
  color: #4b5563;
  font-variant-numeric: tabular-nums;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.wf-zoom__percent:hover {
  background: #eef0f7;
  color: #4f46e5;
}

/* ============ 运行结果面板 ============ */
.wf-run-panel {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  max-height: 300px;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-top: 1px solid #e8eaf2;
  box-shadow: 0 -6px 20px -4px rgba(17, 24, 39, 0.08);
  z-index: 20;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.wf-run-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-bottom: 1px solid #f0f1f7;
  flex-shrink: 0;
}
.wf-run-panel__header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.wf-run-panel__header-right {
  display: flex;
  align-items: center;
  gap: 4px;
}
.wf-run-panel__title {
  font-size: 13px;
  font-weight: 700;
  color: #111827;
}
.wf-run-panel__chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 500;
  padding: 2px 9px;
  border-radius: 999px;
  line-height: 18px;
}
.wf-run-panel__chip.is-running {
  color: #d97706;
  background: #fffbeb;
  border: 1px solid #fde68a;
}
.wf-run-panel__chip.is-success {
  color: #059669;
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
}
.wf-run-panel__chip.is-failed {
  color: #dc2626;
  background: #fef2f2;
  border: 1px solid #fecaca;
}
.wf-run-panel__chip.is-running .wf-run-panel__chip-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #f59e0b;
  animation: wf-pulse 1.6s ease-in-out infinite;
}
.wf-run-panel__icon-btn {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  border-radius: 7px;
  color: #9ca3af;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.wf-run-panel__icon-btn:hover {
  background: #f0f1f7;
  color: #374151;
}
.wf-run-panel__body {
  flex: 1;
  overflow-y: auto;
  padding: 8px 16px 12px;
}
.wf-run-panel__event {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 8px;
  font-size: 12px;
  color: #4b5563;
  border-radius: 6px;
  transition: background 0.1s;
}
.wf-run-panel__event:hover {
  background: #f7f8fc;
}
.wf-run-panel__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
  background: #c4c9d8;
}
.wf-run-panel__event.is-node_started .wf-run-panel__dot { background: #f59e0b; }
.wf-run-panel__event.is-node_succeeded .wf-run-panel__dot { background: #10b981; }
.wf-run-panel__event.is-node_failed .wf-run-panel__dot { background: #ef4444; }
.wf-run-panel__event.is-workflow_succeeded .wf-run-panel__dot { background: #10b981; }
.wf-run-panel__event.is-workflow_failed .wf-run-panel__dot { background: #ef4444; }

.wf-run-panel__node-type {
  font-size: 10px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 4px;
  background: #eef0f7;
  color: #6b7280;
  flex-shrink: 0;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}
.wf-run-panel__event-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.wf-run-panel__error {
  color: #dc2626;
  font-size: 11px;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.wf-run-panel__elapsed {
  font-size: 11px;
  color: #9ca3af;
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}
.wf-run-panel__running {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px;
  color: #d97706;
  font-size: 12px;
}

/* ============ 运行对话框 ============ */
.wf-run-dialog__desc {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 16px;
  padding: 10px 12px;
  background: #eef2ff;
  border-radius: 8px;
}
.wf-run-dialog__desc .el-icon {
  color: #6366f1;
}
.wf-run-dialog__submit {
  font-weight: 600;
}

/* ============ 过渡动画 ============ */
.slide-up-enter-active,
.slide-up-leave-active {
  transition: transform 0.25s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.25s ease;
}
.slide-up-enter-from,
.slide-up-leave-to {
  transform: translateY(100%);
  opacity: 0;
}

.slide-right-enter-active,
.slide-right-leave-active {
  transition: transform 0.2s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.2s ease;
}
.slide-right-enter-from,
.slide-right-leave-to {
  transform: translateX(100%);
  opacity: 0;
}

/* ============ VueFlow 细节覆盖 ============ */
.wf-editor__canvas :deep(.vue-flow__minimap) {
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 16px -2px rgba(17, 24, 39, 0.1);
  border: 1px solid #e8eaf2;
  margin: 0 0 12px 12px;
}
.wf-editor__canvas :deep(.vue-flow__handle) {
  transition: width 0.15s, height 0.15s, border-color 0.15s, background 0.15s;
}
</style>
