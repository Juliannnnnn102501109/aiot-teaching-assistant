package com.aicoursemaster.service.impl;

import com.aicoursemaster.common.ApiResponse;
import com.aicoursemaster.domain.ChatSession;
import com.aicoursemaster.domain.GenerationLog;
import com.aicoursemaster.domain.GenerationTask;
import com.aicoursemaster.mapper.ChatSessionMapper;
import com.aicoursemaster.mapper.GenerationLogMapper;
import com.aicoursemaster.mapper.GenerationTaskMapper;
import com.aicoursemaster.service.GenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class GenerationServiceImpl implements GenerationService {

    private final ChatSessionMapper chatSessionMapper;
    private final GenerationTaskMapper generationTaskMapper;
    private final GenerationLogMapper generationLogMapper;

    @Override
    public ApiResponse<Map<String, Object>> startGeneration(String sessionId, String finalRequirements,
                                                            Integer templateId, Long userId) {
        ChatSession session = chatSessionMapper.selectByIdAndUserId(sessionId, userId);
        if (session == null) {
            return ApiResponse.error(404, "会话不存在或无权限");
        }
        if (Objects.equals(session.getStatus(), 1)) {
            return ApiResponse.error(409, "当前会话已有生成任务进行中");
        }
        GenerationTask task = new GenerationTask();
        task.setSessionId(sessionId);
        task.setFinalRequirements(finalRequirements);
        task.setTemplateId(templateId);
        // 不再依赖数据库触发器，应用侧直接生成 taskId
        task.setTaskId("task-" + java.util.UUID.randomUUID());
        task.setStatus("processing");
        task.setProgress(0);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        generationTaskMapper.insert(task);

        session.setStatus(1);
        chatSessionMapper.updateTitleAndStatus(session);

        GenerationLog log = new GenerationLog();
        log.setTaskId(task.getTaskId());
        log.setMessage("任务已提交，系统开始构建大纲...");
        log.setCreateTime(LocalDateTime.now());
        generationLogMapper.insert(log);

        Map<String, Object> data = new HashMap<>();
        data.put("taskId", task.getTaskId());
        data.put("estimatedTime", "60s");
        return ApiResponse.success("生成任务已启动", data);
    }

    @Override
    public ApiResponse<Map<String, Object>> queryStatus(String sessionId, Long userId) {
        ChatSession session = chatSessionMapper.selectByIdAndUserId(sessionId, userId);
        if (session == null) {
            return ApiResponse.error(404, "会话不存在或无权限");
        }
        GenerationTask latest = generationTaskMapper.selectLatestBySessionId(sessionId);
        if (latest == null) {
            return ApiResponse.error(404, "暂无生成任务");
        }
        List<GenerationLog> logs = generationLogMapper.selectByTaskId(latest.getTaskId());
        Map<String, Object> data = new HashMap<>();
        data.put("status", latest.getStatus());
        data.put("progress", latest.getProgress());
        data.put("currentStep", logs.isEmpty() ? "" : logs.get(logs.size() - 1).getMessage());
        data.put("logs", logs.stream().map(GenerationLog::getMessage).toList());
        return ApiResponse.success(data);
    }

    @Override
    public ApiResponse<Map<String, Object>> getResult(String sessionId, Long userId) {
        ChatSession session = chatSessionMapper.selectByIdAndUserId(sessionId, userId);
        if (session == null) {
            return ApiResponse.error(404, "会话不存在或无权限");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("pptUrl", session.getPptUrl());
        data.put("docUrl", session.getDocUrl());
        data.put("gameUrl", session.getGameUrl());

        GenerationTask latest = generationTaskMapper.selectLatestBySessionId(sessionId);
        if (latest != null) {
            data.put("outline", latest.getOutline());
        } else {
            data.put("outline", null);
        }
        return ApiResponse.success(data);
    }
}

