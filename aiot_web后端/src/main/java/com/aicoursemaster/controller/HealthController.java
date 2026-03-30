package com.aicoursemaster.controller;

import com.aicoursemaster.common.ApiResponse;
import com.aicoursemaster.service.HealthCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class HealthController {

    private final HealthCheckService healthCheckService;

    /**
     * 聚合健康检查：后端、数据库、Redis、AI 服务
     */
    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.success(healthCheckService.aggregate());
    }
}
