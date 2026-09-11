/** 运维备份 API 封装
 *
 * 对应后端 com.ai.api.ops.backup.OpsBackupController
 */
import { get, post } from '@/utils/request'

/** 触发 MySQL JSONL 逻辑备份 */
export function backupMysql() {
  return post<void>('/ops/backup/mysql')
}

/** 触发 ES 快照 */
export function snapshotEs() {
  return post<void>('/ops/backup/es/snapshot')
}

/** 恢复指定日期的 ES 快照 */
export function restoreEs(date: string) {
  return post<void>('/ops/backup/es/restore', { date })
}

/** binlog 配置巡检 */
export function verifyBinlog() {
  return get<Record<string, unknown>>('/ops/binlog/verify')
}
