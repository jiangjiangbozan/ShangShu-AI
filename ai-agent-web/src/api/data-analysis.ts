/** 数据分析（NL2SQL）API 封装
 *
 * 对应后端 com.ai.api.dataanalysis.controller.DataAnalysisController
 *
 * - GET  /data-analysis/schema  可分析数据表清单
 * - POST /data-analysis/ask     自然语言问数（生成 SQL → 执行 → 结果 + 图表规格）
 * - POST /data-analysis/report  基于问数结果生成 Markdown 分析报告
 */
import { get, post } from '@/utils/request'

/** 列元信息 */
export interface ColumnMeta {
  name: string
  comment: string
  type: string
}

/** 数据表元信息 */
export interface TableMeta {
  name: string
  comment: string
  columns: ColumnMeta[]
}

/** schema 响应 */
export interface DataSchemaResp {
  tables: TableMeta[]
}

/** ask 响应：结果集 + 图表规格 */
export interface DataAskResp {
  question: string
  sql: string
  columns: string[]
  rows: Record<string, unknown>[]
  chartType: 'bar' | 'line' | 'pie' | 'table'
  chartTitle: string
  message?: string
  latencyMs: number
}

/** 生成报告请求（复用一次 ask 的结果） */
export interface DataReportReq {
  question: string
  sql: string
  columns: string[]
  rows: Record<string, unknown>[]
}

/** 获取可分析的数据表清单 */
export function fetchDataSchema() {
  return get<DataSchemaResp>('/data-analysis/schema')
}

/** 自然语言问数（NL2SQL） */
export function askData(question: string) {
  return post<DataAskResp>('/data-analysis/ask', { question })
}

/** 生成 Markdown 分析报告 */
export function generateReport(payload: DataReportReq) {
  return post<string>('/data-analysis/report', payload)
}
