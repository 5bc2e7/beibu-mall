-- 创建数据库
CREATE DATABASE IF NOT EXISTS mall_inventory DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE mall_inventory;

-- 库存表：存储商品的库存信息
-- 可用库存（available_stock）：当前可以卖给用户的数量
-- 预占库存（locked_stock）：已经被订单锁定但还没真正扣减的数量
-- 版本号（version）：乐观锁用，每次更新+1，防止并发超卖
CREATE TABLE IF NOT EXISTS inventory_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    sku_id VARCHAR(64) NOT NULL COMMENT '商品SKU ID（SKU=Stock Keeping Unit，库存量单位，比如"红色XL码T恤"就是一个SKU）',
    product_id VARCHAR(64) NOT NULL COMMENT '商品ID',
    product_name VARCHAR(255) NOT NULL COMMENT '商品名称（冗余字段，避免关联查询）',
    available_stock INT NOT NULL DEFAULT 0 COMMENT '可用库存（可以卖给用户的数量）',
    locked_stock INT NOT NULL DEFAULT 0 COMMENT '预占库存（被订单锁定但还没扣减的数量）',
    total_stock INT NOT NULL DEFAULT 0 COMMENT '总库存（= 可用库存 + 预占库存）',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁用，每次更新+1）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_sku_id (sku_id) COMMENT 'SKU ID 唯一索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存表';

-- 库存变动流水表：记录每一次库存变动的明细
-- 用途：审计、排查问题、数据对账
CREATE TABLE IF NOT EXISTS inventory_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    sku_id VARCHAR(64) NOT NULL COMMENT '商品SKU ID',
    order_id VARCHAR(64) COMMENT '关联的订单ID（如果是订单扣减的话）',
    change_type VARCHAR(32) NOT NULL COMMENT '变动类型：DEDUCT-扣减，OCCUPY-预占，RELEASE-释放，CONFIRM-确认扣减',
    change_quantity INT NOT NULL COMMENT '变动数量（正数表示扣减/预占，负数表示释放）',
    before_stock INT NOT NULL COMMENT '变动前库存',
    after_stock INT NOT NULL COMMENT '变动后库存',
    remark VARCHAR(255) COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_sku_id (sku_id),
    INDEX idx_order_id (order_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存变动流水表';

-- 插入一些测试数据
INSERT INTO inventory_item (sku_id, product_id, product_name, available_stock, locked_stock, total_stock, version) VALUES
('SKU001', 'P001', '北海大虾（500g装）', 100, 0, 100, 0),
('SKU002', 'P002', '钦州生蚝（12只装）', 200, 0, 200, 0),
('SKU003', 'P003', '防城港金鲳鱼（1条）', 50, 0, 50, 0);