<template>
  <Handle type="target" :position="Position.Left" />
  <BaseNode :id="id" :data="data" :selected="selected">
    <div class="varagg-node__info">
      <div v-if="aggData.variables?.length" class="varagg-node__vars">
        <span class="varagg-node__label">聚合变量</span>
        <span class="varagg-node__count">{{ aggData.variables.length }} 个</span>
      </div>
      <div v-if="aggData.outputType" class="varagg-node__item">
        <span class="varagg-node__label">输出类型</span>
        <span class="varagg-node__value">{{ aggData.outputType }}</span>
      </div>
      <div v-if="!aggData.variables?.length" class="varagg-node__empty">点击配置变量聚合</div>
    </div>
  </BaseNode>
  <Handle type="source" :position="Position.Right" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Position, Handle } from '@vue-flow/core'
import BaseNode from './BaseNode.vue'
import { type CommonNodeData, type VariableAggregatorNodeData } from '../types'

const props = defineProps<{
  id: string
  data: CommonNodeData
  selected?: boolean
}>()

const aggData = computed(() => props.data as VariableAggregatorNodeData)
</script>

<style scoped>
.varagg-node__info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.varagg-node__vars {
  display: flex;
  align-items: center;
  gap: 4px;
}
.varagg-node__item {
  display: flex;
  align-items: center;
  gap: 4px;
}
.varagg-node__label {
  font-size: 10px;
  padding: 1px 4px;
  border-radius: 3px;
  background: #f0f2f5;
  color: #909399;
}
.varagg-node__value,
.varagg-node__count {
  font-size: 12px;
  color: #303133;
}
.varagg-node__empty {
  color: #c0c4cc;
  font-style: italic;
}
</style>
