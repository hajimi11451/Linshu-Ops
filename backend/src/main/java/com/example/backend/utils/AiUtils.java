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

    @Value("${qianfan.v2.agent-read-timeout-seconds:30}")
    private int agentReadTimeoutSeconds;

    private static final String RAG_FILE_PATH = "d:\\WorkSpace\\JavaWorkSpace\\aiOps\\backend\\src\\main\\resources\\info.md";

    private RestTemplate restTemplate;
    private RestTemplate agentRestTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void initRestTemplate() {
        this.restTemplate = buildRestTemplate(readTimeoutSeconds);
        this.agentRestTemplate = buildRestTemplate(Math.min(readTimeoutSeconds, agentReadTimeoutSeconds));
        log.info("AI HTTP client initialized, readTimeoutSeconds={}, agentReadTimeoutSeconds={}",
                readTimeoutSeconds, Math.min(readTimeoutSeconds, agentReadTimeoutSeconds));
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
        return chatWithOpsAssistant(userQuery, null);
    }

    public String chatWithOpsAssistant(String userQuery, String extraInstruction) {
        String systemPrompt = "你是专业的运维聊天助手。优先给出可执行方案，并明确风险与回滚建议。";
        if (StringUtils.hasText(extraInstruction)) {
            systemPrompt += "\n附加要求：" + extraInstruction.trim();
        }
        return callQianfanApi(systemPrompt, userQuery, chatModelName);
    }

    public Map<String, String> classifyOpsIntent(String userQuery, boolean hasConnection) {
        Map<String, String> fallback = heuristicClassifyOpsIntent(userQuery, hasConnection);
        if (!StringUtils.hasText(userQuery)) {
            return fallback;
        }

        String systemPrompt = "你是运维请求分流助手。请判断用户这次输入更像是咨询提问，还是希望你直接在服务器上执行/检查/处理。"
                + "只允许返回一个 JSON 对象，字段固定且只能包含 intent,reason,confidence。"
                + "其中 intent 只能是 execute、chat、ambiguous 三个值。"
                + "判定规则："
                + "1. 只有在用户明确要求你去当前服务器检查、执行、修改、安装、重启、排查、处理时，才能返回 execute。"
                + "2. 如果用户是在问原理、原因、区别、步骤、建议、命令写法、如何处理，通常返回 chat。"
                + "3. 如果像“帮我看看”“查下”这类表述存在双重理解，就返回 ambiguous。"
                + "4. 当前是否已经选了服务器连接只是背景信息，不能因为有连接就把普通提问判成 execute。"
                + "5. reason 用一句中文短句说明判断依据，confidence 只能是 high、medium、low。"
                + "输出示例：{\"intent\":\"chat\",\"reason\":\"用户在咨询处理思路，没有明确要求立即操作服务器。\",\"confidence\":\"high\"}";

        String userPrompt = "当前是否有完整服务器连接信息: " + (hasConnection ? "是" : "否")
                + "\n用户输入: " + userQuery;

        try {
            String response = callQianfanApi(systemPrompt, userPrompt, chatModelName);
            JsonNode root = objectMapper.readTree(response.trim());
            if (root.isObject()) {
                String intent = normalizeIntent(root.path("intent").asText(""));
                if (StringUtils.hasText(intent)) {
                    Map<String, String> result = defaultIntentDecision();
                    result.put("intent", intent);
                    result.put("reason", sanitizeReason(root.path("reason").asText("")));
                    result.put("confidence", normalizeConfidence(root.path("confidence").asText("")));
                    if (!StringUtils.hasText(result.get("reason"))) {
                        result.put("reason", fallback.get("reason"));
                    }
                    return result;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to classify ops intent via AI, fallback to heuristic: {}", e.getMessage());
        }

        return fallback;
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
        String resolvedOsType = StringUtils.hasText(osType) ? osType.trim() : "Ubuntu Linux";
        String systemPrompt = "你是 Linux 运维专家。根据组件名和操作系统，返回最可能的错误日志文件绝对路径。"
                + "只返回一个以 / 开头的路径，不要返回 tail/cat 命令，不要 Markdown，不要解释。"
                + "如果是 Ubuntu 或 Debian 系统，SSH/sshd/登录认证/sudo 相关日志优先考虑 /var/log/auth.log；"
                + "如果是 CentOS/RHEL/Alibaba Cloud Linux，SSH/sshd/登录认证/sudo 相关日志优先考虑 /var/log/secure；"
                + "Ubuntu 常规系统日志优先考虑 /var/log/syslog。";
        String userPrompt = String.format("操作系统: %s%n组件: %s", resolvedOsType, component);
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

    private Map<String, String> defaultIntentDecision() {
        Map<String, String> result = new HashMap<>();
        result.put("intent", "chat");
        result.put("reason", "默认按咨询处理。");
        result.put("confidence", "low");
        return result;
    }

    private Map<String, String> heuristicClassifyOpsIntent(String userQuery, boolean hasConnection) {
        Map<String, String> result = defaultIntentDecision();
        String normalized = String.valueOf(userQuery == null ? "" : userQuery).trim().toLowerCase();
        if (!StringUtils.hasText(normalized)) {
            result.put("reason", "问题内容为空，默认按咨询处理。");
            return result;
        }

        boolean strongExecute = containsAny(normalized,
                "请执行", "帮我执行", "直接执行", "现在执行", "立刻执行", "到服务器", "上服务器",
                "登录服务器", "当前服务器", "这台服务器", "这台机器", "机器上", "执行一下");
        boolean questionLike = containsAny(normalized,
                "什么", "为什么", "如何", "怎么", "介绍", "解释", "区别", "原理", "含义",
                "建议", "方案", "思路", "怎么写", "如何写", "命令怎么", "命令如何");
        boolean ambiguous = containsAny(normalized,
                "帮我看看", "帮我看下", "帮我看一下", "看看", "看下", "查下", "查一下", "分析一下");
        boolean operatorVerbStart = normalized.matches("^(检查|查看|排查|重启|重载|安装|卸载|启动|停止|修复|处理|清理|执行|部署|更新|回滚|拉取|诊断|登录).*");

        if (strongExecute) {
            result.put("intent", "execute");
            result.put("reason", hasConnection
                    ? "用户明确要求在服务器上执行或处理，按执行请求处理。"
                    : "用户明确提出执行诉求，但当前未提供完整服务器连接。");
            result.put("confidence", "medium");
            return result;
        }

        if (operatorVerbStart && !questionLike) {
            result.put("intent", "execute");
            result.put("reason", "用户使用了明显的操作型动词开头，更像执行请求。");
            result.put("confidence", "medium");
            return result;
        }

        if (questionLike) {
            result.put("intent", "chat");
            result.put("reason", "用户更像是在咨询原因、方案或知识点，没有明确要求立即操作服务器。");
            result.put("confidence", "medium");
            return result;
        }

        if (ambiguous) {
            result.put("intent", "ambiguous");
            result.put("reason", hasConnection
                    ? "表达里有“看看/查下”这类模糊表述，存在咨询和执行两种理解。"
                    : "表达不够明确，先按咨询处理更安全。");
            result.put("confidence", "low");
            return result;
        }

        result.put("reason", "未识别到明确执行意图，默认按咨询处理。");
        return result;
    }

    private boolean containsAny(String text, String... keywords) {
        if (!StringUtils.hasText(text) || keywords == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String normalizeIntent(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim().toLowerCase();
        if ("execute".equals(normalized) || "chat".equals(normalized) || "ambiguous".equals(normalized)) {
            return normalized;
        }
        return "";
    }

    private String normalizeConfidence(String value) {
        if (!StringUtils.hasText(value)) {
            return "low";
        }
        String normalized = value.trim().toLowerCase();
        if ("high".equals(normalized) || "medium".equals(normalized) || "low".equals(normalized)) {
            return normalized;
        }
        return "low";
    }

    private String sanitizeReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return "";
        }
        return reason.replaceAll("\\s+", " ").trim();
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
                    String content = extractMessageContent(choice.path("message"));
                    if (StringUtils.hasText(content)) {
                        log.info("AI request success, model={}, elapsedMs={}", targetModel, System.currentTimeMillis() - start);
                        return content;
                    }
                    log.warn("AI request returned choice but no usable content, model={}, elapsedMs={}, body={}",
                            targetModel, System.currentTimeMillis() - start, response.getBody());
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
            if (e.getStatusCode().value() == 401) {
                return "AI 鉴权失败，请检查 qianfan.v2.token 是否有效。";
            }
            return "调用 AI 服务时发生异常。";
        } catch (Exception e) {
            log.error("Call AI API exception, model={}, elapsedMs={}", targetModel, System.currentTimeMillis() - start, e);
            return "AI 服务连接中断或超时，请稍后重试。";
        }
        log.warn("AI request finished without valid content, model={}, elapsedMs={}", targetModel, System.currentTimeMillis() - start);
        return "未能获取有效回答。";
    }

    private String extractMessageContent(JsonNode messageNode) {
        if (messageNode == null || messageNode.isMissingNode() || messageNode.isNull()) {
            return "";
        }

        JsonNode contentNode = messageNode.path("content");
        String content = flattenContentNode(contentNode);
        if (StringUtils.hasText(content)) {
            return content;
        }

        String reasoningContent = flattenContentNode(messageNode.path("reasoning_content"));
        if (StringUtils.hasText(reasoningContent)) {
            return reasoningContent;
        }
        return "";
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
                if (item == null || item.isNull()) {
                    continue;
                }
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
            return text == null ? "" : text.trim();
        }
        return contentNode.asText("").trim();
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
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                result.put("assistantContent", "任务已被强制停止。");
                return result;
            }
            try {
                return doCallQianfanApiWithTools(messages, tools, start);
            } catch (Exception e) {
                lastException = e;
                if (Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().interrupt();
                    break;
                }
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

        ResponseEntity<String> response = agentRestTemplate.postForEntity(url, entity, String.class);
        
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

    private RestTemplate buildRestTemplate(int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(Math.max(timeoutSeconds, 5) * 1000);
        return new RestTemplate(factory);
    }
}
