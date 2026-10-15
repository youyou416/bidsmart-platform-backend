# 配置与运行边界

配置变量只注入后端进程，不提交实际 `.env`、`settings.xml`、私钥、密码、Cookie、数据库备份或私有日志。`.env.example` 是公开样例；应用不会自动读取它。优先使用受控进程环境或仓库外配置文件。应用读取不到必要秘密时应失败，不用生产数据或另一数据库补足。

| 配置 | 本期默认与约束 |
| --- | --- |
| `APP_ENV` | `dev`；仅接受 dev/test/prod。不要与显式 Spring profile 矛盾 |
| `SERVER_ADDRESS` / `SERVER_PORT` | `127.0.0.1` / `8080`；dev/test 仅回环监听 |
| `DB_HOST` / `DB_PORT` | `127.0.0.1` / `2881`；host 是后端机器的地址 |
| `DB_NAME` / `DB_USERNAME` | dev为 `hr_score_dev` / `root@sys`，仅隔离本机开发；test须显式独立库和业务租户非root应用账号 |
| `DB_PASSWORD` | 必须本机注入，无默认秘密；不进入前端 |
| `DB_SSL_MODE` | dev 默认 `DISABLED`，仅隔离回环dev可采用本机明文连接；test/prod固定 `VERIFY_IDENTITY`。远程配置必须验证可信链与主机身份 |
| `DB_POOL_MAX` | 10；Hikari 连接超时 5 秒、验证超时 3 秒，JDBC 查询/socket 超时 10 秒 |
| `SESSION_IDLE_TIMEOUT` / `SESSION_ABSOLUTE_TIMEOUT` | `30m` / `8h` |
| `SESSION_COOKIE_SECURE` | 本机 HTTP dev 默认 false；prod 固定 true，非本机 HTTPS 必须启用 |
| `CORS_ALLOWED_ORIGINS` | `http://127.0.0.1:5173`；明确来源列表，凭据请求不使用通配；prod 必须 HTTPS |
| `LOGIN_MAX_FAILURES` / `LOGIN_FAILURE_WINDOW` / `LOGIN_LOCKOUT` | 5 / `15m` / `15m`；来源采用真实连接地址，不信任任意 X-Forwarded-For |
| `AUDIT_RETENTION_DAYS` | 30；每天03:00 UTC仅清理本框架的认证/操作审计表 |
| `BOOTSTRAP_ADMIN_ENABLED` | false；只有显式开启才创建不存在的账户；prod 禁止 |
| `BOOTSTRAP_ADMIN_USERNAME` / `BOOTSTRAP_ADMIN_PASSWORD` | 无默认；首次初始化安全注入后移除 |
| `BOOTSTRAP_ADMIN_ROLE_ID` / `BOOTSTRAP_ADMIN_DEPARTMENT_ID` | 1001 / 1001；初始化前验证配置存在，不自动扩大授权 |
| `PUBLIC_SYSTEM_NAME` | 公开系统名称；不包含服务器秘密 |
| `TRANSPORT_VERIFIED` | false；prod 要求 true，但设为 true 不是传输已经验证的证据 |

其他公开界面配置由后端 `platform.system-name`、`logo-path`、`theme-color`、`tabs-enabled` 定义，经专用白名单 DTO 返回。不得把 `Environment`、配置对象全集或数据库参数暴露给前端。HTTP JSON 请求体上限 16 KiB，文件上传关闭。数据源有效URL/用户名不能借Spring覆盖配置绕过已验证DB变量；远程数据库必须身份验证，远程root@sys被拒绝。

test必须显式提供独立 `DB_HOST`、`DB_NAME`、`DB_USERNAME` 和密码，固定数据库 `VERIFY_IDENTITY`；拒绝 `hr_score_dev`、系统租户或任何root账号，不继承dev数据库默认值。没有测试数据库时只运行Maven合成Mock仓库的离线测试，不以实际 `APP_ENV=test` 启动服务假冒独立接入。当前 DB 版本脚本限定 `hr_score_dev` 并拒绝test，独立测试库需管理员对复制/适配方案单独审查，脚本不会自动改写目标。prod不继承dev宽松连接、调试或初始化开关：使用业务租户最小权限账号，禁止全部root/sys形式，数据库验证身份，Secure Cookie，明确HTTPS来源和已验证传输。

SM4/SM2 目前以显式注入的受控工具提供，没有开放加解密接口或默认业务字段加密。只有调用这些能力时才需要现场生成、受控保存的对应密钥；账户密码 PBKDF2-HMAC-SM3 不依赖全局可逆密钥。不要创建 `VITE_SM4_KEY`、`VITE_DB_PASSWORD` 等变量。前端 `VITE_` 仅允许公开名称、相对 API 地址和界面开关。

生产发布需配置同域反向代理、可信证书、静态页面 CSP、禁止嵌入及严格内容类型/引用来源策略。附带的配置样例应依据实际域名和部署方式审查后使用，当前工程没有运行公网部署或国密 TLS 验证。
