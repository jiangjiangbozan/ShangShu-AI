<script setup lang="ts">
/**
 * 模型配置编辑对话框
 *
 * 支持新增/编辑模型配置，含 provider 选择、apiKey 密码框、baseUrl、modelId、parameters JSON 编辑器。
 */
import { ref, watch, computed } from 'vue'
import {
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElSelect,
  ElOption,
  ElSwitch,
  ElInputNumber,
  ElMessage,
} from 'element-plus'
import type { ModelConfigReq, ModelConfigResp } from '@/api/model-config'

const props = defineProps<{
  visible: boolean
  /** 编辑时传入已有配置，新增时为 null */
  config: ModelConfigResp | null
}>()

const emit = defineEmits<{
  'update:visible': [val: boolean]
  submit: [req: ModelConfigReq]
}>()

const formRef = ref<InstanceType<typeof ElForm>>()

const form = ref<ModelConfigReq>({
  provider: 'deepseek',
  modelId: '',
  displayName: '',
  apiKey: '',
  baseUrl: '',
  enabled: 1,
  parameters: '',
  sortOrder: 0,
})

/** 是否为编辑模式 */
const isEdit = computed(() => props.config != null)

/** 对话框标题 */
const dialogTitle = computed(() => (isEdit.value ? '编辑模型配置' : '新增模型配置'))

/** 监听 config 变化，填充表单 */
watch(
  () => props.config,
  (val) => {
    if (val) {
      form.value = {
        provider: val.provider,
        modelId: val.modelId,
        displayName: val.displayName,
        apiKey: '',
        baseUrl: val.baseUrl || '',
        enabled: val.enabled,
        parameters: val.parameters || '',
        sortOrder: val.sortOrder,
      }
    } else {
      form.value = {
        provider: 'deepseek',
        modelId: '',
        displayName: '',
        apiKey: '',
        baseUrl: '',
        enabled: 1,
        parameters: '',
        sortOrder: 0,
      }
    }
  },
)

/** 提交 */
async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  emit('submit', { ...form.value })
}

/** 关闭 */
function handleClose() {
  emit('update:visible', false)
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="dialogTitle"
    width="520px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:visible', $event)"
    @close="handleClose"
  >
    <el-form ref="formRef" :model="form" label-position="top" label-width="auto">
      <el-form-item label="供应商" prop="provider" required>
        <el-select v-model="form.provider" style="width: 100%" :disabled="isEdit">
          <el-option label="DeepSeek" value="deepseek" />
          <el-option label="通义千问 (Qwen)" value="qwen" />
          <el-option label="Ollama" value="ollama" />
          <el-option label="OpenAI 兼容" value="openai" />
          <el-option label="其他" value="other" />
        </el-select>
      </el-form-item>

      <el-form-item
        label="模型 ID"
        prop="modelId"
        :rules="[{ required: true, message: '请输入模型 ID', trigger: 'blur' }]"
      >
        <el-input v-model="form.modelId" placeholder="如 deepseek-chat" maxlength="64" />
      </el-form-item>

      <el-form-item label="展示名称" prop="displayName">
        <el-input v-model="form.displayName" placeholder="用户可见的模型名称" maxlength="128" />
      </el-form-item>

      <el-form-item label="API Key" prop="apiKey">
        <el-input
          v-model="form.apiKey"
          type="password"
          show-password
          placeholder="留空则不修改"
          maxlength="256"
        />
      </el-form-item>

      <el-form-item label="Base URL" prop="baseUrl">
        <el-input v-model="form.baseUrl" placeholder="如 https://api.deepseek.com" maxlength="256" />
      </el-form-item>

      <el-form-item label="启用" prop="enabled">
        <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
      </el-form-item>

      <el-form-item label="模型参数 (JSON)" prop="parameters">
        <el-input
          v-model="form.parameters"
          type="textarea"
          :rows="3"
          placeholder='{"temperature": 0.7, "max_tokens": 2048}'
          maxlength="2048"
        />
      </el-form-item>

      <el-form-item label="排序权重" prop="sortOrder">
        <el-input-number v-model="form.sortOrder" :min="0" :max="999" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleSubmit">{{ isEdit ? '保存' : '创建' }}</el-button>
    </template>
  </el-dialog>
</template>