<template>
  <div class="node-config-panel">
    <div class="node-config-panel__header">
      <div class="node-config-panel__header-icon" :style="{ background: nodeIconBgColor }">
        <span :style="{ color: nodeIconColor, fontSize: nodeIconFontSize }">{{ nodeIcon }}</span>
      </div>
      <span class="node-config-panel__title">{{ panelTitle }}</span>
      <button class="node-config-panel__close" @click="store.selectNode(null)">
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M12 4L4 12M4 4l8 8" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>
      </button>
    </div>
    <div class="node-config-panel__body">
      <template v-if="nodeData">
        <!-- 通用：标题 + 描述 -->
        <el-form label-position="top" class="config-panel__form">
          <el-form-item label="节点标题">
            <el-input v-model="nodeData.title" @change="onDataChange" />
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="nodeData.desc" type="textarea" :rows="2" @change="onDataChange" />
          </el-form-item>

          <!-- Start 节点：输入变量 -->
          <template v-if="nodeData.type === NodeType.Start">
            <el-divider content-position="left">输入变量</el-divider>
            <div v-for="(v, i) in (nodeData as StartNodeData).variables" :key="i" class="var-row">
              <el-input v-model="v.variable" placeholder="变量名" size="small" @change="onDataChange" />
              <el-select v-model="v.type" placeholder="类型" size="small" style="width: 120px" @change="onDataChange">
                <el-option v-for="t in varTypes" :key="t" :label="t" :value="t" />
              </el-select>
              <el-input v-model="v.label" placeholder="标签" size="small" @change="onDataChange" />
              <el-button type="danger" size="small" circle @click="removeStartVar(i)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <el-button size="small" @click="addStartVar">+ 添加变量</el-button>
          </template>

          <!-- LLM 节点 -->
          <template v-if="nodeData.type === NodeType.LLM">
            <el-divider content-position="left">LLM 配置</el-divider>
            <el-form-item label="模型">
              <el-input v-model="(nodeData as LLMNodeData).model" placeholder="如 gpt-4o" @change="onDataChange" />
            </el-form-item>
            <el-form-item label="提示词模板">
              <el-input
                v-model="(nodeData as LLMNodeData).promptTemplate"
                type="textarea"
                :rows="6"
                placeholder="输入提示词..."
                @change="onDataChange"
              />
            </el-form-item>
            <el-form-item label="Temperature">
              <el-slider
                v-model="(nodeData as LLMNodeData).temperature"
                :min="0"
                :max="2"
                :step="0.1"
                show-input
                @change="onDataChange"
              />
            </el-form-item>
            <el-form-item label="Max Tokens">
              <el-input-number
                v-model="(nodeData as LLMNodeData).maxTokens"
                :min="1"
                :max="128000"
                :step="256"
                @change="onDataChange"
              />
            </el-form-item>
          </template>

          <!-- IfElse 节点 -->
          <template v-if="nodeData.type === NodeType.IfElse">
            <el-divider content-position="left">条件配置</el-divider>
            <div v-for="(cond, i) in (nodeData as IfElseNodeData).conditions" :key="cond.id" class="cond-row">
              <el-input v-model="cond.variableSelector.outputKey" placeholder="变量" size="small" @change="onDataChange" />
              <el-select v-model="cond.comparisonOperator" placeholder="比较" size="small" style="width: 110px" @change="onDataChange">
                <el-option v-for="op in comparisonOperators" :key="op" :label="op" :value="op" />
              </el-select>
              <el-input v-model="cond.value" placeholder="值" size="small" @change="onDataChange" />
              <el-button type="danger" size="small" circle @click="removeCondition(i)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <el-button size="small" @click="addCondition">+ 添加条件</el-button>
          </template>

          <!-- End 节点 -->
          <template v-if="nodeData.type === NodeType.End">
            <el-divider content-position="left">输出配置</el-divider>
            <div class="end-hint">输出变量在连线时自动映射上游节点结果</div>
          </template>

          <!-- Code 节点 -->
          <template v-if="nodeData.type === NodeType.Code">
            <el-divider content-position="left">代码配置</el-divider>
            <el-form-item label="语言">
              <el-select v-model="(nodeData as any).codeLanguage" size="small" @change="onDataChange">
                <el-option label="JavaScript" value="javascript" />
                <el-option label="Python" value="python" />
                <el-option label="Jinja2" value="jinja2" />
              </el-select>
            </el-form-item>
            <el-form-item label="代码">
              <el-input v-model="(nodeData as any).code" type="textarea" :rows="8" placeholder="// result.xxx = ..." @change="onDataChange" />
            </el-form-item>
          </template>

          <!-- HTTP Request 节点 -->
          <template v-if="nodeData.type === NodeType.HttpRequest">
            <el-divider content-position="left">HTTP 请求配置</el-divider>
            <el-form-item label="方法">
              <el-select v-model="(nodeData as any).method" size="small" @change="onDataChange">
                <el-option v-for="m in ['GET','POST','PUT','DELETE','PATCH']" :key="m" :label="m" :value="m" />
              </el-select>
            </el-form-item>
            <el-form-item label="URL">
              <el-input v-model="(nodeData as any).url" placeholder="https://api.example.com/data" @change="onDataChange" />
            </el-form-item>
            <el-form-item label="超时(ms)">
              <el-input-number v-model="(nodeData as any).timeout" :min="1000" :max="120000" :step="5000" size="small" @change="onDataChange" />
            </el-form-item>
            <el-form-item label="请求体">
              <el-input v-model="(nodeData as any).body" type="textarea" :rows="4" placeholder='{"key":"value"}' @change="onDataChange" />
            </el-form-item>
            <el-divider content-position="left">鉴权</el-divider>
            <el-form-item label="鉴权类型">
              <el-select v-model="authType" size="small" @change="onAuthTypeChange">
                <el-option label="无" value="no-auth" />
                <el-option label="API Key" value="api-key" />
                <el-option label="Bearer Token" value="bearer" />
              </el-select>
            </el-form-item>
            <template v-if="authType === 'api-key'">
              <el-form-item label="Header Name">
                <el-input v-model="authHeaderName" placeholder="X-API-Key" @change="onAuthChange" />
              </el-form-item>
              <el-form-item label="Header Value">
                <el-input v-model="authHeaderValue" placeholder="your-api-key" @change="onAuthChange" />
              </el-form-item>
            </template>
            <template v-if="authType === 'bearer'">
              <el-form-item label="Token">
                <el-input v-model="authToken" placeholder="your-token" @change="onAuthChange" />
              </el-form-item>
            </template>
          </template>

          <!-- Knowledge Retrieval 节点 -->
          <template v-if="nodeData.type === NodeType.KnowledgeRetrieval">
            <el-divider content-position="left">知识检索配置</el-divider>
            <el-form-item label="知识库 ID">
              <el-input v-model="(nodeData as any).knowledgeBaseId" placeholder="输入知识库 ID" @change="onDataChange" />
            </el-form-item>
            <el-form-item label="Top K">
              <el-input-number v-model="(nodeData as any).topK" :min="1" :max="50" size="small" @change="onDataChange" />
            </el-form-item>
            <el-form-item label="分数阈值">
              <el-slider v-model="(nodeData as any).scoreThreshold" :min="0" :max="1" :step="0.05" show-input @change="onDataChange" />
            </el-form-item>
          </template>

          <!-- Variable Assigner 节点 -->
          <template v-if="nodeData.type === NodeType.VariableAssigner">
            <el-divider content-position="left">变量赋值</el-divider>
            <div v-for="(a, i) in (nodeData as any).assignments" :key="a.id" class="var-row">
              <el-input v-model="a.variable" placeholder="变量名" size="small" @change="onDataChange" />
              <span style="color:#98a2b3;margin:0 4px">&larr;</span>
              <el-input v-model="a.valueSelector.outputKey" placeholder="来源字段" size="small" @change="onDataChange" />
              <el-button type="danger" size="small" circle @click="(nodeData as any).assignments.splice(i, 1); onDataChange()">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <el-button size="small" @click="addAssignment">+ 添加赋值</el-button>
          </template>

          <!-- Variable Aggregator 节点 -->
          <template v-if="nodeData.type === NodeType.VariableAggregator">
            <el-divider content-position="left">变量聚合</el-divider>
            <div v-for="(v, i) in (nodeData as any).variables" :key="i" class="var-row">
              <el-input v-model="v.nodeId" placeholder="节点ID" size="small" @change="onDataChange" />
              <span style="color:#98a2b3">.</span>
              <el-input v-model="v.outputKey" placeholder="字段" size="small" @change="onDataChange" />
              <el-button type="danger" size="small" circle @click="(nodeData as any).variables.splice(i, 1); onDataChange()">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <el-button size="small" @click="(nodeData as any).variables.push({nodeId:'',outputKey:''}); onDataChange()">+ 添加变量</el-button>
            <el-form-item label="输出类型" style="margin-top:12px">
              <el-select v-model="(nodeData as any).outputType" size="small" @change="onDataChange">
                <el-option v-for="t in varTypes" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </template>

          <!-- Answer 节点 -->
          <template v-if="nodeData.type === NodeType.Answer">
            <el-divider content-position="left">回复配置</el-divider>
            <div class="end-hint">选择上游节点的输出作为最终回复</div>
            <el-form-item label="来源节点字段" style="margin-top:8px">
              <el-input v-model="(nodeData as any).answer.outputKey" placeholder="如 text" @change="onDataChange" />
            </el-form-item>
          </template>

          <!-- Template Transform 节点 -->
          <template v-if="nodeData.type === NodeType.TemplateTransform">
            <el-divider content-position="left">模板配置</el-divider>
            <el-form-item label="模板内容">
              <el-input v-model="(nodeData as any).template" type="textarea" :rows="6" placeholder="使用 ${nodeId.key} 引用变量" @change="onDataChange" />
            </el-form-item>
          </template>

          <!-- Iteration 节点 -->
          <template v-if="nodeData.type === NodeType.Iteration">
            <el-divider content-position="left">迭代配置</el-divider>
            <el-form-item label="迭代变量 - 节点ID">
              <el-input v-model="(nodeData as any).iterator.nodeId" placeholder="节点ID" @change="onDataChange" />
            </el-form-item>
            <el-form-item label="迭代变量 - 字段">
              <el-input v-model="(nodeData as any).iterator.outputKey" placeholder="输出字段" @change="onDataChange" />
            </el-form-item>
            <el-form-item label="输出类型">
              <el-select v-model="(nodeData as any).outputType" size="small" @change="onDataChange">
                <el-option v-for="t in varTypes" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </template>

          <!-- Parameter Extractor 节点 -->
          <template v-if="nodeData.type === NodeType.ParameterExtractor">
            <el-divider content-position="left">参数提取配置</el-divider>
            <el-form-item label="模型">
              <el-input v-model="(nodeData as any).model" placeholder="如 gpt-4o" @change="onDataChange" />
            </el-form-item>
            <el-form-item label="查询 - 节点ID">
              <el-input v-model="(nodeData as any).query.nodeId" placeholder="节点ID" @change="onDataChange" />
            </el-form-item>
            <el-form-item label="查询 - 字段">
              <el-input v-model="(nodeData as any).query.outputKey" placeholder="输出字段" @change="onDataChange" />
            </el-form-item>
            <el-form-item label="指令">
              <el-input v-model="(nodeData as any).instruction" type="textarea" :rows="3" placeholder="提取参数的指令..." @change="onDataChange" />
            </el-form-item>
            <el-divider content-position="left">提取参数</el-divider>
            <div v-for="(p, i) in (nodeData as any).parameters" :key="i" class="var-row">
              <el-input v-model="p.variable" placeholder="参数名" size="small" @change="onDataChange" />
              <el-select v-model="p.type" placeholder="类型" size="small" style="width: 100px" @change="onDataChange">
                <el-option v-for="t in varTypes" :key="t" :label="t" :value="t" />
              </el-select>
              <el-button type="danger" size="small" circle @click="(nodeData as any).parameters.splice(i, 1); onDataChange()">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <el-button size="small" @click="(nodeData as any).parameters.push({variable:'',type:'string'}); onDataChange()">+ 添加参数</el-button>
          </template>

          <!-- Question Classifier 节点 -->
          <template v-if="nodeData.type === NodeType.QuestionClassifier">
            <el-divider content-position="left">问题分类配置</el-divider>
            <el-form-item label="模型">
              <el-input v-model="(nodeData as any).model" placeholder="如 gpt-4o" @change="onDataChange" />
            </el-form-item>
            <el-form-item label="查询 - 节点ID">
              <el-input v-model="(nodeData as any).query.nodeId" placeholder="节点ID" @change="onDataChange" />
            </el-form-item>
            <el-form-item label="查询 - 字段">
              <el-input v-model="(nodeData as any).query.outputKey" placeholder="输出字段" @change="onDataChange" />
            </el-form-item>
            <el-divider content-position="left">分类</el-divider>
            <div v-for="(cat, i) in (nodeData as any).categories" :key="cat.id" class="cond-row">
              <el-input v-model="cat.name" placeholder="分类名" size="small" @change="onDataChange" />
              <el-input v-model="cat.description" placeholder="描述" size="small" @change="onDataChange" />
              <el-button type="danger" size="small" circle @click="(nodeData as any).categories.splice(i, 1); onDataChange()">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <el-button size="small" @click="addCategory">+ 添加分类</el-button>
          </template>

          <!-- Tool 节点 -->
          <template v-if="nodeData.type === NodeType.Tool">
            <el-divider content-position="left">工具配置</el-divider>
            <el-form-item label="工具 ID">
              <el-input v-model="(nodeData as any).toolId" placeholder="工具唯一标识" @change="onDataChange" />
            </el-form-item>
            <el-form-item label="工具名称">
              <el-input v-model="(nodeData as any).toolName" placeholder="工具显示名称" @change="onDataChange" />
            </el-form-item>
            <el-form-item label="提供者">
              <el-input v-model="(nodeData as any).providerName" placeholder="如 openai、google" @change="onDataChange" />
            </el-form-item>
          </template>

          <!-- Doc Extractor 节点 -->
          <template v-if="nodeData.type === NodeType.DocExtractor">
            <el-divider content-position="left">文档提取配置</el-divider>
            <el-form-item label="输入 - 节点ID">
              <el-input v-model="(nodeData as any).input.nodeId" placeholder="节点ID" @change="onDataChange" />
            </el-form-item>
            <el-form-item label="输入 - 字段">
              <el-input v-model="(nodeData as any).input.outputKey" placeholder="输出字段" @change="onDataChange" />
            </el-form-item>
            <el-form-item label="输出格式">
              <el-select v-model="(nodeData as any).outputType" size="small" @change="onDataChange">
                <el-option label="纯文本" value="text" />
                <el-option label="Markdown" value="markdown" />
              </el-select>
            </el-form-item>
          </template>

          <!-- List Filter 节点 -->
          <template v-if="nodeData.type === NodeType.ListFilter">
            <el-divider content-position="left">列表过滤配置</el-divider>
            <el-form-item label="输入 - 节点ID">
              <el-input v-model="(nodeData as any).input.nodeId" placeholder="节点ID" @change="onDataChange" />
            </el-form-item>
            <el-form-item label="输入 - 字段">
              <el-input v-model="(nodeData as any).input.outputKey" placeholder="输出字段" @change="onDataChange" />
            </el-form-item>
            <el-form-item label="过滤字段">
              <el-input v-model="(nodeData as any).filterCondition.key" placeholder="过滤的 key" @change="onDataChange" />
            </el-form-item>
            <el-form-item label="比较运算符">
              <el-select v-model="(nodeData as any).filterCondition.comparisonOperator" size="small" @change="onDataChange">
                <el-option v-for="op in comparisonOperators" :key="op" :label="op" :value="op" />
              </el-select>
            </el-form-item>
            <el-form-item label="值">
              <el-input v-model="(nodeData as any).filterCondition.value" placeholder="比较值" @change="onDataChange" />
            </el-form-item>
            <el-form-item label="输出类型">
              <el-select v-model="(nodeData as any).outputType" size="small" @change="onDataChange">
                <el-option v-for="t in varTypes" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </template>

          <!-- Agent 节点 -->
          <template v-if="nodeData.type === NodeType.Agent">
            <el-divider content-position="left">Agent 配置</el-divider>
            <el-form-item label="模型">
              <el-input v-model="(nodeData as any).model" placeholder="如 gpt-4o" @change="onDataChange" />
            </el-form-item>
            <el-form-item label="系统提示词">
              <el-input v-model="(nodeData as any).promptTemplate" type="textarea" :rows="4" placeholder="Agent 的指令..." @change="onDataChange" />
            </el-form-item>
            <el-form-item label="最大迭代次数">
              <el-input-number v-model="(nodeData as any).maxIterations" :min="1" :max="50" size="small" @change="onDataChange" />
            </el-form-item>
            <el-divider content-position="left">可用工具</el-divider>
            <div v-for="(tool, i) in (nodeData as any).tools" :key="i" class="var-row">
              <el-input :model-value="tool" placeholder="工具ID" size="small" @update:model-value="(v: string) => { (nodeData as any).tools[i] = v; onDataChange() }" />
              <el-button type="danger" size="small" circle @click="(nodeData as any).tools.splice(i, 1); onDataChange()">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <el-button size="small" @click="(nodeData as any).tools.push(''); onDataChange()">+ 添加工具</el-button>
          </template>

          <!-- Loop 节点 -->
          <template v-if="nodeData.type === NodeType.Loop">
            <el-divider content-position="left">循环配置</el-divider>
            <el-form-item label="条件变量 - 节点ID">
              <el-input v-model="(nodeData as any).loopCondition.variableSelector.nodeId" placeholder="节点ID" @change="onDataChange" />
            </el-form-item>
            <el-form-item label="条件变量 - 字段">
              <el-input v-model="(nodeData as any).loopCondition.variableSelector.outputKey" placeholder="字段" @change="onDataChange" />
            </el-form-item>
            <el-form-item label="比较运算符">
              <el-select v-model="(nodeData as any).loopCondition.comparisonOperator" size="small" @change="onDataChange">
                <el-option v-for="op in comparisonOperators" :key="op" :label="op" :value="op" />
              </el-select>
            </el-form-item>
            <el-form-item label="值">
              <el-input v-model="(nodeData as any).loopCondition.value" placeholder="比较值" @change="onDataChange" />
            </el-form-item>
            <el-form-item label="最大迭代次数">
              <el-input-number v-model="(nodeData as any).maxIterations" :min="1" :max="10000" size="small" @change="onDataChange" />
            </el-form-item>
          </template>

          <!-- Human Input 节点 -->
          <template v-if="nodeData.type === NodeType.HumanInput">
            <el-divider content-position="left">人工介入配置</el-divider>
            <el-form-item label="超时(秒)">
              <el-input-number v-model="(nodeData as any).timeout" :min="60" :max="86400" size="small" @change="onDataChange" />
            </el-form-item>
            <el-divider content-position="left">表单字段</el-divider>
            <div v-for="(field, i) in (nodeData as any).inputForm" :key="field.id" class="cond-row" style="flex-wrap:wrap">
              <el-input v-model="field.variable" placeholder="变量名" size="small" style="width:100px" @change="onDataChange" />
              <el-select v-model="field.type" size="small" style="width:90px" @change="onDataChange">
                <el-option label="文本" value="text" />
                <el-option label="数字" value="number" />
                <el-option label="选择" value="select" />
                <el-option label="布尔" value="boolean" />
              </el-select>
              <el-input v-model="field.label" placeholder="标签" size="small" @change="onDataChange" />
              <el-button type="danger" size="small" circle @click="(nodeData as any).inputForm.splice(i, 1); onDataChange()">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <el-button size="small" @click="addHumanInputField">+ 添加字段</el-button>
          </template>
        </el-form>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { Delete } from '@element-plus/icons-vue'
import {
  NodeType,
  VarType,
  NODE_TYPE_LABELS,
  NODE_TYPE_ICONS,
  NODE_TYPE_COLORS,
  type CommonNodeData,
  type StartNodeData,
  type LLMNodeData,
  type IfElseNodeData,
  type ComparisonOperator,
  type HttpRequestNodeData,
} from './types'
import { useWorkflowStore } from './store'
import { useVueFlow } from '@vue-flow/core'

const store = useWorkflowStore()
const { findNode } = useVueFlow({ id: 'workflow-canvas' })

/** 节点数据本地编辑副本 */
const nodeData = ref<CommonNodeData | null>(null)

const varTypes = Object.values(VarType)

const comparisonOperators: ComparisonOperator[] = [
  'contains', 'not contains', 'start with', 'end with',
  'is', 'is not', 'empty', 'not empty',
  '=', '!=', '>', '<', '>=', '<=',
]

/** HTTP 授权辅助状态 */
const authType = ref<'no-auth' | 'api-key' | 'bearer'>('no-auth')
const authHeaderName = ref('')
const authHeaderValue = ref('')
const authToken = ref('')

const panelTitle = computed(() => {
  if (!nodeData.value) return '节点配置'
  return `${NODE_TYPE_LABELS[nodeData.value.type] ?? '节点'} 配置`
})

const nodeIcon = computed(() => {
  if (!nodeData.value) return '?'
  return NODE_TYPE_ICONS[nodeData.value.type] ?? '?'
})

const nodeIconColor = computed(() => {
  if (!nodeData.value) return '#98a2b3'
  return NODE_TYPE_COLORS[nodeData.value.type] ?? '#98a2b3'
})

const nodeIconBgColor = computed(() => {
  if (!nodeData.value) return '#f2f4f7'
  return (NODE_TYPE_COLORS[nodeData.value.type] ?? '#98a2b3') + '18'
})

const nodeIconFontSize = computed(() => {
  const icon = nodeIcon.value
  if (icon.length > 2) return '10px'
  if (icon.length > 1) return '11px'
  return '14px'
})

// 监听选中节点变化
watch(
  () => store.selectedNodeId,
  (id) => {
    if (id) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const node = findNode(id) as any
      if (node) {
        nodeData.value = { ...node.data } as CommonNodeData
        // 初始化 HTTP 授权状态
        if (nodeData.value.type === NodeType.HttpRequest) {
          const httpData = nodeData.value as HttpRequestNodeData
          if (httpData.authorization) {
            authType.value = httpData.authorization.type
            authHeaderName.value = httpData.authorization.headerName ?? ''
            authHeaderValue.value = httpData.authorization.headerValue ?? ''
            authToken.value = httpData.authorization.token ?? ''
          } else {
            authType.value = 'no-auth'
          }
        }
      }
    } else {
      nodeData.value = null
    }
  },
)

/** 将本地编辑副本同步回 VueFlow 节点 */
function onDataChange() {
  const id = store.selectedNodeId
  if (!id || !nodeData.value) return
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const node = findNode(id) as any
  if (node) {
    node.data = { ...nodeData.value }
  }
  store.dirty = true
}

// Start 节点变量操作
function addStartVar() {
  const d = nodeData.value as StartNodeData
  d.variables.push({ variable: '', type: VarType.String, label: '' })
  onDataChange()
}
function removeStartVar(index: number) {
  const d = nodeData.value as StartNodeData
  d.variables.splice(index, 1)
  onDataChange()
}

// IfElse 条件操作
function addCondition() {
  const d = nodeData.value as IfElseNodeData
  d.conditions.push({
    id: `cond_${Date.now()}`,
    variableSelector: { nodeId: '', outputKey: '' },
    comparisonOperator: 'contains',
    value: '',
  })
  onDataChange()
}
function removeCondition(index: number) {
  const d = nodeData.value as IfElseNodeData
  d.conditions.splice(index, 1)
  onDataChange()
}

function addAssignment() {
  const d = nodeData.value as any
  if (!d.assignments) d.assignments = []
  d.assignments.push({
    id: `assign_${Date.now()}`,
    variable: '',
    valueSelector: { nodeId: '', outputKey: '' },
  })
  onDataChange()
}

// QuestionClassifier 分类操作
function addCategory() {
  const d = nodeData.value as any
  if (!d.categories) d.categories = []
  d.categories.push({
    id: `cat_${Date.now()}`,
    name: '',
    description: '',
  })
  onDataChange()
}

// HumanInput 字段操作
function addHumanInputField() {
  const d = nodeData.value as any
  if (!d.inputForm) d.inputForm = []
  d.inputForm.push({
    id: `field_${Date.now()}`,
    variable: '',
    type: 'text',
    label: '',
    required: false,
  })
  onDataChange()
}

// HTTP 授权操作
function onAuthTypeChange() {
  onAuthChange()
}
function onAuthChange() {
  const d = nodeData.value as HttpRequestNodeData
  if (authType.value === 'no-auth') {
    d.authorization = undefined
  } else if (authType.value === 'api-key') {
    d.authorization = { type: 'api-key', headerName: authHeaderName.value, headerValue: authHeaderValue.value }
  } else if (authType.value === 'bearer') {
    d.authorization = { type: 'bearer', token: authToken.value }
  }
  onDataChange()
}
</script>

<style scoped>
.node-config-panel {
  width: 360px;
  min-width: 360px;
  height: 100%;
  background: #fff;
  border-left: 1px solid #e8eaed;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.node-config-panel__header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 16px 14px;
  border-bottom: 1px solid #f0f2f5;
  flex-shrink: 0;
}

.node-config-panel__header-icon {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-weight: 700;
}

.node-config-panel__title {
  flex: 1;
  font-size: 15px;
  font-weight: 600;
  color: #101828;
}

.node-config-panel__close {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #98a2b3;
  transition: background 0.15s, color 0.15s;
}

.node-config-panel__close:hover {
  background: #f2f4f7;
  color: #344054;
}

.node-config-panel__body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.config-panel__form {
  padding: 0;
}

.config-panel__form :deep(.el-form-item__label) {
  font-size: 13px;
  font-weight: 500;
  color: #344054;
  padding-bottom: 4px;
}

.config-panel__form :deep(.el-divider__text) {
  font-size: 12px;
  font-weight: 600;
  color: #667085;
  letter-spacing: 0.3px;
}

.config-panel__form :deep(.el-divider) {
  margin: 20px 0 12px;
}

.config-panel__form :deep(.el-input__wrapper),
.config-panel__form :deep(.el-textarea__inner) {
  border-radius: 8px;
}

.config-panel__form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.var-row,
.cond-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  padding: 8px 10px;
  background: #f9fafb;
  border-radius: 8px;
  border: 1px solid #f0f2f5;
}

.end-hint {
  color: #98a2b3;
  font-size: 12px;
  padding: 10px 12px;
  background: #f9fafb;
  border-radius: 8px;
  border: 1px dashed #e4e7ed;
}
</style>
