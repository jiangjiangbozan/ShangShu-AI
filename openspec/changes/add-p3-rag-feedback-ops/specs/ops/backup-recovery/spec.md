## Purpose

构建 MySQL 逻辑备份、ES 快照备份、MySQL binlog 三层容灾体系，支持误删数据恢复与按时间点回滚。

## ADDED Requirements

### Requirement: MySQL JSONL 每日备份

系统 SHALL 每日凌晨 03:00 导出 `chat_session` 与 `chat_message_log` 表为 JSONL 文件，保留 7 天。

#### Scenario: 备份成功生成

- **WHEN** 每日 03:00 cron 触发
- **THEN** 导出 `chat_session` 与 `chat_message_log` 全表为 JSONL 文件
- **AND** 文件命名 `chat_session_YYYYMMDD.jsonl`、`chat_message_log_YYYYMMDD.jsonl`
- **AND** 存储到 `${backup.mysql.path}`（默认 `/data/backup/mysql/`）

#### Scenario: 7 天自动清理

- **WHEN** 备份任务执行
- **THEN** 删除 `${backup.mysql.path}` 下 7 天前的 JSONL 文件
- **AND** 记录清理日志

#### Scenario: 备份失败告警

- **WHEN** 导出失败（如磁盘满或 MySQL 不可达）
- **THEN** 记录 ERROR 日志并触发告警（Prometheus alertmanager）
- **AND** 不影响主流程

### Requirement: ES 快照每周备份

系统 SHALL 每周日凌晨 02:30 对知识库向量索引做快照，注册 snapshot repository。

#### Scenario: 快照成功创建

- **WHEN** 每周日 02:30 cron 触发
- **THEN** 调用 `PUT /_snapshot/kb_backup_repo/snapshot_{YYYYMMDD}` 创建快照
- **AND** 快照包含所有 `kb_*` 索引
- **AND** 快照状态为 `SUCCESS` 后记录日志

#### Scenario: 快照恢复

- **WHEN** 误删文档后管理员调用恢复接口
- **THEN** 调用 `POST /_snapshot/kb_backup_repo/snapshot_{date}/_restore` 恢复索引
- **AND** 恢复完成后索引可正常检索

#### Scenario: 快照清理

- **WHEN** 快照数量 > 4（约一个月）
- **THEN** 删除最旧的快照
- **AND** 保留近 4 个快照

### Requirement: MySQL binlog 规范

MySQL SHALL 开启 row 模式 binlog，保留 7 天，支持按时间点回滚。

#### Scenario: binlog 配置生效

- **WHEN** MySQL 启动
- **THEN** `log_bin=ON`，`binlog_format=ROW`，`binlog_expire_logs_seconds=604800`（7 天）
- **AND** `binlog_row_image=FULL` 记录完整行数据

#### Scenario: 按时间点回滚验证

- **WHEN** 管理员误删某会话数据
- **THEN** 通过 `mysqlbinlog --start-datetime='YYYY-MM-DD HH:MM:SS' --stop-datetime=...` 解析 binlog
- **AND** 提取 ROW 事件重建 SQL，恢复数据
- **AND** 验证恢复后数据完整性

#### Scenario: binlog 容量监控

- **WHEN** binlog 文件总大小超过 10GB
- **THEN** Prometheus 采集 binlog 大小指标
- **AND** 触发告警提示运维检查
