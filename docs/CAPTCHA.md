# 登录图形验证码与 V002 升级

本次新增后端四位数字 PNG 图形验证码，前端提交真实答案；没有开发环境免校验、固定答案或第三方验证码服务。

## 现有数据库升级

后端启动不建表。已有 V001 的环境在启动新版后端前，使用仓库外的 DB_* 环境配置、MySQL 8 客户端和具有建表权限的迁移账号执行：

```sh
python3 db/tools/manage.py check --version V002
python3 db/tools/manage.py init --version V002 --confirm-target hr_score_dev --confirmed-mysql-tenant sys
python3 db/tools/manage.py check --version V002
```

`sys` 仅对应已确认的隔离本机开发系统租户；实际业务租户须填写独立核实的租户名，并匹配 DB_USERNAME。工具保留原有目标库、TLS及权限检查，只接受 hr_score_dev。其他库须由维护者审查目标并使用 `db/migrations/V002__login_captcha.sql`，不会自动连接其他实例。全新库先执行 V001 初始化，再执行 V002。check 缺表返回 2，冲突或连接错误返回 1；兼容表重复 init 不改数据。禁止盲目重复直接执行 CREATE TABLE SQL。

V002 只新增 `sys_login_captcha`，不修改 V001、用户表、密码或已有授权。主键为随机 captcha_id，session_binding_hash 唯一索引保证每个会话一个挑战；expires_at 有清理索引。迁移完成后运行账号需要该表 SELECT/INSERT/DELETE 权限。`db/verification/V002__readonly_checks.sql` 提供只读结构和数量检查。

## 前后端顺序

1. 浏览器 GET `/api/v1/security/csrf`，获得匿名会话和 CSRF 请求头。
2. 同一 Cookie 会话 POST `/api/v1/security/captcha`（带 CSRF）。返回四位数字 PNG 的 data URL、随机 captchaId、UTC expiresAt 和相对 expiresInSeconds，响应禁止缓存。答案仅在图片中。
3. POST `/api/v1/auth/login` 提交 username、password、captchaId、captchaCode（字符串，保留前导零），携带同一 Cookie 与 CSRF。
4. 后端在账号查询/密码派生前验证验证码。验证成功后继续已有登录流程，成功登录仍轮换会话 ID 和 CSRF，首次改密规则继续生效。
5. 验证码错误、过期、缺失会话、跨会话或重复使用返回 400 CAPTCHA_INVALID；缺少必填字段返回 400 INVALID_INPUT；失败次数计入已有登录限流。登录失败后获取新图，不自动重放登录。

刷新会替换本会话旧挑战；任何进入验证码校验的尝试都会消耗本会话最新挑战，包括错误代码/旧ID/正确验证码配错误口令。超过已有登录限流的请求在更早阶段返回429。一个浏览器多个登录页共用会话，刷新任一页会使另一页旧图失效。

前端支持点击图片刷新、加载中禁用、相对有效期倒计时失效提示、失败换图、断网/接口失败后重试、离页取消请求与清理计时器。四位数保存在当前组件内，不写入浏览器存储。采购方/供应商共用服务端验证流程，入口选择不改变身份授权。

## 存储和生命周期

安全随机数生成器分别产生四位数字和 192 位随机挑战ID。每个匿名 HttpSession 持有 256 位服务器秘密，数据库只存 `SM3(secret)` 会话绑定摘要和 `HMAC-SM3(secret, captchaId + ":" + code)` 答案摘要，避免只靠数据库中的四位摘要离线枚举答案。不存明文验证码、Cookie、原始会话ID或HMAC秘密。

校验使用常量时间摘要比较。仓储按挑战ID和会话绑定查询，只有条件DELETE影响一行才返回挑战，防止并发重放；会话内签发/验证同时加锁。过期行每60秒清理，清理失败只记脱敏事件，已过期验证码仍无法通过。数据库异常返回脱敏500并阻止登录，不降级跳过验证码。

当前为单实例内存 HttpSession，与原会话架构一致；重启会丢失会话秘密，旧挑战必须重新获取。多实例共享会话、分布式限流另行实现。图形验证码是辅助登录防护，原有账号/IP失败限流、CSRF、密码派生和会话检查仍必需。

## 配置与核验

默认 `platform.captcha.ttl=2m`，允许30秒至5分钟整秒；默认 `platform.captcha.requests-per-minute=20`，允许1至120。可以通过仓库外配置或 Spring 环境变量 `PLATFORM_CAPTCHA_TTL`、`PLATFORM_CAPTCHA_REQUESTSPERMINUTE` 设置。限流只信任 socket 来源地址，忽略伪造 X-Forwarded-For；内存来源桶最多20000。经过代理部署时应评估共享来源IP的合理配额。

后端自动测试覆盖真实PNG解码、仅摘要持久化、必填和CSRF、错误优先拒绝、刷新、过期、会话绑定、成功/失败后重放、并发单次消费、限流和数据库异常闭锁。HTTP测试使用仓储替身；JDBC测试核验条件删除的影响行语义，不能证明 OceanBase SQL 已执行。浏览器测试使用合成API，不能证明真实数据库联调。当前云环境未连接用户本机 OceanBase，迁移未在真实目标执行；须按数据库说明完成实际建表和登录联调。
