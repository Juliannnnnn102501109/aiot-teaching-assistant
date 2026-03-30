package com.aicoursemaster.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Material {
    private Long id;
    private String sessionId;
    private String fileName;
    private String filePath;
    private String fileType;
    /**
     * 0:等待 1:解析中 2:成功 -1:失败
     */
    private Integer parseStatus;
    private String summary;
    private String keywords;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

