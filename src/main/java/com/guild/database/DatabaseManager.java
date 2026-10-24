package com.guild.database;
import com.guild.GuildPlugin;
import com.guild.guild.Guild;
import com.guild.guild.GuildMember;
import com.guild.guild.GuildPermission;
import com.guild.guild.GuildRole;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class DatabaseManager
{
    private final GuildPlugin plugin;

    private HikariDataSource dataSource;

    private boolean mysql;

    public DatabaseManager(GuildPlugin plugin)
    {
        this.plugin = plugin;
    }

    public void initialize()
    {
        try
        {
            // 数据库类型从 config.yml 读取（修复旧版忽略 MySQL 配置、强制使用 SQLite 的问题）
            String type = plugin.getConfig().getString("database.type", "sqlite");
            this.mysql = "mysql".equalsIgnoreCase(type);
            HikariConfig config = new HikariConfig();
            if (mysql)
            {
                String host = plugin.getConfig().getString("database.host", "localhost");
                int port = plugin.getConfig().getInt("database.port", 3306);
                String database = plugin.getConfig().getString("database.database", "guild");
                String username = plugin.getConfig().getString("database.username", "root");
                String password = plugin.getConfig().getString("database.password", "");
                int poolSize = plugin.getConfig().getInt("database.pool-size", 10);
                long connectionTimeout = plugin.getConfig().getLong("database.connection-timeout", 30000L);
                config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=false&characterEncoding=utf8&useUnicode=true");
                config.setUsername(username);
                config.setPassword(password);
                config.setMaximumPoolSize(Math.max(1, poolSize));
                config.setMinimumIdle(1);
                config.setConnectionTimeout(connectionTimeout);
                config.setMaxLifetime(1800000);
            }
            else
            {
                File dataFolder = plugin.getDataFolder();
                if (!dataFolder.exists())
                {
                    dataFolder.mkdirs();
                }
                String dbPath = dataFolder.getAbsolutePath() + "/guilds.db";
                config.setJdbcUrl("jdbc:sqlite:" + dbPath);
                config.setMaximumPoolSize(3);
                config.setMinimumIdle(1);
                config.setIdleTimeout(30000);
                config.setMaxLifetime(180000);
                config.setConnectionTimeout(5000);
                config.addDataSourceProperty("journal_mode", "WAL");
                config.addDataSourceProperty("synchronous", "NORMAL");
                config.addDataSourceProperty("cache_size", "-2000");
            }
            config.setPoolName("GuildPool");
            this.dataSource = new HikariDataSource(config);
            createTables();
            loadGuilds();
            loadPlayerCurrencies();
            plugin.getLogger().info("Database initialized: " + (mysql ? "MySQL" : "SQLite"));
        }
        catch (SQLException e)
        {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize database", e);
        }
    }

    private Connection getConnection() throws SQLException
    {
        return dataSource.getConnection();
    }

    private void createTables() throws SQLException
    {
        // MySQL 不允许 TEXT 列作主键、不支持 INSERT OR REPLACE，建表语句按方言区分
        if (mysql)
        {
            try (Connection conn = getConnection();
            Statement stmt = conn.createStatement())
            {
                stmt.execute("CREATE TABLE IF NOT EXISTS guilds (" +
                    "name VARCHAR(64) PRIMARY KEY," +
                    "tag VARCHAR(64)," +
                    "tag_color VARCHAR(16)," +
                    "owner VARCHAR(36)," +
                    "level INT DEFAULT 0," +
                    "experience BIGINT DEFAULT 0," +
                    "daily_experience BIGINT DEFAULT 0," +
                    "motd VARCHAR(255) DEFAULT ''," +
                    "public_guild TINYINT DEFAULT 1," +
                    "created_time BIGINT) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                stmt.execute("CREATE TABLE IF NOT EXISTS guild_members (" +
                    "guild_name VARCHAR(64)," +
                    "uuid VARCHAR(36)," +
                    "role VARCHAR(16)," +
                    "joined_time BIGINT," +
                    "total_contribution BIGINT DEFAULT 0," +
                    "daily_contribution BIGINT DEFAULT 0," +
                    "muted TINYINT DEFAULT 0," +
                    "muted_until BIGINT DEFAULT 0," +
                    "nickname VARCHAR(64)," +
                    "PRIMARY KEY (guild_name, uuid)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                stmt.execute("CREATE TABLE IF NOT EXISTS guild_permissions (" +
                    "guild_name VARCHAR(64)," +
                    "permission VARCHAR(32)," +
                    "level INT," +
                    "PRIMARY KEY (guild_name, permission)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                stmt.execute("CREATE TABLE IF NOT EXISTS player_settings (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "guild_invites TINYINT DEFAULT 1," +
                    "join_notifications TINYINT DEFAULT 1) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                stmt.execute("CREATE TABLE IF NOT EXISTS guild_banks (" +
                    "guild_name VARCHAR(64) PRIMARY KEY," +
                    "balance BIGINT DEFAULT 0) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                stmt.execute("CREATE TABLE IF NOT EXISTS player_currency (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "balance BIGINT DEFAULT 0) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            }
            return;
        }
        try (Connection conn = getConnection();
        Statement stmt = conn.createStatement())
        {
            stmt.execute("CREATE TABLE IF NOT EXISTS guilds (" + "name TEXT PRIMARY KEY," + "tag TEXT," + "tag_color TEXT," + "owner TEXT," + "level INTEGER DEFAULT 0," + "experience INTEGER DEFAULT 0," + "daily_experience INTEGER DEFAULT 0," + "motd TEXT DEFAULT ''," + "public_guild INTEGER DEFAULT 1," + "created_time INTEGER)");
            stmt.execute("CREATE TABLE IF NOT EXISTS guild_members (" + "guild_name TEXT," + "uuid TEXT," + "role TEXT," + "joined_time INTEGER," + "total_contribution INTEGER DEFAULT 0," + "daily_contribution INTEGER DEFAULT 0," + "muted INTEGER DEFAULT 0," + "muted_until INTEGER DEFAULT 0," + "nickname TEXT," + "PRIMARY KEY (guild_name, uuid)," + "FOREIGN KEY (guild_name) REFERENCES guilds(name))");
            stmt.execute("CREATE TABLE IF NOT EXISTS guild_permissions (" + "guild_name TEXT," + "permission TEXT," + "level INTEGER," + "PRIMARY KEY (guild_name, permission)," + "FOREIGN KEY (guild_name) REFERENCES guilds(name))");
            stmt.execute("CREATE TABLE IF NOT EXISTS player_settings (" + "uuid TEXT PRIMARY KEY," + "guild_invites INTEGER DEFAULT 1," + "join_notifications INTEGER DEFAULT 1)");
            stmt.execute("CREATE TABLE IF NOT EXISTS guild_banks (" + "guild_name TEXT PRIMARY KEY," + "balance INTEGER DEFAULT 0)");
            stmt.execute("CREATE TABLE IF NOT EXISTS player_currency (" + "uuid TEXT PRIMARY KEY," + "balance INTEGER DEFAULT 0)");
        }
    }

    public void loadGuilds()
    {
        try (Connection conn = getConnection())
        {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM guilds");
            ResultSet rs = ps.executeQuery())
            {
                while (rs.next())
                {
                    String name = rs.getString("name");
                    String tag = rs.getString("tag");
                    String tagColor = rs.getString("tag_color");
                    UUID owner = UUID.fromString(rs.getString("owner"));
                    int level = rs.getInt("level");
                    long experience = rs.getLong("experience");
                    long dailyExperience = rs.getLong("daily_experience");
                    String motd = rs.getString("motd");
                    boolean publicGuild = rs.getBoolean("public_guild");
                    long createdTime = rs.getLong("created_time");
                    Guild guild = new Guild(name, owner, plugin);
                    guild.setTag(tag);
                    guild.setTagColor(tagColor);
                    guild.setLevel(level);
                    guild.setExperience(experience);
                    guild.setDailyExperience(dailyExperience);
                    guild.setMotd(motd);
                    guild.setPublicGuild(publicGuild);
                    guild.setCreatedTime(createdTime);
                    loadGuildMembers(conn, guild);
                    loadGuildPermissions(conn, guild);
                    loadGuildBank(conn, guild);
                    plugin.getGuildManager().addGuild(guild);
                }
            }
        }
        catch (SQLException e)
        {
            plugin.getLogger().log(Level.SEVERE, "Failed to load guilds", e);
        }
    }

    private void loadGuildMembers(Connection conn, Guild guild) throws SQLException
    {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM guild_members WHERE guild_name = ?"))
        {
            ps.setString(1, guild.getName());
            try (ResultSet rs = ps.executeQuery())
            {
                while (rs.next())
                {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    GuildRole role = GuildRole.valueOf(rs.getString("role"));
                    long totalContribution = rs.getLong("total_contribution");
                    long dailyContribution = rs.getLong("daily_contribution");
                    boolean muted = rs.getBoolean("muted");
                    long mutedUntil = rs.getLong("muted_until");
                    String nickname = rs.getString("nickname");
                    GuildMember member = new GuildMember(uuid, role);
                    member.setTotalContribution(totalContribution);
                    member.setDailyContribution(dailyContribution);
                    if (muted)
                    {
                        long remainingMute = mutedUntil - System.currentTimeMillis();
                        if (remainingMute > 0)
                        {
                            member.mute(remainingMute);
                        }
                    }
                    if (nickname != null && !nickname.isEmpty())
                    {
                        member.setNickname(nickname);
                    }
                    guild.getMembers().put(uuid, member);
                }
            }
        }
    }

    private void loadGuildPermissions(Connection conn, Guild guild) throws SQLException
    {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM guild_permissions WHERE guild_name = ?"))
        {
            ps.setString(1, guild.getName());
            try (ResultSet rs = ps.executeQuery())
            {
                while (rs.next())
                {
                    String permission = rs.getString("permission");
                    int level = rs.getInt("level");
                    GuildPermission perm;
                    switch (level)
                    {
                        case 1:
                            perm = GuildPermission.MEMBER;
                            break;
                        case 2:
                            perm = GuildPermission.OFFICER;
                            break;
                        default:
                            perm = GuildPermission.OWNER;
                            break;
                    }
                    guild.getPermissions().put(permission, perm);
                }
            }
        }
    }

    private void loadGuildBank(Connection conn, Guild guild) throws SQLException
    {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM guild_banks WHERE guild_name = ?"))
        {
            ps.setString(1, guild.getName());
            try (ResultSet rs = ps.executeQuery())
            {
                if (rs.next())
                {
                    long balance = rs.getLong("balance");
                    guild.getBank().setBalance(balance);
                }
            }
        }
    }

    // ---------- 玩家公会币持久化 ----------

    public void loadPlayerCurrencies()
    {
        Map<UUID, Long> balances = new HashMap<>();
        try (Connection conn = getConnection();
        PreparedStatement ps = conn.prepareStatement("SELECT uuid, balance FROM player_currency");
        ResultSet rs = ps.executeQuery())
        {
            while (rs.next())
            {
                try
                {
                    balances.put(UUID.fromString(rs.getString("uuid")), rs.getLong("balance"));
                }
                catch (IllegalArgumentException ignored)
                {
                }
            }
        }
        catch (SQLException e)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to load player currencies", e);
            return;
        }
        plugin.getGuildManager().loadPlayerCurrencyBalances(balances);
    }

    /**
     * 确保玩家拥有公会币账户行；不存在时以 initialBalance 建户。
     * 返回该玩家当前的公会币余额。
     */
    public long ensurePlayerCurrency(UUID playerUuid, long initialBalance)
    {
        String uuid = playerUuid.toString();
        try (Connection conn = getConnection())
        {
            try (PreparedStatement ps = conn.prepareStatement("SELECT balance FROM player_currency WHERE uuid = ?"))
            {
                ps.setString(1, uuid);
                try (ResultSet rs = ps.executeQuery())
                {
                    if (rs.next())
                    {
                        return rs.getLong("balance");
                    }
                }
            }
            long balance = Math.max(0L, initialBalance);
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO player_currency (uuid, balance) VALUES (?, ?)"))
            {
                ps.setString(1, uuid);
                ps.setLong(2, balance);
                ps.executeUpdate();
            }
            return balance;
        }
        catch (SQLException e)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to ensure player currency: " + uuid, e);
            return 0L;
        }
    }

    public void savePlayerCurrency(UUID playerUuid, long balance)
    {
        String sql = mysql
            ? "INSERT INTO player_currency (uuid, balance) VALUES (?, ?) ON DUPLICATE KEY UPDATE balance = ?"
            : "INSERT OR REPLACE INTO player_currency (uuid, balance) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
        PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, playerUuid.toString());
            ps.setLong(2, balance);
            ps.setLong(3, balance);
            ps.executeUpdate();
        }
        catch (SQLException e)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to save player currency: " + playerUuid, e);
        }
    }

    /** 公会重命名时迁移数据库中所有关联记录 */
    public void migrateGuildName(String oldName, String newName)
    {
        String[] tables = { "guild_members", "guild_permissions", "guild_banks" };
        try (Connection conn = getConnection())
        {
            conn.setAutoCommit(false);
            try
            {
                for (String table : tables)
                {
                    try (PreparedStatement ps = conn.prepareStatement("UPDATE " + table + " SET guild_name = ? WHERE guild_name = ?"))
                    {
                        ps.setString(1, newName);
                        ps.setString(2, oldName);
                        ps.executeUpdate();
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement("UPDATE guilds SET name = ? WHERE name = ?"))
                {
                    ps.setString(1, newName);
                    ps.setString(2, oldName);
                    ps.executeUpdate();
                }
                conn.commit();
            }
            catch (SQLException e)
            {
                conn.rollback();
                throw e;
            }
            finally
            {
                conn.setAutoCommit(true);
            }
        }
        catch (SQLException e)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to migrate guild name: " + oldName + " -> " + newName, e);
        }
    }

    /**
     * 生成方言感知的 upsert 语句：
     * SQLite 使用 INSERT OR REPLACE，MySQL 使用 ON DUPLICATE KEY UPDATE
     */
    private String upsertSql(String table, String... columns)
    {
        StringBuilder sb = new StringBuilder("INSERT ");
        if (mysql)
        {
            sb.append("INTO ");
        }
        else
        {
            sb.append("OR REPLACE INTO ");
        }
        sb.append(table).append(" (").append(String.join(", ", columns)).append(") VALUES (");
        for (int i = 0; i < columns.length; i++)
        {
            if (i > 0) sb.append(", ");
            sb.append("?");
        }
        sb.append(")");
        if (mysql)
        {
            sb.append(" ON DUPLICATE KEY UPDATE ");
            for (int i = 0; i < columns.length; i++)
            {
                if (i > 0) sb.append(", ");
                sb.append(columns[i]).append(" = VALUES(").append(columns[i]).append(")");
            }
        }
        return sb.toString();
    }

    public void saveGuild(Guild guild)
    {
        try (Connection conn = getConnection())
        {
            conn.setAutoCommit(false);
            try
            {
                try (PreparedStatement ps = conn.prepareStatement(upsertSql("guilds", "name", "tag", "tag_color", "owner", "level", "experience", "daily_experience", "motd", "public_guild", "created_time")))
                {
                    ps.setString(1, guild.getName());
                    ps.setString(2, guild.getTag());
                    ps.setString(3, guild.getTagColor());
                    ps.setString(4, guild.getOwner().toString());
                    ps.setInt(5, guild.getLevel());
                    ps.setLong(6, guild.getExperience());
                    ps.setLong(7, guild.getDailyExperience());
                    ps.setString(8, guild.getMotd());
                    ps.setBoolean(9, guild.isPublicGuild());
                    ps.setLong(10, guild.getCreatedTime());
                    ps.executeUpdate();
                }
                // 3.0.8：同一事务内先清空该公会的成员行再批量插入，
                // 修复旧版只 upsert 不删除导致被踢/退会成员重启后“复活”的 BUG
                try (PreparedStatement delPs = conn.prepareStatement("DELETE FROM guild_members WHERE guild_name = ?"))
                {
                    delPs.setString(1, guild.getName());
                    delPs.executeUpdate();
                }
                try (PreparedStatement insertPs = conn.prepareStatement(upsertSql("guild_members", "guild_name", "uuid", "role", "joined_time", "total_contribution", "daily_contribution", "muted", "muted_until", "nickname")))
                {
                    for (GuildMember member : guild.getMembers().values())
                    {
                        insertPs.setString(1, guild.getName());
                        insertPs.setString(2, member.getUuid().toString());
                        insertPs.setString(3, member.getRole().name());
                        insertPs.setLong(4, member.getJoinedTime());
                        insertPs.setLong(5, member.getTotalContribution());
                        insertPs.setLong(6, member.getDailyContribution());
                        // 修复旧版禁言时间丢失：保存真实到期时间戳
                        boolean muted = member.getMutedUntil() > System.currentTimeMillis();
                        insertPs.setBoolean(7, muted);
                        insertPs.setLong(8, member.getMutedUntil());
                        insertPs.setString(9, member.getNickname());
                        insertPs.addBatch();
                    }
                    insertPs.executeBatch();
                }
                // 修复旧版权限表从不保存的问题
                try (PreparedStatement permPs = conn.prepareStatement(upsertSql("guild_permissions", "guild_name", "permission", "level")))
                {
                    for (Map.Entry<String, GuildPermission> entry : guild.getPermissions().entrySet())
                    {
                        permPs.setString(1, guild.getName());
                        permPs.setString(2, entry.getKey());
                        permPs.setInt(3, entry.getValue().getLevel());
                        permPs.addBatch();
                    }
                    permPs.executeBatch();
                }
                try (PreparedStatement bankPs = conn.prepareStatement(upsertSql("guild_banks", "guild_name", "balance")))
                {
                    bankPs.setString(1, guild.getName());
                    bankPs.setLong(2, guild.getBank().getBalance());
                    bankPs.executeUpdate();
                }
                conn.commit();
            }
            catch (SQLException e)
            {
                conn.rollback();
                throw e;
            }
            finally
            {
                conn.setAutoCommit(true);
            }
        }
        catch (SQLException e)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to save guild: " + guild.getName(), e);
        }
    }

    public void deleteGuild(String guildName)
    {
        try (Connection conn = getConnection())
        {
            conn.setAutoCommit(false);
            try
            {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM guild_members WHERE guild_name = ?"))
                {
                    ps.setString(1, guildName);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM guild_permissions WHERE guild_name = ?"))
                {
                    ps.setString(1, guildName);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM guild_banks WHERE guild_name = ?"))
                {
                    ps.setString(1, guildName);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM guilds WHERE name = ?"))
                {
                    ps.setString(1, guildName);
                    ps.executeUpdate();
                }
                conn.commit();
            }
            catch (SQLException e)
            {
                conn.rollback();
                throw e;
            }
            finally
            {
                conn.setAutoCommit(true);
            }
        }
        catch (SQLException e)
        {
            plugin.getLogger().log(Level.WARNING, "Failed to delete guild: " + guildName, e);
        }
    }

    public void close()
    {
        if (dataSource != null && !dataSource.isClosed())
        {
            dataSource.close();
        }
    }

    public void reload()
    {
        close();
        initialize();
    }
}
