# Guild

> 功能全面的 Minecraft 公会管理插件 - 支持 GUI/等级经验/银行系统/聊天频道/多货币 | 兼容 1.8 ~ 1.26.1+

![Java](https://img.shields.io/badge/Java-8-blue)
![Folia](https://img.shields.io/badge/Folia-supported-green)

## 功能特性

- 🏰 **公会系统** - 完整的公会创建、管理、解散功能
- 🖥️ **GUI 界面** - 可视化操作界面，聊天输入创建公会，无需记忆命令
- 📊 **等级系统** - 公会最高 100 级，等级提升解锁更多成员上限
- ⚡ **经验系统** - 击杀怪物、挖掘方块、PVP 均可获取公会经验
- 🏦 **银行系统** - 公会成员存取资金，支持内置币/Vault/PlayerPoints
- 💬 **聊天频道** - 独立公会聊天频道，支持格式化前缀
- 🏷️ **标签系统** - 自定义公会标签，支持颜色代码
- 📝 **公告系统** - 公会 MOTD 公告显示
- 👥 **角色权限** - 会长 / 管理员 / 成员 三级权限体系
- 📩 **邀请系统** - 邀请玩家加入公会，支持过期验证
- 📱 **上下线通知** - 成员上线/下线自动广播
- 🌐 **多语言** - 内置简体中文、繁体中文、英文三套语言包
- 📈 **排行榜** - 公会等级经验排行榜
- 💾 **多数据库支持** - 同时支持 SQLite 和 MySQL
- ⚡ **高性能** - 使用 HikariCP 连接池和 ConcurrentHashMap 保证线程安全

## 兼容版本

- Minecraft 1.8 ~ 1.26.1+
- 支持 Folia 服务端
- 支持 Spigot / Paper / Purpur 等主流服务端

## 软依赖

| 插件 | 作用 |
|------|------|
| Vault | 经济系统支持（可选） |
| PlaceholderAPI | 变量支持（可选） |
| ProtocolLib | 协议库支持（可选） |

## 快速开始

### 安装

1. 下载最新版本的 `Guild-3.0.4.jar`
2. 将插件放入服务器的 `plugins` 文件夹
3. 启动服务器，插件将自动生成配置文件
4. 根据需要修改 `plugins/Guild/config.yml` 配置文件
5. 执行 `/guild reload` 重载配置

### 编译

项目使用 Maven 构建：

```bash
mvn clean package
```

编译完成后，jar 文件位于 `target/` 目录下。

## 命令列表

### 玩家命令

| 命令 | 别名 | 描述 | 用法 |
|------|------|------|------|
| `/guild` | `/gh` | 公会主指令 | `/guild <子命令>` |
| `/guild create <名称> [标签]` | - | 创建公会 | `/guild create MyGuild TAG` |
| `/guild info [公会名]` | - | 查看公会信息 | `/guild info MyGuild` |
| `/guild invite <玩家>` | - | 邀请玩家 | `/guild invite PlayerName` |
| `/guild kick <玩家>` | - | 踢出成员 | `/guild kick PlayerName` |
| `/guild leave` | - | 离开公会 | `/guild leave` |
| `/guild disband` | - | 解散公会 | `/guild disband` |
| `/guild chat <消息>` | - | 公会频道发言 | `/guild chat Hello` |
| `/guild deposit <金额>` | - | 存入银行 | `/guild deposit 1000` |
| `/guild withdraw <金额>` | - | 取出银行 | `/guild withdraw 500` |
| `/guild top` | - | 公会排行榜 | `/guild top` |
| `/guild reload` | - | 重载配置 | `/guild reload` |
| `/guildchat <消息>` | `/gc`, `/guildc` | 快捷公会聊天 | `/guildchat Hello` |
| `/guildgui` | - | 打开 GUI 界面 | `/guildgui` |

### 管理员命令

| 命令 | 描述 | 用法 | 权限 |
|------|------|------|------|
| `/guild slevel <公会名> <等级>` | 设置公会等级 | `/guild slevel MyGuild 10` | op |
| `/guild gexp <公会名> <数量>` | 给予公会经验 | `/guild gexp MyGuild 1000` | op |
| `/guild clxplev <公会名>` | 清零等级经验 | `/guild clxplev MyGuild` | op |
| `/guild delete <公会名>` | 强制删除公会 | `/guild delete MyGuild` | op |

## 权限节点

| 权限节点 | 描述 | 默认值 |
|----------|------|--------|
| `guild.*` | 所有公会权限 | op |
| `guild.admin` | 管理员权限 | op |
| `guild.player` | 基础玩家权限 | true |
| `guild.create` | 创建公会 | true |
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

## 配置说明

### 货币配置

```yaml
currency:
  type: GUILD_COIN          # GUILD_COIN / VAULT / PLAYERPOINTS
  level-up-cost: 100000     # 升级公会所需货币
  experience-cost: 5000     # 购买经验消耗
  experience-amount: 50     # 购买经验获得量
```

### 公会配置

```yaml
guild:
  max-level: 100           # 最高等级上限
  members-per-level: 5     # 每级增加的最大成员数
  default-tag-color: "&f"  # 默认标签颜色
  min-name-length: 3       # 公会名称最短长度
  max-name-length: 16      # 公会名称最长长度
  max-tag-length: 4        # 标签最长长度
  create-cost: 0           # 创建公会所需货币（0 = 免费）
```

### 经验获取配置

```yaml
experience:
  monster-kill: 10    # 击杀怪物获得的经验值
  block-break: 5      # 挖掘方块获得的经验值
  block-place: 3      # 放置方块获得的经验值
  player-kill: 50     # 击杀玩家获得的经验值
```

### 数据库配置

```yaml
database:
  type: sqlite          # sqlite 或 mysql
  mysql:
    host: localhost
    port: 3306
    database: guild
    username: root
    password: ""
    pool-size: 10
```

### 功能开关

```yaml
features:
  bank-enabled: true           # 公会银行系统
  chat-enabled: true           # 公会聊天频道
  experience-enabled: true     # 经验/贡献度系统
  level-enabled: true          # 公会等级系统
  motd-enabled: true           # 公会公告(MOTD)
  tag-enabled: true            # 公会标签显示
  notification-enabled: true   # 上下线通知
  gui-enabled: true            # GUI 界面
```

## 文件结构

```
Guild/
├── src/main/java/com/guild/
│   ├── GuildPlugin.java           # 插件主类
│   ├── guild/
│   │   ├── Guild.java             # 公会数据模型
│   │   ├── GuildManager.java      # 公会管理器
│   │   ├── GuildMember.java       # 成员数据模型
│   │   ├── GuildRole.java         # 角色枚举
│   │   └── GuildBank.java         # 银行系统
│   ├── gui/
│   │   ├── GuildGUI.java          # 公会主界面
│   │   ├── GuildBankGUI.java      # 银行界面
│   │   ├── GuildManageGUI.java    # 管理界面
│   │   └── GuildListGUI.java      # 公会列表
│   ├── listeners/
│   │   ├── PlayerListener.java    # 玩家事件监听器
│   │   ├── InventoryListener.java # GUI 事件监听器
│   │   └── ChatInputListener.java # 聊天输入监听器
│   ├── commands/
│   │   ├── GuildCommand.java      # 公会主命令
│   │   ├── GuildChatCommand.java  # 聊天命令
│   │   └── GuildGUICommand.java   # GUI 命令
│   ├── database/
│   │   ├── DatabaseManager.java   # 数据库管理器
│   │   └── SQLiteManager.java     # SQLite 实现
│   ├── cache/
│   │   └── PlayerNameCache.java   # 玩家名称缓存
│   ├── config/
│   │   ├── GUIConfig.java         # GUI 配置
│   │   ├── FeatureConfig.java     # 功能配置
│   │   └── CurrencyConfig.java    # 货币配置
│   ├── api/
│   │   ├── GuildAPI.java          # 公开 API
│   │   ├── GuildData.java         # 数据只读视图
│   │   └── GuildInviteInfo.java   # 邀请信息
│   └── currency/
│       ├── GuildCurrency.java     # 货币接口
│       ├── InternalCoin.java      # 内置币实现
│       ├── VaultCurrency.java     # Vault 实现
│       └── PlayerPointsCurrency.java # PlayerPoints 实现
├── src/main/resources/
│   ├── plugin.yml                 # 插件描述文件
│   ├── config.yml                 # 主配置文件
│   └── lang/
│       ├── zh_cn.yml              # 简体中文
│       ├── zh_tw.yml              # 繁体中文
│       └── en_US.yml              # 英文
└── pom.xml                        # Maven 构建配置
```

## 开发者信息

- **作者**: ya_xzer21145
- **Java 版本**: 8+
- **构建工具**: Maven

## 许可证

MIT License 3.0