package com.aicoursemaster.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SensitiveWord {
    private Long id;
    private String word;
    private Integer enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
