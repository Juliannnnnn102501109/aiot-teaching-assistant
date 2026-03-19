package com.aicoursemaster.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai.rag")
public class AiRagProperties {
    /**
     * Python FastAPI 服务地址，例如 http://localhost:8000
     */
    private String baseUrl = "http://localhost:8000";

    /**
     * RAG 问答接口路径
     */
    private String ragChatPath = "/api/rag/chat";

    /**
     * 通用 LLM 生成接口路径
     */
    private String llmGeneratePath = "/api/llm/generate";

    /**
     * 默认 topK
     */
    private int defaultTopK = 5;
}

