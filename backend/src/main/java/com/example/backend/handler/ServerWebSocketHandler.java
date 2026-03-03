package com.example.backend.handler;

import com.example.backend.utils.AiUtils;
import com.example.backend.utils.SshUtils;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ServerWebSocketHandler extends TextWebSocketHandler {

    private static final ConcurrentHashMap<String, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private AiUtils aiUtils;

    @Autowired
    private SshUtils sshUtils;

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

            Map<String, Object> fallback = new HashMap<>();
            fallback.put("type", "error");
            fallback.put("message", "unsupported type, use type=ops_chat");
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
        long start = System.currentTimeMillis();
        String query = node.path("query").asText("");
        boolean execute = node.path("execute").asBoolean(false);
        String serverIp = node.path("serverIp").asText("");
        String username = node.path("username").asText("");
        String password = node.path("password").asText("");
        log.info("ops_chat start, sessionId={}, execute={}, serverIp={}, queryLength={}",
                session.getId(), execute, serverIp, query == null ? 0 : query.length());
        sendProgress(session, "start", "收到请求，开始生成执行计划...", 0);

        long aiStart = System.currentTimeMillis();
        Map<String, Object> plan = aiUtils.planServerCommandByGlm47(query);
        long aiElapsed = System.currentTimeMillis() - aiStart;
        String command = String.valueOf(plan.getOrDefault("command", ""));
        boolean hasCommand = Boolean.TRUE.equals(plan.get("hasCommand"));
        @SuppressWarnings("unchecked")
        List<Map<String, String>> planSteps = (List<Map<String, String>>) plan.getOrDefault("planSteps", List.of());
        log.info("ops_chat ai_plan done, sessionId={}, aiElapsedMs={}, hasCommand={}, commandLength={}",
                session.getId(), aiElapsed, hasCommand, command == null ? 0 : command.length());
        sendProgress(session, "ai_plan_done", "AI 计划生成完成", aiElapsed);

        Map<String, Object> result = new HashMap<>();
        result.put("type", "ops_chat_result");
        result.put("query", query);
        result.put("reply", plan.getOrDefault("reply", ""));
        result.put("riskLevel", plan.getOrDefault("riskLevel", "medium"));
        result.put("needConfirm", plan.getOrDefault("needConfirm", true));
        result.put("hasCommand", hasCommand);
        result.put("command", command);
        result.put("planSteps", planSteps);

        if (execute) {
            if (planSteps != null && !planSteps.isEmpty()) {
                if (!StringUtils.hasText(serverIp) || !StringUtils.hasText(username) || !StringUtils.hasText(password)) {
                    result.put("executed", false);
                    result.put("execResult", "缺少 serverIp/username/password，无法按计划执行命令。");
                    sendJson(session, result);
                    log.info("ops_chat finished, sessionId={}, totalElapsedMs={}", session.getId(), System.currentTimeMillis() - start);
                    return;
                }
                long sshStart = System.currentTimeMillis();
                sendProgress(session, "plan_exec_start", "开始按计划逐步执行...", System.currentTimeMillis() - start);
                String execResult = execByPlan(session, serverIp, username, password, planSteps);
                long sshElapsed = System.currentTimeMillis() - sshStart;
                result.put("executed", true);
                result.put("execResult", execResult);
                log.info("ops_chat ssh_plan_exec done, sessionId={}, sshElapsedMs={}, stepCount={}",
                        session.getId(), sshElapsed, planSteps.size());
                sendProgress(session, "plan_exec_done", "计划执行结束", sshElapsed);
            } else if (!hasCommand || !StringUtils.hasText(command)) {
                result.put("executed", false);
                result.put("execResult", "AI 未提供可执行命令。");
            } else if (!StringUtils.hasText(serverIp) || !StringUtils.hasText(username) || !StringUtils.hasText(password)) {
                result.put("executed", false);
                result.put("execResult", "缺少 serverIp/username/password，无法执行命令。");
            } else {
                long sshStart = System.currentTimeMillis();
                sendProgress(session, "cmd_exec_start", "开始执行命令...", System.currentTimeMillis() - start);
                String execResult = sshUtils.exec(serverIp, username, password, command);
                long sshElapsed = System.currentTimeMillis() - sshStart;
                result.put("executed", true);
                result.put("execResult", execResult);
                log.info("ops_chat ssh_exec done, sessionId={}, sshElapsedMs={}", session.getId(), sshElapsed);
                sendProgress(session, "cmd_exec_done", "命令执行结束", sshElapsed);
            }
        } else {
            result.put("executed", false);
            result.put("execResult", "未执行。若要执行，请携带 execute=true。 ");
        }

        sendJson(session, result);
        sendProgress(session, "finished", "流程结束", System.currentTimeMillis() - start);
        log.info("ops_chat finished, sessionId={}, totalElapsedMs={}", session.getId(), System.currentTimeMillis() - start);
    }

    private String execByPlan(WebSocketSession session, String serverIp, String username, String password, List<Map<String, String>> planSteps) {
        if (!StringUtils.hasText(serverIp) || !StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return "缺少 serverIp/username/password，无法按计划执行命令。";
        }

        StringBuilder trace = new StringBuilder();
        int index = 1;
        for (Map<String, String> step : planSteps) {
            String desc = safe(step.get("description"));
            String checkCommand = safe(step.get("checkCommand"));
            String expectContains = safe(step.get("expectContains"));
            String onPass = safe(step.get("onPass"));
            String onFail = safe(step.get("onFail"));
            String executeCommand = safe(step.get("executeCommand"));

            trace.append("Step ").append(index).append(": ").append(desc).append("\n");
            log.info("ops_chat plan step start, step={}, desc={}", index, desc);
            sendProgress(session, "plan_step_start", "步骤 " + index + " 开始: " + desc, 0);

            if (StringUtils.hasText(checkCommand)) {
                String checkOut = sshUtils.exec(serverIp, username, password, checkCommand);
                boolean passed = !StringUtils.hasText(expectContains) || checkOut.contains(expectContains);
                String action = passed ? onPass : onFail;

                trace.append("  check=").append(passed ? "PASS" : "FAIL").append(", action=").append(action).append("\n");
                sendProgress(session, "plan_step_checked",
                        "步骤 " + index + " 检查完成: " + (passed ? "通过" : "失败") + "，动作=" + action, 0);

                if ("stop".equalsIgnoreCase(action)) {
                    trace.append("  result=STOP\n");
                    sendProgress(session, "plan_step_stop", "步骤 " + index + " 触发停止", 0);
                    trace.append("Final: 流程已停止。\n");
                    return trace.toString();
                }
                if ("execute".equalsIgnoreCase(action) && StringUtils.hasText(executeCommand)) {
                    String execOut = sshUtils.exec(serverIp, username, password, executeCommand);
                    trace.append("  execute=RUN, output=").append(shortOutput(execOut)).append("\n");
                    sendProgress(session, "plan_step_exec", "步骤 " + index + " 已执行命令", 0);
                }
            } else if (StringUtils.hasText(executeCommand)) {
                String execOut = sshUtils.exec(serverIp, username, password, executeCommand);
                trace.append("  execute=RUN, output=").append(shortOutput(execOut)).append("\n");
                sendProgress(session, "plan_step_exec", "步骤 " + index + " 已执行命令", 0);
            } else {
                trace.append("  skipped\n");
                sendProgress(session, "plan_step_skip", "步骤 " + index + " 已跳过（无命令）", 0);
            }
            index++;
        }
        trace.append("Final: 流程执行完成。\n");
        return trace.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String shortOutput(String output) {
        if (!StringUtils.hasText(output)) {
            return "(empty)";
        }
        String oneLine = output.replace("\r", " ").replace("\n", " ").trim();
        return oneLine.length() <= 120 ? oneLine : oneLine.substring(0, 120) + "...";
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket closed: {}", session.getId());
        SESSIONS.remove(session.getId());
    }

    private void sendText(WebSocketSession session, String msg) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(msg));
            }
        } catch (IOException e) {
            log.error("send text failed", e);
        }
    }

    private void sendJson(WebSocketSession session, Map<String, Object> payload) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
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
