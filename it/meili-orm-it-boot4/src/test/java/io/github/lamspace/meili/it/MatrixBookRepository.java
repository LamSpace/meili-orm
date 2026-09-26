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
package io.github.lamspace.meili.it;

import io.github.lamspace.meili.repository.MeiliRepository;
import io.github.lamspace.meili.repository.MeiliQuery;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

/** Matrix repository fixture: one path each for derived, full-text, paginated, and annotated queries. */
public interface MatrixBookRepository extends MeiliRepository<MatrixBook, Long> {

    /**
     * @param genre genre
     * @return matched documents
     */
    List<MatrixBook> findByGenre(String genre);

    /**
     * @param t full-text fragment
     * @return matched documents
     */
    List<MatrixBook> findByTitleContaining(String t);

    /**
     * @param genre genre
     * @param pg    pagination and sorting carrier
     * @return one page of results
     */
    Page<MatrixBook> findPageByGenreOrderByPriceAsc(String genre, Pageable pg);

    /**
     * @param min price lower bound
     * @return matched documents
     */
    @MeiliQuery(filter = "price > :min")
    List<MatrixBook> expensive(@Param("min") Double min);
}
