package com.aicoursemaster.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GenerationLog {
    private Long id;
    private String taskId;
    private String message;
    private LocalDateTime createTime;
}

