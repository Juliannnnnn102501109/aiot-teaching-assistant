package com.aicoursemaster.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CoursewareGenerateResponse {
    private String taskId;
    private String status;
    private Integer progress;
    private String outline;
    private String pptUrl;
    private String docUrl;
    private String gameUrl;
    private String model_used;

    /** Python 返回：是否处于 Mock（未连上真实 Ollama 等） */
    @JsonProperty("llm_mock")
    private Boolean llmMock;

    /** Python 返回：是否已成功解析 LLM 结构化 JSON 并写入文件 */
    @JsonProperty("ai_structured")
    private Boolean aiStructured;
}
