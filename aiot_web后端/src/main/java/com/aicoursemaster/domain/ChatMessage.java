package com.aicoursemaster.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMessage {
    private Long id;
    private String sessionId;
    private String role;
    private String content;
    /**
     * JSON 数组字符串，如 [5001,5002]
     */
    private String attachmentIds;
    private Integer feedbackScore;
    private String feedbackReason;
    private Integer promptTokens;
    private Integer completionTokens;
    private LocalDateTime createTime;
}

