# Guild 公会插件使用文档

> 插件版本：3.1.0 ｜ 配置文件版本：1.4 ｜ 作者：Reationura(ya_xzer21145)
> 功能全面的 Minecraft 公会管理插件：GUI 可视化操作 / 等级经验 / 银行 / 商店 / 聊天频道 / 多货币 / 多语言 / 开发者 API

---

## 目录

1. [概述与兼容性](#1-概述与兼容性)
2. [安装与快速开始](#2-安装与快速开始)
3. [核心功能详解](#3-核心功能详解)
4. [命令一览](#4-命令一览)
5. [权限节点](#5-权限节点)
6. [PlaceholderAPI 变量](#6-placeholderapi-变量)
7. [开发者 API 与事件](#7-开发者-api-与事件)
8. [配置文件总览](#8-配置文件总览)
9. [数据库说明](#9-数据库说明)
10. [性能与线程安全设计](#10-性能与线程安全设计)
11. [预留配置说明](#11-预留配置说明)
12. [常见问题 FAQ](#12-常见问题-faq)

---

## 1. 概述与兼容性

### 功能亮点

- 🏰 **完整公会体系**：创建、入会申请、邀请、离开、解散、踢出、晋升/降级、会长移交、重命名
- 🖥️ **GUI 可视化操作**：主界面 / 公会列表 / 银行 / 管理 / 成员设置 / 商店六大界面，支持铁砧与聊天框两种输入方式，无需记忆命令
- 📊 **等级与经验系统**：击杀怪物、挖掘方块、钓鱼、繁殖、交易、附魔、PVP 等 13 种行为均可产出公会经验，等级公式与上限全部可配置
- 🏦 **银行系统**：与玩家钱包真实打通的存取款，支持限额、税率、每日限额、职位权限、交易记录
- 🛒 **公会商店**：shop.yml 自定义商品，按公会等级解锁，支持发放命令/物品/经验/公会币等 5 种动作
- 💰 **多货币支持**：内置公会币（持久化落库）/ Vault 经济 / PlayerPoints 点券，一处配置全局切换
- 💬 **聊天频道**：公会频道 + 管理频道，格式完全配置化
- 🏷️ **标签与公告**：公会标签（含颜色代码白名单）、MOTD 公告、成员昵称
- 👥 **三级角色权限**：会长 OWNER / 管理员 OFFICER / 成员 MEMBER， guild.yml 支持按功能键设置所需最低职位
- 📱 **上下线通知**：按玩家个人设置开关，可在 GUI 中自由切换
- 🌐 **多语言**：内置简体中文（zh_cn）、繁体中文（zh_tw）、英文（en_US）三套语言包
- 🔄 **自动更新检查**：启动与运行期间定时从 GitHub 检查新版本（直连 + ghfast.top + gh-proxy.com 三线路自动回退，可自定义镜像），发现新版本时提醒管理员
- 🛡️ **一键升级迁移**：旧版本（3.0.9 及以下）直接替换 JAR 即可自动完成配置与数据库的备份、补齐与转换
- 📈 **排行榜**：今日贡献排行、公会等级总览
- 💾 **SQLite / MySQL 双数据库**：HikariCP 连接池，事务写入
- ⚡ **高性能**：全异步保存队列、玩家名缓存、异步数据库 I/O，主线程零阻塞

### 兼容范围

| 项目 | 支持情况 |
|------|----------|
| Minecraft 版本 | 1.8 ~ 26.2+（原生兼容，无需 Via 类插件） |
| 服务端类型 | Spigot / Paper / Purpur / **Folia**（`folia-supported: true`） |
| Java | 8 及以上（产物按 Java 8 字节码编译） |
| Vault | 软依赖，未安装自动降级 |
| PlaceholderAPI | 软依赖，未安装不注册变量 |
| PlayerPoints | 软依赖，未安装自动降级 |
| ProtocolLib | 软依赖 |

---

## 2. 安装与快速开始

1. 将 `Guild-3.1.0.jar` 放入服务器 `plugins/` 目录
2. 启动服务器，插件自动生成全部配置文件与语言包
3. 按需修改 `plugins/Guild/` 下的配置（每个文件顶部都有注释说明）
4. 修改后执行 `/guild reload` 重载，**无需重启服务器**
5. 玩家进服即用：`/guildgui` 或 `/guild` 打开主界面

> **从 3.0.9 及以下版本升级**：直接替换 JAR 即可，无需任何手动操作。首次启动会自动执行一键迁移：全部配置文件与语言包先备份至 `plugins/Guild/backup/config-v旧版本-时间戳/`，再从内置默认配置补齐缺失项（只加不改）；SQLite 数据库（含 WAL/SHM）备份至 `backup/db-时间戳/`，并自动补齐旧库缺失的列。磁盘上存在损坏写法（未加引号的冒号值）的语言包会被自动重建。

---

## 3. 核心功能详解

### 3.1 公会生命周期

| 环节 | 说明 |
|------|------|
| **创建** | `/guild create <名称> [标签]` 或 GUI 内点「创建公会」→ 聊天框/铁砧依次输入名称与标签。名称受长度（`min/max-name-length`）、正则（`name-regex`）、黑名单（`banned-names`）三重校验；创建费由 currency.yml 统一扣取（`create.requires-money` + `create.cost`） |
| **入会申请** | `/guild join <公会名>` 提交申请，会长/管理员在 GUI「批准 / 拒绝」；申请 10 分钟自动过期 |
| **邀请** | `/guild invite <玩家>`，受邀玩家 `/guild accept` / `/guild decline`；邀请 10 分钟过期，受邀者可在个人设置关闭邀请接收 |
| **离开** | `/guild leave`，普通成员直接退出（需输入 confirm 确认）；会长离开等于解散公会 |
| **踢出** | `/guild kick <玩家>` 或 GUI 成员操作。3.0.8 起强制角色保护：不能踢自己、不能踢会长、非会长不能踢同级或更高级成员 |
| **解散** | `/guild disband`（聊天确认），或管理员 `/guild delete <公会名>`（二次 confirm）。解散会清理成员索引、指向该公会的邀请，并异步删除数据库记录 |
| **重命名** | `/guild rename <新名称>` 或 GUI「重命名」。**原地迁移**全部成员/等级/经验/银行/权限数据（3.0.7 起取代旧「解散重建」实现），仅会长可用，校验与创建一致 |

### 3.2 角色与权限体系

- 三级角色：`MEMBER(1)` → `OFFICER(2)` → `OWNER(3)`
- 晋升/降级：`/guild promote|demote <玩家>` 或 GUI 成员设置界面；已到顶/到底时操作返回失败（3.0.8 起不再误报成功）
- 会长移交：`/guild transfer <玩家>`
- **公会内权限门槛**：`/guild permission <权限键> <等级>` 可逐项设置该操作需要的最低职位（1=成员 2=管理员 3=会长），修改实时落库。常用权限键：`kick`、`invite`、`chat`、`motd`、`settings`、`promote` 等
- GUI 根据角色展示不同按钮（会长见「管理公会/升级公会/购买经验」，管理员见「管理公会」）

### 3.3 GUI 界面

| 界面 | 打开方式 | 内容 |
|------|----------|------|
| 主界面 | `/guildgui` 或 `/guild` | 公会信息、成员列表（头骨展示）、银行/商店入口、邀请与通知开关 |
| 公会列表 | 主界面「查看所有公会」 | 全部公会按等级排序展示 |
| 银行界面 | `/guildgui bank` 或主界面「公会银行」 | 余额展示、存入/取出（聊天输入金额）、交易记录 |
| 管理界面 | 主界面「管理公会」（会长/管理员） | 重命名、更改标签、解散引导 |
| 成员设置 | 点击成员头骨 | 设置昵称、晋升/降级、踢出（按权限显示） |
| 商店界面 | 主界面「公会商店」 | shop.yml 商品展示、等级锁定提示、点击购买 |

所有界面均可拖拽防泄漏（取消点击/拖拽/移动事件），标题与物品外观由 gui.yml 配置。

### 3.4 等级与经验系统

- **经验获取**（experience.yml `events`，0 = 关闭该项）：击杀怪物 10、挖掘 5、放置 3、PVP 击杀 50、钓鱼 8、繁殖 15、村民交易 20、附魔 12、合成 5、酿造 6、熔炼 4、驯服 10、达成成就 30（均为默认值，可任意调整）
- **防刷屏**：`no-exp-blocks` / `no-exp-mobs` 黑名单方块与实体不给经验
- **升级公式**：`base × level × (1 + level × growth-rate)`，level-formula 支持 `max-required` 封顶；成员上限 = `base-members + level × members-per-level`；等级上限 `max-level`（guild.yml）
- **加成与限制**：周末倍率、节假日倍率、每日经验上限、每日贡献上限（增量截断而非整单拒绝）
- **每日重置**：跨天自动重置日常经验/贡献与银行每日限额（features.yml `daily-reset`）
- **升级事件**：自然升级与付费升级均触发 `GuildLevelUpEvent`（3.0.8 起全路径恰好一次）
- 管理员干预：`/guild gexp`（给经验）、`/guild slevel`（设等级）、`/guild clxplev`（清零）
- 付费途径：GUI/命令「购买经验」按 currency.yml `experience-cost/amount` 消耗货币；「升级公会」按 `level-up-cost × (1 + level × 0.08)` 扣费

### 3.5 银行系统

- 存款 `/guild deposit <金额>`，取款 `/guild withdraw <金额>`，查询 `/guild balance`，或 GUI 银行界面
- **与玩家钱包真实打通**：存款先扣玩家货币、取款后入账玩家货币，任一环节失败自动回滚，杜绝凭空造币
- bank.yml 全面生效：单次存/取上限、银行余额上下限、存/取税率（按比例折算实际到账）、每日存/取限额（跨天重置）、最低职位限制（`withdraw-permission: OFFICER` 默认取款需管理员）
- 每位玩家保留最近 50 条存取记录（GUI 可查），大额交易写控制台日志
- 整体开关：features.yml `bank`，关闭时命令直接拒绝

### 3.6 货币系统（currency.yml）

| 类型 | 说明 |
|------|------|
| `GUILD_COIN` | 内置公会币（默认）。**独立持久化**于 player_currency 表，重启不丢；支持 `initial-balance` 进服发放（数据库行存在性保证终生一次）、`max-balance` 上限截断 |
| `VAULT` | 对接 Vault 经济（需安装 Vault + 前置经济插件） |
| `PLAYERPOINTS` | 对接 PlayerPoints 点券（反射调用，无需硬依赖） |

`type` 决定创建/升级/购买经验扣除的货币；商店商品可在 shop.yml 逐件指定货币。显示名称与千分位格式化均可配置。

### 3.7 公会商店（shop.yml）

每件商品字段：`slot / material / name / lore / required-level / price / currency-type / action / action-value / buy-message / permission`。

支持 5 种购买动作：

| action | 效果 | action-value 示例 |
|--------|------|-------------------|
| `COMMAND` | 以玩家身份执行命令 | `home set`（支持 %player%） |
| `CONSOLE` | 以控制台身份执行命令 | `give %player% diamond 3` |
| `GIVE_ITEM` | 直接给物品 | `DIAMOND:3`（材质:数量） |
| `GUILD_EXP` | 给公会加经验 | `200` |
| `GUILD_COIN` | 给玩家发公会币 | `500` |

未到等级商品显示为「[锁定]」样式；购买按件校验公会等级、权限与余额。

### 3.8 聊天频道

- `/guild chat <消息>` 或 `/guildchat <消息>`（别名 `/gc`、`/guildc`）：公会频道
- `/guild officerchat <消息>`：管理频道（仅会长/管理员可见）
- 格式配置化（guild.yml）：`chat.format` 支持 `{tag_color} {tag} {player} {message}`；`chat.officer-format` 支持 `{player} {message}`
- 发言受公会内 `chat` 权限与禁言状态约束；被 `/guild mute` 禁言的成员无法发言

### 3.9 标签、公告与通知

- 标签：`/guild tag <标签>` + `/guild tagcolor <颜色代码>`（受 `allowed-tag-colors` 白名单限制），GUI 亦可修改
- 公告：`/guild motd <内容>`（≤100 字符），在 `/guild info` 与 GUI 展示
- 昵称：会长/管理员可为成员设置公会昵称（GUI 成员设置 → 设置昵称，输入 C 取消）
- 上下线通知：成员进服/退服时向开启了通知的同会玩家广播；个人开关可通过 GUI「上下线通知」按钮或 `/guild settings` 调整
- 禁言：`/guild mute <玩家> <分钟>` / `/guild unmute <玩家>`（3.0.8 起：分钟数必须 > 0，不能禁言会长或同级/更高级成员，禁言到期时间真实持久化）

### 3.10 多语言

config.yml `language: zh_cn | zh_tw | en_us`。三款语言文件释放于 `plugins/Guild/lang/`，可自由修改文案（YAML 格式，支持 & 颜色代码）。`/guild reload` 后生效。

---

## 4. 命令一览

主命令 `/guild`（别名 `/gh`）。

### 玩家子命令

| 子命令 | 说明 |
|--------|------|
| `help` | 帮助页 |
| `create <名称> [标签]` | 创建公会 |
| `join <公会名>` | 提交入会申请 |
| `accept [公会名]` / `decline` | 接受 / 拒绝邀请 |
| `leave` | 离开公会（confirm 确认） |
| `info` | 查看自己公会信息 |
| `list` | 本会成员列表 |
| `listguilds` | 全部公会列表 |
| `online` | 本会在线成员 |
| `invite <玩家>` | 邀请玩家 |
| `kick <玩家>` | 踢出成员 |
| `promote / demote <玩家>` | 晋升 / 降级 |
| `transfer <玩家>` | 移交会长 |
| `disband` | 解散公会（confirm 确认） |
| `rename <新名称>` | 重命名公会（仅会长） |
| `tag <标签>` / `tagcolor <代码>` | 修改标签 / 标签颜色 |
| `motd <内容>` | 修改公告 |
| `chat <消息>` | 公会频道发言 |
| `officerchat <消息>` | 管理频道发言 |
| `top [页码]` | 今日贡献排行榜 |
| `requests [页码]` | 查看入会申请（管理员） |
| `member <玩家>` | 查看成员详情 |
| `mute <玩家> <分钟>` / `unmute <玩家>` | 禁言 / 解除禁言 |
| `settings <属性> <值>` | 公会设置（如 `public true`） |
| `permission <权限键> <等级>` | 设置操作所需最低职位（会长） |
| `deposit / withdraw <金额>` | 银行存取款 |
| `balance` | 查询银行余额 |
| `upgrade bexp50 \| upgrade` | 购买经验 / 付费升级 |
| `party` | 向在线公会成员发送组队邀请 |
| `reload` | 重载配置（需 guild.admin） |

### 独立命令

| 命令 | 别名 | 说明 |
|------|------|------|
| `/guildchat <消息>` | `/gc` `/guildc` | 快捷公会频道发言 |
| `/guildgui [bank]` | - | 打开 GUI；带 `bank` 直接打开银行界面 |

### 管理员工具子命令（需 `guild.admin` / op）

| 子命令 | 说明 |
|--------|------|
| `gexp <公会名> <数量>` | 直接给予公会经验 |
| `slevel <公会名> <等级>` | 设置公会等级（0 ~ max-level） |
| `clxplev <公会名>` | 公会等级与经验清零 |
| `delete <公会名>` + `delete confirm` | 强制删除任意公会（二次确认） |

---

## 5. 权限节点

| 权限节点 | 说明 | 默认 |
|----------|------|------|
| `guild.*` | 全部权限 | op |
| `guild.admin` | 管理权限集合（创建/邀请/踢出/升降级/解散/升级/银行/管理/设置/聊天） | op |
| `guild.player` | 基础玩家权限（聊天/信息/商店） | 所有人 |
| `guild.create` | 创建公会 | op |
| `guild.invite` / `guild.kick` / `guild.promote` / `guild.demote` | 邀请 / 踢出 / 晋升 / 降级 | op |
| `guild.disband` / `guild.upgrade` | 解散 / 升级公会 | op |
| `guild.bank` | 使用银行 | 所有人 |
| `guild.chat` | 公会频道发言 | 所有人 |
| `guild.manage` | 管理公会设置（重命名/标签/MOTD） | op |
| `guild.settings` | 修改个人设置 | 所有人 |
| `guild.info` / `guild.shop` | 查看信息 / 使用商店 | 所有人 |

> 权限树采用 children 继承设计，配合权限插件（如 LuckPerms）按需下放即可。

---

## 6. PlaceholderAPI 变量

前置：安装 PlaceholderAPI，识别符为 `guild`（变量写作 `%guild_xxx%`）。无公会玩家返回空或 0，不会报错。

| 变量 | 说明 |
|------|------|
| `%guild_name%` | 公会名称 |
| `%guild_tag%` / `%guild_tag_color%` | 公会标签 / 标签颜色 |
| `%guild_level%` | 公会等级 |
| `%guild_experience%` / `%guild_exp%` | 当前经验 |
| `%guild_required_experience%` / `%guild_required_exp%` | 升到下一级所需经验 |
| `%guild_level_<n>%` | 升到第 n 级所需经验（按 experience.yml 公式计算，3.0.8 起与配置一致） |
| `%guild_owner%` | 会长名（缓存查询，无主线程 I/O） |
| `%guild_member_count%` / `%guild_max_members%` | 成员数 / 成员上限 |
| `%guild_motd%` | 公会公告 |
| `%guild_role%` / `%guild_role_level%` | 本人在公会的职位名 / 职位等级 |
| `%guild_contribution%` / `%guild_daily_contribution%` | 本人总贡献 / 今日贡献 |
| `%guild_joined_time%` | 本人加入时间戳 |
| `%guild_has_guild%` / `%guild_is_owner%` / `%guild_is_officer%` / `%guild_is_member%` | 布尔状态（true/false） |
| `%guild_bank_balance%` | 公会银行余额 |
| `%guild_currency%` | 本人公会币余额 |
| `%guild_created_time%` | 公会创建时间戳 |

---

## 7. 开发者 API 与事件

### GuildApiV2（`com.guild.api.v2`，3.1.0 起推荐）

```java
GuildApiV2 api = GuildApiV2Provider.get();          // 就绪前返回 null
if (GuildApiV2Provider.isAvailable()) { ... }       // 就绪检查
GuildApiV2 api = GuildApiV2Provider.require();      // 未就绪直接抛异常提示
GuildData guild = api.getPlayerGuild(uuid);         // 只读数据视图
```

- **契约式接口**：`GuildApiV2`（含 `API_VERSION = "2.0"` 常量）/ `GuildApiV2Impl`（实现）/ `GuildApiV2Provider`（静态获取器）
- 查询（全部空安全）：`getGuild / getGuildByTag / searchGuilds(keyword, limit) / getPlayerGuild / isInGuild / getMemberRole / getAllGuildList / getTopGuilds / getOnlineMembers / getOnlineCount`
- 统计：`getTotalGuildCount / getTotalMemberCount / getAverageLevel / getBankBalance`
- 同步操作：`createGuild / disbandGuild / joinGuild / leaveGuild / kickMember / promoteMember / demoteMember / transferOwnership / upgradeGuild / sendInvite / acceptInvite / declineInvite`
- **异步操作**：以上写操作 + `depositToBank / withdrawFromBank` 均提供 `xxxAsync(...)` 返回 `CompletableFuture`，自动调度回主线程（事件触发安全），异常通过 Future 正确传递
- 公会币：`getGuildCurrencyBalance / depositGuildCurrency / withdrawGuildCurrency`
- 其他：`addExperience / getMessage / openMainGUI / registerListener / getApiVersion / getPlugin`

### GuildAPI v1（`com.guild.api.GuildAPI`，已标记 @Deprecated，保持兼容）

```java
GuildAPI api = GuildAPI.getInstance();          // onEnable 后可用
if (GuildAPI.isApiReady()) { ... }              // 就绪检查
GuildData guild = api.getPlayerGuild(uuid);     // 只读数据视图
```

- v1 全部方法继续可用并委托同一管理器，老插件无需改动；新项目建议迁移至 v2
- 查询：`getGuild / getGuildByTag / searchGuilds / getPlayerGuild / getAllGuilds / getTopGuilds / getMemberRole / getOnlineMembers / getOnlineCount`
- 操作：`createGuild / disbandGuild / joinGuild / leaveGuild / kickMember / promoteMember / demoteMember / transferOwnership / sendInvite / acceptInvite / upgradeGuild / depositToBank / withdrawFromBank / addExperience`
- 异步：以上写操作均提供 `xxxAsync(...)` 返回 `CompletableFuture`（内部自动切回主线程，3.1.0 起异常不再静默丢失）
- GUI：`openMainGUI / openBankGUI / openShopGUI`
- 工具：`getMessage / reloadConfig / getTotalGuildCount / getAverageLevel`

### 事件（`com.guild.api.event`）

| 事件 | 可取消 | 触发时机 |
|------|--------|----------|
| `GuildCreateEvent` | ✅ | 创建公会前；监听器可 `setGuildName / setGuildTag` 改名改标签，对所有创建路径（命令/GUI/API）生效 |
| `GuildJoinEvent` | ✅ | 成员加入前（申请批准、邀请接受、直接加入均触发） |
| `GuildLeaveEvent` | ✅ | 成员离开前（仅在线玩家触发） |
| `GuildKickEvent` | ✅ | 成员被踢出前 |
| `GuildDisbandEvent` | ❌ | 公会解散成功后 |
| `GuildLevelUpEvent` | ❌ | 等级提升后（旧等级 → 新等级） |

> **3.0.8 重要变更**：事件触发点已统一下沉至 GuildManager。经任何路径（命令、GUI、API）操作都**恰好触发一次**事件；第三方插件不应再自行补发。所有非异步事件均在主线程触发。

接入方式：软依赖 `depend`/`softdepend: [Guild]`，通过 `GuildAPI` 或 Bukkit 事件监听即可。

---

## 8. 配置文件总览

| 文件 | 内容 |
|------|------|
| `config.yml` | 配置版本号、语言（zh_cn/zh_tw/en_us）、消息前缀（message.prefix）、数据库（sqlite/mysql 及连接参数、连接池）、自动更新检查（update-check：开关/间隔/提醒权限/镜像列表）、调试开关 |
| `features.yml` | 10 个功能模块开关 + 自动保存/每日重置等进阶开关 |
| `guild.yml` | 等级/成员上限、名称长度/正则/黑名单、标签颜色白名单、聊天格式（chat.format / officer-format） |
| `currency.yml` | 货币类型、创建费用、升级费用曲线、购买经验价、公会币初始余额与上限、显示格式化 |
| `experience.yml` | 13 种经验获取事件、每日上限、周末/节假日倍率、升级公式、防刷黑名单、周榜奖励（预留） |
| `bank.yml` | 存取限额、税率、职位权限、每日限额、利息（预留）、历史记录 |
| `shop.yml` | 商店全局设置与商品列表（5 种动作类型） |
| `gui.yml` | 各 GUI 界面标题、尺寸、物品外观与文案 |
| `lang/zh_cn.yml` `lang/zh_tw.yml` `lang/en_US.yml` | 全部玩家可见消息文案 |

所有文件均可 `/guild reload` 热重载。

---

## 9. 数据库说明

- **双后端**：`config.yml database.type` 选 `sqlite`（默认，单文件 `plugins/Guild/` 内）或 `mysql`（host/port/pool-size/connection-timeout 全部真实生效，InnoDB + utf8mb4）
- **连接池**：HikariCP（已 shade 重定位至 `com.guild.lib.hikari`，不与其他插件冲突）
- **表结构**：`guilds`（公会主数据）、`guild_members`（成员/角色/贡献/禁言/昵称）、`guild_permissions`（公会内权限门槛）、`guild_banks`（银行余额）、`player_currency`（公会币余额）
- **写入策略**：`saveGuild` 事务化——主数据 upsert、成员表**先删后插**（杜绝离会成员复活，3.0.8）、权限与银行 upsert；解散时异步级联删除
- 重命名公会会同步迁移四张关联表（事务；3.1.0 起迁移移入异步保存线程，主线程零 I/O）
- **自动迁移与备份（3.1.0 起）**：启动建池前自动备份 SQLite 数据文件至 `backup/db-时间戳/`；随后对 8 张表逐列校验，旧版本缺失的列（昵称、禁言到期、每日经验/贡献等）自动 `ALTER TABLE` 补齐；MySQL 通过 information_schema 校验后同样自动补列

---

## 10. 性能与线程安全设计

3.0.8/3.1.0 完成的主要性能优化：

- **主线程零数据库 I/O**：全部公会写操作进入异步保存队列（约 2.5 秒批量落盘）；解散删库、账户初始化、公会改名迁移（3.1.0）等均为异步任务
- **玩家名缓存**：高频 UUID→名字解析统一走缓存，避免 `Bukkit.getOfflinePlayer()` 触发的磁盘读档
- **内存防泄漏**：入会申请/邀请定时过期清理；银行历史记录每人限 50 条；每日银行限额表跨天自动清理（3.1.0）；玩家下线清理挂起的聊天输入与 GUI 状态
- **线程安全**：核心索引使用 ConcurrentHashMap / CopyOnWriteArrayList；升级循环、禁言状态等均有边界防护；异步聊天监听器内的公会操作统一切回主线程
- **API 线程调度统一**（3.1.0）：v1/v2 全部异步方法统一走 supplyOnMainThread 调度器并传递异常

---

## 11. 预留配置说明

以下配置项当前已在配置文件中提供，但**业务逻辑尚未接入**（修改不会产生实际效果，未来版本计划启用）：

- guild.yml：`auto-dormant-days`（公会自动休眠）
- bank.yml：`interest.*`（银行存款利息）
- experience.yml：`weekly-reward.*`（周榜奖励发放）、`cumulative-contribution`
- features.yml：`combat-tag`、`cooldown-bar`（`auto-disband` 未接入）

> 3.0.9 已接通：guild.yml 的 8 项冷却（disband/invite/join/promote/demote/kick/rename/tag-change）、`max-officers`、`allow-self-demote`、`auto-kick-inactive-days`；其余所有配置项均已真实生效。

---

## 12. 常见问题 FAQ

**Q1：插件支持哪些服务端？Folia 能用吗？**
Spigot / Paper / Purpur / Folia 全支持，Minecraft 1.8 ~ 26.2+。`plugin.yml` 已声明 `folia-supported: true`。

**Q2：没装 Vault 能用吗？**
能。Vault / PlayerPoints / PlaceholderAPI 均为软依赖，缺失时自动降级；默认货币为内置公会币（GUILD_COIN），开箱即用且重启不丢数据。

**Q3：创建公会扣了几次钱？**
3.0.8 起命令、GUI 铁砧、API 三条路径全部统一为 **createGuild 内单次扣费**，不再预扣/退款。

**Q4：被踢的玩家为什么不会再"复活"？**
3.0.8 起 `saveGuild` 在事务内先清空该公会成员行再批量插入，离会成员的数据库记录同步删除。

**Q5：GUI 里改了职位/昵称/踢人，重启会丢吗？**
不会。3.0.8 起所有 GUI 写操作即时进入异步保存队列落库。

**Q6：重命名公会会丢成员或银行余额吗？**
不会。3.0.7 起 `renameGuild` 原地迁移内存索引与四张关联表，不重复扣创建费。

**Q7：如何阻止普通成员取款？**
bank.yml `withdraw-permission: OFFICER`（默认已是）；或用 `/guild permission` 调整任意操作的最低职位门槛。

**Q8：提示"配置文件版本较旧"要怎么办？**
无需处理。3.1.0 起配置迁移完全自动化：旧配置先备份至 `plugins/Guild/backup/config-v旧版本-时间戳/`，随后自动补齐缺失配置项（玩家自定义值全部保留）并写入 `config-version: 1.4`。数据库同样先备份再自动补列，数据不受影响。

**Q9：占位符不显示值？**
确认已安装 PlaceholderAPI 且 Guild 在其之后加载（软依赖已自动处理）；无公会玩家变量返回空属正常设计。

**Q10：升级一下子就满级了 / 经验公式配错怎么办？**
3.0.8 起等级公式计算结果 ≤ 0 时直接跳出升级循环，杜绝瞬间刷满级；请检查 experience.yml `level-formula.base` 是否误配为 0。

**Q11：启动时报 "mapping values are not allowed here" 或语言包解析失败？**
3.0.9 及更早版本的语言包 plugin.author 一行未加引号导致 YAML 解析失败。3.1.0 已修复源头写法，并在启动时自动检测：磁盘上解析失败的语言包会用内置默认文件重建，无需手动删除。

**Q12：更新检查提示"所有更新源均无法访问"？**
插件按顺序尝试 GitHub 直连与两个国内加速镜像（ghfast.top / gh-proxy.com），全部超时（8 秒）才会失败。网络受限的服务器可在 config.yml `update-check.mirrors` 追加自定义镜像，或将 `enabled` 设为 false 关闭检查。
