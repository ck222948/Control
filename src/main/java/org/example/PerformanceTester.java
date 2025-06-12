package org.example;

import javax.jms.JMSException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.*;

public class PerformanceTester {
    private static final AtomicLong executionCount = new AtomicLong(0);
    private static volatile boolean running = true;
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    /**
     * 高精度测试函数每秒执行次数
     * @param control 包含要测试方法的 Control 实例
     * @param testDurationSeconds 测试持续时间（秒）
     */
    public static void testCheckSystemStatusPerformance(Control control, int testDurationSeconds) {
        // 重置计数器
        executionCount.set(0);
        running = true;

        // 创建高精度计时器（1ms精度）
        ScheduledFuture<?> timer = scheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        control.checkSystemStatus();
                        executionCount.incrementAndGet();
                    } catch (Exception e) {
                        System.err.println("测试执行出错: " + e.getMessage());
                    }
                },
                0,
                100, // 每100ms尝试执行一次（目标10次/秒）
                TimeUnit.MILLISECONDS
        );

        System.out.println("开始性能测试，持续时间: " + testDurationSeconds + "秒...");

        // 定时停止测试
        scheduler.schedule(() -> {
            running = false;
            timer.cancel(true);

            // 计算结果
            long totalExecutions = executionCount.get();
            double executionsPerSecond = (double) totalExecutions / testDurationSeconds;

            System.out.println("\n测试结果:");
            System.out.println("总执行次数: " + totalExecutions);
            System.out.printf("平均每秒执行次数: %.2f\n", executionsPerSecond);

            // 关闭资源
            try {
                control.stopAllTasks();
            } catch (Exception e) {
                System.err.println("关闭资源时出错: " + e.getMessage());
            }
        }, testDurationSeconds, TimeUnit.SECONDS);
    }

    public static void main(String[] args) throws JMSException {
        // 初始化
        RedisConnector.initPool("192.168.43.69", 6379, null);
        Control control = new Control();

        // 运行性能测试（测试10秒）
        testCheckSystemStatusPerformance(control, 10);

        // 保持主线程运行直到测试完成
        while (running) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}