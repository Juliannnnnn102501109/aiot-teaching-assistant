package com.aicoursemaster.service;

import com.aicoursemaster.domain.SensitiveWord;
import com.aicoursemaster.mapper.SensitiveWordMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 敏感词：用户输入命中则拒绝；助手输出命中则整段替换为固定文案（不展示原文）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SensitiveWordService {

    /**
     * 助手回复命中敏感词时返回的固定文案（整段替换，不保留部分原文）。
     */
    public static final String ASSISTANT_SENSITIVE_PLACEHOLDER =
            "根据内容安全策略，该回复无法展示，请调整提问或稍后重试。";

    private final SensitiveWordMapper sensitiveWordMapper;

    private final CopyOnWriteArrayList<String> enabledWords = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void reload() {
        try {
            List<SensitiveWord> rows = sensitiveWordMapper.selectAllEnabled();
            List<String> list = new ArrayList<>();
            for (SensitiveWord row : rows) {
                if (row != null && StringUtils.hasText(row.getWord())) {
                    list.add(row.getWord().trim());
                }
            }
            list.sort((a, b) -> Integer.compare(b.length(), a.length()));
            enabledWords.clear();
            enabledWords.addAll(list);
            log.info("敏感词库已加载，条数={}", enabledWords.size());
        } catch (Exception e) {
            log.warn("敏感词库加载失败（若尚未建表可忽略）: {}", e.getMessage());
            enabledWords.clear();
        }
    }

    public List<String> snapshotEnabledWords() {
        return Collections.unmodifiableList(new ArrayList<>(enabledWords));
    }

    /**
     * @return 命中的敏感词，未命中返回 null
     */
    public String findHitInUserText(String text) {
        if (!StringUtils.hasText(text) || enabledWords.isEmpty()) {
            return null;
        }
        for (String w : enabledWords) {
            if (text.contains(w)) {
                return w;
            }
        }
        return null;
    }

    /**
     * 助手输出：命中任一敏感词则整段替换为 {@link #ASSISTANT_SENSITIVE_PLACEHOLDER}，否则原样返回。
     */
    public String sanitizeAssistantOutput(String text) {
        if (!StringUtils.hasText(text) || enabledWords.isEmpty()) {
            return text;
        }
        if (findHitInUserText(text) != null) {
            return ASSISTANT_SENSITIVE_PLACEHOLDER;
        }
        return text;
    }

    public List<SensitiveWord> listAllRows() {
        return sensitiveWordMapper.selectAll();
    }

    public void addWord(String word, int enabled) {
        if (!StringUtils.hasText(word)) {
            throw new IllegalArgumentException("word 不能为空");
        }
        String w = word.trim();
        if (sensitiveWordMapper.selectByWord(w) != null) {
            throw new IllegalArgumentException("该敏感词已存在");
        }
        SensitiveWord row = new SensitiveWord();
        row.setWord(w);
        row.setEnabled(enabled);
        sensitiveWordMapper.insert(row);
        reload();
    }

    public void updateWord(Long id, String word, int enabled) {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为空");
        }
        SensitiveWord existing = sensitiveWordMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("记录不存在");
        }
        String w = StringUtils.hasText(word) ? word.trim() : existing.getWord();
        SensitiveWord other = sensitiveWordMapper.selectByWord(w);
        if (other != null && !other.getId().equals(id)) {
            throw new IllegalArgumentException("与其他记录敏感词重复");
        }
        SensitiveWord row = new SensitiveWord();
        row.setId(id);
        row.setWord(w);
        row.setEnabled(enabled);
        sensitiveWordMapper.update(row);
        reload();
    }

    public void deleteWord(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为空");
        }
        int n = sensitiveWordMapper.deleteById(id);
        if (n == 0) {
            throw new IllegalArgumentException("记录不存在");
        }
        reload();
    }

    public long countWords() {
        return sensitiveWordMapper.countAll();
    }
}
