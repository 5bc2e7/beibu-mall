package com.beibu.mall.order.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 雪花算法 ID 生成器
 *
 * 什么是雪花算法（Snowflake）？
 * 雪花算法是 Twitter 开发的分布式 ID 生成算法。
 * 生成的 ID 是一个 64 位的 long 类型数字，结构如下：
 *
 * 0 | 0000000000 0000000000 0000000000 0000000000 0 | 00000 | 00000 | 000000000000
 * 符号位 |              41位时间戳                      | 5位数据中心 | 5位机器 | 12位序列号
 *
 * 为什么不用数据库自增ID当订单号？
 *
 * 1. 【安全问题】自增ID是连续的（1001, 1002, 1003...）
 *    竞争对手可以通过每天下单来猜你的日订单量
 *    雪花算法生成的ID是不连续的（如 1234567890123456789）
 *
 * 2. 【分布式问题】微服务有多个实例，每个实例都连不同的数据库
 *    如果用自增ID，多个数据库可能生成相同的ID（冲突！）
 *    雪花算法通过"数据中心ID + 机器ID"保证每个实例生成的ID都不一样
 *
 * 3. 【性能问题】自增ID需要每次插入时查询"当前最大ID是多少"
 *    在高并发下，这个操作会成为瓶颈
 *    雪花算法是纯内存计算，不依赖数据库，性能极高（每秒可生成400万个ID）
 *
 * 4. 【信息隐藏】订单号暴露给用户后，用户无法通过ID推算出任何业务信息
 *    雪花算法的时间戳部分可以用于排序，但用户看不出来
 *
 * 5. 【趋势递增】雪花算法生成的ID整体是递增的
 *    对数据库索引友好（B+树索引对递增ID插入效率最高）
 */
@Slf4j
@Component
public class SnowflakeIdGenerator {

    /**
     * 起始时间戳（2024-01-01 00:00:00）
     * 作用：让时间戳部分从0开始计数，延长可用时间
     * 如果用当前时间戳（13位），41位只能用69年
     * 用起始时间戳减去后，可以用更久
     */
    private static final long START_TIMESTAMP = 1704067200000L;

    /** 数据中心ID（0-31，5位） */
    private final long datacenterId;

    /** 机器ID（0-31，5位） */
    private final long workerId;

    /** 序列号（0-4095，12位），同一毫秒内自增 */
    private long sequence = 0L;

    /** 上一次生成ID的时间戳 */
    private long lastTimestamp = -1L;

    /**
     * 构造方法（从环境变量读取配置）
     *
     * 为什么从环境变量读取？
     * 每个服务实例的 datacenterId 和 workerId 必须唯一。
     * 通过环境变量或配置中心（Nacos）配置，确保多实例部署时 ID 不冲突。
     *
     * 环境变量配置示例：
     * - SNOWFLAKE_DATACENTER_ID=1
     * - SNOWFLAKE_WORKER_ID=1
     */
    public SnowflakeIdGenerator(
            @Value("${snowflake.datacenter-id:1}") long datacenterId,
            @Value("${snowflake.worker-id:1}") long workerId) {
        // 校验数据中心ID和机器ID的范围
        if (datacenterId > 31 || datacenterId < 0) {
            throw new IllegalArgumentException("数据中心ID不能大于31或小于0，当前值：" + datacenterId);
        }
        if (workerId > 31 || workerId < 0) {
            throw new IllegalArgumentException("机器ID不能大于31或小于0，当前值：" + workerId);
        }
        this.datacenterId = datacenterId;
        this.workerId = workerId;
    }

    /**
     * 初始化后打印配置信息，方便排查问题
     */
    @PostConstruct
    public void init() {
        log.info("雪花算法 ID 生成器初始化完成，数据中心ID：{}，机器ID：{}", datacenterId, workerId);
    }

    /**
     * 生成下一个ID
     *
     * @return 唯一的ID（long类型）
     */
    public synchronized long nextId() {
        // 1. 获取当前时间戳
        long currentTimestamp = System.currentTimeMillis();

        // 2. 时钟回拨检查
        // 如果当前时间小于上一次生成ID的时间戳，说明系统时钟被回拨了
        // 这种情况会生成重复ID，直接抛异常
        if (currentTimestamp < lastTimestamp) {
            throw new RuntimeException("时钟回拨，拒绝生成ID，当前时间戳：" + currentTimestamp
                    + "，上一次时间戳：" + lastTimestamp);
        }

        // 3. 同一毫秒内的处理
        if (currentTimestamp == lastTimestamp) {
            // 同一毫秒内，序列号自增
            sequence = (sequence + 1) & 4095L; // 4095 = 2^12 - 1
            // 序列号溢出（超过4095），等待下一毫秒
            if (sequence == 0) {
                currentTimestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            // 不同毫秒，序列号重置为0
            sequence = 0L;
        }

        // 4. 更新上一次生成ID的时间戳
        lastTimestamp = currentTimestamp;

        // 5. 组装ID
        // 时间戳部分：41位
        long timestampPart = (currentTimestamp - START_TIMESTAMP) << 22;
        // 数据中心部分：5位
        long datacenterPart = datacenterId << 17;
        // 机器部分：5位
        long workerPart = workerId << 12;
        // 序列号部分：12位
        long sequencePart = sequence;

        return timestampPart | datacenterPart | workerPart | sequencePart;
    }

    /**
     * 等待下一毫秒
     *
     * 为什么用 Thread.sleep 而不是忙等待？
     * 忙等待（while 循环）会 100% 占用 CPU，浪费资源。
     * Thread.sleep(1) 让出 CPU 时间片，等待约 1 毫秒后再检查。
     */
    private long waitNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            try {
                // 睡眠 1 毫秒，让出 CPU
                Thread.sleep(1);
            } catch (InterruptedException e) {
                // 如果被中断，记录日志但继续运行
                Thread.currentThread().interrupt();
                log.warn("等待下一毫秒时被中断");
            }
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }

    /**
     * 生成订单号（字符串格式）
     *
     * 为什么转成字符串？
     * 1. 前端 JavaScript 的 long 类型精度不够，超过 2^53 会丢失精度
     * 2. 字符串格式更容易展示和传输
     * 3. 可以添加前缀，如"ORDER" + ID，更易读
     */
    public String nextOrderNo() {
        return String.valueOf(nextId());
    }
}
