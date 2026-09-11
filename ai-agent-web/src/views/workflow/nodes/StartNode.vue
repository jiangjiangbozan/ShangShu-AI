<template>
  <BaseNode :id="id" :data="data" :selected="selected">
    <div class="start-node__vars">
      <div v-for="v in startData.variables" :key="v.variable" class="start-node__var">
        <span class="start-node__var-type">{{ v.type }}</span>
        <span class="start-node__var-name">{{ v.label || v.variable }}</span>
      </div>
      <div v-if="!startData.variables?.length" class="start-node__empty">点击配置输入变量</div>
    </div>
  </BaseNode>
  <Handle type="source" :position="Position.Right" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Position, Handle } from '@vue-flow/core'
import BaseNode from './BaseNode.vue'
import { type CommonNodeData, type StartNodeData, NodeType } from '../types'

const props = defineProps<{
  id: string
  data: CommonNodeData
  selected?: boolean
}>()

const startData = computed(() => props.data as StartNodeData)
</script>

<style scoped>
.start-node__vars {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.start-node__var {
  display: flex;
  align-items: center;
  gap: 6px;
}
.start-node__var-type {
  font-size: 10px;
  padding: 1px 4px;
  border-radius: 3px;
  background: #f0f2f5;
  color: #909399;
}
.start-node__var-name {
  font-size: 12px;
  color: #303133;
}
.start-node__empty {
  color: #c0c4cc;
  font-style: italic;
}
</style>
