# Guild 3.0.7 更新日志

> 配置文件版本：1.3 ｜ 插件版本：3.0.7 ｜ 构建产物：Guild-3.0.7.jar

---

## 【修复】

- **创建公会双重扣费** ： handleCreate 先按 Vault→PlayerPoints 回退链预扣创建费， GuildManager.createGuild 又按 currency.yml 配置的货币类型再扣一次，玩家实际被扣两次费用。现统一由 createGuild 按配置货币类型单次扣费并提示所需金额
- **创建费用货币类型错误** ： 旧版 handleCreate 的扣费/退款完全忽略 currency.yml 的 type 配置（固定走 Vault→点数回退链）。现删除命令层扣费逻辑，全部走配置化货币类型
- **重命名公会丢失全部数据** ： handleRename 通过「解散公会 + 重新创建」实现改名，导致所有成员、等级、经验、银行余额、权限全部丢失，且会再次扣取创建费。现新增 GuildManager.renameGuild 原地迁移内存索引，并配套 DatabaseManager.migrateGuildName 在数据库端迁移 guild_members/guild_permissions/guild_banks/guilds 关联记录（事务执行）
- **强制删除公会永远无法执行** ： handleDelete 的确认逻辑反转—— pendingDeleteConfirm.remove() 返回 true（即玩家确实先发起了删除）时反而提示"请重新发起删除"。现改为记录待删除公会名，确认时执行删除
- **MySQL 配置完全被忽略** ： config.yml 中 database.type/host/port/pool-size 等配置项形同虚设， DatabaseManager.initialize 强制使用 SQLite。现完整实现 MySQL 支持：按 config.yml 建立连接池，MySQL 方言建表（VARCHAR 主键、InnoDB、utf8mb4），upsert 自动切换 INSERT OR REPLACE / ON DUPLICATE KEY UPDATE，pool-size 与 connection-timeout 均已生效
- **公会币余额重启即丢失** ： 玩家公会币（GUILD_COIN）仅存于内存 Map，服务器重启后全部清零。现新增 player_currency 数据库表：启动时全量加载、余额变动异步落库、玩家首次进服自动建户
- **公会权限表从不保存** ： saveGuild 只保存公会/成员/银行三类表，GUI 中修改的权限等级重启后全部还原为默认值。现 saveGuild 事务内同步 upsert guild_permissions 表
- **禁言到期时间丢失** ： saveGuild 保存 muted_until 时写入"当前时间 + 24小时"而非成员真实到期时间，重启后禁言时长错乱。现新增 GuildMember.getMutedUntil()，保存真实时间戳，加载时按剩余时长恢复
- **settings public 子命令失效** ： switch 分支 case " public" 带前导空格， /guild settings public true 永远提示"未知属性"。现修正为 case "public"
- **多处命令参数解析未捕获异常** ： /guild top、log、requests、mute、gexp、slevel 等命令直接 Integer.parseInt / Long.parseLong，传入非数字直接抛 NumberFormatException。现统一捕获并提示"无效的数字"，slevel 增加等级范围校验（0 ~ max-level）
- **公会/管理频道空指针** ： handleChat 与 handleOfficerChat 在成员记录缺失时直接调用 member.isMuted() / getRole() 触发 NPE。现增加判空保护
- **语言文件 en_US 永不释放** ： saveLanguageFiles 中路径字符串 " en_US" 带前导空格，导致英文语言文件从未被释放到插件目录。现修正为 "en_US"
- **入会请求并发安全与重复提交** ： requests 使用 ArrayList 挂载在 ConcurrentHashMap 上仍非线程安全；同一玩家可无限重复提交申请。现改用 CopyOnWriteArrayList 并拒绝重复申请
- **幽灵申请长期堆积** ： 入会请求从不过期清理。现随邀请清理任务每分钟扫描，超时（10分钟）自动移除，空列表同步移除
- **createGuild 缺少"已在公会"校验** ： 旧版仅命令层拦截，直接调用 API 可让已在公会的玩家重复创建。现管理器层补齐 playerGuilds 校验

## 【优化】

- **银行系统与玩家钱包打通** ： 旧版 depositToBank / withdrawFromBank 只是银行数字增减，玩家钱包分文未动。现按 currency.yml 配置的货币类型：存款先从玩家钱包扣款、取款后向玩家钱包入账，失败自动退还，杜绝"凭空造币"
- **银行限额与税收全面生效** ： 接入 bank.yml 的 max-deposit / max-withdraw / max-balance / min-balance / deposit-tax / withdraw-tax / daily-deposit-limit / daily-withdraw-limit，税收按比例折算实际入账/到账金额，每日限额跨天自动重置
- **经验系统接入配置** ： addExperience 现应用 experience.yml 的 weekend-multiplier（周末加成）、holiday-multiplier（节假日加成）、daily-exp-limit（每日经验上限）、daily-contribution-limit（每日贡献上限），上限计为增量截断而非整单拒绝
- **等级/成员上限配置化** ： Guild.getMaxMembers 改为 base-members + level × members-per-level（guild.yml）；等级上限 100 硬编码改为 max-level 配置（升级、checkLevelUp、setlevel 校验三处同步）
- **经验公式配置化** ： Guild.getRequiredExperience 改用 experience.yml level-formula（base / growth-rate / max-required），保留旧公式作为未配置时的回退默认值
- **名称校验配置化** ： /guild create 增加正则与黑名单校验（guild.yml name-regex / banned-names），ChatInputListener 的硬编码正则同步改为读取配置，创建与聊天输入规则一致
- **升级事件补发** ： 自然经验升级从未触发 GuildLevelUpEvent（仅付费升级路径触发）。现 Guild.checkLevelUp 升级时触发事件，付费升级路径不重复触发
- **每日重置任务落地** ： clearAllDailyData 一直无人调用。现 GuildPlugin 启动每分钟检测日期变更，跨天自动重置日常经验/贡献与银行每日限额（受 features.yml daily-reset 开关控制）
- **initial-balance 生效** ： currency.yml 的初始余额从未发放。现玩家首次进服异步建户并发放初始余额（通过数据库行存在性保证终生一次，不会刷初始资金）
- **公会币上限生效** ： depositPlayerGuildCurrency 接入 currency.yml max-balance（0 = 无限制），超出部分截断至上限
- **银行功能开关** ： /guild deposit、withdraw 命令接入 features.yml 银行开关，关闭时直接拒绝并提示

## 【新增】

- **聊天格式配置** ： guild.yml 新增 chat.format / chat.officer-format，公会频道与管理频道格式完全可自定义（占位符 {tag_color} {tag} {player} {message}），替代旧版硬编码格式
- **公会币持久化 API** ： DatabaseManager 新增 loadPlayerCurrencies / ensurePlayerCurrency / savePlayerCurrency 方法；GuildManager 新增 loadPlayerCurrencyBalances / ensurePlayerCurrencyRow / setPlayerGuildCurrency 持久化方法
- **公会重命名 API** ： GuildManager 新增 renameGuild(oldName, newName, operator) 方法，供命令与第三方插件安全调用

## 【其他】

- **代码格式化** ： GuildPlugin、PlayerListener、DatabaseManager 整体重写为规范缩进，消除反编译遗留的级联缩进与 `}}` 链式结构
- **编译产物修复** ： 构建通过（Release 配置），输出 target/Guild-3.0.7.jar
- **配置文件注释更新** ： guild.yml 新增聊天格式段落说明；数据库配置项（config.yml database 段）现已真实生效
- **版本号更新** ： 插件版本更新至 3.0.7；配置文件版本更新至 1.3（config.yml config-version 与插件内检测常量同步，旧配置首次启动将提示配置版本较旧，新配置项自动使用默认值）
