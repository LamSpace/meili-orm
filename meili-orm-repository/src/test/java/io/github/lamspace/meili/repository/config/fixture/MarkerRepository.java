package io.github.lamspace.meili.repository.config.fixture;

import io.github.lamspace.meili.repository.MeiliRepository;
import org.springframework.data.repository.NoRepositoryBean;

/** 注册器测试夹具：标注 {@code @NoRepositoryBean}，不得被注册。 */
@NoRepositoryBean
public interface MarkerRepository extends MeiliRepository<FxBook, Long> {
}
