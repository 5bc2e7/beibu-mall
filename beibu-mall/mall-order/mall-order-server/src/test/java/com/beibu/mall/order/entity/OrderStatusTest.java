package com.beibu.mall.order.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 订单状态枚举测试
 *
 * 测试 OrderStatus 的状态机逻辑，包括：
 * - 状态码转换
 * - 状态流转权限
 * - 终态判断
 */
@DisplayName("订单状态枚举测试")
class OrderStatusTest {

    // ==================== fromCode 测试 ====================

    @ParameterizedTest
    @CsvSource({
            "0, PENDING_PAYMENT",
            "1, PAID",
            "2, DELIVERED",
            "3, COMPLETED",
            "4, CANCELLED",
            "5, REFUNDED"
    })
    @DisplayName("fromCode - 有效状态码返回对应枚举")
    void fromCode_validCode_returnsEnum(int code, OrderStatus expected) {
        assertEquals(expected, OrderStatus.fromCode(code));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 6, 100, 999})
    @DisplayName("fromCode - 无效状态码抛出 IllegalArgumentException")
    void fromCode_invalidCode_throwsException(int code) {
        assertThrows(IllegalArgumentException.class, () -> OrderStatus.fromCode(code));
    }

    // ==================== canCancel 测试 ====================

    @Test
    @DisplayName("canCancel - 待支付状态可以取消")
    void canCancel_pendingPayment_returnsTrue() {
        assertTrue(OrderStatus.PENDING_PAYMENT.canCancel());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PAID", "DELIVERED", "COMPLETED", "CANCELLED", "REFUNDED"})
    @DisplayName("canCancel - 非待支付状态不能取消")
    void canCancel_otherStatus_returnsFalse(OrderStatus status) {
        assertFalse(status.canCancel());
    }

    // ==================== isFinal 测试 ====================

    @ParameterizedTest
    @ValueSource(strings = {"COMPLETED", "CANCELLED", "REFUNDED"})
    @DisplayName("isFinal - 终态返回 true")
    void isFinal_finalStatus_returnsTrue(OrderStatus status) {
        assertTrue(status.isFinal());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_PAYMENT", "PAID", "DELIVERED"})
    @DisplayName("isFinal - 非终态返回 false")
    void isFinal_nonFinalStatus_returnsFalse(OrderStatus status) {
        assertFalse(status.isFinal());
    }

    // ==================== canPay 测试 ====================

    @Test
    @DisplayName("canPay - 待支付状态可以支付")
    void canPay_pendingPayment_returnsTrue() {
        assertTrue(OrderStatus.PENDING_PAYMENT.canPay());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PAID", "DELIVERED", "COMPLETED", "CANCELLED", "REFUNDED"})
    @DisplayName("canPay - 非待支付状态不能支付")
    void canPay_otherStatus_returnsFalse(OrderStatus status) {
        assertFalse(status.canPay());
    }

    // ==================== canDeliver 测试 ====================

    @Test
    @DisplayName("canDeliver - 已支付状态可以发货")
    void canDeliver_paid_returnsTrue() {
        assertTrue(OrderStatus.PAID.canDeliver());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_PAYMENT", "DELIVERED", "COMPLETED", "CANCELLED", "REFUNDED"})
    @DisplayName("canDeliver - 非已支付状态不能发货")
    void canDeliver_otherStatus_returnsFalse(OrderStatus status) {
        assertFalse(status.canDeliver());
    }

    // ==================== canComplete 测试 ====================

    @Test
    @DisplayName("canComplete - 已发货状态可以确认收货")
    void canComplete_delivered_returnsTrue() {
        assertTrue(OrderStatus.DELIVERED.canComplete());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_PAYMENT", "PAID", "COMPLETED", "CANCELLED", "REFUNDED"})
    @DisplayName("canComplete - 非已发货状态不能确认收货")
    void canComplete_otherStatus_returnsFalse(OrderStatus status) {
        assertFalse(status.canComplete());
    }

    // ==================== canRefund 测试 ====================

    @ParameterizedTest
    @ValueSource(strings = {"PAID", "DELIVERED"})
    @DisplayName("canRefund - 已支付或已发货状态可以退款")
    void canRefund_validStatus_returnsTrue(OrderStatus status) {
        assertTrue(status.canRefund());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_PAYMENT", "COMPLETED", "CANCELLED", "REFUNDED"})
    @DisplayName("canRefund - 其他状态不能退款")
    void canRefund_otherStatus_returnsFalse(OrderStatus status) {
        assertFalse(status.canRefund());
    }

    // ==================== canTransitionTo 测试 ====================

    @Test
    @DisplayName("canTransitionTo - 待支付可以流转到已支付")
    void canTransitionTo_pendingToPaid_returnsTrue() {
        assertTrue(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.PAID));
    }

    @Test
    @DisplayName("canTransitionTo - 待支付可以流转到已取消")
    void canTransitionTo_pendingToCancelled_returnsTrue() {
        assertTrue(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.CANCELLED));
    }

    @Test
    @DisplayName("canTransitionTo - 待支付不能流转到已发货")
    void canTransitionTo_pendingToDelivered_returnsFalse() {
        assertFalse(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.DELIVERED));
    }

    @Test
    @DisplayName("canTransitionTo - 已支付可以流转到已发货")
    void canTransitionTo_paidToDelivered_returnsTrue() {
        assertTrue(OrderStatus.PAID.canTransitionTo(OrderStatus.DELIVERED));
    }

    @Test
    @DisplayName("canTransitionTo - 已支付可以流转到已退款")
    void canTransitionTo_paidToRefunded_returnsTrue() {
        assertTrue(OrderStatus.PAID.canTransitionTo(OrderStatus.REFUNDED));
    }

    @Test
    @DisplayName("canTransitionTo - 已发货可以流转到已完成")
    void canTransitionTo_deliveredToCompleted_returnsTrue() {
        assertTrue(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.COMPLETED));
    }

    @Test
    @DisplayName("canTransitionTo - 已发货可以流转到已退款")
    void canTransitionTo_deliveredToRefunded_returnsTrue() {
        assertTrue(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.REFUNDED));
    }

    @Test
    @DisplayName("canTransitionTo - 终态不能流转到任何状态")
    void canTransitionTo_finalState_returnsFalse() {
        OrderStatus[] finalStates = {OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.REFUNDED};
        OrderStatus[] allStates = OrderStatus.values();

        for (OrderStatus finalState : finalStates) {
            for (OrderStatus target : allStates) {
                assertFalse(finalState.canTransitionTo(target),
                        finalState + " 不应该能流转到 " + target);
            }
        }
    }

    // ==================== getAllowedTransitions 测试 ====================

    @Test
    @DisplayName("getAllowedTransitions - 待支付允许流转到已支付和已取消")
    void getAllowedTransitions_pendingPayment_returnsPaidAndCancelled() {
        List<OrderStatus> allowed = OrderStatus.PENDING_PAYMENT.getAllowedTransitions();
        assertEquals(2, allowed.size());
        assertTrue(allowed.contains(OrderStatus.PAID));
        assertTrue(allowed.contains(OrderStatus.CANCELLED));
    }

    @Test
    @DisplayName("getAllowedTransitions - 已支付允许流转到已发货和已退款")
    void getAllowedTransitions_paid_returnsDeliveredAndRefunded() {
        List<OrderStatus> allowed = OrderStatus.PAID.getAllowedTransitions();
        assertEquals(2, allowed.size());
        assertTrue(allowed.contains(OrderStatus.DELIVERED));
        assertTrue(allowed.contains(OrderStatus.REFUNDED));
    }

    @Test
    @DisplayName("getAllowedTransitions - 已发货允许流转到已完成和已退款")
    void getAllowedTransitions_delivered_returnsCompletedAndRefunded() {
        List<OrderStatus> allowed = OrderStatus.DELIVERED.getAllowedTransitions();
        assertEquals(2, allowed.size());
        assertTrue(allowed.contains(OrderStatus.COMPLETED));
        assertTrue(allowed.contains(OrderStatus.REFUNDED));
    }

    @Test
    @DisplayName("getAllowedTransitions - 终态不允许流转")
    void getAllowedTransitions_finalState_returnsEmpty() {
        OrderStatus[] finalStates = {OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.REFUNDED};

        for (OrderStatus finalState : finalStates) {
            List<OrderStatus> allowed = finalState.getAllowedTransitions();
            assertTrue(allowed.isEmpty(), finalState + " 不应该允许任何流转");
        }
    }

    // ==================== 状态码和描述测试 ====================

    @Test
    @DisplayName("getCode - 返回正确的状态码")
    void getCode_returnsCorrectCode() {
        assertEquals(0, OrderStatus.PENDING_PAYMENT.getCode());
        assertEquals(1, OrderStatus.PAID.getCode());
        assertEquals(2, OrderStatus.DELIVERED.getCode());
        assertEquals(3, OrderStatus.COMPLETED.getCode());
        assertEquals(4, OrderStatus.CANCELLED.getCode());
        assertEquals(5, OrderStatus.REFUNDED.getCode());
    }

    @Test
    @DisplayName("getDesc - 返回正确的状态描述")
    void getDesc_returnsCorrectDescription() {
        assertEquals("待支付", OrderStatus.PENDING_PAYMENT.getDesc());
        assertEquals("已支付", OrderStatus.PAID.getDesc());
        assertEquals("已发货", OrderStatus.DELIVERED.getDesc());
        assertEquals("已完成", OrderStatus.COMPLETED.getDesc());
        assertEquals("已取消", OrderStatus.CANCELLED.getDesc());
        assertEquals("已退款", OrderStatus.REFUNDED.getDesc());
    }
}
