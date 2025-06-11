package org.example;
import redis.clients.jedis.*;
import redis.clients.jedis.args.BitOP;
import redis.clients.jedis.exceptions.JedisConnectionException;
import java.util.List;
import java.util.Map;

/**
 * Redis 连接管理工具类（基于 Jedis）
 * 功能：连接池管理、数据读写、事务支持、发布订阅等
 */
public class RedisConnector{
    private static JedisPool jedisPool;
    private static final int MAX_TOTAL = 128;      // 最大连接数
    private static final int MAX_IDLE = 32;        // 最大空闲连接
    private static final int TIMEOUT = 2000;       // 连接超时（毫秒）

    // 静态初始化连接池
    static {
        initPool("172.17.0.2", 6379, null);
    }

    /**
     * 初始化连接池
     * @param host     Redis服务器地址
     * @param port     端口
     * @param password 密码（无密码传null）
     */
    public static void initPool(String host, int port, String password) {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(MAX_TOTAL);
        config.setMaxIdle(MAX_IDLE);
        config.setTestOnBorrow(true); // 验证连接可用性

        jedisPool = new JedisPool(config, host, port, TIMEOUT, password);
    }

    /**
     * 获取Jedis连接 看看（用完必须调用close()归还）
     */
    public static Jedis getConnection() {
        try {
            return jedisPool.getResource();
        } catch (JedisConnectionException e) {
            throw new RuntimeException("Redis连接失败: " + e.getMessage(), e);
        }
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
    public static void closePool() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }

    // ------------ 测试用例 ------------
    public static void main(String[] args) {
        // 1. 初始化连接
        RedisConnector.initPool("192.168.43.69", 6379, null);

        // 2. Bitmap 操作示例
        String bitmapKey = "map"; // 用户签到位图

        // 设置第1天和第3天签到（偏移量从0开始）

for (long i = 0; i < 100; i++) {
    setBit(bitmapKey, i, false); // 第1天
}

        System.out.println(RedisConnector.getBitmapAsBinary("map")); // 输出: true







        // 4. 关闭连接池
        RedisConnector.closePool();
    }
}

