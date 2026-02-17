# FoliaMail

[![Java](https://img.shields.io/badge/Java-21-blue)](https://adoptium.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21-green)](https://papermc.io/)
[![Folia](https://img.shields.io/badge/Folia-Supported-orange)](https://papermc.io/software/folia)
[![](https://jitpack.io/v/cxnaive/FoliaMail.svg)](https://jitpack.io/#cxnaive/FoliaMail)

一个功能完善的 Minecraft 多服务器同步邮件系统插件，支持 Folia/Paper 1.21+。

## 功能特性

- **多服务器同步** - 通过 MySQL 或 H2 数据库实现跨服务器邮件同步
- **邮件附件** - 支持发送物品作为附件（使用 NBT + GZIP 压缩）
- **金币附件** - 支持在邮件中附加金币（需要 `mailsystem.attach.money` 权限）
- **GUI 界面** - 完整的图形界面（主菜单、收件箱、发件箱、写邮件、邮件详情）
- **邮件模板** - 管理员可保存邮件为模板，支持变量替换和快速发送
- **经济系统** - 可选 XConomy 集成，支持邮费功能
- **每日限制** - 玩家每日发送邮件上限（管理员豁免）
- **黑名单** - 玩家可屏蔽特定发送者
- **Folia 兼容** - 完全支持 Folia 多线程服务器架构

## 安装

1. 下载 `FoliaMail-1.1.0.jar`
2. 将 JAR 文件放入服务器的 `plugins` 文件夹
3. 安装依赖插件：
   - [NBTAPI](https://www.spigotmc.org/resources/nbt-api.7939/)（必需）
   - [XConomy](https://github.com/YiC200333/XConomy)（可选，用于经济功能）
4. 重启服务器
5. 编辑 `plugins/MailSystem/config.yml` 配置数据库

## 配置

### 基本配置

```yaml
# 数据库设置
database:
  type: h2  # 或 mysql
  # H2 设置（单服务器）
  h2:
    filename: mailsystem
  # MySQL 设置（多服务器）
  mysql:
    host: localhost
    port: 3306
    database: mailsystem
    username: root
    password: password
    pool-size: 10

# 邮件设置
mail:
  max-attachments: 5          # 最大附件数量
  max-title-length: 32        # 标题最大长度
  max-content-length: 500     # 内容最大长度
  expiration-days: 30         # 邮件过期天数（0为永不过期）
  max-mailbox-size: 20        # 邮箱最大邮件数
  daily-send-limit: 10        # 每日发送上限（0为无限制）

# 经济设置
economy:
  enabled: true
  currency-name: "金币"        # 货币单位
  mail-postage-fee: 10.0      # 基础邮费
  attachment-delivery-fee: 5.0 # 物品附件快递费/个
```

### 数据库超时配置

```yaml
database:
  timeout:
    connection-timeout: 5000   # 连接获取超时（毫秒）
    query-timeout: 10          # 查询超时（秒）
    lock-wait-timeout: 5       # 锁等待超时（秒）
```

**注意**：超时配置修改后需重启服务器生效，reload 命令不会重载。

## 命令

| 命令 | 描述 | 权限 |
|------|------|------|
| `/fmail` | 打开邮件 GUI | `mailsystem.use` |
| `/fmail send <玩家> <标题> [内容]` | 发送邮件 | `mailsystem.send` |
| `/fmail write <玩家> <标题>` | 交互式编写邮件 | `mailsystem.send` |
| `/fmail attach <玩家> [标题]` | 发送手持物品 | `mailsystem.attach` |
| `/fmail money <玩家> <金额> [标题]` | 发送金币 | `mailsystem.attach.money` |
| `/fmail read` | 查看未读邮件 | `mailsystem.read` |
| `/fmail list [页码]` | 查看收件箱 | `mailsystem.read` |
| `/fmail sent` | 查看已发送邮件 | `mailsystem.send` |
| `/fmail open <邮件ID>` | 打开邮件详情 | `mailsystem.read` |
| `/fmail claim <邮件ID>` | 领取附件 | `mailsystem.read` |
| `/fmail delete <邮件ID>` | 删除邮件 | `mailsystem.delete` |
| `/fmail clear` | 清空收件箱 | `mailsystem.delete` |
| `/fmail blacklist add <玩家>` | 添加黑名单 | `mailsystem.use` |
| `/fmail blacklist remove <玩家>` | 移除黑名单 | `mailsystem.use` |
| `/fmail blacklist list` | 查看黑名单 | `mailsystem.use` |
| `/fmail template delete <名称>` | 删除模板 | `mailsystem.template.delete` |
| `/fmail template list [页码]` | 列出所有模板 | `mailsystem.template.use` |
| `/fmail template info <名称>` | 查看模板详情 | `mailsystem.template.use` |
| `/fmail template send <模板名> <玩家>` | 使用模板发送邮件 | `mailsystem.template.send` |
| `/fmail template broadcast <模板名> <目标>` | 使用模板群发邮件 | `mailsystem.template.broadcast` |
| `/fmail reload` | 重载配置 | `mailsystem.admin` |

**模板变量**：模板标题和内容支持以下变量
- `{sender}` - 发送者名称
- `{receiver}` - 接收者名称
- `{time}` - 当前时间 (HH:mm)
- `{date}` - 当前日期 (yyyy-MM-dd)
- `{server}` - 服务器名称

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
| `mailsystem.admin` | 管理员权限（重载、管理等） | op |

## 开发者 API

其他插件可以通过 Gradle 引入 FoliaMail API 来调用邮件发送功能。

### Gradle 配置

在 `build.gradle` 或 `build.gradle.kts` 中添加：

```kotlin
repositories {
    // 使用 GitHub 仓库（通过 JitPack）
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("com.github.cxnaive:FoliaMail:v1.2.0")
}
```

### API 使用示例

```java
import dev.user.mailsystem.api.MailSystemAPI;
import dev.user.mailsystem.api.draft.MailDraft;
import dev.user.mailsystem.api.draft.SendOptions;

public class MyPlugin extends JavaPlugin {

    private MailSystemAPI mailAPI;

    @Override
    public void onEnable() {
        // 获取 API 实例
        mailAPI = Bukkit.getServicesManager().load(MailSystemAPI.class);
        if (mailAPI == null) {
            getLogger().warning("FoliaMail 未安装，邮件功能不可用");
            return;
        }
    }

    // 发送单封邮件
    public void sendRewardMail(Player sender, UUID targetUuid, String targetName, ItemStack reward) {
        MailDraft draft = MailDraft.builder()
            .sender(sender.getUniqueId(), sender.getName())
            .receiver(targetUuid, targetName)
            .title("活动奖励")
            .content("恭喜你获得活动奖励！")
            .addAttachment(reward)
            .build();

        mailAPI.send(draft, sender, result -> {
            if (result.isSuccess(targetUuid)) {
                sender.sendMessage("§a奖励邮件已发送！");
            } else {
                sender.sendMessage("§c发送失败: " + result.getFailReason(targetUuid));
            }
        });
    }

    // 批量发送系统邮件（无费用）
    public void broadcastSystemMail(List<UUID> receivers, String title, String content) {
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

        SendOptions options = SendOptions.systemMail(); // 系统邮件选项

        mailAPI.sendBatch(drafts, options, null, result -> {
            getLogger().info("批量发送完成: " + result.getSuccessCount() + "/" + result.getTotalCount());
        });
    }
}
```

### API 事件监听

```java
import dev.user.mailsystem.api.MailListener;

public class MyMailListener implements MailListener {

    @Override
    public void onMailSend(MailSendEvent event) {
        // 邮件发送时触发
        getLogger().info("邮件从 " + event.getMail().getSenderName() + " 发送给 " + event.getMail().getReceiverName());
    }

    @Override
    public void onAttachmentClaim(AttachmentClaimEvent event) {
        // 附件被领取时触发
        // 可用于记录日志或触发其他奖励
    }
}

// 注册监听器
mailAPI.registerListener(new MyMailListener());
```

## 构建

```bash
# 克隆仓库
git clone <repository-url>
cd mail_system

# 构建项目
./gradlew build

# 输出文件位于 build/libs/FoliaMail-1.1.0.jar
```

## 技术特性

- **异步数据库操作** - 使用单线程队列执行数据库操作，避免阻塞主线程
- **线程安全** - 使用 ConcurrentHashMap、CopyOnWriteArrayList 等线程安全集合
- **性能优化** - 批量数据库插入、IN 子句分批查询、LRU 缓存机制
- **熔断机制** - 数据库队列超载时自动拒绝新任务，保护服务器稳定性

## 许可证

MIT License

## 致谢

- [PaperMC](https://papermc.io/) - 服务端 API
- [Folia](https://papermc.io/software/folia) - 多线程支持
- [NBTAPI](https://github.com/tr7zw/Item-NBT-API) - NBT 序列化
- [XConomy](https://github.com/YiC200333/XConomy) - 经济系统
- [HikariCP](https://github.com/brettwooldridge/HikariCP) - 数据库连接池
