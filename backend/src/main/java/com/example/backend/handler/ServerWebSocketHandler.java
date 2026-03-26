package com.example.backend.handler;

import com.example.backend.dto.MetricDTO;
import com.example.backend.model.TaskState;
import com.example.backend.service.MonitorService;
import com.example.backend.service.OpsAgentService;
import com.example.backend.utils.AiUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class ServerWebSocketHandler extends TextWebSocketHandler {

    private static final ConcurrentHashMap<String, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, TaskState> AGENT_HISTORY = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, FutureTask<Void>> RUNNING_TASKS = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService agentExecutor = Executors.newCachedThreadPool();

    @Autowired
    private AiUtils aiUtils;

    @Autowired
    private OpsAgentService opsAgentService;

    @Autowired
    private MonitorService monitorService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket connected: {}", session.getId());
        SESSIONS.put(session.getId(), session);

        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "welcome");
        msg.put("message", "Connection established. Send type=ops_chat for AI assistant.");
        sendJson(session, msg);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.info("WS recv [{}]: {}", session.getId(), payload);

        if ("ping".equalsIgnoreCase(payload)) {
            sendText(session, "pong");
            return;
        }

        try {
            JsonNode node = objectMapper.readTree(payload);
            String type = node.path("type").asText("");
            if ("ops_chat".equals(type)) {
                handleOpsChat(session, node);
                return;
            }
            if ("ops_force_stop".equals(type)) {
                handleOpsForceStop(session, node);
                return;
            }
            if ("risk_execute".equals(type)) {
                handleRiskExecute(session, node);
                return;
            }
            if ("chart_data_request".equals(type)) {
                handleChartDataRequest(session, node);
                return;
            }

            Map<String, Object> fallback = new HashMap<>();
            fallback.put("type", "error");
            fallback.put("message", "unsupported type, use type=ops_chat/risk_execute/chart_data_request");
            sendJson(session, fallback);
        } catch (Exception ex) {
            // plain text fallback: regular AI chat
            String answer = aiUtils.chatWithOpsAssistant(payload);
            Map<String, Object> data = new HashMap<>();
            data.put("type", "chat_result");
            data.put("reply", answer);
            sendJson(session, data);
        }
    }

    private void handleOpsChat(WebSocketSession session, JsonNode node) {
        String query = node.path("query").asText("");
        boolean execute = node.path("execute").asBoolean(false);
        int requestedMaxRounds = node.path("maxRounds").asInt(15);
        final int maxRounds = Math.min(50, Math.max(1, requestedMaxRounds));

        String serverIp = node.path("serverIp").asText("");
        String username = node.path("username").asText("");
        String password = node.path("password").asText("");

        submitSessionTask(session, "ops_chat",
                () -> routeOpsChat(session, query, execute, maxRounds, serverIp, username, password));
    }

    private void routeOpsChat(WebSocketSession session,
                              String query,
                              boolean executeRequested,
                              int maxRounds,
                              String serverIp,
                              String username,
                              String password) {
        long start = System.currentTimeMillis();
        sendProgress(session, "start", "收到请求，开始分析问题...", 0);

        if (isContinueQuery(query)) {
            sendProgress(session, "intent_result", "识别到这是继续执行请求，我会直接恢复之前的任务。", System.currentTimeMillis() - start);
            processOpsChat(session, query, maxRounds, serverIp, username, password);
            return;
        }

        boolean hasConnection = hasCompleteConnection(serverIp, username, password);
        sendProgress(session, "intent_detecting", "我先判断这次更像是咨询还是执行请求。", System.currentTimeMillis() - start);
        Map<String, String> intentDecision = aiUtils.classifyOpsIntent(query, hasConnection);
        String intent = normalizeIntent(String.valueOf(intentDecision.get("intent")));
        String reason = String.valueOf(intentDecision.getOrDefault("reason", ""));
        sendProgress(session, "intent_result",
                buildIntentResultMessage(intent, hasConnection, executeRequested, reason),
                System.currentTimeMillis() - start);

        if ("execute".equals(intent) && executeRequested && hasConnection) {
            processOpsChat(session, query, maxRounds, serverIp, username, password);
            return;
        }

        processChatOnly(
                session,
                query,
                buildChatOnlyInstruction(intent, hasConnection),
                buildChatOnlyExecResult(intent, hasConnection)
        );
    }

    private void processChatOnly(WebSocketSession session,
                                 String query,
                                 String extraInstruction,
                                 String execResultMessage) {
        long start = System.currentTimeMillis();
        log.info("ops_chat start, sessionId={}, chatOnly=true, queryLength={}",
                session.getId(), query == null ? 0 : query.length());

        Map<String, Object> result = new HashMap<>();
        result.put("type", "ops_chat_result");
        result.put("query", query);
        result.put("chatOnly", true);
        result.put("executed", false);
        result.put("chartSuggest", false);

        try {
            String answer = aiUtils.chatWithOpsAssistant(query, extraInstruction);
            result.put("reply", answer);
            result.put("execResult", execResultMessage);
        } catch (Exception e) {
            log.error("ops_chat chat-only failed, sessionId={}", session.getId(), e);
            result.put("reply", "当前问题处理失败，请稍后重试。");
            result.put("execResult", e.getMessage());
        }

        sendJson(session, result);
        sendProgress(session, "finished", "流程结束", System.currentTimeMillis() - start);
    }

    private void processOpsChat(WebSocketSession session,
                                String query,
                                int maxRounds,
                                String serverIp,
                                String username,
                                String password) {
        long start = System.currentTimeMillis();
        log.info("ops_chat start, sessionId={}, execute=true, serverIp={}, queryLength={}, maxRounds={}",
                session.getId(), serverIp, query == null ? 0 : query.length(), maxRounds);

        Map<String, Object> result = new HashMap<>();
        result.put("type", "ops_chat_result");
        result.put("query", query);

        // 如果用户发送“继续”，且有历史记录，则恢复执行
        if ("继续".equals(query) || "continue".equalsIgnoreCase(query)) {
            TaskState history = AGENT_HISTORY.get(session.getId());
            if (history != null) {
                log.info("Resuming agent loop from history, sessionId={}", session.getId());
                try {
                    OpsAgentService.AgentRunResult agentResult = opsAgentService.runAgentLoopWithAdvice(
                            "[继续执行]", serverIp, username, password, maxRounds, null, session, history
                    );
                    if (agentResult.isStopped()) {
                        result.put("executed", false);
                        result.put("stopped", true);
                        result.put("reply", agentResult.getFinalSummary());
                        result.put("execResult", "任务已被强制停止。");
                        result.put("chartSuggest", false);
                        sendJson(session, result);
                        return;
                    }
                    result.put("executed", true);
                    result.put("reply", agentResult.getFinalSummary());
                    result.put("execResult", "Agent 循环已完成。");
                    mergeChartAdvice(result, agentResult);
                    AGENT_HISTORY.remove(session.getId());
                } catch (OpsAgentService.HighRiskCommandException e) {
                    AGENT_HISTORY.put(session.getId(), e.getTaskState());
                    result.put("executed", false);
                    result.put("needRiskConfirm", true);
                    result.put("riskLevel", "high");
                    result.put("riskCommand", e.getCommand());
                    result.put("reply", e.getReason());
                    result.put("execResult", "检测到高风险命令，等待用户确认。");
                    result.put("chartSuggest", false);
                } catch (OpsAgentService.AgentTimeoutException e) {
                    AGENT_HISTORY.put(session.getId(), e.getTaskState());
                    result.put("executed", false);
                    result.put("timeout", true);
                    result.put("reply", e.getMessage());
                    result.put("execResult", "再次达到最大轮数，任务暂停。");
                    result.put("chartSuggest", false);
                    sendProgress(session, "agent_timeout", e.getMessage(), System.currentTimeMillis() - start);
                } catch (Exception e) {
                    if (Thread.currentThread().isInterrupted()) {
                        result.put("executed", false);
                        result.put("stopped", true);
                        result.put("reply", "任务已被强制停止。");
                        result.put("execResult", "任务已中断。");
                        result.put("chartSuggest", false);
                        sendJson(session, result);
                        return;
                    }
                    log.error("Resumed agent loop failed", e);
                    result.put("executed", false);
                    result.put("reply", "恢复执行失败: " + e.getMessage());
                    result.put("execResult", e.getMessage());
                    result.put("chartSuggest", false);
                }
                sendJson(session, result);
                sendProgress(session, "finished", "流程结束", System.currentTimeMillis() - start);
                return;
            }
        }

        if (!StringUtils.hasText(serverIp) || !StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            result.put("executed", false);
            result.put("reply", "缺少 serverIp/username/password，无法启动 Agent。");
            result.put("execResult", "参数不足。");
            result.put("chartSuggest", false);
            sendJson(session, result);
            sendProgress(session, "finished", "流程结束", System.currentTimeMillis() - start);
            return;
        }

        try {
            AGENT_HISTORY.remove(session.getId());
            OpsAgentService.AgentRunResult agentResult = opsAgentService.runAgentLoopWithAdvice(query, serverIp, username, password, maxRounds, null, session);
            if (agentResult.isStopped()) {
                result.put("executed", false);
                result.put("stopped", true);
                result.put("reply", agentResult.getFinalSummary());
                result.put("execResult", "任务已被强制停止。");
                result.put("chartSuggest", false);
                sendJson(session, result);
                return;
            }
            result.put("executed", true);
            result.put("reply", agentResult.getFinalSummary());
            result.put("execResult", "Agent 循环已完成，请参考上方实时进度日志。");
            mergeChartAdvice(result, agentResult);
            AGENT_HISTORY.remove(session.getId());
        } catch (OpsAgentService.HighRiskCommandException e) {
            AGENT_HISTORY.put(session.getId(), e.getTaskState());
            result.put("executed", false);
            result.put("needRiskConfirm", true);
            result.put("riskLevel", "high");
            result.put("riskCommand", e.getCommand());
            result.put("reply", e.getReason());
            result.put("execResult", "检测到高风险命令，等待用户确认。");
            result.put("chartSuggest", false);
        } catch (OpsAgentService.AgentTimeoutException e) {
            AGENT_HISTORY.put(session.getId(), e.getTaskState());
            result.put("executed", false);
            result.put("timeout", true);
            result.put("reply", e.getMessage());
            result.put("execResult", "达到最大轮数，任务暂停。你可以发送“继续”来恢复执行。");
            result.put("chartSuggest", false);
            sendProgress(session, "agent_timeout", e.getMessage(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            if (Thread.currentThread().isInterrupted()) {
                result.put("executed", false);
                result.put("stopped", true);
                result.put("reply", "任务已被强制停止。");
                result.put("execResult", "任务已中断。");
                result.put("chartSuggest", false);
                sendJson(session, result);
                return;
            }
            log.error("ops_chat agent loop failed, sessionId={}", session.getId(), e);
            result.put("executed", false);
            result.put("reply", "Agent 执行失败");
            result.put("execResult", e.getMessage());
            result.put("chartSuggest", false);
        }

        sendJson(session, result);
        sendProgress(session, "finished", "流程结束", System.currentTimeMillis() - start);
        log.info("ops_chat finished, sessionId={}, totalElapsedMs={}", session.getId(), System.currentTimeMillis() - start);
    }

    private boolean hasCompleteConnection(String serverIp, String username, String password) {
        return StringUtils.hasText(serverIp) && StringUtils.hasText(username) && StringUtils.hasText(password);
    }

    private boolean isContinueQuery(String query) {
        return "继续".equals(query) || "continue".equalsIgnoreCase(query);
    }

    private String normalizeIntent(String intent) {
        if (!StringUtils.hasText(intent)) {
            return "chat";
        }
        String normalized = intent.trim().toLowerCase();
        if ("execute".equals(normalized) || "ambiguous".equals(normalized) || "chat".equals(normalized)) {
            return normalized;
        }
        return "chat";
    }

    private String buildIntentResultMessage(String intent,
                                            boolean hasConnection,
                                            boolean executeRequested,
                                            String reason) {
        String normalizedReason = StringUtils.hasText(reason) ? reason.trim() : "";

        if ("execute".equals(intent) && executeRequested && hasConnection) {
            return appendIntentReason("判断结果：这是执行请求，我会先整理执行计划，再开始实际处理。", normalizedReason);
        }
        if ("execute".equals(intent)) {
            return appendIntentReason("判断结果：这是执行请求，但当前无法直接执行，我先给你处理方案，不操作服务器。", normalizedReason);
        }
        if ("ambiguous".equals(intent)) {
            return appendIntentReason("判断结果：这次表达还有些模糊，我先按咨询处理，不直接操作服务器。", normalizedReason);
        }
        return appendIntentReason("判断结果：这次更像是在咨询或提问，我会直接回答，不操作服务器。", normalizedReason);
    }

    private String appendIntentReason(String prefix, String reason) {
        if (!StringUtils.hasText(reason)) {
            return prefix;
        }
        return prefix + " " + reason;
    }

    private String buildChatOnlyInstruction(String intent, boolean hasConnection) {
        if ("execute".equals(intent) && !hasConnection) {
            return "这次更像是执行型请求，但当前没有完整的服务器连接信息。不要假装已经执行。"
                    + "请直接给出可落地的排查/处理方案、推荐命令、风险和回滚建议，并提醒用户补充连接后可以再执行。";
        }
        if ("ambiguous".equals(intent)) {
            return "这次用户表达存在歧义，默认按咨询处理。不要假装已经执行服务器操作。"
                    + "先给出分析和建议，并在结尾简短提醒：如果需要你直接动服务器，可以明确说“请执行/请检查当前服务器…”。";
        }
        if (hasConnection) {
            return "虽然当前已经选择了服务器连接，但这次问题判断为咨询型问题。"
                    + "请直接回答，不要假装已经登录服务器、查看日志或执行命令。";
        }
        return "当前按纯问答处理。不要假装已经登录服务器或执行命令；如需举例命令，请明确说明那只是建议。";
    }

    private String buildChatOnlyExecResult(String intent, boolean hasConnection) {
        if ("execute".equals(intent) && !hasConnection) {
            return "判断为执行请求，但缺少完整连接信息，未执行服务器操作。";
        }
        if ("ambiguous".equals(intent)) {
            return "执行意图不够明确，已按咨询处理，未执行服务器操作。";
        }
        if (hasConnection) {
            return "判断为咨询问题，未执行服务器操作。";
        }
        return "当前没有服务器连接，已按纯问答处理。";
    }

    private void handleOpsForceStop(WebSocketSession session, JsonNode node) {
        log.info("ops_force_stop received, sessionId={}", session.getId());

        opsAgentService.stopAgent(session.getId());
        FutureTask<Void> runningTask = RUNNING_TASKS.get(session.getId());
        if (runningTask != null) {
            runningTask.cancel(true);
        }
        AGENT_HISTORY.remove(session.getId());

        Map<String, Object> progress = new HashMap<>();
        progress.put("type", "ops_progress");
        progress.put("stage", "agent_stopped");
        progress.put("message", "已收到强制停止请求，当前任务将尽快终止。");
        sendJson(session, progress);

        Map<String, Object> result = new HashMap<>();
        result.put("type", "ops_force_stop_result");
        result.put("message", "已发送强制停止请求，当前任务将尽快终止。");
        sendJson(session, result);
    }

    /**
     * 用户点击高风险确认后的执行入口。
     */
    private void handleRiskExecute(WebSocketSession session, JsonNode node) {
        String command = node.path("command").asText("");
        String serverIp = node.path("serverIp").asText("");
        String username = node.path("username").asText("");
        String password = node.path("password").asText("");

        if (!StringUtils.hasText(command)) {
            Map<String, Object> result = new HashMap<>();
            result.put("type", "ops_chat_result");
            result.put("query", "[高风险确认执行]");
            result.put("executed", false);
            result.put("reply", "缺少命令，无法执行。");
            result.put("execResult", "risk_execute.command 为空");
            sendJson(session, result);
            return;
        }

        if (!StringUtils.hasText(serverIp) || !StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            Map<String, Object> result = new HashMap<>();
            result.put("type", "ops_chat_result");
            result.put("query", "[高风险确认执行]");
            result.put("executed", false);
            result.put("reply", "缺少 serverIp/username/password，无法执行。 ");
            result.put("execResult", "参数不足。");
            sendJson(session, result);
            return;
        }
        submitSessionTask(session, "risk_execute", () -> processRiskExecute(session, command, serverIp, username, password));
    }

    private void processRiskExecute(WebSocketSession session,
                                    String command,
                                    String serverIp,
                                    String username,
                                    String password) {
        long start = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();
        result.put("type", "ops_chat_result");
        result.put("query", "[高风险确认执行]");

        try {
            sendProgress(session, "risk_exec_start", "用户已确认，开始执行高风险命令...", System.currentTimeMillis() - start);

            TaskState history = AGENT_HISTORY.remove(session.getId());

            OpsAgentService.AgentRunResult agentResult = opsAgentService.runAgentLoopWithAdvice(
                    "[高风险确认继续执行]",
                    serverIp,
                    username,
                    password,
                    15,
                    command,
                    session,
                    history
            );

            if (agentResult.isStopped()) {
                result.put("executed", false);
                result.put("stopped", true);
                result.put("reply", agentResult.getFinalSummary());
                result.put("execResult", "任务已被强制停止。");
                result.put("chartSuggest", false);
                sendJson(session, result);
                return;
            }

            result.put("executed", true);
            result.put("reply", agentResult.getFinalSummary());
            result.put("execResult", "高风险命令执行并后续流程已完成。");
            mergeChartAdvice(result, agentResult);
            sendProgress(session, "risk_exec_done", "高风险命令及后续流程执行完成", System.currentTimeMillis() - start);
        } catch (OpsAgentService.HighRiskCommandException e) {
            AGENT_HISTORY.put(session.getId(), e.getTaskState());
            result.put("executed", false);
            result.put("needRiskConfirm", true);
            result.put("riskLevel", "high");
            result.put("riskCommand", e.getCommand());
            result.put("reply", e.getReason());
            result.put("execResult", "再次检测到高风险命令，等待用户确认。");
            result.put("chartSuggest", false);
        } catch (OpsAgentService.AgentTimeoutException e) {
            AGENT_HISTORY.put(session.getId(), e.getTaskState());
            result.put("executed", false);
            result.put("timeout", true);
            result.put("reply", e.getMessage());
            result.put("execResult", "达到最大轮数，任务暂停。你可以发送“继续”来恢复执行。");
            result.put("chartSuggest", false);
            sendProgress(session, "agent_timeout", e.getMessage(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            if (Thread.currentThread().isInterrupted()) {
                result.put("executed", false);
                result.put("stopped", true);
                result.put("reply", "任务已被强制停止。");
                result.put("execResult", "任务已中断。");
                result.put("chartSuggest", false);
                sendJson(session, result);
                return;
            }
            log.error("risk_execute failed, sessionId={}", session.getId(), e);
            result.put("executed", false);
            result.put("reply", "高风险命令执行失败");
            result.put("execResult", e.getMessage());
            result.put("chartSuggest", false);
        }

        sendJson(session, result);
    }

    private void submitSessionTask(WebSocketSession session, String taskType, Runnable task) {
        String sessionId = session.getId();

        while (true) {
            FutureTask<Void> existingTask = RUNNING_TASKS.get(sessionId);
            if (existingTask != null) {
                Map<String, Object> result = new HashMap<>();
                result.put("type", "ops_chat_result");
                result.put("executed", false);
                result.put("reply", "当前已有任务在运行或正在停止，请稍后再试。");
                result.put("execResult", taskType + " 被拒绝：当前会话已有任务占用。");
                result.put("chartSuggest", false);
                sendJson(session, result);
                return;
            }

            AtomicReference<FutureTask<Void>> taskRef = new AtomicReference<>();
            FutureTask<Void> futureTask = new FutureTask<>(() -> {
                try {
                    task.run();
                } catch (RuntimeException e) {
                    log.error("{} failed unexpectedly, sessionId={}", taskType, sessionId, e);
                    Map<String, Object> result = new HashMap<>();
                    result.put("type", "ops_chat_result");
                    result.put("executed", false);
                    result.put("reply", "后台任务执行失败。");
                    result.put("execResult", e.getMessage());
                    result.put("chartSuggest", false);
                    sendJson(session, result);
                } finally {
                    RUNNING_TASKS.remove(sessionId, taskRef.get());
                }
                return null;
            });
            taskRef.set(futureTask);

            if (RUNNING_TASKS.putIfAbsent(sessionId, futureTask) == null) {
                agentExecutor.submit(futureTask);
                return;
            }
        }
    }

    /**
     * 前端点击“生成图表”后请求图表数据。
     */
    private void handleChartDataRequest(WebSocketSession session, JsonNode node) {
        String serverIp = node.path("serverIp").asText("");
        String username = node.path("username").asText("");
        String password = node.path("password").asText("");
        String timeRange = node.path("timeRange").asText("1h");
        String chartTemplate = node.path("chartTemplate").asText("health_overview");
        String chartTitle = node.path("chartTitle").asText("");

        Map<String, Object> result = new HashMap<>();
        result.put("type", "chart_data_result");
        result.put("success", false);

        if (!StringUtils.hasText(serverIp)) {
            result.put("message", "缺少 serverIp，无法生成图表。");
            sendJson(session, result);
            return;
        }

        try {
            Map<String, Object> metrics = monitorService.getMetrics(serverIp, timeRange);
            int historyPoints = 0;
            try {
                historyPoints = Integer.parseInt(String.valueOf(metrics.get("historyPoints")));
            } catch (Exception ignored) {
            }

            if (historyPoints <= 0 && StringUtils.hasText(username) && StringUtils.hasText(password)) {
                Map<String, Object> sampleResult = monitorService.sampleMetricsOnce(serverIp, username, password);
                metrics = monitorService.getMetrics(serverIp, timeRange);
                metrics.put("sampleOnceWhenEmpty", sampleResult);
            }

            result.put("success", true);
            result.put("message", "图表数据已生成。");
            result.put("chartTemplate", chartTemplate);
            result.put("chartData", buildChartPayload(chartTemplate, chartTitle, metrics));
            sendJson(session, result);
        } catch (Exception e) {
            result.put("message", "生成图表数据失败: " + e.getMessage());
            sendJson(session, result);
        }
    }

    private void mergeChartAdvice(Map<String, Object> result, OpsAgentService.AgentRunResult agentResult) {
        if (agentResult == null) {
            result.put("chartSuggest", false);
            return;
        }
        result.put("chartSuggest", agentResult.isChartSuggest());
        result.put("chartReason", agentResult.getChartReason());
        result.put("chartTimeRange", agentResult.getChartTimeRange());
        result.put("chartTemplate", agentResult.getChartTemplate());
        result.put("chartTitle", agentResult.getChartTitle());
    }

    private Map<String, Object> buildChartPayload(String chartTemplate, String chartTitle, Map<String, Object> metrics) {
        String normalizedTemplate = normalizeChartTemplate(chartTemplate);
        String timeRange = String.valueOf(metrics.getOrDefault("timeRange", "1h"));
        String resolvedTitle = StringUtils.hasText(chartTitle)
                ? chartTitle
                : defaultChartTitle(normalizedTemplate, timeRange);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("renderer", "echarts");
        payload.put("template", normalizedTemplate);
        payload.put("title", resolvedTitle);
        payload.put("timeRange", timeRange);
        payload.put("serverIp", metrics.getOrDefault("serverIp", ""));

        List<Map<String, Object>> history = normalizeHistory(metrics.get("history"));
        Map<String, Object> summary = asObjectMap(metrics.get("summary"));
        Map<String, Object> current = asObjectMap(metrics.get("current"));
        List<Map<String, Object>> anomalies = buildAnomalies(history);
        Map<String, Object> healthScores = buildHealthScores(summary, anomalies);

        payload.put("history", history);
        payload.put("summary", summary);
        payload.put("current", current);
        payload.put("anomalies", anomalies);
        payload.put("healthScores", healthScores);
        payload.put("option", buildEchartsOption(normalizedTemplate, history, anomalies, healthScores));
        return payload;
    }

    private List<Map<String, Object>> normalizeHistory(Object rawHistory) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        if (!(rawHistory instanceof List<?> items)) {
            return normalized;
        }

        for (Object item : items) {
            Map<String, Object> point = new LinkedHashMap<>();
            if (item instanceof MetricDTO metric) {
                point.put("time", metric.getTime());
                point.put("cpuUsage", metric.getCpuUsage());
                point.put("memUsage", metric.getMemUsage());
            } else if (item instanceof Map<?, ?> rawMap) {
                point.put("time", rawMap.containsKey("time") ? rawMap.get("time") : "");
                point.put("cpuUsage", rawMap.containsKey("cpuUsage") ? rawMap.get("cpuUsage") : 0);
                point.put("memUsage", rawMap.containsKey("memUsage") ? rawMap.get("memUsage") : 0);
            } else {
                Map<String, Object> converted = objectMapper.convertValue(item, Map.class);
                point.put("time", converted.getOrDefault("time", ""));
                point.put("cpuUsage", converted.getOrDefault("cpuUsage", 0));
                point.put("memUsage", converted.getOrDefault("memUsage", 0));
            }
            normalized.add(point);
        }
        return normalized;
    }

    private Map<String, Object> asObjectMap(Object rawMap) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (rawMap instanceof Map<?, ?> source) {
            source.forEach((key, value) -> normalized.put(String.valueOf(key), value));
        }
        return normalized;
    }

    private List<Map<String, Object>> buildAnomalies(List<Map<String, Object>> history) {
        List<Map<String, Object>> anomalies = new ArrayList<>();
        for (Map<String, Object> point : history) {
            double cpu = toDouble(point.get("cpuUsage"));
            double mem = toDouble(point.get("memUsage"));
            if (cpu < 85 && mem < 85) {
                continue;
            }
            Map<String, Object> anomaly = new LinkedHashMap<>();
            anomaly.put("time", point.getOrDefault("time", ""));
            anomaly.put("cpuUsage", round1(cpu));
            anomaly.put("memUsage", round1(mem));
            anomaly.put("level", cpu >= 95 || mem >= 95 ? "critical" : "warning");
            anomalies.add(anomaly);
        }
        return anomalies;
    }

    private Map<String, Object> buildHealthScores(Map<String, Object> summary, List<Map<String, Object>> anomalies) {
        double avgCpu = toDouble(summary.get("avgCpu"));
        double maxCpu = toDouble(summary.get("maxCpu"));
        double avgMem = toDouble(summary.get("avgMem"));
        double maxMem = toDouble(summary.get("maxMem"));
        double cpuScore = scoreByUsage(avgCpu, maxCpu);
        double memScore = scoreByUsage(avgMem, maxMem);
        double stabilityScore = Math.max(0, 100 - anomalies.size() * 5.0);
        double capacityScore = Math.max(0, 100 - Math.max(maxCpu, maxMem));

        Map<String, Object> healthScores = new LinkedHashMap<>();
        healthScores.put("cpuScore", round1(cpuScore));
        healthScores.put("memScore", round1(memScore));
        healthScores.put("stabilityScore", round1(stabilityScore));
        healthScores.put("capacityScore", round1(capacityScore));
        healthScores.put("overall", round1((cpuScore + memScore + stabilityScore + capacityScore) / 4.0));
        return healthScores;
    }

    private Map<String, Object> buildEchartsOption(String chartTemplate,
                                                   List<Map<String, Object>> history,
                                                   List<Map<String, Object>> anomalies,
                                                   Map<String, Object> healthScores) {
        switch (chartTemplate) {
            case "cpu_mem_trend":
                return buildCpuMemTrendOption(history, false);
            case "anomaly_timeline":
                return buildAnomalyTimelineOption(anomalies);
            case "health_score_radar":
                return buildHealthScoreRadarOption(healthScores, history.isEmpty());
            default:
                return buildCpuMemTrendOption(history, true);
        }
    }

    private Map<String, Object> buildCpuMemTrendOption(List<Map<String, Object>> history, boolean withThreshold) {
        List<String> labels = new ArrayList<>();
        List<Double> cpuData = new ArrayList<>();
        List<Double> memData = new ArrayList<>();
        for (Map<String, Object> point : history) {
            labels.add(String.valueOf(point.getOrDefault("time", "")));
            cpuData.add(round1(toDouble(point.get("cpuUsage"))));
            memData.add(round1(toDouble(point.get("memUsage"))));
        }

        Map<String, Object> option = baseCartesianOption(labels);
        List<Map<String, Object>> series = new ArrayList<>();
        series.add(lineSeries("CPU 使用率(%)", cpuData, "#5470C6", "rgba(84,112,198,0.15)"));
        series.add(lineSeries("内存使用率(%)", memData, "#91CC75", "rgba(145,204,117,0.15)"));
        if (withThreshold) {
            List<Double> threshold = new ArrayList<>();
            for (int index = 0; index < labels.size(); index++) {
                threshold.add(85.0);
            }
            Map<String, Object> thresholdSeries = new LinkedHashMap<>();
            thresholdSeries.put("name", "85% 阈值");
            thresholdSeries.put("type", "line");
            thresholdSeries.put("showSymbol", false);
            thresholdSeries.put("data", threshold);
            thresholdSeries.put("lineStyle", Map.of("type", "dashed", "width", 2, "color", "#EE6666"));
            thresholdSeries.put("itemStyle", Map.of("color", "#EE6666"));
            series.add(thresholdSeries);
        }
        option.put("series", series);
        if (labels.isEmpty()) {
            option.put("graphic", noDataGraphic("暂无可展示的监控数据"));
        }
        return option;
    }

    private Map<String, Object> buildAnomalyTimelineOption(List<Map<String, Object>> anomalies) {
        List<String> labels = new ArrayList<>();
        List<Double> cpuData = new ArrayList<>();
        List<Double> memData = new ArrayList<>();
        for (Map<String, Object> anomaly : anomalies) {
            labels.add(String.valueOf(anomaly.getOrDefault("time", "")));
            cpuData.add(round1(toDouble(anomaly.get("cpuUsage"))));
            memData.add(round1(toDouble(anomaly.get("memUsage"))));
        }

        Map<String, Object> option = baseCartesianOption(labels);
        option.put("series", List.of(
                barSeries("异常 CPU(%)", cpuData, "#EE6666"),
                barSeries("异常内存(%)", memData, "#FAC858")
        ));
        if (labels.isEmpty()) {
            option.put("graphic", noDataGraphic("当前时间范围内没有明显异常"));
        }
        return option;
    }

    private Map<String, Object> buildHealthScoreRadarOption(Map<String, Object> healthScores, boolean emptyHistory) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("tooltip", Map.of("trigger", "item"));
        option.put("legend", Map.of("top", 0));
        option.put("radar", Map.of(
                "radius", "60%",
                "indicator", List.of(
                        Map.of("name", "CPU", "max", 100),
                        Map.of("name", "内存", "max", 100),
                        Map.of("name", "稳定性", "max", 100),
                        Map.of("name", "容量", "max", 100)
                )
        ));
        option.put("series", List.of(Map.of(
                "name", "健康评分",
                "type", "radar",
                "data", List.of(Map.of(
                        "value", List.of(
                                round1(toDouble(healthScores.get("cpuScore"))),
                                round1(toDouble(healthScores.get("memScore"))),
                                round1(toDouble(healthScores.get("stabilityScore"))),
                                round1(toDouble(healthScores.get("capacityScore")))
                        ),
                        "name", "健康评分"
                )),
                "areaStyle", Map.of("color", "rgba(84,112,198,0.22)"),
                "lineStyle", Map.of("color", "#5470C6", "width", 2),
                "itemStyle", Map.of("color", "#5470C6")
        )));
        if (emptyHistory) {
            option.put("graphic", noDataGraphic("样本较少，评分仅供参考"));
        }
        return option;
    }

    private Map<String, Object> baseCartesianOption(List<String> labels) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("animation", true);
        option.put("color", List.of("#5470C6", "#91CC75", "#EE6666", "#FAC858"));
        option.put("tooltip", Map.of("trigger", "axis"));
        option.put("legend", Map.of("top", 0));
        option.put("grid", Map.of("left", "3%", "right", "4%", "bottom", "3%", "containLabel", true));
        option.put("xAxis", Map.of(
                "type", "category",
                "boundaryGap", false,
                "data", labels
        ));
        option.put("yAxis", Map.of(
                "type", "value",
                "min", 0,
                "max", 100,
                "axisLabel", Map.of("formatter", "{value}%")
        ));
        return option;
    }

    private Map<String, Object> lineSeries(String name, List<Double> data, String color, String areaColor) {
        Map<String, Object> series = new LinkedHashMap<>();
        series.put("name", name);
        series.put("type", "line");
        series.put("smooth", true);
        series.put("showSymbol", false);
        series.put("data", data);
        series.put("lineStyle", Map.of("width", 2, "color", color));
        series.put("itemStyle", Map.of("color", color));
        series.put("areaStyle", Map.of("color", areaColor));
        return series;
    }

    private Map<String, Object> barSeries(String name, List<Double> data, String color) {
        Map<String, Object> series = new LinkedHashMap<>();
        series.put("name", name);
        series.put("type", "bar");
        series.put("barMaxWidth", 26);
        series.put("data", data);
        series.put("itemStyle", Map.of("color", color, "borderRadius", List.of(4, 4, 0, 0)));
        return series;
    }

    private Map<String, Object> noDataGraphic(String text) {
        return Map.of(
                "type", "text",
                "left", "center",
                "top", "middle",
                "style", Map.of(
                        "text", text,
                        "fill", "#909399",
                        "fontSize", 14
                )
        );
    }

    private String normalizeChartTemplate(String chartTemplate) {
        String normalized = String.valueOf(chartTemplate).trim().toLowerCase();
        return switch (normalized) {
            case "cpu_mem_trend", "anomaly_timeline", "health_score_radar" -> normalized;
            default -> "health_overview";
        };
    }

    private String defaultChartTitle(String chartTemplate, String timeRange) {
        String prefix = switch (chartTemplate) {
            case "cpu_mem_trend" -> "CPU / 内存趋势图";
            case "anomaly_timeline" -> "异常时序图";
            case "health_score_radar" -> "健康评分雷达图";
            default -> "服务器健康总览";
        };
        return prefix + "（" + (StringUtils.hasText(timeRange) ? timeRange : "1h") + "）";
    }

    private double scoreByUsage(double avg, double max) {
        double penalty = avg * 0.5 + max * 0.5;
        return Math.max(0, 100 - penalty);
    }

    private double toDouble(Object v) {
        if (v == null) return 0.0;
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket closed: {}", session.getId());
        SESSIONS.remove(session.getId());
        AGENT_HISTORY.remove(session.getId());
        opsAgentService.stopAgent(session.getId());
        FutureTask<Void> runningTask = RUNNING_TASKS.remove(session.getId());
        if (runningTask != null) {
            runningTask.cancel(true);
        }
    }

    private void sendText(WebSocketSession session, String msg) {
        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(msg));
                }
            }
        } catch (IOException e) {
            log.error("send text failed", e);
        }
    }

    private void sendJson(WebSocketSession session, Map<String, Object> payload) {
        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
                }
            }
        } catch (Exception e) {
            log.error("send json failed", e);
        }
    }

    private void sendProgress(WebSocketSession session, String stage, String message, long elapsedMs) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "ops_progress");
        payload.put("stage", stage);
        payload.put("message", message);
        payload.put("elapsedMs", elapsedMs);
        sendJson(session, payload);
    }
}
