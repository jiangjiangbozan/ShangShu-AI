/**
 * API 基础地址配置
 *
 * 由 Vite 从 .env.development / .env.production 注入 VITE_API_BASE_URL，
 * 集中在此读取，避免请求封装与令牌刷新模块各自硬编码同一地址。
 */

/** 环境变量缺失时的兜底地址，与开发环境配置保持一致 */
const DEFAULT_API_BASE_URL: string = 'http://localhost:8080/api/v1'

/** API 基础地址 */
export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE_URL
