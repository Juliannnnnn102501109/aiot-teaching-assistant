package com.aicoursemaster.service;

import com.aicoursemaster.common.ApiResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface ChatService {

    ApiResponse<Map<String, Object>> getSessionDetail(String sessionId, Long userId);

    ApiResponse<Map<String, Object>> createSession(Integer sceneType, String firstPrompt, Long userId);

    ApiResponse<Map<String, Object>> listSessions(String keyword, Integer page, Integer size, Long userId);

    ApiResponse<Map<String, Object>> togglePinSession(String sessionId, boolean pin, Long userId);

    ApiResponse<Map<String, Object>> reorderPinnedSessions(List<String> orderedSessionIds, Long userId);

    ApiResponse<Map<String, Object>> sendMessage(String sessionId,
                                                 String content,
                                                 List<Long> fileIds,
                                                 Integer sceneType,
                                                 boolean isResend,
                                                 Long userId);

    ApiResponse<Map<String, Object>> stopGeneration(String sessionId, Long userId);

    ApiResponse<Map<String, Object>> feedback(Long messageId, Integer score, String reason, Long userId);

    ApiResponse<Map<String, Object>> clearHistory(String sessionId, Long userId);

    ApiResponse<Map<String, Object>> deleteSession(String sessionId, Long userId);

    ApiResponse<Map<String, Object>> batchDeleteMessages(String sessionId, List<Long> messageIds, Long userId);

    ApiResponse<Map<String, Object>> uploadMaterial(MultipartFile file, String sessionId, String fileType, Long userId);

    ApiResponse<Map<String, Object>> queryMaterialStatus(Long fileId, Long userId);

    ApiResponse<Map<String, Object>> deleteMaterial(Long fileId, String sessionId, Long userId);

    ApiResponse<Map<String, Object>> previewMaterial(Long fileId, Long userId);

    ApiResponse<Map<String, Object>> renameSession(String sessionId, String title, Long userId);

    ApiResponse<Map<String, Object>> renameMaterial(Long fileId, String fileName, Long userId);

    ApiResponse<Map<String, Object>> editUserMessageAndRegenerate(String sessionId,
                                                                    Long messageId,
                                                                    String newContent,
                                                                    List<Long> fileIds,
                                                                    Long userId);
}

