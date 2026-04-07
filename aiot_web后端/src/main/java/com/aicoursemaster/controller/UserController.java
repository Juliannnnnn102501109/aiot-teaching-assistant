package com.aicoursemaster.controller;

import com.aicoursemaster.common.ApiResponse;
import com.aicoursemaster.dto.auth.ChangePasswordRequest;
import com.aicoursemaster.dto.auth.LoginRequest;
import com.aicoursemaster.dto.auth.UpdateProfileRequest;
import com.aicoursemaster.service.UserAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserAuthService userAuthService;

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody LoginRequest req) {
        return userAuthService.register(req);
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest req) {
        return userAuthService.login(req);
    }

    @PostMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req,
                                            @RequestAttribute("userId") Long userId,
                                            HttpServletRequest request) {
        return userAuthService.changePassword(userId, req, extractBearer(request));
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(@RequestAttribute("userId") Long userId) {
        return userAuthService.me(userId);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        return userAuthService.logout(extractBearer(request));
    }

    @PostMapping("/profile")
    public ApiResponse<Map<String, Object>> profile(@Valid @RequestBody UpdateProfileRequest req,
                                                    @RequestAttribute("userId") Long userId) {
        return userAuthService.updateProfile(userId, req);
    }

    /**
     * 上传头像文件，返回 URL 后再调「修改个人信息」写入 avatar 字段（也可在前端直接链式调用）。
     */
    @PostMapping("/avatar")
    public ApiResponse<Map<String, Object>> uploadAvatar(@RequestParam("file") MultipartFile file,
                                                         @RequestAttribute("userId") Long userId,
                                                         HttpServletRequest request) {
        return userAuthService.uploadAvatar(userId, file, request);
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
}
