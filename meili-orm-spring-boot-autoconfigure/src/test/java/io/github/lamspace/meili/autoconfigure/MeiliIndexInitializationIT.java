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
package io.github.lamspace.meili.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lamspace.meili.core.MeiliContainer;
import io.github.lamspace.meili.core.exception.MeiliIndexAccessException;
import io.github.lamspace.meili.autoconfigure.it.v1.V1Config;
import io.github.lamspace.meili.autoconfigure.it.v2.V2Config;
import io.github.lamspace.meili.core.internal.MeiliRawGateway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-server IT for startup index initialization against the pinned v1.49.0 container:
 * create-if-missing provisioning of a scanned entity, settings drift detection and an
 * {@code apply} push that reconciles a newly filterable field, and the fail-fast behavior
 * when the configured server is unreachable.
 */
class MeiliIndexInitializationIT {

    /** Index claimed by both fixture entity generations. */
    private static final String INDEX = "it_init_books";

    /** Envelope reader for assertions on the server settings document. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * @param config  Boot-shaped configuration to run
     * @param url     meili.url value
     * @param extra   additional property assignments
     * @return a runner wired against the given configuration
     */
    private static ApplicationContextRunner runner(Class<?> config, String url, String... extra) {
        List<String> props = new ArrayList<>(List.of(
                "meili.url=" + url,
                "meili.api-key=" + MeiliContainer.MASTER_KEY,
                "meili.wait-task=true"));
        props.addAll(List.of(extra));
        return new ApplicationContextRunner()
                .withUserConfiguration(config)
                .withPropertyValues(props.toArray(String[]::new));
    }

    /**
     * @param gateway gateway to read live settings from
     * @return the server's current filterableAttributes for {@link #INDEX}
     * @throws Exception on JSON parsing of the settings envelope
     */
    private static List<String> filterable(MeiliRawGateway gateway) throws Exception {
        List<String> out = new ArrayList<>();
        MAPPER.readTree(gateway.getSettings(INDEX))
                .path("filterableAttributes").forEach(node -> out.add(node.asText()));
        return out;
    }

    @Test
    void createThenDriftApplyReconcilesServer() throws Exception {
        String url = MeiliContainer.url();

        runner(V1Config.class, url).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            MeiliRawGateway gateway = ctx.getBean(MeiliRawGateway.class);
            assertThat(gateway.indexExists(INDEX)).isTrue();
            assertThat(filterable(gateway)).containsExactly("genre");
        });

        runner(V1Config.class, url,
                "meili.index.auto-init=sync-settings",
                "meili.index.on-settings-drift=apply")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(filterable(ctx.getBean(MeiliRawGateway.class))).containsExactly("genre");
                });

        runner(V2Config.class, url,
                "meili.index.auto-init=sync-settings",
                "meili.index.on-settings-drift=apply")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(filterable(ctx.getBean(MeiliRawGateway.class)))
                            .containsExactlyInAnyOrder("genre", "price");
                });
    }

    @Test
    void unreachableServerFailsStartup() {
        runner(V1Config.class, "http://127.0.0.1:1")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(causeChain(ctx.getStartupFailure()))
                            .anySatisfy(t -> assertThat(t).isInstanceOf(MeiliIndexAccessException.class));
                });
    }

    /**
     * Walks a throwable's cause chain.
     *
     * @param failure context startup failure, possibly {@code null}
     * @return every link of the chain, root included
     */
    private static List<Throwable> causeChain(Throwable failure) {
        List<Throwable> out = new ArrayList<>();
        for (Throwable t = failure; t != null && t != t.getCause(); t = t.getCause()) {
            out.add(t);
        }
        return out;
    }
}
