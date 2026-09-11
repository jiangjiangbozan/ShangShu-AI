<template>
  <Handle type="target" :position="Position.Left" />
  <BaseNode :id="id" :data="data" :selected="selected">
    <div class="human-input-node__info">
      <div v-if="hiData.inputForm?.length" class="human-input-node__fields">
        <span v-for="f in hiData.inputForm" :key="f.id" class="human-input-node__field-tag">
          {{ f.label || f.variable }}
        </span>
      </div>
      <div class="human-input-node__item">
        <span class="human-input-node__label">超时</span>
        <span class="human-input-node__value">{{ hiData.timeout }}s</span>
      </div>
      <div v-if="!hiData.inputForm?.length" class="human-input-node__empty">点击配置人工介入</div>
    </div>
  </BaseNode>
  <Handle type="source" :position="Position.Right" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Position, Handle } from '@vue-flow/core'
import BaseNode from './BaseNode.vue'
import { type CommonNodeData, type HumanInputNodeData } from '../types'

const props = defineProps<{
  id: string
  data: CommonNodeData
  selected?: boolean
}>()

const hiData = computed(() => props.data as HumanInputNodeData)
</script>

<style scoped>
.human-input-node__info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.human-input-node__fields {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.human-input-node__field-tag {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 3px;
  background: #fef0f0;
  color: #f56c6c;
}
.human-input-node__item {
  display: flex;
  align-items: center;
  gap: 4px;
}
.human-input-node__label {
  font-size: 10px;
  padding: 1px 4px;
  border-radius: 3px;
  background: #f0f2f5;
  color: #909399;
}
.human-input-node__value {
  font-size: 12px;
  color: #303133;
}
.human-input-node__empty {
  color: #c0c4cc;
  font-style: italic;
}
</style>
