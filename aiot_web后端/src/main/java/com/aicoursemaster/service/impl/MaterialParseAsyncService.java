package com.aicoursemaster.service.impl;

import com.aicoursemaster.ai.RagApiClient;
import com.aicoursemaster.ai.dto.LlmGenerateResponse;
import com.aicoursemaster.domain.Material;
import com.aicoursemaster.mapper.MaterialMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaterialParseAsyncService {

    private final MaterialMapper materialMapper;
    private final RagApiClient ragApiClient;

    @Async
    public void parseMaterialAsync(Material material) {
        try {
            materialMapper.updateParseResult(material.getId(), 1, null, null, null);
            String rawText = extractTextSnippet(material.getFilePath(), material.getFileType(), material.getFileName());
            String prompt = "请从以下教学资料中提取摘要与关键词，并严格按 JSON 输出：\n"
                    + "{\"summary\":\"...\",\"keywords\":[\"...\",\"...\"]}\n\n"
                    + "资料内容：\n" + rawText;
            LlmGenerateResponse llmResp = ragApiClient.llmGenerate(
                    prompt,
                    "你是文档解析助手。必须输出可被JSON解析的对象，不要输出额外说明。",
                    material.getSessionId()
            );
            String answer = llmResp == null ? null : llmResp.getAnswer();
            String summary = null;
            String keywords = null;
            if (StringUtils.hasText(answer)) {
                summary = extractJsonField(answer, "summary");
                keywords = extractKeywords(answer);
            }
            if (!StringUtils.hasText(summary)) {
                summary = "已接收文件《" + material.getFileName() + "》，可用于后续对话参考。";
            }
            if (!StringUtils.hasText(keywords)) {
                keywords = "[\"" + material.getFileType() + "\",\"" + material.getFileName() + "\"]";
            }
            materialMapper.updateParseResult(material.getId(), 2, summary, keywords, null);
        } catch (Exception e) {
            log.error("异步解析文件失败 fileId={}, error={}", material.getId(), e.getMessage(), e);
            materialMapper.updateParseResult(material.getId(), -1, null, null, e.getMessage());
        }
    }

    private String extractTextSnippet(String filePath, String fileType, String fileName) throws IOException {
        String normalizedType = fileType == null ? "" : fileType.toLowerCase();
        if (normalizedType.contains("txt") || normalizedType.contains("md") || normalizedType.contains("csv")
                || normalizedType.contains("json") || normalizedType.contains("log")) {
            String text = Files.readString(Path.of(filePath), StandardCharsets.UTF_8);
            return text.length() > 1500 ? text.substring(0, 1500) : text;
        }
        return "文件名：" + fileName + "；文件类型：" + fileType + "。请基于文件元信息生成可用于教学问答的摘要与关键词。";
    }

    private String extractJsonField(String raw, String field) {
        String marker = "\"" + field + "\"";
        int idx = raw.indexOf(marker);
        if (idx < 0) {
            return null;
        }
        int colon = raw.indexOf(':', idx);
        if (colon < 0) {
            return null;
        }
        int firstQuote = raw.indexOf('"', colon + 1);
        if (firstQuote < 0) {
            return null;
        }
        int secondQuote = raw.indexOf('"', firstQuote + 1);
        if (secondQuote < 0) {
            return null;
        }
        return raw.substring(firstQuote + 1, secondQuote);
    }

    private String extractKeywords(String raw) {
        int idx = raw.indexOf("\"keywords\"");
        if (idx < 0) {
            return null;
        }
        int l = raw.indexOf('[', idx);
        int r = raw.indexOf(']', l + 1);
        if (l < 0 || r < 0 || r <= l) {
            return null;
        }
        return raw.substring(l, r + 1);
    }
}

