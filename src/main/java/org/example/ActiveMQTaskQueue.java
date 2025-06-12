package org.example;

import com.google.gson.Gson;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.*;
import java.util.concurrent.*;

public class ActiveMQTaskQueue {
    private final String brokerUrl; // ActiveMQ Broker 的 URL
    private final String queueName; // 队列名称
    private final Gson gson = new Gson(); // Gson 用于对象和 JSON 字符串之间的转换
    @Deprecated
    private static volatile boolean isReconnecting = false; // 标记是否正在重连

    private Connection connection; // ActiveMQ 连接
    private Session session; // JMS 会话

    private static final int MAX_RETRIES = 5; // 最大重试次数
    private static final int RETRY_DELAY_MS = 2000; // 重试之间的延迟时间（单位：毫秒）

    // 构造方法：初始化队列连接并尝试连接到 ActiveMQ
    public ActiveMQTaskQueue(String brokerUrl, String queueName) throws JMSException {
        this.brokerUrl = brokerUrl;
        this.queueName = queueName;
        tryConnect(); // 调用连接方法，尝试连接
    }

    /**
     * 封装连接尝试（带重试机制）
     * 如果连接失败，会重试多次
     */
    private synchronized void tryConnect() throws JMSException {
        int attempt = 0; // 当前尝试次数
        boolean connected = false; // 连接状态
        SystemStatus.isMQReconnecting = true; // 开始重连，设置标志为 true
        // 最大尝试次数控制
        while (attempt < MAX_RETRIES && !connected) {
            attempt++;
            ExecutorService executor = Executors.newSingleThreadExecutor(); // 创建单线程的线程池
            Future<Boolean> future = executor.submit(() -> {
                try {
                    // 创建 ActiveMQ 连接工厂，并开始连接
                    ConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
                    connection = factory.createConnection();
                    connection.start(); // 启动连接
                    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE); // 创建会话
                    return true; // 连接成功
                } catch (JMSException e) {
                    throw e; // 抛出连接异常
                }
            });

            try {
                // 等待连接完成，设置超时时间为 5 秒
                boolean success = future.get(3, TimeUnit.SECONDS);
                if (success) {
                    System.out.println(" [√] 第 " + attempt + " 次连接成功");
                    connected = true; // 设置为已连接
                }
            } catch (TimeoutException e) {
                System.err.println(" [×] 第 " + attempt + " 次连接超时（超过5秒）");
                future.cancel(true); // 超时则取消该线程
            } catch (Exception e) {
                System.err.println(" [×] 第 " + attempt + " 次连接失败: " + e.getMessage());
            } finally {
                executor.shutdownNow(); // 强制关闭线程池
            }

            // 如果未连接成功，等待一段时间后继续重试
            if (!connected) {
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ignored) {}
            }
        }
        SystemStatus.isMQReconnecting = false; // 重连完成，标志设置为 false

        // 如果重试超过最大次数仍未成功，则退出程序
        if (!connected) {
            System.err.println(" [×] 无法建立连接，超过最大尝试次数。系统将停止重试并退出...");
            System.exit(1); // 程序退出
        }
    }

    /**
     * 发送任务消息到队列
     * @param task 任务对象
     * @param <T> 任务类型
     */
    public <T> void sendTask(T task) throws JMSException {
        try {
            // 创建队列目标
            Destination destination = session.createQueue(queueName);
            MessageProducer producer = session.createProducer(destination); // 创建生产者
            producer.setDeliveryMode(DeliveryMode.PERSISTENT); // 设置消息持久化
            String message = gson.toJson(task); // 将任务对象转换为 JSON 字符串
            TextMessage textMessage = session.createTextMessage(message); // 创建消息
            producer.send(textMessage); // 发送消息
            producer.close(); // 关闭生产者
        } catch (JMSException e) {
            System.err.println(" [!] 发送消息失败，尝试重连...");
            tryConnect(); // 连接失败后重试连接
            sendTask(task); // 递归重试发送任务
        }
    }

    /**
     * 启动消费者，消费队列中的消息
     * @param taskType 任务的类型
     * @param taskHandler 消息处理逻辑
     * @param <T> 任务类型
     */
    public <T> void startConsumer(Class<T> taskType, TaskHandler<T> taskHandler) throws JMSException {
        // 创建队列目标
        Destination destination = session.createQueue(queueName);
        MessageConsumer consumer = session.createConsumer(destination); // 创建消费者

        // 设置消息监听器
        consumer.setMessageListener(message -> {
            try {
                // 判断是否为 TextMessage 类型
                if (message instanceof TextMessage) {
                    TextMessage textMessage = (TextMessage) message;
                    String json = textMessage.getText(); // 获取消息内容
                    T task = gson.fromJson(json, taskType); // 将 JSON 字符串转换为任务对象
                    taskHandler.handle(task); // 调用任务处理器处理任务
                }
            } catch (Exception e) {
                System.err.println(" [!] 任务处理失败: " + e.getMessage());
                // 处理异常，比如记录日志等
            }
        });

        System.out.println(" [*] 消费者已启动，等待消息...");
    }

    /**
     * 关闭连接和会话
     */
    public void close() throws JMSException {
        if (session != null) session.close(); // 关闭会话
        if (connection != null) connection.close(); // 关闭连接
    }

    // 任务处理接口，消费者调用此方法来处理接收到的任务
    public interface TaskHandler<T> {
        void handle(T task) throws Exception;
    }

    // 示例：任务对象
    static class MyTask {
        String description; // 任务描述
        int duration; // 任务执行时长（秒）

        public MyTask(String description, int duration) {
            this.description = description;
            this.duration = duration;
        }
    }

    // 主程序示例
    public static void main(String[] args) throws Exception {
        // 创建 ActiveMQTaskQueue 实例
        ActiveMQTaskQueue taskQueue = new ActiveMQTaskQueue("tcp://192.168.43.69:61616", "UpdateCar");

        // 发送任务
        try {
            taskQueue.sendTask("001"); // 发送任务消息
        } catch (Exception e) {
            System.err.println("发送任务失败：" + e.getMessage());
        }



        taskQueue.close(); // 关闭连接
    }
}
