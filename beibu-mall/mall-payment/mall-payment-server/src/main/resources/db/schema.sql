-- ============================================================
-- 北部湾海鲜商城 - 支付服务数据库
-- ============================================================
-- 每个微服务有自己的数据库，这是微服务的核心原则之一！
-- 支付服务只能访问 mall_payment 库，不能连接其他库。
-- ============================================================

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS mall_payment
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE mall_payment;

-- ============================================================
-- 支付订单表
-- ============================================================
-- 设计要点：
-- 1. order_id 加唯一索引：防止同一笔订单重复创建支付单（幂等性的数据库层保障）
-- 2. 金额用 DECIMAL：避免浮点数精度问题
-- 3. 状态用数字：方便扩展，配合枚举类使用
-- ============================================================
CREATE TABLE IF NOT EXISTS payment_order (
    -- 主键ID（雪花算法生成，全局唯一）
    id              BIGINT          NOT NULL COMMENT '支付单ID',
    
    -- 关联的业务订单号（来自订单服务）
    -- 唯一索引！同一笔订单只能有一个支付单，这是幂等性的核心保障
    order_id        VARCHAR(64)     NOT NULL COMMENT '业务订单号',
    
    -- 支付单号（支付系统自己生成的唯一编号）
    payment_no      VARCHAR(64)     NOT NULL COMMENT '支付单号',
    
    -- 用户ID
    user_id         BIGINT          NOT NULL COMMENT '用户ID',
    
    -- 支付金额（DECIMAL(10,2) 表示最多10位数，其中2位小数）
    amount          DECIMAL(10, 2)  NOT NULL COMMENT '支付金额（元）',
    
    -- 支付方式：1-支付宝 2-微信 3-银行卡
    payment_method  TINYINT         NOT NULL DEFAULT 1 COMMENT '支付方式：1支付宝 2微信 3银行卡',
    
    -- 支付状态：0-待支付 1-支付成功 2-支付失败 3-已关闭
    status          TINYINT         NOT NULL DEFAULT 0 COMMENT '支付状态：0待支付 1成功 2失败 3已关闭',
    
    -- 第三方支付平台的交易号（如支付宝流水号）
    trade_no        VARCHAR(128)    DEFAULT NULL COMMENT '第三方交易号',
    
    -- 支付成功时间
    payment_time    DATETIME        DEFAULT NULL COMMENT '支付成功时间',
    
    -- 关闭时间（超时未支付自动关闭）
    close_time      DATETIME        DEFAULT NULL COMMENT '关闭时间',
    
    -- 创建时间
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    -- 更新时间（每次更新自动刷新）
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    -- 逻辑删除：0未删除 1已删除
    deleted         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    
    PRIMARY KEY (id),
    
    -- 唯一索引：order_id 不能重复！
    -- 这是幂等性的数据库层保障：即使代码有bug，数据库也会拒绝重复插入
    UNIQUE KEY uk_order_id (order_id),
    
    -- 普通索引：按支付单号查询
    UNIQUE KEY uk_payment_no (payment_no),
    
    -- 普通索引：按用户ID查询（查"我的支付记录"）
    KEY idx_user_id (user_id),
    
    -- 普通索引：按状态查询（查待支付的单子做超时关闭）
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付订单表';


-- ============================================================
-- 支付流水日志表（审计追踪）
-- ============================================================
-- 记录每一次支付状态变更，用于对账和问题排查
-- 比如：用户说"我付了钱但订单没变"，查这张表就能看到支付回调是否到达
-- ============================================================
CREATE TABLE IF NOT EXISTS payment_log (
    -- 主键ID
    id              BIGINT          NOT NULL COMMENT '日志ID',
    
    -- 关联的支付单ID
    payment_id      BIGINT          NOT NULL COMMENT '支付单ID',
    
    -- 关联的业务订单号
    order_id        VARCHAR(64)     NOT NULL COMMENT '业务订单号',
    
    -- 操作类型：CREATE-创建 CALLBACK-回调 CLOSE-关闭 REFUND-退款
    operation       VARCHAR(32)     NOT NULL COMMENT '操作类型',
    
    -- 操作前的状态
    before_status   TINYINT         DEFAULT NULL COMMENT '操作前状态',
    
    -- 操作后的状态
    after_status    TINYINT         DEFAULT NULL COMMENT '操作后状态',
    
    -- 操作说明（记录详细信息，方便排查问题）
    remark          VARCHAR(500)    DEFAULT NULL COMMENT '操作说明',
    
    -- 创建时间
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    PRIMARY KEY (id),
    
    -- 按支付单ID查询流水
    KEY idx_payment_id (payment_id),
    
    -- 按订单号查询流水
    KEY idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付流水日志表';
