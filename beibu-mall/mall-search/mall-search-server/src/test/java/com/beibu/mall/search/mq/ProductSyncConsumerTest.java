package com.beibu.mall.search.mq;

import com.beibu.mall.search.entity.ProductDoc;
import com.beibu.mall.search.service.SearchService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductSyncConsumerTest {

    @Mock
    private SearchService searchService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private ProductSyncConsumer consumer;

    private static final String RETRY_COUNT_PREFIX = "search:sync:retry:";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void onMessage_saveAction_shouldCallSyncProduct() {
        // Given: a valid save message
        String message = buildSaveMessage(1L, "北海大虾");
        String retryKey = RETRY_COUNT_PREFIX + message.hashCode();

        when(valueOperations.get(retryKey)).thenReturn(null);

        // When
        consumer.onMessage(message);

        // Then
        ArgumentCaptor<ProductDoc> docCaptor = ArgumentCaptor.forClass(ProductDoc.class);
        verify(searchService).syncProduct(docCaptor.capture());

        ProductDoc captured = docCaptor.getValue();
        assertEquals(1L, captured.getId());
        assertEquals("北海大虾", captured.getName());
        assertEquals("北海", captured.getOrigin());
        assertEquals(1, captured.getIsFresh());

        verify(searchService, never()).deleteProduct(anyLong());
        verify(redisTemplate).delete(retryKey);
    }

    @Test
    void onMessage_deleteAction_shouldCallDeleteProduct() {
        // Given: a valid delete message
        String message = "{\"action\":\"DELETE\",\"productId\":42}";
        String retryKey = RETRY_COUNT_PREFIX + message.hashCode();

        when(valueOperations.get(retryKey)).thenReturn(null);

        // When
        consumer.onMessage(message);

        // Then
        verify(searchService).deleteProduct(42L);
        verify(searchService, never()).syncProduct(any());
        verify(redisTemplate).delete(retryKey);
    }

    @Test
    void onMessage_invalidJson_shouldThrowRuntimeException() {
        // Given: invalid JSON
        String message = "not-valid-json{{{";
        String retryKey = RETRY_COUNT_PREFIX + message.hashCode();

        when(valueOperations.get(retryKey)).thenReturn(null);

        // When & Then
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> consumer.onMessage(message));
        assertTrue(ex.getMessage().contains("处理商品同步消息失败"));

        // Should increment retry count in Redis
        verify(valueOperations).set(eq(retryKey), eq("1"), eq(24L), eq(TimeUnit.HOURS));

        // Should NOT call searchService at all
        verify(searchService, never()).syncProduct(any());
        verify(searchService, never()).deleteProduct(anyLong());
    }

    @Test
    void onMessage_retryExceeded_shouldSkipProcessing() {
        // Given: retry count already at MAX (3)
        String message = buildSaveMessage(1L, "北海大虾");
        String retryKey = RETRY_COUNT_PREFIX + message.hashCode();

        when(valueOperations.get(retryKey)).thenReturn(3);

        // When
        consumer.onMessage(message);

        // Then: should NOT call searchService at all
        verify(searchService, never()).syncProduct(any());
        verify(searchService, never()).deleteProduct(anyLong());

        // Should delete the retry key
        verify(redisTemplate).delete(retryKey);
    }

    @Test
    void onMessage_firstRetry_shouldStartFromZero() {
        // Given: Redis returns null (first attempt, key doesn't exist)
        String message = buildDeleteMessage(99L);
        String retryKey = RETRY_COUNT_PREFIX + message.hashCode();

        when(valueOperations.get(retryKey)).thenReturn(null);

        // When
        consumer.onMessage(message);

        // Then: should process normally (retryCount starts at 0, which is < 3)
        verify(searchService).deleteProduct(99L);
        verify(redisTemplate).delete(retryKey);
    }

    @Test
    void onMessage_saveWithSkus_shouldConvertPricesCorrectly() {
        // Given: save message with multiple SKUs having different prices
        String message = "{\"action\":\"SAVE\",\"productId\":10,\"categoryId\":1,\"categoryName\":\"海鲜\"," +
                "\"name\":\"大闸蟹\",\"origin\":\"阳澄湖\",\"isFresh\":1,\"priceType\":0," +
                "\"description\":\"鲜活大闸蟹\",\"status\":1," +
                "\"skuList\":[" +
                "{\"id\":1,\"spec\":\"4两\",\"price\":128.00,\"stock\":50,\"img\":\"img1.jpg\"}," +
                "{\"id\":2,\"spec\":\"6两\",\"price\":258.00,\"stock\":30,\"img\":\"img2.jpg\"}" +
                "]}";
        String retryKey = RETRY_COUNT_PREFIX + message.hashCode();

        when(valueOperations.get(retryKey)).thenReturn(null);

        // When
        consumer.onMessage(message);

        // Then
        ArgumentCaptor<ProductDoc> docCaptor = ArgumentCaptor.forClass(ProductDoc.class);
        verify(searchService).syncProduct(docCaptor.capture());

        ProductDoc captured = docCaptor.getValue();
        assertEquals(10L, captured.getId());
        assertEquals("大闸蟹", captured.getName());
        assertEquals(new BigDecimal("128.00"), captured.getMinPrice());
        assertEquals(new BigDecimal("258.00"), captured.getMaxPrice());
        assertEquals("img1.jpg", captured.getImage());
        assertNotNull(captured.getSkuList());
        assertEquals(2, captured.getSkuList().size());
    }

    // ==================== Helper Methods ====================

    private String buildSaveMessage(Long productId, String name) {
        return "{\"action\":\"SAVE\"," +
                "\"productId\":" + productId + "," +
                "\"categoryId\":1," +
                "\"categoryName\":\"海鲜\"," +
                "\"name\":\"" + name + "\"," +
                "\"origin\":\"北海\"," +
                "\"isFresh\":1," +
                "\"priceType\":0," +
                "\"description\":\"新鲜海鲜\"," +
                "\"status\":1," +
                "\"skuList\":[" +
                "{\"id\":1,\"spec\":\"500g\",\"price\":99.90,\"stock\":100,\"img\":\"img.jpg\"}" +
                "]}";
    }

    private String buildDeleteMessage(Long productId) {
        return "{\"action\":\"DELETE\",\"productId\":" + productId + "}";
    }
}
