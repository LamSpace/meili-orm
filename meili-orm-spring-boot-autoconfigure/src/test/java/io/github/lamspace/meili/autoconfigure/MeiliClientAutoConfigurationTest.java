package io.github.lamspace.meili.autoconfigure;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import com.meilisearch.sdk.json.GsonJsonHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.Ordered;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * L2 conditions-chain tests for the client layer auto configuration: property binding and
 * defaults, master switch, enum rejection. Written test-first against
 * {@link MeiliClientAutoConfiguration}.
 */
class MeiliClientAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MeiliClientAutoConfiguration.class);

    @Test
    void propertyDefaultsAreDocumentedValues() {
        runner.run(ctx -> {
            MeiliProperties props = ctx.getBean(MeiliProperties.class);
            assertThat(props.isEnabled()).isTrue();
            assertThat(props.getUrl()).isEqualTo("http://localhost:7700");
            assertThat(props.getApiKey()).isEmpty();
            assertThat(props.isWaitTask()).isFalse();
            assertThat(props.getWaitTimeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(props.getIndex().getAutoInit())
                    .isEqualTo(MeiliProperties.AutoInit.CREATE_IF_MISSING);
            assertThat(props.getIndex().getOnSettingsDrift())
                    .isEqualTo(MeiliProperties.Drift.WARN);
        });
    }

    @Test
    void defaultsWireClientAndConfigBeans() {
        runner.withPropertyValues("meili.url=http://x:1", "meili.api-key=k")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(Client.class);
                    assertThat(ctx).hasSingleBean(Config.class);
                    assertThat(ctx).hasSingleBean(MeiliConnectionDetails.class);
                    Config config = ctx.getBean(Config.class);
                    assertThat(config.getHostUrl()).isEqualTo("http://x:1");
                    assertThat(config.getApiKey()).isEqualTo("k");
                    assertThat(ctx.getBean(MeiliConnectionDetails.class).getUrl())
                            .isEqualTo("http://x:1");
                });
    }

    @Test
    void disabledSwitchRemovesEverything() {
        runner.withPropertyValues("meili.enabled=false")
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(Client.class);
                    assertThat(ctx).doesNotHaveBean(Config.class);
                    assertThat(ctx).doesNotHaveBean(MeiliConnectionDetails.class);
                });
    }

    @Test
    void invalidDriftEnumValueFailsBinding() {
        runner.withPropertyValues("meili.index.on-settings-drift=bogus")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void kebabCaseIndexPropertiesBind() {
        runner.withPropertyValues(
                        "meili.index.auto-init=sync-settings",
                        "meili.index.on-settings-drift=fail")
                .run(ctx -> {
                    MeiliProperties props = ctx.getBean(MeiliProperties.class);
                    assertThat(props.getIndex().getAutoInit())
                            .isEqualTo(MeiliProperties.AutoInit.SYNC_SETTINGS);
                    assertThat(props.getIndex().getOnSettingsDrift())
                            .isEqualTo(MeiliProperties.Drift.FAIL);
                });
    }

    @Test
    void userClientBeanBacksOff() {
        Client userClient = mock(Client.class);
        runner.withBean(Client.class, () -> userClient)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(Client.class);
                    assertThat(ctx.getBean(Client.class)).isSameAs(userClient);
                });
    }

    @Test
    void customizersApplyInOrderToTheSameConfig() {
        List<String> calls = Collections.synchronizedList(new ArrayList<>());
        List<Config> seen = Collections.synchronizedList(new ArrayList<>());
        runner.withBean("custA", MeiliConfigCustomizer.class, () -> new RecordingCustomizer(1, calls, seen))
                .withBean("custB", MeiliConfigCustomizer.class, () -> new RecordingCustomizer(2, calls, seen))
                .run(ctx -> {
                    assertThat(calls).containsExactly("1", "2");
                    assertThat(seen).hasSize(2)
                            .allSatisfy(config -> assertThat(config).isSameAs(ctx.getBean(Config.class)));
                });
    }

    @Test
    void userConnectionDetailsWinOverProperties() {
        runner.withBean(MeiliConnectionDetails.class, () -> new MeiliConnectionDetails() {
                    @Override
                    public String getUrl() {
                        return "http://manual:9";
                    }

                    @Override
                    public String getApiKey() {
                        return "mk";
                    }
                })
                .withPropertyValues("meili.url=http://ignored:1", "meili.api-key=ignored")
                .run(ctx -> {
                    Config config = ctx.getBean(Config.class);
                    assertThat(config.getHostUrl()).isEqualTo("http://manual:9");
                    assertThat(config.getApiKey()).isEqualTo("mk");
                    assertThat(ctx).doesNotHaveBean(PropertiesMeiliConnectionDetails.class);
                });
    }

    /**
     * Assembly-line sentinel: the client must keep the SDK default JSON handler. Entity
     * read/write flows through raw-string APIs; the SDK typed APIs depend on adapters the
     * default handler registers, so a wired-in alternative handler would silently break them.
     * Complements the server-side evidence kept in the core test suite.
     */
    @Test
    void defaultJsonHandlerIsRetainedOnTheWiredConfig() {
        runner.run(ctx -> assertThat(ctx.getBean(Config.class).getJsonHandler())
                .isInstanceOf(GsonJsonHandler.class));
    }

    /** Ordered customizer that records invocation sequence and the instance under customization. */
    private static final class RecordingCustomizer implements MeiliConfigCustomizer, Ordered {

        /** The declared order value. */
        private final int order;

        /** Invocation log shared across customizers. */
        private final List<String> calls;

        /** Config instances handed to the customization callback. */
        private final List<Config> seen;

        /**
         * @param order relative sequence for ordered-stream application
         * @param calls shared invocation log
         * @param seen  shared config-instance log
         */
        RecordingCustomizer(int order, List<String> calls, List<Config> seen) {
            this.order = order;
            this.calls = calls;
            this.seen = seen;
        }

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public void customize(Config config) {
            calls.add(String.valueOf(order));
            seen.add(config);
        }
    }
}
