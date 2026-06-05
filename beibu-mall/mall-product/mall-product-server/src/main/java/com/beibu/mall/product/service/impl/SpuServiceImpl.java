package com.beibu.mall.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.beibu.mall.common.exception.BizException;
import com.beibu.mall.product.api.dto.PageResult;
import com.beibu.mall.product.api.dto.SkuVO;
import com.beibu.mall.product.api.dto.SpuDetailVO;
import com.beibu.mall.product.api.dto.SpuQueryDTO;
import com.beibu.mall.product.api.dto.SpuSaveDTO;
import com.beibu.mall.product.api.dto.SpuVO;
import com.beibu.mall.product.entity.Category;
import com.beibu.mall.product.entity.Sku;
import com.beibu.mall.product.entity.Spu;
import com.beibu.mall.product.mapper.CategoryMapper;
import com.beibu.mall.product.mapper.SpuMapper;
import com.beibu.mall.product.mq.ProductSyncProducer;
import com.beibu.mall.product.service.SkuService;
import com.beibu.mall.product.service.SpuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * SPU 服务实现类
 *
 * SPU = Standard Product Unit（标准产品单元）
 * 例如「北部湾大对虾」就是一个 SPU。
 *
 * 这个类负责商品的增删改查和上下架操作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpuServiceImpl implements SpuService {

    private final SpuMapper spuMapper;
    private final SkuService skuService;
    private final CategoryMapper categoryMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;
    private final ProductSyncProducer productSyncProducer;

    /** 缓存 key 前缀 */
    private static final String CACHE_KEY_PREFIX = "product:spu:detail:";

    /** 缓存空值的标记（防穿透用） */
    private static final String NULL_PLACEHOLDER = "__NULL__";

    /** 基础过期时间（分钟） */
    private static final int BASE_EXPIRE_MINUTES = 30;

    /** 随机过期时间范围（分钟）— 防雪崩用 */
    private static final int RANDOM_EXPIRE_RANGE = 10;

    /**
     * 构建缓存 key
     */
    private String buildCacheKey(Long spuId) {
        return CACHE_KEY_PREFIX + spuId;
    }

    /**
     * 商品列表分页查询
     *
     * 实现思路：
     * 1. 构建查询条件（分类ID、关键词、状态）
     * 2. 使用 MyBatis-Plus 的 Page 进行分页
     * 3. 返回分页结果
     */
    @Override
    public PageResult<SpuVO> listSpu(SpuQueryDTO query) {
        LambdaQueryWrapper<Spu> wrapper = new LambdaQueryWrapper<>();

        if (query.getCategoryId() != null) {
            wrapper.eq(Spu::getCategoryId, query.getCategoryId());
        }

        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(Spu::getName, query.getKeyword());
        }

        if (query.getStatus() != null) {
            wrapper.eq(Spu::getStatus, query.getStatus());
        }

        wrapper.orderByDesc(Spu::getCreateTime);

        Page<Spu> page = new Page<>(query.getPage(), query.getSize());
        Page<Spu> result = spuMapper.selectPage(page, wrapper);

        // 批量查询分类名称，避免 N+1 问题
        Set<Long> categoryIds = result.getRecords().stream()
                .map(Spu::getCategoryId)
                .collect(Collectors.toSet());
        Map<Long, String> categoryNameMap = getCategoryNameMap(categoryIds);

        List<SpuVO> voList = new ArrayList<>();
        for (Spu spu : result.getRecords()) {
            SpuVO vo = new SpuVO();
            vo.setId(spu.getId());
            vo.setCategoryId(spu.getCategoryId());
            vo.setCategoryName(categoryNameMap.get(spu.getCategoryId()));
            vo.setName(spu.getName());
            vo.setOrigin(spu.getOrigin());
            vo.setIsFresh(spu.getIsFresh());
            vo.setPriceType(spu.getPriceType());
            vo.setMinBuy(spu.getMinBuy());
            vo.setStatus(spu.getStatus());
            voList.add(vo);
        }

        return new PageResult<>(voList, result.getTotal(), query.getPage(), query.getSize());
    }

    /**
     * 获取商品详情（含所有 SKU）— 带缓存
     *
     * 缓存策略：Cache-Aside Pattern（旁路缓存）
     * 1. 先查 Redis 缓存
     * 2. 缓存没有 → 加分布式锁 → 再查一次缓存（双重检查）→ 还没有才查数据库
     * 3. 查到数据写回 Redis，查不到也缓存空值（防穿透）
     * 4. 过期时间加随机值（防雪崩）
     */
    @Override
    public SpuDetailVO getSpuDetail(Long spuId) {
        String cacheKey = buildCacheKey(spuId);

        // ========== 第1步：查缓存 ==========
        Object cached = redisTemplate.opsForValue().get(cacheKey);

        if (cached != null) {
            // 缓存命中
            if (NULL_PLACEHOLDER.equals(cached)) {
                // 命中的是空值标记 → 说明之前查过，数据库确实没有这个商品
                throw new BizException(40004, "商品不存在");
            }
            if (cached instanceof SpuDetailVO vo) {
                // 命中真实数据 → 直接返回，不查数据库
                log.debug("缓存命中: spuId={}", spuId);
                return vo;
            }
            // 类型不匹配（缓存数据损坏）→ 删掉脏数据，继续查数据库
            log.warn("缓存类型异常，删除脏数据: spuId={}, type={}", spuId, cached.getClass().getSimpleName());
            redisTemplate.delete(cacheKey);
        }

        // ========== 第2步：缓存未命中，加分布式锁（防击穿） ==========
        // 锁的 key 按商品 ID 细分，不同商品之间不互斥
        String lockKey = "lock:spu:detail:" + spuId;
        RLock lock = redissonClient.getLock(lockKey);

        boolean locked = false;
        try {
            // tryLock(等待时间, 锁持有时间, 时间单位)
            // 等待 5 秒：最多等 5 秒获取锁，获取不到就放弃
            // 锁持有 10 秒：防止持锁进程崩溃导致死锁
            locked = lock.tryLock(5, 10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // 线程被中断（比如服务关闭）→ 恢复中断标志，降级查数据库
            Thread.currentThread().interrupt();
            log.warn("获取锁被中断，降级查数据库: spuId={}", spuId);
            return loadAndCacheSpuDetail(spuId, cacheKey);
        }

        if (!locked) {
            // 获取锁失败：说明有其他线程正在重建缓存
            // 降级策略：直接查数据库（牺牲一点一致性，保证可用性）
            log.warn("获取锁失败，降级查数据库: spuId={}", spuId);
            return loadAndCacheSpuDetail(spuId, cacheKey);
        }

        try {

            // 【双重检查】加锁后再查一次缓存
            // 为什么？因为可能在你等锁的时候，别的请求已经把缓存重建好了
            cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                if (NULL_PLACEHOLDER.equals(cached)) {
                    throw new BizException(40004, "商品不存在");
                }
                if (cached instanceof SpuDetailVO vo) {
                    log.debug("锁内缓存命中: spuId={}", spuId);
                    return vo;
                }
                // 类型不匹配 → 删除脏数据，继续查数据库重建
                log.warn("锁内缓存类型异常，删除脏数据: spuId={}", spuId);
                redisTemplate.delete(cacheKey);
            }

            // ========== 第3步：缓存真的没有，查数据库 ==========
            log.debug("缓存未命中，查数据库: spuId={}", spuId);
            SpuDetailVO vo = loadSpuDetailFromDB(spuId);

            // 写回缓存，过期时间 = 基础时间 + 随机值（防雪崩）
            int randomMinutes = ThreadLocalRandom.current().nextInt(RANDOM_EXPIRE_RANGE);
            long expireMinutes = BASE_EXPIRE_MINUTES + randomMinutes;
            redisTemplate.opsForValue().set(cacheKey, vo, expireMinutes, TimeUnit.MINUTES);

            return vo;

        } finally {
            // 释放锁（必须在 finally 里，防止异常时锁没释放导致死锁）
            // 只释放自己持有的锁，防止误释放别人的锁
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 从数据库加载商品详情并缓存（降级路径用）
     */
    private SpuDetailVO loadAndCacheSpuDetail(Long spuId, String cacheKey) {
        SpuDetailVO vo = loadSpuDetailFromDB(spuId);
        // 降级路径也要缓存结果，避免持续击穿数据库
        int randomMinutes = ThreadLocalRandom.current().nextInt(RANDOM_EXPIRE_RANGE);
        long expireMinutes = BASE_EXPIRE_MINUTES + randomMinutes;
        redisTemplate.opsForValue().set(cacheKey, vo, expireMinutes, TimeUnit.MINUTES);
        return vo;
    }

    /**
     * 从数据库加载商品详情（原逻辑，无缓存）
     *
     * 查不到时会缓存一个空值标记，防止缓存穿透。
     */
    private SpuDetailVO loadSpuDetailFromDB(Long spuId) {
        Spu spu = spuMapper.selectById(spuId);
        if (spu == null) {
            // 防穿透：查不到的数据也缓存一个空值，设短过期时间（2分钟）
            // 这样下次再查同一个不存在的 ID，直接从 Redis 返回，不打数据库
            String cacheKey = buildCacheKey(spuId);
            redisTemplate.opsForValue().set(cacheKey, NULL_PLACEHOLDER, 2, TimeUnit.MINUTES);
            throw new BizException(40004, "商品不存在");
        }

        SpuDetailVO vo = new SpuDetailVO();
        vo.setId(spu.getId());
        vo.setCategoryId(spu.getCategoryId());
        vo.setCategoryName(getCategoryName(spu.getCategoryId()));
        vo.setName(spu.getName());
        vo.setOrigin(spu.getOrigin());
        vo.setIsFresh(spu.getIsFresh());
        vo.setPriceType(spu.getPriceType());
        vo.setShelfLife(spu.getShelfLife());
        vo.setMinBuy(spu.getMinBuy());
        vo.setDescription(spu.getDescription());
        vo.setStatus(spu.getStatus());

        List<SkuVO> skuList = skuService.listSkuBySpuId(spuId);
        vo.setSkuList(skuList);

        return vo;
    }

    /**
     * 添加商品
     *
     * 实现思路：
     * 1. 将 DTO 转换为实体
     * 2. 设置默认值
     * 3. 插入数据库
     */
    @Override
    @Transactional
    public Long addSpu(SpuSaveDTO dto) {
        validateCategory(dto.getCategoryId());

        Spu spu = new Spu();
        spu.setCategoryId(dto.getCategoryId());
        spu.setName(dto.getName());
        spu.setOrigin(dto.getOrigin());
        spu.setIsFresh(dto.getIsFresh() != null ? dto.getIsFresh() : 0);
        spu.setPriceType(dto.getPriceType() != null ? dto.getPriceType() : 0);
        spu.setShelfLife(dto.getShelfLife());
        spu.setMinBuy(dto.getMinBuy() != null ? dto.getMinBuy() : 1);
        spu.setDescription(dto.getDescription());
        spu.setStatus(0);

        spuMapper.insert(spu);

        // 事务提交后发送 MQ 消息通知搜索服务
        syncProductToSearchAfterCommit(spu);

        return spu.getId();
    }

    /**
     * 修改商品
     *
     * 数据变了，必须删掉缓存，否则用户看到的还是旧数据。
     */
    @Override
    @Transactional
    public void updateSpu(SpuSaveDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(40004, "商品ID不能为空");
        }

        Spu spu = spuMapper.selectById(dto.getId());
        if (spu == null) {
            throw new BizException(40004, "商品不存在");
        }

        if (dto.getCategoryId() != null) {
            validateCategory(dto.getCategoryId());
            spu.setCategoryId(dto.getCategoryId());
        }
        if (dto.getName() != null) {
            spu.setName(dto.getName());
        }
        if (dto.getOrigin() != null) {
            spu.setOrigin(dto.getOrigin());
        }
        if (dto.getIsFresh() != null) {
            spu.setIsFresh(dto.getIsFresh());
        }
        if (dto.getPriceType() != null) {
            spu.setPriceType(dto.getPriceType());
        }
        if (dto.getShelfLife() != null) {
            spu.setShelfLife(dto.getShelfLife());
        }
        if (dto.getMinBuy() != null) {
            spu.setMinBuy(dto.getMinBuy());
        }
        if (dto.getDescription() != null) {
            spu.setDescription(dto.getDescription());
        }

        spuMapper.updateById(spu);

        evictSpuCacheAfterCommit(dto.getId());
        syncProductToSearchAfterCommit(spu);
    }

    /**
     * 上架商品
     *
     * 状态变了，也要删缓存。
     */
    @Override
    @Transactional
    public void onSale(Long spuId) {
        Spu spu = spuMapper.selectById(spuId);
        if (spu == null) {
            throw new BizException(40004, "商品不存在");
        }

        if (spu.getStatus() == 1) {
            throw new BizException(40008, "商品已上架，不能重复上架");
        }

        spu.setStatus(1);
        spuMapper.updateById(spu);

        evictSpuCacheAfterCommit(spuId);
        syncProductToSearchAfterCommit(spu);
    }

    /**
     * 下架商品
     *
     * 状态变了，也要删缓存。
     */
    @Override
    @Transactional
    public void offSale(Long spuId) {
        Spu spu = spuMapper.selectById(spuId);
        if (spu == null) {
            throw new BizException(40004, "商品不存在");
        }

        if (spu.getStatus() == 0) {
            throw new BizException(40007, "商品已下架，不能重复下架");
        }

        spu.setStatus(0);
        spuMapper.updateById(spu);

        evictSpuCacheAfterCommit(spuId);
        syncProductToSearchAfterCommit(spu);
    }

    /**
     * 事务提交后再删除缓存
     *
     * 为什么不在事务内删缓存？
     * 如果事务回滚，缓存已经被删了，下次读取会加载旧数据到缓存。
     * 用 TransactionSynchronization 保证只有事务成功提交后才删缓存。
     */
    private void evictSpuCacheAfterCommit(Long spuId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictSpuCache(spuId);
                }
            });
        } else {
            evictSpuCache(spuId);
        }
    }

    private void syncProductToSearchAfterCommit(Spu spu) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    List<Sku> skuList = skuService.listSkuEntityBySpuId(spu.getId());
                    productSyncProducer.sendProductSyncMessage(spu, skuList);
                }
            });
        } else {
            List<Sku> skuList = skuService.listSkuEntityBySpuId(spu.getId());
            productSyncProducer.sendProductSyncMessage(spu, skuList);
        }
    }

    /**
     * 删除指定商品的缓存
     */
    private void evictSpuCache(Long spuId) {
        String cacheKey = buildCacheKey(spuId);
        redisTemplate.delete(cacheKey);
        log.debug("缓存已删除: spuId={}", spuId);
    }

    private Map<Long, String> getCategoryNameMap(Set<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        List<Category> categories = categoryMapper.selectBatchIds(categoryIds);
        return categories.stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
    }

    private String getCategoryName(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        Category category = categoryMapper.selectById(categoryId);
        return category != null ? category.getName() : null;
    }

    private void validateCategory(Long categoryId) {
        if (categoryId == null) {
            throw new BizException(40009, "分类ID不能为空");
        }
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BizException(40010, "分类不存在");
        }
    }
}
