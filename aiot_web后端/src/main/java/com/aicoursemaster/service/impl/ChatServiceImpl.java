package com.aicoursemaster.service.impl;

import com.aicoursemaster.common.ApiResponse;
import com.aicoursemaster.ai.RagApiClient;
import com.aicoursemaster.ai.dto.LlmGenerateResponse;
import com.aicoursemaster.ai.dto.RagRetrieveAndAnswerResponse;
import com.aicoursemaster.domain.ChatMessage;
import com.aicoursemaster.domain.ChatSession;
import com.aicoursemaster.domain.Material;
import com.aicoursemaster.mapper.ChatMessageMapper;
import com.aicoursemaster.mapper.ChatSessionMapper;
import com.aicoursemaster.mapper.MaterialMapper;
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
    private final StringRedisTemplate stringRedisTemplate;
    private final RagApiClient ragApiClient;
    private final MaterialParseAsyncService materialParseAsyncService;

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
    public ApiResponse<Map<String, Object>> listSessions(String keyword, Long userId) {
        List<ChatSession> sessions = chatSessionMapper.selectByUserId(userId);
        Set<String> pinned = Optional.ofNullable(
                stringRedisTemplate.opsForZSet().reverseRange("pinned_sessions:" + userId, 0, -1)
        ).orElse(Collections.emptySet());
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;

        List<Map<String, Object>> items = sessions.stream()
                .filter(s -> matchSessionByKeyword(s, kw))
                .sorted((a, b) -> {
                    boolean aPinned = pinned.contains(a.getId());
                    boolean bPinned = pinned.contains(b.getId());
                    if (aPinned != bPinned) {
                        return aPinned ? -1 : 1;
                    }
                    LocalDateTime at = a.getUpdateTime() != null ? a.getUpdateTime() : a.getCreateTime();
                    LocalDateTime bt = b.getUpdateTime() != null ? b.getUpdateTime() : b.getCreateTime();
                    return bt.compareTo(at);
                })
                .map(s -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("sessionId", s.getId());
                    item.put("title", s.getTitle());
                    item.put("sceneType", s.getSceneType());
                    item.put("status", s.getStatus());
                    item.put("pinned", pinned.contains(s.getId()));
                    item.put("updateTime", s.getUpdateTime() != null ? s.getUpdateTime() : s.getCreateTime());
                    return item;
                })
                .collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("items", items);
        data.put("total", items.size());
        return ApiResponse.success(data);
    }

    @Override
    public ApiResponse<Map<String, Object>> togglePinSession(String sessionId, boolean pin, Long userId) {
        ChatSession session = chatSessionMapper.selectByIdAndUserId(sessionId, userId);
        if (session == null) {
            return ApiResponse.error(404, "会话不存在或无权限");
        }
        String key = "pinned_sessions:" + userId;
        if (pin) {
            stringRedisTemplate.opsForZSet().add(key, sessionId, System.currentTimeMillis());
        } else {
            stringRedisTemplate.opsForZSet().remove(key, sessionId);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("sessionId", sessionId);
        data.put("pinned", pin);
        return ApiResponse.success(pin ? "已置顶对话" : "已取消置顶", data);
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

        String question = StringUtils.hasText(content) ? content : "";
        String replyText;
        try {
            if (!CollectionUtils.isEmpty(fileIds)) {
                String materialsContext = buildMaterialsContext(fileIds, sessionId);
                String chatHistory = buildLatestHistory(sessionId, 8);
                String prompt = "【历史对话】\n" + chatHistory + "\n\n【附件解析结果】\n" + materialsContext
                        + "\n\n【用户问题】\n" + question;
                LlmGenerateResponse llmResp = ragApiClient.llmGenerate(
                        prompt,
                        "你是教学助手，请优先依据附件解析结果回答。若信息不足，请明确说明不足点。",
                        sessionId
                );
                replyText = llmResp == null ? "" : llmResp.getAnswer();
            } else {
                RagRetrieveAndAnswerResponse ragResp = ragApiClient.ragChat(question, sessionId, 5, false);
                replyText = ragResp == null ? "" : ragResp.getAnswer();
            }
        } catch (Exception e) {
            return ApiResponse.error(503, "AI 服务暂不可用，请稍后重试");
        }

        ChatMessage aiMsg = new ChatMessage();
        aiMsg.setSessionId(sessionId);
        aiMsg.setRole("assistant");
        aiMsg.setContent(StringUtils.hasText(replyText) ? replyText : "（AI 未返回内容）");
        aiMsg.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(aiMsg);

        if ("新课件任务".equals(session.getTitle())) {
            autoRenameSession(session, content, aiMsg.getContent());
        }

        Map<String, Object> tokenUsage = new HashMap<>();
        tokenUsage.put("prompt", 0);
        tokenUsage.put("completion", 0);
        tokenUsage.put("total", 0);

        Map<String, Object> data = new HashMap<>();
        data.put("messageId", aiMsg.getId());
        data.put("reply", aiMsg.getContent());
        data.put("suggestions", Arrays.asList("是的，重点讲", "简单带过", "换个例题"));
        data.put("tokenUsage", tokenUsage);
        ChatSession latestSession = chatSessionMapper.selectById(sessionId);
        data.put("autoTitle", latestSession == null ? session.getTitle() : latestSession.getTitle());
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
        materialParseAsyncService.parseMaterialAsync(material);

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

    private String buildLatestHistory(String sessionId, int limit) {
        List<ChatMessage> history = chatMessageMapper.selectLatestBySessionId(sessionId, limit);
        if (CollectionUtils.isEmpty(history)) {
            return "暂无";
        }
        return history.stream()
                .sorted(Comparator.comparing(ChatMessage::getCreateTime))
                .map(m -> m.getRole() + ": " + m.getContent())
                .collect(Collectors.joining("\n"));
    }

    private String buildMaterialsContext(List<Long> fileIds, String sessionId) {
        List<Material> all = materialMapper.selectBySessionId(sessionId);
        Map<Long, Material> indexed = all.stream().collect(Collectors.toMap(Material::getId, m -> m, (a, b) -> a));
        List<String> lines = new ArrayList<>();
        for (Long fileId : fileIds) {
            Material m = indexed.get(fileId);
            if (m == null) {
                continue;
            }
            if (!Objects.equals(m.getParseStatus(), 2)) {
                lines.add("- " + m.getFileName() + "（解析未完成）");
                continue;
            }
            lines.add("- 文件: " + m.getFileName());
            lines.add("  摘要: " + (m.getSummary() == null ? "" : m.getSummary()));
            lines.add("  关键词: " + (m.getKeywords() == null ? "" : m.getKeywords()));
        }
        return lines.isEmpty() ? "无可用附件摘要" : String.join("\n", lines);
    }

    private void autoRenameSession(ChatSession session, String userContent, String aiReply) {
        try {
            String prompt = "请为这轮教学对话生成一个不超过15字的标题。\n用户:" + userContent + "\n助手:" + aiReply;
            LlmGenerateResponse titleResp = ragApiClient.llmGenerate(
                    prompt,
                    "你是标题助手，只输出标题文本，不要引号和解释。",
                    session.getId()
            );
            String title = titleResp == null ? null : titleResp.getAnswer();
            if (StringUtils.hasText(title)) {
                String clean = title == null ? "" : title.replace("\n", "").trim();
                session.setTitle(clean.length() > 20 ? clean.substring(0, 20) : clean);
                session.setStatus(session.getStatus());
                chatSessionMapper.updateTitleAndStatus(session);
            }
        } catch (Exception e) {
            log.warn("自动命名失败 sessionId={}, error={}", session.getId(), e.getMessage());
        }
    }

    private boolean matchSessionByKeyword(ChatSession session, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        if (StringUtils.hasText(session.getTitle()) && session.getTitle().contains(keyword)) {
            return true;
        }
        List<ChatMessage> messages = chatMessageMapper.selectLatestBySessionId(session.getId(), 20);
        return messages.stream().anyMatch(m -> StringUtils.hasText(m.getContent()) && m.getContent().contains(keyword));
    }
}

