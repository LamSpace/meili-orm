/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.lamspace.meili.example.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.lamspace.meili.core.operations.MeiliSearchOperations;
import io.github.lamspace.meili.core.query.MeiliQuery;
import io.github.lamspace.meili.core.query.MeiliSearchResult;
import io.github.lamspace.meili.example.domain.Book;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo REST surface: import / search (full q+filter+sort+pagination+facet chain) / single read /
 * delete / raw escape hatch.
 *
 * <p>Business code depends only on {@link MeiliSearchOperations} and its own query IR and never
 * touches SDK types — exactly the starter's target user shape. Preset-data deserialization uses an
 * <b>explicitly self-built</b> Jackson 2 mapper rather than an injected container ObjectMapper:
 * the Boot 4 container defaults to Jackson 3, and injection would split behavior between the boot3
 * and boot4 shells; the explicit Jackson 2 mapper guarantees both shells read the same data.json
 * byte-for-byte identically.
 */
@RestController
@RequestMapping("/api/books")
public class BookController {

    /** Mapper dedicated to preset data: JSR-310 support + lenient about unknown keys. */
    private static final ObjectMapper DATA_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** meili-orm template: every scenario reads and writes through its raw channel. */
    private final MeiliSearchOperations operations;

    /**
     * Injects the auto-configured operations template.
     *
     * @param operations the starter-assembled search/document operations template
     */
    public BookController(MeiliSearchOperations operations) {
        this.operations = operations;
    }

    /**
     * Bulk-imports the preset book list (upsert semantics; with wait-task=true, a return means read-after-write).
     *
     * @return receipt of how many books were imported
     * @throws java.io.IOException if the preset data cannot be read
     */
    @PostMapping("/import")
    public Map<String, Object> importBooks() throws java.io.IOException {
        List<Book> books;
        try (var in = BookController.class.getResourceAsStream("/data.json")) {
            books = DATA_MAPPER.readValue(in, new TypeReference<List<Book>>() { });
        }
        operations.saveAll(books);
        return Map.of("imported", books.size());
    }

    /**
     * Full-chain search: full-text q + equality/range filters + sorting + page-number pagination + genre facets.
     *
     * @param q        full-text query
     * @param genre    optional equality filter on genre
     * @param minPrice optional lower price-bound filter
     * @param sort     sort expression, defaults to price:asc
     * @param page     page number (1-based), defaults to 1
     * @param size     hits per page, defaults to 10
     * @return response body with hits, pagination metadata, and facet distribution
     */
    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam String q,
                                      @RequestParam(required = false) String genre,
                                      @RequestParam(required = false) Double minPrice,
                                      @RequestParam(defaultValue = "price:asc") String sort,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "10") int size) {
        var query = MeiliQuery.query(q).sort(sort).page(page).hitsPerPage(size).facets("genre");
        if (genre != null) {
            query.filterAdd("genre = \"" + genre + "\"");
        }
        if (minPrice != null) {
            query.filterAdd("price > " + minPrice);
        }
        MeiliSearchResult<Book> result = operations.search(query, Book.class);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("hits", result.getHits());
        body.put("estimatedTotalHits", result.getEstimatedTotalHits());
        body.put("page", result.getPage());
        body.put("hitsPerPage", result.getHitsPerPage());
        body.put("totalPages", result.getTotalPages());
        body.put("facetDistribution", result.getFacetDistribution());
        body.put("processingTimeMs", result.getProcessingTimeMs());
        return body;
    }

    /**
     * Single read by primary key (deserialized via the raw channel, Long precision lossless).
     *
     * @param id primary key
     * @return 200 + entity, or 404
     */
    @GetMapping("/{id}")
    public ResponseEntity<Book> byId(@PathVariable Long id) {
        return operations.findById(id, Book.class)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Delete by primary key (read-after-write semantics are carried by wait-task).
     *
     * @param id primary key
     * @return 204
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        operations.deleteById(id, Book.class);
        return ResponseEntity.noContent().build();
    }

    /**
     * Escape hatch: passes the server's raw JSON through verbatim, with no mapping.
     *
     * @param q full-text query
     * @return the raw response body
     */
    @GetMapping(value = "/raw", produces = MediaType.APPLICATION_JSON_VALUE)
    public String raw(@RequestParam String q) {
        return operations.search(q, Book.class).getRawJson();
    }
}
