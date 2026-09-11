<template>
  <Handle type="target" :position="Position.Left" />
  <BaseNode :id="id" :data="data" :selected="selected">
    <div class="kb-node__info">
      <div v-if="kbData.knowledgeBaseId" class="kb-node__id">KB: {{ kbData.knowledgeBaseId }}</div>
      <div v-if="kbData.topK" class="kb-node__topk">Top {{ kbData.topK }}</div>
      <div v-if="!kbData.knowledgeBaseId" class="kb-node__empty">点击配置知识库</div>
    </div>
  </BaseNode>
  <Handle type="source" :position="Position.Right" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Position, Handle } from '@vue-flow/core'
import BaseNode from './BaseNode.vue'
import { type CommonNodeData, type KnowledgeRetrievalNodeData } from '../types'

const props = defineProps<{
  id: string
  data: CommonNodeData
  selected?: boolean
}>()

const kbData = computed(() => props.data as KnowledgeRetrievalNodeData)
</script>

<style scoped>
.kb-node__info { display: flex; flex-direction: column; gap: 4px; }
.kb-node__id { font-size: 11px; color: #303133; }
.kb-node__topk { font-size: 10px; color: #909399; }
.kb-node__empty { color: #c0c4cc; font-style: italic; }
</style>
