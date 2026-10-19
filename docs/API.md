# 基础接口契约

默认 API 为本机后端 `http://127.0.0.1:8080/api/v1`，浏览器通过独立前端 Vite `/api` 代理访问。同一浏览器 Cookie 会话配合 CSRF 请求头，前端 HTTP 封装启用凭据；没有可供脚本读取的登录令牌。

所有已定义接口使用统一 envelope，HTTP 状态码仍表达实际成功/失败：

```json
{"code":"OK","message":"成功","data":null,"traceId":"服务端生成的关联标识"}
```

请求追踪在响应 `X-Trace-Id` 与 envelope 一致。Long ID 为 JSON 字符串；时间为带时区 ISO 8601（服务端 UTC），前端按 Asia/Shanghai 展示。不要把 ID 转为 JS number。分页扩展类型默认 page=1、size=20、上限100；排序字段在服务端白名单内，目前没有业务分页 API。

| 方法/路径 | 认证与输入 | `data` / 行为 |
| --- | --- | --- |
| GET `/security/csrf` | 可匿名；限定来源与频率 | `{token,headerName}`，headerName 为 `X-CSRF-TOKEN`；绑定浏览器会话，禁止缓存 |
| POST `/security/captcha` | 可匿名 + CSRF；默认每来源 IP 20 次/分钟 | `{captchaId,imageDataUrl,expiresAt,expiresInSeconds}`，PNG、四位数字、默认 120 秒、会话绑定，刷新使旧图失效 |
| POST `/auth/login` | JSON `{username,password,captchaId,captchaCode}` + CSRF | 当前用户 DTO；轮换会话、保存 SecurityContext，不返回 Cookie 值/密码摘要 |
| POST `/auth/logout` | 当前会话 + CSRF | null；立即销毁当前服务端会话与 Cookie |
| GET `/auth/me` | 当前会话；不接受查询其他用户的 ID | 当前用户 DTO；首次改密期间角色/权限为空、scope 为 null |
| GET `/auth/menus` | 当前会话且已完成首次改密 | 当前用户授权的有效菜单树；不返回其他人员或全部授权配置 |
| POST `/auth/password` | 当前会话 + JSON `{currentPassword,newPassword}` + CSRF | null；只改当前用户，更新密码版本并使旧会话失效，重新登录 |
| GET `/app/public-config` | 可匿名 | `{systemName,logoPath,themeColor,tabsEnabled}`，仅公开白名单 |
| GET `/health` | 可匿名 | `{status:"UP"}`，最小存活状态；不代表数据库/外部服务已核验 |
| GET `/framework/info` | 当前会话、完成首次改密、`framework:home:view` | `{description:"前后端分离基础框架"}`，用于实际基础接口权限闭环 |

密码不裁剪、不自动去首尾空格，新密码必须 12～128 Unicode 字符。用户名最大128个字符。captchaId 最长64，captchaCode 为四位 ASCII 数字（DTO 上限16）；验证码先于账号查询/密码派生校验，每次校验尝试消耗当前会话的最新挑战，失败后重新获取。答案不出现在接口中，详见 [验证码说明](CAPTCHA.md)。请求体上限16KiB，不允许业务文件上传。GET 不执行业务状态变更。

## 当前用户与菜单字段

当前用户 DTO 为 `{id,username,displayName,department,identityType,enterpriseId,roles,permissions,scope,firstPasswordChangeRequired}`。department 为 `{id,name}` 或 null；identityType 为 BUYER/SUPPLIER。role 的合法自有授权与继承计算保持可区分；scope 为当前人员范围上下文，仅返回自身所需非敏感配置，不由请求参数指定主体。

菜单节点字段 `{id,parentId,name,type,routeName,path,component,icon,visible,enabled,permission,children}`，type 为 DIRECTORY/MENU，按钮权限在 permissions 中用于展示且后端仍需鉴权。合成首页为 `FrameworkHome`、`/home`、组件 `Home`、权限 `framework:home:view`。组件必须命中前端登记白名单，不能解释为任意 JS/远程脚本路径。

首次创建账户时 `firstPasswordChangeRequired=true`，login/me 可以返回最小身份，但 roles/permissions 为空、scope=null；普通菜单及其他受保护能力返回403 PASSWORD_CHANGE_REQUIRED。完成改密后重新登录才能进入框架首页。

## 错误契约

| HTTP | code 示例 | 客户端处理 |
| --- | --- | --- |
| 400 | INVALID_INPUT、PASSWORD_POLICY、CAPTCHA_INVALID | 展示安全的输入/密码规则错误 |
| 401 | UNAUTHENTICATED、LOGIN_FAILED | 清理认证/动态路由/页签状态；登录失败使用统一提示 |
| 403 | FORBIDDEN、CSRF_INVALID、FORBIDDEN_ORIGIN、PASSWORD_CHANGE_REQUIRED | 区分无权限、CSRF不符及来源拒绝；首次改密转专用页面 |
| 404 | 前端未知页面状态 | 不自动生成不存在的页面；已定义API以本表契约为准 |
| 405 | METHOD_NOT_ALLOWED | 方法不支持 |
| 429 | RATE_LIMITED | 提示稍后重试，不重复提交登录 |
| 500 | DATABASE_UNAVAILABLE、INTERNAL_ERROR | 展示脱敏消息及 traceId，不泄露 SQL/秘密/内部栈 |

浏览器写请求先握手，使用响应 headerName 放入 token；登录成功、退出或会话失效后必须重新获取。失败请求不自动重放敏感写操作。前端同时处理超时、取消、并发重复提交、401/403/429/5xx。登录回跳只接受已注册站内白名单路径，不接受绝对网址、协议相对地址或任意外部重定向。

本期不提供用户/角色/部门/资源 CRUD、采购/报价/审批 API、公开文档调试入口或对外通用加解密接口。除精确白名单的9个基础端点外，新增已实现的API处理器必须标注所需权限，缺失标注默认403；`/framework/info` 已明确要求 `framework:home:view`。新增能力必须以独立编码任务明确接口、权限码、数据范围和审计；不能只增加菜单配置绕过服务端默认拒绝。
