package com.beibu.mall.search.repository;

import com.beibu.mall.search.entity.ProductDoc;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * 商品搜索仓库（Spring Data Elasticsearch）
 *
 * 大白话：这个接口帮我们操作 ES，类似于 MyBatis-Plus 的 Mapper
 *
 * Spring Data 的好处：
 * 1. 不用写任何代码，就能实现基本的增删改查
 * 2. 只需要定义方法名，Spring 会自动帮你生成查询语句
 * 3. 比如 findByCategoryId(1L) 会自动查 ES 里 categoryId=1 的数据
 *
 * 继承 ElasticsearchRepository<ProductDoc, Long>：
 * - ProductDoc：要操作的文档类型
 * - Long：主键类型（商品 ID 是 Long 类型）
 */
public interface ProductSearchRepository extends ElasticsearchRepository<ProductDoc, Long> {

    // 这里不需要写任何方法！
    // Spring Data ES 已经帮我们准备好了：
    // - save()：保存/更新文档
    // - deleteById()：删除文档
    // - findById()：根据 ID 查询
    // - findAll()：查询所有
    // - count()：统计总数
}
