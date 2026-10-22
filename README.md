# 基于 Spring Boot 的光储智慧调度监控平台

一个面向中小型光储场站的轻量级 Web 监控调度平台，实现设备管理、实时监控、调度策略、告警管理、能量流可视化和统计报表等功能。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 17 + Spring Boot 2.7 + MyBatis-Plus + WebSocket + Spring Task + JWT |
| 数据库 | MySQL 8.0（开发环境）/ H2 文件库（一键运行版内置） |
| 前端 | Vue 3 + Element Plus + ECharts + Axios + Pinia + Vite |
| 数据模拟 | 自编数据生成器（@Scheduled 定时生成，论文预留 Modbus TCP 扩展） |

## 功能模块

1. **用户权限（RBAC）**：管理员 / 调度员 / 普通用户 / 只读用户
2. **用户管理**：管理员可增删改查用户、分配角色、启用停用、重置密码
3. **设备管理**：设备台账（光伏/PCS/电表/负载/电网/充电桩）、通讯参数、状态
4. **数据模拟采集**：模拟光伏、储能、负载、电网实时数据
5. **实时监控**：大屏卡片 + 实时功率曲线 + 历史数据查询
6. **调度策略**：计划曲线、削峰填谷、自发自用、需量控制 + 优先级
7. **光储充一体化**：充电桩调度（谷时充电/峰时避峰/光伏余电充电）+ 充电订单
8. **告警管理**：阈值规则、越限检测、WebSocket 推送、处理
9. **能量流可视化**：桑基图展示光伏/储能/负载/电网能量流向
10. **统计报表**：发电/用电/充放电/收益，日/月报表（峰平谷电价）

## 目录结构

```
毕业设计/
├── backend/          # Spring Boot 后端工程（MySQL / H2 双数据库）
│   ├── pom.xml
│   ├── sql/init.sql  # MySQL 开发环境初始化脚本
│   └── src/main/
│       ├── java/com/energy/
│       │   ├── config/       # 配置（JWT/CORS/MyBatis-Plus/WebSocket/发布初始化）
│       │   ├── controller/   # 控制器（含健康检查、SPA 路由）
│       │   ├── service/      # 业务逻辑（含数据模拟/告警/报表）
│       │   ├── entity/       # 实体
│       │   ├── mapper/       # MyBatis-Plus Mapper
│       │   ├── context/      # 用户上下文/权限工具
│       │   └── util/         # JWT/MD5 工具
│       └── resources/
│           ├── application.yml           # 开发环境（MySQL）
│           ├── application-release.yml   # 一键运行版（H2 + 单端口）
│           └── db/h2-*.sql               # H2 建表与种子数据
├── frontend/         # Vue 3 前端工程
│   ├── vite.config.js
│   └── src/
│       ├── layout/       # 布局（侧边栏/顶栏/WebSocket）
│       ├── views/        # 页面
│       ├── router/       # 路由
│       ├── store/        # Pinia
│       └── utils/        # Axios 封装
├── python-ems/       # FastAPI 预测/DRO 服务
│   ├── server_entry.py   # PyInstaller 冻结入口
│   └── python-ems.spec   # 打包配置
├── launcher/         # C# WinForms 启动器源码
├── scripts/          # 一键构建脚本
└── docs/             # 部署 / 构建 / 使用文档
```

## 一键运行版（答辩演示推荐）

不安装 Java / MySQL / Node / Python，解压后双击 `EnergyMonitor.exe` 即可：
内置精简 JRE、H2 文件数据库、已打包的 Vue 前端，以及 PyInstaller 冻结的 Python 预测服务。

1. 打开 [Releases](https://github.com/IIIIIzzhIIIII/smart-energy-storage-management-system/releases/latest)；
2. 下载 `EnergyMonitor-v1.1.0-win64.zip`（完整版）并解压到**不含中文和空格**的目录；
3. 双击 `EnergyMonitor.exe`，等待浏览器自动打开 `http://127.0.0.1:8082`；
4. 使用 `admin / 123456` 登录。

> 详细说明见 [一键运行版使用说明](docs/一键运行版使用说明.md)，
> 自行构建见 [一键运行版构建](docs/一键运行版构建.md)。

## 内置账号

| 账号 | 密码 | 角色 |
|------|------|------|
| admin | 123456 | 管理员（管理所有用户和设备） |
| dispatcher | 123456 | 调度员 |
| user | 123456 | 普通用户（仅自己名下设备） |
| viewer | 123456 | 只读用户 |

> 演示设备归属 user 用户（id=3），建议用 admin 或 user 登录查看。

## 快速启动

详细步骤见 [部署文档](docs/部署文档.md)。

**后端**（IDEA 运行）：
1. 新建数据库并执行 `backend/sql/init.sql`
2. 复制 `backend/src/main/resources/application-local.yml.example` 为
   `application-local.yml`，填入 MySQL 密码、JWT 密钥、和风天气 API Key
   （该文件已被 `.gitignore` 忽略，不会提交到仓库）
3. IDEA 打开 `backend` 目录，运行 `EnergyMonitorApplication`（默认端口 8082）

**Python 预测服务**（可选，端口 8000）：
```bash
cd python-ems
pip install -r requirements.txt
# 复制 .env.example 为 .env 并填入和风天气 API Key
python main.py
```

> 敏感配置说明：所有密钥（数据库密码 / JWT Secret / 天气 API Key）均不写入源码，
> 后端通过 `application-local.yml` 或环境变量（`DB_PASSWORD` / `JWT_SECRET` /
> `WEATHER_API_KEY` / `WEATHER_API_HOST`）注入；Python 通过 `python-ems/.env` 注入。

**前端**（VS Code 终端运行）：
```bash
cd frontend
npm install
npm run dev
```

浏览器访问 http://localhost:5173

## 端口

- 后端：8082（一键运行版只需这一个端口，前端静态页面由后端直接提供）
- 前端：5173（仅开发模式）
- Python 预测服务：8000（仅本机 127.0.0.1 监听）

## 数据权限说明

- 管理员：查看和管理全站所有用户、设备、策略、告警、报表
- 普通用户/调度员：只能管理自己名下的设备及关联数据
- 只读用户：只能查看，不能增删改