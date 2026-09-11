<script setup lang="ts">
import { RouterView } from 'vue-router'
</script>

<template>
  <RouterView />
</template>

<style>
/* ============ 全局基础样式 ============ */
*,
*::before,
*::after {
  box-sizing: border-box;
}

html,
body,
#app {
  height: 100%;
  margin: 0;
  padding: 0;
}

html {
  /* 平滑滚动 */
  scroll-behavior: smooth;
  /* 字号防缩放（移动端） */
  -webkit-text-size-adjust: 100%;
}

body {
  font-family: var(--font-sans);
  font-size: var(--font-size-base);
  line-height: var(--line-height-normal);
  color: var(--text-primary);
  background-color: var(--bg-page);
  /* 字体渲染优化 */
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-rendering: optimizeLegibility;
}

#app {
  display: block;
}

/* ============ 标题排版 ============ */
h1, h2, h3, h4, h5, h6 {
  margin: 0;
  font-weight: var(--font-weight-semibold);
  line-height: var(--line-height-tight);
  color: var(--text-primary);
}

p {
  margin: 0;
}

/* ============ 链接 ============ */
a {
  color: var(--color-primary-600);
  text-decoration: none;
  transition: color var(--transition-fast);
}
a:hover {
  color: var(--color-primary-700);
  text-decoration: underline;
}

/* ============ 滚动条美化 ============ */
::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}
::-webkit-scrollbar-track {
  background: transparent;
}
::-webkit-scrollbar-thumb {
  background-color: var(--color-gray-300);
  border-radius: var(--radius-full);
  border: 2px solid transparent;
  background-clip: content-box;
}
::-webkit-scrollbar-thumb:hover {
  background-color: var(--color-gray-400);
  background-clip: content-box;
}

/* ============ 选中文本 ============ */
::selection {
  background-color: var(--color-primary-200);
  color: var(--color-primary-900);
}

/* ============ 焦点可见性（可访问性） ============ */
:focus-visible {
  outline: 2px solid var(--color-primary-500);
  outline-offset: 2px;
}

/* ============ 响应式：小屏调整字号 ============ */
@media (max-width: 768px) {
  html {
    font-size: 13px;
  }
}
@media (max-width: 480px) {
  html {
    font-size: 12px;
  }
}

/* ============ 减少动画（可访问性） ============ */
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
    scroll-behavior: auto !important;
  }
}
</style>
