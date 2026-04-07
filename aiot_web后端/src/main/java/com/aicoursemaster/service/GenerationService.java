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

    ApiResponse<Map<String, Object>> cancelGeneration(String sessionId, Long userId);

    /**
     * 生成结果回调：更新任务与会话，并写入版本快照（change_type=generation_callback）。
     */
    ApiResponse<Void> handleGenerationResultCallback(String taskId,
                                                     String status,
                                                     Integer progress,
                                                     String pptUrl,
                                                     String docUrl,
                                                     String gameUrl,
                                                     String outline,
                                                     String errorMsg);

    /**
     * 版本历史列表。taskId 为空则取该会话最新一笔生成任务。
     */
    ApiResponse<Map<String, Object>> listGenerationVersions(String sessionId, String taskId, Long userId);

    /**
     * 版本详情。taskId 为空则取该会话最新一笔生成任务。
     */
    ApiResponse<Map<String, Object>> getGenerationVersionDetail(String sessionId,
                                                                String taskId,
                                                                Integer versionNo,
                                                                Long userId);
}

