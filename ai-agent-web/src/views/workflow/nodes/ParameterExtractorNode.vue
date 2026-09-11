<template>
  <Handle type="target" :position="Position.Left" />
  <BaseNode :id="id" :data="data" :selected="selected">
    <div class="param-node__info">
      <div v-if="paramData.model" class="param-node__item">
        <span class="param-node__label">模型</span>
        <span class="param-node__value">{{ paramData.model }}</span>
      </div>
      <div v-if="paramData.parameters?.length" class="param-node__params">
        <span v-for="p in paramData.parameters" :key="p.variable" class="param-node__param-tag">{{ p.variable }}</span>
      </div>
      <div v-if="!paramData.model" class="param-node__empty">点击配置参数提取</div>
    </div>
  </BaseNode>
  <Handle type="source" :position="Position.Right" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Position, Handle } from '@vue-flow/core'
import BaseNode from './BaseNode.vue'
import { type CommonNodeData, type ParameterExtractorNodeData } from '../types'

const props = defineProps<{
  id: string
  data: CommonNodeData
  selected?: boolean
}>()

const paramData = computed(() => props.data as ParameterExtractorNodeData)
</script>

<style scoped>
.param-node__info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.param-node__item {
  display: flex;
  align-items: center;
  gap: 4px;
}
.param-node__label {
  font-size: 10px;
  padding: 1px 4px;
  border-radius: 3px;
  background: #ede9fe;
  color: #6366f1;
}
.param-node__value {
  font-size: 12px;
  color: #303133;
}
.param-node__params {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.param-node__param-tag {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 3px;
  background: #f0f2f5;
  color: #606266;
}
.param-node__empty {
  color: #c0c4cc;
  font-style: italic;
}
</style>
