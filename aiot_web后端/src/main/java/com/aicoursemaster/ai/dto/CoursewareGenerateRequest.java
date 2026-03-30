package com.aicoursemaster.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CoursewareGenerateRequest {
    private String sessionId;
    private String finalRequirements;
    private String outline;
    private Integer templateId;
}
