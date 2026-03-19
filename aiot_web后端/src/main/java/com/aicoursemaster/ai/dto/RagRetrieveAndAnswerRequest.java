package com.aicoursemaster.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagRetrieveAndAnswerRequest {
    private String question;
    private Integer topK;
    private String sessionId;
    private Boolean useCoT;
}

