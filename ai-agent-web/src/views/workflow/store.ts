/**
 * 工作流画布状态管理
 *
 * 管理工作流元数据、选中状态、DSL 转换。
 * VueFlow 节点/边操作由编辑器组件通过 VueFlow composable 管理。
 */
import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import {
  type CommonNodeData,
  type WorkflowDSL,
  type WorkflowNode,
  type WorkflowEdge,
  type WorkflowVariable,
  createDefaultNodeData,
  NodeType,
  NODE_TYPE_LABELS,
} from './types'
import {
  listWorkflows,
  getWorkflow,
  createWorkflow,
  updateWorkflow,
  deleteWorkflow as apiDeleteWorkflow,
  duplicateWorkflow,
  runWorkflowSSE,
  type WorkflowListItem,
  type WorkflowEvent,
} from '@/api/workflow'

/** 生成短 ID */
function genId(): string {
  return Math.random().toString(36).slice(2, 10)
}

export const useWorkflowStore = defineStore('workflow', () => {
  // ============ 状态 ============
  /** 当前工作流 ID（草稿 ID） */
  const workflowId = ref<string>('')
  /** 工作流名称 */
  const workflowName = ref('未命名工作流')
  /** 是否只读模式 */
  const readonly = ref(false)
  /** 选中节点 ID */
  const selectedNodeId = ref<string | null>(null)
  /** 是否已修改 */
  const dirty = ref(false)
  /** 工作流列表 */
  const workflowList = ref<WorkflowListItem[]>([])
  /** 列表加载中 */
  const listLoading = ref(false)
  /** 运行状态 */
  const running = ref(false)
  /** 运行事件列表（节点执行进度） */
  const runEvents = ref<WorkflowEvent[]>([])

  // ============ 选中节点 ============
  function selectNode(id: string | null) {
    selectedNodeId.value = id
  }

  // ============ 工具方法 ============
  /** 生成节点 ID */
  function genNodeId(type: NodeType): string {
    return `${type}_${genId()}`
  }

  /** 创建节点默认 data */
  function createNodeData(type: NodeType, title?: string): CommonNodeData {
    return { ...createDefaultNodeData(type), title: title ?? NODE_TYPE_LABELS[type] } as CommonNodeData
  }

  /** 创建默认空白工作流 DSL（含 Start + End 节点） */
  function createBlankDSL(): WorkflowDSL {
    return {
      version: '1.0',
      viewport: { x: 0, y: 0, zoom: 1 },
      nodes: [
        {
          id: 'start_1',
          type: NodeType.Start,
          position: { x: 80, y: 300 },
          data: { type: NodeType.Start, title: '开始', desc: '', variables: [] },
        },
        {
          id: 'end_1',
          type: NodeType.End,
          position: { x: 600, y: 300 },
          data: { type: NodeType.End, title: '结束', desc: '', outputs: {} },
        },
      ],
      edges: [{ id: 'e_start_1-end_1', source: 'start_1', target: 'end_1' }],
      environmentVariables: [],
      conversationVariables: [],
    }
  }

  // ============ API 操作 ============

  /** 加载工作流列表 */
  async function loadList() {
    listLoading.value = true
    try {
      workflowList.value = await listWorkflows()
    } finally {
      listLoading.value = false
    }
  }

  /** 新建工作流 */
  async function createNew(name?: string) {
    const dsl = createBlankDSL()
    const result = await createWorkflow({
      name: name ?? '未命名工作流',
      graph: JSON.stringify(dsl),
    })
    if (result) {
      workflowId.value = result.workflowId
      workflowName.value = result.name
      dirty.value = false
      await loadList()
    }
    return result
  }

  /** 打开工作流 */
  async function openWorkflow(id: string) {
    const result = await getWorkflow(id)
    if (result) {
      workflowId.value = result.workflowId
      workflowName.value = result.name
      dirty.value = false
    }
    return result
  }

  /** 保存工作流 */
  async function saveWorkflow(graph: string) {
    if (!workflowId.value) return
    await updateWorkflow(workflowId.value, {
      name: workflowName.value,
      graph,
    })
    dirty.value = false
    await loadList()
  }

  /** 删除工作流 */
  async function removeWorkflow(id: string) {
    await apiDeleteWorkflow(id)
    if (workflowId.value === id) {
      workflowId.value = ''
      workflowName.value = '未命名工作流'
    }
    await loadList()
  }

  /** 复制工作流 */
  async function copyWorkflow(id: string) {
    const result = await duplicateWorkflow(id)
    await loadList()
    return result
  }

  /** 运行工作流（SSE） */
  function runWorkflow(inputs: Record<string, unknown> = {}): AbortController {
    running.value = true
    runEvents.value = []
    const controller = runWorkflowSSE(
      workflowId.value,
      inputs,
      (event: WorkflowEvent) => {
        runEvents.value.push(event)
        if (event.eventType === 'workflow_succeeded' || event.eventType === 'workflow_failed') {
          running.value = false
        }
      },
      () => {
        running.value = false
      },
    )
    return controller
  }

  /** 重置 */
  function reset() {
    workflowId.value = ''
    workflowName.value = '未命名工作流'
    selectedNodeId.value = null
    dirty.value = false
    runEvents.value = []
    running.value = false
  }

  return {
    // state
    workflowId,
    workflowName,
    readonly,
    selectedNodeId,
    dirty,
    workflowList,
    listLoading,
    running,
    runEvents,
    // actions
    selectNode,
    genNodeId,
    createNodeData,
    createBlankDSL,
    loadList,
    createNew,
    openWorkflow,
    saveWorkflow,
    removeWorkflow,
    copyWorkflow,
    runWorkflow,
    reset,
  }
})
