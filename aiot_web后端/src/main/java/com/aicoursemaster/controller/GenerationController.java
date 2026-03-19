package com.aicoursemaster.controller;

import com.aicoursemaster.common.ApiResponse;
import com.aicoursemaster.service.GenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/generation")
@RequiredArgsConstructor
public class GenerationController {

    private final GenerationService generationService;

    private Long currentUserId(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return userId != null ? userId : 1L;
    }

    @PostMapping("/start")
    public ApiResponse<Map<String, Object>> start(@RequestBody Map<String, Object> body,
                                                  @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        String sessionId = (String) body.get("sessionId");
        String finalRequirements = (String) body.get("finalRequirements");
        Number tId = (Number) body.get("templateId");
        Integer templateId = tId == null ? null : tId.intValue();
        return generationService.startGeneration(sessionId, finalRequirements, templateId, currentUserId(userId));
    }

    @PostMapping("/status")
    public ApiResponse<Map<String, Object>> status(@RequestBody Map<String, Object> body,
                                                   @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        String sessionId = (String) body.get("sessionId");
        return generationService.queryStatus(sessionId, currentUserId(userId));
    }

    @PostMapping("/result")
    public ApiResponse<Map<String, Object>> result(@RequestBody Map<String, Object> body,
                                                   @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        String sessionId = (String) body.get("sessionId");
        return generationService.getResult(sessionId, currentUserId(userId));
    }

    @PostMapping("/outline/save")
    public ApiResponse<Map<String, Object>> saveOutline(@RequestBody Map<String, Object> body,
                                                        @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        String sessionId = (String) body.get("sessionId");
        String outline = (String) body.get("outline");
        String reason = (String) body.get("reason");
        return generationService.saveOutline(sessionId, outline, reason, currentUserId(userId));
    }
}

