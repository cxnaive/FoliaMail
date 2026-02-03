# MailSystem - 多服务器同步邮件系统

支持 Folia 的多服务器同步邮件系统插件，支持邮件附件，附件物品数据同步使用 NBT API + GZIP 压缩存储。

## 特性

- ✅ **Folia 支持** - 完全兼容 Folia 多线程服务器
- ✅ **多服务器同步** - 支持 MySQL 和 H2 数据库，实现多服务器邮件同步
- ✅ **邮件附件** - 支持发送物品作为附件，完整保留物品NBT数据
- ✅ **NBT API 集成** - 使用 NBT API 序列化物品，GZIP 压缩存储减少空间占用
- ✅ **过期清理** - 自动清理过期邮件
- ✅ **GUI 界面** - 完整的图形界面操作
- ✅ **邮箱上限** - 可配置玩家邮箱最大邮件数量
- ✅ **管理员功能** - 可管理其他玩家的邮件（查看/删除/修改状态）
- ✅ **群发功能** - 支持向在线玩家/近期登录玩家/所有玩家群发邮件
- ✅ **多语言支持** - 支持中文界面

## 命令

### 基础命令

| 命令 | 描述 | 权限 |
|------|------|------|
| `/fmail` | 打开邮件系统GUI | mailsystem.use |
| `/fmail send <玩家> <标题> [内容]` | 发送邮件 | mailsystem.send |
| `/fmail write <玩家> <标题>` | 交互式编写邮件 | mailsystem.send |
| `/fmail attach <玩家> [标题]` | 发送手持物品 | mailsystem.attach |
| `/fmail read` | 查看未读邮件 | mailsystem.read |
| `/fmail list [页码]` | 查看收件箱 | mailsystem.read |
| `/fmail inbox [页码]` | 查看收件箱（别名） | mailsystem.read |
| `/fmail sent` | 查看已发送 | mailsystem.send |
| `/fmail open <邮件ID>` | 打开邮件详情 | mailsystem.read |
| `/fmail claim <邮件ID>` | 领取附件 | mailsystem.read |
| `/fmail delete <邮件ID>` | 删除邮件 | mailsystem.delete |
| `/fmail clear` | 清空收件箱（需确认） | mailsystem.delete |

### 管理员命令

| 命令 | 描述 | 权限 |
|------|------|------|
| `/fmail broadcast <目标> <标题> [内容]` | 群发邮件 | mailsystem.admin |
| `/fmail manage <玩家>` | 管理指定玩家的邮件 | mailsystem.admin |
| `/fmail reload` | 重载配置 | mailsystem.admin |
| `/fmail clearcache` | 清除缓存 | mailsystem.admin |

**群发目标类型：**
- `online` - 所有当前子服在线玩家
- `3days` - 3天内登录过的玩家
- `7days` - 7天内登录过的玩家
- `all` - 所有曾经登录过的玩家

## 权限

- `mailsystem.use` - 使用邮件系统基本功能
- `mailsystem.send` - 发送邮件
- `mailsystem.read` - 阅读邮件
- `mailsystem.delete` - 删除邮件
- `mailsystem.attach` - 发送带附件的邮件
- `mailsystem.admin` - 管理员权限（群发、管理其他玩家邮件、重载配置）

## 配置文件

```yaml
# 服务器ID（用于多服务器区分，留空则使用服务器名称）
server:
  id: ""

# 数据库设置
database:
  # 数据库类型: mysql 或 h2
  type: h2

  # MySQL 设置
  mysql:
    host: localhost
    port: 3306
    database: mailsystem
    username: root
    password: password
    pool-size: 10

  # H2 设置
  h2:
    filename: mailsystem

# 邮件设置
mail:
  # 最大附件数量
  max-attachments: 5
  # 邮件标题最大长度
  max-title-length: 32
  # 邮件内容最大长度
  max-content-length: 500
  # 邮件过期天数（0为永不过期）
  expiration-days: 30
  # 未读邮件检查间隔（秒）
  unread-check-interval: 60
  # 跨服务器新邮件检查间隔（秒）
  cross-server-check-interval: 10
  # 玩家邮箱最大邮件数量（0为无限制）
  max-mailbox-size: 100

# 经济设置（需要安装 XConomy 插件）
economy:
  # 是否启用经济功能
  enabled: true
  # 发送邮件基础邮费
  mail-postage-fee: 10.0
  # 每个附件的快递费
  attachment-delivery-fee: 5.0
```

## 多服务器配置

### 使用 MySQL 实现多服务器同步

1. 确保所有子服务器使用同一个 MySQL 数据库
2. 在每个服务器的配置文件中设置：

```yaml
database:
  type: mysql
  mysql:
    host: 你的MySQL主机
    port: 3306
    database: mailsystem
    username: 用户名
    password: 密码

server:
  id: "server1"  # 每个服务器设置不同的ID
```

3. 重启所有服务器

## 依赖

- **NBTAPI** - 必须安装，用于物品数据序列化
- **Folia** 或 **Paper** 1.21+
- **XConomy**（可选）- 经济插件，用于邮件费用功能

## 编译

```bash
./gradlew clean shadowJar
```

编译后的插件位于 `build/libs/mail_system-1.0.0.jar`

## 项目结构

```
src/main/java/dev/user/mailsystem/
├── MailSystemPlugin.java          # 主类
├── cache/
│   └── PlayerCacheManager.java    # 玩家缓存管理
├── command/
│   └── MailCommand.java           # 命令处理
├── config/
│   └── MailConfig.java            # 配置管理
├── database/
│   ├── DatabaseManager.java       # 数据库连接池
│   └── DatabaseQueue.java         # 异步数据库操作队列
├── economy/
│   └── EconomyManager.java        # 经济系统管理（XConomy软依赖）
├── gui/
│   ├── AdminMailManageGUI.java    # 管理员邮件管理GUI
│   ├── ComposeGUI.java            # 写邮件GUI
│   ├── GUIManager.java            # GUI管理器
│   ├── InboxGUI.java              # 收件箱GUI
│   ├── MainMenuGUI.java           # 主菜单GUI
│   ├── MailViewGUI.java           # 邮件详情GUI
│   └── SentBoxGUI.java            # 发件箱GUI
├── listener/
│   └── MailListener.java          # 事件监听
├── mail/
│   ├── CrossServerNotifier.java   # 跨服通知器
│   ├── Mail.java                  # 邮件数据模型
│   └── MailManager.java           # 邮件管理器
└── util/
    └── ItemBuilder.java           # 物品构建工具
```

## 技术细节

### 物品序列化

使用 NBT API 将 ItemStack 序列化为 NBT，然后 GZIP 压缩存储到数据库：

```java
// 序列化
ReadWriteNBT nbtList = NBT.createNBTObject();
for (ItemStack item : items) {
    ReadWriteNBT itemNbt = NBT.itemStackToNBT(item);
    nbtList.setString("item_" + i, itemNbt.toString());
}
String nbtString = nbtList.toString();
// GZIP 压缩
byte[] data = gzipCompress(nbtString);
```

```java
// 反序列化
String nbtString = gzipDecompress(data);
ReadWriteNBT nbtList = NBT.parseNBT(nbtString);
for (...) {
    ReadWriteNBT itemNbt = NBT.parseNBT(nbtList.getString("item_" + i));
    ItemStack item = NBT.itemStackFromNBT(itemNbt);
}
```

### 异步数据库操作

使用单线程队列执行所有数据库操作，避免阻塞主线程：

```java
// 提交异步任务
databaseQueue.submitAsync("taskName", conn -> {
    // 数据库操作
    return result;
});

// 带回调的任务
databaseQueue.submit("taskName", conn -> {
    return result;
}, callback);
```

### Folia 线程安全

- 使用 `GlobalRegionScheduler` 执行定时任务
- 使用 `player.getScheduler()` 在玩家区域线程操作背包
- 使用 `ConcurrentHashMap` 和 `CopyOnWriteArrayList` 保证线程安全
- 使用 `CompletableFuture` 处理异步发送结果

### 邮箱上限检查

发送邮件前异步查询目标玩家的邮件数量：

```java
getMailCountAsync(playerUuid, count -> {
    if (count >= maxMailboxSize) {
        // 邮箱已满，发送失败
    } else {
        // 执行发送
    }
});
```

管理员（`mailsystem.admin` 权限）不受此限制。

## 代码审查与优化记录

### 已修复问题（18项）

| # | 问题 | 文件 | 修复方案 |
|---|------|------|----------|
| 1 | SentBoxGUI性能问题 | `SentBoxGUI.java`, `MailManager.java` | 添加`loadSentMails()`使用数据库查询sender_uuid |
| 2 | 附件领取竞态条件 | `MailManager.java` | 内存putIfAbsent + 数据库UPDATE WHERE双重保护 |
| 3 | 聊天监听器内存泄漏 | `GUIManager.java`, `MailListener.java` | GUIManager集中管理监听器，PlayerQuitEvent清理 |
| 4 | ResultSet资源泄漏 | `PlayerCacheManager.java`, `CrossServerNotifier.java`, `MailManager.java` | 使用try-with-resources包裹ResultSet |
| 5 | lastCheckTime更新时机 | `CrossServerNotifier.java` | 查询前立即更新，避免漏检 |
| 6 | Mail对象同步 | `Mail.java` | read/claimed方法添加synchronized |
| 7 | 批量缓存清理 | `MailManager.java` | doSendMailsBatch中批量清理缓存 |
| 8 | 批量数据库插入 | `MailManager.java` | JDBC batch insert，每100条执行一次 |
| 9 | IN子句参数限制 | `MailManager.java` | 每批500个参数分批查询 |
| 10 | GUIManager线程安全 | `GUIManager.java` | HashMap改为ConcurrentHashMap |
| 11 | 群发选择监听器泄漏 | `ComposeGUI.java` | 使用GUIManager注册和注销 |
| 12 | Command sent缓存遍历 | `MailCommand.java` | 改用`loadSentMails()`数据库查询 |
| 13 | ComposeGUI快速点击 | `ComposeGUI.java`, `GUIManager.java` | 添加AtomicBoolean processing状态标记 |
| 14 | notifiedMails无限增长 | `CrossServerNotifier.java` | 使用LinkedHashMap实现LRU缓存，限制10000条 |
| 15 | PlayerChat兼容性问题 | `ComposeGUI.java`, `InboxGUI.java`, `AdminMailManageGUI.java`, `MailCommand.java` | 同时监听AsyncChatEvent和AsyncPlayerChatEvent |
| 16 | getDisplayName弃用 | `MailCommand.java` | 改用Adventure Component API |
| 17 | setCustomModelData弃用 | `ItemBuilder.java` | 添加@SuppressWarnings注解 |

### 关键修复代码

**附件领取竞态条件修复：**
```java
// 第一层保护：内存原子操作
if (processingClaims.putIfAbsent(mailId, Boolean.TRUE) != null) {
    player.sendMessage("§c该邮件附件正在处理中...");
    return;
}

databaseQueue.submit("claimAttachments", conn -> {
    // 第二层保护：数据库原子操作
    String sql = "UPDATE mails SET is_claimed = ? WHERE id = ? AND is_claimed = ?";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setBoolean(1, true);
        ps.setString(2, mailId.toString());
        ps.setBoolean(3, false);
        return ps.executeUpdate();  // 返回实际更新行数
    }
}, updated -> {
    if (updated > 0) {
        // 领取成功
    } else {
        // 已被其他线程/服务器领取
    }
    processingClaims.remove(mailId);
});
```

**批量发送优化：**
```java
// JDBC batch insert，每100条执行一次
for (Mail mail : mails) {
    // ... 设置参数
    ps.addBatch();
    if (++batchCount % 100 == 0) {
        ps.executeBatch();
        batchCount = 0;
    }
}
if (batchCount > 0) ps.executeBatch();
```

**IN子句分批查询：**
```java
// 每批500个，避免超过数据库参数限制(1000)
int batchSize = 500;
for (int i = 0; i < receiverList.size(); i += batchSize) {
    List<UUID> batch = receiverList.subList(i, Math.min(i + batchSize, receiverList.size()));
    String placeholders = String.join(",", Collections.nCopies(batch.size(), "?"));
    // ... 执行查询
}
```

### 待处理问题（5项）

1. **InboxGUI过期邮件过滤** - 建议在SQL层过滤而非内存
2. **getUnreadCount性能** - 建议维护计数器或使用数据库COUNT
3. **serializeAttachments失败处理** - 建议添加严格错误处理
4. **DatabaseQueue队列无限增长** - 建议添加大小限制
5. **NBT版本兼容** - 建议添加版本标记

---

## 新增功能

### 邮件费用系统（2026-02-02）

**配置文件**:
```yaml
economy:
  # 是否启用经济功能
  enabled: true
  # 发送邮件基础邮费
  mail-postage-fee: 10.0
  # 每个附件的快递费
  attachment-delivery-fee: 5.0
```

**费用计算**:
- 总费用 = 基础邮费 + 附件数量 × 快递费
- 群发邮件不扣费（管理员功能）
- 发送失败（邮箱满）自动退还费用

**提示信息**:
- 余额不足时提示：`§c[邮件系统] §e余额不足！需要 XX，当前余额 XX`
- 扣费成功时提示：`§a[邮件系统] §e已扣费 XX`（统一在 MailManager 中发送）
- 扣费失败时提示：`§c[邮件系统] §e扣费失败，请稍后再试！`
- 发送失败（邮箱满）不扣费，无需退还

**界面提示**:
- ComposeGUI 发送按钮显示基础邮费和快递费标准
- ComposeGUI 附件区域显示每个附件的快递费
- `/fmail` 帮助命令显示当前费用配置

**支持的费用场景**:
| 发送方式 | 费用计算 |
|---------|---------|
| GUI发送 | 基础邮费 + 附件数 × 快递费 |
| `/fmail send` | 基础邮费（无附件） |
| `/fmail write` | 基础邮费（无附件） |
| `/fmail attach` | 基础邮费 + 1个附件快递费 |
| 群发 | 不扣费（管理员功能） |

**核心代码**:
```java
// MailManager.java - 统一处理费用和发送
private void processPaymentAndSend(Mail mail, Player sender, double cost,
        Consumer<Boolean> callback, boolean clearCache) {
    // 检查余额并扣费
    if (cost > 0 && plugin.getEconomyManager().isEnabled()) {
        if (!plugin.getEconomyManager().hasEnough(sender, cost)) {
            sender.sendMessage("§c[邮件系统] §e余额不足！...");
            callback.accept(false);
            return;
        }
        if (!plugin.getEconomyManager().withdraw(sender, cost)) {
            sender.sendMessage("§c[邮件系统] §e扣费失败...");
            callback.accept(false);
            return;
        }
        // 扣费成功提示
        sender.getScheduler().run(plugin, task -> {
            sender.sendMessage("§a[邮件系统] §e已扣费 §f" +
                plugin.getEconomyManager().format(cost));
        }, null);
    }
    // 执行发送
    doSendMail(mail, sender, clearCache);
    callback.accept(true);
}
```

### 清空收件箱功能（2026-02-02）

**命令**: `/fmail clear`
- 需要权限: `mailsystem.delete`
- 需要先输入 'confirm' 确认，防止误操作

**GUI按钮**:
- InboxGUI: 槽位51，使用熔岩桶图标
- AdminMailManageGUI: 槽位51，管理其他玩家时显示

**实现代码**:
```java
// MailManager.java - 清空收件箱
public void clearInbox(UUID playerUuid, Consumer<Integer> callback) {
    databaseQueue.submit("clearInbox", conn -> {
        String sql = "DELETE FROM mails WHERE receiver_uuid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            return ps.executeUpdate();
        }
    }, deleted -> {
        playerMailCache.remove(playerUuid);
        unreadNotificationSent.remove(playerUuid);
        if (callback != null) {
            callback.accept(deleted);
        }
    });
}
```

---

*最后更新: 2026-02-02*

### 本次更新记录

**修复问题：**
- #12 Command sent命令改用数据库查询
- #13 ComposeGUI添加发送状态标记防止快速点击
- #14 notifiedMails添加LRU缓存机制（最大10000条）
- #15 PlayerChat兼容性：同时监听新旧聊天事件
- #16-17 修复弃用API警告

**关键变更：**
- 聊天监听器现在同时监听 `AsyncChatEvent`（Paper新事件）和 `AsyncPlayerChatEvent`（Bukkit旧事件）
- 旧事件处理器只取消事件，逻辑统一在新事件中处理，避免重复执行
- 添加 `event.viewers().clear()` 和 `event.getRecipients().clear()` 双重保险防止消息泄露
