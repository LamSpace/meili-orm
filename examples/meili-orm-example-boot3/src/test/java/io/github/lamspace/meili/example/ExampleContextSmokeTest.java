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
package io.github.lamspace.meili.example;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.lamspace.meili.core.event.MeiliEntityCallbacks;
import io.github.lamspace.meili.core.operations.MeiliSearchOperations;
import io.github.lamspace.meili.core.serialize.Jackson2DocumentSerializer;
import io.github.lamspace.meili.core.serialize.MeiliDocumentSerializer;
import io.github.lamspace.meili.example.web.BookController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Assembly-time anti-corruption smoke (zero external dependencies): with auto-init=none + a
 * non-listening port, it verifies that the startup context completes full assembly without any
 * network contact — the scanner finds the entities, controller/callback/serializer/template beans
 * are all present, and the default serialization channel is Jackson2 (the demo does not pull in
 * the optional Jackson3 module).
 *
 * <p>Real-server behavior verification lives elsewhere (carried by the demo README's curl smoke
 * script); this test only guards assembly regressions: any wiring break goes red at mvn verify,
 * no Docker needed.
 */
@SpringBootTest(properties = {
        "meili.url=http://127.0.0.1:1",
        "meili.api-key=smoke-dummy-key",
        "meili.index.auto-init=none"
})
class ExampleContextSmokeTest {

    @Autowired
    private BookController controller;

    @Autowired
    private MeiliSearchOperations operations;

    @Autowired
    private MeiliEntityCallbacks callbacks;

    @Autowired
    private MeiliDocumentSerializer serializer;

    @Test
    void wiringCompletesWithoutServerContact() {
        assertThat(controller).isNotNull();
        assertThat(operations).isNotNull();
        assertThat(serializer).isInstanceOf(Jackson2DocumentSerializer.class);
        assertThat(callbacks.registeredCount()).isGreaterThanOrEqualTo(1);
    }
}
