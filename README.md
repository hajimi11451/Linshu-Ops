# AIOps Platform

一个前后端分离的智能运维平台，支持服务器监控、自动诊断、AI 聊天助手、远程命令规划与执行。

## 功能概览

- 系统监控：展示 CPU、内存等实时/历史指标
- 智能诊断：自动读取日志并调用 AI 进行分析
- 监控配置：按用户维护服务器与组件配置
- 灵枢助手（AI 聊天）：
  - WebSocket 实时对话
  - AI 先规划命令，再按确认执行
  - 支持分步 `planSteps`（先检测后执行）
  - 过程进度实时推送（不额外消耗 token）

## 技术栈

- 后端：Spring Boot 3、MyBatis-Plus、MySQL、JSch、WebSocket
- 前端：Vue 3、Vite、Element Plus、Tailwind CSS、Axios、Chart.js

## 目录结构

```text
aiOps/
├─ backend/
│  ├─ src/main/java
│  ├─ src/main/resources
│  │  ├─ application.yml
│  │  └─ info.md
│  └─ pom.xml
├─ front/
│  ├─ src/
│  │  ├─ views/
│  │  └─ router/
│  ├─ package.json
│  └─ vite.config.js
├─ sql/
│  └─ schema.sql
└─ README.md
```

## 运行环境

- JDK 17+
- Maven 3.8+
- Node.js 18+
- MySQL 8+

## 快速启动

### 1) 初始化数据库

1. 创建数据库：`ai_ops_db`
2. 执行脚本：`sql/schema.sql`

### 2) 配置后端

编辑 `backend/src/main/resources/application.yml`：

- `spring.datasource.*`：MySQL 连接
- `qianfan.v2.base-url`：千帆 v2 地址（默认已配置）
- `qianfan.v2.audit-model-name`：日志分析模型
- `qianfan.v2.chat-model-name`：聊天助手模型
- `qianfan.v2.read-timeout-seconds`：AI 请求读取超时（秒）

**重要：token 不再写死在 yml，使用环境变量**

- Windows PowerShell：

```powershell
$env:QIANFAN_V2_TOKEN="你的token"
```

- Linux/macOS：

```bash
export QIANFAN_V2_TOKEN="你的token"
```

### 3) 启动后端

```bash
cd backend
mvn clean package -DskipTests
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

默认地址：`http://localhost:8080`

### 4) 启动前端

```bash
cd front
npm install
npm run dev
```

默认地址：`http://localhost:5173`

## 灵枢助手说明

- 页面入口：侧边栏 `灵枢助手`
- 通信方式：`/ws/server/connect`
- 消息类型：
  - `ops_chat`：提交聊天/规划/执行请求
  - `ops_progress`：后端过程进度推送
  - `ops_chat_result`：最终结果

### 典型请求（前端发送）

```json
{
  "type": "ops_chat",
  "query": "安装 nginx 并设置开机自启",
  "execute": false,
  "serverIp": "192.168.1.10",
  "username": "root",
  "password": "***"
}
```

## 常见问题

### 1) `401 Unauthorized`

通常是 token 无效/过期/权限不足。请在千帆控制台重置 token，并更新环境变量 `QIANFAN_V2_TOKEN`。

### 2) `Unexpected end of file from server` 或 `Read timed out`

是 AI 接口链路/网关超时问题。可尝试：

- 调大 `qianfan.v2.read-timeout-seconds`
- 简化提问内容
- 重试请求

### 3) SSH 执行提示权限不足

如 `apt-get` 相关错误，说明当前用户无 root 权限。请使用 root 或 `sudo` 可用账号。

## 安全建议

- 不要把 token、服务器密码提交到 Git
- 建议将敏感配置放环境变量或密钥管理系统
- 已泄露的 token 请立即作废并重置
