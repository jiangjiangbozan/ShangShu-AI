/** 数据分析（NL2SQL）类型定义 */

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

/** 结果集行（值可能是字符串、数字或 null） */
export type DataRow = Record<string, string | number | null>

/** 问数响应 */
export interface DataAskResp {
  question: string
  sql: string
  columns: string[]
  rows: DataRow[]
  chartType: 'bar' | 'line' | 'pie' | 'table'
  chartTitle: string
  message?: string
  latencyMs: number
}

/** 生成报告请求 */
export interface DataReportReq {
  question: string
  sql: string
  columns: string[]
  rows: DataRow[]
}
