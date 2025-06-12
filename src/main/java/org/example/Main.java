package org.example;

import javax.jms.Connection;
import javax.jms.JMSException;
import java.util.concurrent.TimeUnit;

import static org.example.Control.scheduler;

public class Main {
    private static volatile boolean isPaused = false;  // 使用 volatile 保证可见性
    public static long startTime = System.nanoTime();
    public static void main(String[] args) throws JMSException {



        Control control = new Control();

        // 2. 启动定时任务（每秒执行一次）
        control.statusCheckFuture = scheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        String StopMark=RedisConnector.get("StopMark");
                        if(StopMark!=null&& StopMark.equals("1")) {
                            if (!isPaused) {
                                System.out.println("实验已暂停");
                                isPaused = true;
                            }
                            return;  // 直接跳过，不执行后续逻辑
                        }
                        else{
                            if (isPaused) {
                                System.out.println("实验已恢复");
                                isPaused = false;
                            }

                            }

                        control.checkSystemStatus();  // 正常执行
                    }
                    catch (Exception e) {
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