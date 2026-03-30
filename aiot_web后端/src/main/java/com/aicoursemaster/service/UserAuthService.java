package com.aicoursemaster.service;

import com.aicoursemaster.auth.JwtService;
import com.aicoursemaster.auth.TokenBlacklistService;
import com.aicoursemaster.common.ApiResponse;
import com.aicoursemaster.config.AppAuthProperties;
import com.aicoursemaster.domain.User;
import com.aicoursemaster.dto.auth.ChangePasswordRequest;
import com.aicoursemaster.dto.auth.LoginRequest;
import com.aicoursemaster.dto.auth.UpdateProfileRequest;
import com.aicoursemaster.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAuthService {

    private static final Pattern AVATAR_FILE_PATTERN = Pattern.compile(
            "^[0-9a-fA-F\\-]{36}\\.(jpg|jpeg|png|gif|webp)$");

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final AppAuthProperties authProperties;

    @Transactional
    public ApiResponse<Map<String, Object>> register(LoginRequest req) {
        User existing = userMapper.selectByUsername(req.getUsername().trim());
        if (existing != null) {
            return ApiResponse.error(400, "该用户名已被占用，请换一个试试");
        }
        User user = new User();
        user.setUsername(req.getUsername().trim());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setRole("teacher");
        user.setAvatar(null);
        LocalDateTime now = LocalDateTime.now();
        user.setCreateTime(now);
        user.setUpdateTime(now);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            return ApiResponse.error(400, "该用户名已被占用，请换一个试试");
        }
        String token = jwtService.createToken(user.getId(), user.getUsername());
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("expiresIn", authProperties.getJwtExpirationSeconds());
        return ApiResponse.success("注册成功", data);
    }

    @Transactional
    public ApiResponse<Map<String, Object>> login(LoginRequest req) {
        User user = userMapper.selectByUsername(req.getUsername().trim());
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            return ApiResponse.error(401, "用户名或密码错误");
        }
        userMapper.updateLastLoginAt(user.getId());
        user = userMapper.selectById(user.getId());
        String token = jwtService.createToken(user.getId(), user.getUsername());

        Map<String, Object> u = userToBrief(user);
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", u);
        data.put("expiresIn", authProperties.getJwtExpirationSeconds());
        return ApiResponse.success("登录成功", data);
    }

    @Transactional
    public ApiResponse<Void> changePassword(Long userId, ChangePasswordRequest req, String currentToken) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return ApiResponse.error(404, "用户不存在");
        }
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            return ApiResponse.error(400, "原密码输入错误");
        }
        userMapper.updatePassword(userId, passwordEncoder.encode(req.getNewPassword()));
        if (StringUtils.hasText(currentToken)) {
            tokenBlacklistService.blacklistUntilNaturalExpiry(currentToken, jwtService);
        }
        return ApiResponse.success("密码修改成功，请重新登录", null);
    }

    public ApiResponse<Map<String, Object>> me(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return ApiResponse.error(404, "用户不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("role", user.getRole());
        data.put("avatar", user.getAvatar());
        data.put("createTime", user.getCreateTime());
        return ApiResponse.success("获取用户信息成功", data);
    }

    public ApiResponse<Void> logout(String token) {
        if (StringUtils.hasText(token)) {
            tokenBlacklistService.blacklistUntilNaturalExpiry(token, jwtService);
        }
        return ApiResponse.success("退出登录成功", null);
    }

    @Transactional
    public ApiResponse<Map<String, Object>> updateProfile(Long userId, UpdateProfileRequest req) {
        User current = userMapper.selectById(userId);
        if (current == null) {
            return ApiResponse.error(404, "用户不存在");
        }
        if (StringUtils.hasText(req.getUsername())) {
            String name = req.getUsername().trim();
            User other = userMapper.selectByUsernameExcludeId(name, userId);
            if (other != null) {
                return ApiResponse.error(400, "该用户名已被占用，请换一个试试");
            }
        }
        User patch = new User();
        patch.setId(userId);
        if (StringUtils.hasText(req.getUsername())) {
            patch.setUsername(req.getUsername().trim());
        }
        if (req.getAvatar() != null) {
            patch.setAvatar(req.getAvatar().trim().isEmpty() ? null : req.getAvatar().trim());
        }
        if (patch.getUsername() == null && req.getAvatar() == null) {
            return ApiResponse.error(400, "没有需要更新的字段");
        }
        userMapper.updateProfileSelective(patch);
        User fresh = userMapper.selectById(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("id", fresh.getId());
        data.put("username", fresh.getUsername());
        data.put("avatar", fresh.getAvatar());
        return ApiResponse.success("个人信息修改成功", data);
    }

    /**
     * 头像文件落盘到 uploads/avatars/，返回可供前端写入「修改个人信息」的 URL（相对站点根路径）。
     */
    public ApiResponse<Map<String, Object>> uploadAvatar(Long userId, MultipartFile file, HttpServletRequest request) {
        if (file == null || file.isEmpty()) {
            return ApiResponse.error(400, "文件不能为空");
        }
        log.debug("upload avatar userId={}", userId);
        String original = Objects.requireNonNullElse(file.getOriginalFilename(), "");
        String suffix = "";
        int idx = original.lastIndexOf('.');
        if (idx != -1) {
            suffix = original.substring(idx + 1).toLowerCase();
        }
        if (!suffix.matches("jpg|jpeg|png|gif|webp")) {
            return ApiResponse.error(400, "仅支持 jpg、jpeg、png、gif、webp 图片");
        }
        String stored = UUID.randomUUID() + "." + suffix;
        File dir = new File("uploads" + File.separator + "avatars");
        if (!dir.exists()) {
            // noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        File dest = new File(dir, stored);
        try {
            file.transferTo(dest.toPath());
        } catch (IOException e) {
            return ApiResponse.error(500, "头像保存失败");
        }
        String path = "/api/v1/files/avatar/" + stored;
        String base = resolvePublicBase(request);
        Map<String, Object> data = new HashMap<>();
        data.put("url", base + path);
        data.put("path", path);
        return ApiResponse.success("头像上传成功", data);
    }

    public boolean isAllowedAvatarFileName(String fileName) {
        return fileName != null && AVATAR_FILE_PATTERN.matcher(fileName).matches();
    }

    public File resolveAvatarFile(String fileName) {
        if (!isAllowedAvatarFileName(fileName)) {
            return null;
        }
        try {
            Path base = Path.of("uploads", "avatars").toAbsolutePath().normalize();
            Path target = base.resolve(fileName).normalize();
            if (!target.startsWith(base)) {
                return null;
            }
            File f = target.toFile();
            return f.isFile() ? f : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String resolvePublicBase(HttpServletRequest request) {
        String configured = authProperties.getPublicBaseUrl();
        if (StringUtils.hasText(configured)) {
            return configured.replaceAll("/$", "");
        }
        String scheme = request.getScheme();
        int port = request.getServerPort();
        String host = request.getServerName();
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        if (defaultPort) {
            return scheme + "://" + host;
        }
        return scheme + "://" + host + ":" + port;
    }

    private static Map<String, Object> userToBrief(User user) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", user.getId());
        m.put("username", user.getUsername());
        m.put("role", user.getRole());
        m.put("lastLoginAt", user.getLastLoginAt());
        return m;
    }
}
