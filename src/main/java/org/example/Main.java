package org.example;

import javax.jms.JMSException;
import java.util.concurrent.TimeUnit;

import static org.example.Control.scheduler;

public class Main {
    public static void main(String[] args) throws JMSException {
        RedisConnector.initPool("192.168.43.69", 6379, null);

        Control control = new Control();

        // 2. 启动定时任务（每秒执行一次）
        control.statusCheckFuture = scheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        control.checkSystemStatus();
                    } catch (Exception e) {
                        System.err.println("定时任务执行异常: " + e.getMessage());
                        // 发生异常时停止所有任务
                        control.stopAllTasks();
                    }
                },
                0,  // 初始延迟（0 表示立即执行）
                100,  // 执行间隔（1 秒）
                TimeUnit.MILLISECONDS  // 时间单位改为毫秒
        );

    }
}