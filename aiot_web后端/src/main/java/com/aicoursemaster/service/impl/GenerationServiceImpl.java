package com.aicoursemaster.service.impl;

import com.aicoursemaster.ai.RagApiClient;
import com.aicoursemaster.ai.AiRagProperties;
import com.aicoursemaster.ai.dto.CoursewareGenerateResponse;
import com.aicoursemaster.common.ApiResponse;
import com.aicoursemaster.domain.ChatSession;
import com.aicoursemaster.domain.GenerationLog;
import com.aicoursemaster.domain.GenerationTask;
import com.aicoursemaster.domain.GenerationTaskVersion;
import com.aicoursemaster.mapper.ChatSessionMapper;
import com.aicoursemaster.mapper.GenerationLogMapper;
import com.aicoursemaster.mapper.GenerationTaskMapper;
import com.aicoursemaster.mapper.GenerationTaskVersionMapper;
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

    private static final String CHANGE_INITIAL = "initial";
    private static final String CHANGE_OUTLINE_SAVE = "outline_save";
    private static final String CHANGE_GENERATION_CALLBACK = "generation_callback";

    private final ChatSessionMapper chatSessionMapper;
    private final GenerationTaskMapper generationTaskMapper;
    private final GenerationTaskVersionMapper generationTaskVersionMapper;
    private final GenerationLogMapper generationLogMapper;
    private final RagApiClient ragApiClient;
    private final AiRagProperties aiRagProperties;

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
        task.setStatus("processing");
        task.setProgress(0);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        generationTaskMapper.insert(task);
        task.setTaskId("task-" + task.getId());
        // 兼容/兜底：不依赖 DB trigger，确保 task_id 在表中非空
        generationTaskMapper.updateTaskIdById(task.getId(), task.getTaskId());

        session.setStatus(1);
        chatSessionMapper.updateTitleAndStatus(session);

        GenerationLog submitLog = new GenerationLog();
        submitLog.setTaskId(task.getTaskId());
        submitLog.setMessage("任务已提交，系统开始构建大纲...");
        submitLog.setCreateTime(LocalDateTime.now());
        generationLogMapper.insert(submitLog);

        try {
            GenerationLog generatingLog = new GenerationLog();
            generatingLog.setTaskId(task.getTaskId());
            generatingLog.setMessage("正在调用 AI 生成课件文件（PPT/DOCX/互动页面）...");
            generatingLog.setCreateTime(LocalDateTime.now());
            generationLogMapper.insert(generatingLog);

            CoursewareGenerateResponse generated = ragApiClient.generateCourseware(
                    sessionId,
                    finalRequirements == null ? "" : finalRequirements,
                    null,
                    templateId
            );

            String pptUrl = toAbsoluteUrl(aiRagProperties.getBaseUrl(), generated.getPptUrl());
            String docUrl = toAbsoluteUrl(aiRagProperties.getBaseUrl(), generated.getDocUrl());
            String gameUrl = toAbsoluteUrl(aiRagProperties.getBaseUrl(), generated.getGameUrl());

            task.setOutline(generated.getOutline());
            task.setPptUrl(pptUrl);
            task.setDocUrl(docUrl);
            task.setGameUrl(gameUrl);
            task.setStatus("success");
            task.setProgress(generated.getProgress() == null ? 100 : generated.getProgress());
            task.setUpdateTime(LocalDateTime.now());
            generationTaskMapper.updateByTaskId(task);

            session.setPptUrl(pptUrl);
            session.setDocUrl(docUrl);
            session.setGameUrl(gameUrl);
            session.setStatus(2);
            chatSessionMapper.updateResultAndStatus(session);

            GenerationLog doneLog = new GenerationLog();
            doneLog.setTaskId(task.getTaskId());
            doneLog.setMessage("课件文件生成完成，可在结果接口获取下载地址。");
            doneLog.setCreateTime(LocalDateTime.now());
            generationLogMapper.insert(doneLog);
        } catch (Exception e) {
            log.warn("调用 AI 课件生成失败，taskId={}, error={}", task.getTaskId(), e.getMessage());
            task.setStatus("failed");
            task.setProgress(0);
            task.setFailReason("课件生成失败: " + e.getMessage());
            task.setUpdateTime(LocalDateTime.now());
            generationTaskMapper.updateByTaskId(task);

            session.setStatus(0);
            chatSessionMapper.updateTitleAndStatus(session);

            GenerationLog failLog = new GenerationLog();
            failLog.setTaskId(task.getTaskId());
            failLog.setMessage("课件生成失败：" + e.getMessage());
            failLog.setCreateTime(LocalDateTime.now());
            generationLogMapper.insert(failLog);
        }

        recordGenerationVersion(task, CHANGE_INITIAL, null);

        Map<String, Object> data = new HashMap<>();
        data.put("taskId", task.getTaskId());
        data.put("estimatedTime", "0-30s");
        return ApiResponse.success("生成任务已启动", data);
    }

    private String toAbsoluteUrl(String baseUrl, String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return rawPath;
        }
        if (rawPath.startsWith("http://") || rawPath.startsWith("https://")) {
            return rawPath;
        }
        String b = baseUrl == null ? "" : baseUrl.trim();
        if (b.endsWith("/") && rawPath.startsWith("/")) {
            return b.substring(0, b.length() - 1) + rawPath;
        }
        if (!b.endsWith("/") && !rawPath.startsWith("/")) {
            return b + "/" + rawPath;
        }
        return b + rawPath;
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

        Integer vNo = recordGenerationVersion(latest, CHANGE_OUTLINE_SAVE, reason);

        Map<String, Object> data = new HashMap<>();
        data.put("taskId", latest.getTaskId());
        data.put("outline", latest.getOutline());
        data.put("saved", true);
        if (vNo != null) {
            data.put("versionNo", vNo);
        }
        return ApiResponse.success("大纲修改已保存", data);
    }

    @Override
    public ApiResponse<Map<String, Object>> cancelGeneration(String sessionId, Long userId) {
        ChatSession session = chatSessionMapper.selectByIdAndUserId(sessionId, userId);
        if (session == null) {
            return ApiResponse.error(404, "会话不存在或无权限");
        }
        GenerationTask latest = generationTaskMapper.selectLatestBySessionId(sessionId);
        if (latest == null) {
            return ApiResponse.error(404, "暂无生成任务");
        }
        if (!"processing".equals(latest.getStatus())) {
            return ApiResponse.error(400, "当前任务不可取消");
        }
        latest.setStatus("cancelled");
        latest.setProgress(0);
        latest.setFailReason("用户取消");
        generationTaskMapper.updateByTaskId(latest);

        GenerationLog cancelLog = new GenerationLog();
        cancelLog.setTaskId(latest.getTaskId());
        cancelLog.setMessage("用户已取消生成任务");
        cancelLog.setCreateTime(LocalDateTime.now());
        generationLogMapper.insert(cancelLog);

        session.setStatus(0);
        chatSessionMapper.updateTitleAndStatus(session);

        Map<String, Object> data = new HashMap<>();
        data.put("taskId", latest.getTaskId());
        data.put("status", "cancelled");
        return ApiResponse.success("生成任务已取消", data);
    }

    @Override
    public ApiResponse<Void> handleGenerationResultCallback(String taskId,
                                                            String status,
                                                            Integer progress,
                                                            String pptUrl,
                                                            String docUrl,
                                                            String gameUrl,
                                                            String outline,
                                                            String errorMsg) {
        GenerationTask task = generationTaskMapper.selectByTaskId(taskId);
        if (task == null) {
            return ApiResponse.error(404, "任务不存在");
        }
        task.setStatus(status);
        task.setProgress(progress);
        task.setPptUrl(pptUrl);
        task.setDocUrl(docUrl);
        task.setGameUrl(gameUrl);
        task.setOutline(outline);
        task.setFailReason(errorMsg);
        generationTaskMapper.updateByTaskId(task);

        ChatSession session = chatSessionMapper.selectById(task.getSessionId());
        if (session != null) {
            session.setPptUrl(pptUrl);
            session.setDocUrl(docUrl);
            session.setGameUrl(gameUrl);
            session.setStatus("success".equals(status) ? 2 : 0);
            chatSessionMapper.updateResultAndStatus(session);
        }

        recordGenerationVersion(task, CHANGE_GENERATION_CALLBACK, null);
        return ApiResponse.success(null);
    }

    @Override
    public ApiResponse<Map<String, Object>> listGenerationVersions(String sessionId, String taskId, Long userId) {
        ChatSession session = chatSessionMapper.selectByIdAndUserId(sessionId, userId);
        if (session == null) {
            return ApiResponse.error(404, "会话不存在或无权限");
        }
        GenerationTask task = resolveTaskForSession(sessionId, taskId);
        if (task == null) {
            return ApiResponse.error(404, "暂无生成任务");
        }
        List<GenerationTaskVersion> rows = generationTaskVersionMapper.selectByTaskIdOrderByVersionDesc(task.getTaskId());
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", task.getTaskId());
        data.put("total", rows.size());
        data.put("versions", rows.stream().map(this::toVersionMap).toList());
        return ApiResponse.success(data);
    }

    @Override
    public ApiResponse<Map<String, Object>> getGenerationVersionDetail(String sessionId,
                                                                       String taskId,
                                                                       Integer versionNo,
                                                                       Long userId) {
        ChatSession session = chatSessionMapper.selectByIdAndUserId(sessionId, userId);
        if (session == null) {
            return ApiResponse.error(404, "会话不存在或无权限");
        }
        if (versionNo == null || versionNo < 1) {
            return ApiResponse.error(400, "versionNo 无效");
        }
        GenerationTask task = resolveTaskForSession(sessionId, taskId);
        if (task == null) {
            return ApiResponse.error(404, "暂无生成任务");
        }
        GenerationTaskVersion v = generationTaskVersionMapper.selectByTaskIdAndVersionNo(task.getTaskId(), versionNo);
        if (v == null) {
            return ApiResponse.error(404, "版本不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", task.getTaskId());
        data.put("version", toVersionMap(v));
        return ApiResponse.success(data);
    }

    private GenerationTask resolveTaskForSession(String sessionId, String taskId) {
        if (taskId != null && !taskId.isBlank()) {
            GenerationTask t = generationTaskMapper.selectByTaskId(taskId);
            if (t == null || !sessionId.equals(t.getSessionId())) {
                return null;
            }
            return t;
        }
        GenerationTask t = generationTaskMapper.selectLatestBySessionId(sessionId);
        // 兼容旧数据：generation_task.task_id 可能为 NULL
        if (t != null && (t.getTaskId() == null || t.getTaskId().isBlank()) && t.getId() != null) {
            t.setTaskId("task-" + t.getId());
        }
        return t;
    }

    private Map<String, Object> toVersionMap(GenerationTaskVersion v) {
        Map<String, Object> m = new HashMap<>();
        m.put("versionNo", v.getVersionNo());
        m.put("pptUrl", v.getPptUrl());
        m.put("docUrl", v.getDocUrl());
        m.put("gameUrl", v.getGameUrl());
        m.put("outline", v.getOutline());
        m.put("outlineChangeReason", v.getOutlineChangeReason());
        m.put("requirements", v.getRequirementsSnapshot());
        m.put("changeType", v.getChangeType());
        m.put("createTime", v.getCreateTime());
        return m;
    }

    /**
     * @return 写入的版本号；失败时返回 null（表未建时降级，不影响主流程）
     */
    private Integer recordGenerationVersion(GenerationTask task, String changeType, String outlineChangeReason) {
        if (task == null || task.getTaskId() == null) {
            return null;
        }
        try {
            Integer max = generationTaskVersionMapper.selectMaxVersionNoByTaskId(task.getTaskId());
            int next = (max == null) ? 1 : max + 1;
            GenerationTaskVersion v = new GenerationTaskVersion();
            v.setTaskId(task.getTaskId());
            v.setSessionId(task.getSessionId());
            v.setVersionNo(next);
            v.setPptUrl(task.getPptUrl());
            v.setDocUrl(task.getDocUrl());
            v.setGameUrl(task.getGameUrl());
            v.setOutline(task.getOutline());
            if (CHANGE_OUTLINE_SAVE.equals(changeType)) {
                v.setOutlineChangeReason(outlineChangeReason);
            } else {
                v.setOutlineChangeReason(null);
            }
            v.setRequirementsSnapshot(task.getFinalRequirements());
            v.setChangeType(changeType);
            v.setCreateTime(LocalDateTime.now());
            generationTaskVersionMapper.insert(v);
            return next;
        } catch (Exception e) {
            log.warn("写入生成版本快照失败（请确认已执行 generation_task_version 补丁），taskId={}, error={}",
                    task.getTaskId(), e.getMessage());
            return null;
        }
    }
}

