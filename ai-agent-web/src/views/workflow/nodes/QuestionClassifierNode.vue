<template>
  <Handle type="target" :position="Position.Left" />
  <BaseNode :id="id" :data="data" :selected="selected">
    <div class="qclassifier-node__info">
      <div v-if="qcData.model" class="qclassifier-node__item">
        <span class="qclassifier-node__label">模型</span>
        <span class="qclassifier-node__value">{{ qcData.model }}</span>
      </div>
      <div v-if="qcData.categories?.length" class="qclassifier-node__categories">
        <span v-for="cat in qcData.categories" :key="cat.id" class="qclassifier-node__cat-tag">{{ cat.name }}</span>
      </div>
      <div v-if="!qcData.model && !qcData.categories?.length" class="qclassifier-node__empty">点击配置问题分类</div>
    </div>
  </BaseNode>
  <Handle type="source" :position="Position.Right" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Position, Handle } from '@vue-flow/core'
import BaseNode from './BaseNode.vue'
import { type CommonNodeData, type QuestionClassifierNodeData } from '../types'

const props = defineProps<{
  id: string
  data: CommonNodeData
  selected?: boolean
}>()

const qcData = computed(() => props.data as QuestionClassifierNodeData)
</script>

<style scoped>
.qclassifier-node__info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.qclassifier-node__item {
  display: flex;
  align-items: center;
  gap: 4px;
}
.qclassifier-node__label {
  font-size: 10px;
  padding: 1px 4px;
  border-radius: 3px;
  background: #ede9fe;
  color: #6366f1;
}
.qclassifier-node__value {
  font-size: 12px;
  color: #303133;
}
.qclassifier-node__categories {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.qclassifier-node__cat-tag {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 3px;
  background: #fdf6ec;
  color: #e6a23c;
}
.qclassifier-node__empty {
  color: #c0c4cc;
  font-style: italic;
}
</style>
