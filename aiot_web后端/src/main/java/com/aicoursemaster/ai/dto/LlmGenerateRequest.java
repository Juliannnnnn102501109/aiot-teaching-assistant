package com.aicoursemaster.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LlmGenerateRequest {
    private String prompt;
    private String systemPrompt;
    private String sessionId;
}

