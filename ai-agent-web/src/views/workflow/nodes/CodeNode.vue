<template>
  <Handle type="target" :position="Position.Left" />
  <BaseNode :id="id" :data="data" :selected="selected">
    <div class="code-node__info">
      <div v-if="codeData.codeLanguage" class="code-node__lang">
        <span class="code-node__label">{{ codeData.codeLanguage }}</span>
      </div>
      <div v-if="codeData.code" class="code-node__preview">{{ codeData.code.slice(0, 80) }}{{ codeData.code.length > 80 ? '...' : '' }}</div>
      <div v-if="!codeData.code" class="code-node__empty">点击配置代码</div>
    </div>
  </BaseNode>
  <Handle type="source" :position="Position.Right" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Position, Handle } from '@vue-flow/core'
import BaseNode from './BaseNode.vue'
import { type CommonNodeData, type CodeNodeData } from '../types'

const props = defineProps<{
  id: string
  data: CommonNodeData
  selected?: boolean
}>()

const codeData = computed(() => props.data as CodeNodeData)
</script>

<style scoped>
.code-node__info { display: flex; flex-direction: column; gap: 4px; }
.code-node__lang { display: flex; align-items: center; gap: 4px; }
.code-node__label { font-size: 10px; padding: 1px 4px; border-radius: 3px; background: #409EFF; color: #fff; }
.code-node__preview { font-size: 11px; color: #909399; font-family: monospace; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.code-node__empty { color: #c0c4cc; font-style: italic; }
</style>
