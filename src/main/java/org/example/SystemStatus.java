package org.example;

/**
 * 系统连接状态统一管理类
 */
public class SystemStatus {
    // 标记 Redis 是否正在重连
    public static volatile boolean isRedisReconnecting = false;

    // 标记 ActiveMQ 是否正在重连
    public static volatile boolean isMQReconnecting = false;

    /**
     * 是否有任意组件正在重连（用于判断是否暂停定时任务）
     */
    public static boolean isAnyReconnecting() {
        return isRedisReconnecting || isMQReconnecting;
    }
}
