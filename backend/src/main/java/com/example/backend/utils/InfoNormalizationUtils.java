package com.example.backend.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class InfoNormalizationUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_SUGGESTED_ACTIONS = 4;
    private static final int MAX_ACTION_LENGTH = 120;

    private InfoNormalizationUtils() {
    }

    public static String normalizeComponent(String component) {
        if (!StringUtils.hasText(component)) {
            return "未识别组件";
        }
        return component.trim();
    }

    public static String normalizeText(String text, String defaultValue) {
        if (!StringUtils.hasText(text)) {
            return defaultValue;
        }
        String normalized = text
                .replace("\r", "")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
        if (!StringUtils.hasText(normalized)) {
            return defaultValue;
        }
        String lowered = normalized.toLowerCase(Locale.ROOT);
        if ("null".equals(lowered) || "undefined".equals(lowered) || "-".equals(normalized) || "暂无".equals(normalized)) {
            return defaultValue;
        }
        return normalized;
    }

    public static String normalizeRiskLevel(String riskLevel) {
        if (!StringUtils.hasText(riskLevel)) {
            return "无";
        }

        String value = riskLevel.trim();
        String lowered = value.toLowerCase(Locale.ROOT);
        if (containsAny(lowered, "high", "critical", "fatal", "error") || value.contains("高") || value.contains("严重")) {
            return "高";
        }
        if (containsAny(lowered, "medium", "warning", "warn", "moderate") || value.contains("中") || value.contains("警告")) {
            return "中";
        }
        if (containsAny(lowered, "low", "minor", "info", "notice") || value.contains("低") || value.contains("提示")) {
            return "低";
        }
        if (containsAny(lowered, "none", "normal", "ok", "healthy", "safe", "no issue") || value.contains("无") || value.contains("正常") || value.contains("安全") || value.contains("健康")) {
            return "无";
        }
        return "中";
    }

    public static String normalizeSuggestedActions(JsonNode node, String normalizedRiskLevel) {
        if ("无".equals(normalizedRiskLevel) || node == null || node.isNull()) {
            return "[]";
        }

        List<String> actions = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                addCandidate(actions, item == null ? "" : item.asText(""));
            }
        } else if (node.isTextual()) {
            actions.addAll(splitSuggestedActions(node.asText("")));
        } else {
            actions.addAll(splitSuggestedActions(node.toString()));
        }
        return toJsonArray(limitAndDeduplicate(actions));
    }

    public static String normalizeSuggestedActions(String rawValue, String normalizedRiskLevel) {
        String normalized = rawValue == null ? "" : rawValue.trim();
        if ("无".equals(normalizedRiskLevel) || !StringUtils.hasText(normalized)) {
            return "[]";
        }

        String lowered = normalized.toLowerCase(Locale.ROOT);
        if ("null".equals(lowered) || "undefined".equals(lowered) || "-".equals(normalized)
                || "无".equals(normalized) || "暂无".equals(normalized) || "[]".equals(normalized)) {
            return "[]";
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(normalized);
            return normalizeSuggestedActions(root, normalizedRiskLevel);
        } catch (Exception ignored) {
        }
        return toJsonArray(limitAndDeduplicate(splitSuggestedActions(normalized)));
    }

    private static List<String> splitSuggestedActions(String text) {
        List<String> actions = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return actions;
        }

        String normalized = text.trim();
        if ("无".equals(normalized) || "[]".equals(normalized) || "暂无".equals(normalized) || "-".equals(normalized) || "null".equalsIgnoreCase(normalized) || "undefined".equalsIgnoreCase(normalized)) {
            return actions;
        }

        normalized = normalized
                .replace("\r", "")
                .replaceAll("([；;])(?=\\S)", "$1\n")
                .replaceAll("\\s+(?=\\d+[\\.、）\\)])", "\n")
                .replaceAll("\\s+(?=[一二三四五六七八九十]+、)", "\n");

        String[] rawLines = normalized.split("\n");
        String previous = null;
        for (String rawLine : rawLines) {
            String cleaned = cleanupAction(rawLine);
            if (!StringUtils.hasText(cleaned)) {
                continue;
            }
            boolean isContinuation = previous != null
                    && !rawLine.matches("^\\s*([\\-*•]|\\d+[\\.、）\\)]|[一二三四五六七八九十]+、).*");
            if (isContinuation && !actions.isEmpty()) {
                int lastIndex = actions.size() - 1;
                actions.set(lastIndex, cropAction(actions.get(lastIndex) + " " + cleaned));
                previous = rawLine;
                continue;
            }
            addCandidate(actions, cleaned);
            previous = rawLine;
        }

        if (actions.size() <= 1) {
            for (String part : normalized.split("[；;]")) {
                addCandidate(actions, cleanupAction(part));
            }
        }
        return actions;
    }

    private static String cleanupAction(String action) {
        if (!StringUtils.hasText(action)) {
            return "";
        }
        return cropAction(action
                .replaceAll("^\\s*([\\-*•]|\\d+[\\.、）\\)]|[一二三四五六七八九十]+、)\\s*", "")
                .replaceAll("\\s+", " ")
                .trim());
    }

    private static void addCandidate(List<String> actions, String candidate) {
        String cleaned = cleanupAction(candidate);
        if (!StringUtils.hasText(cleaned) || "无".equals(cleaned) || "暂无".equals(cleaned) || "-".equals(cleaned) || "null".equalsIgnoreCase(cleaned) || "undefined".equalsIgnoreCase(cleaned)) {
            return;
        }
        actions.add(cleaned);
    }

    private static List<String> limitAndDeduplicate(List<String> actions) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String action : actions) {
            if (!StringUtils.hasText(action)) {
                continue;
            }
            unique.add(action.trim());
            if (unique.size() >= MAX_SUGGESTED_ACTIONS) {
                break;
            }
        }
        return new ArrayList<>(unique);
    }

    private static String cropAction(String action) {
        String value = StringUtils.hasText(action) ? action.trim() : "";
        if (value.length() <= MAX_ACTION_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ACTION_LENGTH).trim();
    }

    private static String toJsonArray(List<String> actions) {
        try {
            return OBJECT_MAPPER.writeValueAsString(actions == null ? List.of() : actions);
        } catch (Exception ignored) {
            return "[]";
        }
    }

    private static boolean containsAny(String source, String... keywords) {
        for (String keyword : keywords) {
            if (source.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
