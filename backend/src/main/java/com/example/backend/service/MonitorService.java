package com.example.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.backend.dto.MetricDTO;
import com.example.backend.entity.ComponentConfig;
import com.example.backend.entity.Information;
import com.example.backend.entity.UserLogin;
import com.example.backend.mapper.ComponentConfigMapper;
import com.example.backend.mapper.InformationMapper;
import com.example.backend.mapper.UserLoginMapper;
import com.example.backend.utils.InfoNormalizationUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MonitorService {

    private static final String MONITOR_FAILURE_COMPONENT = "系统监控";
    private static final String MONITOR_FAILURE_SUMMARY = "SSH服务器网络异常";
    private static final String MONITOR_FAILURE_ANALYSIS = "采集不到系统监控数据，请检查 SSH 连接、服务器网络状态以及目标主机是否可达。";
    private static final String SYSTEM_MONITOR_CONFIG_KEY = "system_monitor";
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final int SSH_CONNECT_TIMEOUT_MS = 5000;
    private static final int SSH_SOCKET_TIMEOUT_MS = 10000;
    private static final String CPU_CMD = "vmstat 1 2 | tail -1 | awk '{print 100 - $15}'";
    private static final String MEM_CMD = "awk '/MemTotal/ {total=$2} /MemAvailable/ {avail=$2} END {if (total>0 && avail>=0) print (total-avail)/total*100}' /proc/meminfo";
    private static final String UPTIME_CMD = "uptime -p";
    private static final String OS_CMD = "cat /etc/os-release | grep PRETTY_NAME | cut -d= -f2 | tr -d '\"'";
    private static final String TOTAL_MEM_CMD = "free -h | awk '/Mem:/ {print $2}'";
    private static final String AVAILABLE_MEM_CMD = "free -h | awk '/Mem:/ {print $7}'";
    private static final String NET_COUNTER_CMD = "awk 'NR>2 {gsub(/:/,\"\",$1); iface=$1; if (iface != \"lo\" && iface !~ /^(docker|veth|br-|virbr|vmnet|zt|tailscale|tun|tap)/) {rx+=$2; tx+=$10}} END {printf \"%.0f %.0f\", rx+0, tx+0}' /proc/net/dev";
    private static final String DISK_COUNTER_CMD = "awk '$3 ~ /^(sd[a-z]+|vd[a-z]+|xvd[a-z]+|nvme[0-9]+n[0-9]+)$/ {read+=$6*512; write+=$10*512} END {printf \"%.0f %.0f\", read+0, write+0}' /proc/diskstats";
    private static final int MAX_HISTORY_POINTS = 2160;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private ComponentConfigMapper componentConfigMapper;

    @Autowired
    private UserLoginMapper userLoginMapper;

    @Autowired
    private InformationMapper informationMapper;

    @Value("${monitor.schedule.fixed-rate:60000}")
    private long monitorFixedRateMs;

    // IP -> History List
    private final Map<String, List<MetricDTO>> historyMap = new ConcurrentHashMap<>();
    
    // IP -> Current Info Map
    private final Map<String, Map<String, Object>> currentInfoMap = new ConcurrentHashMap<>();

    // IP -> 上一轮原始计数器，用于计算网卡/磁盘速率
    private final Map<String, RawCounterSnapshot> counterSnapshotMap = new ConcurrentHashMap<>();

    /**
     * 定时任务：每隔一段时间执行一次 (由 monitor.schedule.fixed-rate 配置，默认 60000ms)
     * 遍历数据库中的服务器配置，远程采集启用的系统监控指标
     */
    @Scheduled(
            initialDelayString = "${monitor.schedule.fixed-rate:60000}",
            fixedRateString = "${monitor.schedule.fixed-rate:60000}"
    )
    public void collectMetrics() {
        List<ComponentConfig> configs = componentConfigMapper.selectList(new QueryWrapper<ComponentConfig>()
                .eq("config_key", SYSTEM_MONITOR_CONFIG_KEY)
                .eq("is_enabled", 1)
                .orderByDesc("updated_at"));

        Map<String, ComponentConfig> uniqueServers = new LinkedHashMap<>();
        for (ComponentConfig config : configs) {
            String serverIp = normalizeServerIp(config == null ? null : config.getServerIp());
            if (!StringUtils.hasText(serverIp) || uniqueServers.containsKey(serverIp)) {
                continue;
            }
            config.setServerIp(serverIp);
            uniqueServers.put(serverIp, config);
        }

        if (uniqueServers.isEmpty()) {
            System.out.println("没有配置需要监控的服务器");
            return;
        }

        for (ComponentConfig server : uniqueServers.values()) {
            collectServerMetrics(server);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void collectMetricsOnStartup() {
        System.out.println("应用启动完成，立即执行一次系统监控采集");
        collectMetrics();
    }

    private void collectServerMetrics(ComponentConfig server) {
        String ip = normalizeServerIp(server == null ? null : server.getServerIp());
        String user = server == null ? null : server.getUsername();
        String password = server == null ? null : server.getPassword();
        MonitorSettings settings = parseMonitorSettings(server == null ? null : server.getConfigValue());

        if (!StringUtils.hasText(ip)) {
            return;
        }

        if (!StringUtils.hasText(user) || !StringUtils.hasText(password)) {
            System.err.println("跳过服务器 " + ip + ": 缺少用户名或密码");
            return;
        }

        if (!settings.hasAnyMetricEnabled()) {
            counterSnapshotMap.remove(ip);
            cacheDisabledSnapshot(ip, settings);
            System.out.println("服务器 " + ip + " 当前未启用任何采集指标，已跳过系统监控采样");
            return;
        }

        try {
            MetricSnapshot snapshot = collectMetricSnapshot(ip, user, password, settings);
            MetricDTO metric = cacheMetricSnapshot(ip, snapshot);

            System.out.println("已采集服务器 " + ip + " 数据: " + metric);

        } catch (Exception e) {
            System.err.println("采集服务器 " + ip + " 失败: " + e.getMessage());
            createMonitorFailureInfo(ip, e.getMessage());
        }
    }

    private MetricSnapshot collectMetricSnapshot(String host, String user, String password, MonitorSettings settings) throws Exception {
        Session session = openSession(host, user, password);
        long collectedAtMs = System.currentTimeMillis();
        try {
            String upTime = safeTrim(sshExec(session, UPTIME_CMD));
            String os = safeTrim(sshExec(session, OS_CMD));
            String totalMem = safeTrim(sshExec(session, TOTAL_MEM_CMD));
            String availableMem = safeTrim(sshExec(session, AVAILABLE_MEM_CMD));

            Double cpuUsage = settings.cpuEnabled()
                    ? parseMetricValue(sshExec(session, CPU_CMD), "CPU")
                    : null;
            Double memUsage = settings.memEnabled()
                    ? parseMetricValue(sshExec(session, MEM_CMD), "内存")
                    : null;

            long[] netCounters = settings.hasAnyNetworkMetricEnabled()
                    ? parseCounterPair(sshExec(session, NET_COUNTER_CMD), "网卡")
                    : null;
            long[] diskCounters = settings.hasAnyDiskMetricEnabled()
                    ? parseCounterPair(sshExec(session, DISK_COUNTER_CMD), "磁盘")
                    : null;

            RateSnapshot rateSnapshot = resolveRateSnapshot(host, settings, netCounters, diskCounters, collectedAtMs);

            return new MetricSnapshot(
                    cpuUsage,
                    memUsage,
                    rateSnapshot.netRxBytesPerSec(),
                    rateSnapshot.netTxBytesPerSec(),
                    rateSnapshot.diskReadBytesPerSec(),
                    rateSnapshot.diskWriteBytesPerSec(),
                    upTime,
                    os,
                    totalMem,
                    availableMem,
                    settings
            );
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    private MetricDTO cacheMetricSnapshot(String serverIp, MetricSnapshot snapshot) {
        String currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        MetricDTO metric = new MetricDTO(
                currentTime,
                roundNullable(snapshot.cpuUsage()),
                roundNullable(snapshot.memUsage()),
                roundNullable(snapshot.netRxBytesPerSec()),
                roundNullable(snapshot.netTxBytesPerSec()),
                roundNullable(snapshot.diskReadBytesPerSec()),
                roundNullable(snapshot.diskWriteBytesPerSec())
        );

        historyMap.computeIfAbsent(serverIp, k -> new CopyOnWriteArrayList<>()).add(metric);
        List<MetricDTO> history = historyMap.get(serverIp);
        if (history.size() > MAX_HISTORY_POINTS) {
            history.remove(0);
        }

        Map<String, Object> info = new HashMap<>();
        info.put("os", snapshot.os().isEmpty() ? "Linux" : snapshot.os());
        info.put("upTime", snapshot.upTime());
        info.put("cpuUsage", metric.getCpuUsage());
        info.put("memUsage", metric.getMemUsage());
        info.put("netRxBytesPerSec", metric.getNetRxBytesPerSec());
        info.put("netTxBytesPerSec", metric.getNetTxBytesPerSec());
        info.put("diskReadBytesPerSec", metric.getDiskReadBytesPerSec());
        info.put("diskWriteBytesPerSec", metric.getDiskWriteBytesPerSec());
        info.put("totalMemory", snapshot.totalMem());
        info.put("availableMemory", snapshot.availableMem());
        info.put("processor", "Remote Server");
        info.put("monitorSettings", snapshot.settings().toMap());
        currentInfoMap.put(serverIp, info);
        return metric;
    }

    private void cacheDisabledSnapshot(String serverIp, MonitorSettings settings) {
        Map<String, Object> previous = currentInfoMap.getOrDefault(serverIp, Collections.emptyMap());
        Map<String, Object> info = new LinkedHashMap<>(previous);
        info.put("cpuUsage", null);
        info.put("memUsage", null);
        info.put("netRxBytesPerSec", null);
        info.put("netTxBytesPerSec", null);
        info.put("diskReadBytesPerSec", null);
        info.put("diskWriteBytesPerSec", null);
        info.put("monitorSettings", settings.toMap());
        if (!info.containsKey("os")) {
            info.put("os", "Linux");
        }
        if (!info.containsKey("upTime")) {
            info.put("upTime", "");
        }
        if (!info.containsKey("totalMemory")) {
            info.put("totalMemory", "");
        }
        if (!info.containsKey("availableMemory")) {
            info.put("availableMemory", "");
        }
        info.put("processor", "Remote Server");
        currentInfoMap.put(serverIp, info);
    }

    private RateSnapshot resolveRateSnapshot(String serverIp,
                                             MonitorSettings settings,
                                             long[] netCounters,
                                             long[] diskCounters,
                                             long collectedAtMs) {
        RawCounterSnapshot previous = counterSnapshotMap.get(serverIp);

        Double netRxRate = calculateRate(
                settings.netRxEnabled(),
                previous == null ? null : previous.netRxBytes(),
                netCounters == null ? null : netCounters[0],
                previous == null ? null : previous.collectedAtMs(),
                collectedAtMs
        );
        Double netTxRate = calculateRate(
                settings.netTxEnabled(),
                previous == null ? null : previous.netTxBytes(),
                netCounters == null ? null : netCounters[1],
                previous == null ? null : previous.collectedAtMs(),
                collectedAtMs
        );
        Double diskReadRate = calculateRate(
                settings.diskReadEnabled(),
                previous == null ? null : previous.diskReadBytes(),
                diskCounters == null ? null : diskCounters[0],
                previous == null ? null : previous.collectedAtMs(),
                collectedAtMs
        );
        Double diskWriteRate = calculateRate(
                settings.diskWriteEnabled(),
                previous == null ? null : previous.diskWriteBytes(),
                diskCounters == null ? null : diskCounters[1],
                previous == null ? null : previous.collectedAtMs(),
                collectedAtMs
        );

        counterSnapshotMap.put(
                serverIp,
                new RawCounterSnapshot(
                        collectedAtMs,
                        settings.hasAnyNetworkMetricEnabled() && netCounters != null ? netCounters[0] : null,
                        settings.hasAnyNetworkMetricEnabled() && netCounters != null ? netCounters[1] : null,
                        settings.hasAnyDiskMetricEnabled() && diskCounters != null ? diskCounters[0] : null,
                        settings.hasAnyDiskMetricEnabled() && diskCounters != null ? diskCounters[1] : null
                )
        );

        return new RateSnapshot(netRxRate, netTxRate, diskReadRate, diskWriteRate);
    }

    private Double calculateRate(boolean enabled, Long previousValue, Long currentValue, Long previousTimeMs, long currentTimeMs) {
        if (!enabled) {
            return null;
        }
        if (previousValue == null || currentValue == null || previousTimeMs == null) {
            return 0.0;
        }

        long delta = currentValue - previousValue;
        long elapsedMs = currentTimeMs - previousTimeMs;
        if (delta < 0 || elapsedMs <= 0) {
            return 0.0;
        }

        return delta * 1000.0 / elapsedMs;
    }

    private Session openSession(String host, String user, String password) throws Exception {
        JSch jsch = new JSch();
        HostAndPort hostAndPort = parseHostAndPort(host);
        Session session = jsch.getSession(user, hostAndPort.host(), hostAndPort.port());
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(SSH_CONNECT_TIMEOUT_MS);
        session.setTimeout(SSH_SOCKET_TIMEOUT_MS);
        return session;
    }

    private String sshExec(Session session, String command) throws Exception {
        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            InputStream in = channel.getInputStream();
            channel.connect(SSH_CONNECT_TIMEOUT_MS);

            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            return output.toString();
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
        }
    }

    private HostAndPort parseHostAndPort(String host) {
        String normalizedHost = host == null ? "" : host.trim();
        int port = 22;

        if (normalizedHost.contains(":")) {
            String[] parts = normalizedHost.split(":", 2);
            if (parts.length == 2) {
                normalizedHost = parts[0];
                try {
                    port = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {
                    port = 22;
                }
            }
        }

        return new HostAndPort(normalizedHost, port);
    }

    private double parseMetricValue(String str, String metricName) {
        String raw = safeTrim(str);
        if (isNetworkFailure(raw)) {
            throw new RuntimeException(MONITOR_FAILURE_SUMMARY);
        }

        try {
            return Double.parseDouble(raw);
        } catch (Exception e) {
            Matcher matcher = NUMERIC_PATTERN.matcher(raw.replace(",", "."));
            if (matcher.find()) {
                return Double.parseDouble(matcher.group());
            }
            throw new RuntimeException(metricName + "数据采集失败");
        }
    }

    private long[] parseCounterPair(String str, String metricName) {
        String raw = safeTrim(str);
        if (isNetworkFailure(raw)) {
            throw new RuntimeException(MONITOR_FAILURE_SUMMARY);
        }

        Matcher matcher = NUMERIC_PATTERN.matcher(raw.replace(",", "."));
        List<Long> values = new ArrayList<>(2);
        while (matcher.find()) {
            values.add((long) Double.parseDouble(matcher.group()));
            if (values.size() >= 2) {
                break;
            }
        }

        if (values.size() < 2) {
            throw new RuntimeException(metricName + "速率采集失败");
        }
        return new long[]{values.get(0), values.get(1)};
    }

    private boolean isNetworkFailure(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }

        return raw.startsWith("SSH Error:")
                || raw.contains("Connection timed out")
                || raw.contains("Connection refused")
                || raw.contains("No route to host")
                || raw.contains("Read timed out")
                || raw.contains("Auth fail")
                || raw.contains("timeout")
                || raw.contains("refused");
    }

    private MonitorSettings parseMonitorSettings(String configValue) {
        if (!StringUtils.hasText(configValue)) {
            return MonitorSettings.defaultEnabled();
        }

        try {
            Map<?, ?> raw = objectMapper.readValue(configValue, Map.class);
            return new MonitorSettings(
                    readSetting(raw, "cpuEnabled", true),
                    readSetting(raw, "memEnabled", true),
                    readSetting(raw, "netRxEnabled", true),
                    readSetting(raw, "netTxEnabled", true),
                    readSetting(raw, "diskReadEnabled", true),
                    readSetting(raw, "diskWriteEnabled", true)
            );
        } catch (Exception ignored) {
            return MonitorSettings.defaultEnabled();
        }
    }

    private boolean readSetting(Map<?, ?> raw, String key, boolean defaultValue) {
        if (raw == null || !raw.containsKey(key)) {
            return defaultValue;
        }

        Object value = raw.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private void createMonitorFailureInfo(String serverIp, String detailMessage) {
        List<ComponentConfig> configs = componentConfigMapper.selectList(
                new QueryWrapper<ComponentConfig>()
                        .select("DISTINCT user_id, server_ip")
                        .eq("config_key", SYSTEM_MONITOR_CONFIG_KEY)
                        .eq("is_enabled", 1)
                        .eq("server_ip", serverIp)
        );

        if (configs == null || configs.isEmpty()) {
            return;
        }

        Set<Long> userIds = new LinkedHashSet<>();
        for (ComponentConfig config : configs) {
            if (config != null && config.getUserId() != null) {
                userIds.add(config.getUserId());
            }
        }

        String normalizedRiskLevel = InfoNormalizationUtils.normalizeRiskLevel("高");
        String normalizedRawLog = InfoNormalizationUtils.normalizeText(detailMessage, MONITOR_FAILURE_SUMMARY);

        for (Long userId : userIds) {
            Information info = new Information();
            info.setUserId(userId);
            info.setServerIp(serverIp);
            info.setComponent(MONITOR_FAILURE_COMPONENT);
            info.setErrorSummary(MONITOR_FAILURE_SUMMARY);
            info.setAnalysisResult(MONITOR_FAILURE_ANALYSIS);
            info.setSuggestedActions(InfoNormalizationUtils.normalizeSuggestedActions(
                    "检查 SSH 网络连通性；确认服务器在线；检查 SSH 端口、账号密码和防火墙配置。",
                    normalizedRiskLevel
            ));
            info.setRawLog(normalizedRawLog);
            info.setRiskLevel(normalizedRiskLevel);
            info.setCreatedAt(LocalDateTime.now());
            informationMapper.insert(info);
        }
    }

    /**
     * 获取指定IP的历史趋势数据
     */
    public List<MetricDTO> getHistory(String ip) {
        return historyMap.getOrDefault(ip, Collections.emptyList());
    }

    /**
     * 获取指定IP的当前详细硬件信息
     */
    public Map<String, Object> getRealtime(String ip) {
        return currentInfoMap.getOrDefault(ip, Collections.emptyMap());
    }
    
    /**
     * 获取所有已监控的IP列表
     */
    public List<String> getMonitoredIps() {
        return new ArrayList<>(currentInfoMap.keySet());
    }

    public List<String> getMonitoredIpsByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Collections.emptyList();
        }
        UserLogin user = userLoginMapper.selectOne(
                new QueryWrapper<UserLogin>().eq("username", username)
        );
        if (user == null) {
            return Collections.emptyList();
        }

        List<ComponentConfig> ownedConfigs = componentConfigMapper.selectList(
                new QueryWrapper<ComponentConfig>()
                        .select("DISTINCT server_ip")
                        .eq("config_key", SYSTEM_MONITOR_CONFIG_KEY)
                        .eq("user_id", user.getId())
        );
        Set<String> ownedIps = new LinkedHashSet<>();
        for (ComponentConfig cfg : ownedConfigs) {
            String serverIp = normalizeServerIp(cfg == null ? null : cfg.getServerIp());
            if (StringUtils.hasText(serverIp)) {
                ownedIps.add(serverIp);
            }
        }
        return new ArrayList<>(ownedIps);
    }

    public boolean isServerMonitorEnabledByUsername(String username, String serverIp) {
        if (username == null || username.isBlank() || serverIp == null || serverIp.isBlank()) {
            return false;
        }

        UserLogin user = userLoginMapper.selectOne(
                new QueryWrapper<UserLogin>().eq("username", username)
        );
        if (user == null || user.getId() == null) {
            return false;
        }

        ComponentConfig config = componentConfigMapper.selectOne(
                new QueryWrapper<ComponentConfig>()
                        .eq("user_id", user.getId())
                        .eq("server_ip", serverIp.trim())
                        .eq("config_key", SYSTEM_MONITOR_CONFIG_KEY)
                        .orderByDesc("updated_at")
                        .last("LIMIT 1")
        );
        return config != null && !Integer.valueOf(0).equals(config.getIsEnabled());
    }

    /**
     * 提供给 Agent 的监控数据读取入口：
     * - serverIp: 目标服务器 IP
     * - timeRange: 例如 30m / 1h / 2h
     * 返回值是可直接序列化为 JSON 的结构。
     */
    public Map<String, Object> getMetrics(String serverIp, String timeRange) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("serverIp", serverIp);
        result.put("timeRange", timeRange);
        result.put("sampleIntervalMs", monitorFixedRateMs);
        result.put("current", getRealtime(serverIp));

        List<MetricDTO> fullHistory = historyMap.getOrDefault(serverIp, Collections.emptyList());
        int points = calcPointsByRange(timeRange, monitorFixedRateMs);
        int startIdx = Math.max(0, fullHistory.size() - points);
        List<MetricDTO> sliced = new ArrayList<>(fullHistory.subList(startIdx, fullHistory.size()));

        result.put("history", sliced);
        result.put("historyPoints", sliced.size());
        result.put("summary", buildSummary(sliced));
        return result;
    }

    /**
     * 当历史为空时，允许 Agent 触发一次即时采样。
     * 采样成功会写入 historyMap / currentInfoMap，便于后续 getMetrics 直接拿到数据。
     */
    public Map<String, Object> sampleMetricsOnce(String serverIp, String username, String password) {
        return sampleMetricsOnce(serverIp, username, password, null);
    }

    public Map<String, Object> sampleMetricsOnce(String serverIp, String username, String password, String configValue) {
        Map<String, Object> sample = new LinkedHashMap<>();
        String normalizedServerIp = normalizeServerIp(serverIp);
        sample.put("serverIp", normalizedServerIp);

        if (!StringUtils.hasText(normalizedServerIp)
                || !StringUtils.hasText(username)
                || !StringUtils.hasText(password)) {
            sample.put("success", false);
            sample.put("error", "serverIp/username/password 不能为空");
            return sample;
        }

        MonitorSettings settings = parseMonitorSettings(configValue);
        if (!settings.hasAnyMetricEnabled()) {
            counterSnapshotMap.remove(normalizedServerIp);
            cacheDisabledSnapshot(normalizedServerIp, settings);
            sample.put("success", true);
            sample.put("message", "当前未启用任何系统指标采集项");
            sample.put("current", currentInfoMap.get(normalizedServerIp));
            sample.put("monitorSettings", settings.toMap());
            return sample;
        }

        try {
            MetricSnapshot snapshot = collectMetricSnapshot(normalizedServerIp, username, password, settings);
            MetricDTO metric = cacheMetricSnapshot(normalizedServerIp, snapshot);

            sample.put("success", true);
            sample.put("metric", metric);
            sample.put("current", currentInfoMap.get(normalizedServerIp));
            sample.put("monitorSettings", settings.toMap());
            return sample;
        } catch (Exception e) {
            sample.put("success", false);
            sample.put("error", e.getMessage());
            return sample;
        }
    }

    public void clearServerCache(String serverIp) {
        String normalizedServerIp = normalizeServerIp(serverIp);
        if (!StringUtils.hasText(normalizedServerIp)) {
            return;
        }
        historyMap.remove(normalizedServerIp);
        currentInfoMap.remove(normalizedServerIp);
        counterSnapshotMap.remove(normalizedServerIp);
    }

    private int calcPointsByRange(String timeRange, long intervalMs) {
        if (intervalMs <= 0) {
            intervalMs = 60000;
        }
        if (timeRange == null || timeRange.isBlank()) {
            return 30;
        }

        String s = timeRange.trim().toLowerCase(Locale.ROOT);
        try {
            long minutes;
            if (s.endsWith("m")) {
                minutes = Long.parseLong(s.substring(0, s.length() - 1));
            } else if (s.endsWith("h")) {
                minutes = Long.parseLong(s.substring(0, s.length() - 1)) * 60;
            } else {
                minutes = 30;
            }
            long totalMs = minutes * 60_000L;
            return (int) Math.max(1, Math.ceil((double) totalMs / intervalMs));
        } catch (Exception ignored) {
            return 30;
        }
    }

    private Map<String, Object> buildSummary(List<MetricDTO> metrics) {
        Map<String, Object> summary = new LinkedHashMap<>();
        MetricAccumulator cpuStats = new MetricAccumulator();
        MetricAccumulator memStats = new MetricAccumulator();
        MetricAccumulator netRxStats = new MetricAccumulator();
        MetricAccumulator netTxStats = new MetricAccumulator();
        MetricAccumulator diskReadStats = new MetricAccumulator();
        MetricAccumulator diskWriteStats = new MetricAccumulator();

        if (metrics != null) {
            for (MetricDTO metric : metrics) {
                cpuStats.accept(metric.getCpuUsage());
                memStats.accept(metric.getMemUsage());
                netRxStats.accept(metric.getNetRxBytesPerSec());
                netTxStats.accept(metric.getNetTxBytesPerSec());
                diskReadStats.accept(metric.getDiskReadBytesPerSec());
                diskWriteStats.accept(metric.getDiskWriteBytesPerSec());
            }
        }

        summary.put("avgCpu", cpuStats.average());
        summary.put("maxCpu", cpuStats.max());
        summary.put("avgMem", memStats.average());
        summary.put("maxMem", memStats.max());
        summary.put("avgNetRxBytesPerSec", netRxStats.average());
        summary.put("maxNetRxBytesPerSec", netRxStats.max());
        summary.put("avgNetTxBytesPerSec", netTxStats.average());
        summary.put("maxNetTxBytesPerSec", netTxStats.max());
        summary.put("avgDiskReadBytesPerSec", diskReadStats.average());
        summary.put("maxDiskReadBytesPerSec", diskReadStats.max());
        summary.put("avgDiskWriteBytesPerSec", diskWriteStats.average());
        summary.put("maxDiskWriteBytesPerSec", diskWriteStats.max());
        return summary;
    }

    private Double roundNullable(Double value) {
        if (value == null || !Double.isFinite(value)) {
            return null;
        }
        return Math.round(value * 10.0) / 10.0;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeServerIp(String serverIp) {
        return StringUtils.hasText(serverIp) ? serverIp.trim() : null;
    }

    private record MetricSnapshot(
            Double cpuUsage,
            Double memUsage,
            Double netRxBytesPerSec,
            Double netTxBytesPerSec,
            Double diskReadBytesPerSec,
            Double diskWriteBytesPerSec,
            String upTime,
            String os,
            String totalMem,
            String availableMem,
            MonitorSettings settings
    ) {
    }

    private record RateSnapshot(
            Double netRxBytesPerSec,
            Double netTxBytesPerSec,
            Double diskReadBytesPerSec,
            Double diskWriteBytesPerSec
    ) {
    }

    private record RawCounterSnapshot(
            long collectedAtMs,
            Long netRxBytes,
            Long netTxBytes,
            Long diskReadBytes,
            Long diskWriteBytes
    ) {
    }

    private record MonitorSettings(
            boolean cpuEnabled,
            boolean memEnabled,
            boolean netRxEnabled,
            boolean netTxEnabled,
            boolean diskReadEnabled,
            boolean diskWriteEnabled
    ) {
        private static MonitorSettings defaultEnabled() {
            return new MonitorSettings(true, true, true, true, true, true);
        }

        private boolean hasAnyMetricEnabled() {
            return cpuEnabled || memEnabled || netRxEnabled || netTxEnabled || diskReadEnabled || diskWriteEnabled;
        }

        private boolean hasAnyNetworkMetricEnabled() {
            return netRxEnabled || netTxEnabled;
        }

        private boolean hasAnyDiskMetricEnabled() {
            return diskReadEnabled || diskWriteEnabled;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("cpuEnabled", cpuEnabled);
            map.put("memEnabled", memEnabled);
            map.put("netRxEnabled", netRxEnabled);
            map.put("netTxEnabled", netTxEnabled);
            map.put("diskReadEnabled", diskReadEnabled);
            map.put("diskWriteEnabled", diskWriteEnabled);
            return map;
        }
    }

    private static final class MetricAccumulator {
        private double sum;
        private double max;
        private int count;

        private void accept(Double value) {
            if (value == null || !Double.isFinite(value)) {
                return;
            }
            sum += value;
            max = Math.max(max, value);
            count++;
        }

        private double average() {
            if (count <= 0) {
                return 0.0;
            }
            return Math.round((sum / count) * 10.0) / 10.0;
        }

        private double max() {
            if (count <= 0) {
                return 0.0;
            }
            return Math.round(max * 10.0) / 10.0;
        }
    }

    private record HostAndPort(String host, int port) {
    }
}

