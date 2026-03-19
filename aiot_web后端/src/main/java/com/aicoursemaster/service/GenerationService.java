package com.aicoursemaster.service;

import com.aicoursemaster.common.ApiResponse;

import java.util.Map;

public interface GenerationService {

    ApiResponse<Map<String, Object>> startGeneration(String sessionId,
                                                     String finalRequirements,
                                                     Integer templateId,
                                                     Long userId);

    ApiResponse<Map<String, Object>> queryStatus(String sessionId, Long userId);

    ApiResponse<Map<String, Object>> getResult(String sessionId, Long userId);

    ApiResponse<Map<String, Object>> saveOutline(String sessionId,
                                                 String outline,
                                                 String reason,
                                                 Long userId);
}

