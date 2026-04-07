package com.aicoursemaster.ai.dto;

import lombok.Data;

@Data
public class LlmGenerateResponse {
    private String answer;
    private String sessionId;
    private String model_used;
}

