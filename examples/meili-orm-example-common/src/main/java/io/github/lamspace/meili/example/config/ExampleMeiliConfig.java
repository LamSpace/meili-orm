package io.github.lamspace.meili.example.config;

import io.github.lamspace.meili.core.event.BeforeConvertCallback;
import io.github.lamspace.meili.example.domain.Book;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 演示装配：生命周期回调四件套之 BeforeConvertCallback 的实战形态。
 *
 * <p>声明为 bean 即被 meili-orm 自动配置的回调注册表收集，在每次写路径序列化前
 * 触发——此处规整书名前后空白，展示"不改业务调用点即可插入横切行为"。
 */
@Configuration(proxyBeanMethods = false)
public class ExampleMeiliConfig {

    /**
     * 配置类由容器实例化；全部行为在 {@code @Bean} 方法中。
     */
    public ExampleMeiliConfig() {
    }

    /**
     * 写前回调 bean：去除书名首尾空白。
     *
     * <p>以命名类而非 lambda 声明：回调注册表从实现类的泛型签名解析目标实体，
     * lambda 实例的泛型实参被 JVM 擦除后不可解析（见限制清单）。
     *
     * @return 作用于 {@link Book} 的转换前回调
     */
    @Bean
    BeforeConvertCallback<Book> trimBookTitle() {
        return new TrimBookTitle();
    }

    /** 书名规整实现：record 不可变，命中时重建返回。 */
    static final class TrimBookTitle implements BeforeConvertCallback<Book> {

        /** 无状态实现；目标实体类型由类声明的泛型实参承载。 */
        TrimBookTitle() {
        }

        @Override
        public Book onBeforeConvert(Book entity, String indexName) {
            return entity.title() == null ? entity
                    : new Book(entity.id(), entity.title().strip(), entity.overview(),
                            entity.author(), entity.tags(), entity.genre(), entity.price(),
                            entity.publishedAt(), entity.internalNote());
        }
    }
}
