# 智能化竞价采购平台后端基础工程

本工程只交付文档确认的基础框架：认证会话、首次改密、授权菜单、权限与范围计算、国密工具、审计和配置。采购、报价、竞价、审批、供应商管理等具体业务留待后续实现。

后端为独立工程 `bidsmart-platform-backend`，前端为另一独立工程 `bidsmart-platform-frontend`。两者不放在同一工程根目录下，分别构建与运行；数据库资料和后端接口文档放在本工程的 `db/`、`docs/`。当前云端目录分别为 `/workspace/bidsmart-platform-backend` 和 `/workspace/bidsmart-platform-frontend`。

## 版本与前提

- 后端固定 JDK 11、Maven 3.6.1、Spring Boot 2.5.3，编译插件 3.8.1，字节码目标 55。
- 前端使用用户后续指定的 **Node.js 20.19.5** 和 npm；这一明确指令替代附件中 Node 22 的默认基线。
- 自动安装工具链需要 Python 3.12+、HTTPS 网络和 `tar`；安装器校验官方摘要并采用安全解包。
- 应用运行需要独立 OceanBase MySQL 兼容数据库、正确密码与已初始化的框架表。MySQL 8 客户端用于显式数据库检查/初始化。
- 默认后端 `127.0.0.1:8080`、前端 `127.0.0.1:5173`，只使用合成数据。

## 安装、测试与打包

```bash
cd /workspace/bidsmart-platform-backend
python3 scripts/bootstrap-tools.py /workspace/.toolchains
export JAVA_HOME=/workspace/.toolchains/jdk-11
export PATH="$JAVA_HOME/bin:/workspace/.toolchains/maven-3.6.1/bin:/workspace/.toolchains/node-20/bin:$PATH"
java -version
mvn -version
node --version
BIDSMART_TOOLCHAIN_DIR=/workspace/.toolchains bash scripts/verify.sh
```

示例中的 `/workspace` 是当前云端路径；下载后请替换为解压后的实际独立工程目录。本机可省略安装器目录参数，默认安装到 `~/.bidsmart-toolchains`，相应调整 `JAVA_HOME` 与 `PATH`。自动安装器面向 Linux x64；其他操作系统请自行安装相同固定版本的 JDK/Maven/Node。已有正确版本工具链时可直接使用。安装器不会修改系统 Java，也不会关闭 HTTPS、证书或摘要验证。前端在它自己的目录执行 `npm ci`、检查、测试和构建，详见前端 README。

`scripts/verify.sh` 会完成实际版本检查、编译/测试/打包、有效父/插件版本及应用/运行依赖字节码核验；可通过仓库外的 `BIDSMART_MAVEN_SETTINGS` 配置企业仓库，通过 `BIDSMART_MAVEN_REPO` 配置本地缓存。单元及框架 HTTP 集成测试使用合成配置/测试替身，不需要真实数据库。它们不能证明 OceanBase SQL 兼容性。必须接入真实 OceanBase 后完成 [数据库核验清单](db/README.md)。本次执行结果由 [验证记录](docs/VALIDATION.md) 单独列出。

## 数据库准备与首次运行

数据库地址采用用户给定的 `127.0.0.1:2881`，用户名 `root@sys`，目标库 `hr_score_dev`。该账号属于系统租户，仅限隔离本机研发；本工程不创建数据库、租户或修改 root 密码。先由数据库管理员确认目标库存在、属于预期租户、MySQL 模式、字符集及最小所需权限。详细步骤见 [数据库说明](db/README.md)。

从 `.env.example` 选择公开配置并注入环境；**Spring Boot 不自动加载 `.env`**。秘密必须来自仓库之外的受控配置或当前 shell。不要把密码写进命令参数、源码、聊天或前端配置。以下命令读取密码而不回显：

```bash
read -r -s -p '数据库密码: ' DB_PASSWORD
export DB_PASSWORD
printf '\n'
export APP_ENV=dev DB_HOST=127.0.0.1 DB_PORT=2881 DB_NAME=hr_score_dev DB_USERNAME=root@sys
# 仅隔离本机数据库且明确使用明文开发连接时设置；远程数据库必须验证 TLS。
export DB_SSL_MODE=DISABLED
python3 db/tools/manage.py check
# 在独立核实租户、模式、库归属及权限后显式初始化：
python3 db/tools/manage.py init --confirm-target hr_score_dev --confirmed-mysql-tenant sys
```

首次账户与 SQL 初始化分开。没有内置账户、密码或管理员通配权限。显式选择初始用户名、输入 12～128 个 Unicode 字符的长口令后，仅首次开启：

```bash
read -r -p '初始账户名: ' BOOTSTRAP_ADMIN_USERNAME
read -r -s -p '一次性初始密码: ' BOOTSTRAP_ADMIN_PASSWORD
export BOOTSTRAP_ADMIN_USERNAME BOOTSTRAP_ADMIN_PASSWORD
printf '\n'
export BOOTSTRAP_ADMIN_ENABLED=true
java -jar target/platform-base-1.0.0.jar
```

服务校验部门和角色存在后，只创建不存在的账户，不覆盖已有密码、状态或授权。默认关联合成部门/角色 `1001`，数据范围为 `SELF_AND_SPECIFIED`；“平台管理员”名称不提供额外绕过。首次登录只能修改自身密码或退出，改密后旧会话失效，需要重新登录。

完成初始账户创建后按 `Ctrl+C` 停止，移除一次性秘密与开关，再正常启动：

```bash
unset BOOTSTRAP_ADMIN_USERNAME BOOTSTRAP_ADMIN_PASSWORD
export BOOTSTRAP_ADMIN_ENABLED=false
java -jar target/platform-base-1.0.0.jar
```

另开终端在独立前端目录按它的 README 启动，浏览器访问 `http://127.0.0.1:5173`。Vite 将 `/api` 代理到本机后端。前端不访问数据库。两端默认前台运行，分别 `Ctrl+C` 停止；后端重启后全部内存会话失效。

## 配置与扩展

见 [接口契约](docs/API.md)、[安全约束](docs/SECURITY.md)、[配置说明](docs/CONFIGURATION.md)、[权限扩展](docs/EXTENSIONS.md)、[依赖基线](docs/DEPENDENCIES.md) 及 [验证记录](docs/VALIDATION.md)。不发布公网、不声称国密 TLS 或多实例共享会话已实现。历史固定版本的漏洞风险须在受控网络中评估，核心版本升级另行定版。
