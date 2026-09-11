<template>
  <Handle type="target" :position="Position.Left" />
  <BaseNode :id="id" :data="data" :selected="selected">
    <div class="iteration-node__info">
      <div v-if="iterData.iterator?.outputKey" class="iteration-node__item">
        <span class="iteration-node__label">迭代对象</span>
        <span class="iteration-node__value">{{ iterData.iterator.nodeId }}.{{ iterData.iterator.outputKey }}</span>
      </div>
      <div v-if="iterData.outputType" class="iteration-node__item">
        <span class="iteration-node__label">输出类型</span>
        <span class="iteration-node__value">{{ iterData.outputType }}</span>
      </div>
      <div v-if="!iterData.iterator?.outputKey" class="iteration-node__empty">点击配置迭代</div>
    </div>
  </BaseNode>
  <Handle type="source" :position="Position.Right" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Position, Handle } from '@vue-flow/core'
import BaseNode from './BaseNode.vue'
import { type CommonNodeData, type IterationNodeData } from '../types'

const props = defineProps<{
  id: string
  data: CommonNodeData
  selected?: boolean
}>()

const iterData = computed(() => props.data as IterationNodeData)
</script>

<style scoped>
.iteration-node__info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.iteration-node__item {
  display: flex;
  align-items: center;
  gap: 4px;
}
.iteration-node__label {
  font-size: 10px;
  padding: 1px 4px;
  border-radius: 3px;
  background: #fdf6ec;
  color: #e6a23c;
}
.iteration-node__value {
  font-size: 12px;
  color: #303133;
}
.iteration-node__empty {
  color: #c0c4cc;
  font-style: italic;
}
</style>
