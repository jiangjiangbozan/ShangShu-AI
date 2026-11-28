-- ============================================================
-- 移动端任务中心查询索引
-- 背景：任务列表按 user_id + status 过滤并按 create_time 排序，
--   现有索引均不以 user_id 开头，数据量增长后会退化为全表扫描。
-- 影响：仅新增索引，不改变数据语义；匿名 Agent Runtime 的 user_id 为 NULL，
--   不会被用户维度列表查询命中。
-- ============================================================

ALTER TABLE agent_runtime
    ADD INDEX idx_user_status_time (user_id, status, create_time);
