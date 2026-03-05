package com.aicoursemaster.service.impl;

import com.aicoursemaster.common.ApiResponse;
import com.aicoursemaster.domain.ChatMessage;
import com.aicoursemaster.domain.ChatSession;
import com.aicoursemaster.domain.Material;
import com.aicoursemaster.mapper.ChatMessageMapper;
import com.aicoursemaster.mapper.ChatSessionMapper;
import com.aicoursemaster.mapper.MaterialMapper;
import com.aicoursemaster.mapper.GenerationTaskMapper;
import com.aicoursemaster.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final MaterialMapper materialMapper;
    private final GenerationTaskMapper generationTaskMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public ApiResponse<Map<String, Object>> getSessionDetail(String sessionId, Long userId) {
        ChatSession session = chatSessionMapper.selectByIdAndUserId(sessionId, userId);
        if (session == null) {
            return ApiResponse.error(404, "会话不存在或无权限");
        }
        List<ChatMessage> messages = chatMessageMapper.selectBySessionId(sessionId);
        List<Material> materials = materialMapper.selectBySessionId(sessionId);

        Map<String, Object> sessionInfo = new HashMap<>();
        sessionInfo.put("sessionId", session.getId());
        sessionInfo.put("title", session.getTitle());
        sessionInfo.put("sceneType", session.getSceneType());
        sessionInfo.put("status", session.getStatus());
        sessionInfo.put("createTime", session.getCreateTime());

        List<Map<String, Object>> history = new ArrayList<>();
        for (ChatMessage m : messages) {
            Map<String, Object> item = new HashMap<>();
            item.put("messageId", m.getId());
            item.put("role", m.getRole());
            item.put("content", m.getContent());
            // 简化：附件只返回 id 列表，具体映射前端可结合 materials
            if (StringUtils.hasText(m.getAttachmentIds())) {
                item.put("attachmentIds", m.getAttachmentIds());
            }
            history.add(item);
        }

        List<Map<String, Object>> materialViews = new ArrayList<>();
        for (Material material : materials) {
            Map<String, Object> mv = new HashMap<>();
            mv.put("fileId", material.getId());
            mv.put("fileName", material.getFileName());
            mv.put("type", material.getFileType());
            mv.put("status", material.getParseStatus());
            materialViews.add(mv);
        }

        Map<String, Object> finalResult = null;
        if (Objects.equals(session.getStatus(), 2)) {
            finalResult = new HashMap<>();
            finalResult.put("pptUrl", session.getPptUrl());
            finalResult.put("docUrl", session.getDocUrl());
            finalResult.put("gameUrl", session.getGameUrl());
        }

        Map<String, Object> data = new HashMap<>();
        data.put("sessionInfo", sessionInfo);
        data.put("history", history);
        data.put("materials", materialViews);
        data.put("finalResult", finalResult);
        return ApiResponse.success(data);
    }

    @Override
    public ApiResponse<Map<String, Object>> createSession(Integer sceneType, String firstPrompt, Long userId) {
        if (sceneType == null) {
            return ApiResponse.error(400, "sceneType 必填");
        }
        Long activeCount = chatSessionMapper.countActiveByUserId(userId);
        if (activeCount != null && activeCount > 50) {
            return ApiResponse.error(429, "激活对话数已达上限");
        }
        String sessionId = UUID.randomUUID().toString();
        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setSceneType(sceneType);
        session.setStatus(0);
        session.setTitle("新课件任务");
        session.setCreateTime(LocalDateTime.now());
        chatSessionMapper.insert(session);

        if (StringUtils.hasText(firstPrompt)) {
            ChatMessage message = new ChatMessage();
            message.setSessionId(sessionId);
            message.setRole("user");
            message.setContent(firstPrompt);
            message.setCreateTime(LocalDateTime.now());
            chatMessageMapper.insert(message);
            // 此处也可以复用 sendMessage 逻辑，实现“一键创建并提问”
        }

        Map<String, Object> data = new HashMap<>();
        data.put("sessionId", sessionId);
        data.put("sceneType", sceneType);
        data.put("title", session.getTitle());
        data.put("createTime", session.getCreateTime());
        return ApiResponse.success("新任务已开启", data);
    }

    @Override
    public ApiResponse<Map<String, Object>> sendMessage(String sessionId, String content, List<Long> fileIds,
                                                        Integer sceneType, boolean isResend, Long userId) {
        ChatSession session = chatSessionMapper.selectByIdAndUserId(sessionId, userId);
        if (session == null) {
            return ApiResponse.error(404, "会话不存在或无权限");
        }
        // 保存用户消息
        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(content);
        if (!CollectionUtils.isEmpty(fileIds)) {
            userMsg.setAttachmentIds(fileIds.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]")));
        }
        userMsg.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(userMsg);

        // TODO: 构建 Redis 中的历史对话、场景、附件摘要，调用 AI 服务
        // 为了尽快打通链路，这里先用一个模拟的回复
        ChatMessage aiMsg = new ChatMessage();
        aiMsg.setSessionId(sessionId);
        aiMsg.setRole("assistant");
        aiMsg.setContent("这是模拟的 AI 回复，用于打通接口。");
        aiMsg.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(aiMsg);

        Map<String, Object> tokenUsage = new HashMap<>();
        tokenUsage.put("prompt", 0);
        tokenUsage.put("completion", 0);
        tokenUsage.put("total", 0);

        Map<String, Object> data = new HashMap<>();
        data.put("messageId", aiMsg.getId());
        data.put("reply", aiMsg.getContent());
        data.put("suggestions", Arrays.asList("是的，重点讲", "简单带过", "换个例题"));
        data.put("tokenUsage", tokenUsage);
        data.put("autoTitle", session.getTitle());
        data.put("status", "waiting");
        return ApiResponse.success(data);
    }

    @Override
    public ApiResponse<Map<String, Object>> stopGeneration(String sessionId, Long userId) {
        ChatSession session = chatSessionMapper.selectByIdAndUserId(sessionId, userId);
        if (session == null) {
            return ApiResponse.error(404, "会话不存在或无权限");
        }
        String key = "stop_signal:" + sessionId;
        stringRedisTemplate.opsForValue().set(key, "true");
        stringRedisTemplate.expire(key, java.time.Duration.ofMinutes(1));

        Map<String, Object> data = new HashMap<>();
        data.put("sessionId", sessionId);
        data.put("stopAt", LocalDateTime.now().toString());
        return ApiResponse.success("已下发停止指令", data);
    }

    @Override
    public ApiResponse<Map<String, Object>> feedback(Long messageId, Integer score, String reason, Long userId) {
        ChatMessage msg = chatMessageMapper.selectById(messageId);
        if (msg == null) {
            return ApiResponse.error(404, "消息不存在");
        }
        ChatSession session = chatSessionMapper.selectById(msg.getSessionId());
        if (session == null || !Objects.equals(session.getUserId(), userId)) {
            return ApiResponse.error(403, "无权评价该消息");
        }
        chatMessageMapper.updateFeedback(messageId, score, reason);

        Map<String, Object> data = new HashMap<>();
        data.put("messageId", messageId);
        data.put("currentScore", score);
        return ApiResponse.success("感谢您的反馈，我们会持续改进", data);
    }

    @Override
    public ApiResponse<Map<String, Object>> clearHistory(String sessionId, Long userId) {
        ChatSession session = chatSessionMapper.selectByIdAndUserId(sessionId, userId);
        if (session == null) {
            return ApiResponse.error(404, "会话不存在或无权限");
        }
        // 清理 Redis
        String key = "chat_history:" + sessionId;
        stringRedisTemplate.delete(key);
        // 删除消息
        chatMessageMapper.deleteBySessionId(sessionId);
        // 重置状态
        session.setStatus(0);
        chatSessionMapper.updateTitleAndStatus(session);

        List<Material> materials = materialMapper.selectBySessionId(sessionId);
        Map<String, Object> data = new HashMap<>();
        data.put("sessionId", sessionId);
        data.put("clearedAt", LocalDateTime.now().toString());
        data.put("remainingMaterialsCount", materials.size());
        return ApiResponse.success("对话历史已清空，您可以重新开始提问", data);
    }

    @Override
    public ApiResponse<Map<String, Object>> deleteSession(String sessionId, Long userId) {
        ChatSession session = chatSessionMapper.selectByIdAndUserId(sessionId, userId);
        if (session == null) {
            return ApiResponse.error(404, "会话不存在或无权限");
        }
        List<Material> materials = materialMapper.selectBySessionId(sessionId);
        for (Material material : materials) {
            File f = new File(material.getFilePath());
            if (f.exists()) {
                // 忽略删除失败
                // noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
        chatMessageMapper.deleteBySessionId(sessionId);
        materialMapper.deleteBySessionId(sessionId);
        chatSessionMapper.deleteByIdAndUserId(sessionId, userId);
        stringRedisTemplate.delete("chat_history:" + sessionId);

        Map<String, Object> data = new HashMap<>();
        data.put("sessionId", sessionId);
        data.put("deletedAt", LocalDateTime.now().toString());
        return ApiResponse.success("对话及关联文件已彻底删除", data);
    }

    @Override
    public ApiResponse<Map<String, Object>> batchDeleteMessages(String sessionId, List<Long> messageIds, Long userId) {
        if (CollectionUtils.isEmpty(messageIds)) {
            return ApiResponse.error(400, "messageIds 不能为空");
        }
        ChatSession session = chatSessionMapper.selectByIdAndUserId(sessionId, userId);
        if (session == null) {
            return ApiResponse.error(404, "会话不存在或无权限");
        }
        Long cnt = chatMessageMapper.countByIdsAndSession(messageIds, sessionId);
        if (cnt == null || cnt != messageIds.size()) {
            return ApiResponse.error(403, "存在不属于该会话的消息，拒绝删除");
        }
        chatMessageMapper.deleteByIds(messageIds);

        // 重建 Redis 对话缓存
        String key = "chat_history:" + sessionId;
        stringRedisTemplate.delete(key);
        List<ChatMessage> latest = chatMessageMapper.selectLatestBySessionId(sessionId, 10);
        if (!latest.isEmpty()) {
            List<String> historyJson = latest.stream()
                    .sorted(Comparator.comparing(ChatMessage::getCreateTime))
                    .map(ChatMessage::getContent)
                    .collect(Collectors.toList());
            stringRedisTemplate.opsForList().rightPushAll(key, historyJson);
            stringRedisTemplate.expire(key, java.time.Duration.ofHours(2));
        }

        List<ChatMessage> remain = chatMessageMapper.selectBySessionId(sessionId);
        Map<String, Object> data = new HashMap<>();
        data.put("sessionId", sessionId);
        data.put("deletedCount", messageIds.size());
        data.put("remainingCount", remain.size());
        return ApiResponse.success("选定消息已删除，对话上下文已重构", data);
    }

    @Override
    public ApiResponse<Map<String, Object>> uploadMaterial(MultipartFile file, String sessionId, String fileType, Long userId) {
        ChatSession session = chatSessionMapper.selectByIdAndUserId(sessionId, userId);
        if (session == null) {
            return ApiResponse.error(404, "会话不存在或无权限");
        }
        if (file == null || file.isEmpty()) {
            return ApiResponse.error(400, "文件不能为空");
        }
        String originalName = Objects.requireNonNull(file.getOriginalFilename());
        String suffix = "";
        int idx = originalName.lastIndexOf('.');
        if (idx != -1) {
            suffix = originalName.substring(idx);
        }

        String baseDir = "uploads" + File.separator + sessionId;
        File dir = new File(baseDir);
        if (!dir.exists()) {
            // noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        String newName = UUID.randomUUID() + suffix;
        File dest = new File(dir, newName);
        try {
            // 使用 Path 重载，避免 Servlet 容器忽略绝对路径导致写到临时目录失败
            file.transferTo(dest.toPath());
        } catch (IOException e) {
            log.error("保存上传文件失败, sessionId={}, originalName={}, destPath={}, error={}",
                    sessionId, originalName, dest.getAbsolutePath(), e.getMessage(), e);
            return ApiResponse.error(500, "文件保存失败");
        }

        Material material = new Material();
        material.setSessionId(sessionId);
        material.setFileName(originalName);
        material.setFilePath(dest.getAbsolutePath());
        material.setFileType(StringUtils.hasText(fileType) ? fileType : suffix.replace(".", ""));
        material.setParseStatus(0);
        material.setCreateTime(LocalDateTime.now());
        material.setUpdateTime(LocalDateTime.now());
        materialMapper.insert(material);

        Map<String, Object> data = new HashMap<>();
        data.put("fileId", material.getId());
        data.put("fileName", material.getFileName());
        data.put("status", "parsing");
        data.put("previewUrl", "/api/files/preview/" + material.getId());
        return ApiResponse.success("文件上传成功，正在后台解析中", data);
    }

    @Override
    public ApiResponse<Map<String, Object>> queryMaterialStatus(Long fileId, Long userId) {
        Material material = materialMapper.selectById(fileId);
        if (material == null) {
            return ApiResponse.error(404, "文件不存在");
        }
        ChatSession session = chatSessionMapper.selectById(material.getSessionId());
        if (session == null || !Objects.equals(session.getUserId(), userId)) {
            return ApiResponse.error(403, "无权查看该文件状态");
        }
        Map<String, Object> result = new HashMap<>();
        String statusStr = switch (material.getParseStatus()) {
            case 2 -> "completed";
            case -1 -> "failed";
            default -> "parsing";
        };
        result.put("fileId", material.getId());
        result.put("status", statusStr);
        result.put("progress", Objects.equals(statusStr, "completed") ? 100 : 50);

        Map<String, Object> inner = new HashMap<>();
        inner.put("summary", material.getSummary());
        inner.put("keywords", material.getKeywords());
        inner.put("previewUrl", "/api/files/preview/" + material.getId());
        result.put("result", inner);

        Map<String, Object> data = new HashMap<>();
        data.put("fileId", material.getId());
        data.put("status", statusStr);
        data.put("progress", result.get("progress"));
        data.put("result", inner);
        return ApiResponse.success(data);
    }

    @Override
    public ApiResponse<Map<String, Object>> previewMaterial(Long fileId, Long userId) {
        Material material = materialMapper.selectById(fileId);
        if (material == null) {
            return ApiResponse.error(404, "文件不存在");
        }
        ChatSession session = chatSessionMapper.selectById(material.getSessionId());
        if (session == null || !Objects.equals(session.getUserId(), userId)) {
            return ApiResponse.error(403, "无权访问该文件");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("fileId", material.getId());
        data.put("fileType", material.getFileType());
        data.put("url", "/api/v1/stream/file/" + material.getId());
        data.put("canDirectPreview", true);
        return ApiResponse.success("获取预览链接成功", data);
    }
}

