package com.aicoursemaster.dto.auth;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(min = 3, max = 64, message = "用户名长度为 3～64")
    private String username;

    @Size(max = 512, message = "头像 URL 过长")
    private String avatar;
}
