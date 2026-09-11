/**
 * 工作流 DSL 类型定义
 *
 * 对齐 Dify 工作流 DSL，适配 VueFlow + Vue 3 体系。
 */

/** 节点类型枚举 */
export enum NodeType {
  Start = 'start',
  End = 'end',
  LLM = 'llm',
  IfElse = 'if-else',
  Code = 'code',
  HttpRequest = 'http-request',
  KnowledgeRetrieval = 'knowledge-retrieval',
  VariableAssigner = 'variable-assigner',
  VariableAggregator = 'variable-aggregator',
  Answer = 'answer',
  TemplateTransform = 'template-transform',
  Iteration = 'iteration',
  ParameterExtractor = 'parameter-extractor',
  QuestionClassifier = 'question-classifier',
  Tool = 'tool',
  DocExtractor = 'document-extractor',
  ListFilter = 'list-operator',
  Agent = 'agent',
  Loop = 'loop',
  HumanInput = 'human-input',
}

/** 变量类型 */
export enum VarType {
  String = 'string',
  Number = 'number',
  Boolean = 'boolean',
  Object = 'object',
  ArrayString = 'array[string]',
  ArrayNumber = 'array[number]',
  ArrayObject = 'array[object]',
}

/** 变量引用：nodeId.outputKey */
export interface VarSelector {
  nodeId: string
  outputKey: string
}

/** 变量定义 */
export interface VarDefinition {
  variable: string
  type: VarType
  label?: string
  required?: boolean
  default?: unknown
}

/** 节点运行状态 */
export enum NodeRunningStatus {
  Idle = 'idle',
  Running = 'running',
  Succeeded = 'succeeded',
  Failed = 'failed',
  Paused = 'paused',
}

/** ============ 各节点 data 类型 ============ */

/** 所有节点数据的公共字段 */
export interface BaseNodeData {
  type: NodeType
  title: string
  desc: string
}

export interface StartNodeData extends BaseNodeData {
  type: NodeType.Start
  variables: VarDefinition[]
}

export interface EndNodeData extends BaseNodeData {
  type: NodeType.End
  outputs: Record<string, VarSelector>
}

export interface LLMNodeData extends BaseNodeData {
  type: NodeType.LLM
  model: string
  promptTemplate: string
  temperature: number
  maxTokens: number
  context?: VarSelector[]
}

export interface IfElseNodeData extends BaseNodeData {
  type: NodeType.IfElse
  conditions: IfElseCondition[]
}

export interface IfElseCondition {
  id: string
  variableSelector: VarSelector
  comparisonOperator: ComparisonOperator
  value: string
}

export type ComparisonOperator =
  | 'contains'
  | 'not contains'
  | 'start with'
  | 'end with'
  | 'is'
  | 'is not'
  | 'empty'
  | 'not empty'
  | '='
  | '!='
  | '>'
  | '<'
  | '>='
  | '<='

export interface CodeNodeData extends BaseNodeData {
  type: NodeType.Code
  codeLanguage: 'javascript' | 'python' | 'jinja2'
  code: string
  inputs: Record<string, VarSelector>
  outputs: VarDefinition[]
}

export interface HttpRequestNodeData extends BaseNodeData {
  type: NodeType.HttpRequest
  method: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'
  url: string
  headers: Record<string, string>
  body?: string
  timeout: number
  authorization?: {
    type: 'no-auth' | 'api-key' | 'bearer'
    headerName?: string
    headerValue?: string
    token?: string
  }
}

export interface KnowledgeRetrievalNodeData extends BaseNodeData {
  type: NodeType.KnowledgeRetrieval
  knowledgeBaseId: string
  query: VarSelector
  topK: number
  scoreThreshold: number
}

export interface VariableAssignerNodeData extends BaseNodeData {
  type: NodeType.VariableAssigner
  assignments: VariableAssignment[]
}

export interface VariableAssignment {
  id: string
  variable: string
  valueSelector: VarSelector
}

export interface VariableAggregatorNodeData extends BaseNodeData {
  type: NodeType.VariableAggregator
  variables: VarSelector[]
  outputType: VarType
}

export interface AnswerNodeData extends BaseNodeData {
  type: NodeType.Answer
  answer: VarSelector
}

export interface TemplateTransformNodeData extends BaseNodeData {
  type: NodeType.TemplateTransform
  template: string
  variables: Record<string, VarSelector>
}

export interface IterationNodeData extends BaseNodeData {
  type: NodeType.Iteration
  iterator: VarSelector
  outputType: VarType
}

export interface ParameterExtractorNodeData extends BaseNodeData {
  type: NodeType.ParameterExtractor
  model: string
  query: VarSelector
  parameters: VarDefinition[]
  instruction: string
}

export interface QuestionClassifierNodeData extends BaseNodeData {
  type: NodeType.QuestionClassifier
  model: string
  query: VarSelector
  categories: QuestionClassifierCategory[]
}

export interface QuestionClassifierCategory {
  id: string
  name: string
  description: string
}

export interface ToolNodeData extends BaseNodeData {
  type: NodeType.Tool
  toolId: string
  toolName: string
  providerName?: string
  inputs: Record<string, VarSelector | string>
}

export interface DocExtractorNodeData extends BaseNodeData {
  type: NodeType.DocExtractor
  input: VarSelector
  outputType: 'text' | 'markdown'
}

export interface ListFilterNodeData extends BaseNodeData {
  type: NodeType.ListFilter
  input: VarSelector
  filterCondition: {
    key: string
    comparisonOperator: ComparisonOperator
    value: string
  }
  outputType: VarType
}

export interface AgentNodeData extends BaseNodeData {
  type: NodeType.Agent
  model: string
  promptTemplate: string
  tools: string[]
  maxIterations: number
}

export interface LoopNodeData extends BaseNodeData {
  type: NodeType.Loop
  loopCondition: {
    variableSelector: VarSelector
    comparisonOperator: ComparisonOperator
    value: string
  }
  maxIterations: number
}

export interface HumanInputNodeData extends BaseNodeData {
  type: NodeType.HumanInput
  inputForm: HumanInputField[]
  timeout: number
}

export interface HumanInputField {
  id: string
  variable: string
  type: 'text' | 'number' | 'select' | 'boolean'
  label: string
  required: boolean
  options?: string[]
}

/** 节点 data 联合类型 */
export type WorkflowNodeData =
  | StartNodeData
  | EndNodeData
  | LLMNodeData
  | IfElseNodeData
  | CodeNodeData
  | HttpRequestNodeData
  | KnowledgeRetrievalNodeData
  | VariableAssignerNodeData
  | VariableAggregatorNodeData
  | AnswerNodeData
  | TemplateTransformNodeData
  | IterationNodeData
  | ParameterExtractorNodeData
  | QuestionClassifierNodeData
  | ToolNodeData
  | DocExtractorNodeData
  | ListFilterNodeData
  | AgentNodeData
  | LoopNodeData
  | HumanInputNodeData

/** 通用节点扩展属性（VueFlow 适配层） */
export type CommonNodeData = BaseNodeData & {
  _runningStatus?: NodeRunningStatus
  _selected?: boolean
}

/** 边上的额外数据 */
export interface EdgeData {
  sourceType?: NodeType
  targetType?: NodeType
  _hovering?: boolean
}

/** 工作流 DSL 完整结构 */
export interface WorkflowDSL {
  /** 工作流版本 */
  version: string
  /** 画布视口 */
  viewport: { x: number; y: number; zoom: number }
  /** 节点列表 */
  nodes: WorkflowNode[]
  /** 边列表 */
  edges: WorkflowEdge[]
  /** 环境变量 */
  environmentVariables: WorkflowVariable[]
  /** 会话变量 */
  conversationVariables: WorkflowVariable[]
}

/** 工作流变量 */
export interface WorkflowVariable {
  id: string
  name: string
  type: VarType
  value: string
  label?: string
}

/** 序列化节点 */
export interface WorkflowNode {
  id: string
  type: NodeType
  position: { x: number; y: number }
  data: WorkflowNodeData
  width?: number
  height?: number
}

/** 序列化边 */
export interface WorkflowEdge {
  id: string
  source: string
  target: string
  sourceHandle?: string
  targetHandle?: string
}

/** 节点类型 → 默认标题 */
export const NODE_TYPE_LABELS: Record<NodeType, string> = {
  [NodeType.Start]: '开始',
  [NodeType.End]: '结束',
  [NodeType.LLM]: 'LLM',
  [NodeType.IfElse]: '条件分支',
  [NodeType.Code]: '代码执行',
  [NodeType.HttpRequest]: 'HTTP 请求',
  [NodeType.KnowledgeRetrieval]: '知识检索',
  [NodeType.VariableAssigner]: '变量赋值',
  [NodeType.VariableAggregator]: '变量聚合',
  [NodeType.Answer]: '直接回复',
  [NodeType.TemplateTransform]: '模板转换',
  [NodeType.Iteration]: '迭代',
  [NodeType.ParameterExtractor]: '参数提取',
  [NodeType.QuestionClassifier]: '问题分类',
  [NodeType.Tool]: '工具',
  [NodeType.DocExtractor]: '文档提取',
  [NodeType.ListFilter]: '列表过滤',
  [NodeType.Agent]: 'Agent',
  [NodeType.Loop]: '循环',
  [NodeType.HumanInput]: '人工介入',
}

/** 节点类型 → 图标 (文本缩写，更专业) */
export const NODE_TYPE_ICONS: Record<NodeType, string> = {
  [NodeType.Start]: '▶',
  [NodeType.End]: '■',
  [NodeType.LLM]: 'AI',
  [NodeType.IfElse]: '?',
  [NodeType.Code]: '{}',
  [NodeType.HttpRequest]: '↗',
  [NodeType.KnowledgeRetrieval]: 'K',
  [NodeType.VariableAssigner]: '=',
  [NodeType.VariableAggregator]: 'Σ',
  [NodeType.Answer]: '↩',
  [NodeType.TemplateTransform]: 'T',
  [NodeType.Iteration]: '↻',
  [NodeType.ParameterExtractor]: 'P',
  [NodeType.QuestionClassifier]: '?',
  [NodeType.Tool]: '⚙',
  [NodeType.DocExtractor]: 'D',
  [NodeType.ListFilter]: '[ ]',
  [NodeType.Agent]: 'A',
  [NodeType.Loop]: '⟳',
  [NodeType.HumanInput]: '●',
}

/** 节点类型 → 颜色 (Dify 风格) */
export const NODE_TYPE_COLORS: Record<NodeType, string> = {
  [NodeType.Start]: '#2970FF',
  [NodeType.End]: '#F04438',
  [NodeType.LLM]: '#7C3AED',
  [NodeType.IfElse]: '#F79009',
  [NodeType.Code]: '#158AE6',
  [NodeType.HttpRequest]: '#12B76A',
  [NodeType.KnowledgeRetrieval]: '#06AED4',
  [NodeType.VariableAssigner]: '#667085',
  [NodeType.VariableAggregator]: '#667085',
  [NodeType.Answer]: '#7C3AED',
  [NodeType.TemplateTransform]: '#158AE6',
  [NodeType.Iteration]: '#F79009',
  [NodeType.ParameterExtractor]: '#7C3AED',
  [NodeType.QuestionClassifier]: '#F79009',
  [NodeType.Tool]: '#12B76A',
  [NodeType.DocExtractor]: '#158AE6',
  [NodeType.ListFilter]: '#158AE6',
  [NodeType.Agent]: '#7C3AED',
  [NodeType.Loop]: '#F79009',
  [NodeType.HumanInput]: '#F04438',
}

/** 创建节点默认 data */
export function createDefaultNodeData(type: NodeType, title?: string): WorkflowNodeData {
  const label = title ?? NODE_TYPE_LABELS[type]
  switch (type) {
    case NodeType.Start:
      return { type, title: label, desc: '', variables: [] }
    case NodeType.End:
      return { type, title: label, desc: '', outputs: {} }
    case NodeType.LLM:
      return { type, title: label, desc: '', model: '', promptTemplate: '', temperature: 0.7, maxTokens: 2048 }
    case NodeType.IfElse:
      return { type, title: label, desc: '', conditions: [] }
    case NodeType.Code:
      return { type, title: label, desc: '', codeLanguage: 'javascript', code: '', inputs: {}, outputs: [] }
    case NodeType.HttpRequest:
      return { type, title: label, desc: '', method: 'GET', url: '', headers: {}, timeout: 30000 }
    case NodeType.KnowledgeRetrieval:
      return { type, title: label, desc: '', knowledgeBaseId: '', query: { nodeId: '', outputKey: '' }, topK: 5, scoreThreshold: 0.5 }
    case NodeType.VariableAssigner:
      return { type, title: label, desc: '', assignments: [] }
    case NodeType.VariableAggregator:
      return { type, title: label, desc: '', variables: [], outputType: VarType.String }
    case NodeType.Answer:
      return { type, title: label, desc: '', answer: { nodeId: '', outputKey: '' } }
    case NodeType.TemplateTransform:
      return { type, title: label, desc: '', template: '', variables: {} }
    case NodeType.Iteration:
      return { type, title: label, desc: '', iterator: { nodeId: '', outputKey: '' }, outputType: VarType.ArrayString }
    case NodeType.ParameterExtractor:
      return { type, title: label, desc: '', model: '', query: { nodeId: '', outputKey: '' }, parameters: [], instruction: '' }
    case NodeType.QuestionClassifier:
      return { type, title: label, desc: '', model: '', query: { nodeId: '', outputKey: '' }, categories: [] }
    case NodeType.Tool:
      return { type, title: label, desc: '', toolId: '', toolName: '', inputs: {} }
    case NodeType.DocExtractor:
      return { type, title: label, desc: '', input: { nodeId: '', outputKey: '' }, outputType: 'text' }
    case NodeType.ListFilter:
      return { type, title: label, desc: '', input: { nodeId: '', outputKey: '' }, filterCondition: { key: '', comparisonOperator: 'contains', value: '' }, outputType: VarType.ArrayObject }
    case NodeType.Agent:
      return { type, title: label, desc: '', model: '', promptTemplate: '', tools: [], maxIterations: 5 }
    case NodeType.Loop:
      return { type, title: label, desc: '', loopCondition: { variableSelector: { nodeId: '', outputKey: '' }, comparisonOperator: 'contains', value: '' }, maxIterations: 100 }
    case NodeType.HumanInput:
      return { type, title: label, desc: '', inputForm: [], timeout: 3600 }
  }
}
