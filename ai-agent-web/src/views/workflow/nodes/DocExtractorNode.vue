<template>
  <Handle type="target" :position="Position.Left" />
  <BaseNode :id="id" :data="data" :selected="selected">
    <div class="docex-node__info">
      <div v-if="docexData.input?.outputKey" class="docex-node__item">
        <span class="docex-node__label">输入</span>
        <span class="docex-node__value">{{ docexData.input.nodeId }}.{{ docexData.input.outputKey }}</span>
      </div>
      <div class="docex-node__item">
        <span class="docex-node__label">输出格式</span>
        <span class="docex-node__value">{{ docexData.outputType }}</span>
      </div>
      <div v-if="!docexData.input?.outputKey" class="docex-node__empty">点击配置文档提取</div>
    </div>
  </BaseNode>
  <Handle type="source" :position="Position.Right" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Position, Handle } from '@vue-flow/core'
import BaseNode from './BaseNode.vue'
import { type CommonNodeData, type DocExtractorNodeData } from '../types'

const props = defineProps<{
  id: string
  data: CommonNodeData
  selected?: boolean
}>()

const docexData = computed(() => props.data as DocExtractorNodeData)
</script>

<style scoped>
.docex-node__info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.docex-node__item {
  display: flex;
  align-items: center;
  gap: 4px;
}
.docex-node__label {
  font-size: 10px;
  padding: 1px 4px;
  border-radius: 3px;
  background: #ecf5ff;
  color: #409eff;
}
.docex-node__value {
  font-size: 12px;
  color: #303133;
}
.docex-node__empty {
  color: #c0c4cc;
  font-style: italic;
}
</style>
