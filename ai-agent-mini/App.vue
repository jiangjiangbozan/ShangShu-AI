<script setup lang="ts">
/**
 * 应用入口
 *
 * onLaunch：初始化登录态校验。
 * 全局样式通过 @import 引入 reset.scss。
 */
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

// 应用启动时校验登录态
authStore.initialize()
</script>

<style lang="scss">
@import '@/styles/reset.scss';
</style>
