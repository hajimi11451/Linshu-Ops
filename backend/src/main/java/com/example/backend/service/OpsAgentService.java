package com.example.backend.service;

import com.example.backend.utils.AiUtils;
import com.example.backend.utils.SshUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
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

    private static final int MAX_AGENT_ROUNDS = 15;
    private static final long SSH_TIMEOUT_SECONDS = 60;

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

    public String runAgentLoop(String userQuery,
                               String serverIp,
                               String username,
                               String password,
                               WebSocketSession session) {
        return runAgentLoop(userQuery, serverIp, username, password, null, session);
    }

    /**
     * 动态自主 Agent 主循环。
     * @param approvedRiskCommand 为空表示未确认高风险命令；非空表示允许执行该命令。
     */
    public String runAgentLoop(String userQuery,
                               String serverIp,
                               String username,
                               String password,
                               String approvedRiskCommand,
                               WebSocketSession session) {
        long start = System.currentTimeMillis();
        String finalSummary = "";

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(systemMessage());

        Map<String, Object> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", "用户目标: " + (userQuery == null ? "" : userQuery)
                + "\n目标服务器: " + serverIp
                + "\n请你自主分析并逐步执行，完成后必须调用 finish_task。");
        messages.add(userMsg);

        List<Map<String, Object>> tools = buildTools();
        sendProgress(session, "agent_start", "进入 Agent 自主循环", System.currentTimeMillis() - start);

        for (int round = 1; round <= MAX_AGENT_ROUNDS; round++) {
            sendProgress(session, "agent_think", "第 " + round + " 轮：AI 正在决策下一步", System.currentTimeMillis() - start);

            Map<String, Object> aiResp = aiUtils.callQianfanApiWithTools(messages, tools);
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

            boolean shouldStop = false;
            for (Map<String, Object> toolCall : toolCalls) {
                String toolCallId = String.valueOf(toolCall.getOrDefault("id", ""));
                String toolName = String.valueOf(toolCall.getOrDefault("name", ""));
                @SuppressWarnings("unchecked")
                Map<String, Object> args = (Map<String, Object>) toolCall.getOrDefault("arguments", new HashMap<>());

                if ("finish_task".equals(toolName)) {
                    finalSummary = String.valueOf(args.getOrDefault("final_summary", assistantContent));
                    if (!StringUtils.hasText(finalSummary)) {
                        finalSummary = "任务已结束。";
                    }

                    Map<String, Object> toolMsg = new LinkedHashMap<>();
                    toolMsg.put("role", "tool");
                    toolMsg.put("tool_call_id", toolCallId);
                    toolMsg.put("name", "finish_task");
                    toolMsg.put("content", "任务已由 Agent 标记完成。");
                    messages.add(toolMsg);

                    sendProgress(session, "agent_finish", finalSummary, System.currentTimeMillis() - start);
                    shouldStop = true;
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
                        throw new HighRiskCommandException(rawCommand, "检测到高风险命令，需要用户确认后再执行。");
                    }

                    String safeCommand = injectSudoPassword(rawCommand, password);
                    sendProgress(session, "cmd_exec_start", "执行命令: " + safeCommand, System.currentTimeMillis() - start);

                    String execResult = execWithTimeout(serverIp, username, password, safeCommand, SSH_TIMEOUT_SECONDS);
                    appendToolMessage(messages, toolCallId, "execute_command", execResult);

                    String preview = execResult == null ? "" : execResult;
                    if (preview.length() > 1200) {
                        preview = preview.substring(0, 1200) + "\n...(输出已截断)";
                    }
                    sendProgress(session, "cmd_exec_done", preview, System.currentTimeMillis() - start);
                }

                if ("get_server_metrics".equals(toolName)) {
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

            if (shouldStop) {
                break;
            }
        }

        if (!StringUtils.hasText(finalSummary)) {
            finalSummary = "达到最大循环轮次(" + MAX_AGENT_ROUNDS + ")，任务未显式 finish_task，已强制结束。";
            sendProgress(session, "agent_timeout", finalSummary, System.currentTimeMillis() - start);
        }
        return finalSummary;
    }

    public String executeApprovedRiskCommand(String serverIp,
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
                        + "4) 最终完成任务时必须调用 finish_task，并在 final_summary 给出结果总结。\n"
                        + "5) 每轮只做必要动作，优先先检查再变更，避免危险命令。\n"
                        + "6) 如果你需要分析服务器状态，请先调用 get_server_metrics 获取真实数据，然后基于真实数据给出专业的运维分析报告。");
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
        finishParams.put("properties", finishProps);
        finishParams.put("required", List.of("final_summary"));

        Map<String, Object> finishFn = new LinkedHashMap<>();
        finishFn.put("name", "finish_task");
        finishFn.put("description", "任务完成时调用，标记循环结束。");
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

    private String execWithTimeout(String serverIp,
                                   String username,
                                   String password,
                                   String command,
                                   long timeoutSeconds) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit(() -> sshUtils.exec(serverIp, username, password, command));
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return "SSH Error: 命令执行超时(" + timeoutSeconds + "s)，已中断。";
        } catch (Exception e) {
            return "SSH Error: " + e.getMessage();
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

        public HighRiskCommandException(String command, String reason) {
            super(reason);
            this.command = command;
            this.reason = reason;
        }
    }
}
