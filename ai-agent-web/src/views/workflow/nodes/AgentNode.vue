<template>
  <Handle type="target" :position="Position.Left" />
  <BaseNode :id="id" :data="data" :selected="selected">
    <div class="agent-node__info">
      <div v-if="agentData.model" class="agent-node__item">
        <span class="agent-node__label">模型</span>
        <span class="agent-node__value">{{ agentData.model }}</span>
      </div>
      <div v-if="agentData.tools?.length" class="agent-node__item">
        <span class="agent-node__label">工具</span>
        <span class="agent-node__value">{{ agentData.tools.length }} 个</span>
      </div>
      <div v-if="agentData.promptTemplate" class="agent-node__prompt">
        {{ agentData.promptTemplate.slice(0, 50) }}{{ agentData.promptTemplate.length > 50 ? '...' : '' }}
      </div>
      <div v-if="!agentData.model" class="agent-node__empty">点击配置 Agent</div>
    </div>
  </BaseNode>
  <Handle type="source" :position="Position.Right" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Position, Handle } from '@vue-flow/core'
import BaseNode from './BaseNode.vue'
import { type CommonNodeData, type AgentNodeData } from '../types'

const props = defineProps<{
  id: string
  data: CommonNodeData
  selected?: boolean
}>()

const agentData = computed(() => props.data as AgentNodeData)
</script>

<style scoped>
.agent-node__info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.agent-node__item {
  display: flex;
  align-items: center;
  gap: 4px;
}
.agent-node__label {
  font-size: 10px;
  padding: 1px 4px;
  border-radius: 3px;
  background: #ede9fe;
  color: #6366f1;
}
.agent-node__value {
  font-size: 12px;
  color: #303133;
}
.agent-node__prompt {
  font-size: 11px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.agent-node__empty {
  color: #c0c4cc;
  font-style: italic;
}
</style>
