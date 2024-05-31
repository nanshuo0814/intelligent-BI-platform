# 数据库初始化

-- 创建库
CREATE DATABASE IF NOT EXISTS `intelligent-BI-platform`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- 切换库
USE `intelligent-BI-platform`;

-- 用户表
CREATE TABLE IF NOT EXISTS `user`
(
    `id`            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '用户id',
    `user_account`  VARCHAR(16)                          NOT NULL COMMENT '用户账号',
    `user_password` VARCHAR(256)                         NOT NULL COMMENT '用户密码',
    `user_name`     VARCHAR(256)                         NULL COMMENT '用户昵称',
    `user_gender`   TINYINT    DEFAULT 2                 NULL COMMENT '0-女，1-男，2-未知',
    `user_email`    VARCHAR(255)                         NULL COMMENT '邮箱',
    `user_avatar`   VARCHAR(1024)                        NULL COMMENT '用户头像',
    `user_role`     VARCHAR(5) DEFAULT 'user'            NOT NULL COMMENT '用户角色：user/admin',
    `create_time`   DATETIME   DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    `update_time`   DATETIME   DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_delete`     TINYINT    DEFAULT 0                 NOT NULL COMMENT '是否删除，0:默认，1:删除',
    index idx_user_account (user_account)
) COMMENT '用户表' ENGINE = InnoDB
                 CHARACTER SET utf8mb4
                 COLLATE utf8mb4_unicode_ci;

-- 图表表
create table if not exists chart
(
    id           bigint auto_increment comment 'id' primary key,
    goal				 text  null comment '分析目标',
    `name`               varchar(128) null comment '图表名称',
    chart_data    text  null comment '图表数据',
    chart_type	   varchar(128) null comment '图表类型',
    gen_chart		 text	 null comment '生成的图表数据',
    gen_result		 text	 null comment '生成的分析结论',
    status       varchar(128) not null default 'wait' comment 'wait,running,succeed,failed',
    exec_message  text   null comment '执行信息',
    user_id       bigint null comment '创建用户 id',
    create_time   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    is_delete     tinyint      default 0                 not null comment '是否删除'
) comment '图表信息表' collate = utf8mb4_unicode_ci;
