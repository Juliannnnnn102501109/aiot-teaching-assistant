package com.aicoursemaster.ai;

import com.aicoursemaster.ai.dto.RagRetrieveAndAnswerRequest;
import com.aicoursemaster.ai.dto.RagRetrieveAndAnswerResponse;
import com.aicoursemaster.ai.dto.LlmGenerateRequest;
import com.aicoursemaster.ai.dto.LlmGenerateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class RagApiClient {

    private final RestTemplate ragRestTemplate;
    private final AiRagProperties props;

    public RagRetrieveAndAnswerResponse ragChat(String question, String sessionId, Integer topK, boolean useCoT) {
        String url = props.getBaseUrl() + props.getRagChatPath();

        RagRetrieveAndAnswerRequest request = new RagRetrieveAndAnswerRequest(
                question,
                topK != null && topK > 0 ? topK : props.getDefaultTopK(),
                sessionId,
                useCoT
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<RagRetrieveAndAnswerRequest> entity = new HttpEntity<>(request, headers);

        try {
            RagRetrieveAndAnswerResponse resp = ragRestTemplate.postForObject(url, entity, RagRetrieveAndAnswerResponse.class);
            if (resp == null) {
                throw new RestClientException("Empty response");
            }
            return resp;
        } catch (RestClientException e) {
            log.error("调用 RAG 接口失败 url={}, sessionId={}, error={}", url, sessionId, e.getMessage(), e);
            throw e;
        }
    }

    public LlmGenerateResponse llmGenerate(String prompt, String systemPrompt, String sessionId) {
        String url = props.getBaseUrl() + props.getLlmGeneratePath();
        LlmGenerateRequest request = new LlmGenerateRequest(prompt, systemPrompt, sessionId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<LlmGenerateRequest> entity = new HttpEntity<>(request, headers);

        try {
            LlmGenerateResponse resp = ragRestTemplate.postForObject(url, entity, LlmGenerateResponse.class);
            if (resp == null) {
                throw new RestClientException("Empty response");
            }
            return resp;
        } catch (RestClientException e) {
            log.error("调用 LLM 接口失败 url={}, sessionId={}, error={}", url, sessionId, e.getMessage(), e);
            throw e;
        }
    }
}

