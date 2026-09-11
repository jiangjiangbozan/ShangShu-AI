<script setup lang="ts">
/**
 * 403 无权访问页
 *
 * 当用户访问了声明权限点（meta.perm）但不具备对应权限的路由时，由路由守卫跳转至此。
 */
import { useRouter } from 'vue-router'
import { ElButton, ElResult } from 'element-plus'

const router = useRouter()

function goBack() {
  router.push('/')
}
</script>

<template>
  <div class="forbidden-page">
    <el-result icon="warning" title="403" sub-title="抱歉，您没有访问该页面的权限">
      <template #extra>
        <el-button type="primary" @click="goBack">返回首页</el-button>
      </template>
    </el-result>
  </div>
</template>

<style scoped>
.forbidden-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: var(--bg-page);
}
</style>