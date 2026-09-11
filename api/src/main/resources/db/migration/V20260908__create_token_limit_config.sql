-- Token 限额 / 限流配置表（A2）
-- scope 维度 + target_id 定位：target_id=0 表示该 scope 的默认（全局）配置
CREATE TABLE IF NOT EXISTS `sys_token_limit_config` (
    `id`                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    `scope`                VARCHAR(16)   NOT NULL DEFAULT 'USER' COMMENT '作用域: USER/TENANT/PUBLIC',
    `target_id`            BIGINT        NOT NULL DEFAULT 0 COMMENT '目标 ID: 0=默认(该 scope 全部生效), 非0=具体用户/租户',
    `token_quota`          BIGINT        NOT NULL DEFAULT 0 COMMENT '周期 Token 限额（0=不限）',
    `quota_period`         VARCHAR(16)   NOT NULL DEFAULT 'DAY' COMMENT '限额周期: DAY/MONTH',
    `rate_limit_per_minute` INT          NOT NULL DEFAULT 0 COMMENT '每分钟请求数限制（0=不限）',
    `rate_limit_per_day`   INT           NOT NULL DEFAULT 0 COMMENT '每日请求数限制（0=不限）',
    `status`               VARCHAR(16)   NOT NULL DEFAULT 'active' COMMENT '状态: active/disabled',
    `remark`               VARCHAR(255)  DEFAULT NULL COMMENT '备注',
    `creator`              VARCHAR(64)   DEFAULT NULL COMMENT '创建人',
    `create_time`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`              VARCHAR(64)   DEFAULT NULL COMMENT '更新人',
    `update_time`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`              TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY `uk_scope_target` (`scope`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Token 限额/限流配置';

-- 默认配置示例：普通用户每分钟 100 次、每日 5000 次、每日 token 限额 200 万
INSERT INTO `sys_token_limit_config`
    (`scope`, `target_id`, `token_quota`, `quota_period`, `rate_limit_per_minute`, `rate_limit_per_day`, `status`, `remark`)
VALUES
    ('USER', 0, 2000000, 'DAY', 100, 5000, 'active', '默认用户限额');
