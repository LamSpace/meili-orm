/**
 * meili-orm Repository 层：Spring Data 风格的声明式仓库访问。
 *
 * <p>本模块是全工程唯一依赖 {@code spring-data-commons} 的产品模块；全部数据通路委托
 * {@link io.github.lamspace.meili.core.operations.MeiliSearchOperations}，实体读写与
 * 序列化语义以 core 元模型为唯一权威。应用显式引入本坐标即启用仓库自动配置。
 */
package io.github.lamspace.meili.repository;
