package com.aicoursemaster.service.impl;

import com.aicoursemaster.ai.RagApiClient;
import com.aicoursemaster.ai.dto.LlmGenerateResponse;
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
    private final RagApiClient ragApiClient;

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

        GenerationLog submitLog = new GenerationLog();
        submitLog.setTaskId(task.getTaskId());
        submitLog.setMessage("任务已提交，系统开始构建大纲...");
        submitLog.setCreateTime(LocalDateTime.now());
        generationLogMapper.insert(submitLog);

        // 调用通用生成接口，先产出一个可预览的大纲（失败时降级为占位文案，不中断主流程）
        String outlinePrompt = String.format(
                "请为以下教学需求生成一份结构化课程大纲（按章节列点，中文输出）：\n%s",
                finalRequirements == null ? "" : finalRequirements
        );
        try {
            LlmGenerateResponse llmResp = ragApiClient.llmGenerate(
                    outlinePrompt,
                    "你是课程设计助手，请只输出清晰的大纲正文，不要输出额外解释。",
                    sessionId
            );
            if (llmResp != null && llmResp.getAnswer() != null) {
                task.setOutline(llmResp.getAnswer());
                task.setProgress(20);
                task.setUpdateTime(LocalDateTime.now());
                generationTaskMapper.updateByTaskId(task);

                GenerationLog outlineLog = new GenerationLog();
                outlineLog.setTaskId(task.getTaskId());
                outlineLog.setMessage("已生成初版课程大纲，可继续触发后续生成流程。");
                outlineLog.setCreateTime(LocalDateTime.now());
                generationLogMapper.insert(outlineLog);
            }
        } catch (Exception e) {
            log.warn("调用 LLM 生成大纲失败，taskId={}, error={}", task.getTaskId(), e.getMessage());
        }

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

    @Override
    public ApiResponse<Map<String, Object>> saveOutline(String sessionId, String outline, String reason, Long userId) {
        ChatSession session = chatSessionMapper.selectByIdAndUserId(sessionId, userId);
        if (session == null) {
            return ApiResponse.error(404, "会话不存在或无权限");
        }
        GenerationTask latest = generationTaskMapper.selectLatestBySessionId(sessionId);
        if (latest == null) {
            return ApiResponse.error(404, "暂无可修改的生成任务");
        }
        latest.setOutline(outline);
        latest.setUpdateTime(LocalDateTime.now());
        if (reason != null && !reason.isBlank()) {
            String mergedReq = (latest.getFinalRequirements() == null ? "" : latest.getFinalRequirements())
                    + "\n【用户大纲修改原因】" + reason;
            latest.setFinalRequirements(mergedReq);
        }
        generationTaskMapper.updateByTaskId(latest);

        GenerationLog editLog = new GenerationLog();
        editLog.setTaskId(latest.getTaskId());
        editLog.setMessage("用户已修改大纲并保存。原因：" + (reason == null ? "未填写" : reason));
        editLog.setCreateTime(LocalDateTime.now());
        generationLogMapper.insert(editLog);

        Map<String, Object> data = new HashMap<>();
        data.put("taskId", latest.getTaskId());
        data.put("outline", latest.getOutline());
        data.put("saved", true);
        return ApiResponse.success("大纲修改已保存", data);
    }
}

