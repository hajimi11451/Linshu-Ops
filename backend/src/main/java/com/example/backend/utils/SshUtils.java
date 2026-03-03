package com.example.backend.utils;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * SSH 工具类
 */
@Slf4j
@Component
public class SshUtils {

    /**
     * 执行 SSH 命令（默认凭据，仅兼容旧逻辑）
     */
    public String exec(String ip, String command) {
        return exec(ip, "root", "123456", command);
    }

    /**
     * 测试 SSH 连通性
     */
    public boolean testConnection(String ip, String user, String password) {
        Session session = null;
        try {
            JSch jsch = new JSch();
            HostAndPort hostAndPort = parseHostAndPort(ip);

            session = jsch.getSession(user, hostAndPort.host(), hostAndPort.port());
            session.setPassword(password);

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.setTimeout(5000);

            session.connect();
            log.info("SSH 连接测试成功: {}@{}:{}", user, hostAndPort.host(), hostAndPort.port());
            return true;
        } catch (Exception e) {
            log.error("SSH 连接测试失败: {}@{} - {}", user, ip, e.getMessage());
            return false;
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    public String exec(String ip, String user, String password, String command) {
        log.info("正在服务器 {} 上执行命令: {}", ip, command);

        Session session = null;
        ChannelExec channel = null;

        try {
            JSch jsch = new JSch();
            HostAndPort hostAndPort = parseHostAndPort(ip);

            session = jsch.getSession(user, hostAndPort.host(), hostAndPort.port());
            session.setPassword(password);

            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.setTimeout(5000);
            session.connect();

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);

            InputStream stdout = channel.getInputStream();
            InputStream stderr = channel.getErrStream();
            ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
            ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();

            channel.connect();

            byte[] temp = new byte[1024];
            while (true) {
                while (stdout.available() > 0) {
                    int len = stdout.read(temp, 0, temp.length);
                    if (len > 0) {
                        outBuffer.write(temp, 0, len);
                    }
                }

                while (stderr.available() > 0) {
                    int len = stderr.read(temp, 0, temp.length);
                    if (len > 0) {
                        errBuffer.write(temp, 0, len);
                    }
                }

                if (channel.isClosed()) {
                    // drain remaining bytes after channel close
                    while (stdout.available() > 0) {
                        int len = stdout.read(temp, 0, temp.length);
                        if (len > 0) {
                            outBuffer.write(temp, 0, len);
                        }
                    }
                    while (stderr.available() > 0) {
                        int len = stderr.read(temp, 0, temp.length);
                        if (len > 0) {
                            errBuffer.write(temp, 0, len);
                        }
                    }
                    break;
                }

                Thread.sleep(50);
            }

            String outText = outBuffer.toString(StandardCharsets.UTF_8);
            String errText = errBuffer.toString(StandardCharsets.UTF_8);
            int exitStatus = channel.getExitStatus();

            String merged = (outText + (errText.isEmpty() ? "" : errText)).trim();
            if (exitStatus != 0) {
                log.warn("命令执行失败，exitCode={}, output={}", exitStatus, merged);
                if (merged.isEmpty()) {
                    return "Command failed with exit code " + exitStatus;
                }
                return merged;
            }

            log.info("命令执行成功，返回长度: {}", merged.length());
            return merged;
        } catch (Exception e) {
            log.error("SSH 执行异常: {}", e.getMessage(), e);
            return "SSH Error: " + e.getMessage();
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }

    private HostAndPort parseHostAndPort(String ip) {
        String host = ip;
        int port = 22;

        if (ip != null && ip.contains(":")) {
            String[] parts = ip.split(":");
            if (parts.length == 2) {
                host = parts[0];
                try {
                    port = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    log.warn("端口解析失败，使用默认 22: {}", ip);
                }
            }
        }
        return new HostAndPort(host, port);
    }

    private record HostAndPort(String host, int port) {}
}
