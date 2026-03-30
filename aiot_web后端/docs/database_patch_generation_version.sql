-- ----------------------------
-- 生成任务版本历史（快照）
-- 执行：在目标库中运行本脚本一次
-- ----------------------------

CREATE TABLE IF NOT EXISTS `generation_task_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` VARCHAR(64) NOT NULL COMMENT '关联 generation_task.task_id',
  `session_id` VARCHAR(36) NOT NULL COMMENT '所属会话ID（便于按会话查询）',
  `version_no` INT NOT NULL COMMENT '版本号，同一 task_id 内从 1 递增',
  `ppt_url` VARCHAR(512) DEFAULT NULL COMMENT '该版本 PPT URL',
  `doc_url` VARCHAR(512) DEFAULT NULL COMMENT '该版本 Word URL',
  `game_url` VARCHAR(512) DEFAULT NULL COMMENT '该版本互动/游戏 URL',
  `outline` TEXT DEFAULT NULL COMMENT '该版本大纲',
  `outline_change_reason` VARCHAR(512) DEFAULT NULL COMMENT '大纲修改原因（仅 outline_save 时有值）',
  `requirements_snapshot` TEXT DEFAULT NULL COMMENT '该版本「要求」快照（对应 final_requirements）',
  `change_type` VARCHAR(32) NOT NULL COMMENT 'initial / outline_save / generation_callback',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '快照时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_version` (`task_id`, `version_no`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生成任务版本快照';
