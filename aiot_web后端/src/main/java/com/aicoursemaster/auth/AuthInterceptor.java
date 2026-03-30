package com.aicoursemaster.auth;

import com.aicoursemaster.common.ApiResponse;
import com.aicoursemaster.config.AppAuthProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final AppAuthProperties authProperties;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (isWhitelisted(request)) {
            return true;
        }

        String bearer = extractBearer(request);
        if (bearer != null && !bearer.isBlank()) {
            if (tokenBlacklistService.isBlacklisted(bearer)) {
                return writeUnauthorized(response, "登录已失效，请重新登录");
            }
            try {
                Claims claims = jwtService.parseClaims(bearer);
                long userId = Long.parseLong(claims.getSubject());
                request.setAttribute("userId", userId);
                return true;
            } catch (JwtException | IllegalArgumentException e) {
                return writeUnauthorized(response, "登录已失效或 Token 无效");
            }
        }

        if (authProperties.isAllowHeaderUserId()) {
            String header = request.getHeader("X-User-Id");
            if (header != null && !header.isBlank()) {
                try {
                    request.setAttribute("userId", Long.parseLong(header.trim()));
                    return true;
                } catch (NumberFormatException ignored) {
                    return writeUnauthorized(response, "X-User-Id 格式错误");
                }
            }
        }

        return writeUnauthorized(response, "未登录或缺少凭证");
    }

    private boolean isWhitelisted(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = normalizedPath(request);
        if ("/api/v1/health".equals(path)) {
            return true;
        }
        if (path.startsWith("/api/callback")) {
            return true;
        }
        if (path.startsWith("/api/v1/admin")) {
            return true;
        }
        if (path.startsWith("/api/v1/files/avatar")) {
            return true;
        }
        return "/api/v1/user/register".equals(path) || "/api/v1/user/login".equals(path);
    }

    private static String normalizedPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
            uri = uri.substring(ctx.length());
        }
        if (uri.isEmpty()) {
            return "/";
        }
        return uri;
    }

    private static String extractBearer(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null) {
            return null;
        }
        auth = auth.trim();
        if (auth.length() > 7 && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return auth.substring(7).trim();
        }
        return null;
    }

    private boolean writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> body = ApiResponse.error(401, message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
        return false;
    }
}
