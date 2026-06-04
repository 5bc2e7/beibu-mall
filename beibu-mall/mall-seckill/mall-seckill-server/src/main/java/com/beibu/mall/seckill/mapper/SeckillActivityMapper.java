package com.beibu.mall.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beibu.mall.seckill.entity.SeckillActivity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 秒杀活动 Mapper 接口
 *
 * @Mapper：标记这是一个 Mapper 接口，Spring 会自动创建它的实现类
 * extends BaseMapper<SeckillActivity>：继承 MyBatis-Plus 的基础 Mapper
 *
 * BaseMapper 已经提供了常用的 CRUD 方法：
 * - insert(T)：插入
 * - deleteById(id)：删除
 * - updateById(T)：更新
 * - selectById(id)：根据ID查询
 * - selectList(wrapper)：条件查询
 *
 * 你不需要写 SQL，MyBatis-Plus 帮你生成了
 */
@Mapper
public interface SeckillActivityMapper extends BaseMapper<SeckillActivity> {

    // 如果需要自定义 SQL，可以在这里写方法
    // 比如：@Select("SELECT * FROM seckill_activity WHERE status = 1")
    // List<SeckillActivity> selectActiveActivities();
}
