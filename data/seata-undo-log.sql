-- =============================================
-- Seata AT 模式必须的回滚日志表
-- =============================================
--
-- 这个表是 Seata AT 模式的核心！
--
-- 什么是 undo_log？
--   就是"后悔药"，记录了每个数据库操作"之前的数据"。
--   如果分布式事务失败，Seata 会用这些数据把数据库恢复到原来的状态。
--
-- 需要在哪些数据库执行？
--   每个参与分布式事务的数据库都要加这张表！
--   本项目中：mall_order 和 mall_inventory 都需要。
--

CREATE TABLE IF NOT EXISTS `undo_log` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
    `branch_id` BIGINT(20) NOT NULL COMMENT '分支事务ID（每个服务的本地事务ID）',
    `xid` VARCHAR(100) NOT NULL COMMENT '全局事务ID（整个分布式事务的ID）',
    `context` VARCHAR(128) NOT NULL COMMENT '上下文（Seata 内部使用）',
    `rollback_info` LONGBLOB NOT NULL COMMENT '回滚数据（JSON格式，记录了操作前的数据）',
    `log_status` INT(11) NOT NULL COMMENT '日志状态：0-正常，1-已完成回滚',
    `log_created` DATETIME NOT NULL COMMENT '创建时间',
    `log_modified` DATETIME NOT NULL COMMENT '修改时间',
    `ext` VARCHAR(100) DEFAULT NULL COMMENT '扩展字段',
    PRIMARY KEY (`id`),
    UNIQUE KEY `ux_undo_log` (`xid`, `branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Seata AT 模式回滚日志表';

-- 添加索引（根据 Seata 官方最佳实践，提高性能）
ALTER TABLE `undo_log` ADD INDEX `ix_log_created` (`log_created`);

-- =============================================
-- 说明
-- =============================================
--
-- xid（全局事务ID）：
--   比如你下单时，Seata 会生成一个全局事务ID，类似 "192.168.1.1:8091:1234567890"
--   订单服务和库存服务都会拿到这个 ID
--
-- branch_id（分支事务ID）：
--   每个服务的本地事务都有一个唯一的 ID
--   比如：订单保存是一个分支，库存扣减是另一个分支
--
-- rollback_info（回滚数据）：
--   记录了操作前的数据，JSON 格式
--   比如：库存从 100 扣到 98，rollback_info 会记录 {"before": 100, "after": 98}
--   如果要回滚，Seata 会用这个数据把库存改回 100
--
-- log_status（日志状态）：
--   0 = 正常（事务还在进行中）
--   1 = 已完成回滚（回滚完成，可以清理了）
--
-- Seata 会自动清理已回滚的 undo_log，不需要手动维护
