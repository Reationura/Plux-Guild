# Guild 3.1.0 更新日志

> 配置文件版本：1.4 ｜ 插件版本：3.1.0 ｜ 构建产物：Guild-3.1.0.jar

***

## 【修复】

- **语言包 YAML 解析崩溃** ： 三份语言包的 plugin.author 值（"作者: ya_xzer21145"）未加引号且内部含冒号+空格，SnakeYAML 报 "mapping values are not allowed here"，导致 fillMissingMessages 抛 InvalidConfigurationException、升级时新文案键无法补齐。现全部值加引号修复，并在配置迁移器中增加自愈逻辑——磁盘上的旧语言包若解析失败（残留同样写法的文件），自动用 JAR 内置默认文件重建，不再依赖手动删除
- **公会改名在主线程同步执行数据库迁移** ： GuildManager.renameGuild 直接在主线程调用 migrateGuildName，对成员/权限/银行/公会 4 张表逐条 UPDATE，成员多的大公会改名瞬间会卡服数秒。现改名迁移并入异步保存队列，与本批保存严格按"先迁移后保存"的顺序串行执行（先迁移可避免新名数据先落库导致主键冲突），插件关闭时同样保证排空
- **每日银行限额表内存泄漏** ： dailyBankUsage 只在存取款时写入、从不清理，玩家跨天后旧条目永久残留，长期运行内存单调增长。现并入每分钟定时清理任务，自动移除非当日条目
- **旧版升级只警告不迁移** ： checkConfigVersion 检测到配置版本落后时仅打印 3 行警告，新配置项全靠各配置类的默认值兜底，玩家无从得知哪些项生效。现整体替换为真正的自动迁移器（见【新增】）
- **GuildAPI 源码排版错乱** ： 反编译遗留的级联缩进使 GuildAPI.java 方法闭合关系混乱、极易在后续修改中被破坏。现整文件重写为规范排版，全部查询方法改为单次遍历实现（旧版 getGuildByTag / searchGuilds 等使用 stream 链式多次流转换）
- **统计接口重复计算** ： getTotalMemberCount 旧版新建 HashSet 去重全服成员（公会成员 UUID 本就不会跨公会重复），白白产生大集合；现改为直接累加各公会成员数

## 【优化】

- **配置文件体系全面升级（v1.4）** ： config.yml 重写并逐项补充中文说明（数据库、语言、调试等），新增 message.prefix（可自定义全局聊天前缀）与 update-check（自动更新检查）两个配置区，所有新增项在旧配置文件中自动补齐并附带注释
- **API 异步方法线程调度统一** ： v1 API 全部 *Async() 方法与 v2 一致改为统一的 supplyOnMainThread 调度器（主线程调用立即执行、异步调用调度回主线程），并补充异常捕获——旧版任务抛出的异常会静默丢失，调用方永远拿不到失败原因
- **数据库初始化健壮性** ： SQLite 依赖文件（guilds.db / -wal / -shm）统一备份后再建连接池；表结构在 createTables 后统一走列补全校验，任何旧版本数据库都能自动补齐缺失列后再加载数据
- **启动流程整合** ： GuildPlugin.onEnable 中的配置检查、语言加载、管理器/数据库初始化顺序经过重新梳理，配置迁移在最前执行，保证后续所有模块读到的都是 v1.4 配置
- **全量代码注释清理** ： 应要求移除全部 Java 源码注释（41 个文件），不改变任何逻辑；YAML/配置文件中的说明性注释保留

## 【新增】

- **自动更新检查** ： 新增 UpdateChecker，启动后异步请求 GitHub 版本文件（raw.githubusercontent.com）并在运行期间按小时定时复查；默认配置 3 条线路按顺序回退（GitHub 直连 → ghfast.top 加速 → gh-proxy.com 加速），任一可达即完成检查，全部失败时输出提示并允许在 config.yml mirrors 中自行追加镜像。发现新版本时控制台提示下载地址，并对拥有 guild.admin（可配置 notify-permission）的在线玩家在聊天栏提醒，管理员进服时也会再次提醒
- **配置自动迁移器（ConfigMigrator）** ： 检测到配置版本低于 1.4 时自动执行一键升级——先将全部 yml（config/features/currency/guild/experience/bank/gui/shop + 3 份语言包）备份至 plugins/Guild/backup/config-v旧版本-时间戳/ 目录，再从 JAR 内置默认配置向现有文件补齐缺失键（玩家自定义值一律保留、只加不改），最后写入 config-version: 1.4。全新安装不触发任何迁移
- **旧数据库自动转换与备份** ： 启动建库前自动备份 SQLite 数据文件（含 WAL/SHM）至 plugins/Guild/backup/db-时间戳/；随后对 8 张表逐一校验列结构，旧版本（3.0.9 及以下任意时期）缺失的列（如昵称、禁言到期、每日经验/贡献、每日限额等）自动 ALTER TABLE 补齐，实现旧库无损一键升级；MySQL 同样通过 information_schema 校验后自动补列
- **外部 API v2（com.guild.api.v2）** ： 全新契约式 API 三件套——GuildApiV2（接口，含 API_VERSION 常量）、GuildApiV2Impl（实现）、GuildApiV2Provider（静态获取器，get/require/isAvailable）。v2 对 v1 全面增强：全部变更操作（创建/解散/加入/离开/踢出/升降职/转让/升级/银行存取）提供 CompletableFuture 异步版本并自动调度回主线程（事件触发安全）；查询方法全部空安全；新增 searchGuilds(keyword, limit) 限量搜索、getGuildCurrencyBalance / depositGuildCurrency / withdrawGuildCurrency 公会币操作等。v1 GuildAPI 保留兼容并标记 @Deprecated，内部委托同一管理器，老插件无需改动
- **全局消息前缀** ： 新增 message.prefix 配置与 lang 三语包 message.prefix 键，插件暴露 getPrefix() 供命令与第三方插件统一取用
- **语言包新增 2 个键** ： message.prefix、update.available，简中/繁中/英文三份同步；旧版服务器的语言文件在迁移时自动补齐

## 【其他】

- **升级须知** ： 从 3.0.9 及以下版本直接替换 JAR 即可完成升级，无需手动操作——首次启动会自动完成配置备份/补齐（backup/config-v*）与数据库备份/补列（backup/db-*）；若希望开启更新检查提醒，请确认 config.yml 中 update-check.enabled 为 true（全新配置默认开启，旧文件迁移后同样自动获得）
- **仍为预留未接入的配置** ： bank.yml 的 interest.*、experience.yml 的 weekly-reward.* 与 cumulative-contribution、guild.yml 的 auto-dormant-days、features.yml 的 combat-tag 与 cooldown-bar 本版仍未实现，后续版本再行接入
- **编译产物** ： mvn package 构建通过，输出 target/Guild-3.1.0.jar（含 HikariCP shade 重定位）
- **版本号更新** ： 插件版本更新至 3.1.0（pom.xml / plugin.yml）；配置文件版本更新至 1.4（config.yml / ConfigMigrator.TARGET_VERSION）；作者更新为 Reationura(ya_xzer21145)（plugin.yml author / 三份语言包 plugin.author）
