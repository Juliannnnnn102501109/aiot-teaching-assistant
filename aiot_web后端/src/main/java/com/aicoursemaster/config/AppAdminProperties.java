package com.aicoursemaster.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.admin")
public class AppAdminProperties {
    /**
     * 管理端接口 Header：X-Admin-Token 需与此一致
     */
    private String token = "dev-admin-token";
}
