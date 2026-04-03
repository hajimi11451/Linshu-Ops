package com.example.backend.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmClient {

    private final ObjectMapper objectMapper;

    @Value("${qianfan.v2.base-url}")
    private String baseUrl;

    @Value("${qianfan.v2.token}")
    private String token;

    @Value("${qianfan.v2.chat-model-name}")
    private String chatModelName;

    @Value("${qianfan.v2.agent-read-timeout-seconds}")
    private int agentReadTimeoutSeconds;

    private RestClient restClient;

    @PostConstruct
    public void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(Math.max(agentReadTimeoutSeconds, 5) * 1_000);
        restClient = RestClient.builder()
                .baseUrl(normalizeBaseUrl(baseUrl))
                .requestFactory(factory)
                .build();
    }

    public LlmResponse call(List<Message> messages, List<Map<String, Object>> toolDefinitions) {
        long start = System.currentTimeMillis();

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", chatModelName);
        requestBody.put("messages", toApiMessages(messages));
        requestBody.put("tools", toolDefinitions == null ? Collections.emptyList() : toolDefinitions);

        JsonNode root = post(requestBody);
        JsonNode messageNode = firstMessageNode(root);
        String content = extractMessageContent(messageNode);

        List<Map<String, Object>> rawToolCalls = new ArrayList<>();
        List<LlmResponse.ToolCall> toolCalls = new ArrayList<>();
        JsonNode toolCallsNode = messageNode.path("tool_calls");
        if (toolCallsNode.isArray()) {
            for (JsonNode toolCallNode : toolCallsNode) {
                rawToolCalls.add(objectMapper.convertValue(toolCallNode, Map.class));
                String argumentsRaw = toolCallNode.path("function").path("arguments").asText("{}");
                Map<String, Object> arguments = parseArguments(argumentsRaw);
                toolCalls.add(LlmResponse.ToolCall.builder()
                        .id(toolCallNode.path("id").asText(""))
                        .name(toolCallNode.path("function").path("name").asText(""))
                        .argumentsRaw(argumentsRaw)
                        .arguments(arguments)
                        .build());
            }
        }

        Message assistantMessage = Message.assistantWithToolCalls(content, rawToolCalls);
        LlmResponse response = LlmResponse.builder()
                .type(toolCalls.isEmpty() ? LlmResponse.ResponseType.TEXT : LlmResponse.ResponseType.TOOL_CALL)
                .content(content)
                .assistantMessage(assistantMessage)
                .toolCalls(toolCalls)
                .build();

        log.info("LLM tool call success, toolCalls={}, elapsedMs={}", toolCalls.size(), System.currentTimeMillis() - start);
        return response;
    }

    public JsonNode callForJson(String systemPrompt, String userPrompt) {
        List<Message> messages = new ArrayList<>();
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(Message.system(systemPrompt));
        }
        messages.add(Message.user(userPrompt == null ? "" : userPrompt));
        return callForJson(messages);
    }

    public JsonNode callForJson(List<Message> messages) {
        long start = System.currentTimeMillis();

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", chatModelName);
        requestBody.put("messages", toApiMessages(messages));
        requestBody.put("response_format", Map.of("type", "json_object"));

        JsonNode root = post(requestBody);
        JsonNode messageNode = firstMessageNode(root);
        String content = extractMessageContent(messageNode);

        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("LLM 未返回 JSON 内容。");
        }

        try {
            JsonNode jsonNode = objectMapper.readTree(extractJsonText(content));
            log.info("LLM JSON call success, elapsedMs={}", System.currentTimeMillis() - start);
            return jsonNode;
        } catch (Exception e) {
            throw new IllegalStateException("LLM JSON 解析失败: " + e.getMessage(), e);
        }
    }

    private JsonNode post(Map<String, Object> requestBody) {
        try {
            String responseBody = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            if (!StringUtils.hasText(responseBody)) {
                throw new IllegalStateException("LLM 返回为空。");
            }

            JsonNode root = objectMapper.readTree(responseBody);
            if (root.has("error")) {
                throw new IllegalStateException("LLM API error: " + root.path("error"));
            }
            return root;
        } catch (Exception e) {
            throw new IllegalStateException("调用 LLM 失败: " + e.getMessage(), e);
        }
    }

    private JsonNode firstMessageNode(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new IllegalStateException("LLM 未返回 choices。");
        }
        return choices.get(0).path("message");
    }

    private List<Map<String, Object>> toApiMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> apiMessages = new ArrayList<>();
        for (Message message : messages) {
            apiMessages.add(message.toMap());
        }
        return apiMessages;
    }

    private Map<String, Object> parseArguments(String argumentsRaw) {
        try {
            JsonNode argsNode = objectMapper.readTree(argumentsRaw);
            return objectMapper.convertValue(argsNode, Map.class);
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private String normalizeBaseUrl(String rawBaseUrl) {
        if (!StringUtils.hasText(rawBaseUrl)) {
            return "";
        }
        return rawBaseUrl.endsWith("/") ? rawBaseUrl.substring(0, rawBaseUrl.length() - 1) : rawBaseUrl;
    }

    private String extractMessageContent(JsonNode messageNode) {
        if (messageNode == null || messageNode.isMissingNode() || messageNode.isNull()) {
            return "";
        }
        String content = flattenContentNode(messageNode.path("content"));
        if (StringUtils.hasText(content)) {
            return content;
        }
        return flattenContentNode(messageNode.path("reasoning_content"));
    }

    private String flattenContentNode(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            return "";
        }
        if (contentNode.isTextual()) {
            return contentNode.asText("").trim();
        }
        if (contentNode.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : contentNode) {
                String text = "";
                if (item.isTextual()) {
                    text = item.asText("");
                } else if (item.isObject()) {
                    text = item.path("text").asText("");
                    if (!StringUtils.hasText(text)) {
                        text = item.path("content").asText("");
                    }
                }
                if (StringUtils.hasText(text)) {
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append(text.trim());
                }
            }
            return sb.toString().trim();
        }
        if (contentNode.isObject()) {
            String text = contentNode.path("text").asText("");
            if (!StringUtils.hasText(text)) {
                text = contentNode.path("content").asText("");
            }
            return text.trim();
        }
        return contentNode.asText("").trim();
    }

    private String extractJsonText(String rawContent) {
        String content = rawContent == null ? "" : rawContent.trim();
        if (content.startsWith("```")) {
            content = content.replaceFirst("^```(?:json)?", "").trim();
            if (content.endsWith("```")) {
                content = content.substring(0, content.length() - 3).trim();
            }
        }

        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }
}
