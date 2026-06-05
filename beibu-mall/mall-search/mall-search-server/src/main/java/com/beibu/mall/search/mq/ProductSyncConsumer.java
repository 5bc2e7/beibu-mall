package com.beibu.mall.search.mq;

import com.beibu.mall.search.entity.ProductDoc;
import com.beibu.mall.search.service.SearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
    topic = "product-sync-topic",
    consumerGroup = "search-consumer-group"
)
public class ProductSyncConsumer implements RocketMQListener<String> {

    private final SearchService searchService;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final int MAX_RETRY_COUNT = 3;
    private static final String RETRY_COUNT_PREFIX = "search:sync:retry:";

    @Override
    public void onMessage(String message) {
        log.info("收到商品同步消息: {}", message);

        String retryKey = RETRY_COUNT_PREFIX + message.hashCode();
        int retryCount = 0;

        try {
            Object countObj = redisTemplate.opsForValue().get(retryKey);
            if (countObj != null) {
                retryCount = Integer.parseInt(countObj.toString());
            }

            if (retryCount >= MAX_RETRY_COUNT) {
                log.error("商品同步消息超过最大重试次数，放弃处理: {}", message);
                redisTemplate.delete(retryKey);
                return;
            }

            ProductSyncMessage syncMessage = objectMapper.readValue(message, ProductSyncMessage.class);

            String action = syncMessage.getAction();
            if ("DELETE".equals(action)) {
                searchService.deleteProduct(syncMessage.getProductId());
                log.info("商品已从 ES 删除: productId={}", syncMessage.getProductId());
            } else {
                ProductDoc doc = convertToProductDoc(syncMessage);
                searchService.syncProduct(doc);
                log.info("商品已同步到 ES: productId={}", syncMessage.getProductId());
            }

            redisTemplate.delete(retryKey);

        } catch (Exception e) {
            retryCount++;
            redisTemplate.opsForValue().set(retryKey, String.valueOf(retryCount), 24, TimeUnit.HOURS);

            log.error("处理商品同步消息失败，重试次数 {}/{}: {}", retryCount, MAX_RETRY_COUNT, message, e);

            throw new RuntimeException("处理商品同步消息失败", e);
        }
    }

    private ProductDoc convertToProductDoc(ProductSyncMessage message) {
        ProductDoc doc = new ProductDoc();
        doc.setId(message.getProductId());
        doc.setCategoryId(message.getCategoryId());
        doc.setCategoryName(message.getCategoryName());
        doc.setName(message.getName());
        doc.setOrigin(message.getOrigin());
        doc.setIsFresh(message.getIsFresh());
        doc.setPriceType(message.getPriceType());
        doc.setDescription(message.getDescription());
        doc.setStatus(message.getStatus());
        doc.setCreateTime(message.getCreateTime());

        if (message.getSkuList() != null && !message.getSkuList().isEmpty()) {
            List<BigDecimal> prices = message.getSkuList().stream()
                .map(ProductSyncMessage.SkuInfo::getPrice)
                .filter(price -> price != null)
                .toList();

            if (!prices.isEmpty()) {
                doc.setMinPrice(prices.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO));
                doc.setMaxPrice(prices.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO));
            }

            doc.setImage(message.getSkuList().get(0).getImg());

            List<ProductDoc.SkuInfo> skuInfoList = message.getSkuList().stream()
                .map(sku -> {
                    ProductDoc.SkuInfo skuInfo = new ProductDoc.SkuInfo();
                    skuInfo.setId(sku.getId());
                    skuInfo.setSpec(sku.getSpec());
                    skuInfo.setPrice(sku.getPrice());
                    skuInfo.setStock(sku.getStock());
                    skuInfo.setImg(sku.getImg());
                    return skuInfo;
                })
                .collect(Collectors.toList());
            doc.setSkuList(skuInfoList);
        }

        return doc;
    }
}
