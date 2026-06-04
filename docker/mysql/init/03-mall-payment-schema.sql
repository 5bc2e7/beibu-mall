-- ============================================================
-- 北部湾海鲜商城 - 支付服务数据库初始化
-- ============================================================

CREATE DATABASE IF NOT EXISTS `mall_payment`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE `mall_payment`;

-- 支付订单表
CREATE TABLE IF NOT EXISTS `payment_order` (
    `id`              BIGINT          NOT NULL COMMENT '支付单ID',
    `order_id`        VARCHAR(64)     NOT NULL COMMENT '业务订单号',
    `payment_no`      VARCHAR(64)     NOT NULL COMMENT '支付单号',
    `user_id`         BIGINT          NOT NULL COMMENT '用户ID',
    `amount`          DECIMAL(10, 2)  NOT NULL COMMENT '支付金额（元）',
    `payment_method`  TINYINT         NOT NULL DEFAULT 1 COMMENT '支付方式：1支付宝 2微信 3银行卡',
    `status`          TINYINT         NOT NULL DEFAULT 0 COMMENT '支付状态：0待支付 1成功 2失败 3已关闭',
    `trade_no`        VARCHAR(128)    DEFAULT NULL COMMENT '第三方交易号',
    `payment_time`    DATETIME        DEFAULT NULL COMMENT '支付成功时间',
    `close_time`      DATETIME        DEFAULT NULL COMMENT '关闭时间',
    `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    PRIMARY KEY (id),
    UNIQUE KEY `uk_order_id` (`order_id`),
    UNIQUE KEY `uk_payment_no` (`payment_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付订单表';

-- 支付流水日志表
CREATE TABLE IF NOT EXISTS `payment_log` (
    `id`              BIGINT          NOT NULL COMMENT '日志ID',
    `payment_id`      BIGINT          NOT NULL COMMENT '支付单ID',
    `order_id`        VARCHAR(64)     NOT NULL COMMENT '业务订单号',
    `operation`       VARCHAR(32)     NOT NULL COMMENT '操作类型',
    `before_status`   TINYINT         DEFAULT NULL COMMENT '操作前状态',
    `after_status`    TINYINT         DEFAULT NULL COMMENT '操作后状态',
    `remark`          VARCHAR(500)    DEFAULT NULL COMMENT '操作说明',
    `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY `idx_payment_id` (`payment_id`),
    KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付流水日志表';

-- 插入测试数据（可选）
INSERT INTO `payment_order` (`id`, `order_id`, `payment_no`, `user_id`, `amount`, `payment_method`, `status`)
VALUES
    (1, 'ORDER_TEST_001', 'PAY_TEST_001', 1001, 99.99, 1, 0),
    (2, 'ORDER_TEST_002', 'PAY_TEST_002', 1002, 199.00, 2, 0);
