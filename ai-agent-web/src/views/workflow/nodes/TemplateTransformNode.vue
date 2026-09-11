<template>
  <Handle type="target" :position="Position.Left" />
  <BaseNode :id="id" :data="data" :selected="selected">
    <div class="tmpl-node__info">
      <div v-if="tmplData.template" class="tmpl-node__preview">{{ tmplData.template.slice(0, 80) }}{{ tmplData.template.length > 80 ? '...' : '' }}</div>
      <div v-else class="tmpl-node__empty">点击配置模板</div>
    </div>
  </BaseNode>
  <Handle type="source" :position="Position.Right" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Position, Handle } from '@vue-flow/core'
import BaseNode from './BaseNode.vue'
import { type CommonNodeData, type TemplateTransformNodeData } from '../types'

const props = defineProps<{
  id: string
  data: CommonNodeData
  selected?: boolean
}>()

const tmplData = computed(() => props.data as TemplateTransformNodeData)
</script>

<style scoped>
.tmpl-node__info { display: flex; flex-direction: column; gap: 4px; }
.tmpl-node__preview { font-size: 11px; color: #909399; font-family: monospace; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tmpl-node__empty { color: #c0c4cc; font-style: italic; }
</style>
