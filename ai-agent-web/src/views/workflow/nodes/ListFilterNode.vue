<template>
  <Handle type="target" :position="Position.Left" />
  <BaseNode :id="id" :data="data" :selected="selected">
    <div class="listfilter-node__info">
      <div v-if="lfData.input?.outputKey" class="listfilter-node__item">
        <span class="listfilter-node__label">输入</span>
        <span class="listfilter-node__value">{{ lfData.input.nodeId }}.{{ lfData.input.outputKey }}</span>
      </div>
      <div v-if="lfData.filterCondition?.key" class="listfilter-node__item">
        <span class="listfilter-node__label">条件</span>
        <span class="listfilter-node__value">{{ lfData.filterCondition.key }} {{ lfData.filterCondition.comparisonOperator }} {{ lfData.filterCondition.value }}</span>
      </div>
      <div v-if="!lfData.input?.outputKey" class="listfilter-node__empty">点击配置列表过滤</div>
    </div>
  </BaseNode>
  <Handle type="source" :position="Position.Right" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Position, Handle } from '@vue-flow/core'
import BaseNode from './BaseNode.vue'
import { type CommonNodeData, type ListFilterNodeData } from '../types'

const props = defineProps<{
  id: string
  data: CommonNodeData
  selected?: boolean
}>()

const lfData = computed(() => props.data as ListFilterNodeData)
</script>

<style scoped>
.listfilter-node__info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.listfilter-node__item {
  display: flex;
  align-items: center;
  gap: 4px;
}
.listfilter-node__label {
  font-size: 10px;
  padding: 1px 4px;
  border-radius: 3px;
  background: #ecf5ff;
  color: #409eff;
}
.listfilter-node__value {
  font-size: 12px;
  color: #303133;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.listfilter-node__empty {
  color: #c0c4cc;
  font-style: italic;
}
</style>
