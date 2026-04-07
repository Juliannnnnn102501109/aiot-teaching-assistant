package com.aicoursemaster.controller;

import com.aicoursemaster.common.ApiResponse;
import com.aicoursemaster.config.AppAdminProperties;
import com.aicoursemaster.domain.SensitiveWord;
import com.aicoursemaster.mapper.ChatMessageMapper;
import com.aicoursemaster.mapper.ChatSessionMapper;
import com.aicoursemaster.mapper.GenerationTaskMapper;
import com.aicoursemaster.mapper.MaterialMapper;
import com.aicoursemaster.service.SensitiveWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AppAdminProperties adminProperties;
    private final SensitiveWordService sensitiveWordService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final MaterialMapper materialMapper;
    private final GenerationTaskMapper generationTaskMapper;

    private boolean tokenOk(String token) {
        return StringUtils.hasText(token) && token.equals(adminProperties.getToken());
    }

    private ApiResponse<Map<String, Object>> reject() {
        return ApiResponse.error(403, "管理端 Token 无效或未携带 X-Admin-Token");
    }

    @PostMapping("/sensitive-word/list")
    public ApiResponse<Map<String, Object>> listSensitiveWords(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (!tokenOk(token)) {
            return reject();
        }
        List<SensitiveWord> rows = sensitiveWordService.listAllRows();
        Map<String, Object> data = new HashMap<>();
        data.put("items", rows);
        data.put("total", rows.size());
        return ApiResponse.success(data);
    }

    @PostMapping("/sensitive-word/add")
    public ApiResponse<Map<String, Object>> addSensitiveWord(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                                             @RequestBody Map<String, Object> body) {
        if (!tokenOk(token)) {
            return reject();
        }
        String word = (String) body.get("word");
        Number en = (Number) body.get("enabled");
        int enabled = en == null ? 1 : en.intValue();
        try {
            sensitiveWordService.addWord(word, enabled);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
        Map<String, Object> data = new HashMap<>();
        data.put("saved", true);
        return ApiResponse.success("敏感词已添加", data);
    }

    @PostMapping("/sensitive-word/update")
    public ApiResponse<Map<String, Object>> updateSensitiveWord(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                                               @RequestBody Map<String, Object> body) {
        if (!tokenOk(token)) {
            return reject();
        }
        Number idNum = (Number) body.get("id");
        Long id = idNum == null ? null : idNum.longValue();
        String word = (String) body.get("word");
        Number en = (Number) body.get("enabled");
        int enabled = en == null ? 1 : en.intValue();
        try {
            sensitiveWordService.updateWord(id, word, enabled);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
        Map<String, Object> data = new HashMap<>();
        data.put("saved", true);
        return ApiResponse.success("敏感词已更新", data);
    }

    @PostMapping("/sensitive-word/delete")
    public ApiResponse<Map<String, Object>> deleteSensitiveWord(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                                                @RequestBody Map<String, Object> body) {
        if (!tokenOk(token)) {
            return reject();
        }
        Number idNum = (Number) body.get("id");
        Long id = idNum == null ? null : idNum.longValue();
        try {
            sensitiveWordService.deleteWord(id);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
        Map<String, Object> data = new HashMap<>();
        data.put("deleted", true);
        return ApiResponse.success("敏感词已删除", data);
    }

    /**
     * 按前缀或通配清理 Redis 键（默认仅清理业务前缀，慎用全库 pattern=*）
     */
    @PostMapping("/redis/clear")
    public ApiResponse<Map<String, Object>> clearRedis(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                                       @RequestBody Map<String, Object> body) {
        if (!tokenOk(token)) {
            return reject();
        }
        String rawPattern = body == null ? "" : Objects.toString(body.get("pattern"), "");
        String pattern = StringUtils.hasText(rawPattern) ? rawPattern.trim() : "chat_history:*";
        if ("*".equals(pattern)) {
            return ApiResponse.error(400, "禁止直接使用 pattern=*，请指定更具体的前缀");
        }
        Set<String> keys = stringRedisTemplate.keys(pattern);
        long deleted = 0;
        if (!CollectionUtils.isEmpty(keys)) {
            deleted = stringRedisTemplate.delete(keys);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("pattern", pattern);
        data.put("deletedCount", deleted);
        return ApiResponse.success("Redis 清理完成", data);
    }

    @PostMapping("/metrics/overview")
    public ApiResponse<Map<String, Object>> metrics(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (!tokenOk(token)) {
            return reject();
        }
        Map<String, Object> data = new HashMap<>();
        data.put("chatSessions", chatSessionMapper.countAll());
        data.put("chatMessages", chatMessageMapper.countAll());
        data.put("materials", materialMapper.countAll());
        data.put("generationTasks", generationTaskMapper.countAll());
        data.put("sensitiveWords", sensitiveWordService.countWords());
        try {
            Long dbSize = stringRedisTemplate.execute((RedisCallback<Long>) connection -> connection.serverCommands().dbSize());
            data.put("redisApproxKeyCount", dbSize);
        } catch (Exception e) {
            data.put("redisApproxKeyCount", null);
        }
        return ApiResponse.success(data);
    }
}
