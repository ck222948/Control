package org.example;

import redis.clients.jedis.*;
import redis.clients.jedis.args.BitOP;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RedisConnector {
    private static JedisPool jedisPool;
    private static final int MAX_TOTAL = 128;      // 最大连接数

    private static final int MAX_IDLE = 32;        // 最大空闲连接
    private static final int TIMEOUT = 2000;       // 连接超时（毫秒）

    // 重连机制配置
    private static final int RECONNECT_INTERVAL = 2000; // 重连间隔(毫秒)
    private static final int MAX_RETRY_TIMES = 5;       // 最大重试次数
    @Deprecated
    public static volatile boolean isRedisReconnecting = false;  // 不再使用，仅保留兼容性

    // 静态初始化连接池
    static {
        initPool("192.168.43.69", 6379, null);  // 默认连接配置
    }

    // 初始化连接池
    public static void initPool(String host, int port, String password) {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(MAX_TOTAL);
        config.setMaxIdle(MAX_IDLE);
        config.setTestOnBorrow(false); // 禁用借出时测试（提升性能）

        jedisPool = new JedisPool(config, host, port, TIMEOUT, password);

        // 测试连接池是否正常
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.ping();  // 测试连接
            System.out.println("Redis连接池初始化成功");
        } catch (Exception e) {
            System.err.println("Redis连接池初始化失败: " + e.getMessage());
            startReconnectThread("192.168.43.69", 6379, null);  // 启动重连线程
        }
    }

    // 获取Jedis连接，重连机制
    public static Jedis getConnection() {
        int retryCount = 0;

        while (retryCount <= MAX_RETRY_TIMES) {
            try {
                if (jedisPool == null || jedisPool.isClosed()) {
                    throw new JedisConnectionException("连接池未初始化或已关闭");
                }

                Jedis jedis = jedisPool.getResource();
                jedis.ping();  // 简单测试连接是否有效
                return jedis;

            } catch (JedisConnectionException e) {
                retryCount++;
                System.err.println("获取Redis连接失败，尝试重连(" + retryCount + "/" + MAX_RETRY_TIMES + ")");

                if (retryCount <= MAX_RETRY_TIMES) {
                    // 启动异步重连线程
                    if (!SystemStatus.isRedisReconnecting) {
                        startReconnectThread("192.168.43.69", 6379, null);  // 启动重连
                    }

                    try {
                        TimeUnit.MILLISECONDS.sleep(RECONNECT_INTERVAL);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    throw new RuntimeException("Redis连接失败，已达到最大重试次数", e);
                }
            }
        }
        throw new RuntimeException("无法获取Redis连接");
    }

    // 启动异步重连线程
    private static synchronized void startReconnectThread(String host, int port, String password) {
        if (!SystemStatus.isRedisReconnecting) {
            System.out.println("重连任务正在进行中，跳过此次重连");
            return;
        }

        SystemStatus.isRedisReconnecting = true;
        new Thread(() -> {
            System.out.println("启动Redis重连线程...");
            int attempt = 0;
            boolean success = false;

            while (attempt < MAX_RETRY_TIMES && !success) {
                attempt++;
                try {
                    System.out.println("尝试重新连接Redis(" + attempt + "/" + MAX_RETRY_TIMES + ")");

                    // 关闭旧连接池
                    if (jedisPool != null && !jedisPool.isClosed()) {
                        jedisPool.close();
                    }

                    // 创建新连接池
                    JedisPoolConfig config = new JedisPoolConfig();
                    config.setMaxTotal(MAX_TOTAL);
                    config.setMaxIdle(MAX_IDLE);
                    config.setTestOnBorrow(false);

                    jedisPool = new JedisPool(config, host, port, TIMEOUT, password);

                    // 测试新连接
                    try (Jedis jedis = jedisPool.getResource()) {
                        jedis.ping();
                        success = true;
                        System.out.println("Redis重连成功");
                    }
                } catch (Exception e) {
                    System.err.println("Redis重连失败: " + e.getMessage());
                    if (attempt < MAX_RETRY_TIMES) {
                        try {
                            TimeUnit.MILLISECONDS.sleep(RECONNECT_INTERVAL);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }

            SystemStatus.isRedisReconnecting = false;
            if (!success) {
                System.err.println("Redis重连失败，已达到最大重试次数");
            }
        }).start();
    }


    // ------------ 基础数据操作 ------------

    /** 设置字符串键值 */
    public static void set(String key, String value) {
        try (Jedis jedis = getConnection()) {
            jedis.set(key, value);
        }
    }

    /** 获取字符串值 */
    public static String get(String key) {
        try (Jedis jedis = getConnection()) {
            return jedis.get(key);
        }
    }

    /** 设置哈希表字段 */
    public static void hset(String key, String field, String value) {
        try (Jedis jedis = getConnection()) {
            jedis.hset(key, field, value);
        }
    }

    /** 获取哈希表字段值 */
    public static String hget(String key, String field) {
        try (Jedis jedis = getConnection()) {
            return jedis.hget(key, field);
        }
    }

    /** 获取整个哈希表 */
    public static Map<String, String> hgetAll(String key) {
        try (Jedis jedis = getConnection()) {
            return jedis.hgetAll(key);
        }
    }

    // ------------ 高级功能 ------------

    /** 执行事务（示例） */
    public static List<Object> executeTransaction() {
        try (Jedis jedis = getConnection()) {
            Transaction tx = jedis.multi();
            tx.set("tx_key1", "value1");
            tx.set("tx_key2", "value2");
            return tx.exec();
        }
    }
// ------------ 列表(List)操作 ------------

    /** 从列表左侧插入元素 */
    public static long lpush(String key, String... values) {
        try (Jedis jedis = getConnection()) {
            return jedis.lpush(key, values);
        }
    }

    /** 从列表右侧插入元素 */
    public static long rpush(String key, String... values) {
        try (Jedis jedis = getConnection()) {
            return jedis.rpush(key, values);
        }
    }

    /** 获取列表指定范围内的元素 */
    public static List<String> lrange(String key, long start, long stop) {
        try (Jedis jedis = getConnection()) {
            return jedis.lrange(key, start, stop);
        }
    }
    /** 获取整个列表**/
    public static List<String> lrAll(String key) {
        try (Jedis jedis = getConnection()) {
            return jedis.lrange(key,0, -1);
        }

    }

    /** 从列表左侧弹出元素 */
    public static String lpop(String key) {
        try (Jedis jedis = getConnection()) {
            return jedis.lpop(key);
        }
    }

    /** 从列表右侧弹出元素 */
    public static String rpop(String key) {
        try (Jedis jedis = getConnection()) {
            return jedis.rpop(key);
        }
    }

    /** 获取列表长度 */
    public static long llen(String key) {
        try (Jedis jedis = getConnection()) {
            return jedis.llen(key);
        }
    }

    /** 修剪列表，只保留指定范围内的元素 */
    public static String ltrim(String key, long start, long stop) {
        try (Jedis jedis = getConnection()) {
            return jedis.ltrim(key, start, stop);
        }
    }
     /**清空数据库**/
    public static void flushDB() {
        try (Jedis jedis =getConnection()) {
            jedis.flushDB();
            System.out.println("Redis 当前数据库已清空");
        }
    }


    // ------------ Bitmap 位图操作 ------------
    /**
     * 设置位图中某一位的值
     * @param key    键名
     * @param offset 偏移量（从0开始）
     * @param value  布尔值（true=1, false=0）
     * @return 该位原来的值（0或1）
     */
    public static boolean setBit(String key, long offset, boolean value) {
        try (Jedis jedis = getConnection()) {
            return jedis.setbit(key, offset, value);
        }
    }

    /**
     * 获取位图中某一位的值
     * @param key    键名
     * @param offset 偏移量
     * @return true=1, false=0
     */
    public static boolean getBit(String key, long offset) {
        try (Jedis jedis = getConnection()) {
            return jedis.getbit(key, offset);
        }
    }

    /**
     * 统计位图中值为1的位数
     * @param key 键名
     * @return 1的个数
     */
    public static long bitCount(String key) {
        try (Jedis jedis = getConnection()) {
            return jedis.bitcount(key);
        }
    }

    /**
     * 执行位运算（AND/OR/XOR/NOT）
     * @param operation 操作类型（"AND"/"OR"/"XOR"/"NOT"）
     * @param destKey   结果存储键
     * @param srcKeys   源键列表
     * @return 结果位图的字节长度
     */
    public static long bitOp(String operation, String destKey, String... srcKeys) {
        try (Jedis jedis = getConnection()) {
            BitOP op;
            switch (operation.toUpperCase()) {
                case "AND": op = BitOP.AND; break;
                case "OR":  op = BitOP.OR;  break;
                case "XOR": op = BitOP.XOR; break;
                case "NOT": op = BitOP.NOT; break;
                default: throw new IllegalArgumentException("无效的位操作类型");
            }
            return jedis.bitop(op, destKey, srcKeys);
        }
    }
    /**
     * 将位图内容转换为二进制字符串（如 "01100001"）
     * @param key 位图键名
     * @return 二进制字符串（按字节对齐）
     */
    public static String getBitmapAsBinary(String key) {
        try (Jedis jedis = getConnection()) {
            byte[] bytes = jedis.get(key.getBytes());
            if (bytes == null) return null;

            StringBuilder binaryStr = new StringBuilder();
            for (byte b : bytes) {
                for (int i = 7; i >= 0; i--) {
                    binaryStr.append((b >> i) & 1);
                }
            }
            return binaryStr.toString();
        }
    }
    //其他操作
    /** 发布消息到频道 */
    public static void publish(String channel, String message) {
        try (Jedis jedis = getConnection()) {
            jedis.publish(channel, message);
        }
    }

    /** 关闭连接池 */
    public static synchronized void closePool() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }
    /**
     * 从列表中移除元素
     * @param key 列表键名
     * @param count 移除数量（0表示全部匹配项）
     * @param value 要移除的值
     * @return 实际移除的元素数量
     */
    public static long lrem(String key, long count, String value) {
        try (Jedis jedis = getConnection()) {
            return jedis.lrem(key, count, value);
        }
    }

    // ------------ 测试用例 ------------
    public static void main(String[] args) {
        // 4. 关闭连接池
        RedisConnector.closePool();
    }
}

