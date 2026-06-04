-- ============================================================
-- 秒杀服务数据库初始化脚本
-- ============================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS mall_seckill DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE mall_seckill;

-- ============================================================
-- 秒杀活动表
-- ============================================================
-- 这张表存储秒杀活动的基本信息，比如"帝王蟹秒杀活动"
CREATE TABLE IF NOT EXISTS seckill_activity (
    id BIGINT NOT NULL COMMENT '活动ID（雪花算法生成）',
    activity_name VARCHAR(200) NOT NULL COMMENT '活动名称，比如"帝王蟹限时秒杀"',
    product_id BIGINT NOT NULL COMMENT '商品ID（关联商品服务）',
    product_name VARCHAR(200) NOT NULL COMMENT '商品名称（冗余，避免跨服务查询）',
    product_image VARCHAR(500) COMMENT '商品图片URL',
    original_price DECIMAL(10,2) NOT NULL COMMENT '原价',
    seckill_price DECIMAL(10,2) NOT NULL COMMENT '秒杀价',
    total_stock INT NOT NULL COMMENT '总库存',
    available_stock INT NOT NULL COMMENT '剩余可抢库存',
    start_time DATETIME NOT NULL COMMENT '活动开始时间',
    end_time DATETIME NOT NULL COMMENT '活动结束时间',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0=未开始，1=进行中，2=已结束',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
    PRIMARY KEY (id),
    KEY idx_product_id (product_id),
    KEY idx_start_time (start_time),
    KEY idx_status (status),
    KEY idx_status_start_time (status, start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀活动表';

-- ============================================================
-- 秒杀订单表
-- ============================================================
-- 这张表存储秒杀成功的订单
-- 重点：uk_user_activity 这个唯一索引，保证同一个用户在同一个活动中只能抢一次
CREATE TABLE IF NOT EXISTS seckill_order (
    id BIGINT NOT NULL COMMENT '订单ID（雪花算法生成）',
    activity_id BIGINT NOT NULL COMMENT '活动ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    product_name VARCHAR(200) NOT NULL COMMENT '商品名称',
    seckill_price DECIMAL(10,2) NOT NULL COMMENT '秒杀价格',
    order_status TINYINT NOT NULL DEFAULT 0 COMMENT '订单状态：0=待支付，1=已支付，2=已取消',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删除，1=已删除',
    PRIMARY KEY (id),
    -- 唯一索引：同一个用户在同一个活动中只能有一条订单
    -- 这是防止重复抢购的最后一道防线（数据库层面兜底）
    UNIQUE KEY uk_user_activity (user_id, activity_id),
    KEY idx_activity_id (activity_id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀订单表';

-- ============================================================
-- 插入测试数据
-- ============================================================
-- 插入一个秒杀活动：帝王蟹限时秒杀
INSERT INTO seckill_activity (id, activity_name, product_id, product_name, product_image, original_price, seckill_price, total_stock, available_stock, start_time, end_time, status)
VALUES
(1, '帝王蟹限时秒杀', 1001, '北海道帝王蟹 2.5-3斤/只', 'https://example.com/king-crab.jpg', 599.00, 99.00, 10, 10, '2026-06-05 15:00:00', '2026-06-05 15:30:00', 0);
