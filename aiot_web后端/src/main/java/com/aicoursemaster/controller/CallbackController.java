package com.aicoursemaster.controller;

import com.aicoursemaster.common.ApiResponse;
import com.aicoursemaster.domain.ChatSession;
import com.aicoursemaster.domain.GenerationTask;
import com.aicoursemaster.mapper.ChatSessionMapper;
import com.aicoursemaster.mapper.GenerationTaskMapper;
import com.aicoursemaster.mapper.MaterialMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/callback")
@RequiredArgsConstructor
public class CallbackController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final MaterialMapper materialMapper;
    private final GenerationTaskMapper generationTaskMapper;
    private final ChatSessionMapper chatSessionMapper;

    @PostMapping("/material/parsed")
    public ApiResponse<Void> materialParsed(@RequestBody Map<String, Object> body) {
        Number fileIdNum = (Number) body.get("fileId");
        Long fileId = fileIdNum == null ? null : fileIdNum.longValue();
        Number statusNum = (Number) body.get("status");
        Integer status = statusNum == null ? 0 : statusNum.intValue();
        String summary = (String) body.get("summary");
        Object keywordsObj = body.get("keywords");
        String keywords = null;
        if (keywordsObj != null) {
            if (keywordsObj instanceof String s) {
                keywords = s;
            } else {
                try {
                    // 将数组 / 集合对象序列化成合法 JSON，写入 JSON 列
                    keywords = OBJECT_MAPPER.writeValueAsString(keywordsObj);
                } catch (JsonProcessingException e) {
                    // 序列化失败时，先置空，避免写入非法 JSON
                    keywords = null;
                }
            }
        }
        String errorMsg = (String) body.get("errorMsg");
        materialMapper.updateParseResult(fileId, status, summary, keywords, errorMsg);
        return ApiResponse.success(null);
    }

    @PostMapping("/generation/result")
    public ApiResponse<Void> generationResult(@RequestBody Map<String, Object> body) {
        String taskId = (String) body.get("taskId");
        GenerationTask task = generationTaskMapper.selectByTaskId(taskId);
        if (task == null) {
            return ApiResponse.error(404, "任务不存在");
        }
        String status = (String) body.get("status");
        Number progressNum = (Number) body.get("progress");
        Integer progress = progressNum == null ? null : progressNum.intValue();
        String pptUrl = (String) body.get("pptUrl");
        String docUrl = (String) body.get("docUrl");
        String gameUrl = (String) body.get("gameUrl");
        String outline = (String) body.get("outline");
        String errorMsg = (String) body.get("errorMsg");

        task.setStatus(status);
        task.setProgress(progress);
        task.setPptUrl(pptUrl);
        task.setDocUrl(docUrl);
        task.setGameUrl(gameUrl);
        task.setOutline(outline);
        task.setFailReason(errorMsg);
        generationTaskMapper.updateByTaskId(task);

        // 同步回 chat_session（方案 A）
        ChatSession session = chatSessionMapper.selectById(task.getSessionId());
        if (session != null) {
            session.setPptUrl(pptUrl);
            session.setDocUrl(docUrl);
            session.setGameUrl(gameUrl);
            session.setStatus("success".equals(status) ? 2 : 0);
            chatSessionMapper.updateResultAndStatus(session);
        }
        return ApiResponse.success(null);
    }
}

