package com.beibu.mall.order.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.endpoint.event.RefreshEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 限流配置
 *
 * 这个配置类做了什么？
 * 1. 启动时加载限流规则
 * 2. 监听 Nacos 配置变更，动态刷新规则
 *
 * Sentinel 的工作原理：
 * 1. 我们定义"资源"（比如 createOrder）
 * 2. 我们定义"规则"（比如每秒最多 50 个请求）
 * 3. Sentinel 会自动拦截请求，如果超过规则就拒绝
 *
 * @RefreshScope + @Value 一起使用：
 * - @Value 从配置文件读取值
 * - @RefreshScope 让这个值能在运行时动态更新（配合 Nacos 配置中心）
 */
@Configuration
@RefreshScope
@Slf4j
public class SentinelConfig {

    /**
     * 下单接口的 QPS 限制
     *
     * @Value("${order.sentinel.qps:50}") 的意思是：
     * - 从配置文件读取 order.sentinel.qps 这个值
     * - 如果没有配置，就用默认值 50
     *
     * 为什么用 @RefreshScope？
     * - 当你在 Nacos 配置中心改了这个值，服务会自动读取新值
     * - 不需要重启服务！
     */
    @Value("${order.sentinel.qps:50}")
    private int orderQps;

    /**
     * 查询订单接口的 QPS 限制（查询通常比写入允许更高的 QPS）
     */
    @Value("${order.sentinel.query-qps:100}")
    private int orderQueryQps;

    @Value("${order.sentinel.cancel-qps:30}")
    private int orderCancelQps;

    /**
     * 服务启动时自动执行，加载限流规则
     *
     * @PostConstruct = Spring 创建完这个 Bean 后自动调用
     * 可以理解为"初始化方法"
     */
    @PostConstruct
    public void initFlowRules() {
        refreshRules();
    }

    /**
     * 监听 Nacos 配置变更事件
     *
     * 当你在 Nacos 控制台修改配置并发布时，Spring 会发送 RefreshEvent
     * 这个方法会捕获事件，重新加载限流规则
     */
    @EventListener(RefreshEvent.class)
    public void onRefresh(RefreshEvent event) {
        log.info("检测到Nacos配置变更，刷新Sentinel限流规则，下单QPS: {}, 查询QPS: {}, 取消QPS: {}", orderQps, orderQueryQps, orderCancelQps);
        refreshRules();
    }

    /**
     * 刷新限流规则的核心方法
     *
     * FlowRule 的属性说明：
     * - resource: 资源名，要和 @SentinelResource 的 value 对应
     * - grade: 限流类型，FLOW_GRADE_QPS = 按 QPS 限流（还有 FLOW_GRADE_THREAD = 按线程数）
     * - count: 阈值，比如 50 表示每秒最多 50 个请求
     * - controlBehavior: 超过阈值时的处理方式
     *   - CONTROL_BEHAVIOR_DEFAULT = 快速失败（直接拒绝）
     *   - CONTROL_BEHAVIOR_WARM_UP = 预热（慢慢放开流量，防止冷启动时被压垮）
     *   - CONTROL_BEHAVIOR_RATE_LIMITER = 匀速排队（请求排队等待）
     */
    public void refreshRules() {
        List<FlowRule> rules = new ArrayList<>();

        // 下单接口限流：每秒最多 orderQps 个请求
        FlowRule orderRule = new FlowRule();
        orderRule.setResource("createOrder");
        orderRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        orderRule.setCount(orderQps);
        orderRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        rules.add(orderRule);

        // 查询订单接口限流：每秒最多 orderQueryQps 个请求
        FlowRule queryRule = new FlowRule();
        queryRule.setResource("queryOrder");
        queryRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        queryRule.setCount(orderQueryQps);
        queryRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        rules.add(queryRule);

        // 取消订单接口限流：每秒最多 orderCancelQps 个请求
        FlowRule cancelRule = new FlowRule();
        cancelRule.setResource("cancelOrder");
        cancelRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        cancelRule.setCount(orderCancelQps);
        cancelRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        rules.add(cancelRule);

        FlowRuleManager.loadRules(rules);
        log.info("Sentinel限流规则已更新 - createOrder: {} QPS, queryOrder: {} QPS, cancelOrder: {} QPS", orderQps, orderQueryQps, orderCancelQps);
    }
}
