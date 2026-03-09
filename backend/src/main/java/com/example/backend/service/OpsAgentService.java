package com.example.backend.service;

import com.example.backend.utils.AiUtils;
import com.example.backend.utils.SshUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.Pattern;

@Slf4j
@Service
public class OpsAgentService {

    private static final int DEFAULT_AGENT_ROUNDS = 15;
    private static final long SSH_TIMEOUT_SECONDS = 60;
    private static final String DEFAULT_CHART_RANGE = "1h";
    private static final String DEFAULT_CHART_TEMPLATE = "health_overview";
    private static final List<String> SUPPORTED_CHART_RANGES = Arrays.asList("30m", "1h", "2h");
    private static final List<String> SUPPORTED_CHART_TEMPLATES = Arrays.asList(
            "health_overview",
            "cpu_mem_trend",
            "anomaly_timeline",
            "health_score_radar"
    );

    // session -> stop flag
    private final ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicBoolean> stopFlags = new ConcurrentHashMap<>();

    private static final List<Pattern> HIGH_RISK_PATTERNS = Arrays.asList(
            Pattern.compile("(^|\\s)rm\\s+-rf\\s+/(\\s|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(^|\\s)mkfs(\\.|\\s|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(^|\\s)dd\\s+if=.*of=/dev/", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(^|\\s)shutdown(\\s|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(^|\\s)reboot(\\s|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(^|\\s)userdel\\s+-r\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(^|\\s)chmod\\s+-R\\s+777\\s+/", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(^|\\s)>\\s*/etc/", Pattern.CASE_INSENSITIVE)
    );

    @Autowired
    private AiUtils aiUtils;

    @Autowired
    private SshUtils sshUtils;

    @Autowired
    private MonitorService monitorService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void stopAgent(String sessionId) {
        java.util.concurrent.atomic.AtomicBoolean flag = stopFlags.get(sessionId);
        if (flag != null) {
            flag.set(true);
        }
    }

    private boolean shouldStop(String sessionId) {
        java.util.concurrent.atomic.AtomicBoolean flag = stopFlags.get(sessionId);
        return flag != null && flag.get();
    }

    @Getter
    @Setter
    public static class AgentRunResult {
        private String finalSummary;
        private boolean chartSuggest;
        private String chartReason;
        private String chartTimeRange;
        private String chartTemplate;
        private String chartTitle;

        public static AgentRunResult defaults() {
            AgentRunResult result = new AgentRunResult();
            result.setFinalSummary("");
            result.setChartSuggest(false);
            result.setChartReason("无需图表");
            result.setChartTimeRange(DEFAULT_CHART_RANGE);
            result.setChartTemplate(DEFAULT_CHART_TEMPLATE);
            result.setChartTitle(defaultChartTitle(DEFAULT_CHART_TEMPLATE, DEFAULT_CHART_RANGE));
            return result;
        }
    }

    public String runAgentLoop(String userQuery,
                               String serverIp,
                               String username,
                               String password,
                               WebSocketSession session) {
        return runAgentLoopWithAdvice(userQuery, serverIp, username, password, DEFAULT_AGENT_ROUNDS, null, session)
                .getFinalSummary();
    }

    public AgentRunResult runAgentLoopWithAdvice(String userQuery,
                                                 String serverIp,
                                                 String username,
                                                 String password,
                                                 WebSocketSession session) {
        return runAgentLoopWithAdvice(userQuery, serverIp, username, password, DEFAULT_AGENT_ROUNDS, null, session);
    }

    /**
     * 动态自主 Agent 主循环。
     * @param approvedRiskCommand 为空表示未确认高风险命令；非空表示允许执行该命令。
     */
    public String runAgentLoop(String userQuery,
                               String serverIp,
                               String username,
                               String password,
                               int maxRounds,
                               String approvedRiskCommand,
                               WebSocketSession session) {
        return runAgentLoopWithAdvice(userQuery, serverIp, username, password, maxRounds, approvedRiskCommand, session)
                .getFinalSummary();
    }

    public AgentRunResult runAgentLoopWithAdvice(String userQuery,
                                                 String serverIp,
                                                 String username,
                                                 String password,
                                                 int maxRounds,
                                                 String approvedRiskCommand,
                                                 WebSocketSession session) {
        return runAgentLoopWithAdvice(userQuery, serverIp, username, password, maxRounds, approvedRiskCommand, session, new ArrayList<>());
    }

    public String runAgentLoop(String userQuery,
                               String serverIp,
                               String username,
                               String password,
                               int maxRounds,
                               String approvedRiskCommand,
                               WebSocketSession session,
                               List<Map<String, Object>> existingHistory) {
        return runAgentLoopWithAdvice(userQuery, serverIp, username, password, maxRounds, approvedRiskCommand, session, existingHistory)
                .getFinalSummary();
    }

    public AgentRunResult runAgentLoopWithAdvice(String userQuery,
                                                 String serverIp,
                                                 String username,
                                                 String password,
                                                 int maxRounds,
                                                 String approvedRiskCommand,
                                                 WebSocketSession session,
                                                 List<Map<String, Object>> existingHistory) {
        long start = System.currentTimeMillis();
        AgentRunResult finalResult = AgentRunResult.defaults();
        String sessionId = session.getId();
        boolean metricsRequested = false;

        // 如果是首次运行（不是继续执行），则初始化停止标志
        // 如果是继续执行，需要保留之前的停止标志状态，或者重新初始化为 false
        // 这里选择重新初始化，确保每次 runAgentLoop 开始时都是可运行状态
        stopFlags.put(sessionId, new java.util.concurrent.atomic.AtomicBoolean(false));

        try {
            List<Map<String, Object>> messages = existingHistory;
            if (messages == null || messages.isEmpty()) {
                messages = new ArrayList<>();
                messages.add(systemMessage());

                Map<String, Object> userMsg = new LinkedHashMap<>();
                userMsg.put("role", "user");
                userMsg.put("content", "用户目标: " + (userQuery == null ? "" : userQuery)
                        + "\n目标服务器: " + serverIp
                        + "\n请你自主分析并逐步执行，完成后必须调用 finish_task。");
                messages.add(userMsg);
            }

        List<Map<String, Object>> tools = buildTools();
        // 只有首次运行时才发送 agent_start
        if (existingHistory == null || existingHistory.isEmpty()) {
            sendProgress(session, "agent_start", "进入 Agent 自主循环", System.currentTimeMillis() - start);
        } else {
            sendProgress(session, "agent_resume", "Agent 继续执行", System.currentTimeMillis() - start);
        }

        for (int round = 1; round <= maxRounds; round++) {
            if (shouldStop(sessionId)) {
                finalResult.setFinalSummary("任务已被用户强制停止。");
                sendProgress(session, "agent_stop", finalResult.getFinalSummary(), System.currentTimeMillis() - start);
                return finalResult;
            }

            sendProgress(session, "agent_think", "第 " + round + " 轮：AI 正在决策下一步", System.currentTimeMillis() - start);

            Map<String, Object> aiResp = aiUtils.callQianfanApiWithTools(messages, tools);

            if (shouldStop(sessionId)) {
                finalResult.setFinalSummary("任务已被用户强制停止。");
                sendProgress(session, "agent_stop", finalResult.getFinalSummary(), System.currentTimeMillis() - start);
                return finalResult;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> assistantMessage = (Map<String, Object>) aiResp.getOrDefault("assistantMessage", new HashMap<>());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) aiResp.getOrDefault("toolCalls", new ArrayList<Map<String, Object>>());
            String assistantContent = String.valueOf(aiResp.getOrDefault("assistantContent", ""));

            if (!assistantMessage.containsKey("role")) {
                assistantMessage.put("role", "assistant");
            }
            if (!assistantMessage.containsKey("content")) {
                assistantMessage.put("content", assistantContent);
            }

            messages.add(assistantMessage);

            if (StringUtils.hasText(assistantContent)) {
                sendProgress(session, "agent_reply", assistantContent, System.currentTimeMillis() - start);
            }

            if (toolCalls.isEmpty()) {
                Map<String, Object> remind = new LinkedHashMap<>();
                remind.put("role", "user");
                remind.put("content", "你尚未调用工具。请继续调用 execute_command 或 finish_task，不要只输出说明。");
                messages.add(remind);
                continue;
            }

            boolean shouldBreak = false;
            for (Map<String, Object> toolCall : toolCalls) {
                if (shouldStop(sessionId)) {
                    finalResult.setFinalSummary("任务已被用户强制停止。");
                    sendProgress(session, "agent_stop", finalResult.getFinalSummary(), System.currentTimeMillis() - start);
                    return finalResult;
                }

                String toolCallId = String.valueOf(toolCall.getOrDefault("id", ""));
                String toolName = String.valueOf(toolCall.getOrDefault("name", ""));
                @SuppressWarnings("unchecked")
                Map<String, Object> args = (Map<String, Object>) toolCall.getOrDefault("arguments", new HashMap<>());

                if ("finish_task".equals(toolName)) {
                    fillFinalResult(finalResult, args, assistantContent, metricsRequested);

                    Map<String, Object> toolMsg = new LinkedHashMap<>();
                    toolMsg.put("role", "tool");
                    toolMsg.put("tool_call_id", toolCallId);
                    toolMsg.put("name", "finish_task");
                    toolMsg.put("content", "任务已由 Agent 标记完成。");
                    messages.add(toolMsg);

                    sendProgress(session, "agent_finish", finalResult.getFinalSummary(), System.currentTimeMillis() - start);
                    shouldBreak = true;
                    break;
                }

                if ("execute_command".equals(toolName)) {
                    String rawCommand = String.valueOf(args.getOrDefault("command", "")).trim();
                    if (!StringUtils.hasText(rawCommand)) {
                        appendToolMessage(messages, toolCallId, "execute_command", "Error: command 为空，无法执行。");
                        sendProgress(session, "cmd_skip", "AI 未提供有效命令，已跳过。", System.currentTimeMillis() - start);
                        continue;
                    }

                    if (isHighRiskCommand(rawCommand)
                            && (!StringUtils.hasText(approvedRiskCommand) || !rawCommand.equals(approvedRiskCommand))) {
                        throw new HighRiskCommandException(rawCommand, "检测到高风险命令，需要用户确认后再执行。", messages);
                    }

                    String safeCommand = injectSudoPassword(rawCommand, password);
                    sendProgress(session, "cmd_exec_start", "执行命令: " + safeCommand, System.currentTimeMillis() - start);

                    SshUtils.SshResult execResult = execWithTimeout(serverIp, username, password, safeCommand, SSH_TIMEOUT_SECONDS);
                    appendToolMessage(messages, toolCallId, "execute_command", execResult.output());

                    String preview = execResult.output() == null ? "" : execResult.output();
                    if (preview.length() > 1200) {
                        preview = preview.substring(0, 1200) + "\n...(输出已截断)";
                    }
                    
                    if (execResult.exitCode() != 0) {
                        try {
                            Map<String, Object> payload = new HashMap<>();
                            payload.put("type", "ops_progress");
                            payload.put("stage", "cmd_exec_fail");
                            payload.put("message", preview);
                            payload.put("command", rawCommand);
                            payload.put("elapsedMs", System.currentTimeMillis() - start);
                            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
                        } catch (Exception e) {
                            log.warn("Failed to send command failure progress: {}", e.getMessage());
                        }
                    } else {
                        sendProgress(session, "cmd_exec_done", preview, System.currentTimeMillis() - start);
                    }
                }

                if ("get_server_metrics".equals(toolName)) {
                    metricsRequested = true;
                    String targetIp = String.valueOf(args.getOrDefault("serverIp", serverIp)).trim();
                    String timeRange = String.valueOf(args.getOrDefault("timeRange", "30m")).trim();
                    if (!StringUtils.hasText(targetIp)) {
                        targetIp = serverIp;
                    }
                    if (!StringUtils.hasText(timeRange)) {
                        timeRange = "30m";
                    }

                    sendProgress(session, "metrics_fetch_start",
                            "开始获取监控数据: ip=" + targetIp + ", range=" + timeRange,
                            System.currentTimeMillis() - start);
                    try {
                        Map<String, Object> metrics = monitorService.getMetrics(targetIp, timeRange);

                        // history 为空时，自动执行一次即时采样，再重新取数返回给 AI
                        int historyPoints = toInt(metrics.get("historyPoints"));
                        if (historyPoints <= 0) {
                            sendProgress(session, "metrics_empty_sample_once",
                                    "历史监控为空，自动执行一次即时采样...",
                                    System.currentTimeMillis() - start);
                            Map<String, Object> sampleResult = monitorService.sampleMetricsOnce(targetIp, username, password);
                            metrics.put("sampleOnceWhenEmpty", sampleResult);
                            metrics = monitorService.getMetrics(targetIp, timeRange);
                            metrics.put("sampleOnceWhenEmpty", sampleResult);
                        }

                        String metricsJson = objectMapper.writeValueAsString(metrics);
                        appendToolMessage(messages, toolCallId, "get_server_metrics", metricsJson);

                        String preview = metricsJson.length() > 600
                                ? metricsJson.substring(0, 600) + "...(监控数据已截断)"
                                : metricsJson;
                        sendProgress(session, "metrics_fetch_done", preview, System.currentTimeMillis() - start);
                    } catch (Exception e) {
                        appendToolMessage(messages, toolCallId, "get_server_metrics", "Error: " + e.getMessage());
                        sendProgress(session, "metrics_fetch_fail",
                                "获取监控数据失败: " + e.getMessage(),
                                System.currentTimeMillis() - start);
                    }
                }
            }

            if (shouldBreak) {
                break;
            }
        }

        if (!StringUtils.hasText(finalResult.getFinalSummary())) {
            if (shouldStop(sessionId)) {
                finalResult.setFinalSummary("任务已被用户强制停止。");
                sendProgress(session, "agent_stop", finalResult.getFinalSummary(), System.currentTimeMillis() - start);
            } else {
                finalResult.setFinalSummary("达到最大循环轮次(" + maxRounds + ")，任务未显式 finish_task，已强制结束。");
                // 抛出超时异常，携带历史记录以便后续恢复
                throw new AgentTimeoutException(finalResult.getFinalSummary(), messages);
            }
        }
        return finalResult;
        } finally {
            stopFlags.remove(sessionId);
        }
    }

    private void fillFinalResult(AgentRunResult finalResult,
                                 Map<String, Object> args,
                                 String assistantContent,
                                 boolean metricsRequested) {
        String summary = String.valueOf(args.getOrDefault("final_summary", assistantContent));
        if (!StringUtils.hasText(summary)) {
            summary = "任务已结束。";
        }
        finalResult.setFinalSummary(summary);

        boolean chartSuggest = args.containsKey("chart_suggest")
                ? toBoolean(args.get("chart_suggest"))
                : metricsRequested && containsChartSignal(summary);

        String chartTemplate = normalizeChartTemplate(String.valueOf(args.getOrDefault("chart_template", DEFAULT_CHART_TEMPLATE)));
        String chartTimeRange = normalizeChartTimeRange(String.valueOf(args.getOrDefault("chart_time_range", DEFAULT_CHART_RANGE)));
        String chartReason = String.valueOf(args.getOrDefault("chart_reason", ""));
        String chartTitle = String.valueOf(args.getOrDefault("chart_title", ""));

        if (!StringUtils.hasText(chartReason)) {
            chartReason = chartSuggest ? "结果包含趋势、峰值或异常信息，适合补充图表展示。" : "文本总结已足够表达处理结果，无需额外图表。";
        }
        if (!StringUtils.hasText(chartTitle)) {
            chartTitle = defaultChartTitle(chartTemplate, chartTimeRange);
        }

        finalResult.setChartSuggest(chartSuggest);
        finalResult.setChartReason(chartReason);
        finalResult.setChartTimeRange(chartTimeRange);
        finalResult.setChartTemplate(chartTemplate);
        finalResult.setChartTitle(chartTitle);
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "true".equalsIgnoreCase(String.valueOf(value));
    }

    private boolean containsChartSignal(String text) {
        String content = String.valueOf(text).toLowerCase();
        return content.contains("cpu")
                || content.contains("内存")
                || content.contains("负载")
                || content.contains("趋势")
                || content.contains("波动")
                || content.contains("峰值")
                || content.contains("异常")
                || content.contains("监控");
    }

    private String normalizeChartTimeRange(String timeRange) {
        if (!StringUtils.hasText(timeRange)) {
            return DEFAULT_CHART_RANGE;
        }
        String normalized = timeRange.trim().toLowerCase();
        return SUPPORTED_CHART_RANGES.contains(normalized) ? normalized : DEFAULT_CHART_RANGE;
    }

    private String normalizeChartTemplate(String chartTemplate) {
        if (!StringUtils.hasText(chartTemplate)) {
            return DEFAULT_CHART_TEMPLATE;
        }
        String normalized = chartTemplate.trim().toLowerCase();
        return SUPPORTED_CHART_TEMPLATES.contains(normalized) ? normalized : DEFAULT_CHART_TEMPLATE;
    }

    private static String defaultChartTitle(String chartTemplate, String timeRange) {
        String prefix;
        switch (chartTemplate) {
            case "cpu_mem_trend":
                prefix = "CPU / 内存趋势图";
                break;
            case "anomaly_timeline":
                prefix = "异常时序图";
                break;
            case "health_score_radar":
                prefix = "健康评分雷达图";
                break;
            default:
                prefix = "服务器健康总览";
                break;
        }
        return prefix + "（" + (StringUtils.hasText(timeRange) ? timeRange : DEFAULT_CHART_RANGE) + "）";
    }

    public SshUtils.SshResult executeApprovedRiskCommand(String serverIp,
                                             String username,
                                             String password,
                                             String command) {
        String safeCommand = injectSudoPassword(command, password);
        return execWithTimeout(serverIp, username, password, safeCommand, SSH_TIMEOUT_SECONDS);
    }

    public boolean isHighRiskCommand(String command) {
        if (!StringUtils.hasText(command)) {
            return false;
        }
        return HIGH_RISK_PATTERNS.stream().anyMatch(p -> p.matcher(command).find());
    }

    private Map<String, Object> systemMessage() {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "system");
        msg.put("content",
                "你是一名资深 Linux 运维 Agent。你必须遵守：\n"
                        + "1) 所有可执行动作必须通过工具 execute_command，不要假装执行。\n"
                        + "2) 包管理命令必须是非交互式：apt/yum/dnf 等要加 -y 或等价非交互参数。\n"
                        + "3) 遇到错误要基于错误信息自动修复并继续，不要立即放弃。\n"
                        + "4) 最终完成任务时必须调用 finish_task，并一次性给出 final_summary、chart_suggest、chart_reason、chart_time_range、chart_template、chart_title。\n"
                        + "5) 每轮只做必要动作，优先先检查再变更，避免危险命令。\n"
                        + "6) 如果你需要分析服务器状态，请先调用 get_server_metrics 获取真实数据，然后基于真实数据给出专业的运维分析报告。\n"
                        + "7) 只有当真实数据中存在趋势、峰值、波动、异常或评分对比价值时，才将 chart_suggest 设为 true；否则设为 false。\n"
                        + "8) chart_template 只能是 health_overview、cpu_mem_trend、anomaly_timeline、health_score_radar 之一；chart_time_range 只能是 30m、1h、2h 之一。");
        return msg;
    }

    private List<Map<String, Object>> buildTools() {
        List<Map<String, Object>> tools = new ArrayList<>();

        Map<String, Object> executeParams = new LinkedHashMap<>();
        executeParams.put("type", "object");
        Map<String, Object> executeProps = new LinkedHashMap<>();
        executeProps.put("command", Map.of(
                "type", "string",
                "description", "需要在目标服务器执行的 Linux 命令"));
        executeParams.put("properties", executeProps);
        executeParams.put("required", List.of("command"));

        Map<String, Object> executeFn = new LinkedHashMap<>();
        executeFn.put("name", "execute_command");
        executeFn.put("description", "在目标服务器执行命令，并返回 stdout/stderr。");
        executeFn.put("parameters", executeParams);
        tools.add(Map.of("type", "function", "function", executeFn));

        Map<String, Object> finishParams = new LinkedHashMap<>();
        finishParams.put("type", "object");
        Map<String, Object> finishProps = new LinkedHashMap<>();
        finishProps.put("final_summary", Map.of(
                "type", "string",
                "description", "任务完成后的最终总结"));
        finishProps.put("chart_suggest", Map.of(
                "type", "boolean",
                "description", "是否建议前端再请求生成图表"));
        finishProps.put("chart_reason", Map.of(
                "type", "string",
                "description", "建议或不建议生成图表的原因"));
        finishProps.put("chart_time_range", Map.of(
                "type", "string",
                "description", "推荐图表时间范围，只能是 30m、1h、2h"));
        finishProps.put("chart_template", Map.of(
                "type", "string",
                "description", "推荐图表模板，只能是 health_overview、cpu_mem_trend、anomaly_timeline、health_score_radar"));
        finishProps.put("chart_title", Map.of(
                "type", "string",
                "description", "推荐图表标题"));
        finishParams.put("properties", finishProps);
        finishParams.put("required", List.of(
                "final_summary",
                "chart_suggest",
                "chart_reason",
                "chart_time_range",
                "chart_template",
                "chart_title"
        ));

        Map<String, Object> finishFn = new LinkedHashMap<>();
        finishFn.put("name", "finish_task");
        finishFn.put("description", "任务完成时调用，标记循环结束，并同步返回图表建议。\n只有在图表确有展示价值时才将 chart_suggest 设为 true。");
        finishFn.put("parameters", finishParams);
        tools.add(Map.of("type", "function", "function", finishFn));

        Map<String, Object> metricsParams = new LinkedHashMap<>();
        metricsParams.put("type", "object");
        Map<String, Object> metricsProps = new LinkedHashMap<>();
        metricsProps.put("serverIp", Map.of(
                "type", "string",
                "description", "目标服务器 IP，建议传入当前会话服务器"));
        metricsProps.put("timeRange", Map.of(
                "type", "string",
                "description", "时间范围，如 30m、1h、2h"));
        metricsParams.put("properties", metricsProps);
        metricsParams.put("required", List.of("serverIp", "timeRange"));

        Map<String, Object> metricsFn = new LinkedHashMap<>();
        metricsFn.put("name", "get_server_metrics");
        metricsFn.put("description", "获取指定服务器最近的 CPU 和内存负载监控数据，用于分析服务器健康状态。");
        metricsFn.put("parameters", metricsParams);
        tools.add(Map.of("type", "function", "function", metricsFn));

        return tools;
    }

    /**
     * 如果命令包含 sudo，自动改写为 `echo "password" | sudo -S ...`，避免交互阻塞。
     */
    private String injectSudoPassword(String command, String password) {
        if (!StringUtils.hasText(command) || !command.contains("sudo")) {
            return command;
        }

        String escapedPwd = password == null ? "" : password.replace("\"", "\\\"");
        String withoutSudo = command.replaceAll("\\bsudo\\s+", "").trim();
        return "echo \"" + escapedPwd + "\" | sudo -S " + withoutSudo;
    }

    private int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private SshUtils.SshResult execWithTimeout(String serverIp,
                                   String username,
                                   String password,
                                   String command,
                                   long timeoutSeconds) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<SshUtils.SshResult> future = executor.submit(() -> sshUtils.execWithResult(serverIp, username, password, command));
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return new SshUtils.SshResult(-1, "SSH Error: 命令执行超时(" + timeoutSeconds + "s)，已中断。");
        } catch (Exception e) {
            return new SshUtils.SshResult(-1, "SSH Error: " + e.getMessage());
        } finally {
            executor.shutdownNow();
        }
    }

    private void appendToolMessage(List<Map<String, Object>> messages, String toolCallId, String toolName, String content) {
        Map<String, Object> toolMsg = new LinkedHashMap<>();
        toolMsg.put("role", "tool");
        toolMsg.put("tool_call_id", toolCallId);
        toolMsg.put("name", toolName);
        toolMsg.put("content", content == null ? "" : content);
        messages.add(toolMsg);
    }

    private void sendProgress(WebSocketSession session, String stage, String message, long elapsedMs) {
        try {
            if (session == null || !session.isOpen()) {
                return;
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "ops_progress");
            payload.put("stage", stage);
            payload.put("message", message);
            payload.put("elapsedMs", elapsedMs);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        } catch (Exception e) {
            log.warn("Agent progress push failed: {}", e.getMessage());
        }
    }

    @Getter
    public static class HighRiskCommandException extends RuntimeException {
        private final String command;
        private final String reason;
        private final List<Map<String, Object>> history;

        public HighRiskCommandException(String command, String reason, List<Map<String, Object>> history) {
            super(reason);
            this.command = command;
            this.reason = reason;
            this.history = history;
        }
    }

    @Getter
    public static class AgentTimeoutException extends RuntimeException {
        private final List<Map<String, Object>> history;

        public AgentTimeoutException(String message, List<Map<String, Object>> history) {
            super(message);
            this.history = history;
        }
    }
}
