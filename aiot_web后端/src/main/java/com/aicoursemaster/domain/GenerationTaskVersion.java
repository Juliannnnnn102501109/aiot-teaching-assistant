package com.aicoursemaster.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GenerationTaskVersion {
    private Long id;
    private String taskId;
    private String sessionId;
    private Integer versionNo;
    private String pptUrl;
    private String docUrl;
    private String gameUrl;
    private String outline;
    private String outlineChangeReason;
    private String requirementsSnapshot;
    private String changeType;
    private LocalDateTime createTime;
}
