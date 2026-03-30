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

    @PostMapping("/chat/session/detail")
    public ApiResponse<Map<String, Object>> getSessionDetail(@RequestBody Map<String, Object> body,
                                                             @RequestAttribute("userId") Long userId) {
        String sessionId = (String) body.get("sessionId");
        return chatService.getSessionDetail(sessionId, userId);
    }

    @PostMapping("/chat/session/create")
    public ApiResponse<Map<String, Object>> createSession(@RequestBody Map<String, Object> body,
                                                          @RequestAttribute("userId") Long userId) {
        Integer sceneType = (Integer) body.get("sceneType");
        String firstPrompt = (String) body.get("firstPrompt");
        return chatService.createSession(sceneType, firstPrompt, userId);
    }

    @PostMapping("/chat/session/list")
    public ApiResponse<Map<String, Object>> listSessions(@RequestBody(required = false) Map<String, Object> body,
                                                         @RequestAttribute("userId") Long userId) {
        String keyword = body == null ? null : (String) body.get("keyword");
        Number pageNum = body == null ? null : (Number) body.get("page");
        Number sizeNum = body == null ? null : (Number) body.get("size");
        Integer page = pageNum == null ? null : pageNum.intValue();
        Integer size = sizeNum == null ? null : sizeNum.intValue();
        return chatService.listSessions(keyword, page, size, userId);
    }

    @PostMapping("/chat/session/pin")
    public ApiResponse<Map<String, Object>> pinSession(@RequestBody Map<String, Object> body,
                                                       @RequestAttribute("userId") Long userId) {
        String sessionId = (String) body.get("sessionId");
        Boolean pin = (Boolean) body.getOrDefault("pin", Boolean.TRUE);
        return chatService.togglePinSession(sessionId, Boolean.TRUE.equals(pin), userId);
    }

    @PostMapping("/chat/session/pin/reorder")
    public ApiResponse<Map<String, Object>> reorderPinnedSessions(@RequestBody Map<String, Object> body,
                                                                  @RequestAttribute("userId") Long userId) {
        @SuppressWarnings("unchecked")
        List<String> orderedSessionIds = (List<String>) body.get("orderedSessionIds");
        return chatService.reorderPinnedSessions(orderedSessionIds, userId);
    }

    @PostMapping("/chat/message/send")
    public ApiResponse<Map<String, Object>> sendMessage(@RequestBody Map<String, Object> body,
                                                        @RequestAttribute("userId") Long userId) {
        String sessionId = (String) body.get("sessionId");
        String content = (String) body.get("content");
        @SuppressWarnings("unchecked")
        List<Integer> fileIdsRaw = (List<Integer>) body.get("fileIds");
        List<Long> fileIds = fileIdsRaw == null ? null : fileIdsRaw.stream().map(Integer::longValue).toList();
        Integer sceneType = (Integer) body.get("sceneType");
        Boolean isResend = (Boolean) body.getOrDefault("isResend", Boolean.FALSE);
        return chatService.sendMessage(sessionId, content, fileIds, sceneType, Boolean.TRUE.equals(isResend), userId);
    }

    @PostMapping("/chat/message/stop")
    public ApiResponse<Map<String, Object>> stop(@RequestBody Map<String, Object> body,
                                                 @RequestAttribute("userId") Long userId) {
        String sessionId = (String) body.get("sessionId");
        return chatService.stopGeneration(sessionId, userId);
    }

    @PostMapping("/chat/message/feedback")
    public ApiResponse<Map<String, Object>> feedback(@RequestBody Map<String, Object> body,
                                                     @RequestAttribute("userId") Long userId) {
        Number messageIdNum = (Number) body.get("messageId");
        Long messageId = messageIdNum == null ? null : messageIdNum.longValue();
        Number scoreNum = (Number) body.get("score");
        Integer score = scoreNum == null ? null : scoreNum.intValue();
        String reason = (String) body.get("reason");
        return chatService.feedback(messageId, score, reason, userId);
    }

    @PostMapping("/chat/session/clear")
    public ApiResponse<Map<String, Object>> clear(@RequestBody Map<String, Object> body,
                                                  @RequestAttribute("userId") Long userId) {
        String sessionId = (String) body.get("sessionId");
        return chatService.clearHistory(sessionId, userId);
    }

    @PostMapping("/chat/session/delete")
    public ApiResponse<Map<String, Object>> deleteSession(@RequestBody Map<String, Object> body,
                                                          @RequestAttribute("userId") Long userId) {
        String sessionId = (String) body.get("sessionId");
        return chatService.deleteSession(sessionId, userId);
    }

    @PostMapping("/chat/message/batchDelete")
    public ApiResponse<Map<String, Object>> batchDelete(@RequestBody Map<String, Object> body,
                                                        @RequestAttribute("userId") Long userId) {
        String sessionId = (String) body.get("sessionId");
        @SuppressWarnings("unchecked")
        List<Integer> idsRaw = (List<Integer>) body.get("messageIds");
        List<Long> ids = idsRaw == null ? null : idsRaw.stream().map(Integer::longValue).toList();
        return chatService.batchDeleteMessages(sessionId, ids, userId);
    }

    @PostMapping("/chat/session/rename")
    public ApiResponse<Map<String, Object>> renameSession(@RequestBody Map<String, Object> body,
                                                           @RequestAttribute("userId") Long userId) {
        String sessionId = (String) body.get("sessionId");
        String title = (String) body.get("title");
        return chatService.renameSession(sessionId, title, userId);
    }

    @PostMapping("/material/rename")
    public ApiResponse<Map<String, Object>> renameMaterial(@RequestBody Map<String, Object> body,
                                                           @RequestAttribute("userId") Long userId) {
        Number fileIdNum = (Number) body.get("fileId");
        Long fileId = fileIdNum == null ? null : fileIdNum.longValue();
        String fileName = (String) body.get("fileName");
        return chatService.renameMaterial(fileId, fileName, userId);
    }

    /**
     * 编辑用户消息并删除其后的消息，重新生成助手回复。fileIds 不传则保留原消息附件；传 [] 可清空附件。
     */
    @PostMapping("/chat/message/editRegenerate")
    public ApiResponse<Map<String, Object>> editRegenerate(@RequestBody Map<String, Object> body,
                                                           @RequestAttribute("userId") Long userId) {
        String sessionId = (String) body.get("sessionId");
        Number messageIdNum = (Number) body.get("messageId");
        Long messageId = messageIdNum == null ? null : messageIdNum.longValue();
        String newContent = (String) body.get("newContent");
        @SuppressWarnings("unchecked")
        List<Integer> fileIdsRaw = (List<Integer>) body.get("fileIds");
        List<Long> fileIds = fileIdsRaw == null ? null : fileIdsRaw.stream().map(Integer::longValue).toList();
        return chatService.editUserMessageAndRegenerate(sessionId, messageId, newContent, fileIds, userId);
    }

    @PostMapping("/material/upload")
    public ApiResponse<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                                   @RequestParam("sessionId") String sessionId,
                                                   @RequestParam(value = "fileType", required = false) String fileType,
                                                   @RequestAttribute("userId") Long userId) {
        return chatService.uploadMaterial(file, sessionId, fileType, userId);
    }

    @PostMapping("/material/status")
    public ApiResponse<Map<String, Object>> materialStatus(@RequestBody Map<String, Object> body,
                                                           @RequestAttribute("userId") Long userId) {
        Number fileIdNum = (Number) body.get("fileId");
        Long fileId = fileIdNum == null ? null : fileIdNum.longValue();
        return chatService.queryMaterialStatus(fileId, userId);
    }

    /**
     * 删除附件：删库记录、本地文件，并从会话内消息的 attachment_ids 中移除该 fileId。
     */
    @PostMapping("/material/delete")
    public ApiResponse<Map<String, Object>> deleteMaterial(@RequestBody Map<String, Object> body,
                                                           @RequestAttribute("userId") Long userId) {
        Number fileIdNum = (Number) body.get("fileId");
        Long fileId = fileIdNum == null ? null : fileIdNum.longValue();
        String sessionId = (String) body.get("sessionId");
        return chatService.deleteMaterial(fileId, sessionId, userId);
    }
}

