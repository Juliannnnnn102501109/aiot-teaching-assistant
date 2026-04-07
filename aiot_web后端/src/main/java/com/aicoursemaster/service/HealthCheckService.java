package com.aicoursemaster.service;

import com.aicoursemaster.ai.RagApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class HealthCheckService {

    private final DataSource dataSource;
    private final StringRedisTemplate stringRedisTemplate;
    private final RagApiClient ragApiClient;

    public Map<String, Object> aggregate() {
        Map<String, Object> root = new LinkedHashMap<>();

        Map<String, Object> backend = new LinkedHashMap<>();
        backend.put("status", "UP");
        root.put("backend", backend);

        Map<String, Object> db = new LinkedHashMap<>();
        boolean dbOk = checkDatabase();
        db.put("status", dbOk ? "UP" : "DOWN");
        root.put("database", db);

        Map<String, Object> redis = new LinkedHashMap<>();
        boolean redisOk = checkRedis();
        redis.put("status", redisOk ? "UP" : "DOWN");
        root.put("redis", redis);

        Map<String, Object> ai = new LinkedHashMap<>();
        Map<String, Object> aiBody = ragApiClient.aiHealth();
        boolean aiOk = aiBody != null && isAiHealthy(aiBody);
        ai.put("status", aiOk ? "UP" : "DOWN");
        if (aiBody != null) {
            ai.put("detail", aiBody);
        }
        root.put("ai", ai);

        boolean allUp = dbOk && redisOk && aiOk;
        root.put("status", allUp ? "UP" : "DEGRADED");
        return root;
    }

    private boolean checkDatabase() {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            st.execute("SELECT 1");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkRedis() {
        try {
            stringRedisTemplate.opsForValue().get("__health_check_key__");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isAiHealthy(Map<String, Object> body) {
        Object status = body.get("status");
        if (status != null && "healthy".equalsIgnoreCase(String.valueOf(status))) {
            return true;
        }
        Object rag = body.get("rag_ready");
        Object llm = body.get("llm_ready");
        if (Boolean.TRUE.equals(rag) && Boolean.TRUE.equals(llm)) {
            return true;
        }
        return body.values().stream().anyMatch(v -> Objects.equals(v, Boolean.TRUE));
    }
}
