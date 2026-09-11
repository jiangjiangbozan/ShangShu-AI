<script setup lang="ts">
/**
 * 知识库 RAG 页（小程序端）
 *
 * 对标 Web 端 KnowledgeView（精简版）：
 * - 知识库列表 + 新建知识库
 * - 文档列表 + 上传（uni.uploadFile）/ 删除
 * - 检索测试
 */
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import NavBar from '@/components/common/NavBar.vue'
import {
  createKnowledgeBase,
  deleteDocument,
  listDocuments,
  listKnowledgeBases,
  searchKnowledge,
  uploadDocument,
} from '@/api/knowledge'
import type { KnowledgeBaseResp, KnowledgeDocumentResp } from '@/types/knowledge'

const loading = ref(false)
const bases = ref<KnowledgeBaseResp[]>([])
const activeKb = ref<number | null>(null)
const documents = ref<KnowledgeDocumentResp[]>([])
const docsLoading = ref(false)

const showCreate = ref(false)
const newName = ref('')
const newDesc = ref('')
const creating = ref(false)

const searchQ = ref('')
const searching = ref(false)
const searchResults = ref<string[]>([])
const searchDone = ref(false)

const STATUS_TEXT: Record<string, string> = {
  pending: '等待中',
  parsing: '解析中',
  vectorizing: '向量化',
  completed: '完成',
  failed: '失败',
}

onShow(() => {
  loadBases()
})

async function loadBases() {
  loading.value = true
  try {
    bases.value = await listKnowledgeBases()
  } catch {
    // request 层已 toast
  } finally {
    loading.value = false
  }
}

async function openKb(kbId: number) {
  activeKb.value = activeKb.value === kbId ? null : kbId
  if (activeKb.value === kbId) {
    await loadDocs(kbId)
  }
}

async function loadDocs(kbId: number) {
  docsLoading.value = true
  documents.value = []
  try {
    documents.value = await listDocuments(kbId)
  } catch {
    // 已 toast
  } finally {
    docsLoading.value = false
  }
}

async function handleCreate() {
  const name = newName.value.trim()
  if (!name) {
    uni.showToast({ title: '请输入知识库名称', icon: 'none' })
    return
  }
  creating.value = true
  try {
    await createKnowledgeBase({ name, description: newDesc.value.trim() || undefined })
    uni.showToast({ title: '创建成功', icon: 'success' })
    showCreate.value = false
    newName.value = ''
    newDesc.value = ''
    await loadBases()
  } catch {
    // 已 toast
  } finally {
    creating.value = false
  }
}

function chooseAndUpload() {
  const kbId = activeKb.value
  if (kbId == null) {
    uni.showToast({ title: '请先展开一个知识库', icon: 'none' })
    return
  }
  uni.chooseMessageFile({
    count: 1,
    extension: ['txt', 'md', 'pdf', 'docx'],
    success: async (res) => {
      const file = res.tempFiles[0]
      uni.showLoading({ title: '上传中...', mask: true })
      try {
        await uploadDocument(kbId, file.path)
        uni.hideLoading()
        uni.showToast({ title: '上传成功', icon: 'success' })
        await loadDocs(kbId)
      } catch {
        uni.hideLoading()
      }
    },
  })
}

function handleDelete(doc: KnowledgeDocumentResp) {
  uni.showModal({
    title: '删除文档',
    content: `确定删除「${doc.fileName}」吗？`,
    success: async (res) => {
      if (!res.confirm || activeKb.value == null) return
      try {
        await deleteDocument(doc.id)
        uni.showToast({ title: '已删除', icon: 'success' })
        await loadDocs(activeKb.value)
        await loadBases()
      } catch {
        // 已 toast
      }
    },
  })
}

async function handleSearch() {
  const q = searchQ.value.trim()
  if (!q) {
    uni.showToast({ title: '请输入检索内容', icon: 'none' })
    return
  }
  searching.value = true
  searchResults.value = []
  searchDone.value = false
  try {
    searchResults.value = await searchKnowledge({ query: q, topK: 5 })
    searchDone.value = true
  } catch {
    // 已 toast
  } finally {
    searching.value = false
  }
}

function statusClass(status: string): string {
  if (status === 'completed') return 'is-success'
  if (status === 'failed') return 'is-error'
  return 'is-running'
}
</script>

<template>
  <view class="kb-page">
    <NavBar title="知识库 RAG" show-back />

    <!-- 顶部操作 -->
    <view class="kb-actions">
      <button class="kb-btn" @tap="showCreate = true">新建知识库</button>
    </view>

    <!-- 新建知识库弹窗 -->
    <view v-if="showCreate" class="kb-modal-mask" @tap="showCreate = false">
      <view class="kb-modal" @tap.stop>
        <view class="kb-modal__title">新建知识库</view>
        <input v-model="newName" class="kb-input" placeholder="知识库名称" />
        <textarea
          v-model="newDesc"
          class="kb-textarea"
          placeholder="描述（可选）"
          :maxlength="200"
        />
        <view class="kb-modal__btns">
          <button class="kb-btn kb-btn--ghost" @tap="showCreate = false">取消</button>
          <button class="kb-btn" :loading="creating" :disabled="creating" @tap="handleCreate">创建</button>
        </view>
      </view>
    </view>

    <!-- 知识库列表 -->
    <view v-if="loading" class="kb-empty">加载中...</view>
    <view v-else-if="!bases.length" class="kb-empty">
      <text>还没有知识库，点击上方按钮新建</text>
    </view>
    <view v-else class="kb-list">
      <view
        v-for="kb in bases"
        :key="kb.id"
        class="kb-card"
        @tap="openKb(kb.id)"
      >
        <view class="kb-card__head">
          <view class="kb-card__info">
            <text class="kb-card__name">{{ kb.name }}</text>
            <text class="kb-card__desc">{{ kb.description || '暂无描述' }}</text>
          </view>
          <text class="kb-card__arrow">{{ activeKb === kb.id ? '收起' : '展开' }}</text>
        </view>
        <view class="kb-card__meta">
          <text class="kb-meta">文档 {{ kb.documentCount }}</text>
          <text class="kb-meta">分片 {{ kb.chunkCount }}</text>
          <text class="kb-meta">{{ kb.embeddingModel }}</text>
        </view>

        <!-- 文档列表 -->
        <view v-if="activeKb === kb.id" class="kb-docs">
          <view class="kb-docs__toolbar">
            <text class="kb-docs__count">文档（{{ documents.length }}）</text>
            <button class="kb-btn kb-btn--mini" @tap="chooseAndUpload">上传文档</button>
          </view>
          <view v-if="docsLoading" class="kb-empty">加载中...</view>
          <view v-else-if="!documents.length" class="kb-empty">暂无文档</view>
          <view v-else class="kb-doc">
            <view v-for="doc in documents" :key="doc.id" class="kb-doc__item">
              <view class="kb-doc__info">
                <text class="kb-doc__name">{{ doc.fileName }}</text>
                <view class="kb-doc__meta">
                  <text class="kb-status" :class="statusClass(doc.status)">{{ STATUS_TEXT[doc.status] || doc.status }}</text>
                  <text class="kb-doc__chunk">{{ doc.chunkCount }} 分片</text>
                  <text v-if="doc.errorMessage" class="kb-doc__error">{{ doc.errorMessage }}</text>
                </view>
              </view>
              <text class="kb-doc__del" @tap.stop="handleDelete(doc)">删除</text>
            </view>
          </view>
        </view>
      </view>
    </view>

    <!-- 检索测试 -->
    <view class="kb-card kb-search">
      <view class="kb-card__info">
        <text class="kb-card__name">检索测试</text>
      </view>
      <view class="kb-search__row">
        <input v-model="searchQ" class="kb-input kb-input--inline" placeholder="输入问题，测试 RAG 召回" @confirm="handleSearch" />
        <button class="kb-btn kb-btn--mini" :loading="searching" :disabled="searching" @tap="handleSearch">检索</button>
      </view>
      <view v-if="searchDone" class="kb-search__result">
        <view v-if="!searchResults.length" class="kb-empty">未检索到相关内容</view>
        <view v-else v-for="(item, i) in searchResults" :key="i" class="kb-hit">
          <text class="kb-hit__index">{{ i + 1 }}</text>
          <text class="kb-hit__text">{{ item }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@import '@/styles/tokens.scss';

.kb-page {
  min-height: 100vh;
  background-color: $bg-page;
  padding-bottom: $space-8;
}

.kb-actions {
  padding: $space-3;
}

.kb-btn {
  margin: 0;
  height: 72rpx;
  line-height: 72rpx;
  padding: 0 $space-5;
  font-size: $font-size-base;
  color: $text-inverse;
  background-color: $color-primary;
  border-radius: $radius-lg;
  border: none;

  &::after {
    border: none;
  }

  &--ghost {
    color: $text-secondary;
    background-color: $bg-card;
    border: 1rpx solid $border-default;
  }

  &--mini {
    height: 56rpx;
    line-height: 56rpx;
    padding: 0 $space-3;
    font-size: $font-size-xs;
  }
}

.kb-list {
  display: flex;
  flex-direction: column;
  gap: $space-3;
  padding: 0 $space-3;
}

.kb-card {
  padding: $space-4;
  background-color: $bg-card;
  border-radius: $radius-xl;
  box-shadow: $shadow-xs;
}

.kb-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.kb-card__info {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
  flex: 1;
}

.kb-card__name {
  font-size: $font-size-md;
  font-weight: $font-weight-semibold;
  color: $text-primary;
}

.kb-card__desc {
  font-size: $font-size-xs;
  color: $text-tertiary;
}

.kb-card__arrow {
  font-size: $font-size-sm;
  color: $color-primary;
}

.kb-card__meta {
  display: flex;
  gap: $space-3;
  margin-top: $space-2;
}

.kb-meta {
  font-size: $font-size-xs;
  color: $text-tertiary;
}

.kb-docs {
  margin-top: $space-3;
  border-top: 1rpx solid $border-subtle;
  padding-top: $space-3;
}

.kb-docs__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.kb-docs__count {
  font-size: $font-size-sm;
  color: $text-secondary;
}

.kb-doc {
  margin-top: $space-2;
  display: flex;
  flex-direction: column;
  gap: $space-2;
}

.kb-doc__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $space-3;
  background-color: $bg-subtle;
  border-radius: $radius-lg;
}

.kb-doc__info {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
  flex: 1;
}

.kb-doc__name {
  font-size: $font-size-sm;
  color: $text-primary;
}

.kb-doc__meta {
  display: flex;
  align-items: center;
  gap: $space-2;
}

.kb-doc__chunk {
  font-size: $font-size-xs;
  color: $text-tertiary;
}

.kb-doc__error {
  font-size: $font-size-xs;
  color: $color-danger;
}

.kb-doc__del {
  font-size: $font-size-sm;
  color: $color-danger;
  margin-left: $space-3;
}

.kb-status {
  font-size: $font-size-xs;
  padding: 2rpx 12rpx;
  border-radius: $radius-full;

  &.is-success {
    color: $color-success;
    background-color: $color-success-soft;
  }

  &.is-error {
    color: $color-danger;
    background-color: $color-danger-soft;
  }

  &.is-running {
    color: $color-warning;
    background-color: $color-warning-soft;
  }
}

.kb-empty {
  padding: $space-8 $space-3;
  text-align: center;
  font-size: $font-size-sm;
  color: $text-tertiary;
}

.kb-search {
  margin: $space-3;
}

.kb-search__row {
  display: flex;
  align-items: center;
  gap: $space-2;
  margin-top: $space-3;
}

.kb-input {
  width: 100%;
  height: 72rpx;
  padding: 0 $space-3;
  background-color: $bg-subtle;
  border-radius: $radius-lg;
  font-size: $font-size-base;
  color: $text-primary;
  box-sizing: border-box;

  &--inline {
    flex: 1;
    width: auto;
  }
}

.kb-textarea {
  width: 100%;
  height: 140rpx;
  margin-top: $space-2;
  padding: $space-2 $space-3;
  background-color: $bg-subtle;
  border-radius: $radius-lg;
  font-size: $font-size-base;
  color: $text-primary;
  box-sizing: border-box;
}

.kb-modal-mask {
  position: fixed;
  inset: 0;
  background-color: rgba(0, 0, 0, 0.4);
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
}

.kb-modal {
  width: 600rpx;
  padding: $space-5;
  background-color: $bg-card;
  border-radius: $radius-xl;
}

.kb-modal__title {
  font-size: $font-size-lg;
  font-weight: $font-weight-semibold;
  color: $text-primary;
  margin-bottom: $space-3;
}

.kb-modal__btns {
  display: flex;
  justify-content: flex-end;
  gap: $space-2;
  margin-top: $space-4;
}

.kb-search__result {
  margin-top: $space-3;
  display: flex;
  flex-direction: column;
  gap: $space-2;
}

.kb-hit {
  display: flex;
  gap: $space-2;
  padding: $space-2 $space-3;
  background-color: $bg-subtle;
  border-radius: $radius-md;
}

.kb-hit__index {
  font-size: $font-size-xs;
  color: $color-primary;
  flex-shrink: 0;
}

.kb-hit__text {
  font-size: $font-size-sm;
  color: $text-primary;
  line-height: $line-height-snug;
  word-break: break-all;
}
</style>
