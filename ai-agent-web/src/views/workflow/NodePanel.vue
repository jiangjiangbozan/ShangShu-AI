<template>
  <div class="wf-node-panel">
    <!-- 面板标题 -->
    <div class="wf-node-panel__header">
      <span class="wf-node-panel__title">节点库</span>
    </div>

    <!-- 搜索 -->
    <div class="wf-node-panel__search">
      <el-input v-model="searchQuery" placeholder="搜索节点..." :prefix-icon="Search" clearable />
    </div>

    <!-- 节点分组 -->
    <div class="wf-node-panel__groups">
      <template v-for="group in filteredGroups" :key="group.label">
        <div v-if="group.items.length" class="wf-node-panel__group">
          <div class="wf-node-panel__group-label">{{ group.label }}</div>
          <div class="wf-node-panel__items">
            <div
              v-for="item in group.items"
              :key="item.type"
              class="wf-node-panel__item"
              draggable="true"
              @dragstart="onDragStart($event, item.type)"
            >
              <span
                class="wf-node-panel__item-icon"
                :style="{ background: item.color + '1A', color: item.color }"
              >
                {{ item.icon }}
              </span>
              <span class="wf-node-panel__item-label">{{ item.label }}</span>
              <svg
                class="wf-node-panel__item-drag"
                width="8"
                height="12"
                viewBox="0 0 8 12"
                fill="currentColor"
              >
                <circle cx="2" cy="2" r="1.2" />
                <circle cx="6" cy="2" r="1.2" />
                <circle cx="2" cy="6" r="1.2" />
                <circle cx="6" cy="6" r="1.2" />
                <circle cx="2" cy="10" r="1.2" />
                <circle cx="6" cy="10" r="1.2" />
              </svg>
            </div>
          </div>
        </div>
      </template>
      <div v-if="!filteredGroups.some((g) => g.items.length)" class="wf-node-panel__no-result">
        未找到匹配的节点
      </div>
    </div>

    <!-- 底部提示 -->
    <div class="wf-node-panel__footer">
      <el-icon :size="12"><InfoFilled /></el-icon>
      拖拽节点到画布中添加
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Search, InfoFilled } from '@element-plus/icons-vue'
import { NodeType, NODE_TYPE_LABELS, NODE_TYPE_ICONS, NODE_TYPE_COLORS } from './types'

interface NodeGroup {
  label: string
  items: { type: NodeType; label: string; icon: string; color: string }[]
}

const groups: NodeGroup[] = [
  {
    label: '基础',
    items: [
      {
        type: NodeType.Start,
        label: NODE_TYPE_LABELS[NodeType.Start],
        icon: NODE_TYPE_ICONS[NodeType.Start],
        color: NODE_TYPE_COLORS[NodeType.Start],
      },
      {
        type: NodeType.End,
        label: NODE_TYPE_LABELS[NodeType.End],
        icon: NODE_TYPE_ICONS[NodeType.End],
        color: NODE_TYPE_COLORS[NodeType.End],
      },
      {
        type: NodeType.Answer,
        label: NODE_TYPE_LABELS[NodeType.Answer],
        icon: NODE_TYPE_ICONS[NodeType.Answer],
        color: NODE_TYPE_COLORS[NodeType.Answer],
      },
    ],
  },
  {
    label: 'AI',
    items: [
      {
        type: NodeType.LLM,
        label: NODE_TYPE_LABELS[NodeType.LLM],
        icon: NODE_TYPE_ICONS[NodeType.LLM],
        color: NODE_TYPE_COLORS[NodeType.LLM],
      },
      {
        type: NodeType.KnowledgeRetrieval,
        label: NODE_TYPE_LABELS[NodeType.KnowledgeRetrieval],
        icon: NODE_TYPE_ICONS[NodeType.KnowledgeRetrieval],
        color: NODE_TYPE_COLORS[NodeType.KnowledgeRetrieval],
      },
      {
        type: NodeType.ParameterExtractor,
        label: NODE_TYPE_LABELS[NodeType.ParameterExtractor],
        icon: NODE_TYPE_ICONS[NodeType.ParameterExtractor],
        color: NODE_TYPE_COLORS[NodeType.ParameterExtractor],
      },
      {
        type: NodeType.QuestionClassifier,
        label: NODE_TYPE_LABELS[NodeType.QuestionClassifier],
        icon: NODE_TYPE_ICONS[NodeType.QuestionClassifier],
        color: NODE_TYPE_COLORS[NodeType.QuestionClassifier],
      },
      {
        type: NodeType.Agent,
        label: NODE_TYPE_LABELS[NodeType.Agent],
        icon: NODE_TYPE_ICONS[NodeType.Agent],
        color: NODE_TYPE_COLORS[NodeType.Agent],
      },
    ],
  },
  {
    label: '逻辑',
    items: [
      {
        type: NodeType.IfElse,
        label: NODE_TYPE_LABELS[NodeType.IfElse],
        icon: NODE_TYPE_ICONS[NodeType.IfElse],
        color: NODE_TYPE_COLORS[NodeType.IfElse],
      },
      {
        type: NodeType.Iteration,
        label: NODE_TYPE_LABELS[NodeType.Iteration],
        icon: NODE_TYPE_ICONS[NodeType.Iteration],
        color: NODE_TYPE_COLORS[NodeType.Iteration],
      },
      {
        type: NodeType.Loop,
        label: NODE_TYPE_LABELS[NodeType.Loop],
        icon: NODE_TYPE_ICONS[NodeType.Loop],
        color: NODE_TYPE_COLORS[NodeType.Loop],
      },
      {
        type: NodeType.HumanInput,
        label: NODE_TYPE_LABELS[NodeType.HumanInput],
        icon: NODE_TYPE_ICONS[NodeType.HumanInput],
        color: NODE_TYPE_COLORS[NodeType.HumanInput],
      },
    ],
  },
  {
    label: '工具',
    items: [
      {
        type: NodeType.Code,
        label: NODE_TYPE_LABELS[NodeType.Code],
        icon: NODE_TYPE_ICONS[NodeType.Code],
        color: NODE_TYPE_COLORS[NodeType.Code],
      },
      {
        type: NodeType.HttpRequest,
        label: NODE_TYPE_LABELS[NodeType.HttpRequest],
        icon: NODE_TYPE_ICONS[NodeType.HttpRequest],
        color: NODE_TYPE_COLORS[NodeType.HttpRequest],
      },
      {
        type: NodeType.TemplateTransform,
        label: NODE_TYPE_LABELS[NodeType.TemplateTransform],
        icon: NODE_TYPE_ICONS[NodeType.TemplateTransform],
        color: NODE_TYPE_COLORS[NodeType.TemplateTransform],
      },
      {
        type: NodeType.Tool,
        label: NODE_TYPE_LABELS[NodeType.Tool],
        icon: NODE_TYPE_ICONS[NodeType.Tool],
        color: NODE_TYPE_COLORS[NodeType.Tool],
      },
      {
        type: NodeType.DocExtractor,
        label: NODE_TYPE_LABELS[NodeType.DocExtractor],
        icon: NODE_TYPE_ICONS[NodeType.DocExtractor],
        color: NODE_TYPE_COLORS[NodeType.DocExtractor],
      },
    ],
  },
  {
    label: '数据',
    items: [
      {
        type: NodeType.VariableAssigner,
        label: NODE_TYPE_LABELS[NodeType.VariableAssigner],
        icon: NODE_TYPE_ICONS[NodeType.VariableAssigner],
        color: NODE_TYPE_COLORS[NodeType.VariableAssigner],
      },
      {
        type: NodeType.VariableAggregator,
        label: NODE_TYPE_LABELS[NodeType.VariableAggregator],
        icon: NODE_TYPE_ICONS[NodeType.VariableAggregator],
        color: NODE_TYPE_COLORS[NodeType.VariableAggregator],
      },
      {
        type: NodeType.ListFilter,
        label: NODE_TYPE_LABELS[NodeType.ListFilter],
        icon: NODE_TYPE_ICONS[NodeType.ListFilter],
        color: NODE_TYPE_COLORS[NodeType.ListFilter],
      },
    ],
  },
]

const searchQuery = ref('')

const filteredGroups = computed(() => {
  if (!searchQuery.value) return groups
  const q = searchQuery.value.toLowerCase()
  return groups
    .map((g) => ({
      ...g,
      items: g.items.filter(
        (i) => i.label.toLowerCase().includes(q) || i.type.toLowerCase().includes(q),
      ),
    }))
    .filter((g) => g.items.length > 0)
})

function onDragStart(event: DragEvent, type: NodeType) {
  event.dataTransfer?.setData('application/vueflow', type)
  event.dataTransfer!.effectAllowed = 'move'
}
</script>

<style scoped>
.wf-node-panel {
  width: 248px;
  min-width: 248px;
  height: 100%;
  background: #fff;
  border-right: 1px solid #e8eaf2;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.wf-node-panel__header {
  padding: 16px 16px 0;
  flex-shrink: 0;
}
.wf-node-panel__title {
  font-size: 14px;
  font-weight: 700;
  color: #111827;
  letter-spacing: -0.2px;
}

.wf-node-panel__search {
  padding: 12px 16px;
  flex-shrink: 0;
}
.wf-node-panel__search :deep(.el-input__wrapper) {
  border-radius: 10px;
  background: #f4f5fb;
  box-shadow: none;
  transition:
    background 0.15s,
    box-shadow 0.15s;
}
.wf-node-panel__search :deep(.el-input__wrapper:hover) {
  background: #eef0f8;
}
.wf-node-panel__search :deep(.el-input__wrapper.is-focus) {
  background: #fff;
  box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.3) inset;
}

.wf-node-panel__groups {
  flex: 1;
  overflow-y: auto;
  padding: 0 12px 12px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.wf-node-panel__group-label {
  font-size: 11px;
  font-weight: 600;
  color: #9ca3af;
  text-transform: uppercase;
  letter-spacing: 0.8px;
  margin-bottom: 6px;
  padding: 0 6px;
}

.wf-node-panel__items {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.wf-node-panel__item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 8px;
  border-radius: 10px;
  cursor: grab;
  transition:
    background 0.12s,
    transform 0.12s;
  user-select: none;
  border: 1px solid transparent;
}
.wf-node-panel__item:hover {
  background: #f4f5fb;
  border-color: #e8eaf2;
}
.wf-node-panel__item:active {
  cursor: grabbing;
  background: #eef0f8;
  transform: scale(0.98);
}

.wf-node-panel__item-icon {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
  letter-spacing: -0.5px;
}

.wf-node-panel__item-label {
  flex: 1;
  font-size: 13px;
  color: #374151;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wf-node-panel__item-drag {
  color: #d1d5db;
  flex-shrink: 0;
  opacity: 0;
  transition:
    opacity 0.15s,
    color 0.15s;
}
.wf-node-panel__item:hover .wf-node-panel__item-drag {
  opacity: 1;
}

.wf-node-panel__no-result {
  padding: 24px 0;
  text-align: center;
  font-size: 12px;
  color: #9ca3af;
}

.wf-node-panel__footer {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  border-top: 1px solid #f0f1f7;
  font-size: 11px;
  color: #9ca3af;
  background: #fafbfe;
}

/* 细滚动条 */
.wf-node-panel__groups::-webkit-scrollbar {
  width: 5px;
}
.wf-node-panel__groups::-webkit-scrollbar-thumb {
  background: #d9dce8;
  border-radius: 999px;
}
.wf-node-panel__groups::-webkit-scrollbar-thumb:hover {
  background: #c2c6d8;
}
.wf-node-panel__groups::-webkit-scrollbar-track {
  background: transparent;
}
</style>
