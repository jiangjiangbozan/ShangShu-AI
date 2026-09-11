<template>
  <Handle type="target" :position="Position.Left" />
  <BaseNode :id="id" :data="data" :selected="selected">
    <div class="llm-node__info">
      <div v-if="llmData.model" class="llm-node__model">
        <span class="llm-node__label">模型</span>
        <span class="llm-node__value">{{ llmData.model }}</span>
      </div>
      <div v-if="llmData.promptTemplate" class="llm-node__prompt">
        {{ llmData.promptTemplate.slice(0, 60) }}{{ llmData.promptTemplate.length > 60 ? '...' : '' }}
      </div>
      <div v-if="!llmData.model && !llmData.promptTemplate" class="llm-node__empty">点击配置 LLM</div>
    </div>
  </BaseNode>
  <Handle type="source" :position="Position.Right" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Position, Handle } from '@vue-flow/core'
import BaseNode from './BaseNode.vue'
import { type CommonNodeData, type LLMNodeData } from '../types'

const props = defineProps<{
  id: string
  data: CommonNodeData
  selected?: boolean
}>()

const llmData = computed(() => props.data as LLMNodeData)
</script>

<style scoped>
.llm-node__info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.llm-node__model {
  display: flex;
  align-items: center;
  gap: 4px;
}
.llm-node__label {
  font-size: 10px;
  padding: 1px 4px;
  border-radius: 3px;
  background: #ede9fe;
  color: #6366f1;
}
.llm-node__value {
  font-size: 12px;
  color: #303133;
}
.llm-node__prompt {
  font-size: 11px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.llm-node__empty {
  color: #c0c4cc;
  font-style: italic;
}
</style>
