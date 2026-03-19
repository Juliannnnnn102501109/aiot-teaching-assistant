package com.aicoursemaster.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatSession {
    private String id;
    private Long userId;
    private Integer sceneType;
    /**
     * 0:沟通中 1:生成中 2:已完成
     */
    private Integer status;
    private String title;
    private String pptUrl;
    private String docUrl;
    private String gameUrl;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

