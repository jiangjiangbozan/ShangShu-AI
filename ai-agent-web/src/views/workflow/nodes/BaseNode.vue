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

    <!-- 头部：图标芯片 + 标题 + 状态 + 菜单 -->
    <div class="wf-node__header">
      <span class="wf-node__icon" :style="{ background: iconSoftBg, color: iconColor }">{{ nodeIcon }}</span>
      <span class="wf-node__title">{{ data.title }}</span>
      <!-- 运行状态图标 -->
      <span v-if="data._runningStatus === 'running'" class="wf-node__status-icon wf-node__status-icon--running">
        <svg width="13" height="13" viewBox="0 0 14 14" fill="none"><circle cx="7" cy="7" r="5.5" stroke="currentColor" stroke-width="2" stroke-dasharray="4 3" stroke-linecap="round"><animateTransform attributeName="transform" type="rotate" from="0 7 7" to="360 7 7" dur="1s" repeatCount="indefinite"/></circle></svg>
      </span>
      <span v-else-if="data._runningStatus === 'succeeded'" class="wf-node__status-icon wf-node__status-icon--succeeded">
        <svg width="12" height="12" viewBox="0 0 12 12" fill="none"><path d="M2.5 6.2 5 8.7l4.5-5.4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
      </span>
      <span v-else-if="data._runningStatus === 'failed'" class="wf-node__status-icon wf-node__status-icon--failed">
        <svg width="12" height="12" viewBox="0 0 12 12" fill="none"><path d="M3 3l6 6M9 3l-6 6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
      </span>
      <!-- 操作菜单 -->
      <el-dropdown v-if="!readonly" trigger="click" @command="onCommand" class="wf-node__menu" @click.stop>
        <span class="wf-node__menu-trigger" @click.stop>
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><circle cx="3" cy="7" r="1.2" fill="currentColor"/><circle cx="7" cy="7" r="1.2" fill="currentColor"/><circle cx="11" cy="7" r="1.2" fill="currentColor"/></svg>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="copy">
              <el-icon><CopyDocument /></el-icon>复制节点
            </el-dropdown-item>
            <el-dropdown-item command="delete" class="wf-node__danger-item">
              <el-icon><Delete /></el-icon>删除
            </el-dropdown-item>
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
import { CopyDocument, Delete } from '@element-plus/icons-vue'
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

const iconColor = computed(() => {
  return NODE_TYPE_COLORS[props.data.type as NodeType] ?? '#909399'
})
const iconSoftBg = computed(() => {
  return iconColor.value + '1A'
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
  box-shadow: 0 1px 2px rgba(17, 24, 39, 0.05), 0 1px 6px rgba(17, 24, 39, 0.04);
  min-width: 220px;
  max-width: 280px;
  border: 1.5px solid #e5e8f2;
  transition: border-color 0.2s, box-shadow 0.2s;
  position: relative;
  cursor: grab;
}

.wf-node:hover {
  border-color: #c7cdf0;
  box-shadow: 0 4px 14px -2px rgba(17, 24, 39, 0.1), 0 1px 3px rgba(17, 24, 39, 0.05);
}

.wf-node--selected {
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.14), 0 4px 14px -2px rgba(17, 24, 39, 0.1);
}

.wf-node--running {
  border-color: #f59e0b;
  box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.15), 0 4px 14px -2px rgba(17, 24, 39, 0.1);
  animation: wf-node-running-glow 1.6s ease-in-out infinite;
}
@keyframes wf-node-running-glow {
  0%, 100% { box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.15), 0 4px 14px -2px rgba(17, 24, 39, 0.1); }
  50% { box-shadow: 0 0 0 5px rgba(245, 158, 11, 0.22), 0 4px 14px -2px rgba(17, 24, 39, 0.1); }
}

.wf-node--succeeded {
  border-color: #10b981;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.14), 0 4px 14px -2px rgba(17, 24, 39, 0.1);
}

.wf-node--failed {
  border-color: #ef4444;
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.14), 0 4px 14px -2px rgba(17, 24, 39, 0.1);
}

/* ============ Handles ============ */
.wf-node__handle {
  width: 10px !important;
  height: 10px !important;
  border: 2px solid #c7cdf0 !important;
  background: #fff !important;
  border-radius: 50% !important;
  transition: border-color 0.15s, background 0.15s, transform 0.15s;
}

.wf-node__handle:hover {
  border-color: #6366f1 !important;
  background: #6366f1 !important;
  transform: scale(1.25);
}

.wf-node__handle--target {
  left: -6px !important;
}

.wf-node__handle--source {
  right: -6px !important;
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
  gap: 8px;
  padding: 10px 12px 0;
  line-height: 1;
}

.wf-node__icon {
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
  width: 26px;
  height: 26px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  letter-spacing: -0.5px;
}

.wf-node__title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 600;
  color: #111827;
}

.wf-node__status-icon {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
}

.wf-node__status-icon--running {
  color: #f59e0b;
  background: rgba(245, 158, 11, 0.12);
}

.wf-node__status-icon--succeeded {
  color: #10b981;
  background: rgba(16, 185, 129, 0.12);
}

.wf-node__status-icon--failed {
  color: #ef4444;
  background: rgba(239, 68, 68, 0.12);
}

/* ============ 菜单 ============ */
.wf-node__menu {
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s;
  flex-shrink: 0;
}

.wf-node:hover .wf-node__menu {
  opacity: 1;
}

.wf-node__menu-trigger {
  display: flex;
  align-items: center;
  color: #9ca3af;
  padding: 3px;
  border-radius: 5px;
  transition: background 0.15s, color 0.15s;
}

.wf-node__menu-trigger:hover {
  background: #f0f1f7;
  color: #374151;
}

/* ============ 内容区 ============ */
.wf-node__body {
  padding: 8px 12px 12px;
  font-size: 12px;
  color: #4b5563;
}

.wf-node__desc {
  margin-top: 4px;
  color: #9ca3af;
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ============ Start/End 特殊样式 ============ */
.wf-node--start,
.wf-node--end {
  border-radius: 16px;
  min-width: 110px;
  border-width: 1.5px;
}

.wf-node--start .wf-node__header,
.wf-node--end .wf-node__header {
  justify-content: center;
  padding-top: 12px;
}

.wf-node--start .wf-node__body,
.wf-node--end .wf-node__body {
  text-align: center;
  padding-bottom: 12px;
}

/* ============ 下拉危险项 ============ */
.wf-node__danger-item {
  color: #ef4444;
}
</style>
