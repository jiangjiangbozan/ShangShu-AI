<template>
  <Handle type="target" :position="Position.Left" />
  <BaseNode :id="id" :data="data" :selected="selected">
    <div class="end-node__outputs">
      <div v-for="(_, key) in endData.outputs" :key="key" class="end-node__output">
        <span class="end-node__output-key">{{ key }}</span>
      </div>
      <div v-if="!Object.keys(endData.outputs || {}).length" class="end-node__empty">点击配置输出</div>
    </div>
  </BaseNode>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Position, Handle } from '@vue-flow/core'
import BaseNode from './BaseNode.vue'
import { type CommonNodeData, type EndNodeData } from '../types'

const props = defineProps<{
  id: string
  data: CommonNodeData
  selected?: boolean
}>()

const endData = computed(() => props.data as EndNodeData)
</script>

<style scoped>
.end-node__outputs {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.end-node__output {
  display: flex;
  align-items: center;
  gap: 4px;
}
.end-node__output-key {
  font-size: 12px;
  color: #303133;
}
.end-node__empty {
  color: #c0c4cc;
  font-style: italic;
}
</style>
