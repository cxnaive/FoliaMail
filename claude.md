# FoliaMail - 多服务器同步邮件系统

[![Java](https://img.shields.io/badge/Java-21-blue)](https://adoptium.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21-green)](https://papermc.io/)
[![Folia](https://img.shields.io/badge/Folia-Supported-orange)](https://papermc.io/software/folia)

一个功能完善的 Minecraft 多服务器同步邮件系统插件，支持 Folia/Paper 1.21+。

## 特性

- **Folia 支持** - 完全兼容 Folia 多线程服务器架构
- **多服务器同步** - 支持 MySQL 和 H2 数据库，实现跨服务器邮件同步
- **邮件附件** - 支持发送物品作为附件（NBT + GZIP 压缩）
- **金币附件** - 支持在邮件中附加金币（需要 `mailsystem.attach.money` 权限）
- **GUI 界面** - 完整的图形界面（主菜单、收件箱、发件箱、写邮件、邮件详情）
- **经济系统** - 可选 XConomy 集成，支持邮费功能
- **每日限制** - 玩家每日发送邮件上限（管理员豁免）
- **黑名单** - 玩家可屏蔽特定发送者
- **对外 API** - 完整的开发者 API，支持其他插件调用邮件功能
- **Pipeline 架构** - 使用责任链模式处理邮件发送流程
- **邮件模板** - 管理员可保存邮件为模板，支持变量替换和快速发送

## 命令

### 基础命令

| 命令 | 描述 | 权限 |
|------|------|------|
| `/fmail` | 打开邮件系统GUI | `mailsystem.use` |
| `/fmail send <玩家> <标题> [内容]` | 发送邮件 | `mailsystem.send` |
| `/fmail write <玩家> <标题>` | 交互式编写邮件 | `mailsystem.send` |
| `/fmail attach <玩家> [标题]` | 发送手持物品 | `mailsystem.attach` |
| `/fmail money <玩家> <金额> [标题]` | 发送金币 | `mailsystem.attach.money` |
| `/fmail read` | 查看未读邮件 | `mailsystem.read` |
| `/fmail list [页码]` | 查看收件箱 | `mailsystem.read` |
| `/fmail sent` | 查看已发送 | `mailsystem.send` |
| `/fmail open <邮件ID>` | 打开邮件详情 | `mailsystem.read` |
| `/fmail claim <邮件ID>` | 领取附件 | `mailsystem.read` |
| `/fmail delete <邮件ID>` | 删除邮件 | `mailsystem.delete` |
| `/fmail clear` | 清空收件箱（需确认） | `mailsystem.delete` |

### 黑名单命令

| 命令 | 描述 | 权限 |
|------|------|------|
| `/fmail blacklist add <玩家>` | 添加黑名单 | `mailsystem.use` |
| `/fmail blacklist remove <玩家>` | 移除黑名单 | `mailsystem.use` |
| `/fmail blacklist list` | 查看黑名单 | `mailsystem.use` |

### 管理员命令

| 命令 | 描述 | 权限 |
|------|------|------|
| `/fmail template save <名称> [显示名]` | 保存当前邮件为模板 | `mailsystem.template.create` |
| `/fmail template delete <名称>` | 删除模板 | `mailsystem.template.delete` |
| `/fmail template list [页码]` | 列出所有模板 | `mailsystem.template.use` |
| `/fmail template info <名称>` | 查看模板详情 | `mailsystem.template.use` |
| `/fmail template send <模板名> <玩家>` | 使用模板发送邮件 | `mailsystem.template.send` |
| `/fmail template broadcast <模板名> <目标>` | 使用模板群发邮件 | `mailsystem.template.broadcast` |
| `/fmail reload` | 重载配置 | `mailsystem.admin` |

## 权限

| 权限 | 描述 | 默认 |
|------|------|------|
| `mailsystem.use` | 使用邮件系统基础功能 | true |
| `mailsystem.read` | 查看收件箱和阅读邮件 | true |
| `mailsystem.send` | 发送邮件 | op |
| `mailsystem.attach` | 发送带物品附件的邮件 | op |
| `mailsystem.attach.money` | 发送带金币附件的邮件 | op |
| `mailsystem.delete` | 删除自己的邮件 | op |
| `mailsystem.template.create` | 创建邮件模板 | op |
| `mailsystem.template.delete` | 删除邮件模板 | op |
| `mailsystem.template.use` | 查看和使用邮件模板 | op |
| `mailsystem.template.send` | 使用模板发送给单个玩家 | op |
| `mailsystem.template.broadcast` | 使用模板群发邮件 | op |
| `mailsystem.admin` | 管理员权限 | op |

## 配置文件

```yaml
# 服务器ID（用于多服务器区分，留空则使用服务器名称）
server:
  id: ""

# 数据库设置
database:
  type: h2  # 或 mysql

  # MySQL 设置（多服务器）
  mysql:
    host: localhost
    port: 3306
    database: mailsystem
    username: root
    password: password
    pool-size: 10

  # H2 设置（单服务器）
  h2:
    filename: mailsystem

  # 超时配置（修改后需重启）
  timeout:
    connection-timeout: 5000   # 连接获取超时（毫秒）
    query-timeout: 10          # 查询超时（秒）
    lock-wait-timeout: 5       # 锁等待超时（秒）

# 邮件设置
mail:
  max-attachments: 5          # 最大附件数量
  max-title-length: 32        # 标题最大长度
  max-content-length: 500     # 内容最大长度
  expiration-days: 30         # 邮件过期天数（0为永不过期）
  max-mailbox-size: 20        # 邮箱最大邮件数
  daily-send-limit: 10        # 每日发送上限（0为无限制）
  unread-check-interval: 60   # 未读邮件检查间隔（秒）

# 经济设置（需要安装 XConomy 插件）
economy:
  enabled: true
  currency-name: "金币"        # 货币单位
  mail-postage-fee: 10.0      # 基础邮费
  attachment-delivery-fee: 5.0 # 物品附件快递费/个
```

## 项目结构

```
src/main/java/dev/user/mailsystem/
├── MailSystemPlugin.java          # 主类
├── api/                           # 对外 API
│   ├── MailSystemAPI.java         # API 接口
│   ├── MailSystemAPIImpl.java     # API 实现
│   ├── MailListener.java          # 事件监听器接口
│   ├── event/                     # API 事件
│   │   ├── AttachmentClaimEvent.java
│   │   ├── MailReadEvent.java
│   │   └── MailSendEvent.java
│   └── draft/                     # 邮件草稿与选项
│       ├── MailDraft.java         # 邮件草稿（Builder模式）
│       ├── SendOptions.java       # 发送选项
│       ├── SendResult.java        # 发送结果（密封类）
│       └── BatchSendResult.java   # 批量发送结果
├── command/
│   └── MailCommand.java           # 命令处理
├── config/
│   └── MailConfig.java            # 配置管理
├── database/
│   ├── DatabaseManager.java       # 数据库连接池
│   └── DatabaseQueue.java         # 异步数据库操作队列
├── economy/
│   └── EconomyManager.java        # 经济系统管理
├── gui/                           # GUI 界面
│   ├── ComposeGUI.java
│   ├── InboxGUI.java
│   ├── MainMenuGUI.java
│   ├── MailViewGUI.java
│   ├── SentBoxGUI.java
│   ├── TemplateListGUI.java       # 模板列表界面
│   └── GUIManager.java
├── listener/
│   └── PlayerListener.java        # 玩家事件监听
├── mail/                          # 邮件核心
│   ├── Mail.java                  # 邮件数据模型
│   ├── MailManager.java           # 邮件管理器（精简版）
│   ├── BlacklistManager.java      # 黑名单管理
│   ├── MailCacheManager.java      # 邮件缓存管理（含TTL）
│   ├── MailLogManager.java        # 发送日志管理
│   ├── AttachmentManager.java     # 附件序列化管理
│   ├── CrossServerNotifier.java   # 跨服通知器
│   └── pipeline/                  # Pipeline 发送流程
├── template/                      # 邮件模板功能
│   ├── MailTemplate.java          # 模板数据模型
│   └── TemplateManager.java       # 模板管理器
│       ├── SendPipeline.java      # 发送管道
│       ├── SendContext.java       # 发送上下文
│       ├── SendChain.java         # 责任链接口
│       ├── SendFilter.java        # 过滤器接口
│       └── filters/               # 过滤器实现
│           ├── ValidationFilter.java
│           ├── MailboxLimitFilter.java
│           ├── DailyLimitFilter.java
│           ├── BlacklistFilter.java
│           ├── EconomyFilter.java
│           └── PersistenceFilter.java
└── util/
    └── ItemBuilder.java           # 物品构建工具
```

## 核心架构

### Pipeline 发送流程

邮件发送使用责任链模式（Pipeline）处理，所有发送（单发/批量）统一走批量模式：

```java
// MailManager.java - 统一发送入口
public void send(List<MailDraft> drafts, SendOptions options, Player sender, Consumer<BatchSendResult> callback)
```

**Pipeline 过滤器顺序：**

1. **ValidationFilter** - 验证草稿数据完整性
2. **MailboxLimitFilter** - 检查收件人邮箱容量
3. **DailyLimitFilter** - 检查发送者每日限制
4. **BlacklistFilter** - 检查黑名单关系
5. **EconomyFilter** - 处理经济扣费
6. **PersistenceFilter** - 持久化到数据库

**过滤器统一批量处理逻辑**（单发 = size==1 的批量）：

```java
public void filterBatch(List<SendContext> contexts, SendChain chain) {
    // 统一处理所有上下文
    for (SendContext ctx : contexts) {
        // 执行过滤逻辑
    }
    chain.proceed(contexts); // 传递给下一个过滤器
}
```

### 缓存系统

**MailCacheManager** - 带 TTL 的邮件缓存：

```java
// 30分钟缓存过期
cacheExpiry.put(playerUuid, System.currentTimeMillis() + 30 * 60 * 1000);

// 线程安全的原子操作
mailCache.compute(playerUuid, (key, oldValue) -> newMailList);
```

**缓存清理时机：**
1. 定时任务每10分钟清理过期缓存
2. 邮件发送成功后清理对应玩家缓存
3. 附件领取后清理缓存
4. 玩家退出后延迟清理（通过 PlayerListener）
5. 插件重载时清理全部缓存

### 跨服竞态条件处理

**附件领取使用数据库行锁：**

```java
// 1. 内存锁防止同服并发
if (processingClaims.putIfAbsent(mailId, Boolean.TRUE) != null) {
    return; // 正在处理中
}

// 2. 数据库事务 + SELECT FOR UPDATE 防止跨服竞态
databaseQueue.submit("claimAttachments", conn -> {
    conn.setAutoCommit(false);
    // 锁定行
    String selectSql = "SELECT is_claimed FROM mails WHERE id = ? FOR UPDATE";
    // 更新状态
    String updateSql = "UPDATE mails SET is_claimed = TRUE WHERE id = ?";
    conn.commit();
});
```

### 异步数据库操作

使用单线程队列执行所有数据库操作：

```java
// 带回调的异步任务
databaseQueue.submit("taskName", conn -> {
    // 数据库操作
    return result;
}, callback, errorCallback);

// 异步任务（无回调）
databaseQueue.submitAsync("taskName", conn -> {
    return result;
});
```

**熔断机制：**
- 告警阈值：100个任务
- 超载阈值：200个任务（拒绝新任务）

## 开发者 API (v1.2.0)

### Gradle 配置

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("com.github.cxnaive:FoliaMail:1.2.0")
}
```

### 获取 API 实例

```java
MailSystemAPI api = Bukkit.getServicesManager().load(MailSystemAPI.class);
if (api == null) {
    getLogger().warning("FoliaMail 未安装");
    return;
}
```

### 发送邮件

```java
// 构建邮件草稿
MailDraft draft = MailDraft.builder()
    .sender(player.getUniqueId(), player.getName())
    .receiver(targetUuid, targetName)
    .title("活动奖励")
    .content("恭喜你获得奖励！")
    .addAttachment(rewardItem)
    .moneyAttachment(100)
    .build();

// 发送（统一使用 BatchSendResult 回调）
api.send(draft, player, result -> {
    if (result.isSuccess(targetUuid)) {
        player.sendMessage("发送成功！");
    } else {
        player.sendMessage("发送失败: " + result.getFailReason(targetUuid));
    }
});
```

### 批量发送

```java
List<MailDraft> drafts = new ArrayList<>();
for (UUID receiver : receivers) {
    drafts.add(MailDraft.builder()
        .systemSender()
        .receiver(receiver, "")
        .title(title)
        .content(content)
        .build()
    );
}

// 系统邮件选项（免限制、免扣费）
SendOptions options = SendOptions.systemMail();

api.sendBatch(drafts, options, admin, result -> {
    getLogger().info("批量发送完成: " + result.getSuccessCount() + "/" + result.getTotalCount());
});
```

### 事件监听

```java
api.registerListener(new MailListener() {
    @Override
    public void onMailSend(MailSendEvent event) {
        // 邮件发送时触发
    }

    @Override
    public void onMailRead(MailReadEvent event) {
        // 邮件被阅读时触发
    }

    @Override
    public void onAttachmentClaim(AttachmentClaimEvent event) {
        // 附件被领取时触发
    }
});
```

### SendOptions 预定义选项

| 选项 | 说明 |
|------|------|
| `SendOptions.defaults()` | 默认选项（正常限制和扣费） |
| `SendOptions.systemMail()` | 系统邮件（免所有限制、免扣费） |
| `SendOptions.noCharge()` | 不扣费但保留其他限制 |
| `SendOptions.noNotification()` | 不通知接收者 |

## 编译

```bash
./gradlew clean shadowJar
```

编译后的插件位于 `build/libs/FoliaMail-1.2.0.jar`

## 依赖

- **Java 21+**
- **Folia** 或 **Paper** 1.21+
- **NBTAPI** - 必须安装，用于物品数据序列化
- **XConomy**（可选）- 经济插件

## 版本历史

### v1.2.0 (2026-02-17)

**新增功能：**
- 邮件模板系统 - 管理员可将邮件保存为模板，支持变量替换
  - 支持变量：`{sender}`, `{receiver}`, `{time}`, `{date}`, `{server}`
  - 模板命令：`/fmail template [save|delete|list|info|send|broadcast]`
  - GUI支持：写邮件界面可一键保存/加载模板
- 模板权限系统：`mailsystem.template.[create|delete|use|send|broadcast]`

**Bug修复：**
- 修复保存模板时附件数据丢失问题（添加 `setReturned` 标记保护）
- 修复模板命令 Tab 补全的权限检查和参数位置问题
- 统一 ComposeGUI 中所有 chat listener 的注册方式

**代码重构：**
- 重构 chat listener 为函数式接口，支持通用注册方法
- 优化异步操作的监听器注销逻辑

### v1.1.0 (2026-02-15)

**重大变更：**
- 新增完整的对外 API，支持其他插件通过 JitPack 引入
- 重构 MailManager，使用 Pipeline 责任链模式处理发送流程
- 统一单发/批量发送逻辑（单发 = size==1 的批量）
- 所有 API 回调统一使用 `Consumer<BatchSendResult>`

**新增功能：**
- 金币附件功能（需要 `mailsystem.attach.money` 权限）
- 邮件缓存 TTL 机制（30分钟过期）
- JitPack 配置，支持 Gradle 引入

**安全修复：**
- 修复附件领取权限漏洞（验证接收者身份）
- 修复 SQL 注入风险（添加白名单验证）
- 修复玩家名大小写处理问题
- 改进附件领取竞态条件处理（数据库行锁）

**代码质量：**
- 精简 MailManager（1374行 → ~470行）
- 提取专业管理器：BlacklistManager、MailCacheManager、MailLogManager、AttachmentManager
- 移除重复代码，统一批处理逻辑
- 修复 NPE 风险

### v1.0.1 (2026-02-04)

- 项目更名为 FoliaMail
- 新增每日发送限制功能
- 新增黑名单功能
- 新增数据库超时配置与熔断机制
- 新增可配置货币单位

### v1.0.0 (2026-02-02)

- 初始版本
- 多服务器同步邮件系统
- 邮件附件（物品）
- GUI 界面
- 经济系统集成

---

*最后更新: 2026-02-17 - v1.2.0 邮件模板功能发布*
