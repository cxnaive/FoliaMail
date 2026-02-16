package dev.user.mailsystem.database;

import dev.user.mailsystem.MailSystemPlugin;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class DatabaseQueue {

    private final MailSystemPlugin plugin;
    private final BlockingQueue<DatabaseTask<?>> taskQueue;
    private final ExecutorService executor;
    private final AtomicBoolean running;
    private Thread workerThread;

    // 熔断机制相关
    private final AtomicLong lastOverloadWarningTime = new AtomicLong(0);
    private static final int OVERLOAD_WARNING_INTERVAL_MS = 30000; // 30秒
    private final int queueOverloadThreshold; // 队列超载阈值（从配置读取）
    private final int queueWarningThreshold;  // 队列告警阈值（从配置读取）

    public DatabaseQueue(MailSystemPlugin plugin) {
        this.plugin = plugin;
        this.taskQueue = new LinkedBlockingQueue<>();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "MailSystem-DB-Queue");
            t.setDaemon(true);
            return t;
        });
        this.running = new AtomicBoolean(false);
        // 从配置读取队列阈值
        this.queueOverloadThreshold = plugin.getMailConfig().getQueueMaxSize();
        this.queueWarningThreshold = plugin.getMailConfig().getQueueWarningThreshold();
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            workerThread = new Thread(this::processLoop, "MailSystem-DB-Worker");
            workerThread.setDaemon(true);
            workerThread.start();
            plugin.getLogger().info("数据库操作队列已启动");
        }
    }

    public void stop() {
        running.set(false);
        workerThread.interrupt();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        plugin.getLogger().info("数据库操作队列已停止");
    }

    private void processLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                // 队列深度监控
                checkQueueDepth();

                DatabaseTask<?> task = taskQueue.poll(100, TimeUnit.MILLISECONDS);
                if (task != null) {
                    executeTask(task);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                plugin.getLogger().severe("数据库任务执行错误: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * 检查队列深度，如果超过阈值则输出警告
     */
    private void checkQueueDepth() {
        int queueSize = taskQueue.size();

        // 队列超载告警（带节流，避免日志刷屏）
        if (queueSize >= queueWarningThreshold) {
            long now = System.currentTimeMillis();
            long lastWarning = lastOverloadWarningTime.get();

            if (now - lastWarning > OVERLOAD_WARNING_INTERVAL_MS &&
                lastOverloadWarningTime.compareAndSet(lastWarning, now)) {
                plugin.getLogger().warning("数据库队列堆积: " + queueSize + " 个任务待处理，" +
                    "可能影响邮件功能响应速度！");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void executeTask(DatabaseTask<T> task) {
        long startTime = System.currentTimeMillis();
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            // 设置查询超时
            int queryTimeout = plugin.getMailConfig().getQueryTimeout();
            if (queryTimeout > 0) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.setQueryTimeout(queryTimeout);
                } catch (SQLException ignored) {
                    // 某些驱动可能不支持，忽略错误
                }
            }

            T result = task.getOperation().apply(conn);
            long duration = System.currentTimeMillis() - startTime;

            if (duration > 1000) {
                plugin.getLogger().warning("慢查询: " + task.getName() + " 耗时 " + duration + "ms");
            }

            if (task.getCallback() != null) {
                Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> {
                    ((Consumer<T>) task.getCallback()).accept(result);
                });
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("数据库操作失败 [" + task.getName() + "]: " + e.getMessage());
            e.printStackTrace();

            if (task.getErrorCallback() != null) {
                Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> {
                    task.getErrorCallback().accept(e);
                });
            }
        }
    }

    public <T> void submit(String name, DatabaseOperation<T> operation, Consumer<T> callback) {
        submit(name, operation, callback, null);
    }

    public <T> void submit(String name, DatabaseOperation<T> operation, Consumer<T> callback, Consumer<SQLException> errorCallback) {
        if (!running.get()) {
            plugin.getLogger().warning("数据库队列未运行，无法提交任务: " + name);
            // 触发错误回调，让调用方知道操作失败
            if (errorCallback != null) {
                errorCallback.accept(new SQLException("数据库队列未运行"));
            }
            return;
        }

        // 熔断机制：队列超载时拒绝新任务
        int queueSize = taskQueue.size();
        if (queueSize >= queueOverloadThreshold) {
            plugin.getLogger().severe("数据库队列超载 (" + queueSize + "/" + queueOverloadThreshold + " 个任务)，拒绝新任务: " + name);
            if (errorCallback != null) {
                errorCallback.accept(new SQLException("数据库队列超载，请稍后重试"));
            }
            return;
        }

        DatabaseTask<T> task = new DatabaseTask<>(name, operation, callback, errorCallback);
        try {
            boolean offered = taskQueue.offer(task, 5, TimeUnit.SECONDS);
            if (!offered) {
                plugin.getLogger().severe("提交数据库任务超时 (5秒): " + name);
                if (errorCallback != null) {
                    errorCallback.accept(new SQLException("提交任务超时"));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().severe("提交数据库任务失败: " + name);
            if (errorCallback != null) {
                errorCallback.accept(new SQLException("提交任务被中断", e));
            }
        }
    }

    public void submitAsync(String name, DatabaseOperation<Void> operation) {
        submit(name, operation, null, null);
    }

    public <T> Future<T> submitCallable(Callable<T> callable) {
        return executor.submit(callable);
    }

    public int getPendingCount() {
        return taskQueue.size();
    }

    public boolean isRunning() {
        return running.get();
    }

    @FunctionalInterface
    public interface DatabaseOperation<T> {
        T apply(Connection connection) throws SQLException;
    }

    private static class DatabaseTask<T> {
        private final String name;
        private final DatabaseOperation<T> operation;
        private final Consumer<T> callback;
        private final Consumer<SQLException> errorCallback;

        public DatabaseTask(String name, DatabaseOperation<T> operation, Consumer<T> callback, Consumer<SQLException> errorCallback) {
            this.name = name;
            this.operation = operation;
            this.callback = callback;
            this.errorCallback = errorCallback;
        }

        public String getName() {
            return name;
        }

        public DatabaseOperation<T> getOperation() {
            return operation;
        }

        public Consumer<T> getCallback() {
            return callback;
        }

        public Consumer<SQLException> getErrorCallback() {
            return errorCallback;
        }
    }
}
