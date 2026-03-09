package com.example.backend.controller;

import com.example.backend.service.DiagnosisService;
import com.example.backend.service.ServerService;
import com.example.backend.utils.AiUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 诊断与 AI 接口
 */
@Slf4j
@RestController
@RequestMapping("/diagnosis")
public class DiagnosisController {

    @Autowired
    private DiagnosisService diagnosisService;

    @Autowired
    private AiUtils aiUtils;

    @Autowired
    private ServerService serverService;

    /**
     * 1. 获取日志路径 (用于路径发现)
     */
    @GetMapping("/logPath")
    public ResponseEntity<?> getLogPath(@RequestParam String serverIp, 
                                      @RequestParam String component,
                                      @RequestParam(required = false) String username,
                                      @RequestParam(required = false) String password) {
        String path = diagnosisService.getLogPath(serverIp, component, username, password);
        
        Map<String, Object> data = new HashMap<>();
        data.put("path", path);
        data.put("isVerified", true);
        
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("data", data);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 5. 添加监控配置
     * 会先验证SSH连接，若失败则创建Information记录并返回错误信息
     */
    @PostMapping("/config/add")
    public ResponseEntity<?> addConfig(@RequestBody com.example.backend.entity.ComponentConfig config) {
        try {
            diagnosisService.addConfig(config);
            
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("msg", "配置添加成功");
            
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            // SSH连接失败等异常，返回错误信息给前端
            Map<String, Object> response = new HashMap<>();
            response.put("code", 400);
            response.put("msg", e.getMessage());
            response.put("data", null);
            
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("添加配置时发生未知错误", e);
            Map<String, Object> response = new HashMap<>();
            response.put("code", 500);
            response.put("msg", "服务器内部错误: " + e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/server-monitor/add")
    public ResponseEntity<?> addServerMonitor(@RequestBody com.example.backend.entity.ComponentConfig config) {
        try {
            Map<String, Object> data = diagnosisService.addServerMonitor(config);

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("msg", "服务器监控添加成功");
            response.put("data", data);

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("code", 400);
            response.put("msg", e.getMessage());
            response.put("data", null);

            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("添加服务器监控时发生未知错误", e);
            Map<String, Object> response = new HashMap<>();
            response.put("code", 500);
            response.put("msg", "服务器内部错误: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 6. 获取监控配置列表
     */
    @GetMapping("/config/list")
    public ResponseEntity<?> listConfigs(@RequestParam(required = false) String username) {
        List<com.example.backend.entity.ComponentConfig> list = diagnosisService.listConfigs(username);
        
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("data", list);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 7. 删除监控配置
     */
    @PostMapping("/config/delete")
    public ResponseEntity<?> deleteConfig(@RequestBody Map<String, Object> request) {
        Long id = request.get("id") == null ? null : Long.valueOf(String.valueOf(request.get("id")));
        String username = request.get("username") == null ? null : String.valueOf(request.get("username"));
        if (id != null) {
            diagnosisService.deleteConfig(id, username);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("msg", "删除成功");
        
        return ResponseEntity.ok(response);
    }

    /**
     * 2. 执行诊断
     */
    @PostMapping("/execute")
    public ResponseEntity<?> executeDiagnosis(@RequestBody Map<String, String> request) {
        String serverIp = request.get("serverIp");
        String component = request.get("component");
        String logPath = request.get("logPath");
        String username = request.get("username");
        String password = request.get("password");

        Map<String, Object> diagnosisResult = diagnosisService.executeDiagnosis(serverIp, component, logPath, username, password);

        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("data", diagnosisResult);

        return ResponseEntity.ok(response);
    }

    /**
     * 3. AI 智能问答 (RAG Chat)
     */
    @PostMapping("/ai/chat")
    public ResponseEntity<?> chat(@RequestBody Map<String, String> request) {
        String query = request.get("query");
        String answer = aiUtils.chatWithOpsAssistant(query);

        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("data", answer);

        return ResponseEntity.ok(response);
    }

    /**
     * 仅测试 AI 日志分析（不依赖 SSH），用于验证千帆接口与 AI 判断是否正常。
     * 请求体: { "logContent": "一段日志文本" }
     */
    @PostMapping("/ai/analyze")
    public ResponseEntity<?> analyzeLogOnly(@RequestBody Map<String, String> request) {
        String logContent = request.get("logContent");
        if (logContent == null) {
            logContent = "";
        }
        String result = aiUtils.analyzeLog(logContent);
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("data", result);
        return ResponseEntity.ok(response);
    }

    /**
     * 4. 连接服务器 (资源管理)
     */
    @PostMapping("/server/connect")
    public ResponseEntity<?> connectServer(@RequestBody Map<String, String> request) {
        String ip = request.get("ip");
        String user = request.get("user");
        String password = request.get("password");

        List<String> components = serverService.connectAndDetect(ip, user, password);

        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("msg", "连接成功");
        response.put("data", components);

        return ResponseEntity.ok(response);
    }
}
