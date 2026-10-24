# Guild 3.0.8 更新日志

> 配置文件版本：1.3 ｜ 插件版本：3.0.8 ｜ 构建产物：Guild-3.0.8.jar

---

## 【修复】

- **Paper 26.2 启动崩溃（ShopConfig 空指针）** ： ShopConfig 字段初始化器在父类 SimpleFileConfig 构造函数调用可覆写的 onLoad() 时尚未执行，导致 items 为 null，插件在 26.2 服务端 onEnable 直接 NPE 崩溃。现将 items 的创建移入 onLoad 内部，消除模板方法初始化顺序陷阱
- **公会事件双发与漏发** ： GuildAPI 与 GuildManager 两层各自触发事件，第三方通过 API 操作时事件被触发两次；而命令/GUI 路径则完全不触发（创建/加入/离开/踢出/解散/升级六类事件均存在遗漏）。现将事件触发统一下沉至 GuildManager，所有路径恰好触发一次，API 层不再重复触发
- **批准入会申请必然崩溃** ： acceptRequest 在 CopyOnWriteArrayList 的迭代器上调用 iter.remove()，必抛 UnsupportedOperationException，玩家批准申请即报错且申请无法移除。现改为列表直接 remove 元素，空列表同步移除键
- **GUI 重命名实际修改公告** ： 管理 GUI「重命名」按钮注册的挂起动作是 guild_motd，玩家输入新名称后修改的是公告而非公会名。现新增独立的 guild_rename 动作与完整校验处理
- **铁砧创建公会双重扣费** ： 3.0.7 仅修复了命令路径的预扣费，GUI 铁砧创建路径仍然先预扣创建费、createGuild 内部再扣一次（失败时还退款）。现删除铁砧路径预扣/退款逻辑，统一由 createGuild 单次扣费
- **被踢/退会成员重启后复活** ： saveGuild 对成员表只 upsert 不删除，离会成员的数据库行永久残留，重启后被踢玩家重新出现在成员列表。现同一事务内先清空该公会成员行再批量插入
- **公会频道命令空指针** ： /guildchat 直接调用 getMember(...).isMuted()，成员记录缺失时 NPE；且未检查聊天权限、使用硬编码格式与 /guild chat 不一致。现补齐判空与权限校验，格式统一读取 guild.yml chat.format
- **升级循环防护缺失** ： 经验公式配置为 0（或 /guild clxplev 清零后 level=0）时 checkLevelUp 的 required 恒为 0，轻则一次加经验瞬间刷满级，重则循环空转。现 required ≤ 0 时直接跳出升级循环
- **GUI 升级等级上限硬编码** ： 升级按钮仍写死 100 级上限，max-level 配置大于 100 时无法继续升级。现改用 guild.getMaxLevel()
- **异步线程触发同步事件** ： 聊天挂起输入运行于 AsyncPlayerChatEvent 异步线程，其中创建/离开/解散流程会在异步线程 callEvent 并直接操作 Bukkit API，存在线程安全异常风险。现统一切回主线程执行管理器操作
- **玩家下线状态残留吞消息** ： 挂起聊天输入、铁砧待确认状态、成员设置目标三项静态映射从不清理，玩家下线前挂起的操作会吞掉其下次上线的第一条聊天消息且映射长期泄漏。现于 PlayerQuit 统一清理
- **踢出/升降级防护缺失** ： kickMember 无操作者判空（API 传 null 直接 NPE），可踢出自己、踢出会长、同级互踢；promote/demote 已到顶/底时仍返回成功导致调用方误报。现补齐全部角色保护校验，职位无变化时返回失败
- **重复入会与幽灵成员** ： joinGuild/批准申请/接受邀请均不检查玩家是否已在其他公会，可制造同时在多个公会的幽灵成员记录。现统一增加 playerGuilds 占用校验
- **改名/解散遗留脏数据** ： 重命名后旧名称下的待处理入会申请直接丢失；解散公会后指向它的待处理邀请残留内存。现改名同步迁移申请、解散同步清理相关邀请
- **GUI 操作重启丢失** ： GUI 中的晋升/降级/踢出/昵称修改仅改内存不落库，重启后全部还原。现操作后立即进入异步保存队列
- **API 解散空指针** ： disbandGuildAsync(name) 内部以 null 作为解散者调用 disbandGuild，直接 disbander.getUniqueId() NPE。现 disbander 为 null 时自动回退为公会会长
- **禁言缺少保护与校验** ： /guild mute 管理员可禁言会长、可同级互禁，且允许传入 0 或负数分钟。现禁止禁言会长及同级/更高级成员，分钟数必须大于 0
- **命令改名校验不全** ： /guild rename 注释声称"与创建时一致"但缺少正则与黑名单校验，可改名成非法字符或违禁名称。现补齐 name-regex / banned-names 校验

## 【优化】

- **主线程数据库 I/O 全面移除** ： 命令层 10 处（标签/标签颜色/公告/设置/权限/gexp/slevel/clxplev/mute/unmute）同步 saveGuild 与解散公会的数据库删除全部改为异步：GUI/命令写操作走 scheduleSavePublic 异步保存队列，deleteGuild 改 runTaskAsynchronously（关闭时刻自动回退同步），大公会操作不再卡顿 TPS
- **玩家名查询缓存化** ： 成员列表、排行榜、GUI 头部点击、升降级/踢出/昵称提示等高频路径的 Bukkit.getOfflinePlayer(uuid).getName() 主线程磁盘查询统一改走 GuildManager.getPlayerNameCached 缓存
- **银行历史记录限容** ： 存取款历史 Map 每人无上限增长，长期运行占用内存。现每人仅保留最近 50 条记录

## 【新增】

- **重命名聊天输入处理** ： ChatInputListener 新增 guild_rename 挂起动作，支持取消、长度/正则/黑名单/重名校验，校验通过后走 renameGuild 原地迁移
- **事件层 API 完善** ： GuildCreateEvent 监听器修改的名称与标签现对所有创建路径（命令/GUI/API）生效；GuildAPI.createGuild(name, tag, owner) 的标签参数在事件未被监听器改动时正常应用

## 【其他】

- **代码格式化** ： 修复均保持原有反编译风格文件的局部最小改动，未涉及整体重排
- **编译产物修复** ： 构建通过，输出 target/Guild-3.0.8.jar
- **版本号更新** ： 插件版本更新至 3.0.8（pom.xml / plugin.yml）；配置文件版本保持 1.3 不变，无需迁移任何配置文件
