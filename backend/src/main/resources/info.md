# 运维日志分析知识库

## 风险分级补充规则
- SSH/sshd 日志中如果仅出现 Failed password、Invalid user、authentication failure、Connection closed、断连、重试等暴力破解尝试迹象，但没有成功登录、提权成功、后门落地、关键文件被篡改等“已攻破”证据时，最高判定为“中”风险。
- 只有明确出现登录成功（如 Accepted password、Accepted publickey、session opened）、提权成功、持久化、后门落地、敏感配置或数据被改动等“已攻破”证据时，才判定为“高”风险。

## 编写建议
- 后续新增规则可以继续直接写在这个文件里，优先写清楚“场景、判断依据、风险等级、建议动作”。
- 这个文件会在每次日志分析时重新读取，修改后无需改 Java 代码。
