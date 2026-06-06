package com.beibu.mall.payment.service;

import com.beibu.mall.common.exception.BizException;
import com.beibu.mall.payment.dto.PaymentCallbackDTO;
import com.beibu.mall.payment.dto.PaymentCreateDTO;
import com.beibu.mall.payment.entity.PaymentLog;
import com.beibu.mall.payment.entity.PaymentOrder;
import com.beibu.mall.payment.enums.PaymentStatus;
import com.beibu.mall.payment.mapper.PaymentLogMapper;
import com.beibu.mall.payment.mapper.PaymentOrderMapper;
import com.beibu.mall.payment.mq.PaymentSuccessProducer;
import com.beibu.mall.payment.service.impl.PaymentServiceImpl;
import com.beibu.mall.payment.vo.PaymentVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 支付服务单元测试
 *
 * 大白话：这个测试类验证支付服务的核心逻辑是否正确
 *
 * 重点测试：
 * 1. 正常支付回调流程
 * 2. 幂等性：重复回调只处理一次（核心！）
 * 3. 不同订单号：各自独立处理
 *
 * 为什么用 Mockito？
 * 支付服务依赖数据库（Mapper）和消息队列（Producer），
 * 单元测试时不应该真的访问这些外部资源，
 * 所以用 Mock 对象来模拟它们的行为。
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    /**
     * @Mock：创建假的 Mapper 对象
     * 这些假对象不会真的访问数据库
     */
    @Mock
    private PaymentOrderMapper paymentOrderMapper;

    @Mock
    private PaymentLogMapper paymentLogMapper;

    @Mock
    private PaymentSuccessProducer paymentSuccessProducer;

    /**
     * @InjectMocks：创建真实的 Service 对象，并注入上面的 Mock
     */
    @InjectMocks
    private PaymentServiceImpl paymentService;

    private PaymentOrder testOrder;
    private PaymentCallbackDTO callbackDTO;

    /**
     * 每个测试方法执行前都会调用：准备测试数据
     */
    @BeforeEach
    void setUp() {
        // 准备一个待支付的支付单
        testOrder = new PaymentOrder();
        testOrder.setId(1L);
        testOrder.setOrderId("ORDER_20240101_001");
        testOrder.setPaymentNo("PAY_20240101_001");
        testOrder.setUserId(1001L);
        testOrder.setAmount(new BigDecimal("199.80"));
        testOrder.setPaymentMethod(1);
        testOrder.setStatus(PaymentStatus.PENDING.getCode());
        testOrder.setCreateTime(LocalDateTime.now());

        // 准备回调请求
        callbackDTO = new PaymentCallbackDTO();
        callbackDTO.setOrderId("ORDER_20240101_001");
        callbackDTO.setTradeNo("ALIPAY_20240101_001");
        callbackDTO.setCallbackStatus("SUCCESS");
    }

    /**
     * 测试1：正常支付回调 - 第一次回调应该成功处理
     *
     * 场景：用户付了钱，支付宝发来第一次回调通知
     * 预期：支付单状态从"待支付"变为"支付成功"，发送MQ消息
     */
    @Test
    @DisplayName("正常支付回调 - 第一次回调成功处理")
    void handleCallback_FirstTime_Success() {
        try (var mockedStatic = mockStatic(TransactionSynchronizationManager.class)) {
            ArgumentCaptor<TransactionSynchronization> syncCaptor =
                    ArgumentCaptor.forClass(TransactionSynchronization.class);
            mockedStatic.when(() -> TransactionSynchronizationManager.registerSynchronization(syncCaptor.capture()))
                    .thenAnswer(invocation -> null);

            when(paymentOrderMapper.selectOne(any()))
                    .thenReturn(testOrder);
            when(paymentOrderMapper.updateById(any(PaymentOrder.class)))
                    .thenReturn(1);
            when(paymentLogMapper.insert(any(PaymentLog.class)))
                    .thenReturn(1);

            PaymentVO result = paymentService.handleCallback(callbackDTO);

            assertNotNull(result);
            assertEquals(PaymentStatus.SUCCESS.getCode(), result.getStatus());
            assertEquals("支付成功", result.getStatusDesc());

            verify(paymentOrderMapper, times(1)).updateById(any(PaymentOrder.class));
            verify(paymentLogMapper, times(1)).insert(any(PaymentLog.class));

            syncCaptor.getValue().afterCommit();
            verify(paymentSuccessProducer, times(1)).sendPaymentSuccess(any(PaymentOrder.class));
        }
    }

    /**
     * 测试2：幂等性 - 重复回调只处理一次（核心测试！）
     *
     * 场景：支付宝因为网络问题，发了两次回调通知
     * 预期：
     * - 第一次回调：正常处理，状态变为"支付成功"
     * - 第二次回调：发现已经是"支付成功"了，直接返回，不重复处理
     *
     * 这就是幂等性！执行一次和执行N次，结果完全一样。
     */
    @Test
    @DisplayName("幂等性测试 - 重复回调只处理一次")
    void handleCallback_DuplicateCallback_Idempotent() {
        // ========== Given：准备一个已经是"支付成功"状态的订单 ==========
        PaymentOrder alreadyPaidOrder = new PaymentOrder();
        alreadyPaidOrder.setId(1L);
        alreadyPaidOrder.setOrderId("ORDER_20240101_001");
        alreadyPaidOrder.setPaymentNo("PAY_20240101_001");
        alreadyPaidOrder.setUserId(1001L);
        alreadyPaidOrder.setAmount(new BigDecimal("199.80"));
        alreadyPaidOrder.setPaymentMethod(1);
        alreadyPaidOrder.setStatus(PaymentStatus.SUCCESS.getCode());  // 已经是成功状态！
        alreadyPaidOrder.setPaymentTime(LocalDateTime.now());
        alreadyPaidOrder.setTradeNo("ALIPAY_20240101_001");

        // 模拟数据库查询：返回已支付的订单
        when(paymentOrderMapper.selectOne(any()))
                .thenReturn(alreadyPaidOrder);

        // ========== When：执行回调（模拟第二次回调） ==========
        PaymentVO result = paymentService.handleCallback(callbackDTO);

        // ========== Then：验证幂等性 ==========

        // 验证返回结果不为空
        assertNotNull(result);

        // 验证状态仍然是"支付成功"
        assertEquals(PaymentStatus.SUCCESS.getCode(), result.getStatus());

        // 关键验证：没有调用数据库更新！（因为已经是成功状态了）
        verify(paymentOrderMapper, never()).updateById(any(PaymentOrder.class));

        // 关键验证：没有发送MQ消息！（因为已经发过了）
        verify(paymentSuccessProducer, never()).sendPaymentSuccess(any(PaymentOrder.class));

        // 关键验证：没有插入操作日志！（因为没有实际操作）
        verify(paymentLogMapper, never()).insert(any(PaymentLog.class));
    }

    /**
     * 测试3：不同订单号 - 各自独立处理
     *
     * 场景：两笔不同的订单分别回调
     * 预期：各自独立处理，互不影响
     */
    @Test
    @DisplayName("不同订单号 - 各自独立处理")
    void handleCallback_DifferentOrder_ProcessedIndependently() {
        try (var mockedStatic = mockStatic(TransactionSynchronizationManager.class)) {
            ArgumentCaptor<TransactionSynchronization> syncCaptor =
                    ArgumentCaptor.forClass(TransactionSynchronization.class);
            mockedStatic.when(() -> TransactionSynchronizationManager.registerSynchronization(syncCaptor.capture()))
                    .thenAnswer(invocation -> null);

            PaymentOrder secondOrder = new PaymentOrder();
            secondOrder.setId(2L);
            secondOrder.setOrderId("ORDER_20240101_002");
            secondOrder.setPaymentNo("PAY_20240101_002");
            secondOrder.setUserId(1002L);
            secondOrder.setAmount(new BigDecimal("299.00"));
            secondOrder.setPaymentMethod(2);
            secondOrder.setStatus(PaymentStatus.PENDING.getCode());

            PaymentCallbackDTO secondCallback = new PaymentCallbackDTO();
            secondCallback.setOrderId("ORDER_20240101_002");
            secondCallback.setTradeNo("WECHAT_20240101_002");
            secondCallback.setCallbackStatus("SUCCESS");

            when(paymentOrderMapper.selectOne(any()))
                    .thenReturn(secondOrder);
            when(paymentOrderMapper.updateById(any(PaymentOrder.class)))
                    .thenReturn(1);
            when(paymentLogMapper.insert(any(PaymentLog.class)))
                    .thenReturn(1);

            PaymentVO result = paymentService.handleCallback(secondCallback);

            assertNotNull(result);
            assertEquals(PaymentStatus.SUCCESS.getCode(), result.getStatus());
            assertEquals("299.00", result.getAmount().toString());

            verify(paymentOrderMapper, times(1)).updateById(any(PaymentOrder.class));

            syncCaptor.getValue().afterCommit();
            verify(paymentSuccessProducer, times(1)).sendPaymentSuccess(any(PaymentOrder.class));
        }
    }

    /**
     * 测试4：订单不存在 - 应该抛出异常
     *
     * 场景：回调的订单号在数据库里找不到
     * 预期：抛出 BizException
     */
    @Test
    @DisplayName("订单不存在 - 抛出异常")
    void handleCallback_OrderNotFound_ThrowException() {
        // ========== Given ==========
        when(paymentOrderMapper.selectOne(any()))
                .thenReturn(null);  // 数据库查不到

        // ========== When & Then ==========
        BizException exception = assertThrows(BizException.class, () -> {
            paymentService.handleCallback(callbackDTO);
        });

        assertTrue(exception.getMessage().contains("不存在"));
    }

    // ========== P0: callbackStatus 验证 ==========

    /**
     * 测试5：回调状态为FAIL时应抛出异常
     *
     * 场景：支付宝回调通知支付失败
     * 预期：抛出 BizException，不应该把失败的回调当作成功处理
     */
    @Test
    @DisplayName("P0: 回调状态FAIL - 抛出异常")
    void handleCallback_CallbackStatusFail_ThrowsException() {
        // ========== Given ==========
        callbackDTO.setCallbackStatus("FAIL");

        // ========== When & Then ==========
        BizException exception = assertThrows(BizException.class, () -> {
            paymentService.handleCallback(callbackDTO);
        });

        assertEquals(40021, exception.getCode());
        assertTrue(exception.getMessage().contains("回调状态异常"));
    }

    // ========== P1: 终态检查 ==========

    /**
     * 测试6：已关闭的支付单不应被更新为成功
     *
     * 场景：支付单已超时关闭，但回调仍尝试更新为成功
     * 预期：抛出 BizException，终态不可变更
     */
    @Test
    @DisplayName("P1: 已关闭的支付单 - 抛出异常")
    void handleCallback_ClosedOrder_ThrowsException() {
        // ========== Given ==========
        PaymentOrder closedOrder = new PaymentOrder();
        closedOrder.setId(1L);
        closedOrder.setOrderId("ORDER_20240101_001");
        closedOrder.setStatus(PaymentStatus.CLOSED.getCode());

        when(paymentOrderMapper.selectOne(any()))
                .thenReturn(closedOrder);

        // ========== When & Then ==========
        BizException exception = assertThrows(BizException.class, () -> {
            paymentService.handleCallback(callbackDTO);
        });

        assertEquals(40022, exception.getCode());
        assertTrue(exception.getMessage().contains("已关闭"));
    }

    /**
     * 测试7：已失败的支付单不应被更新为成功
     *
     * 场景：支付单之前已标记为失败，但回调仍尝试更新为成功
     * 预期：抛出 BizException，终态不可变更
     */
    @Test
    @DisplayName("P1: 已失败的支付单 - 抛出异常")
    void handleCallback_FailedOrder_ThrowsException() {
        // ========== Given ==========
        PaymentOrder failedOrder = new PaymentOrder();
        failedOrder.setId(1L);
        failedOrder.setOrderId("ORDER_20240101_001");
        failedOrder.setStatus(PaymentStatus.FAILED.getCode());

        when(paymentOrderMapper.selectOne(any()))
                .thenReturn(failedOrder);

        // ========== When & Then ==========
        BizException exception = assertThrows(BizException.class, () -> {
            paymentService.handleCallback(callbackDTO);
        });

        assertEquals(40023, exception.getCode());
        assertTrue(exception.getMessage().contains("已失败"));
    }

    // ========== P1: 支付单号唯一性 ==========

    /**
     * 测试8：支付单号应保证唯一性
     *
     * 场景：高并发下生成大量支付单号
     * 预期：所有生成的单号都不重复
     */
    @Test
    @DisplayName("P1: 支付单号唯一性 - 生成1000个无重复")
    void generatePaymentNo_Unique_NoDuplicates() {
        // ========== Given & When ==========
        // 通过反射调用 private 方法
        Set<String> uniqueNos = new HashSet<>();
        try {
            var method = PaymentServiceImpl.class.getDeclaredMethod("generatePaymentNo");
            method.setAccessible(true);
            for (int i = 0; i < 1000; i++) {
                String no = (String) method.invoke(paymentService);
                uniqueNos.add(no);
            }
        } catch (Exception e) {
            fail("反射调用失败: " + e.getMessage());
        }

        // ========== Then ==========
        assertEquals(1000, uniqueNos.size(), "生成的支付单号不应有重复");
    }

    // ========== P2: PaymentStatus.fromCode 异常 ==========

    /**
     * 测试9：无效的状态码应抛出异常
     *
     * 场景：传入一个不存在的状态码
     * 预期：抛出 IllegalArgumentException，而不是返回 null
     */
    @Test
    @DisplayName("P2: 无效状态码 - 抛出异常")
    void paymentStatus_FromCode_InvalidCode_ThrowsException() {
        // ========== When & Then ==========
        assertThrows(IllegalArgumentException.class, () -> {
            PaymentStatus.fromCode(999);
        });
    }

    // ========== P0: MQ 发送应在事务提交后 ==========

    /**
     * 测试10：MQ消息应在事务提交后发送（不在事务内）
     *
     * 场景：正常支付回调
     * 预期：MQ消息通过 TransactionSynchronization 注册，在 afterCommit 中发送
     */
    @Test
    @DisplayName("P0: MQ消息在事务提交后发送")
    void handleCallback_MQSentAfterTransactionCommit() {
        // ========== Given ==========
        try (var mockedStatic = mockStatic(TransactionSynchronizationManager.class)) {
            // 捕获注册的 TransactionSynchronization
            ArgumentCaptor<TransactionSynchronization> syncCaptor =
                    ArgumentCaptor.forClass(TransactionSynchronization.class);

            mockedStatic.when(() -> TransactionSynchronizationManager.registerSynchronization(syncCaptor.capture()))
                    .thenAnswer(invocation -> null);

            when(paymentOrderMapper.selectOne(any()))
                    .thenReturn(testOrder);
            when(paymentOrderMapper.updateById(any(PaymentOrder.class)))
                    .thenReturn(1);
            when(paymentLogMapper.insert(any(PaymentLog.class)))
                    .thenReturn(1);

            // ========== When ==========
            paymentService.handleCallback(callbackDTO);

            // ========== Then ==========
            // 验证注册了事务同步回调
            mockedStatic.verify(() ->
                    TransactionSynchronizationManager.registerSynchronization(any()), times(1));

            // 模拟事务提交，触发 afterCommit
            TransactionSynchronization sync = syncCaptor.getValue();
            sync.afterCommit();

            // 验证 MQ 消息在 afterCommit 中发送
            verify(paymentSuccessProducer, times(1)).sendPaymentSuccess(any(PaymentOrder.class));
        }
    }

    // ========== createPayment 测试 ==========

    /**
     * 测试11：创建支付单 - 新订单成功创建
     *
     * 场景：用户下单后首次创建支付单
     * 预期：数据库中不存在该订单的支付单，创建成功并返回 PaymentVO
     */
    @Test
    @DisplayName("创建支付单 - 新订单成功创建")
    void createPayment_newOrder_success() {
        // ========== Given ==========
        PaymentCreateDTO createDTO = new PaymentCreateDTO();
        createDTO.setOrderId("ORDER_20240201_001");
        createDTO.setUserId(2001L);
        createDTO.setAmount(new BigDecimal("99.90"));
        createDTO.setPaymentMethod(1);

        // 数据库中不存在该订单的支付单
        when(paymentOrderMapper.selectOne(any()))
                .thenReturn(null);
        // insert 成功，且回填 ID
        when(paymentOrderMapper.insert(any(PaymentOrder.class)))
                .thenAnswer(invocation -> {
                    PaymentOrder order = invocation.getArgument(0);
                    order.setId(100L);
                    return 1;
                });
        when(paymentLogMapper.insert(any(PaymentLog.class)))
                .thenReturn(1);

        // ========== When ==========
        PaymentVO result = paymentService.createPayment(createDTO);

        // ========== Then ==========
        assertNotNull(result);
        assertEquals("ORDER_20240201_001", result.getOrderId());
        assertEquals(new BigDecimal("99.90"), result.getAmount());
        assertEquals(PaymentStatus.PENDING.getCode(), result.getStatus());
        assertEquals("待支付", result.getStatusDesc());
        assertEquals("支付宝", result.getPaymentMethodDesc());
        assertNotNull(result.getPaymentNo());
        assertTrue(result.getPaymentNo().startsWith("PAY"));

        verify(paymentOrderMapper, times(1)).insert(any(PaymentOrder.class));
        verify(paymentLogMapper, times(1)).insert(any(PaymentLog.class));
    }

    /**
     * 测试12：创建支付单 - 已存在时幂等返回
     *
     * 场景：重复调用 createPayment（同一订单号）
     * 预期：返回已有的支付单，不重复插入
     */
    @Test
    @DisplayName("创建支付单 - 已存在时幂等返回")
    void createPayment_existingOrder_idempotent() {
        // ========== Given ==========
        PaymentCreateDTO createDTO = new PaymentCreateDTO();
        createDTO.setOrderId("ORDER_20240101_001");
        createDTO.setUserId(1001L);
        createDTO.setAmount(new BigDecimal("199.80"));
        createDTO.setPaymentMethod(1);

        // 数据库中已存在该订单的支付单
        when(paymentOrderMapper.selectOne(any()))
                .thenReturn(testOrder);

        // ========== When ==========
        PaymentVO result = paymentService.createPayment(createDTO);

        // ========== Then ==========
        assertNotNull(result);
        assertEquals("ORDER_20240101_001", result.getOrderId());

        // 关键验证：没有插入新支付单
        verify(paymentOrderMapper, never()).insert(any(PaymentOrder.class));
        // 关键验证：没有插入操作日志
        verify(paymentLogMapper, never()).insert(any(PaymentLog.class));
    }

    // ========== getPaymentByOrderId 测试 ==========

    /**
     * 测试13：查询支付单 - 存在时返回 PaymentVO
     *
     * 场景：根据订单号查询支付单详情
     * 预期：返回正确的 PaymentVO
     */
    @Test
    @DisplayName("查询支付单 - 存在时返回PaymentVO")
    void getPaymentByOrderId_found() {
        // ========== Given ==========
        when(paymentOrderMapper.selectOne(any()))
                .thenReturn(testOrder);

        // ========== When ==========
        PaymentVO result = paymentService.getPaymentByOrderId("ORDER_20240101_001");

        // ========== Then ==========
        assertNotNull(result);
        assertEquals("ORDER_20240101_001", result.getOrderId());
        assertEquals("PAY_20240101_001", result.getPaymentNo());
        assertEquals(new BigDecimal("199.80"), result.getAmount());
        assertEquals(PaymentStatus.PENDING.getCode(), result.getStatus());
        assertEquals("待支付", result.getStatusDesc());
    }

    /**
     * 测试14：查询支付单 - 不存在时抛出异常
     *
     * 场景：根据订单号查询不存在的支付单
     * 预期：抛出 BizException，错误码 40020
     */
    @Test
    @DisplayName("查询支付单 - 不存在时抛出BizException")
    void getPaymentByOrderId_notFound() {
        // ========== Given ==========
        when(paymentOrderMapper.selectOne(any()))
                .thenReturn(null);

        // ========== When & Then ==========
        BizException exception = assertThrows(BizException.class, () -> {
            paymentService.getPaymentByOrderId("ORDER_NOT_EXIST");
        });

        assertEquals(40020, exception.getCode());
        assertTrue(exception.getMessage().contains("不存在"));
    }
}
