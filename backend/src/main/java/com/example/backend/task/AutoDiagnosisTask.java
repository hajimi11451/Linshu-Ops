package com.example.backend.task;

import com.example.backend.entity.ComponentConfig;
import com.example.backend.service.DiagnosisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 自动诊断定时任务
 * 定期扫描监控配置，执行诊断并保存结果
 */
@Slf4j
@Component
public class AutoDiagnosisTask {

    @Autowired
    private DiagnosisService diagnosisService;

    /**
     * 每 5 分钟执行一次
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void runAutoDiagnosis() {
        log.info("开始执行自动诊断任务...");
        
        List<ComponentConfig> configs = diagnosisService.listAllConfigsForAutoTask();
        log.info("自动诊断扫描到配置数量: {}", configs.size());
        if (configs == null || configs.isEmpty()) {
            log.info("当前没有监控配置，跳过自动诊断。");
            return;
        }

        for (ComponentConfig config : configs) {
            diagnosisService.diagnoseAndSave(config);
        }
        
        log.info("自动诊断任务执行完成。");
    }
}
