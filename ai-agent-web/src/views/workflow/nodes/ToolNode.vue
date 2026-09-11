<template>
  <Handle type="target" :position="Position.Left" />
  <BaseNode :id="id" :data="data" :selected="selected">
    <div class="tool-node__info">
      <div v-if="toolData.toolName" class="tool-node__item">
        <span class="tool-node__label">工具</span>
        <span class="tool-node__value">{{ toolData.toolName }}</span>
      </div>
      <div v-if="toolData.providerName" class="tool-node__item">
        <span class="tool-node__label">来源</span>
        <span class="tool-node__value">{{ toolData.providerName }}</span>
      </div>
      <div v-if="!toolData.toolName" class="tool-node__empty">点击配置工具</div>
    </div>
  </BaseNode>
  <Handle type="source" :position="Position.Right" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Position, Handle } from '@vue-flow/core'
import BaseNode from './BaseNode.vue'
import { type CommonNodeData, type ToolNodeData } from '../types'

const props = defineProps<{
  id: string
  data: CommonNodeData
  selected?: boolean
}>()

const toolData = computed(() => props.data as ToolNodeData)
</script>

<style scoped>
.tool-node__info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.tool-node__item {
  display: flex;
  align-items: center;
  gap: 4px;
}
.tool-node__label {
  font-size: 10px;
  padding: 1px 4px;
  border-radius: 3px;
  background: #e8f5e9;
  color: #67c23a;
}
.tool-node__value {
  font-size: 12px;
  color: #303133;
}
.tool-node__empty {
  color: #c0c4cc;
  font-style: italic;
}
</style>
