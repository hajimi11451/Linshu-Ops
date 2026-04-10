# LinshuOps - 智能运维助手平台

基于 Spring Boot + Vue 3 的 AI 驱动智能运维平台，集成大语言模型实现日志诊断、服务器监控、告警通知与自动化运维处置。

## 功能概览

### 服务器监控

- 通过 SSH 远程采集 Linux 服务器 CPU、内存、网卡流量、磁盘 I/O 等指标
- 支持多服务器并行监控，可按需启停单台服务器的监控
- 监控数据实时展示，历史趋势图表可视化（ECharts）
- 健康评分算法：综合告警等级与资源占用率自动计算 0-100 分健康评分

### 日志诊断

- 自动发现 Nginx、Tomcat、SSH 等组件的错误日志路径
- AI 分析日志内容，输出问题摘要、根因分析、处理建议与风险等级
- 每 5 分钟自动执行一轮诊断扫描，无需人工触发
- 支持手动添加/删除监控配置，可暂停/恢复单条监控

### 告警通知

- 高风险告警自动二次复检，确认后通过邮件通知
- 复检机制：重新采集指标或重新诊断，误报自动删除
- 邮件冷却期（默认 30 分钟），避免重复告警轰炸
- 每周自动清理超期告警数据

### 灵枢 AI 助手

- 对话式运维交互，自动识别用户意图（咨询 / 执行）
- Agent 编排引擎：AI 自动生成执行计划，逐步调用工具完成任务
- 内置工具：`execute_command`（远程执行命令）、`read_log`（读取日志）、`get_server_metrics`（获取监控数据）
- 高风险命令拦截：`rm -rf`、`shutdown`、`mkfs` 等命令需用户二次确认
- 前端/后端双重安全校验，防止危险命令误执行
- WebSocket 实时推送执行进度，支持中途强制停止

### RAG 知识库

- 运维知识文件（`info.md`）作为 RAG 上下文注入 AI 分析流程
- 每周自动从历史运维记录中提取高频规律，更新知识库
- 知识库内容可手动编辑，修改后即时生效

### 用户系统

- 注册 / 登录，数据按用户隔离
- 每位用户独立配置服务器连接、监控项与告警邮箱

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端框架 | Vue 3 + Vue Router |
| UI 组件库 | Element Plus |
| CSS 方案 | Tailwind CSS 4 |
| 图表 | ECharts 5 |
| HTTP 客户端 | Axios |
| 构建工具 | Vite 6 |
| 后端框架 | Spring Boot 3.4 |
| ORM | MyBatis-Plus 3.5 |
| 数据库 | MySQL 8 |
| SSH 连接 | JSch 0.2 |
| 实时通信 | Spring WebSocket |
| 邮件 | Spring Boot Mail（QQ 邮箱 SMTP） |
| AI 接口 | 百度千帆 OpenAI 兼容接口（GLM-5） |
| Java 版本 | 17 |

## 项目结构

```
LinshuOps/
├── backend/                          # 后端 Spring Boot 工程
│   ├── src/main/java/com/example/backend/
│   │   ├── agent/                    # Agent 编排引擎
│   │   │   ├── AgentOrchestrator.java    # 任务编排与执行主循环
│   │   │   ├── AgentExecutionContext.java # Agent 运行上下文
│   │   │   ├── PlannerService.java       # AI 执行计划生成
│   │   │   └── ...                       # 异常定义
│   │   ├── config/                   # 配置类
│   │   ├── controller/               # REST 控制器
│   │   │   ├── MonitorController.java     # 监控仪表盘接口
│   │   │   ├── DiagnosisController.java   # 诊断与 AI 接口
│   │   │   ├── AlertController.java       # 告警通知接口
│   │   │   ├── InfoConttroller.java       # 巡检记录接口
│   │   │   └── UserController.java        # 用户注册登录接口
│   │   ├── entity/                   # 数据库实体
│   │   ├── dto/                      # 数据传输对象
│   │   ├── mapper/                   # MyBatis-Plus Mapper
│   │   ├── model/                    # Agent 模型（Step, TaskState 等）
│   │   ├── service/                  # 业务逻辑层
│   │   │   ├── MonitorService.java        # 系统监控采集与缓存
│   │   │   ├── HealthService.java         # 健康评分计算
│   │   │   ├── DiagnosisService.java      # 日志诊断
│   │   │   ├── OpsAgentService.java       # Agent 服务入口
│   │   │   ├── CommandSafetyService.java  # 命令安全校验
│   │   │   ├── AlertMailService.java      # 告警邮件发送
│   │   │   └── ...
│   │   ├── task/                     # 定时任务
│   │   │   ├── AutoDiagnosisTask.java     # 自动诊断（每 5 分钟）
│   │   │   ├── HealthAlertTask.java       # 告警复检与邮件通知
│   │   │   └── RagUpdateTask.java         # RAG 知识库更新（每周）
│   │   ├── tool/                     # Agent 工具
│   │   │   ├── AgentTool.java             # 工具接口
│   │   │   ├── ShellTool.java             # 远程命令执行
│   │   │   ├── LogTool.java               # 日志读取
│   │   │   ├── MetricsTool.java           # 监控数据查询
│   │   │   └── ToolRegistry.java          # 工具注册中心
│   │   ├── llm/                      # LLM 客户端
│   │   │   ├── LlmClient.java             # 千帆 API 调用
│   │   │   ├── LlmResponse.java           # 响应模型
│   │   │   └── Message.java               # 消息模型
│   │   ├── handler/                  # WebSocket 处理器
│   │   │   └── ServerWebSocketHandler.java # AI 助手 WebSocket 入口
│   │   ├── utils/                    # 工具类
│   │   │   ├── AiUtils.java               # AI 调用封装（日志分析/RAG/意图识别）
│   │   │   ├── SshUtils.java              # SSH 工具
│   │   │   └── InfoNormalizationUtils.java # 数据标准化
│   │   └── websocket/                # WebSocket 通知
│   │       └── WsNotifier.java            # Agent 进度推送
│   └── src/main/resources/
│       ├── application.yml           # 应用配置
│       └── info.md                   # RAG 知识库文件
├── front/                            # 前端 Vue 工程
│   ├── src/
│   │   ├── api/                      # API 请求封装
│   │   ├── views/                    # 页面组件
│   │   │   ├── Dashboard.vue             # 总览仪表盘
│   │   │   ├── DiagnosisView.vue         # 诊断配置页
│   │   │   ├── OpsAssistantView.vue      # 灵枢 AI 助手
│   │   │   ├── AutoExecutionView.vue     # 自动处置页
│   │   │   ├── InfoListView.vue          # 告警列表
│   │   │   ├── InfoDetailView.vue        # 告警详情
│   │   │   ├── AlertSettingsView.vue     # 通知邮箱设置
│   │   │   ├── Login.vue                 # 登录
│   │   │   └── Register.vue              # 注册
│   │   ├── components/               # 公共组件
│   │   ├── layouts/                  # 布局组件
│   │   ├── router/                   # 路由配置
│   │   └── utils/                    # 工具函数
│   └── vite.config.js                # Vite 配置（含代理）
└── sql/
    └── linshu_db.sql                 # 数据库建表脚本
```

## 快速开始

### 环境要求

- JDK 17+
- Node.js 18+
- MySQL 8.0+
- Maven 3.8+

### 1. 初始化数据库

创建数据库并导入建表脚本：

```sql
CREATE DATABASE linshu_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

```bash
mysql -u root -p linshu_db < sql/linshu_db.sql
```

### 2. 配置后端

编辑 `backend/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/linshu_db?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true
    username: root
    password: 你的数据库密码

  mail:
    host: smtp.qq.com
    port: 465
    username: 你的QQ邮箱
    password: 你的SMTP授权码

qianfan:
  v2:
    base-url: https://qianfan.baidubce.com/v2
    token: 你的千帆API Token
    audit-model-name: glm-5
    chat-model-name: glm-5
```

### 3. 启动后端

```bash
cd backend
./mvnw spring-boot:run
```

后端默认运行在 `http://localhost:8080`。

### 4. 启动前端

```bash
cd front
npm install
npm run dev
```

前端开发服务器默认运行在 `http://localhost:5173`，已配置 Vite 代理将 API 请求转发至后端。

### 5. 访问系统

浏览器打开 `http://localhost:5173`，注册账号后即可使用。

## API 接口概览

| 模块 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 用户 | POST | `/user/register` | 用户注册 |
| 用户 | POST | `/user/login` | 用户登录 |
| 监控 | GET | `/api/system/dashboard` | 获取仪表盘数据 |
| 诊断 | GET | `/diagnosis/logPath` | 自动发现日志路径 |
| 诊断 | POST | `/diagnosis/execute` | 执行日志诊断 |
| 诊断 | POST | `/diagnosis/config/add` | 添加监控配置 |
| 诊断 | GET | `/diagnosis/config/list` | 获取监控配置列表 |
| 诊断 | POST | `/diagnosis/config/delete` | 删除监控配置 |
| 诊断 | POST | `/diagnosis/server-monitor/add` | 添加服务器监控 |
| 诊断 | POST | `/diagnosis/server-monitor/stop` | 暂停服务器监控 |
| 诊断 | POST | `/diagnosis/server-monitor/resume` | 恢复服务器监控 |
| AI | POST | `/diagnosis/ai/chat` | AI 智能问答 |
| AI | POST | `/diagnosis/ai/analyze` | AI 日志分析（测试） |
| 告警 | GET | `/api/alert/contact` | 获取通知邮箱 |
| 告警 | POST | `/api/alert/contact` | 保存通知邮箱 |
| 告警 | POST | `/api/alert/test` | 发送测试邮件 |
| 巡检 | POST | `/info/selectAllInfo` | 查询所有巡检记录 |
| 巡检 | POST | `/info/selectInfo` | 按条件查询巡检记录 |
| 巡检 | POST | `/info/insertProcess` | 保存处理记录 |
| 巡检 | POST | `/info/deleteInfo` | 删除单条告警 |
| WebSocket | - | `/ws` | AI 助手实时通信 |

## Agent 工作流程

```
用户输入 → 意图识别（咨询/执行）
                │
        ┌───────┴───────┐
        │               │
      咨询             执行
        │               │
   AI 直接回答     生成执行计划（Planner）
                        │
                  逐步执行 Steps
                        │
              ┌─────────┼─────────┐
              │         │         │
         命令执行    日志读取   监控查询
              │         │         │
              └─────────┼─────────┘
                        │
                  高风险命令？
                   │       │
                  是       否
                   │       │
              用户确认   直接执行
                   │
              确认/取消
                   │
              执行/跳过
                        │
                  汇总结果 + 图表建议
                        │
                  返回给用户
```

## 安全机制

- **命令安全校验**：后端 `CommandSafetyService` 通过正则匹配拦截 `rm -rf /`、`mkfs`、`dd of=/dev/`、`shutdown`、`reboot`、`chmod -R 777 /`、`> /etc/` 等高风险命令
- **前端安全拦截**：OpsAssistantView 前端同样校验用户输入，双重保障
- **二次确认**：高风险命令必须经用户在界面确认后才可执行
- **SSH 密码处理**：sudo 命令自动注入密码，密码不落日志
- **会话管理**：WebSocket 连接超时自动清理，最大连接数限制

## 数据库表结构

| 表名 | 说明 |
|------|------|
| `userlogin` | 用户表（用户名、密码、告警邮箱） |
| `componentconfig` | 组件配置表（服务器连接信息、日志路径、监控开关） |
| `information` | 巡检记录表（诊断结果、风险等级、AI 分析） |
| `userprocess` | 用户处理记录表（历史运维操作） |

## 配置项说明

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `monitor.schedule.fixed-rate` | 30000 | 监控采集间隔（毫秒） |
| `alert.schedule.fixed-rate` | 120000 | 告警检查间隔（毫秒） |
| `alert.recheck.lookback-minutes` | 20 | 告警回查窗口（分钟） |
| `alert.mail.cooldown-minutes` | 30 | 邮件冷却期（分钟） |
| `qianfan.v2.read-timeout-seconds` | 120 | AI 接口超时（秒） |
| `qianfan.v2.agent-read-timeout-seconds` | 30 | Agent 工具调用超时（秒） |

## License

本项目仅供学习与参考使用。
