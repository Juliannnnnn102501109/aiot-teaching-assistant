-- ----------------------------
-- AICourseMaster 数据库表结构 (MySQL)
-- 约定：
--   1. 生成完成后将 ppt_url/doc_url/game_url 同步写回 chat_session（方案 A）
--   2. generation_task.task_id 为 "task-" + 自增 id，由触发器自动填充
--   3. material 不冗余 user_id，权限通过 session 归属当前 user 校验
-- ----------------------------

-- ----------------------------
-- 1. 用户表（认证模块后置实现，表先建好）
-- ----------------------------
CREATE TABLE `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` VARCHAR(64) NOT NULL COMMENT '用户名',
  `password` VARCHAR(128) NOT NULL COMMENT 'BCrypt 加密后的密码',
  `role` VARCHAR(32) NOT NULL DEFAULT 'teacher' COMMENT '角色：teacher/admin',
  `avatar` VARCHAR(512) DEFAULT NULL COMMENT '头像 URL',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `last_login_at` DATETIME DEFAULT NULL COMMENT '最后登录时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ----------------------------
-- 2. 对话会话表
-- 生成完成后将 ppt_url/doc_url/game_url 同步写回本表（方案 A）
-- ----------------------------
CREATE TABLE `chat_session` (
  `id` VARCHAR(36) NOT NULL COMMENT '会话ID（UUID），对外暴露用',
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
  `scene_type` TINYINT NOT NULL COMMENT '教学场景：1室内 2室外 3理论 4实践 5大学 6中学',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0沟通中 1生成中 2已完成',
  `title` VARCHAR(128) NOT NULL DEFAULT '新课件任务' COMMENT '对话标题（首轮后可异步更新）',
  `ppt_url` VARCHAR(512) DEFAULT NULL COMMENT '最终 PPT 文件 URL',
  `doc_url` VARCHAR(512) DEFAULT NULL COMMENT '最终 Word 教案 URL',
  `game_url` VARCHAR(512) DEFAULT NULL COMMENT '互动小游戏/动画 URL（H5 等）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_status` (`user_id`, `status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话会话表';

-- ----------------------------
-- 3. 聊天消息表
-- ----------------------------
CREATE TABLE `chat_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `session_id` VARCHAR(36) NOT NULL COMMENT '所属会话ID',
  `role` VARCHAR(16) NOT NULL COMMENT '角色：user / assistant',
  `content` TEXT NOT NULL COMMENT '消息内容',
  `attachment_ids` JSON DEFAULT NULL COMMENT '关联的附件ID数组，如 [5001, 5002]',
  `feedback_score` TINYINT DEFAULT NULL COMMENT '反馈：1赞 -1踩 0取消评价 NULL未评价',
  `feedback_reason` VARCHAR(512) DEFAULT NULL COMMENT '踩时的原因/建议',
  `prompt_tokens` INT DEFAULT NULL COMMENT '本条消息请求消耗的 Token 数（assistant 条存上一轮 user+本条）',
  `completion_tokens` INT DEFAULT NULL COMMENT '本条回复消耗的 Token 数（仅 assistant）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_session_create` (`session_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表';

-- ----------------------------
-- 4. 参考资料/附件表
-- 权限通过 session 归属当前 user 校验，不冗余 user_id
-- ----------------------------
CREATE TABLE `material` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '文件ID',
  `session_id` VARCHAR(36) NOT NULL COMMENT '所属会话ID',
  `file_name` VARCHAR(256) NOT NULL COMMENT '原始文件名',
  `file_path` VARCHAR(512) NOT NULL COMMENT '存储路径（如 uploads/{sessionId}/xxx.pdf）',
  `file_type` VARCHAR(32) NOT NULL COMMENT '文件类型：pdf/doc/docx/png/jpg/mp4 等',
  `parse_status` TINYINT NOT NULL DEFAULT 0 COMMENT '解析状态：0等待 1解析中 2成功 -1失败',
  `summary` TEXT DEFAULT NULL COMMENT '解析后的摘要（AI 回调写入）',
  `keywords` JSON DEFAULT NULL COMMENT '解析后的关键词数组',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_parse_status` (`parse_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='参考资料表';

-- ----------------------------
-- 5. 生成任务表（确认意图触发生成的一笔任务）
-- task_id 由触发器设为 "task-" + 自增 id
-- ----------------------------
CREATE TABLE `generation_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务自增ID',
  `task_id` VARCHAR(64) DEFAULT NULL COMMENT '业务任务ID："task-" + id，由触发器填充',
  `session_id` VARCHAR(36) NOT NULL COMMENT '所属会话ID',
  `final_requirements` TEXT DEFAULT NULL COMMENT '用户最终确认的需求描述',
  `template_id` INT DEFAULT NULL COMMENT '模板ID（若有）',
  `material_ids` JSON DEFAULT NULL COMMENT '引用的素材ID列表，如 [5001, 5002]',
  `status` VARCHAR(32) NOT NULL DEFAULT 'processing' COMMENT 'processing / success / failed',
  `progress` TINYINT DEFAULT 0 COMMENT '进度 0-100',
  `ppt_url` VARCHAR(512) DEFAULT NULL COMMENT '生成结果 PPT URL',
  `doc_url` VARCHAR(512) DEFAULT NULL COMMENT '生成结果 Word URL',
  `game_url` VARCHAR(512) DEFAULT NULL COMMENT '生成结果 互动/游戏 URL',
  `outline` TEXT DEFAULT NULL COMMENT '大纲或教学要素摘要',
  `fail_reason` VARCHAR(512) DEFAULT NULL COMMENT '失败原因',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_id` (`task_id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生成任务表';

DELIMITER ;;
CREATE TRIGGER `tr_generation_task_task_id`
AFTER INSERT ON `generation_task`
FOR EACH ROW
BEGIN
  UPDATE `generation_task` SET `task_id` = CONCAT('task-', NEW.`id`) WHERE `id` = NEW.`id`;
END;;
DELIMITER ;

-- ----------------------------
-- 6. 生成过程日志表（轮询时返回 logs）
-- ----------------------------
CREATE TABLE `generation_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `task_id` VARCHAR(64) NOT NULL COMMENT '关联 generation_task.task_id',
  `message` VARCHAR(512) NOT NULL COMMENT '当前步骤描述',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_task_create` (`task_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生成过程日志表';

-- ----------------------------
-- 说明：JWT 黑名单、stop_signal、chat_history 使用 Redis 存储，无需建表
-- ----------------------------
