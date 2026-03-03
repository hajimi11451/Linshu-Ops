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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                + "1. 如果发现异常或错误，返回标准 JSON 对象，字段包含 component,errorSummary,analysisResult,suggestedActions,riskLevel。\n"
                + "2. 如果日志无异常，errorSummary/analysisResult/suggestedActions 填写\"无\"，riskLevel 填写\"无\"。\n"
                + "3. 直接返回 JSON，不要 Markdown。";

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

    public Map<String, Object> planServerCommandByGlm47(String userQuery) {
        String systemPrompt = "你是 Linux 运维助手。请把用户需求拆解为可执行的计划步骤，并返回 JSON，不要输出 Markdown 和额外文本。"
                + "顶层 JSON 字段固定为：reply,hasCommand,command,riskLevel,needConfirm,planSteps。"
                + "其中 hasCommand 和 needConfirm 必须是布尔值；riskLevel 只能是 low/medium/high。"
                + "planSteps 是数组，每项字段固定为：description,checkCommand,expectContains,onPass,onFail,executeCommand。"
                + "onPass/onFail 只能是 continue|execute|stop。"
                + "要求：优先使用“先检测后执行”的嵌套逻辑。"
                + "例如创建文件前，应先检测目录是否存在、文件是否已存在，再决定是否创建。"
                + "如果无需执行命令，hasCommand=false 且 command 为空字符串，planSteps 为空数组。";

        String response = callQianfanApi(systemPrompt, userQuery, "glm-4.7");
        Map<String, Object> parsed = parseCommandPlan(response);
        if (Boolean.TRUE.equals(parsed.get("hasCommand")) || hasPlanSteps(parsed)) {
            return parsed;
        }

        // AI 超时/断连/异常时本地兜底，不额外消耗 token
        if (isAiUnavailableResponse(response)) {
            log.warn("AI planning unavailable, fallback to local rule planner. query={}", userQuery);
            return buildFallbackPlan(userQuery, response);
        }
        return parsed;
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

    private boolean hasPlanSteps(Map<String, Object> plan) {
        Object raw = plan.get("planSteps");
        if (!(raw instanceof List<?> list)) {
            return false;
        }
        return !list.isEmpty();
    }

    private boolean isAiUnavailableResponse(String response) {
        if (!StringUtils.hasText(response)) {
            return true;
        }
        String s = response.trim();
        return s.contains("超时") || s.contains("连接中断") || s.contains("调用 AI 服务时发生异常");
    }

    private Map<String, Object> buildFallbackPlan(String query, String aiErrorText) {
        Map<String, Object> result = new HashMap<>();
        result.put("reply", "AI 规划不可用，已切换本地规则规划。");
        result.put("hasCommand", true);
        result.put("command", "");
        result.put("riskLevel", "low");
        result.put("needConfirm", true);

        String folderName = extractFolderName(query);
        String fileName = extractFileName(query);

        if (!StringUtils.hasText(folderName)) {
            folderName = "default_home_folder";
        }
        if (!StringUtils.hasText(fileName)) {
            fileName = "new_file.txt";
        } else if (!fileName.contains(".")) {
            fileName = fileName + ".txt";
        }

        String folderPath = "/home/" + folderName;
        String filePath = folderPath + "/" + fileName;

        List<Map<String, String>> steps = new ArrayList<>();
        steps.add(step(
                "检查目标目录是否存在",
                "test -d '" + folderPath + "' && echo YES || echo NO",
                "YES",
                "continue",
                "stop",
                ""
        ));
        steps.add(step(
                "检查目标文件是否已存在",
                "test -f '" + filePath + "' && echo YES || echo NO",
                "YES",
                "stop",
                "execute",
                "touch '" + filePath + "' && echo CREATED"
        ));

        result.put("planSteps", steps);
        result.put("fallback", true);
        result.put("fallbackReason", aiErrorText);
        return result;
    }

    private Map<String, String> step(String description, String checkCommand, String expectContains,
                                     String onPass, String onFail, String executeCommand) {
        Map<String, String> step = new HashMap<>();
        step.put("description", description);
        step.put("checkCommand", checkCommand);
        step.put("expectContains", expectContains);
        step.put("onPass", onPass);
        step.put("onFail", onFail);
        step.put("executeCommand", executeCommand);
        return step;
    }

    private String extractFolderName(String query) {
        if (!StringUtils.hasText(query)) return "";
        Pattern p = Pattern.compile("home目录(?:下|上)?(?:的)?([\\u4e00-\\u9fa5A-Za-z0-9_\\-]+?)文件夹");
        Matcher m = p.matcher(query);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "";
    }

    private String extractFileName(String query) {
        if (!StringUtils.hasText(query)) return "";
        Pattern named = Pattern.compile("(?:叫做|名叫)([\\u4e00-\\u9fa5A-Za-z0-9_\\-.]+)");
        Matcher m1 = named.matcher(query);
        if (m1.find()) {
            return m1.group(1).trim();
        }
        Pattern create = Pattern.compile("创建([\\u4e00-\\u9fa5A-Za-z0-9_\\-.]+?)(?:文件|txt|文本)");
        Matcher m2 = create.matcher(query);
        if (m2.find()) {
            return m2.group(1).trim();
        }
        return "";
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

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(Math.max(readTimeoutSeconds, 1) * 1000);
        return new RestTemplate(factory);
    }
}
