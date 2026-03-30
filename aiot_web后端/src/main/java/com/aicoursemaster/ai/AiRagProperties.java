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
     * 课件生成接口路径（产出 PPT/DOCX/互动页面）
     */
    private String coursewareGeneratePath = "/api/courseware/generate";

    /**
     * AI 服务健康检查路径（GET）
     */
    private String healthPath = "/health";

    /**
     * 默认 topK
     */
    private int defaultTopK = 5;

    /**
     * 调用 Python 服务的连接超时（秒）
     */
    private int connectTimeoutSeconds = 10;

    /**
     * 调用 Python 服务的读取超时（秒）
     */
    private int readTimeoutSeconds = 300;
}

