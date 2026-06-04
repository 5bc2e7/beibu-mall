package com.beibu.mall.seckill.config;

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

@Configuration
@RefreshScope
@Slf4j
public class SentinelConfig {

    @Value("${seckill.sentinel.qps:200}")
    private int seckillQps;

    @PostConstruct
    public void initFlowRules() {
        refreshRules();
    }

    @EventListener(RefreshEvent.class)
    public void onRefresh(RefreshEvent event) {
        log.info("检测到Nacos配置变更，刷新Sentinel限流规则，新QPS: {}", seckillQps);
        refreshRules();
    }

    public void refreshRules() {
        List<FlowRule> rules = new ArrayList<>();

        FlowRule seckillRule = new FlowRule();
        seckillRule.setResource("seckill");
        seckillRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        seckillRule.setCount(seckillQps);
        seckillRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        rules.add(seckillRule);

        FlowRuleManager.loadRules(rules);
        log.info("Sentinel限流规则已更新，资源: seckill, QPS: {}", seckillQps);
    }
}
