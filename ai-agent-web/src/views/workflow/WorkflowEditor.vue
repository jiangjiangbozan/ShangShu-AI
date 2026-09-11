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
      >
        <Background :gap="16" :size="1.5" pattern-color="#d0d5dd" />
        <MiniMap position="bottom-left" :pannable="true" :zoomable="true" />
      </VueFlow>

      <!-- 顶部工具栏（放在 VueFlow 外部，覆盖在画布上方） -->
      <div class="wf-toolbar">
        <div class="wf-toolbar__left">
          <el-button text size="small" @click="goBack" class="wf-toolbar__back">
            <el-icon><ArrowLeft /></el-icon>
          </el-button>
          <div class="wf-toolbar__name-wrap">
            <input
              v-model="store.workflowName"
              class="wf-toolbar__name-input"
              @change="store.dirty = true"
            />
            <el-tag v-if="store.dirty" size="small" type="warning" effect="dark" round class="wf-toolbar__dirty">未保存</el-tag>
          </div>
        </div>
        <div class="wf-toolbar__actions">
          <el-button size="small" @click="handleUndo" :disabled="!canUndo" title="撤销">
            <el-icon><RefreshLeft /></el-icon>
          </el-button>
          <el-button size="small" @click="handleRedo" :disabled="!canRedo" title="重做">
            <el-icon><RefreshRight /></el-icon>
          </el-button>
          <el-divider direction="vertical" />
          <el-button size="small" @click="handleSave" :loading="saving">
            <el-icon><Check /></el-icon>保存
          </el-button>
          <el-button size="small" type="primary" @click="handleRun" :loading="store.running" class="wf-toolbar__run-btn">
            <el-icon><VideoPlay /></el-icon>
            <span>{{ store.running ? '运行中...' : '运行' }}</span>
          </el-button>
          <el-button size="small" @click="showRunHistory" title="运行历史">
            <el-icon><Clock /></el-icon>
          </el-button>
          <el-divider direction="vertical" />
          <el-button size="small" type="warning" @click="handlePublish" :loading="publishing">
            <el-icon><Upload /></el-icon>发布
          </el-button>
        </div>
      </div>

      <!-- 缩放控制（右下角） -->
      <div class="wf-editor__zoom-controls">
        <el-button-group>
          <el-button size="small" @click="zoomIn" title="放大"><el-icon><ZoomIn /></el-icon></el-button>
          <el-button size="small" @click="zoomOut" title="缩小"><el-icon><ZoomOut /></el-icon></el-button>
          <el-button size="small" @click="fitView" title="适应画布"><el-icon><FullScreen /></el-icon></el-button>
        </el-button-group>
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
      <div class="wf-run-dialog__desc">以下变量将作为工作流运行的输入参数</div>
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
        <el-button type="primary" @click="confirmRun" :loading="store.running">
          <el-icon><VideoPlay /></el-icon>开始运行
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
            <el-tag v-if="store.running" size="small" type="warning" effect="light" round>运行中</el-tag>
            <el-tag v-else-if="lastEventIsSuccess" size="small" type="success" effect="light" round>完成</el-tag>
            <el-tag v-else-if="lastEventIsFailed" size="small" type="danger" effect="light" round>失败</el-tag>
          </div>
          <div class="wf-run-panel__header-right">
            <el-button size="small" text @click="store.runEvents = []">
              <el-icon><Delete /></el-icon>清除
            </el-button>
            <el-button size="small" text @click="runPanelCollapsed = !runPanelCollapsed">
              <el-icon><component :is="runPanelCollapsed ? 'ArrowUp' : 'ArrowDown'" /></el-icon>
            </el-button>
          </div>
        </div>
        <div v-show="!runPanelCollapsed" class="wf-run-panel__body">
          <div
            v-for="(evt, i) in store.runEvents"
            :key="i"
            class="wf-run-panel__event"
            :class="'wf-run-panel__event--' + evt.eventType"
          >
            <span class="wf-run-panel__dot" />
            <span class="wf-run-panel__node-type">{{ evt.nodeType || '' }}</span>
            <span class="wf-run-panel__event-title">{{ evt.title || evt.eventType }}</span>
            <span v-if="evt.data?.error" class="wf-run-panel__error">{{ evt.data.error }}</span>
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
function zoomIn() { vfZoomIn() }
function zoomOut() { vfZoomOut() }
function fitView() { vfFitView({ padding: 0.2 }) }

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
  height: calc(100vh - 60px);
  overflow: hidden;
  background: #f9fafb;
}

/* ============ 画布 ============ */
.wf-editor__canvas {
  flex: 1;
  position: relative;
  background: #f2f4f7;
}

/* ============ 顶部工具栏 ============ */
.wf-toolbar {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 16px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(16, 24, 40, 0.1), 0 1px 2px rgba(16, 24, 40, 0.06);
  min-width: 520px;
}
.wf-toolbar__left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.wf-toolbar__back {
  padding: 4px;
}
.wf-toolbar__name-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
}
.wf-toolbar__name-input {
  border: none;
  outline: none;
  font-size: 14px;
  font-weight: 600;
  color: #1d1d1f;
  background: transparent;
  width: 160px;
  padding: 2px 4px;
  border-radius: 4px;
  transition: background 0.15s;
}
.wf-toolbar__name-input:focus {
  background: #f2f4f7;
}
.wf-toolbar__dirty {
  font-size: 10px;
  height: 18px;
  line-height: 18px;
  padding: 0 6px;
}
.wf-toolbar__actions {
  display: flex;
  align-items: center;
  gap: 4px;
}
.wf-toolbar__run-btn {
  min-width: 80px;
}

/* ============ 缩放控制 ============ */
.wf-editor__zoom-controls {
  position: absolute;
  bottom: 16px;
  right: 16px;
  z-index: 10;
}

/* ============ 运行结果面板 ============ */
.wf-run-panel {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  max-height: 280px;
  background: #fff;
  border-top: 1px solid #eaecf0;
  box-shadow: 0 -4px 8px -2px rgba(16, 24, 40, 0.08);
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
  border-bottom: 1px solid #f0f2f5;
  flex-shrink: 0;
}
.wf-run-panel__header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.wf-run-panel__header-right {
  display: flex;
  align-items: center;
  gap: 4px;
}
.wf-run-panel__title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}
.wf-run-panel__body {
  flex: 1;
  overflow-y: auto;
  padding: 8px 16px;
}
.wf-run-panel__event {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 0;
  font-size: 12px;
  color: #606266;
  border-bottom: 1px solid #fafafa;
}
.wf-run-panel__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
  background: #909399;
}
.wf-run-panel__event--node_started .wf-run-panel__dot { background: #F79009; }
.wf-run-panel__event--node_succeeded .wf-run-panel__dot { background: #12B76A; }
.wf-run-panel__event--node_failed .wf-run-panel__dot { background: #F04438; }
.wf-run-panel__event--workflow_succeeded .wf-run-panel__dot { background: #12B76A; }
.wf-run-panel__event--workflow_failed .wf-run-panel__dot { background: #F04438; }

.wf-run-panel__node-type {
  font-size: 10px;
  padding: 1px 5px;
  border-radius: 3px;
  background: #f0f2f5;
  color: #909399;
  flex-shrink: 0;
}
.wf-run-panel__event-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.wf-run-panel__error {
  color: #F04438;
  font-size: 11px;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.wf-run-panel__elapsed {
  font-size: 11px;
  color: #909399;
  flex-shrink: 0;
}
.wf-run-panel__running {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 0;
  color: #F79009;
  font-size: 12px;
}

/* ============ 运行对话框 ============ */
.wf-run-dialog__desc {
  font-size: 13px;
  color: #909399;
  margin-bottom: 16px;
}

/* ============ 过渡动画 ============ */
.slide-up-enter-active,
.slide-up-leave-active {
  transition: transform 0.25s ease, opacity 0.25s ease;
}
.slide-up-enter-from,
.slide-up-leave-to {
  transform: translateY(100%);
  opacity: 0;
}

.slide-right-enter-active,
.slide-right-leave-active {
  transition: transform 0.2s ease, opacity 0.2s ease;
}
.slide-right-enter-from,
.slide-right-leave-to {
  transform: translateX(100%);
  opacity: 0;
}
</style>
