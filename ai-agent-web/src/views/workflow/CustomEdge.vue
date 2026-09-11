<template>
  <svg width="0" height="0" style="position: absolute">
    <defs>
      <marker
        :id="markerId"
        markerWidth="16"
        markerHeight="16"
        refX="12"
        refY="8"
        orient="auto"
        markerUnits="strokeWidth"
      >
        <path d="M0,0 L0,16 L12,8 z" :fill="edgeStyle.stroke" />
      </marker>
    </defs>
  </svg>
  <BaseEdge :path="path" :style="edgeStyle" :marker-end="`url(#${markerId})`" />
  <circle
    v-if="data?.animated || animated"
    :r="3"
    fill="#6366f1"
    opacity="0.8"
  >
    <animateMotion
      :path="path"
      dur="1.5s"
      repeatCount="indefinite"
    />
  </circle>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { BaseEdge, getSmoothStepPath, type EdgeProps } from '@vue-flow/core'

const props = defineProps<EdgeProps>()

const markerId = `wf-arrow-${props.id}`

const path = computed(() => {
  const [edgePath] = getSmoothStepPath({
    sourceX: props.sourceX,
    sourceY: props.sourceY,
    targetX: props.targetX,
    targetY: props.targetY,
    sourcePosition: props.sourcePosition,
    targetPosition: props.targetPosition,
    borderRadius: 12,
  })
  return edgePath
})

const edgeStyle = computed(() => {
  const base = {
    strokeWidth: 2,
    stroke: '#6366f1',
  }
  // 运行中高亮
  if (props.data?._runningStatus === 'running') {
    return { ...base, stroke: '#e6a23c', strokeWidth: 2.5 }
  }
  if (props.data?._runningStatus === 'succeeded') {
    return { ...base, stroke: '#67c23a' }
  }
  if (props.data?._runningStatus === 'failed') {
    return { ...base, stroke: '#f56c6c' }
  }
  return base
})
</script>
