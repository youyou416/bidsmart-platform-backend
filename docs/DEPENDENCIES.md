# 工具链、依赖与风险记录

## 固定要求及本期选择

后端固定 JDK11、Maven3.6.1、Spring Boot2.5.3、maven-compiler-plugin3.8.1；Tomcat9/Servlet4.0 使用 `javax.servlet`、`javax.validation`，不使用 record/text block/Jakarta Boot3 API。前端 Node **20.19.5** 是用户后续明确指定版本，替代原附件默认 Node22。工程为各自独立目录，npm 使用 package-lock.json 冻结安装。

安装器选择 Temurin11.0.32.1+1、Maven3.6.1、Node20.19.5，校验 HTTPS 官方摘要并安全解包；目录分别为 jdk-11/maven-3.6.1/node-20。不把内网 HTTP 私服、旧项目包名或凭据复制到项目；内网私服须用仓库外受控 settings.xml。没有 Maven Wrapper，实际 Maven3.6.1 由 Enforcer 约束。

| 后端依赖/插件 | 版本来源 |
| --- | --- |
| Spring Boot parent / 可执行 JAR 插件 | 2.5.3 固定 |
| Spring Framework / Spring Security | Boot父清单管理（5.3系列 / 5.5.1），有效依赖树为准 |
| Web/Security/Validation/JDBC/Test、Jackson、Hikari、Tomcat | Boot父清单管理，未引入第二套JSON/ORM |
| `com.mysql:mysql-connector-j` | 8.0.33，Java11兼容；真实 OceanBase 兼容仍待接入 |
| `org.bouncycastle:bcprov-jdk18on` | 1.78.1，包含SM3、显式摘要PBKDF2、SM4-GCM、SM2；Java11兼容 |
| maven-compiler-plugin | 3.8.1，release11 |
| maven-enforcer-plugin | 3.4.1，约束实际JDK/Maven版本 |

| 前端直接依赖 | 精确版本 |
| --- | --- |
| Vue / Vue Router / Pinia | 3.5.44 / 4.6.4 / 3.0.4 |
| Element Plus / icons-vue | 2.13.7 / 2.3.2 |
| Vite / plugin-vue | 7.3.7 / 6.0.10 |
| TypeScript / vue-tsc | 5.9.3 / 3.3.13 |
| Vitest | 4.1.11 |
| ESLint / eslint-plugin-vue / typescript-eslint | 10.12.0 / 10.11.1 / 8.71.2 |
| Prettier / @types/node | 3.9.10 / 22.20.5（仅开发声明，无Node22运行要求） |

最终锁定版本以实际 `pom.xml`、有效依赖树与独立前端 `package-lock.json` 为准。Vite要求 Node20.19+ 或22.12+，此次在用户指定20.19.5（npm10.8.2）执行验证，不擅自升级Node。Element Plus选择2.13.7以避免2.14分支的传递依赖要求Node22；Vitest采用4.1.11消除旧3系开发依赖公告。前端engine-strict安装检查确认当前传递依赖兼容Node20.19.5。根依赖扫描/字节码检查记录见 [验证记录](VALIDATION.md)。

## 安全扫描状态与历史基线风险

当前后端尝试通过 OSV API 进行依赖漏洞查询，`api.osv.dev` 遭网络代理 HTTP CONNECT403 阻断，因此**后端漏洞自动扫描未完成**，不能标记“无漏洞”。2026-10-14前端实际执行生产依赖与全依赖 npm audit，均返回0条公告；这是查询当时npm公告库与当前锁文件的结果，不证明所有未知风险均不存在。安装和编译通过不能替代漏洞扫描。

已核实的历史风险公告示例：

| 依赖基线 | 公告 | 限制说明 |
| --- | --- | --- |
| Spring Framework5.3.9 | [CVE-2022-22965 / GHSA-36p3-wjmg-h94x](https://github.com/advisories/GHSA-36p3-wjmg-h94x) | 位于受影响版本范围；5.3.18修复。当前可执行JAR及参数受控JSON接口未必满足全部利用条件 |
| SnakeYAML1.28 | [CVE-2022-1471 / GHSA-mjmj-j48q-9wg2](https://github.com/advisories/GHSA-mjmj-j48q-9wg2) | 位于受影响版本范围；不向不可信客户端提供任意YAML解析入口不能替代依赖修补 |
| Tomcat9.0.50 | [CVE-2025-24813 / GHSA-83qj-6fr2-vhqg](https://github.com/advisories/GHSA-83qj-6fr2-vhqg) | 位于受影响版本范围；公告利用条件包含特定写入/会话等配置，当前最小接口不代表完整风险已排除 |

这些是已核实受影响范围示例，不是完整扫描，也不是每项在当前应用都可利用的结论。Spring Boot2.5系列已是历史版本，Tomcat、Jackson、Security及其他传递依赖均需在最新漏洞源下完整复核。

本期尊重指定核心版本，不以未经验证的依赖替换冒充完成修复。部署隔离方案为仅回环监听、无公网入口、最小接口、明确来源、代理限制及无真实业务数据；正式部署应审查可以兼容固定基线的补丁覆盖，运行完整回归测试。需要改变Spring Boot/JDK/Maven核心版本时，单独定版并获得用户授权。风险缓解不等于漏洞消失或可以公网发布。

官方核验参考：[Spring Boot2.5.3系统要求](https://docs.spring.io/spring-boot/docs/2.5.3/reference/html/getting-started.html#getting-started.system-requirements)、[依赖清单](https://docs.spring.io/spring-boot/docs/2.5.3/reference/html/dependency-versions.html)、[Spring Security5.5.1](https://docs.spring.io/spring-security/site/docs/5.5.1/reference/html5/)、[Connector/J要求](https://dev.mysql.com/doc/connector-j/en/connector-j-versions.html)、[Bouncy Castle资料](https://www.bouncycastle.org/documentation/documentation-java/)、[Vite运行要求](https://vite.dev/guide/)、[Spring4Shell公告](https://spring.io/blog/2022/03/31/spring-framework-rce-early-announcement)。联网受限项目单独标注，不关闭证书/摘要验证或任意增加公共脚本CDN。
