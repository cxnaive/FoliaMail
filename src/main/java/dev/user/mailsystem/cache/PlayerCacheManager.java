package dev.user.mailsystem.cache;

import dev.user.mailsystem.MailSystemPlugin;
import dev.user.mailsystem.database.DatabaseQueue;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 玩家缓存管理器 - 存储玩家名与UUID的映射关系
 */
public class PlayerCacheManager {

    private final MailSystemPlugin plugin;
    private final DatabaseQueue databaseQueue;

    // 本地缓存
    private final Map<String, UUID> nameToUuidCache;
    private final Map<UUID, String> uuidToNameCache;
    private final Map<UUID, Long> uuidToLastSeenCache;

    public PlayerCacheManager(MailSystemPlugin plugin) {
        this.plugin = plugin;
        this.databaseQueue = plugin.getDatabaseQueue();
        this.nameToUuidCache = new HashMap<>();
        this.uuidToNameCache = new HashMap<>();
        this.uuidToLastSeenCache = new HashMap<>();
    }

    /**
     * 更新或插入玩家缓存
     */
    public void updatePlayerCache(UUID uuid, String playerName) {
        updatePlayerCache(uuid, playerName, System.currentTimeMillis());
    }

    /**
     * 更新或插入玩家缓存（指定时间）
     */
    public void updatePlayerCache(UUID uuid, String playerName, long lastSeen) {
        // 更新本地缓存
        nameToUuidCache.put(playerName.toLowerCase(), uuid);
        uuidToNameCache.put(uuid, playerName);
        uuidToLastSeenCache.put(uuid, lastSeen);

        // 异步更新数据库
        final long finalLastSeen = lastSeen;
        databaseQueue.submitAsync("updatePlayerCache", conn -> {
            String sql = getUpsertSql(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, playerName);
                ps.setLong(3, finalLastSeen);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /**
     * 根据玩家名获取UUID（先查本地缓存，再查数据库）
     */
    public void getPlayerUuid(String playerName, Consumer<UUID> callback) {
        // 先检查本地缓存
        UUID cached = nameToUuidCache.get(playerName.toLowerCase());
        if (cached != null) {
            callback.accept(cached);
            return;
        }

        // 异步查询数据库
        databaseQueue.submit("getPlayerUuid", conn -> {
            String sql = "SELECT uuid FROM player_cache WHERE player_name = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return UUID.fromString(rs.getString("uuid"));
                    }
                }
            }
            return null;
        }, result -> {
            if (result != null) {
                // 更新本地缓存
                nameToUuidCache.put(playerName.toLowerCase(), result);
            }
            callback.accept(result);
        });
    }

    /**
     * 根据UUID获取玩家名（先查本地缓存，再查数据库）
     */
    public void getPlayerName(UUID uuid, Consumer<String> callback) {
        // 先检查本地缓存
        String cached = uuidToNameCache.get(uuid);
        if (cached != null) {
            callback.accept(cached);
            return;
        }

        // 异步查询数据库
        databaseQueue.submit("getPlayerName", conn -> {
            String sql = "SELECT player_name FROM player_cache WHERE uuid = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("player_name");
                    }
                }
            }
            return null;
        }, result -> {
            if (result != null) {
                // 更新本地缓存
                uuidToNameCache.put(uuid, result);
            }
            callback.accept(result);
        });
    }

    /**
     * 从数据库加载所有缓存到内存
     */
    public void loadAllCache(Runnable callback) {
        databaseQueue.submit("loadAllCache", conn -> {
            Map<String, UUID> loadedNames = new HashMap<>();
            Map<UUID, Long> loadedTimes = new HashMap<>();
            String sql = "SELECT uuid, player_name, last_seen FROM player_cache";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    String name = rs.getString("player_name");
                    long lastSeen = rs.getLong("last_seen");
                    loadedNames.put(name.toLowerCase(), uuid);
                    loadedTimes.put(uuid, lastSeen);
                }
            }
            return new CacheData(loadedNames, loadedTimes);
        }, result -> {
            nameToUuidCache.putAll(result.names);
            result.names.forEach((name, uuid) -> uuidToNameCache.put(uuid, name));
            uuidToLastSeenCache.putAll(result.lastSeenTimes);
            plugin.getLogger().info("已加载 " + result.names.size() + " 个玩家缓存");
            if (callback != null) {
                callback.run();
            }
        });
    }

    private record CacheData(Map<String, UUID> names, Map<UUID, Long> lastSeenTimes) {}

    /**
     * 根据数据库类型获取UPSERT SQL语句
     */
    private String getUpsertSql(Connection conn) throws SQLException {
        String dbName = conn.getMetaData().getDatabaseProductName().toLowerCase();
        if (dbName.contains("mysql") || dbName.contains("mariadb")) {
            // MySQL/MariaDB 语法 - 以 player_name 为唯一键，同名则更新 UUID
            return "INSERT INTO player_cache (uuid, player_name, last_seen) VALUES (?, ?, ?) " +
                   "ON DUPLICATE KEY UPDATE uuid = VALUES(uuid), last_seen = VALUES(last_seen)";
        } else {
            // H2 语法（以及其他支持MERGE的数据库）- 以 player_name 为唯一键
            return "MERGE INTO player_cache (uuid, player_name, last_seen) KEY(player_name) VALUES (?, ?, ?)";
        }
    }

    /**
     * 同步获取UUID（仅从本地缓存）
     */
    public UUID getUuidFromCache(String playerName) {
        return nameToUuidCache.get(playerName.toLowerCase());
    }

    /**
     * 同步获取玩家名（仅从本地缓存）
     */
    public String getNameFromCache(UUID uuid) {
        return uuidToNameCache.get(uuid);
    }

    /**
     * 同步获取最后登录时间（仅从本地缓存）
     */
    public Long getLastSeenFromCache(UUID uuid) {
        return uuidToLastSeenCache.get(uuid);
    }

    /**
     * 异步获取玩家最后登录时间
     */
    public void getPlayerLastSeen(UUID uuid, Consumer<Long> callback) {
        // 先检查本地缓存
        Long cached = uuidToLastSeenCache.get(uuid);
        if (cached != null) {
            callback.accept(cached);
            return;
        }

        // 异步查询数据库
        databaseQueue.submit("getPlayerLastSeen", conn -> {
            String sql = "SELECT last_seen FROM player_cache WHERE uuid = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong("last_seen");
                    }
                }
            }
            return null;
        }, result -> {
            if (result != null) {
                // 更新本地缓存
                uuidToLastSeenCache.put(uuid, result);
            }
            callback.accept(result);
        });
    }

    /**
     * 获取指定时间内登录过的所有玩家
     * @param sinceTime 时间戳（毫秒），获取在此时间之后登录的玩家
     * @param callback 回调函数，返回 UUID 到玩家名的映射
     */
    public void getPlayersLoggedInSince(long sinceTime, Consumer<Map<UUID, String>> callback) {
        databaseQueue.submit("getPlayersLoggedInSince", conn -> {
            Map<UUID, String> players = new HashMap<>();
            String sql = "SELECT uuid, player_name FROM player_cache WHERE last_seen >= ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, sinceTime);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        String name = rs.getString("player_name");
                        players.put(uuid, name);
                    }
                }
            }
            return players;
        }, callback);
    }

    /**
     * 获取所有缓存过的玩家
     * @param callback 回调函数，返回 UUID 到玩家名的映射
     */
    public void getAllCachedPlayers(Consumer<Map<UUID, String>> callback) {
        databaseQueue.submit("getAllCachedPlayers", conn -> {
            Map<UUID, String> players = new HashMap<>();
            String sql = "SELECT uuid, player_name FROM player_cache";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    String name = rs.getString("player_name");
                    players.put(uuid, name);
                }
            }
            return players;
        }, callback);
    }
}
