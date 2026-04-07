package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("componentconfig")
public class ComponentConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long userId;

    private String serverIp;

    /**
     * 服务器登录用户名
     */
    private String username;

    /**
     * 服务器登录密码
     */
    private String password;

    /**
     * 是否使用 sudo 读取日志
     */
    private Boolean useSudo;

    /**
     * 前端登录用户名（用于解析业务 userId），不落库
     */
    @TableField(exist = false)
    private String appUsername;

    @TableField(exist = false)
    private Boolean cpuEnabled;

    @TableField(exist = false)
    private Boolean memEnabled;

    @TableField(exist = false)
    private Boolean netRxEnabled;

    @TableField(exist = false)
    private Boolean netTxEnabled;

    @TableField(exist = false)
    private Boolean diskReadEnabled;

    @TableField(exist = false)
    private Boolean diskWriteEnabled;
    
    private String component;
    
    /**
     * 配置项 (固定为 error_log_path)
     */
    private String configKey;
    
    /**
     * 路径值
     */
    private String configValue;
    
    /**
     * 是否验证通过 (0=未验证, 1=已验证)
     */
    private Integer isVerified;

    /**
     * 是否启用检测 (0=暂停, 1=启用)
     */
    private Integer isEnabled;
    
    private LocalDateTime updatedAt;
}
