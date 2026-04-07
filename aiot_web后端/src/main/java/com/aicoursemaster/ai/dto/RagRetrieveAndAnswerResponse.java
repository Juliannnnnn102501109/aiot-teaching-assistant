package com.aicoursemaster.ai.dto;

import lombok.Data;

import java.util.List;

@Data
public class RagRetrieveAndAnswerResponse {
    private String answer;
    private List<RagSourceItem> sources;
    private String sessionId;
    private String model_used;

    @Data
    public static class RagSourceItem {
        private String content;
        private String source;
        private Integer page;
    }
}

