# Guild 3.0.9 更新日志

> 配置文件版本：1.3 ｜ 插件版本：3.0.9 ｜ 构建产物：Guild-3.0.9.jar

---

## 【修复】

- **experience.yml 经验事件几乎全部失效** ： 13 种经验事件此前仅"击杀玩家"一项接入业务逻辑，挖掘/放置/钓鱼/繁殖/交易/附魔/合成/酿造/熔炼/驯服/成就/击杀怪物共 12 项写满配置也毫无作用。新增 ExperienceListener 将 13 种事件全部接入 GuildManager.addExperience，并统一施加功能开关、公会在册判定、黑名单与限流
- **击杀玩家经验绕过开关** ： 旧发放路径写在 PlayerListener 内，既不检查 features.yml 的 experience 总开关，也没有任何限流，且会与新监听器对同一次击杀重复计入。现整体迁移至 ExperienceListener.onEntityDeath 统一发放（自杀不再计分）
- **guild.yml 八项冷却完全不生效** ： disband/invite/join/promote/demote/rename/tag-change/kick-cooldown 全部只有 getter、业务代码零引用，玩家可无限解散重开刷奖励、被拒后瞬间反复邀请、随意改标签。现全部落地生效，并在失败时向操作者提示剩余秒数
- **职位与自动管理配置不生效** ： max-officers（管理员上限）、allow-self-demote（禁止自我降级）、auto-kick-inactive-days（不活跃自动踢出）长期为预留项。现已接入，自动踢人以异步任务运行且默认关闭（0）
- **bank.yml 配置不生效** ： max-history-records 被 GuildBank 硬编码 50 覆盖、log-large-transactions / large-transaction-threshold 完全无人读取。现历史条数按配置并可 /guild reload 即时生效，大额存取款同步写入后台审计日志
- **bank.yml 存取款职位限制不生效** ： deposit-permission / withdraw-permission 只有 getter，实际仅按公会权限表的 withdraw 判定，存款完全不看职位。现按职位等级双重校验（默认 MEMBER / OFFICER，与旧行为一致，配置值非法时视为不限制），职位不足时直接提示所需职位
- **GUI 职位操作绕过管理器** ： 成员设置界面的晋升/降级/踢出直接调用 setMemberRole / removeMember，跳过权限校验、角色保护、事件触发与数据落库。现统一改走 GuildManager 对应方法，命令与 GUI 行为一致
- **标签修改无长度与冷却校验** ： 命令与 GUI 两条路径都直接 guild.setTag()，不校验 max-tag-length、不受 tag-change-cooldown 限制。现收敛为 GuildManager.changeTag 单一入口（返回码区分无权限/冷却中）
- **重启/关闭丢失未落库数据** ： onDisable 仅关闭数据库连接，异步保存队列中最多 10 秒内的等级/经验/银行/成员变更直接丢弃。现 onDisable 先调用 GuildManager.shutdown() 兜底入队并排空全部保存与删除任务
- **解散公会在数据库复活** ： 解散用 runTaskAsynchronously 删库，与在途的 saveGuild 无序竞争，删除完成后旧队列又把该公会整库写回。现删除任务与保存共用同一异步线程严格串行（本批保存完成后再删），并从去重集合与队列中移除该公会
- **保存队列重复入队** ： ConcurrentLinkedQueue 无去重，高频事件下同一公会会被重复排入并整库写入多次。现增加按对象身份的去重集合，队列内同一公会只保留一份
- **并发写回已解散公会** ： scheduleSave 不校验公会是否仍在册，自动踢人等异步任务可能把刚解散的公会重新入队。现仅保存注册表中仍存在的公会实例
- **公会币发币上限竞态** ： depositPlayerGuildCurrency 采用 merge 取值后再 put 截断，多线程同时发币会少加/越过上限。现改为 Map.compute 原子更新
- **初始化顺序隐患** ： GuildManager 构造早于 guild.yml/bank.yml 等配置创建（其内部会读取配置），且数据库在配置就绪前就加载公会，Guild 构造只能回退默认值，并重现 3.0.7 的"子类字段未初始化即被访问"崩溃陷阱。现调整为先建全部配置，再建管理器与加载数据库
- **plugin.version 显示旧版本** ： 语言包中硬编码 v3.0.4，/guild 与公告栏版本号永远不变。现以 plugin.yml 实际版本覆盖
- **升级后文案缺键** ： 已部署服务器的 lang/*.yml 是旧版释放的文件，不含本次新增键，玩家会在聊天栏看到 guild.cooldown-join 这类原始键名。现启动时用 JAR 内置语言包仅补齐缺失键，玩家自定义值一律保留
- **酿造经验可刷取** ： 从酿造台取回材料同样触发发奖，反复存取即可刷经验。现仅在"放入"类动作（PLACE_*/HOTBAR_*/SWAP_WITH_CURSOR）且光标为准酿造材料时发奖
- **升级播报线程安全** ： 公会升级标题与聊天在事件监听中直接发送，API 从异步线程触发升级事件时会跨线程操作玩家。现非主线程自动调度回主线程
- **失败提示重复且误导** ： 邀请处于冷却时管理器已提示剩余秒数，命令仍追加"该玩家已有待处理的邀请"；升降级被冷却/职位上限拦截后命令仍追加"目标可能已是最高/最低级别"。现细化分支，只在管理器未给出具体原因时补充提示

## 【优化】

- **高频事件限流** ： 13 种经验事件各自设置最小发奖间隔（挖块/放置 1s、杀怪 250ms、交易 10s、酿造 5s 等），配合保存队列去重，避免满服玩家挖矿砍树时打爆异步写库
- **配置读取缓存化** ： no-exp-blocks / no-exp-mobs 的 getStringList 每次调用都会新建列表，现转为 Set 缓存并 30 秒 TTL 刷新，黑名单匹配为 O(1)
- **内存占用治理** ： 限流时间戳表按 10 分钟不活跃自动清理，邀请/申请/冷却表定期移除过期项，长期运行不再单调增长
- **非公会玩家零开销** ： 发奖入口先判功能开关与是否在公会，未入会玩家在配置读取与限流之前直接返回
- **跨版本兼容强化** ： 窗口类型、实体类型、持有者与点击动作一律按名称字符串判定（InventoryType 的 MERCHANT/BREWING/BLAST_FURNACE 等枚举常量与 Mob#getKiller 在不同版本间差异极大，直接引用会抛 NoSuchFieldError/NoSuchMethodError），反射取不到时静默降级不发奖；仅 1.13+ 存在的繁殖与成就事件放入内部类，用 Class.forName 探测成功后才注册，避免旧服务端事件反射解析参数类型失败
- **移除对已删除 API 的依赖** ： CraftingEvent 在 paper-api 1.21.4 已被移除，合成经验改由 InventoryClickEvent 检测工作台/玩家合成产出格
- **玩家名查询线程安全** ： 文案表 messages 由 HashMap 改为 ConcurrentHashMap，异步任务读取时不会与 /guild reload 的并发写冲突

## 【新增】

- **公会升级播报** ： 等级提升时向全体在线成员发送聊天提示并弹出标题（1.9+ 兼容降级），不再只有手动 /guild info 才能发现
- **ExperienceListener** ： 公会经验获取的唯一直通入口，含玩家击杀/击杀怪物/挖掘/放置/钓鱼/附魔/驯服/熔炼/合成/交易/酿造/繁殖/成就共 13 类
- **管理器查询接口** ： GuildManager 新增 changeTag、shutdown、deleteGuildData，以及 getJoinCooldownRemaining / getInviteCooldownRemaining / getRenameCooldownRemaining / getTagCooldownRemaining / getDisbandCooldownRemaining 供命令、GUI 与第三方插件展示冷却
- **语言包新增 11 个键** ： guild.cooldown-join / cooldown-invite / cooldown-rename / cooldown-tag / cooldown-disband / cooldown-promote / cooldown-demote / cooldown-kick / officer-limit / self-demote 与 bank.permission-required，简中/繁中/英文三份同步
- **银行失败提示文案修正** ： bank.deposit-failed / bank.withdraw-failed 由“余额不足”改为“余额不足或超出限额”（仅影响新生成的语言包，已有文件保留玩家自定义值）
- **银行审计日志** ： 达到 large-transaction-threshold 的存取款以 `[GuildBank] DEPOSIT/WITHDRAW 玩家 guild=.. amount=.. actual=..` 格式写入控制台，便于追查错账

## 【其他】

- **升级须知（行为变化）** ： 本版把此前"写了没用"的配置真正接通，因此默认配置会立即改变服务器行为——guild.yml 的 join-cooldown: 600、invite-cooldown: 300、disband-cooldown: 86400、promote/demote-cooldown: 60、kick-cooldown: 30、rename/tag-change-cooldown: 3600、max-officers: 3 将开始生效；experience.yml 各事件默认非 0，挖掘/放置/杀怪等会开始累积公会经验。如不需要限制，请将对应项改为 0（0 一律表示关闭），并可用 daily-exp-limit / no-exp-blocks 控制节奏
- **仍为预留未接入的配置** ： bank.yml 的 interest.*（存款利息）、experience.yml 的 weekly-reward.* 与 cumulative-contribution、guild.yml 的 auto-dormant-days、features.yml 的 combat-tag 与 cooldown-bar 本版仍未实现，后续版本再行接入
- **代码格式化** ： ExperienceListener 为新增文件，其余改动均在既有文件中做局部最小修改，未整体重排
- **编译产物** ： mvn package 构建通过，输出 target/Guild-3.0.9.jar
- **版本号更新** ： 插件版本更新至 3.0.9（pom.xml / plugin.yml）；配置文件版本保持 1.3 不变，无需迁移任何配置文件
