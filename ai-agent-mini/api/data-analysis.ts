/**
 * 数据分析（NL2SQL）API 封装
 *
 * 对标 Web 端 src/api/data-analysis.ts，接口路径完全一致。
 * 对应后端 com.ai.api.dataanalysis.controller.DataAnalysisController。
 */
import { get, post } from '@/utils/request'
import type { DataAskResp, DataReportReq, DataSchemaResp } from '@/types/data-analysis'

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
