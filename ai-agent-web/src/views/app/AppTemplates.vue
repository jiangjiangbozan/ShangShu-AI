<template>
  <div class="page">
    <el-card shadow="never">
      <div class="title-bar">
        <span class="title">应用模板</span>
        <span class="sub">基于模板一键创建 AI 应用，创建后可在「应用列表」中编辑配置并发布</span>
      </div>

      <el-row :gutter="16">
        <el-col v-for="tpl in TEMPLATES" :key="tpl.type + tpl.name" :span="8" class="tpl-col">
          <el-card shadow="hover" class="tpl-card">
            <div class="tpl-head">
              <el-tag :type="typeTag(tpl.type)" effect="dark">{{ typeLabel(tpl.type) }}</el-tag>
              <span class="tpl-name">{{ tpl.name }}</span>
            </div>
            <p class="tpl-desc">{{ tpl.description }}</p>
            <div class="tpl-actions">
              <el-button size="small" text @click="preview(tpl)">查看配置</el-button>
              <el-button size="small" type="primary" @click="useTemplate(tpl)">
                用模板创建
              </el-button>
            </div>
          </el-card>
        </el-col>
        <el-col :span="8" class="tpl-col">
          <el-card shadow="hover" class="tpl-card legacy-card">
            <div class="tpl-head">
              <el-tag type="info" effect="dark">场景入口</el-tag>
              <span class="tpl-name">数据分析</span>
            </div>
            <p class="tpl-desc">
              数据分析场景已迁移为 Agent 应用模板（NL2SQL 方向）。打开独立的探索式分析工作台。
            </p>
            <div class="tpl-actions">
              <el-button size="small" type="info" plain @click="router.push('/data-analysis')">
                打开数据分析工作台
              </el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <!-- 配置预览 / 模板创建 -->
    <el-dialog
      v-model="dlgVisible"
      :title="previewOnly ? '模板配置预览' : '用模板创建应用'"
      width="560px"
    >
      <el-form label-width="90px">
        <el-form-item label="模板" v-if="!previewOnly">
          <el-tag :type="typeTag(current.type)" effect="plain">{{ typeLabel(current.type) }}</el-tag>
        </el-form-item>
        <el-form-item label="名称" v-if="!previewOnly" required>
          <el-input v-model="form.name" placeholder="应用名称" />
        </el-form-item>
        <el-form-item label="描述" v-if="!previewOnly">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="配置 JSON">
          <el-input v-model="form.configJson" type="textarea" :rows="7" :disabled="previewOnly" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlgVisible = false">关闭</el-button>
        <el-button v-if="!previewOnly" type="primary" :loading="creating" @click="create">
          创建应用
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { createApp, type AppPayload, type AppType } from '@/api/apps'

const router = useRouter()

/** 官方模板：与后端 AiApplicationService 各类型 config_json 读取键对齐 */
interface TemplateDef {
  name: string
  type: AppType
  description: string
  configJson: string
}

const TEMPLATES: TemplateDef[] = [
  {
    name: '通用对话助手',
    type: 'chat',
    description: '直接接入大模型的多轮对话应用，适合通用问答、文案起草等场景。',
    configJson: '{}',
  },
  {
    name: '企业知识问答',
    type: 'rag',
    description: '基于知识库检索增强生成（RAG），回答附带知识库依据。需将 kbId 替换为你的知识库 ID。',
    configJson: '{\n  "kbId": 0,\n  "topK": 5\n}',
  },
  {
    name: '自主任务执行',
    type: 'agent',
    description: 'Agent 引擎（ReAct / Function Calling）驱动，自主规划并调用工具完成任务。需将 agentConfigId 替换为已配置的 Agent 配置 ID。',
    configJson: '{\n  "agentConfigId": 0\n}',
  },
  {
    name: '流程化任务执行',
    type: 'workflow',
    description: '触发可视化工作流同步执行并返回 End 节点输出。需将 workflowId 替换为已发布的工作流 ID。',
    configJson: '{\n  "workflowId": "",\n  "inputs": {}\n}',
  },
]

const dlgVisible = ref(false)
const previewOnly = ref(false)
const creating = ref(false)
const current = ref<TemplateDef>({
  name: '通用对话助手',
  type: 'chat',
  description: '',
  configJson: '{}',
})
const form = ref<AppPayload>({ name: '', type: 'chat', configJson: '{}', description: '' })

const TYPE_LABELS: Record<AppType, string> = {
  chat: 'Chat 对话',
  rag: 'RAG 问答',
  agent: 'Agent',
  workflow: 'Workflow',
}
const typeLabel = (t: string) => TYPE_LABELS[t as AppType] ?? t
const typeTag = (t: string) =>
  ({ chat: 'primary', rag: 'success', agent: 'warning', workflow: 'danger' }[t as AppType] ?? 'info')

function preview(tpl: TemplateDef) {
  current.value = tpl
  previewOnly.value = true
  dlgVisible.value = true
}

function useTemplate(tpl: TemplateDef) {
  current.value = tpl
  previewOnly.value = false
  form.value = {
    name: tpl.name,
    type: tpl.type,
    configJson: tpl.configJson,
    description: tpl.description,
  }
  dlgVisible.value = true
}

async function create() {
  if (!form.value.name.trim()) {
    ElMessage.warning('请填写应用名称')
    return
  }
  creating.value = true
  try {
    await createApp(form.value)
    ElMessage.success('应用已创建，可在应用列表中发布')
    dlgVisible.value = false
    router.push('/app-list')
  } finally {
    creating.value = false
  }
}
</script>

<style scoped>
.title-bar {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 16px;
}

.title {
  font-size: 16px;
  font-weight: 600;
}

.sub {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.tpl-col {
  margin-bottom: 16px;
}

.tpl-card {
  height: 100%;
}

.legacy-card {
  background: var(--el-fill-color-lighter);
}

.tpl-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tpl-name {
  font-weight: 600;
}

.tpl-desc {
  font-size: 13px;
  color: var(--el-text-color-regular);
  line-height: 1.7;
  min-height: 60px;
  margin: 10px 0;
}

.tpl-actions {
  display: flex;
  justify-content: flex-end;
}
</style>
