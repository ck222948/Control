package org.example;

import javax.jms.JMSException;

public class TaskProducer {
    private final ActiveMQTaskQueue taskQueue;

    // 初始化时传入ActiveMQ连接信息
    public TaskProducer(String brokerUrl, String queueName) throws JMSException {
        this.taskQueue = new ActiveMQTaskQueue(brokerUrl, queueName);
    }

    /**
     * 发送任务到队列
     * @param task 任务对象（需可序列化）
     */
    public <T> void sendTask(T task) throws JMSException {

            taskQueue.sendTask(task);

    }

    // 关闭连接（可选）
    public void close() throws JMSException {
        taskQueue.close();
    }
}