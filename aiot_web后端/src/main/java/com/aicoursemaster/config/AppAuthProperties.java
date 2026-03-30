package com.aicoursemaster.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.auth")
public class AppAuthProperties {

    /**
     * HS256 密钥，生产环境务必改为足够长的随机串。
     */
    private String jwtSecret = "dev-only-change-me-use-long-random-secret-key-please";

    /**
     * JWT 有效期（秒），默认 7 天。
     */
    private long jwtExpirationSeconds = 604_800L;

    /**
     * 未携带 Bearer 时，是否允许用 X-User-Id 作为当前用户（仅建议开发/联调开启）。
     */
    private boolean allowHeaderUserId = true;

    /**
     * 对外拼接头像等静态访问 URL 时使用；不配置则按请求推导 Host/Port。
     */
    private String publicBaseUrl;
}
