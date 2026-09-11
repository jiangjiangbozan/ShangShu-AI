<template>
  <Handle type="target" :position="Position.Left" />
  <BaseNode :id="id" :data="data" :selected="selected">
    <div class="var-node__info">
      <div v-for="a in varData.assignments" :key="a.variable" class="var-node__item">
        <span class="var-node__name">{{ a.variable || '?' }}</span>
        <span class="var-node__arrow">&larr;</span>
        <span class="var-node__src">{{ (a.valueSelector as any)?.outputKey || '?' }}</span>
      </div>
      <div v-if="!varData.assignments?.length" class="var-node__empty">点击配置赋值</div>
    </div>
  </BaseNode>
  <Handle type="source" :position="Position.Right" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Position, Handle } from '@vue-flow/core'
import BaseNode from './BaseNode.vue'
import { type CommonNodeData, type VariableAssignerNodeData } from '../types'

const props = defineProps<{
  id: string
  data: CommonNodeData
  selected?: boolean
}>()

const varData = computed(() => props.data as VariableAssignerNodeData)
</script>

<style scoped>
.var-node__info { display: flex; flex-direction: column; gap: 4px; }
.var-node__item { display: flex; align-items: center; gap: 4px; font-size: 11px; }
.var-node__name { color: #303133; font-weight: 600; }
.var-node__arrow { color: #909399; }
.var-node__src { color: #6366f1; }
.var-node__empty { color: #c0c4cc; font-style: italic; }
</style>
