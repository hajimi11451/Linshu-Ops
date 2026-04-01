package com.example.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.backend.entity.ComponentConfig;
import com.example.backend.entity.Information;
import com.example.backend.entity.UserLogin;
import com.example.backend.entity.UserProcess;
import com.example.backend.mapper.ComponentConfigMapper;
import com.example.backend.mapper.InformationMapper;
import com.example.backend.mapper.UserLoginMapper;
import com.example.backend.mapper.UserProcessMapper;
import com.example.backend.service.InfoService;
import com.example.backend.utils.InfoNormalizationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class InfoServiceImpl implements InfoService {

    @Autowired
    private InformationMapper informationMapper;

    @Autowired
    private UserProcessMapper userProcessMapper;

    @Autowired
    private ComponentConfigMapper componentConfigMapper;

    @Autowired
    private UserLoginMapper userLoginMapper;

    private Long resolveUserId(Map<String, String> request) {
        String username = request.get("username");
        if (username != null && !username.isBlank()) {
            QueryWrapper<UserLogin> wrapper = new QueryWrapper<>();
            wrapper.eq("username", username);
            UserLogin user = userLoginMapper.selectOne(wrapper);
            return user == null ? null : user.getId();
        }

        String userId = request.get("userId");
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @Override
    public List<Information> selectAllInfo(Map<String, String> request) {
        Long userId = resolveUserId(request);
        QueryWrapper<Information> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId == null ? -1L : userId);
        queryWrapper.orderByDesc("created_at");

        List<Information> response = informationMapper.selectList(queryWrapper);
        normalizeInformationList(response);
        return response.isEmpty() ? null : response;
    }

    @Override
    public List<Information> selectInfo(Map<String, String> request) {
        Long userId = resolveUserId(request);
        String id = request.get("id");
        String serverIp = request.get("serverIp");
        String component = request.get("component");
        String errorSummary = request.get("errorSummary");
        String riskLevel = request.get("riskLevel");
        if (riskLevel != null && !riskLevel.isBlank()) {
            riskLevel = InfoNormalizationUtils.normalizeRiskLevel(riskLevel);
        }

        LocalDateTime startTime = null;
        LocalDateTime endTime = null;
        if (request.get("startTime") != null && request.get("endTime") != null) {
            try {
                startTime = LocalDateTime.parse(request.get("startTime"));
                endTime = LocalDateTime.parse(request.get("endTime"));
            } catch (Exception ignored) {
            }
        }

        QueryWrapper<Information> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId == null ? -1L : userId);
        if (id != null && !id.isBlank()) {
            queryWrapper.eq("id", id);
        }
        if (serverIp != null && !serverIp.isBlank()) {
            queryWrapper.eq("server_ip", serverIp);
        }
        if (component != null && !component.isBlank()) {
            queryWrapper.eq("component", component);
        }
        if (errorSummary != null && !errorSummary.isBlank()) {
            queryWrapper.eq("error_summary", errorSummary);
        }
        if (riskLevel != null && !riskLevel.isBlank()) {
            queryWrapper.eq("risk_level", riskLevel);
        }
        if (startTime != null && endTime != null) {
            queryWrapper.between("created_at", startTime, endTime);
        }

        List<Information> response = informationMapper.selectList(queryWrapper);
        normalizeInformationList(response);
        return response.isEmpty() ? null : response;
    }

    @Override
    public String insertProcess(Map<String, String> request) {
        Long userId = resolveUserId(request);
        String serverIp = request.get("serverIp");
        String component = request.get("component");
        String problemLog = resolveProblemLog(request);
        String processMethod = request.get("processMethod");

        LocalDateTime processTime = null;
        String processTimeText = request.get("processTime");
        if (processTimeText == null || processTimeText.isBlank()) {
            processTimeText = request.get("endTime");
        }
        if (processTimeText != null && !processTimeText.isBlank()) {
            try {
                processTime = LocalDateTime.parse(processTimeText);
            } catch (Exception ignored) {
            }
        }
        if (processTime == null) {
            processTime = LocalDateTime.now();
        }

        if (userId == null
                || serverIp == null || serverIp.isBlank()
                || component == null || component.isBlank()
                || processMethod == null || processMethod.isBlank()) {
            return "输入参数不能为空";
        }

        UserProcess userProcess = new UserProcess();
        userProcess.setUserId(userId);
        userProcess.setServerIp(serverIp);
        userProcess.setComponent(component);
        userProcess.setProblemLog(problemLog);
        userProcess.setProcessMethod(processMethod);
        userProcess.setProcessTime(processTime);

        int result = userProcessMapper.insert(userProcess);
        return result == 1 ? "录入成功" : "录入失败";
    }

    @Override
    public List<UserProcess> selectAllProcess(Map<String, String> request) {
        Long userId = resolveUserId(request);
        QueryWrapper<UserProcess> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId == null ? -1L : userId);
        queryWrapper.orderByDesc("process_time");

        List<UserProcess> response = userProcessMapper.selectList(queryWrapper);
        return response.isEmpty() ? null : response;
    }

    @Override
    public List<UserProcess> selectProcess(Map<String, String> request) {
        Long userId = resolveUserId(request);
        String serverIp = request.get("serverIp");
        String component = request.get("component");

        LocalDateTime startTime = null;
        LocalDateTime endTime = null;
        if (request.get("startTime") != null && request.get("endTime") != null) {
            try {
                startTime = LocalDateTime.parse(request.get("startTime"));
                endTime = LocalDateTime.parse(request.get("endTime"));
            } catch (Exception ignored) {
            }
        }

        QueryWrapper<UserProcess> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId == null ? -1L : userId);
        if (serverIp != null && !serverIp.isBlank()) {
            queryWrapper.eq("server_ip", serverIp);
        }
        if (component != null && !component.isBlank()) {
            queryWrapper.eq("component", component);
        }
        if (startTime != null && endTime != null) {
            queryWrapper.between("process_time", startTime, endTime);
        }
        queryWrapper.orderByDesc("process_time");

        List<UserProcess> response = userProcessMapper.selectList(queryWrapper);
        return response.isEmpty() ? null : response;
    }

    @Override
    public int deleteAllInfo(Map<String, String> request) {
        Long userId = resolveUserId(request);
        if (userId == null) {
            return 0;
        }

        QueryWrapper<Information> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        return informationMapper.delete(queryWrapper);
    }

    @Override
    public int deleteInfo(Map<String, String> request) {
        Long userId = resolveUserId(request);
        if (userId == null) {
            return 0;
        }

        String id = request.get("id");
        if (!StringUtils.hasText(id)) {
            return 0;
        }

        QueryWrapper<Information> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.eq("id", id.trim());
        return informationMapper.delete(queryWrapper);
    }

    @Override
    public int deleteInfoByServerIp(Map<String, String> request) {
        Long userId = resolveUserId(request);
        if (userId == null) {
            return 0;
        }

        String serverIp = request.get("serverIp");
        if (!StringUtils.hasText(serverIp)) {
            return 0;
        }

        QueryWrapper<Information> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.eq("server_ip", serverIp.trim());
        return informationMapper.delete(queryWrapper);
    }

    private void normalizeInformationList(List<Information> response) {
        if (response == null || response.isEmpty()) {
            return;
        }

        Map<String, List<ComponentConfig>> configCache = new HashMap<>();

        for (Information info : response) {
            if (info == null) {
                continue;
            }

            String normalizedRiskLevel = InfoNormalizationUtils.normalizeRiskLevel(info.getRiskLevel());
            info.setComponent(resolveDisplayComponent(info, configCache));
            info.setErrorSummary(InfoNormalizationUtils.normalizeText(info.getErrorSummary(), "无"));
            info.setAnalysisResult(InfoNormalizationUtils.normalizeText(info.getAnalysisResult(), "无"));
            info.setRawLog(InfoNormalizationUtils.normalizeText(info.getRawLog(), "无"));
            info.setRiskLevel(normalizedRiskLevel);
            info.setSuggestedActions(InfoNormalizationUtils.normalizeSuggestedActions(info.getSuggestedActions(), normalizedRiskLevel));
        }
    }

    private String resolveDisplayComponent(Information info, Map<String, List<ComponentConfig>> configCache) {
        String currentComponent = info.getComponent();
        List<ComponentConfig> configs = loadConfigsByUserAndServer(info.getUserId(), info.getServerIp(), configCache);
        if (configs.isEmpty()) {
            return InfoNormalizationUtils.normalizeComponent(currentComponent);
        }

        String matchedComponent = matchConfiguredComponent(currentComponent, configs);
        if (StringUtils.hasText(matchedComponent)) {
            return matchedComponent;
        }

        if (InfoNormalizationUtils.isUnknownComponent(currentComponent) && configs.size() == 1) {
            return InfoNormalizationUtils.normalizeComponent(configs.get(0).getComponent());
        }

        return InfoNormalizationUtils.normalizeComponent(currentComponent);
    }

    private List<ComponentConfig> loadConfigsByUserAndServer(Long userId,
                                                             String serverIp,
                                                             Map<String, List<ComponentConfig>> configCache) {
        if (userId == null || !StringUtils.hasText(serverIp)) {
            return Collections.emptyList();
        }

        String cacheKey = userId + "@" + serverIp.trim();
        if (configCache.containsKey(cacheKey)) {
            return configCache.get(cacheKey);
        }

        QueryWrapper<ComponentConfig> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.eq("server_ip", serverIp.trim());

        List<ComponentConfig> configs = componentConfigMapper.selectList(queryWrapper);
        configCache.put(cacheKey, configs == null ? Collections.emptyList() : configs);
        return configCache.get(cacheKey);
    }

    private String matchConfiguredComponent(String currentComponent, List<ComponentConfig> configs) {
        String currentKey = normalizeLookupKey(currentComponent);
        if (!StringUtils.hasText(currentKey)) {
            return null;
        }

        for (ComponentConfig config : configs) {
            if (config == null || !StringUtils.hasText(config.getComponent())) {
                continue;
            }

            String configuredComponent = InfoNormalizationUtils.normalizeComponent(config.getComponent());
            String configuredKey = normalizeLookupKey(configuredComponent);
            if (!StringUtils.hasText(configuredKey)) {
                continue;
            }

            if (configuredKey.equals(currentKey)
                    || configuredKey.contains(currentKey)
                    || currentKey.contains(configuredKey)) {
                return configuredComponent;
            }
        }
        return null;
    }

    private String normalizeLookupKey(String value) {
        if (InfoNormalizationUtils.isUnknownComponent(value)) {
            return "";
        }

        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace("服务", "")
                .replaceAll("[\\s_-]+", "");
    }

    private String resolveProblemLog(Map<String, String> request) {
        String[] candidates = {"problemLog", "problem", "issue", "rawLog", "logContent", "logInfo"};
        for (String key : candidates) {
            String value = request.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
