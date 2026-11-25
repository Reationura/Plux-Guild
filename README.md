# Guild

> 功能全面的 Minecraft 公会管理插件 - 支持 GUI/等级经验/银行系统/聊天频道/多货币/自动更新检查 | 兼容 1.8 ~ 26.2+

![Version](https://img.shields.io/badge/版本-3.1.0-orange)
![Java](https://img.shields.io/badge/Java-8-blue)
![Folia](https://img.shields.io/badge/Folia-supported-green)

## 功能特性

- 🏰 **公会系统** - 完整的公会创建、管理、解散、重命名功能
- 🖥️ **GUI 界面** - 可视化操作界面，聊天输入创建公会，无需记忆命令
- 📊 **等级系统** - 公会最高 100 级（可配置），等级提升解锁更多成员上限
- ⚡ **经验系统** - 击杀怪物/玩家、挖掘、放置、钓鱼、繁殖、交易、附魔、合成、酿造、熔炼、驯服、成就等 13 种行为均可获取公会经验
- 🏦 **银行系统** - 公会成员存取资金，支持限额、税率、每日限额、职位权限、大额审计日志
- 🛒 **公会商店** - shop.yml 自定义商品，按等级解锁，支持命令/物品/经验/公会币等 5 种动作
- 💬 **聊天频道** - 独立公会聊天与管理频道，格式完全配置化
- 🏷️ **标签系统** - 自定义公会标签，支持颜色代码白名单
- 📝 **公告系统** - 公会 MOTD 公告显示
- 👥 **角色权限** - 会长 / 管理员 / 成员 三级权限体系，8 项操作冷却可配置
- 📩 **邀请系统** - 邀请/申请双通道入会，支持过期验证与冷却
- 📱 **上下线通知** - 成员上线/下线自动广播，个人开关
- 🔄 **自动更新检查** - GitHub 三线路（直连 + 双加速镜像）回退检查新版本
- 🛡️ **一键升级** - 旧版本自动备份并迁移配置与数据库，无需手动操作
- 🌐 **多语言** - 内置简体中文、繁体中文、英文三套语言包
- 📈 **排行榜** - 今日贡献排行、公会等级经验排行榜
- 💾 **多数据库支持** - 同时支持 SQLite 和 MySQL（HikariCP 连接池）
- ⚡ **高性能** - 全异步保存队列、缓存化查询，主线程零数据库 I/O

## 兼容版本

- Minecraft 1.8 ~ 26.2+
- 支持 Folia 服务端
- 支持 Spigot / Paper / Purpur 等主流服务端

## 软依赖

| 插件 | 作用 |
|------|------|
| Vault | 经济系统支持（可选） |
| PlaceholderAPI | 变量支持（可选） |
| PlayerPoints | 点券货币支持（可选） |
| ProtocolLib | 协议库支持（可选） |

## 快速开始

### 安装

1. 下载最新版本的 `Guild-3.1.0.jar`
2. 将插件放入服务器的 `plugins` 文件夹
3. 启动服务器，插件将自动生成配置文件
4. 根据需要修改 `plugins/Guild/config.yml` 配置文件
5. 执行 `/guild reload` 重载配置

### 从旧版本升级（3.0.9 及以下）

直接替换 JAR 即可，首次启动自动完成：

- 全部配置与语言包备份至 `plugins/Guild/backup/config-v旧版本-时间戳/`，并补齐缺失配置项（自定义值保留）
- 数据库备份至 `backup/db-时间戳/`，缺失列自动补齐
- 损坏的语言包自动用内置默认文件重建

### 编译

项目使用 Maven 构建：

```bash
mvn clean package
```

编译完成后，jar 文件位于 `target/` 目录下。

## 命令列表

### 玩家命令

主命令 `/guild`（别名 `/gh`）：

| 命令 | 描述 |
|------|------|
| `/guild create <名称> [标签]` | 创建公会 |
| `/guild info` | 查看公会信息 |
| `/guild join <公会名>` | 提交入会申请 |
| `/guild invite <玩家>` | 邀请玩家 |
| `/guild kick <玩家>` | 踢出成员 |
| `/guild leave` | 离开公会 |
| `/guild disband` | 解散公会 |
| `/guild rename <新名称>` | 重命名公会 |
| `/guild tag <标签>` / `tagcolor <代码>` | 修改标签/颜色 |
| `/guild motd <内容>` | 修改公告 |
| `/guild chat <消息>` / `officerchat <消息>` | 公会/管理频道发言 |
| `/guild deposit <金额>` / `withdraw <金额>` | 银行存/取款 |
| `/guild balance` | 查询银行余额 |
| `/guild upgrade` | 付费升级公会 |
| `/guild top` | 贡献排行榜 |
| `/guild promote / demote / transfer <玩家>` | 晋升/降级/移交会长 |
| `/guild mute <玩家> <分钟>` / `unmute <玩家>` | 禁言/解除禁言 |
| `/guild reload` | 重载配置（需 guild.admin） |

独立命令：

| 命令 | 别名 | 描述 |
|------|------|------|
| `/guildchat <消息>` | `/gc`, `/guildc` | 快捷公会聊天 |
| `/guildgui [bank]` | - | 打开 GUI 界面 |

### 管理员命令

| 命令 | 描述 |
|------|------|
| `/guild slevel <公会名> <等级>` | 设置公会等级 |
| `/guild gexp <公会名> <数量>` | 给予公会经验 |
| `/guild clxplev <公会名>` | 清零等级经验 |
| `/guild delete <公会名>` | 强制删除公会（二次确认） |

## 权限节点

| 权限节点 | 描述 | 默认值 |
|----------|------|--------|
| `guild.*` | 所有公会权限 | op |
| `guild.admin` | 管理员权限 | op |
| `guild.player` | 基础玩家权限 | true |
| `guild.create` | 创建公会 | op |
| `guild.invite` | 邀请玩家加入公会 | op |
| `guild.kick` | 踢出公会成员 | op |
| `guild.promote` | 提升成员职位 | op |
| `guild.demote` | 降低成员职位 | op |
| `guild.disband` | 解散公会 | op |
| `guild.upgrade` | 升级公会等级 | op |
| `guild.bank` | 使用公会银行存取款 | true |
| `guild.chat` | 在公会频道发言 | true |
| `guild.manage` | 管理公会设置 | op |
| `guild.settings` | 修改个人公会设置 | true |
| `guild.info` | 查看公会信息 | true |
| `guild.shop` | 使用公会商店 | true |

## 配置说明

全部配置文件均带中文注释说明，支持 `/guild reload` 热重载。

### 核心配置（config.yml，版本 1.4）

```yaml
language: zh_cn            # zh_cn / zh_tw / en_US
message:
  prefix: "&8[&6公会&8] &r"   # 全局聊天前缀
database:
  type: sqlite             # sqlite 或 mysql
  # mysql 时另有 host/port/database/username/password/pool-size/connection-timeout
update-check:
  enabled: true            # 自动更新检查
  interval-hours: 6        # 检查间隔（小时）
  notify-permission: guild.admin
  mirrors:                 # 三线路回退，可自行追加镜像
    - "https://raw.githubusercontent.com/Reationura/Plux-Guild/refs/heads/main/version"
    - "https://ghfast.top/https://raw.githubusercontent.com/Reationura/Plux-Guild/refs/heads/main/version"
    - "https://gh-proxy.com/https://raw.githubusercontent.com/Reationura/Plux-Guild/refs/heads/main/version"
```

### 拆分配置文件

| 文件 | 内容 |
|------|------|
| `features.yml` | 功能模块开关 |
| `guild.yml` | 等级/成员上限、名称校验、聊天格式、操作冷却 |
| `currency.yml` | 货币类型（GUILD_COIN / VAULT / PLAYERPOINTS）、费用曲线 |
| `experience.yml` | 13 种经验事件、每日上限、倍率、升级公式 |
| `bank.yml` | 存取限额、税率、职位权限、审计日志 |
| `gui.yml` / `shop.yml` | GUI 界面 / 公会商店 |
| `lang/*.yml` | 三套语言包 |

## 开发者 API

```java
// v2（3.1.0 起推荐）
GuildApiV2 api = GuildApiV2Provider.get();
GuildData guild = api.getPlayerGuild(uuid);
api.createGuildAsync("MyGuild", player).thenAccept(g -> { ... });

// v1（保持兼容，已标记 @Deprecated）
GuildAPI api = GuildAPI.getInstance();
```

全部写操作提供 `CompletableFuture` 异步版本（自动调度回主线程）；事件在 `com.guild.api.event` 下（创建/加入/离开/踢出/解散/升级 6 种，前四种可取消）。详见 [WIKI.md](WIKI.md)。

## 文件结构

```
Guild/
├── src/main/java/com/guild/
│   ├── GuildPlugin.java           # 插件主类
│   ├── guild/                     # 公会模型（Guild/GuildManager/GuildMember/GuildRole/GuildBank 等）
│   ├── gui/                       # 7 个 GUI 界面与构建器
│   ├── listeners/                 # 玩家/界面/聊天输入/经验事件监听
│   ├── commands/                  # 主命令/聊天命令/GUI 命令
│   ├── database/                  # DatabaseManager（SQLite/MySQL + 自动迁移备份）
│   ├── cache/                     # 玩家名缓存服务
│   ├── config/                    # 8 个配置类 + ConfigMigrator 自动迁移器
│   ├── api/                       # v1 API（兼容）+ api/v2 新版 API + 6 种事件
│   ├── currency/                  # 内置币/Vault/PlayerPoints 货币实现
│   ├── papi/                      # PlaceholderAPI 变量注册
│   └── utils/                     # UpdateChecker 更新检查 / VersionCompat 版本兼容
├── src/main/resources/
│   ├── plugin.yml / config.yml    # 插件描述 / 主配置（v1.4）
│   ├── *.yml                      # 拆分配置（features/guild/currency/experience/bank/gui/shop）
│   └── lang/                      # zh_cn / zh_tw / en_US 语言包
└── pom.xml                        # Maven 构建配置
```

## 文档

完整使用文档（功能详解/命令/权限/PAPI 变量/FAQ）见 [WIKI.md](WIKI.md)，版本变更见 [AAA-CHANGELOG3.1.0.md](AAA-CHANGELOG3.1.0.md)。

## 开发者信息

- **作者**: Reationura(ya_xzer21145)
- **Java 版本**: 8+
- **构建工具**: Maven

## 许可证

GNU General Public License v3.0（详见 [LICENSE](LICENSE)）
