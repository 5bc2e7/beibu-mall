-- Nacos 3.x 数据库初始化脚本
-- 说明：这个脚本会在 MySQL 容器首次启动时自动执行
-- 作用：创建 Nacos 需要的数据库和表结构

-- 创建 Nacos 专用数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS `nacos_config` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 切换到 nacos_config 数据库
USE `nacos_config`;

-- ========================================
-- 配置信息主表（存储所有微服务的配置）
-- ========================================
CREATE TABLE `config_info` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
    `data_id` varchar(255) NOT NULL COMMENT '配置ID（如 user-service.yaml）',
    `group_id` varchar(128) DEFAULT NULL COMMENT '分组（默认 DEFAULT_GROUP）',
    `content` longtext NOT NULL COMMENT '配置内容（YAML/JSON/Properties）',
    `md5` varchar(32) DEFAULT NULL COMMENT '内容的MD5值，用于判断是否变化',
    `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
    `src_user` text COMMENT '创建者',
    `src_ip` varchar(50) DEFAULT NULL COMMENT '创建者IP',
    `app_name` varchar(128) DEFAULT NULL COMMENT '应用名',
    `tenant_id` varchar(128) DEFAULT '' COMMENT '租户ID（多租户隔离用）',
    `c_desc` varchar(256) DEFAULT NULL COMMENT '配置描述',
    `c_use` varchar(64) DEFAULT NULL COMMENT '配置用途',
    `effect` varchar(64) DEFAULT NULL COMMENT '生效描述',
    `type` varchar(64) DEFAULT NULL COMMENT '配置类型（yaml/json/properties）',
    `c_schema` text COMMENT '配置模式',
    `encrypted_data_key` varchar(1024) NOT NULL DEFAULT '' COMMENT '加密密钥',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_configinfo_datagrouptenant` (`data_id`,`group_id`,`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='Nacos配置信息表';

-- ========================================
-- 灰度发布配置表（Nacos 2.5.0+ 新增）
-- ========================================
CREATE TABLE `config_info_gray` (
    `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'id',
    `data_id` varchar(255) NOT NULL COMMENT '配置ID',
    `group_id` varchar(128) NOT NULL COMMENT '分组',
    `content` longtext NOT NULL COMMENT '配置内容',
    `md5` varchar(32) DEFAULT NULL COMMENT 'MD5',
    `src_user` text COMMENT '创建者',
    `src_ip` varchar(100) DEFAULT NULL COMMENT '创建者IP',
    `gmt_create` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `gmt_modified` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '修改时间',
    `app_name` varchar(128) DEFAULT NULL COMMENT '应用名',
    `tenant_id` varchar(128) DEFAULT '' COMMENT '租户ID',
    `gray_name` varchar(128) NOT NULL COMMENT '灰度名称',
    `gray_rule` text NOT NULL COMMENT '灰度规则',
    `encrypted_data_key` varchar(256) NOT NULL DEFAULT '' COMMENT '加密密钥',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_configinfogray_datagrouptenantgray` (`data_id`,`group_id`,`tenant_id`,`gray_name`),
    KEY `idx_dataid_gmt_modified` (`data_id`,`gmt_modified`),
    KEY `idx_gmt_modified` (`gmt_modified`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 COMMENT='灰度配置表';

-- ========================================
-- 配置标签关联表
-- ========================================
CREATE TABLE `config_tags_relation` (
    `id` bigint(20) NOT NULL COMMENT '配置ID',
    `tag_name` varchar(128) NOT NULL COMMENT '标签名',
    `tag_type` varchar(64) DEFAULT NULL COMMENT '标签类型',
    `data_id` varchar(255) NOT NULL COMMENT '配置ID',
    `group_id` varchar(128) NOT NULL COMMENT '分组',
    `tenant_id` varchar(128) DEFAULT '' COMMENT '租户ID',
    `nid` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '自增ID',
    PRIMARY KEY (`nid`),
    UNIQUE KEY `uk_configtagrelation_configidtag` (`id`,`tag_name`,`tag_type`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='配置标签关联表';

-- ========================================
-- 分组容量信息表
-- ========================================
CREATE TABLE `group_capacity` (
    `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `group_id` varchar(128) NOT NULL DEFAULT '' COMMENT '分组ID',
    `quota` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '配额（0=默认值）',
    `usage` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '已使用量',
    `max_size` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '单个配置大小上限（字节）',
    `max_aggr_count` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '聚合子配置最大个数',
    `max_aggr_size` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '单个聚合数据大小上限（字节）',
    `max_history_count` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '最大变更历史数量',
    `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_id` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='分组容量信息表';

-- ========================================
-- 配置历史表（记录每次配置变更）
-- ========================================
CREATE TABLE `his_config_info` (
    `id` bigint(20) unsigned NOT NULL COMMENT '配置ID',
    `nid` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '自增ID',
    `data_id` varchar(255) NOT NULL COMMENT '配置ID',
    `group_id` varchar(128) NOT NULL COMMENT '分组',
    `app_name` varchar(128) DEFAULT NULL COMMENT '应用名',
    `content` longtext NOT NULL COMMENT '配置内容',
    `md5` varchar(32) DEFAULT NULL COMMENT 'MD5',
    `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
    `src_user` text COMMENT '创建者',
    `src_ip` varchar(50) DEFAULT NULL COMMENT '创建者IP',
    `op_type` char(10) DEFAULT NULL COMMENT '操作类型',
    `tenant_id` varchar(128) DEFAULT '' COMMENT '租户ID',
    `encrypted_data_key` varchar(1024) NOT NULL DEFAULT '' COMMENT '加密密钥',
    `publish_type` varchar(50) DEFAULT 'formal' COMMENT '发布类型（formal/gray）',
    `gray_name` varchar(50) DEFAULT NULL COMMENT '灰度名称',
    `ext_info` longtext DEFAULT NULL COMMENT '扩展信息',
    PRIMARY KEY (`nid`),
    KEY `idx_gmt_create` (`gmt_create`),
    KEY `idx_gmt_modified` (`gmt_modified`),
    KEY `idx_did` (`data_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='配置历史表';

-- ========================================
-- 租户容量信息表
-- ========================================
CREATE TABLE `tenant_capacity` (
    `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tenant_id` varchar(128) NOT NULL DEFAULT '' COMMENT '租户ID',
    `quota` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '配额',
    `usage` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '已使用量',
    `max_size` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '单个配置大小上限（字节）',
    `max_aggr_count` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '聚合子配置最大个数',
    `max_aggr_size` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '单个聚合数据大小上限（字节）',
    `max_history_count` int(10) unsigned NOT NULL DEFAULT '0' COMMENT '最大变更历史数量',
    `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='租户容量信息表';

-- ========================================
-- 租户信息表
-- ========================================
CREATE TABLE `tenant_info` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
    `kp` varchar(128) NOT NULL COMMENT 'kp',
    `tenant_id` varchar(128) default '' COMMENT '租户ID',
    `tenant_name` varchar(128) default '' COMMENT '租户名称',
    `tenant_desc` varchar(256) DEFAULT NULL COMMENT '租户描述',
    `create_source` varchar(32) DEFAULT NULL COMMENT '创建来源',
    `gmt_create` bigint(20) NOT NULL COMMENT '创建时间',
    `gmt_modified` bigint(20) NOT NULL COMMENT '修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_info_kptenantid` (`kp`,`tenant_id`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='租户信息表';

-- ========================================
-- 用户表（Nacos 控制台登录用户）
-- ========================================
CREATE TABLE `users` (
    `username` varchar(50) NOT NULL PRIMARY KEY COMMENT '用户名',
    `password` varchar(500) NOT NULL COMMENT '密码（BCrypt加密）',
    `enabled` boolean NOT NULL COMMENT '是否启用'
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='Nacos用户表';

-- ========================================
-- 角色表
-- ========================================
CREATE TABLE `roles` (
    `username` varchar(50) NOT NULL COMMENT '用户名',
    `role` varchar(50) NOT NULL COMMENT '角色',
    UNIQUE INDEX `idx_user_role` (`username` ASC, `role` ASC) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='Nacos角色表';

-- ========================================
-- 权限表
-- ========================================
CREATE TABLE `permissions` (
    `role` varchar(50) NOT NULL COMMENT '角色',
    `resource` varchar(128) NOT NULL COMMENT '资源',
    `action` varchar(8) NOT NULL COMMENT '操作',
    UNIQUE INDEX `uk_role_permission` (`role`,`resource`,`action`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='Nacos权限表';

-- ========================================
-- 插入默认管理员账号
-- 说明：用户名 nacos，密码 nacos（BCrypt加密后的值）
-- Nacos 3.x 启动时会自动创建，这里显式插入确保万无一失
-- ========================================
INSERT INTO `users` (`username`, `password`, `enabled`) VALUES
('nacos', '$2a$10$EuWPZHzz32dJN7jexM34MOeYirDdFAZm2kuWj7VEOJhhZkDrxfvUu', true);

INSERT INTO `roles` (`username`, `role`) VALUES
('nacos', 'ROLE_ADMIN');
