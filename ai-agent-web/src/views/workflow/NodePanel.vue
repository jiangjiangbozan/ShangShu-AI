<template>
  <div class="wf-node-panel">
    <!-- 搜索 -->
    <div class="wf-node-panel__search">
      <el-input
        v-model="searchQuery"
        placeholder="搜索节点..."
        :prefix-icon="Search"
        clearable
        size="default"
      />
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
              <span class="wf-node-panel__item-icon" :style="{ background: item.color + '18' }">
                <span :style="{ color: item.color }">{{ item.icon }}</span>
              </span>
              <span class="wf-node-panel__item-label">{{ item.label }}</span>
            </div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { NodeType, NODE_TYPE_LABELS, NODE_TYPE_ICONS, NODE_TYPE_COLORS } from './types'

interface NodeGroup {
  label: string
  items: { type: NodeType; label: string; icon: string; color: string }[]
}

const groups: NodeGroup[] = [
  {
    label: '基础',
    items: [
      { type: NodeType.Start, label: NODE_TYPE_LABELS[NodeType.Start], icon: NODE_TYPE_ICONS[NodeType.Start], color: NODE_TYPE_COLORS[NodeType.Start] },
      { type: NodeType.End, label: NODE_TYPE_LABELS[NodeType.End], icon: NODE_TYPE_ICONS[NodeType.End], color: NODE_TYPE_COLORS[NodeType.End] },
      { type: NodeType.Answer, label: NODE_TYPE_LABELS[NodeType.Answer], icon: NODE_TYPE_ICONS[NodeType.Answer], color: NODE_TYPE_COLORS[NodeType.Answer] },
    ],
  },
  {
    label: 'AI',
    items: [
      { type: NodeType.LLM, label: NODE_TYPE_LABELS[NodeType.LLM], icon: NODE_TYPE_ICONS[NodeType.LLM], color: NODE_TYPE_COLORS[NodeType.LLM] },
      { type: NodeType.KnowledgeRetrieval, label: NODE_TYPE_LABELS[NodeType.KnowledgeRetrieval], icon: NODE_TYPE_ICONS[NodeType.KnowledgeRetrieval], color: NODE_TYPE_COLORS[NodeType.KnowledgeRetrieval] },
      { type: NodeType.ParameterExtractor, label: NODE_TYPE_LABELS[NodeType.ParameterExtractor], icon: NODE_TYPE_ICONS[NodeType.ParameterExtractor], color: NODE_TYPE_COLORS[NodeType.ParameterExtractor] },
      { type: NodeType.QuestionClassifier, label: NODE_TYPE_LABELS[NodeType.QuestionClassifier], icon: NODE_TYPE_ICONS[NodeType.QuestionClassifier], color: NODE_TYPE_COLORS[NodeType.QuestionClassifier] },
      { type: NodeType.Agent, label: NODE_TYPE_LABELS[NodeType.Agent], icon: NODE_TYPE_ICONS[NodeType.Agent], color: NODE_TYPE_COLORS[NodeType.Agent] },
    ],
  },
  {
    label: '逻辑',
    items: [
      { type: NodeType.IfElse, label: NODE_TYPE_LABELS[NodeType.IfElse], icon: NODE_TYPE_ICONS[NodeType.IfElse], color: NODE_TYPE_COLORS[NodeType.IfElse] },
      { type: NodeType.Iteration, label: NODE_TYPE_LABELS[NodeType.Iteration], icon: NODE_TYPE_ICONS[NodeType.Iteration], color: NODE_TYPE_COLORS[NodeType.Iteration] },
      { type: NodeType.Loop, label: NODE_TYPE_LABELS[NodeType.Loop], icon: NODE_TYPE_ICONS[NodeType.Loop], color: NODE_TYPE_COLORS[NodeType.Loop] },
      { type: NodeType.HumanInput, label: NODE_TYPE_LABELS[NodeType.HumanInput], icon: NODE_TYPE_ICONS[NodeType.HumanInput], color: NODE_TYPE_COLORS[NodeType.HumanInput] },
    ],
  },
  {
    label: '工具',
    items: [
      { type: NodeType.Code, label: NODE_TYPE_LABELS[NodeType.Code], icon: NODE_TYPE_ICONS[NodeType.Code], color: NODE_TYPE_COLORS[NodeType.Code] },
      { type: NodeType.HttpRequest, label: NODE_TYPE_LABELS[NodeType.HttpRequest], icon: NODE_TYPE_ICONS[NodeType.HttpRequest], color: NODE_TYPE_COLORS[NodeType.HttpRequest] },
      { type: NodeType.TemplateTransform, label: NODE_TYPE_LABELS[NodeType.TemplateTransform], icon: NODE_TYPE_ICONS[NodeType.TemplateTransform], color: NODE_TYPE_COLORS[NodeType.TemplateTransform] },
      { type: NodeType.Tool, label: NODE_TYPE_LABELS[NodeType.Tool], icon: NODE_TYPE_ICONS[NodeType.Tool], color: NODE_TYPE_COLORS[NodeType.Tool] },
      { type: NodeType.DocExtractor, label: NODE_TYPE_LABELS[NodeType.DocExtractor], icon: NODE_TYPE_ICONS[NodeType.DocExtractor], color: NODE_TYPE_COLORS[NodeType.DocExtractor] },
    ],
  },
  {
    label: '数据',
    items: [
      { type: NodeType.VariableAssigner, label: NODE_TYPE_LABELS[NodeType.VariableAssigner], icon: NODE_TYPE_ICONS[NodeType.VariableAssigner], color: NODE_TYPE_COLORS[NodeType.VariableAssigner] },
      { type: NodeType.VariableAggregator, label: NODE_TYPE_LABELS[NodeType.VariableAggregator], icon: NODE_TYPE_ICONS[NodeType.VariableAggregator], color: NODE_TYPE_COLORS[NodeType.VariableAggregator] },
      { type: NodeType.ListFilter, label: NODE_TYPE_LABELS[NodeType.ListFilter], icon: NODE_TYPE_ICONS[NodeType.ListFilter], color: NODE_TYPE_COLORS[NodeType.ListFilter] },
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
      items: g.items.filter((i) => i.label.toLowerCase().includes(q) || i.type.toLowerCase().includes(q)),
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
  width: 240px;
  min-width: 240px;
  padding: 12px;
  border-right: 1px solid #e8eaed;
  background: #fff;
  overflow-y: auto;
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.wf-node-panel__search {
  flex-shrink: 0;
}

.wf-node-panel__groups {
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex: 1;
  overflow-y: auto;
}

.wf-node-panel__group-label {
  font-size: 11px;
  font-weight: 600;
  color: #98a2b3;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 4px;
  padding: 0 4px;
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
  padding: 8px 10px;
  border-radius: 8px;
  cursor: grab;
  transition: background 0.12s;
  user-select: none;
}

.wf-node-panel__item:hover {
  background: #f5f6f8;
}

.wf-node-panel__item:active {
  cursor: grabbing;
  background: #edeef1;
}

.wf-node-panel__item-icon {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
  letter-spacing: -0.5px;
}

.wf-node-panel__item-label {
  font-size: 13px;
  color: #344054;
  font-weight: 500;
}
</style>
