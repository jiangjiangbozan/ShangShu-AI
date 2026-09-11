<template>
  <Handle type="target" :position="Position.Left" />
  <BaseNode :id="id" :data="data" :selected="selected">
    <div class="answer-node__info">
      <div class="answer-node__icon">💬</div>
      <div v-if="ansData.answer?.outputKey" class="answer-node__src">输出: {{ ansData.answer.outputKey }}</div>
      <div v-else class="answer-node__empty">点击配置回复来源</div>
    </div>
  </BaseNode>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Position, Handle } from '@vue-flow/core'
import BaseNode from './BaseNode.vue'
import { type CommonNodeData, type AnswerNodeData } from '../types'

const props = defineProps<{
  id: string
  data: CommonNodeData
  selected?: boolean
}>()

const ansData = computed(() => props.data as AnswerNodeData)
</script>

<style scoped>
.answer-node__info { display: flex; align-items: center; gap: 6px; }
.answer-node__icon { font-size: 14px; }
.answer-node__src { font-size: 11px; color: #6366f1; }
.answer-node__empty { color: #c0c4cc; font-style: italic; }
</style>
