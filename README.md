# 掌上律师 MVP 1.0.0

**掌上律师** — 劳动纠纷法律助手微信小程序。帮助用户梳理案件事实、收集证据、理解法律权利、规划维权流程。

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 4.1.1 |
| 语言 | Java | 21 |
| LLM 框架 | Spring AI | 2.0.1 |
| ORM | MyBatis-Plus | 3.5.17 |
| 数据库（开发） | H2 内存数据库 | 2.4.240 |
| 数据库（生产） | PostgreSQL | 16+ |
| 向量扩展（生产） | pgvector | 0.7+ |
| 缓存 | Caffeine | 3.1.8 |
| 前端 | 微信小程序 | 基础库 3.x |
| 语音输入 | 微信同声传译插件（WechatSI） | 0.3.x |
| 外部 API | 法律之星（法规检索）、OpenAI 兼容 LLM（Agnes） | |

## 项目结构

```
zsls/
├── backend/                    # Spring Boot 后端
│   ├── pom.xml
│   ├── docker-compose.yml      # 生产 PostgreSQL 部署
│   ├── db/init.sql             # 生产数据库初始化脚本（含 pgvector）
│   └── src/main/
│       ├── java/com/palmlawyer/
│       │   ├── PalmLawyerApplication.java
│       │   ├── agent/          # Agent 编排（事实提取/证据建议/免责声明）
│       │   ├── config/         # LLM / Web / H2 Console 配置
│       │   ├── controller/     # REST API（7 个控制器）
│       │   ├── dto/            # 数据传输对象
│       │   ├── entity/         # MyBatis-Plus 实体（6 个）
│       │   ├── mapper/         # MyBatis-Plus Mapper（6 个）
│       │   ├── service/        # 业务服务
│       │   ├── tool/           # Agent 工具
│       │   └── util/           # 工具类
│       └── resources/
│           ├── application.yml # 主配置
│           ├── schema.sql      # H2 建表
│           ├── data.sql        # H2 测试数据
│           └── plugins/        # 领域插件 YAML
│               └── labor-dispute.yaml
├── miniprogram/                # 微信小程序前端
│   ├── app.js / app.json / app.wxss
│   ├── project.config.json
│   ├── pages/                  # 8 个页面
│   └── utils/                  # auth / request / storage / casenav
└── 开发文档.md                  # 完整开发文档
```

## 环境要求

| 工具 | 版本 | 说明 |
|------|------|------|
| JDK | 21 | 必需（项目使用 Java 21 语法） |
| Maven | 3.9+ | IDEA 内置或独立安装均可 |
| IDEA | 2023.3+ | 或任意支持 Spring Boot 的 IDE |
| 微信开发者工具 | 最新稳定版 | 运行小程序 |
| Node.js | 无需 | 小程序为原生开发，无 npm 依赖 |

> 项目无任何项目目录以外的文件依赖，复制整个目录到其他电脑即可继续开发。

## 快速开始

### 1. 启动后端

**方式一：IDEA**
打开 `backend/` 目录，等待 Maven 导入依赖，运行 `PalmLawyerApplication` 主类。

**方式二：命令行**
```bash
cd backend
# 注意：本机 JAVA_HOME 需指向 JDK 21（如 D:\JDK21），否则编译会因版本不符失败
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# 或打包后运行
mvn package -DskipTests
java -jar target/palm-lawyer-backend-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev
```

启动成功输出：
```
Started PalmLawyerApplication in ~3 seconds
API 地址: http://localhost:8080/api/v1
H2 控制台: http://localhost:8080/api/v1/h2-console   # 仅 dev profile 开放
```

> 未指定 profile 时也可直接运行（默认 H2 内存库），但 H2 控制台默认关闭。

### 2. 启动小程序

1. 打开微信开发者工具
2. 导入 `miniprogram/` 目录
3. 使用游客模式（appid 为 touristappId）或填写自己的 AppID
4. 编译运行

> 小程序 `utils/request.js` 中 BASE_URL 指向 `http://localhost:8080/api/v1`，真机调试需改为电脑局域网 IP 并关闭域名校验（project.config.json 中 urlCheck: false）。

## 配置说明

### LLM（application.yml）

```yaml
palmlawyer:
  llm:
    api-key: ${LLM_API_KEY:sk-默认key}
    base-url: '${LLM_BASE_URL:https://apihub.agnes-ai.com/v1}'
    model: ${LLM_MODEL:agnes-2.0-flash}
    temperature: 0.3
    max-tokens: 4096
```

支持 OpenAI 兼容 API 供应商。所有配置均有环境变量覆盖（`LLM_API_KEY`、`LLM_BASE_URL`、`LLM_MODEL`）。

### 微信小程序

```yaml
palmlawyer:
  wechat:
    app-id: ${WECHAT_APP_ID:wx...}
    app-secret: ${WECHAT_APP_SECRET:...}
```

### 数据库

- **开发**：H2 内存数据库（无需安装，重启数据重置），启动参数 `--spring.profiles.active=dev`
- **生产**：使用 `--spring.profiles.active=prod`（PostgreSQL，禁用 SQL 脚本初始化，避免 H2 方言脚本在 PG 上执行失败）。建表脚本见 `backend/db/init.sql`（含 pgvector），或使用 `backend/docker-compose.yml` 一键启动（需显式设置 `POSTGRES_PASSWORD` 环境变量）

### 认证（v1.1 起为真实登录）

- 前端 wx.login 拿 code → POST /auth/login → 后端调微信 jscode2session 换 openid → 签发 JWT（7 天有效）
- 所有业务接口均需 `Authorization: Bearer <token>`；案件/会话/时间轴/证据/导出均有属主校验
- 本地调试：默认 `palmlawyer.auth.dev-mode: true`，微信登录失败时自动降级为 dev_openid 兜底；**生产必须设置 `PALMLAWYER_DEV_MODE=false`**
- 密钥：`PALMLAWYER_JWT_SECRET` 必须 ≥ 32 字节（默认值仅供本地开发，生产用环境变量覆盖）

## 核心 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/auth/login | 微信登录 |
| GET/POST | /api/v1/cases | 案件列表 / 创建案件 |
| GET/PUT | /api/v1/cases/{caseId} | 案件详情 / 更新 |
| POST | /api/v1/cases/{caseId}/timeline | 添加时间轴事件 |
| GET | /api/v1/cases/{caseId}/timeline | 时间轴列表 |
| POST | /api/v1/chat/sessions | 创建会话 |
| POST | /api/v1/chat/sessions/{id}/messages | 发送消息（SSE 流式） |
| GET | /api/v1/law/search?query= | 法规检索 |
| POST | /api/v1/cases/{id}/export/pdf | PDF 导出（openhtmltopdf，含脱敏） |
| POST | /api/v1/cases/{id}/evidence | 证据上传（multipart） |
| PUT | /api/v1/cases/{id}/evidence/{refId} | 更新证据收集状态 |
| DELETE | /api/v1/cases/{id}/evidence/{refId} | 删除证据 |
| GET | /api/v1/files/{caseId}/{filename} | 受控文件下载（属主校验） |
| GET | /api/v1/plugins | 领域插件列表 |
| POST | /api/v1/plugins/reload | 插件热重载 |

## 领域插件

后端采用领域插件机制：`resources/plugins/*.yaml` 定义领域的事实要素、证据规则、法条映射、维权流程、文书模板。当前内置 **劳动纠纷（LABOR）** 插件，新增领域只需添加 YAML 文件。

## 常见问题

**Q: 8080 端口被占用？**
A: `netstat -ano | findstr 8080` 找到 PID 后 `taskkill /PID <pid> /F`。

**Q: 访问 /api/v1/ 返回 404？**
A: 正常。根路径无内容，请访问具体端点（如 /api/v1/cases、/api/v1/plugins）。

**Q: LLM 回答异常？**
A: 检查 application.yml 中 llm 配置（api-key/base-url/model），或设置环境变量覆盖。

**Q: 登录一直返回 dev_openid？**
A: 这是开发模式兜底（palmlawyer.auth.dev-mode=true）。填入真实小程序 AppID/Secret 或设 PALMLAWYER_DEV_MODE=false 后即为真实微信登录。

**Q: 聊天回复不上屏？**
A: 请使用微信开发者工具新版基础库（支持 wx.request 的 onChunkReceived）；流式解析代码在 pages/chat/chat.js。

**Q: 换电脑后如何继续开发？**
A: 复制 backend/、miniprogram/、开发文档.md 三个目录即可。首次构建 Maven 会自动下载依赖，需联网。

## 免责声明

本项目为 MVP 演示版本，法律意见仅供参考，不构成正式法律建议。正式使用需完成隐私合规、数据安全、律师审核等。
