-- ============================================================
-- 北部湾海鲜商城 - 订单服务数据库建表脚本
-- ============================================================
-- 数据库：mall_order
-- 说明：订单服务独立数据库，遵循"每个服务独立数据库"原则
-- ============================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS `mall_order` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `mall_order`;

-- ============================================================
-- 1. 订单主表
-- ============================================================
-- 作用：存储订单的基本信息（谁买的、多少钱、什么状态）
-- 设计要点：
-- - order_no 用雪花算法生成，全局唯一且有序
-- - 收货地址信息冗余存储，避免地址修改影响历史订单
-- - status 用数字表示状态，方便扩展
-- ============================================================
DROP TABLE IF EXISTS `order_info`;
CREATE TABLE `order_info` (
    `id` BIGINT NOT NULL COMMENT '主键ID（雪花算法生成）',
    `order_no` VARCHAR(64) NOT NULL COMMENT '订单号（雪花算法生成，全局唯一）',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    
    -- 金额相关
    `total_amount` DECIMAL(10,2) NOT NULL COMMENT '订单总金额（单位：元）',
    `pay_amount` DECIMAL(10,2) NOT NULL COMMENT '实付金额（总金额 - 优惠 + 运费）',
    `freight_amount` DECIMAL(10,2) DEFAULT 0.00 COMMENT '运费',
    `discount_amount` DECIMAL(10,2) DEFAULT 0.00 COMMENT '优惠金额',
    
    -- 收货地址（冗余存储，快照）
    -- 为什么冗余？因为用户可能修改地址，但历史订单应该保留下单时的地址
    `receiver_name` VARCHAR(64) NOT NULL COMMENT '收货人姓名',
    `receiver_phone` VARCHAR(20) NOT NULL COMMENT '收货人电话',
    `receiver_province` VARCHAR(32) COMMENT '省',
    `receiver_city` VARCHAR(32) COMMENT '市',
    `receiver_district` VARCHAR(32) COMMENT '区',
    `receiver_detail` VARCHAR(256) NOT NULL COMMENT '详细地址',
    
    -- 订单状态
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '订单状态：0待支付 1已支付 2已发货 3已完成 4已取消 5已退款',
    `auto_cancel_time` DATETIME COMMENT '自动取消时间（下单后30分钟未支付自动取消）',
    `pay_time` DATETIME COMMENT '支付时间',
    `deliver_time` DATETIME COMMENT '发货时间',
    `receive_time` DATETIME COMMENT '收货时间',
    `cancel_time` DATETIME COMMENT '取消时间',
    `cancel_reason` VARCHAR(256) COMMENT '取消原因',
    
    -- 备注
    `remark` VARCHAR(256) COMMENT '订单备注',
    
    -- 时间字段
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    -- 复合索引：优化 "查询某用户的订单，按时间倒序" 的常见查询
    -- 为什么用复合索引而不是两个单独索引？
    -- 复合索引 (user_id, create_time) 可以同时用于 WHERE 和 ORDER BY
    -- 两个单独索引只能用于各自的 WHERE 条件，排序需要额外的文件排序
    KEY `idx_user_id_create_time` (`user_id`, `create_time`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单主表';

-- ============================================================
-- 2. 订单商品明细表
-- ============================================================
-- 作用：存储订单中每个商品的详细信息
-- 设计要点：
-- - 冗余存储商品名称、价格、图片，避免商品修改影响历史订单
-- - sku_snapshot 存储规格快照（如"鲜活/500g"）
-- ============================================================
DROP TABLE IF EXISTS `order_item`;
CREATE TABLE `order_item` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `order_id` BIGINT NOT NULL COMMENT '订单ID（关联 order_info.id）',
    `order_no` VARCHAR(64) NOT NULL COMMENT '订单号（冗余，方便查询）',
    
    -- 商品信息（下单时快照）
    `sku_id` BIGINT NOT NULL COMMENT 'SKU ID',
    `spu_id` BIGINT COMMENT 'SPU ID',
    `product_name` VARCHAR(128) NOT NULL COMMENT '商品名称',
    `sku_spec` VARCHAR(128) COMMENT 'SKU规格（如：鲜活/500g）',
    `product_img` VARCHAR(256) COMMENT '商品图片URL',
    
    -- 价格和数量
    `price` DECIMAL(10,2) NOT NULL COMMENT '单价（下单时的价格）',
    `quantity` INT NOT NULL COMMENT '购买数量',
    `subtotal` DECIMAL(10,2) NOT NULL COMMENT '小计 = price * quantity',
    
    -- 时间字段
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_order_no` (`order_no`),
    KEY `idx_sku_id` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单商品明细表';

-- ============================================================
-- 订单状态流转说明
-- ============================================================
-- 待支付(0) --[用户支付]--> 已支付(1)
-- 待支付(0) --[超时/用户取消]--> 已取消(4)
-- 已支付(1) --[商家发货]--> 已发货(2)
-- 已发货(2) --[用户收货]--> 已完成(3)
-- 已支付(1) --[用户退款]--> 已退款(5)
-- ============================================================
