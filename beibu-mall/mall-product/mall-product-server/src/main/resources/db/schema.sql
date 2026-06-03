-- ==================== 商品服务建表 SQL ====================
-- 数据库：mall_product
-- 字符集：utf8mb4（支持 emoji 表情）
-- 引擎：InnoDB（支持事务、行锁）

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS mall_product DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE mall_product;

-- ==================== 1. 商品分类表 ====================
-- 什么是分类？
-- 分类是商品的"标签"，帮助用户快速找到想要的商品。
-- 例如：活鲜 → 鱼类、虾类、蟹类
-- 为什么用 parent_id 实现树形结构？
-- 因为分类可能是多级的（大类 → 小类 → 子类），parent_id 可以实现无限层级。

CREATE TABLE t_category (
  id        BIGINT PRIMARY KEY AUTO_INCREMENT,
  parent_id BIGINT DEFAULT 0,        -- 父分类ID，0表示顶级分类
  name      VARCHAR(50) NOT NULL,    -- 分类名称
  level     TINYINT DEFAULT 1,       -- 层级深度（1=顶级，2=二级，3=三级...）
  sort      INT DEFAULT 0,           -- 排序号（越小越靠前）
  status    TINYINT DEFAULT 1,       -- 状态：1启用 0禁用
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted   TINYINT DEFAULT 0,       -- 逻辑删除：0未删 1已删
  KEY idx_parent(parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== 2. SPU 表 ====================
-- 什么是 SPU？
-- SPU = Standard Product Unit（标准产品单元）
-- 是商品的"抽象定义"，不包含具体规格信息。
-- 例如「北部湾大对虾」就是一个 SPU，它代表一类商品。
-- 一个 SPU 可以有多个 SKU（不同规格）。

CREATE TABLE t_spu (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  category_id  BIGINT NOT NULL,          -- 所属分类ID
  name         VARCHAR(100) NOT NULL,    -- 商品名称（如：北部湾大对虾）
  origin       VARCHAR(50),              -- 产地（如：广西北海）
  is_fresh     TINYINT DEFAULT 0,        -- 是否活鲜：1活鲜 0非活鲜
  price_type   TINYINT DEFAULT 0,        -- 计价方式：0按件 1按重(克)
  shelf_life   INT,                      -- 保质期（小时），活鲜很短
  min_buy      INT DEFAULT 1,            -- 起售量
  description  TEXT,                      -- 商品描述（支持富文本）
  status       TINYINT DEFAULT 0,        -- 状态：0下架 1上架
  create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted      TINYINT DEFAULT 0,
  KEY idx_category(category_id),
  KEY idx_status(status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== 3. SKU 表 ====================
-- 什么是 SKU？
-- SKU = Stock Keeping Unit（库存量单位）
-- 是商品的"具体规格"，是库存管理的最小单位。
-- 例如「北部湾大对虾」这个 SPU 下有多个 SKU：
--   - SKU1：鲜活/500g/¥89
--   - SKU2：鲜活/1000g/¥168
--   - SKU3：冰鲜/500g/¥69

CREATE TABLE t_sku (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  spu_id      BIGINT NOT NULL,           -- 所属 SPU ID
  spec        VARCHAR(100),              -- 规格描述（如：鲜活/500g）
  price       DECIMAL(10,2) NOT NULL,    -- 价格
  unit        VARCHAR(10),               -- 单位（件/g/kg）
  stock       INT DEFAULT 0,             -- 库存数量
  img         VARCHAR(255),              -- SKU 图片
  status      TINYINT DEFAULT 1,         -- 状态：1启用 0禁用
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted     TINYINT DEFAULT 0,
  KEY idx_spu(spu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== 初始数据 ====================

-- 顶级分类
INSERT INTO t_category (id, parent_id, name, level, sort) VALUES
(1, 0, '活鲜', 1, 1),
(2, 0, '冰鲜', 1, 2),
(3, 0, '冻品', 1, 3),
(4, 0, '干货', 1, 4);

-- 活鲜子分类
INSERT INTO t_category (id, parent_id, name, level, sort) VALUES
(5, 1, '鱼类', 2, 1),
(6, 1, '虾类', 2, 2),
(7, 1, '蟹类', 2, 3),
(8, 1, '贝类', 2, 4);

-- 冰鲜子分类
INSERT INTO t_category (id, parent_id, name, level, sort) VALUES
(9, 2, '冰鲜鱼', 2, 1),
(10, 2, '冰鲜虾', 2, 2);

-- 冻品子分类
INSERT INTO t_category (id, parent_id, name, level, sort) VALUES
(11, 3, '冻鱼', 2, 1),
(12, 3, '冻虾', 2, 2);

-- 干货子分类
INSERT INTO t_category (id, parent_id, name, level, sort) VALUES
(13, 4, '干鱼', 2, 1),
(14, 4, '干虾', 2, 2);

-- 示例商品：北部湾大对虾
INSERT INTO t_spu (id, category_id, name, origin, is_fresh, price_type, shelf_life, min_buy, description, status) VALUES
(1, 6, '北部湾大对虾', '广西北海', 1, 1, 24, 1, '北部湾特产，鲜活捕捞，肉质鲜美', 1);

-- 示例 SKU
INSERT INTO t_sku (id, spu_id, spec, price, unit, stock, status) VALUES
(1, 1, '鲜活/500g', 89.00, 'g', 100, 1),
(2, 1, '鲜活/1000g', 168.00, 'g', 50, 1),
(3, 1, '冰鲜/500g', 69.00, 'g', 200, 1);
