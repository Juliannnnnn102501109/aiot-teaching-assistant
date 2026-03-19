package com.aicoursemaster.controller;

import com.aicoursemaster.common.ApiResponse;
import com.aicoursemaster.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    // 为简化演示，这里暂时从 Header 中读取 userId，后续接入 JWT 后替换
    private Long currentUserId(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return userId != null ? userId : 1L;
    }

    @PostMapping("/chat/session/detail")
    public ApiResponse<Map<String, Object>> getSessionDetail(@RequestBody Map<String, Object> body,
                                                             @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        String sessionId = (String) body.get("sessionId");
        return chatService.getSessionDetail(sessionId, currentUserId(userId));
    }

    @PostMapping("/chat/session/create")
    public ApiResponse<Map<String, Object>> createSession(@RequestBody Map<String, Object> body,
                                                          @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        Integer sceneType = (Integer) body.get("sceneType");
        String firstPrompt = (String) body.get("firstPrompt");
        return chatService.createSession(sceneType, firstPrompt, currentUserId(userId));
    }

    @PostMapping("/chat/session/list")
    public ApiResponse<Map<String, Object>> listSessions(@RequestBody(required = false) Map<String, Object> body,
                                                         @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        String keyword = body == null ? null : (String) body.get("keyword");
        return chatService.listSessions(keyword, currentUserId(userId));
    }

    @PostMapping("/chat/session/pin")
    public ApiResponse<Map<String, Object>> pinSession(@RequestBody Map<String, Object> body,
                                                       @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        String sessionId = (String) body.get("sessionId");
        Boolean pin = (Boolean) body.getOrDefault("pin", Boolean.TRUE);
        return chatService.togglePinSession(sessionId, Boolean.TRUE.equals(pin), currentUserId(userId));
    }

    @PostMapping("/chat/message/send")
    public ApiResponse<Map<String, Object>> sendMessage(@RequestBody Map<String, Object> body,
                                                        @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        String sessionId = (String) body.get("sessionId");
        String content = (String) body.get("content");
        @SuppressWarnings("unchecked")
        List<Integer> fileIdsRaw = (List<Integer>) body.get("fileIds");
        List<Long> fileIds = fileIdsRaw == null ? null : fileIdsRaw.stream().map(Integer::longValue).toList();
        Integer sceneType = (Integer) body.get("sceneType");
        Boolean isResend = (Boolean) body.getOrDefault("isResend", Boolean.FALSE);
        return chatService.sendMessage(sessionId, content, fileIds, sceneType, Boolean.TRUE.equals(isResend), currentUserId(userId));
    }

    @PostMapping("/chat/message/stop")
    public ApiResponse<Map<String, Object>> stop(@RequestBody Map<String, Object> body,
                                                 @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        String sessionId = (String) body.get("sessionId");
        return chatService.stopGeneration(sessionId, currentUserId(userId));
    }

    @PostMapping("/chat/message/feedback")
    public ApiResponse<Map<String, Object>> feedback(@RequestBody Map<String, Object> body,
                                                     @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        Number messageIdNum = (Number) body.get("messageId");
        Long messageId = messageIdNum == null ? null : messageIdNum.longValue();
        Number scoreNum = (Number) body.get("score");
        Integer score = scoreNum == null ? null : scoreNum.intValue();
        String reason = (String) body.get("reason");
        return chatService.feedback(messageId, score, reason, currentUserId(userId));
    }

    @PostMapping("/chat/session/clear")
    public ApiResponse<Map<String, Object>> clear(@RequestBody Map<String, Object> body,
                                                  @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        String sessionId = (String) body.get("sessionId");
        return chatService.clearHistory(sessionId, currentUserId(userId));
    }

    @PostMapping("/chat/session/delete")
    public ApiResponse<Map<String, Object>> deleteSession(@RequestBody Map<String, Object> body,
                                                          @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        String sessionId = (String) body.get("sessionId");
        return chatService.deleteSession(sessionId, currentUserId(userId));
    }

    @PostMapping("/chat/message/batchDelete")
    public ApiResponse<Map<String, Object>> batchDelete(@RequestBody Map<String, Object> body,
                                                        @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        String sessionId = (String) body.get("sessionId");
        @SuppressWarnings("unchecked")
        List<Integer> idsRaw = (List<Integer>) body.get("messageIds");
        List<Long> ids = idsRaw == null ? null : idsRaw.stream().map(Integer::longValue).toList();
        return chatService.batchDeleteMessages(sessionId, ids, currentUserId(userId));
    }

    @PostMapping("/material/upload")
    public ApiResponse<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                                   @RequestParam("sessionId") String sessionId,
                                                   @RequestParam(value = "fileType", required = false) String fileType,
                                                   @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return chatService.uploadMaterial(file, sessionId, fileType, currentUserId(userId));
    }

    @PostMapping("/material/status")
    public ApiResponse<Map<String, Object>> materialStatus(@RequestBody Map<String, Object> body,
                                                           @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        Number fileIdNum = (Number) body.get("fileId");
        Long fileId = fileIdNum == null ? null : fileIdNum.longValue();
        return chatService.queryMaterialStatus(fileId, currentUserId(userId));
    }
}

