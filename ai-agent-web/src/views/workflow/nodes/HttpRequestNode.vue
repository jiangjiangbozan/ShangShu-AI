<template>
  <Handle type="target" :position="Position.Left" />
  <BaseNode :id="id" :data="data" :selected="selected">
    <div class="http-node__info">
      <div class="http-node__method">{{ httpData.method || 'GET' }}</div>
      <div v-if="httpData.url" class="http-node__url">{{ httpData.url }}</div>
      <div v-if="!httpData.url" class="http-node__empty">点击配置请求</div>
    </div>
  </BaseNode>
  <Handle type="source" :position="Position.Right" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Position, Handle } from '@vue-flow/core'
import BaseNode from './BaseNode.vue'
import { type CommonNodeData, type HttpRequestNodeData } from '../types'

const props = defineProps<{
  id: string
  data: CommonNodeData
  selected?: boolean
}>()

const httpData = computed(() => props.data as HttpRequestNodeData)
</script>

<style scoped>
.http-node__info { display: flex; align-items: center; gap: 8px; }
.http-node__method { font-size: 10px; padding: 1px 6px; border-radius: 3px; background: #67C23A; color: #fff; font-weight: 700; }
.http-node__url { font-size: 11px; color: #606266; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 180px; }
.http-node__empty { color: #c0c4cc; font-style: italic; }
</style>
