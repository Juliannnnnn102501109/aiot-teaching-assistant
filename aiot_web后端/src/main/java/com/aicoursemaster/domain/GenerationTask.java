package com.aicoursemaster.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GenerationTask {
    private Long id;
    private String taskId;
    private String sessionId;
    private String finalRequirements;
    private Integer templateId;
    private String materialIds;
    private String status;
    private Integer progress;
    private String pptUrl;
    private String docUrl;
    private String gameUrl;
    private String outline;
    private String failReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

