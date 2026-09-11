<template>
  <Handle type="target" :position="Position.Left" />
  <BaseNode :id="id" :data="data" :selected="selected">
    <div class="loop-node__info">
      <div v-if="loopData.loopCondition?.variableSelector?.outputKey" class="loop-node__item">
        <span class="loop-node__label">条件</span>
        <span class="loop-node__value">
          {{ loopData.loopCondition.variableSelector.outputKey }}
          {{ loopData.loopCondition.comparisonOperator }}
          {{ loopData.loopCondition.value || '?' }}
        </span>
      </div>
      <div class="loop-node__item">
        <span class="loop-node__label">最大次数</span>
        <span class="loop-node__value">{{ loopData.maxIterations }}</span>
      </div>
      <div v-if="!loopData.loopCondition?.variableSelector?.outputKey" class="loop-node__empty">点击配置循环</div>
    </div>
  </BaseNode>
  <Handle type="source" :position="Position.Right" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Position, Handle } from '@vue-flow/core'
import BaseNode from './BaseNode.vue'
import { type CommonNodeData, type LoopNodeData } from '../types'

const props = defineProps<{
  id: string
  data: CommonNodeData
  selected?: boolean
}>()

const loopData = computed(() => props.data as LoopNodeData)
</script>

<style scoped>
.loop-node__info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.loop-node__item {
  display: flex;
  align-items: center;
  gap: 4px;
}
.loop-node__label {
  font-size: 10px;
  padding: 1px 4px;
  border-radius: 3px;
  background: #fdf6ec;
  color: #e6a23c;
}
.loop-node__value {
  font-size: 12px;
  color: #303133;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.loop-node__empty {
  color: #c0c4cc;
  font-style: italic;
}
</style>
