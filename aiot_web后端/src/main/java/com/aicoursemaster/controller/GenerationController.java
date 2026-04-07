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

    @PostMapping("/start")
    public ApiResponse<Map<String, Object>> start(@RequestBody Map<String, Object> body,
                                                  @RequestAttribute("userId") Long userId) {
        String sessionId = (String) body.get("sessionId");
        String finalRequirements = (String) body.get("finalRequirements");
        Number tId = (Number) body.get("templateId");
        Integer templateId = tId == null ? null : tId.intValue();
        return generationService.startGeneration(sessionId, finalRequirements, templateId, userId);
    }

    @PostMapping("/status")
    public ApiResponse<Map<String, Object>> status(@RequestBody Map<String, Object> body,
                                                   @RequestAttribute("userId") Long userId) {
        String sessionId = (String) body.get("sessionId");
        return generationService.queryStatus(sessionId, userId);
    }

    @PostMapping("/result")
    public ApiResponse<Map<String, Object>> result(@RequestBody Map<String, Object> body,
                                                   @RequestAttribute("userId") Long userId) {
        String sessionId = (String) body.get("sessionId");
        return generationService.getResult(sessionId, userId);
    }

    @PostMapping("/outline/save")
    public ApiResponse<Map<String, Object>> saveOutline(@RequestBody Map<String, Object> body,
                                                        @RequestAttribute("userId") Long userId) {
        String sessionId = (String) body.get("sessionId");
        String outline = (String) body.get("outline");
        String reason = (String) body.get("reason");
        return generationService.saveOutline(sessionId, outline, reason, userId);
    }

    @PostMapping("/cancel")
    public ApiResponse<Map<String, Object>> cancel(@RequestBody Map<String, Object> body,
                                                   @RequestAttribute("userId") Long userId) {
        String sessionId = (String) body.get("sessionId");
        return generationService.cancelGeneration(sessionId, userId);
    }

    /**
     * 生成任务版本历史列表（PPT/大纲/要求快照等）。
     */
    @PostMapping("/version/list")
    public ApiResponse<Map<String, Object>> versionList(@RequestBody Map<String, Object> body,
                                                       @RequestAttribute("userId") Long userId) {
        String sessionId = (String) body.get("sessionId");
        String taskId = (String) body.get("taskId");
        return generationService.listGenerationVersions(sessionId, taskId, userId);
    }

    /**
     * 指定版本详情。
     */
    @PostMapping("/version/detail")
    public ApiResponse<Map<String, Object>> versionDetail(@RequestBody Map<String, Object> body,
                                                         @RequestAttribute("userId") Long userId) {
        String sessionId = (String) body.get("sessionId");
        String taskId = (String) body.get("taskId");
        Number vn = (Number) body.get("versionNo");
        Integer versionNo = vn == null ? null : vn.intValue();
        return generationService.getGenerationVersionDetail(sessionId, taskId, versionNo, userId);
    }
}

