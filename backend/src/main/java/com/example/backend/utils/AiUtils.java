package com.example.backend.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AiUtils {

    @Value("${qianfan.v2.base-url}")
    private String baseUrl;

    @Value("${qianfan.v2.token}")
    private String token;

    @Value("${qianfan.v2.audit-model-name:glm-4.7}")
    private String auditModelName;

    @Value("${qianfan.v2.chat-model-name:glm-4.7}")
    private String chatModelName;

    @Value("${qianfan.v2.read-timeout-seconds:40}")
    private int readTimeoutSeconds;

    private static final String RAG_FILE_PATH = "d:\\WorkSpace\\JavaWorkSpace\\aiOps\\backend\\src\\main\\resources\\info.md";

    private RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void initRestTemplate() {
        this.restTemplate = buildRestTemplate();
        log.info("AI HTTP client initialized, readTimeoutSeconds={}", readTimeoutSeconds);
    }

    public String analyzeLog(String logContent) {
        String ragContext = readRagFile();
        String systemPrompt = "你是一名智能运维专家，请根据提供的日志信息进行分析。\n";

        if (StringUtils.hasText(ragContext)) {
            systemPrompt += "参考资料如下：\n" + ragContext + "\n\n";
        }

        systemPrompt += "请严格遵守以下输出规则：\n"
                + "1. 只允许返回一个 JSON 对象，字段必须且只能包含 component,errorSummary,analysisResult,suggestedActions,riskLevel。\n"
                + "2. riskLevel 只允许是 \"高\"、\"中\"、\"低\"、\"无\" 四个值，绝对不要返回 high/medium/low/normal/warning 等英文。\n"
                + "3. suggestedActions 必须是 JSON 数组，例如 [\"检查配置文件\",\"重启服务\"]，每项是一条独立的处理建议，最多 4 条，不要返回长段落，不要返回编号拼接字符串。\n"
                + "4. errorSummary 要简洁，analysisResult 要清楚说明根因或现象，不要输出 Markdown，不要输出代码块，不要增加其他字段。\n"
                + "5. 如果日志无异常，errorSummary 和 analysisResult 填写 \"无\"，suggestedActions 返回 []，riskLevel 返回 \"无\"。\n"
                + "6. 输出示例：{\"component\":\"nginx\",\"errorSummary\":\"配置加载失败\",\"analysisResult\":\"Nginx 配置文件存在语法错误，导致服务启动失败。\",\"suggestedActions\":[\"执行 nginx -t 检查配置语法\",\"修复报错配置项后重新加载服务\"],\"riskLevel\":\"中\"}";

        return callQianfanApi(systemPrompt, logContent, auditModelName);
    }

    public String chatWithRag(String userQuery) {
        String ragContext = readRagFile();
        String systemPrompt = "你是一个专业的 AI 助手。";
        if (StringUtils.hasText(ragContext)) {
            systemPrompt += "\n以下是参考资料，请结合资料回答；如果资料无相关信息，可基于常识补充：\n" + ragContext;
        }
        return callQianfanApi(systemPrompt, userQuery, chatModelName);
    }

    public String chatWithOpsAssistant(String userQuery) {
        String systemPrompt = "你是专业的运维聊天助手。优先给出可执行方案，并明确风险与回滚建议。";
        return callQianfanApi(systemPrompt, userQuery, chatModelName);
    }

    /**
     * 在对话结束后判断是否建议生成图表（由 AI 判断）。
     * 返回结构：{ chartSuggest: boolean, chartReason: string, chartTimeRange: string, chartTemplate: string }
     */
    public Map<String, Object> analyzeChartNeed(String userQuery, String finalReply, String execResult) {
        Map<String, Object> result = new HashMap<>();
        result.put("chartSuggest", false);
        result.put("chartReason", "无需图表");
        result.put("chartTimeRange", "1h");
        result.put("chartTemplate", "health_overview");

        String systemPrompt = "你是运维分析助手。请判断当前对话结果是否适合用图表展示。"
                + "仅返回 JSON：{\"chartSuggest\":true/false,\"chartReason\":\"...\",\"chartTimeRange\":\"30m|1h|2h\",\"chartTemplate\":\"health_overview|cpu_mem_trend|anomaly_timeline|health_score_radar\"}。"
                + "如果涉及 CPU/内存/负载/趋势/波动/峰值/监控，优先建议 chartSuggest=true。";
        String userPrompt = "用户问题: " + (userQuery == null ? "" : userQuery)
                + "\n最终回复: " + (finalReply == null ? "" : finalReply)
                + "\n执行结果: " + (execResult == null ? "" : execResult);

        try {
            String resp = callQianfanApi(systemPrompt, userPrompt, chatModelName);
            JsonNode root = objectMapper.readTree(resp);
            if (root.isObject()) {
                boolean suggest = root.path("chartSuggest").asBoolean(false);
                String reason = root.path("chartReason").asText("无需图表");
                String range = root.path("chartTimeRange").asText("1h");
                String template = root.path("chartTemplate").asText("health_overview");
                if (!"30m".equals(range) && !"1h".equals(range) && !"2h".equals(range)) {
                    range = "1h";
                }
                if (!"health_overview".equals(template)
                        && !"cpu_mem_trend".equals(template)
                        && !"anomaly_timeline".equals(template)
                        && !"health_score_radar".equals(template)) {
                    template = "health_overview";
                }
                result.put("chartSuggest", suggest);
                result.put("chartReason", reason);
                result.put("chartTimeRange", range);
                result.put("chartTemplate", template);
                return result;
            }
        } catch (Exception ignored) {
            // fallback to keyword heuristic below
        }

        String merged = ((userQuery == null ? "" : userQuery) + " " + (finalReply == null ? "" : finalReply) + " " + (execResult == null ? "" : execResult))
                .toLowerCase();
        if (merged.contains("cpu") || merged.contains("内存") || merged.contains("负载")
                || merged.contains("趋势") || merged.contains("波动") || merged.contains("监控")) {
            result.put("chartSuggest", true);
            result.put("chartReason", "包含监控与趋势分析信息，适合图表展示。");
            result.put("chartTimeRange", "1h");
            result.put("chartTemplate", "health_overview");
        }
        return result;
    }

    public Map<String, Object> planServerCommandByGlm47(String userQuery) {
        String systemPrompt = "你是 Linux 运维助手。请根据用户需求生成可直接执行的一条命令并返回 JSON，不要输出 Markdown 和额外文本。"
                + "JSON 字段固定为：reply,hasCommand,command,riskLevel,needConfirm。"
                + "其中 hasCommand 和 needConfirm 必须是布尔值；riskLevel 只能是 low/medium/high。"
                + "如果无需执行命令，hasCommand=false 且 command 为空字符串。";

        String response = callQianfanApi(systemPrompt, userQuery, "glm-4.7");
        return parseCommandPlan(response);
    }

    public String generateLogCommand(String component, String osType) {
        String systemPrompt = "你是 Linux 运维专家。根据组件名和操作系统，生成一条获取最近50行错误日志的命令。"
                + "只返回命令字符串，不要 Markdown，不要解释。";
        String userPrompt = String.format("OS: %s, Component: %s", osType, component);
        String cmd = callQianfanApi(systemPrompt, userPrompt, auditModelName);
        return cmd.replace("```bash", "").replace("```", "").trim();
    }

    private String readRagFile() {
        try {
            Path path = Paths.get(RAG_FILE_PATH);
            if (Files.exists(path)) {
                return Files.readString(path);
            }
            log.warn("RAG file does not exist: {}", RAG_FILE_PATH);
            return "";
        } catch (IOException e) {
            log.error("Failed to read RAG file", e);
            return "";
        }
    }

    public void analyzeAndExtractKnowledge(List<String> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            log.info("No history data for AI extraction.");
            return;
        }

        StringBuilder sb = new StringBuilder("以下是最近7天的用户运维操作记录：\n");
        for (String record : dataList) {
            sb.append("- ").append(record).append("\n");
        }

        String systemPrompt = "你是运维知识库构建专家。请分析数据，提取高频可复用处理方式。"
                + "如果没有规律，直接回答“无”。";

        log.info("Start extracting operation knowledge by AI...");
        String aiResponse = callQianfanApi(systemPrompt, sb.toString(), auditModelName);

        if (StringUtils.hasText(aiResponse) && !"无".equals(aiResponse.trim())) {
            appendKnowledgeToRagFile(aiResponse);
        }
    }

    private void appendKnowledgeToRagFile(String content) {
        try {
            Path path = Paths.get(RAG_FILE_PATH);
            String finalContent = "\n\n### 自动归档知识 (" + LocalDate.now() + ")\n" + content;
            Files.createDirectories(path.getParent());
            Files.writeString(path, finalContent, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            log.info("Knowledge appended to {}", RAG_FILE_PATH);
        } catch (IOException e) {
            log.error("Failed to append knowledge", e);
        }
    }

    private Map<String, Object> parseCommandPlan(String response) {
        Map<String, Object> result = new HashMap<>();
        result.put("reply", response);
        result.put("hasCommand", false);
        result.put("command", "");
        result.put("riskLevel", "medium");
        result.put("needConfirm", true);
        result.put("planSteps", new ArrayList<>());

        if (!StringUtils.hasText(response)) {
            result.put("reply", "未获取到有效回答。");
            return result;
        }

        try {
            JsonNode root = objectMapper.readTree(response.trim());
            if (root.isObject()) {
                result.put("reply", root.path("reply").asText(response));
                result.put("hasCommand", root.path("hasCommand").asBoolean(false));
                result.put("command", root.path("command").asText(""));
                result.put("riskLevel", root.path("riskLevel").asText("medium"));
                result.put("needConfirm", root.path("needConfirm").asBoolean(true));
                result.put("planSteps", parsePlanSteps(root.path("planSteps")));
            }
        } catch (Exception ignored) {
            // keep fallback shape
        }
        return result;
    }

    private List<Map<String, String>> parsePlanSteps(JsonNode planStepsNode) {
        List<Map<String, String>> steps = new ArrayList<>();
        if (!planStepsNode.isArray()) {
            return steps;
        }

        for (JsonNode item : planStepsNode) {
            if (!item.isObject()) {
                continue;
            }
            Map<String, String> step = new HashMap<>();
            step.put("description", item.path("description").asText(""));
            step.put("checkCommand", item.path("checkCommand").asText(""));
            step.put("expectContains", item.path("expectContains").asText("YES"));
            step.put("onPass", normalizeAction(item.path("onPass").asText("continue")));
            step.put("onFail", normalizeAction(item.path("onFail").asText("stop")));
            step.put("executeCommand", item.path("executeCommand").asText(""));
            steps.add(step);
        }
        return steps;
    }

    private String normalizeAction(String action) {
        if (!StringUtils.hasText(action)) {
            return "continue";
        }
        String normalized = action.trim().toLowerCase();
        if ("execute".equals(normalized) || "stop".equals(normalized)) {
            return normalized;
        }
        return "continue";
    }

    private String callQianfanApi(String systemPrompt, String userPrompt, String targetModel) {
        long start = System.currentTimeMillis();
        try {
            String url = baseUrl;
            if (!url.endsWith("/")) {
                url += "/";
            }
            url += "chat/completions";
            log.info("AI request start, model={}, url={}", targetModel, url);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", targetModel);

            List<Map<String, String>> messages = new ArrayList<>();

            if (StringUtils.hasText(systemPrompt)) {
                Map<String, String> systemMsg = new HashMap<>();
                systemMsg.put("role", "system");
                systemMsg.put("content", systemPrompt);
                messages.add(systemMsg);
            }

            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userPrompt == null ? "" : userPrompt);
            messages.add(userMsg);

            requestBody.put("messages", messages);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + token);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                if (root.has("choices") && root.get("choices").isArray() && root.get("choices").size() > 0) {
                    JsonNode choice = root.get("choices").get(0);
                    if (choice.has("message") && choice.get("message").has("content")) {
                        log.info("AI request success, model={}, elapsedMs={}", targetModel, System.currentTimeMillis() - start);
                        return choice.get("message").get("content").asText();
                    }
                } else if (root.has("error")) {
                    log.error("AI request failed, model={}, elapsedMs={}, error={}",
                            targetModel, System.currentTimeMillis() - start, root.get("error"));
                    log.error("AI API error: {}", root.get("error"));
                    return "AI 服务暂时不可用: " + root.get("error");
                }
            }
        } catch (HttpClientErrorException e) {
            log.error("AI request http error, model={}, status={}, elapsedMs={}, body={}",
                    targetModel, e.getStatusCode(), System.currentTimeMillis() - start, e.getResponseBodyAsString(), e);
            return "调用 AI 服务时发生异常。";
        } catch (Exception e) {
            log.error("Call AI API exception, model={}, elapsedMs={}", targetModel, System.currentTimeMillis() - start, e);
            return "AI 服务连接中断或超时，请稍后重试。";
        }
        log.warn("AI request finished without valid content, model={}, elapsedMs={}", targetModel, System.currentTimeMillis() - start);
        return "未能获取有效回答。";
    }

    /**
     * 调用千帆 Chat Completions（支持 message 历史 + tools/function calling）。
     * 返回结构：
     * - assistantContent: assistant 普通文本内容
     * - toolCalls: 工具调用列表，每项包含 id/name/argumentsRaw/arguments
     * - assistantMessage: 可直接追加到 messages 的 assistant 消息（含 tool_calls）
     */
    public Map<String, Object> callQianfanApiWithTools(List<Map<String, Object>> messages,
                                                       List<Map<String, Object>> tools) {
        long start = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();
        // 默认返回值，避免空指针
        result.put("assistantContent", "");
        result.put("toolCalls", new ArrayList<Map<String, Object>>());
        Map<String, Object> defaultAssistantMessage = new HashMap<>();
        defaultAssistantMessage.put("role", "assistant");
        defaultAssistantMessage.put("content", "");
        result.put("assistantMessage", defaultAssistantMessage);

        int maxRetries = 2;
        int attempt = 0;
        Exception lastException = null;

        while (attempt <= maxRetries) {
            try {
                return doCallQianfanApiWithTools(messages, tools, start);
            } catch (Exception e) {
                lastException = e;
                attempt++;
                log.warn("AI tools request failed (attempt {}/{}), elapsedMs={}, error={}", 
                        attempt, maxRetries + 1, System.currentTimeMillis() - start, e.getMessage());
                
                if (attempt <= maxRetries) {
                    try {
                        Thread.sleep(1000L * attempt); // 指数退避
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("AI tools request failed after {} retries, elapsedMs={}", maxRetries + 1, System.currentTimeMillis() - start, lastException);
        result.put("assistantContent", "AI 服务连接中断或超时，请稍后重试。");
        return result;
    }

    private Map<String, Object> doCallQianfanApiWithTools(List<Map<String, Object>> messages,
                                                          List<Map<String, Object>> tools,
                                                          long start) {
        String url = baseUrl;
        if (!url.endsWith("/")) {
            url += "/";
        }
        url += "chat/completions";

        // 这里显式组装 JSON：model + messages + tools
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", chatModelName);
        requestBody.put("messages", messages == null ? Collections.emptyList() : messages);
        requestBody.put("tools", tools == null ? Collections.emptyList() : tools);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + token);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
        
        Map<String, Object> result = new HashMap<>();
        // 默认返回值
        result.put("assistantContent", "");
        result.put("toolCalls", new ArrayList<Map<String, Object>>());
        Map<String, Object> defaultAssistantMessage = new HashMap<>();
        defaultAssistantMessage.put("role", "assistant");
        defaultAssistantMessage.put("content", "");
        result.put("assistantMessage", defaultAssistantMessage);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            result.put("assistantContent", "AI 返回为空或状态异常。");
            return result;
        }

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            // 检查是否有错误字段
            if (root.has("error")) {
                String errorMsg = root.get("error").toString();
                log.error("AI API returned error: {}", errorMsg);
                throw new RuntimeException("AI API error: " + errorMsg);
            }

            JsonNode choice = root.path("choices").isArray() && root.path("choices").size() > 0
                    ? root.path("choices").get(0) : null;
            if (choice == null) {
                result.put("assistantContent", "AI 未返回可用 choices。");
                return result;
            }

            JsonNode msgNode = choice.path("message");
            String content = msgNode.path("content").asText("");
            result.put("assistantContent", content);

            Map<String, Object> assistantMessage = new LinkedHashMap<>();
            assistantMessage.put("role", "assistant");
            assistantMessage.put("content", content);

            List<Map<String, Object>> toolCalls = new ArrayList<>();
            JsonNode toolCallsNode = msgNode.path("tool_calls");
            if (toolCallsNode.isArray()) {
                List<Map<String, Object>> rawToolCalls = new ArrayList<>();
                // 解析千帆 tool_calls，提取 function.name 和 arguments(JSON 字符串)
                for (JsonNode tc : toolCallsNode) {
                    Map<String, Object> rawTc = objectMapper.convertValue(tc, Map.class);
                    rawToolCalls.add(rawTc);

                    Map<String, Object> parsed = new LinkedHashMap<>();
                    String toolCallId = tc.path("id").asText("");
                    String toolName = tc.path("function").path("name").asText("");
                    String argumentsRaw = tc.path("function").path("arguments").asText("{}");

                    Map<String, Object> argumentsObj;
                    try {
                        JsonNode argsNode = objectMapper.readTree(argumentsRaw);
                        argumentsObj = objectMapper.convertValue(argsNode, Map.class);
                    } catch (Exception ignored) {
                        argumentsObj = new HashMap<>();
                    }

                    parsed.put("id", toolCallId);
                    parsed.put("name", toolName);
                    parsed.put("argumentsRaw", argumentsRaw);
                    parsed.put("arguments", argumentsObj);
                    toolCalls.add(parsed);
                }
                assistantMessage.put("tool_calls", rawToolCalls);
            }

            result.put("toolCalls", toolCalls);
            result.put("assistantMessage", assistantMessage);
            log.info("AI tools request success, model={}, toolCalls={}, elapsedMs={}",
                    chatModelName, toolCalls.size(), System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
             throw new RuntimeException("Parse AI response failed", e);
        }
    }

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(Math.max(readTimeoutSeconds, 30) * 1000);
        return new RestTemplate(factory);
    }
}
