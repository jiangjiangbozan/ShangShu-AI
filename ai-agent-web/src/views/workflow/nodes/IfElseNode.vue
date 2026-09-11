<template>
  <Handle type="target" :position="Position.Left" />
  <BaseNode :id="id" :data="data" :selected="selected">
    <div class="ifelse-node__conditions">
      <div v-for="(cond, i) in ifElseData.conditions" :key="cond.id" class="ifelse-node__cond">
        <span class="ifelse-node__branch-label">{{ i === 0 ? 'IF' : 'ELIF' }}</span>
        <span class="ifelse-node__cond-text">{{ cond.variableSelector?.outputKey || '?' }} {{ cond.comparisonOperator }} {{ cond.value || '?' }}</span>
      </div>
      <div v-if="!ifElseData.conditions?.length" class="ifelse-node__empty">点击配置条件</div>
      <div class="ifelse-node__else">
        <span class="ifelse-node__branch-label ifelse-node__branch-label--else">ELSE</span>
      </div>
    </div>
  </BaseNode>
  <!-- IF 分支输出 -->
  <Handle type="source" :position="Position.Right" :id="ifHandleId" style="top: 35%" />
  <!-- ELSE 分支输出 -->
  <Handle type="source" :position="Position.Right" :id="elseHandleId" style="top: 65%" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Position, Handle } from '@vue-flow/core'
import BaseNode from './BaseNode.vue'
import { type CommonNodeData, type IfElseNodeData } from '../types'

const props = defineProps<{
  id: string
  data: CommonNodeData
  selected?: boolean
}>()

const ifElseData = computed(() => props.data as IfElseNodeData)
const ifHandleId = 'if'
const elseHandleId = 'else'
</script>

<style scoped>
.ifelse-node__conditions {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ifelse-node__cond {
  display: flex;
  align-items: center;
  gap: 6px;
}
.ifelse-node__branch-label {
  font-size: 10px;
  font-weight: 700;
  padding: 1px 4px;
  border-radius: 3px;
  background: #fdf6ec;
  color: #e6a23c;
}
.ifelse-node__branch-label--else {
  background: #f0f2f5;
  color: #909399;
}
.ifelse-node__cond-text {
  font-size: 11px;
  color: #606266;
}
.ifelse-node__empty {
  color: #c0c4cc;
  font-style: italic;
}
.ifelse-node__else {
  margin-top: 4px;
}
</style>
