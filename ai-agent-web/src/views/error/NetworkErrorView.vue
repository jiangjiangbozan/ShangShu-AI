<script setup lang="ts">
/**
 * 网络错误 / 服务不可用页面
 *
 * 当后端服务不可达（502/503/网络断开）时显示。
 * 从 URL query 读取 status 和 msg 参数。
 */
import { useRoute, useRouter } from 'vue-router'
import { ElButton, ElResult } from 'element-plus'

const route = useRoute()
const router = useRouter()

const statusCode = Number(route.query.status) || 0
const customMsg = route.query.msg as string

const title = statusCode ? `${statusCode}` : '网络异常'
const subTitle = customMsg || '服务器暂时无法响应，请检查网络连接或稍后重试'

function goHome() {
  router.push('/')
}

function retry() {
  window.location.reload()
}
</script>

<template>
  <div class="network-error-page">
    <el-result icon="warning" :title="title" :sub-title="subTitle">
      <template #extra>
        <el-button type="primary" @click="retry">重新加载</el-button>
        <el-button @click="goHome">返回首页</el-button>
      </template>
    </el-result>
  </div>
</template>

<style scoped>
.network-error-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: var(--bg-page);
}
</style>
