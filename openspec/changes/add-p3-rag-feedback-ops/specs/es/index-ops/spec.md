## Purpose

通过动态调整 ES 索引 `refresh_interval` 与按容量规划分片数，提升批量导入吞吐与长期查询稳定性。

## ADDED Requirements

### Requirement: 批量导入动态 refresh_interval

批量导入文档时 SHALL 临时设置索引 `refresh_interval = -1`，导入完成后恢复默认 `1s`。

#### Scenario: 导入开始禁用刷新

- **WHEN** 调用 `KnowledgeBaseService.batchImport` 且文档数量 > 1000
- **THEN** 调用 `esIndexOpsService.setRefreshInterval(index, "-1")`
- **AND** 后续批量写入不触发 segment refresh

#### Scenario: 导入完成恢复刷新

- **WHEN** 批量导入结束（无论成功失败）
- **THEN** 在 finally 块中调用 `esIndexOpsService.setRefreshInterval(index, "1s")`
- **AND** 触发一次 `POST /index/_refresh`，使新文档可被检索

#### Scenario: 小批量导入不调整

- **WHEN** 批量导入文档数量 ≤ 1000
- **THEN** 不调整 `refresh_interval`，按默认 1s 处理
- **AND** 避免小批量场景下不必要的索引设置变更

### Requirement: 分片容量规划

新增知识库索引 SHALL 按 30GB/分片标准规划分片数，结合业务预估数据量动态计算。

#### Scenario: 新建知识库索引分片计算

- **WHEN** 创建新知识库
- **THEN** 根据预估文档数与平均 chunk 大小计算总分片数：`shardCount = ceil(预估总大小GB / 30)`
- **AND** 最小 1 分片，最大 10 分片
- **AND** 默认副本数 1（生产环境 2）

#### Scenario: 索引设置生效

- **WHEN** 知识库索引创建
- **THEN** `number_of_shards` 与 `number_of_replicas` 按规划值设置
- **AND** 记录日志便于后续运维审计

### Requirement: 副本数环境差异化

副本数 SHALL 按环境差异化配置（dev=1，prod=2），支持运行时调整。

#### Scenario: 开发环境副本数

- **WHEN** `spring.profiles.active=dev`
- **THEN** 新建索引副本数 = 1
- **AND** 单节点 ES 集群下副本分片不分配（健康状态 yellow 可接受）

#### Scenario: 生产环境副本数

- **WHEN** `spring.profiles.active=prod`
- **THEN** 新建索引副本数 = 2
- **AND** 多节点集群下副本分片正常分配，健康状态 green
