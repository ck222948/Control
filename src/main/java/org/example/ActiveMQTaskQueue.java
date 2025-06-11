package org.example;

import org.apache.activemq.ActiveMQConnectionFactory;
import com.google.gson.Gson;

import javax.jms.*;
import java.util.concurrent.TimeoutException;

/**
 * ActiveMQ 任务队列工具类
 * 功能：消息发送、消费、队列声明、异常处理
 */
public class ActiveMQTaskQueue {
    private final Connection connection;// ActiveMQ连接对象
    private final Session session;// 会话对象（生产/消费消息）
    private final Gson gson = new Gson();// JSON序列化工具
    private final String queueName;// 队列名称

    /**
     * 初始化连接和队列
     * @param brokerUrl  ActiveMQ服务器地址
     * @param queueName  队列名称
     */
    public ActiveMQTaskQueue(String brokerUrl, String queueName) throws JMSException {
        ConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);// 1. 创建连接工厂
        this.connection = factory.createConnection();// 2. 创建连接并启动
        this.connection.start();//启动
        this.session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);//  3. 创建会话（非事务，自动ACK）
        this.queueName = queueName;// 保存队列名
    }

    /**
     * 发送任务消息（自动转为JSON）
     * @param task 可序列化的任务对象
     */
    public <T> void sendTask(T task) throws JMSException {
        // 1. 创建指向队列的Producer
        Destination destination = session.createQueue(queueName);
        MessageProducer producer = session.createProducer(destination);
        // 2. 设置消息持久化
        producer.setDeliveryMode(DeliveryMode.PERSISTENT);
        // 3. 对象转JSON并发送
        String message = gson.toJson(task);
        TextMessage textMessage = session.createTextMessage(message);
        producer.send(textMessage);
        //完成（测试）并释放
//        System.out.println(" [x] Sent: " + message);
        producer.close();
    }

    /**
     * 启动消费者（同步监听）
     * @param taskHandler 任务处理回调接口
     */
    public <T> void startConsumer(Class<T> taskType, TaskHandler<T> taskHandler) throws JMSException {
       //创建指向目的队列的消费者
        Destination destination = session.createQueue(queueName);
        MessageConsumer consumer = session.createConsumer(destination);
        // 2. 设置异步监听器
        consumer.setMessageListener(message -> {
            try {

                if (message instanceof TextMessage) {
                    // 3. 提取JSON并反序列化
                    TextMessage textMessage = (TextMessage) message;
                    String json = textMessage.getText();
                    T task = gson.fromJson(json, taskType);

                    // 处理任务
                    taskHandler.handle(task);

                    // ActiveMQ 的 AUTO_ACKNOWLEDGE 模式会自动确认消息
                }
            } catch (Exception e) {
                System.err.println(" [!] Task failed: " + e.getMessage());
                // 在 AUTO_ACKNOWLEDGE 模式下无法手动 NACK，需要特殊处理
            }
        });

        System.out.println(" [*] Waiting for messages...");
    }

    /**
     * 关闭连接
     */
    public void close() throws JMSException {
        session.close();
        connection.close();
    }

    /**
     * 任务处理回调接口
     */
    public interface TaskHandler<T> {
        void handle(T task) throws Exception;
    }

    // ==================== 使用示例 ====================
    public static void main(String[] args) throws Exception {
        // 1. 初始化队列（队列名: "task_queue"）
        // ActiveMQ 默认连接地址为 tcp://localhost:61616
        ActiveMQTaskQueue taskQueue = new ActiveMQTaskQueue("tcp://dk10061fo3371.vicp.fun:13074", "UpdateCar01");

        // 2. 发送任务
        taskQueue.sendTask(new MyTask("Process data", 3));

        // 3. 启动消费者
        taskQueue.startConsumer(MyTask.class, task -> {
            System.out.println(" [x] Processing: " + task.description);
            Thread.sleep(task.duration * 1000L); // 模拟耗时任务
            System.out.println(" [x] Done: " + task.description);
        });

        // 4. 实际应用中保持运行（这里简单示例，暂停10秒）
        Thread.sleep(10000);
        taskQueue.close();
    }

    // 示例任务类（需可序列化）
    static class MyTask {
        String description;
        int duration; // seconds

        public MyTask(String description, int duration) {
            this.description = description;
            this.duration = duration;
        }
    }
}