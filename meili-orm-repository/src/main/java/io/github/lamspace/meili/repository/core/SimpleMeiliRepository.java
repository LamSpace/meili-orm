package io.github.lamspace.meili.repository.core;

import io.github.lamspace.meili.core.mapping.MeiliPersistentEntity;
import io.github.lamspace.meili.core.operations.MeiliSearchOperations;
import io.github.lamspace.meili.core.query.DocumentsFetchQuery;
import io.github.lamspace.meili.core.query.MeiliQuery;
import io.github.lamspace.meili.core.query.MeiliSearchResult;
import io.github.lamspace.meili.repository.MeiliRepository;
import io.github.lamspace.meili.repository.support.MeiliPropertyPaths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Default {@link MeiliRepository} implementation: every data-path call is delegated to
 * {@link MeiliSearchOperations} so callbacks, serialization, task-waiting and the exception
 * family behave exactly as when the template is used directly.
 *
 * <p><b>State machine / contract boundaries.</b> Instances are immutable after construction
 * (bound operations + entity metamodel + domain type) and safe for concurrent use. Errors
 * from the operations layer propagate without re-wrapping.
 *
 * <p><b>Unbounded-read caveat.</b> {@link #findAll()} and {@link #findAll(Sort)} ride the
 * document-fetch channel, which the server caps at the index's {@code maxTotalHits}
 * (default 1000). When a fetch returns a full cap-sized batch the repository logs a WARN
 * declaring the possible truncation; it never claims completeness it cannot prove.
 * Paged reads go through the search channel and expose the server's estimated totals via
 * {@link Page} (see {@code repository-layer} capability contract; estimates are not exact).
 *
 * <p><b>Bulk-delete cost.</b> {@link #deleteAll(Iterable)} and
 * {@link #deleteAllById(Iterable)} issue one delete request per id — deliberate for now,
 * because batching would widen the core gateway surface; the limitation is documented.
 *
 * @param <T>  domain entity type
 * @param <ID> primary-key type
 */
public class SimpleMeiliRepository<T, ID> implements MeiliRepository<T, ID> {

    /** Shared logger for cap-truncation warnings. */
    private static final Logger log = LoggerFactory.getLogger(SimpleMeiliRepository.class);

    /** Server default for {@code pagination.maxTotalHits} — the observed fetch ceiling. */
    static final int FETCH_CEILING = 1000;

    /** Delegated template carrying all data-path behavior. */
    private final MeiliSearchOperations operations;
    /** Entity metamodel (index name, id accessor). */
    private final MeiliPersistentEntity entity;
    /** Domain type shortcut. */
    private final Class<T> domainType;

    /**
     * Binds a repository instance to one entity type.
     *
     * @param operations template to delegate to; required
     * @param entity     metamodel of the domain type; required
     */
    @SuppressWarnings("unchecked")
    public SimpleMeiliRepository(MeiliSearchOperations operations, MeiliPersistentEntity entity) {
        this.operations = java.util.Objects.requireNonNull(operations, "operations");
        this.entity = java.util.Objects.requireNonNull(entity, "entity");
        this.domainType = (Class<T>) entity.getType();
    }

    @Override
    public <S extends T> S save(S entity) {
        return operations.save(entity);
    }

    @Override
    public <S extends T> List<S> saveAll(Iterable<S> entities) {
        return operations.saveAll(entities);
    }

    @Override
    public Optional<T> findById(ID id) {
        return operations.findById(id, domainType);
    }

    @Override
    public boolean existsById(ID id) {
        return findById(id).isPresent();
    }

    @Override
    public List<T> findAll() {
        List<T> docs = operations.findAll(domainType, DocumentsFetchQuery.fetchQuery());
        warnIfAtCeiling(docs.size(), "findAll()");
        return docs;
    }

    @Override
    public List<T> findAll(Sort sort) {
        DocumentsFetchQuery q = DocumentsFetchQuery.fetchQuery();
        if (sort != null && sort.isSorted()) {
            q.sort(renderSort(sort));
        }
        List<T> docs = operations.findAll(domainType, q);
        warnIfAtCeiling(docs.size(), "findAll(Sort)");
        return docs;
    }

    @Override
    public List<T> findAllById(Iterable<ID> ids) {
        List<T> out = new ArrayList<>();
        for (ID id : ids) {
            findById(id).ifPresent(out::add);
        }
        return out;
    }

    @Override
    public long count() {
        return operations.count(domainType);
    }

    @Override
    public void deleteById(ID id) {
        operations.deleteById(id, domainType);
    }

    @Override
    public void delete(T entity) {
        operations.deleteById(this.entity.idValue(entity), domainType);
    }

    @Override
    public void deleteAllById(Iterable<? extends ID> ids) {
        for (ID id : ids) {
            deleteById(id);
        }
    }

    @Override
    public void deleteAll(Iterable<? extends T> entities) {
        for (T e : entities) {
            delete(e);
        }
    }

    @Override
    public void deleteAll() {
        operations.deleteAll(domainType);
    }

    /**
     * Paged browse over the search channel: zero-based {@link Pageable} maps to Meili's
     * 1-based page mode, and {@link Page} totals carry the server's <em>estimated</em> hit
     * count (exact totals are not a Meili guarantee).
     *
     * @param pageable page cursor and optional sort
     * @return one page of entities plus estimated pagination metadata
     */
    @Override
    public Page<T> findAll(Pageable pageable) {
        MeiliQuery q = MeiliQuery.query(null)
                .page(pageable.getPageNumber() + 1)
                .hitsPerPage(pageable.getPageSize());
        if (pageable.getSort().isSorted()) {
            q.sort(renderSort(pageable.getSort()));
        }
        MeiliSearchResult<T> result = operations.search(q, domainType);
        List<T> hits = result.getHits();
        Long total = result.getEstimatedTotalHits() != null ? result.getEstimatedTotalHits()
                : result.getTotalHits();
        long reported = total != null ? total : hits.size();
        if (total == null) {
            log.debug("检索响应未携带总数估计（estimatedTotalHits/totalHits 均为空），"
                    + "以本页命中数 {} 作为 Page 总数（索引 {}）", hits.size(), entity.getIndexName());
        }
        return new PageImpl<>(hits, pageable, reported);
    }

    /**
     * Renders a commons {@link Sort} into Meili sort expressions
     * ({@code documentPath:asc|desc}), bridging each order property through
     * {@link MeiliPropertyPaths} so renamed and nested fields address their projection paths.
     *
     * @param sort non-empty sort
     * @return sort expression array for {@link MeiliQuery#sort(String...)}
     * @throws IllegalArgumentException when an order property cannot be resolved
     */
    private String[] renderSort(Sort sort) {
        List<String> out = new ArrayList<>();
        for (Sort.Order order : sort) {
            String path = MeiliPropertyPaths.resolveDotted(domainType,
                    "Pageable/Sort 属性 " + order.getProperty(), order.getProperty());
            out.add(path + ":" + (order.isDescending() ? "desc" : "asc"));
        }
        return out.toArray(new String[0]);
    }

    /** WARN-declares a full-cap fetch so a caller never mistakes a capped read for "all". */
    private void warnIfAtCeiling(int size, String call) {
        if (size >= FETCH_CEILING) {
            log.warn("索引 {} 的 {} 返回 {} 条，已达 documents/fetch 上限"
                    + "（pagination.maxTotalHits，默认 1000）——结果可能被截断，请改用分页查询",
                    entity.getIndexName(), call, size);
        }
    }
}
