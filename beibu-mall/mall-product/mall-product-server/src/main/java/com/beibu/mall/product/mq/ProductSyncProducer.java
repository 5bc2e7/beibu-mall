package com.beibu.mall.product.mq;

import com.beibu.mall.product.entity.Sku;
import com.beibu.mall.product.entity.Spu;
import com.beibu.mall.product.mapper.CategoryMapper;
import com.beibu.mall.product.entity.Category;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商品同步消息生产者
 *
 * 大白话：商品数据变更时，发消息通知搜索服务更新 ES 索引
 *
 * 为什么用 MQ 通知而不是直接调用搜索服务？
 * 1. 解耦：商品服务不需要知道搜索服务的存在
 * 2. 异步：商品服务发完消息就返回，不用等 ES 处理完
 * 3. 可靠：消息存在 MQ 里，即使搜索服务挂了，消息也不会丢
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductSyncProducer {

    private static final String TOPIC = "product-sync-topic";
    private static final int TIMEOUT_MS = 3000;

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;
    private final CategoryMapper categoryMapper;

    /**
     * 发送商品同步消息（新增/更新）
     *
     * @param spu 商品 SPU
     * @param skuList 商品 SKU 列表
     */
    public void sendProductSyncMessage(Spu spu, List<Sku> skuList) {
        try {
            // 查询分类名称
            Category category = categoryMapper.selectById(spu.getCategoryId());
            String categoryName = category != null ? category.getName() : null;

            // 构建消息体
            Map<String, Object> messageBody = buildMessageBody(spu, skuList, categoryName, "SAVE");

            // 转换为 JSON 字符串
            String json = objectMapper.writeValueAsString(messageBody);

            // 发送消息
            Message<String> message = MessageBuilder.withPayload(json).build();
            rocketMQTemplate.syncSend(TOPIC, message, TIMEOUT_MS);

            log.info("商品同步消息已发送: spuId={}, action=SAVE", spu.getId());
        } catch (Exception e) {
            // 消息发送失败不影响主流程
            // 搜索数据可以通过定时任务补偿
            log.error("商品同步消息发送失败: spuId={}", spu.getId(), e);
        }
    }

    /**
     * 发送商品删除消息
     *
     * @param spuId 商品 SPU ID
     */
    public void sendProductDeleteMessage(Long spuId) {
        try {
            Map<String, Object> messageBody = Map.of(
                "action", "DELETE",
                "productId", spuId
            );

            String json = objectMapper.writeValueAsString(messageBody);
            Message<String> message = MessageBuilder.withPayload(json).build();
            rocketMQTemplate.syncSend(TOPIC, message, TIMEOUT_MS);

            log.info("商品删除消息已发送: spuId={}", spuId);
        } catch (Exception e) {
            log.error("商品删除消息发送失败: spuId={}", spuId, e);
        }
    }

    /**
     * 构建消息体
     */
    private Map<String, Object> buildMessageBody(Spu spu, List<Sku> skuList, String categoryName, String action) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("action", action);
        body.put("productId", spu.getId());
        body.put("categoryId", spu.getCategoryId());
        body.put("categoryName", categoryName);
        body.put("name", spu.getName());
        body.put("origin", spu.getOrigin());
        body.put("isFresh", spu.getIsFresh());
        body.put("priceType", spu.getPriceType());
        body.put("description", spu.getDescription());
        body.put("status", spu.getStatus());
        body.put("createTime", spu.getCreateTime());

        // 构建 SKU 列表
        if (skuList != null && !skuList.isEmpty()) {
            List<Map<String, Object>> skuDataList = skuList.stream()
                .map(sku -> {
                    Map<String, Object> skuData = new java.util.HashMap<>();
                    skuData.put("id", sku.getId());
                    skuData.put("spec", sku.getSpec());
                    skuData.put("price", sku.getPrice());
                    skuData.put("stock", sku.getStock());
                    skuData.put("img", sku.getImg());
                    return skuData;
                })
                .collect(Collectors.toList());
            body.put("skuList", skuDataList);
        }

        return body;
    }
}
