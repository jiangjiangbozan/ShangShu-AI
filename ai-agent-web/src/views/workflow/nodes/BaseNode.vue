<template>
  <div
    class="wf-node"
    :class="[
      `wf-node--${data.type}`,
      {
        'wf-node--selected': selected,
        'wf-node--running': data._runningStatus === 'running',
        'wf-node--succeeded': data._runningStatus === 'succeeded',
        'wf-node--failed': data._runningStatus === 'failed',
      },
    ]"
  >
    <!-- VueFlow Handles -->
    <Handle type="target" :position="Position.Left" class="wf-node__handle wf-node__handle--target" />
    <Handle type="source" :position="Position.Right" class="wf-node__handle wf-node__handle--source" />

    <!-- 头部 -->
    <div class="wf-node__header" :style="{ background: headerBg }">
      <span class="wf-node__icon">{{ nodeIcon }}</span>
      <span class="wf-node__title">{{ data.title }}</span>
      <!-- 运行状态图标 -->
      <span v-if="data._runningStatus === 'running'" class="wf-node__status-icon wf-node__status-icon--running">
        <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><circle cx="7" cy="7" r="6" stroke="#e6a23c" stroke-width="1.5" stroke-dasharray="3 2"><animateTransform attributeName="transform" type="rotate" from="0 7 7" to="360 7 7" dur="1s" repeatCount="indefinite"/></circle></svg>
      </span>
      <span v-else-if="data._runningStatus === 'succeeded'" class="wf-node__status-icon wf-node__status-icon--succeeded">✓</span>
      <span v-else-if="data._runningStatus === 'failed'" class="wf-node__status-icon wf-node__status-icon--failed">✕</span>
      <!-- 操作菜单 -->
      <el-dropdown v-if="!readonly" trigger="click" @command="onCommand" class="wf-node__menu" @click.stop>
        <span class="wf-node__menu-trigger">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><circle cx="3" cy="7" r="1.2" fill="currentColor"/><circle cx="7" cy="7" r="1.2" fill="currentColor"/><circle cx="11" cy="7" r="1.2" fill="currentColor"/></svg>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="copy">复制节点</el-dropdown-item>
            <el-dropdown-item command="delete" style="color: #f56c6c">删除</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <!-- 节点内容 -->
    <div class="wf-node__body">
      <slot />
      <div v-if="data.desc" class="wf-node__desc">{{ data.desc }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position, useVueFlow } from '@vue-flow/core'
import { NODE_TYPE_COLORS, NODE_TYPE_ICONS, type CommonNodeData, type NodeType } from '../types'
import { useWorkflowStore } from '../store'

const props = defineProps<{
  id: string
  data: CommonNodeData
  selected?: boolean
}>()

const store = useWorkflowStore()
const { removeNodes, addNodes } = useVueFlow({ id: 'workflow-canvas' })
const readonly = computed(() => store.readonly)

const headerBg = computed(() => {
  const color = NODE_TYPE_COLORS[props.data.type as NodeType] ?? '#909399'
  return color
})
const nodeIcon = computed(() => NODE_TYPE_ICONS[props.data.type as NodeType] ?? '◻')

function onCommand(cmd: string) {
  if (cmd === 'delete') {
    removeNodes([props.id])
    store.selectNode(null)
    store.dirty = true
  } else if (cmd === 'copy') {
    const position = { x: (props as any).position?.x ?? 0 + 30, y: (props as any).position?.y ?? 0 + 30 }
    const newId = store.genNodeId(props.data.type as NodeType)
    const newData = { ...props.data, title: `${props.data.title} (复制)`, _runningStatus: undefined }
    addNodes([{ id: newId, type: props.data.type as NodeType, position, data: newData }])
    store.dirty = true
  }
}
</script>

<style scoped>
/* ============ 节点容器 ============ */
.wf-node {
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06), 0 0 0 1px rgba(0, 0, 0, 0.04);
  min-width: 220px;
  max-width: 280px;
  border: 2px solid transparent;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.1s;
  position: relative;
  cursor: grab;
}

.wf-node:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1), 0 0 0 1px rgba(0, 0, 0, 0.06);
}

.wf-node--selected {
  border-color: #155eef;
  box-shadow: 0 0 0 2px rgba(21, 94, 239, 0.2), 0 4px 12px rgba(0, 0, 0, 0.1);
}

.wf-node--running {
  border-color: #e6a23c;
  box-shadow: 0 0 0 2px rgba(230, 162, 60, 0.2);
}

.wf-node--succeeded {
  border-color: #67c23a;
  box-shadow: 0 0 0 2px rgba(103, 194, 58, 0.2);
}

.wf-node--failed {
  border-color: #f56c6c;
  box-shadow: 0 0 0 2px rgba(245, 108, 108, 0.2);
}

/* ============ Handles ============ */
.wf-node__handle {
  width: 12px !important;
  height: 12px !important;
  border: 2px solid #d0d5dd !important;
  background: #fff !important;
  border-radius: 50% !important;
  transition: border-color 0.15s, background 0.15s;
}

.wf-node__handle:hover {
  border-color: #6366f1 !important;
  background: #6366f1 !important;
}

.wf-node__handle--target {
  left: -7px !important;
}

.wf-node__handle--source {
  right: -7px !important;
}

/* Start 节点隐藏 target handle */
.wf-node--start .wf-node__handle--target {
  opacity: 0;
  pointer-events: none;
}

/* End 节点隐藏 source handle */
.wf-node--end .wf-node__handle--source {
  opacity: 0;
  pointer-events: none;
}

/* ============ 头部 ============ */
.wf-node__header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-radius: 10px 10px 0 0;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
}

.wf-node__icon {
  font-size: 14px;
  flex-shrink: 0;
  width: 18px;
  text-align: center;
}

.wf-node__title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}

.wf-node__status-icon {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  border-radius: 50%;
}

.wf-node__status-icon--running {
  color: #e6a23c;
  background: rgba(230, 162, 60, 0.15);
}

.wf-node__status-icon--succeeded {
  color: #67c23a;
  background: rgba(103, 194, 58, 0.15);
}

.wf-node__status-icon--failed {
  color: #f56c6c;
  background: rgba(245, 108, 108, 0.15);
}

/* ============ 菜单 ============ */
.wf-node__menu {
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s;
}

.wf-node:hover .wf-node__menu {
  opacity: 0.8;
}

.wf-node__menu:hover {
  opacity: 1 !important;
}

.wf-node__menu-trigger {
  display: flex;
  align-items: center;
  color: rgba(255, 255, 255, 0.7);
  padding: 2px;
  border-radius: 3px;
  transition: background 0.15s;
}

.wf-node__menu-trigger:hover {
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
}

/* ============ 内容区 ============ */
.wf-node__body {
  padding: 10px 12px;
  font-size: 12px;
  color: #606266;
  border-radius: 0 0 10px 10px;
}

.wf-node__desc {
  margin-top: 4px;
  color: #909399;
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ============ Start/End 特殊样式 ============ */
.wf-node--start {
  border-radius: 20px;
  min-width: 100px;
}

.wf-node--start .wf-node__header {
  border-radius: 18px 18px 0 0;
  justify-content: center;
}

.wf-node--start .wf-node__body {
  border-radius: 0 0 18px 18px;
  text-align: center;
}

.wf-node--end {
  border-radius: 20px;
  min-width: 100px;
}

.wf-node--end .wf-node__header {
  border-radius: 18px 18px 0 0;
  justify-content: center;
}

.wf-node--end .wf-node__body {
  border-radius: 0 0 18px 18px;
  text-align: center;
}
</style>
