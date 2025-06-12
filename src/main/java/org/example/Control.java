package org.example;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

import javax.jms.JMSException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
public class Control{

    // 新增：存储导航状态的旧值（初始设为-1确保首次触发）
    private String lastNaViFinish = "-1";
    // ActiveMQ 连接配置
    private static final String BROKER_URL = "tcp://192.168.43.69:61616";
    private static final String CAR_QUEUE = "UpdateCar";
    private static final String NAVI_QUEUE = "UpdateNavigate";
    private static final String DISPLAY_QUEUE = "UpdateView";
    public long endTime;

    // 数据库状态标志
    private volatile String IsCarOpen;
    private volatile String IsNaviOpen;
    private volatile String IsViewOpen;
    private volatile int CarFinish = 0; // 小车标志值
    private volatile int CarNumber = 0;// 小车数量
    private volatile String IsNaViFinish;  // 补充声明缺失的变量
    private volatile int NaviNumber = 0;// 小车数量
    // 新增：定时任务句柄用于停止
    public ScheduledFuture<?> statusCheckFuture;
    // 消息队列工具
    private final TaskProducer carQueue;
    private final TaskProducer naviQueue;
    private final TaskProducer displayQueue;

    // 线程池
    public static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public  Control() throws JMSException {
        // 初始化消息队列
        this.carQueue = new TaskProducer(BROKER_URL, CAR_QUEUE);
        this.naviQueue = new TaskProducer(BROKER_URL, NAVI_QUEUE);
        this.displayQueue = new TaskProducer(BROKER_URL, DISPLAY_QUEUE);



    }

    /**
     * 检测系统状态并触发消息发送
     */
    public void checkSystemStatus() {
try {
    // 1. 从数据库读取状态（模拟）
    // refreshDatabaseState();
    IsCarOpen = RedisConnector.get("IsCarOpen");
    IsNaviOpen = RedisConnector.get("IsNaviOpen");
    IsViewOpen = RedisConnector.get("IsViewOpen");
    IsNaViFinish =RedisConnector.get("IsNaviFinish");  // 读取导航状态值

    // 1. 显示器队列逻辑
    if (IsViewOpen!=null&&Objects.equals(IsViewOpen, "1")) {
        sendDisplayData();
    }
    System.out.println(lastNaViFinish);
    System.out.println(IsNaViFinish);
// 2. 导航队列逻辑
    if (IsNaviOpen!=null&&(!Objects.equals(IsNaviOpen, "0")) &&(!Objects.equals(lastNaViFinish,IsNaViFinish))) {

        sendNaviCommand();


    }
    // 3. 小车队列逻辑
    if (IsCarOpen!=null&&Objects.equals(IsCarOpen, "1")) {
        handleCarMessages();
    }



    // 4. 检测地图全亮（新增核心逻辑）
    if (checkMapAllOne()) {
        System.out.println("地图全亮，停止所有任务");
        sendDisplayData();
        String data="#";
        displayQueue.sendTask("#");
        System.out.println("[显示器] 数据已发送: " + data);
        stopAllTasks();  // 停止定时任务并释放资源
    }
}
catch (Exception e) {
    System.err.println("【错误】checkSystemStatus 执行失败: " + e.getMessage());
    e.printStackTrace();
}
    }
    /**
     * 检测地图是否全为1（新增方法）
     */
    private boolean checkMapAllOne() {
       /* String map = RedisConnector.get("map");  // 假设地图数据存储在Redis的"map"键中
        if (map == null) return false;
        // 遍历所有字符检查是否为'1'
        for (char c : map.toCharArray()) {
            if (c != '1') return false;
        }
        return true;}*/
        //-----使用bitmap操作------//

        long XTotalNumbers = Long.parseLong(RedisConnector.get("mapWidth"));
        long YTotalNumbers = Long.parseLong(RedisConnector.get("mapLength"));
        long OneNumber = RedisConnector.bitCount("map");
        if (OneNumber == XTotalNumbers * YTotalNumbers) {
            return true;
        } else
            return false;
    }



    /**
     * 停止所有任务并释放资源（新增方法）
     */
    public void stopAllTasks() {


        // 1. 取消定时任务
        if (statusCheckFuture != null && !statusCheckFuture.isCancelled()) {
            statusCheckFuture.cancel(true);
            System.out.println("定时任务已取消");
        }

        // 2. 关闭线程池
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            System.out.println("线程池已关闭");
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // 3. 关闭Redis连接池
        RedisConnector.closePool();
        System.out.println("Redis连接已关闭");

        // 4. 关闭ActiveMQ连接
        try {
            carQueue.close();
            naviQueue.close();
            displayQueue.close();
            System.out.println("ActiveMQ连接已关闭");
        } catch (JMSException e) {
            System.err.println("关闭消息队列失败: " + e.getMessage());
        }

        // 5. 完全退出程序
        System.out.println("程序退出");
        endTime = System.nanoTime();
        long duration = endTime - Main.startTime;
        System.out.println("程序运行时间：" + duration/1000000+ " 毫秒");
        System.exit(0); // 正常退出
    }

    /**
     * 发送显示数据到显示器队列
     */
    private void sendDisplayData() {
        try {
           String data="repaint";
            displayQueue.sendTask("repaint");
            System.out.println("[显示器] 数据已发送: " + data);
        } catch (Exception e) {
            System.err.println("[显示器] 发送失败: " + e.getMessage());
        }
    }

    /**
     * 处理小车消息队列
     */
   /* private void handleCarMessages() {
        try {
            CarNumber=Integer.parseInt(RedisConnector.get("CarNumber"));

            for(int i=1;i<=CarNumber;i++) {
               List<String> list= RedisConnector.lrAll("Car00"+i+"TaskList");
                Collections.reverse(list);
                if (list.isEmpty()) {
                    System.out.println("任务队列为空，不给小车发送消息");
                }
                else if (!list.isEmpty() ) {
                    String cmd="00"+i;
                    carQueue.sendTask(cmd);
                    System.out.println("[小车] 指令已发送: " + cmd);

                    list.remove(list.size()-1);
                }
            }
        } catch (Exception e) {
            System.err.println("[小车] 发送失败: " + e.getMessage());
        }

    }*/

    /**
     * 优化后的处理小车消息队列方法
     */
    private void handleCarMessages() {
        try {
            // 1. 使用线程池并行处理小车任务
            ExecutorService carExecutor = Executors.newFixedThreadPool(10); // 根据小车数量调整

            String CarNumberString=RedisConnector.get("CarNumber");
            if (CarNumberString==null) {
                CarNumber=0;
            }
            // 2. 一次性获取所有小车数量
          else
              CarNumber = Integer.parseInt(RedisConnector.get("CarNumber"));

            // 3. 使用Redis管道批量获取任务列表
            try (Jedis jedis = RedisConnector.getConnection()) {
                Pipeline pipeline = jedis.pipelined();

                // 预先收集所有LRANGE命令
                for (int i = 1; i <= CarNumber; i++) {
                    pipeline.lrange("Car00" + i + "TaskList", 0, -1);
                }

                // 批量执行并获取结果
                List<Object> results = pipeline.syncAndReturnAll();

                // 4. 并行处理每个小车
                for (int i = 0; i < results.size(); i++) {
                    final int carIndex = i + 1;
                    @SuppressWarnings("unchecked")
                    List<String> taskList = (List<String>) results.get(i);

                    carExecutor.submit(() -> {
                        try {
                            if (taskList != null && !taskList.isEmpty()) {
                                // 5. 直接发送最新任务（不反转列表）
                                String cmd = "00" + carIndex;
                                carQueue.sendTask(cmd);
                                System.out.println("[小车] 指令已发送: " + cmd);


                            }
                        } catch (Exception e) {
                            System.err.println("[小车" + carIndex + "] 处理异常: " + e.getMessage());
                        }
                    });
                }

                carExecutor.shutdown();
                carExecutor.awaitTermination(1, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            System.err.println("[小车控制] 系统错误: " + e.getMessage());
        }
    }
    /**
     * 发送导航指令
     */
    private void sendNaviCommand() {
        try {

            NaviNumber=Integer.parseInt(RedisConnector.get("IsNaviOpen"));
            String CarNumberString=RedisConnector.get("CarNumber");
            if (CarNumberString==null) {
                CarNumber=0;
            }
          else
              CarNumber=Integer.parseInt(RedisConnector.get("CarNumber"));
            for(int i=1;i<=CarNumber;i++) {
                List<String> list= RedisConnector.lrAll("Car00"+i+"TaskList");
                if (list==null||list.isEmpty()) {
                String cmd="Car00"+i;
                    naviQueue.sendTask(cmd);
                    lastNaViFinish = IsNaViFinish;// 更新旧值记录
                    System.out.println("[导航器] 指令已发送: " + cmd);
                }
            }
        } catch (Exception e) {
            System.err.println("[导航器] 发送失败: " + e.getMessage());
        }
    }







    public static void main(String[] args) throws Exception {
// 1. 初始化连接（如果非默认配置）
        RedisConnector.initPool("192.168.43.69", 6379, null);

        Control control = new Control();
        // 启动状态检测任务（每秒1次）

        // 2. 启动定时任务（每秒执行一次）
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
                1000,  // 执行间隔（1 秒）
                TimeUnit.MILLISECONDS  // 时间单位改为毫秒
        );






























    }
       /* // 1. 初始化连接（如果非默认配置）
        RedisConnector.initPool("192.168.43.69", 6379, null);





        // 2. 基本操作示例
*//*
        RedisConnector.set("map", "0000000000000000000000000");
        System.out.println(RedisConnector.get("map"));
        String map = RedisConnector.get("map");
        RedisConnector.set("obstacle_map", "0000110000010000000000001");
        System.out.println(RedisConnector.get("obstacle_map"));
        RedisConnector.set("CarID", "0000110000010000000000001");
        System.out.println(RedisConnector.get("CarID"));
        RedisConnector.lpush("CarIdTaskList","1,1","2,2" );
        System.out.println(RedisConnector.lrAll("CarIdTaskList"));*//*

      *//*  TaskProducer taskProducer=new TaskProducer("tcp://192.168.43.69:61616","CarId001");
        taskProducer.sendTask("gogogo");
//        taskProducer.sendTask(map);
        TaskProducer taskProducer1=new TaskProducer("tcp://192.168.43.69:61616","UpdateNavigate");
        taskProducer1.sendTask("Car001");*//*

        // 4. 关闭连接池（通常在应用退出时调用）
        RedisConnector.closePool();
    }*/
}