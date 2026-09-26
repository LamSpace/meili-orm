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

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Binding target for the whole {@code meili.*} configuration namespace.
 *
 * <p>Contract tier: this is a pure configuration data carrier — no behavior, no thread
 * constraints beyond normal bean-publishing visibility. Values are bound once during context
 * startup (relaxed binding: kebab-case keys map onto camelCase fields) and read concurrently
 * afterwards; mutation after binding is not supported. Unknown or malformed values — e.g. an
 * enum constant outside the declared sets — fail binding and therefore startup (fail-fast at
 * the property level).
 *
 * <p>Deliberately absent: connect/read timeout properties. The official SDK client builds its
 * HTTP transport internally and offers no injection point, so exposing timeouts here would
 * advertise an unsupported knob.
 *
 * @see MeiliConnectionDetails
 */
@ConfigurationProperties(prefix = "meili")
public class MeiliProperties {

    /** Master switch: {@code false} retires every meili-orm managed bean. */
    private boolean enabled = true;

    /** MeiliSearch service base URL, including scheme. */
    private String url = "http://localhost:7700";

    /** API key (master key or tenant key); empty string means unauthenticated. */
    private String apiKey = "";

    /** Whether write operations block until their server task reaches a terminal state. */
    private boolean waitTask = false;

    /** Upper bound for any single task wait (explicit {@code awaitTask} or wait-task writes). */
    private Duration waitTimeout = Duration.ofSeconds(5);

    /**
     * Extra User-Agent tokens ({@code meili.client-agents}). Applied at client construction:
     * the SDK prefixes its own version token and joins everything with {@code ;}, so the final
     * header is {@code <sdk version token>;<entry>;<entry>...}. The default single token
     * identifies meili-orm traffic; an explicitly empty value falls back to the pure SDK
     * User-Agent.
     */
    private List<String> clientAgents = List.of("meili-orm");

    /** Index lifecycle settings under {@code meili.index.*}. */
    private final Index index = new Index();

    /**
     * Creates an instance carrying the documented defaults until property binding overwrites
     * individual values.
     */
    public MeiliProperties() {
    }

    /**
     * Gets the master switch.
     *
     * @return whether meili-orm auto configuration is active
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets the master switch.
     *
     * @param enabled {@code false} retires every meili-orm managed bean
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Gets the service URL.
     *
     * @return the configured MeiliSearch base URL, including scheme
     */
    public String getUrl() {
        return url;
    }

    /**
     * Sets the service URL.
     *
     * @param url the MeiliSearch base URL, including scheme
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Gets the API key.
     *
     * @return the configured API key; empty for an unauthenticated server
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * Sets the API key.
     *
     * @param apiKey the API key; empty string means unauthenticated
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Gets the write-await flag.
     *
     * @return whether write operations block until their server task finishes
     */
    public boolean isWaitTask() {
        return waitTask;
    }

    /**
     * Sets the write-await flag.
     *
     * @param waitTask {@code true} to make writes synchronous up to {@link #getWaitTimeout()}
     */
    public void setWaitTask(boolean waitTask) {
        this.waitTask = waitTask;
    }

    /**
     * Gets the per-wait timeout bound.
     *
     * @return the upper bound for any single task wait
     */
    public Duration getWaitTimeout() {
        return waitTimeout;
    }

    /**
     * Sets the per-wait timeout bound.
     *
     * @param waitTimeout the upper bound for explicit {@code awaitTask} and wait-task writes
     */
    public void setWaitTimeout(Duration waitTimeout) {
        this.waitTimeout = waitTimeout;
    }

    /**
     * Gets the extra User-Agent tokens.
     *
     * @return the configured {@code meili.client-agents} entries; never {@code null}
     */
    public List<String> getClientAgents() {
        return clientAgents;
    }

    /**
     * Sets the extra User-Agent tokens.
     *
     * @param clientAgents entries appended after the SDK's own version token; empty keeps the
     *                     pure SDK User-Agent
     */
    public void setClientAgents(List<String> clientAgents) {
        this.clientAgents = clientAgents;
    }

    /**
     * Gets the nested index lifecycle group.
     *
     * @return the {@code meili.index.*} values; never {@code null}
     */
    public Index getIndex() {
        return index;
    }

    /**
     * Startup-time index lifecycle policy group ({@code meili.index.*}).
     */
    public static class Index {

        /** What initialization does for each scanned entity. */
        private AutoInit autoInit = AutoInit.CREATE_IF_MISSING;

        /** What a detected settings drift does to startup. */
        private Drift onSettingsDrift = Drift.WARN;

        /**
         * Creates an instance carrying the documented defaults until binding overwrites them.
         */
        public Index() {
        }

        /**
         * Gets the initialization mode.
         *
         * @return the startup index initialization mode
         */
        public AutoInit getAutoInit() {
            return autoInit;
        }

        /**
         * Sets the initialization mode.
         *
         * @param autoInit the startup index initialization mode
         */
        public void setAutoInit(AutoInit autoInit) {
            this.autoInit = autoInit;
        }

        /**
         * Gets the drift strategy.
         *
         * @return what a detected settings drift does to startup
         */
        public Drift getOnSettingsDrift() {
            return onSettingsDrift;
        }

        /**
         * Sets the drift strategy.
         *
         * @param onSettingsDrift what a detected settings drift does to startup
         */
        public void setOnSettingsDrift(Drift onSettingsDrift) {
            this.onSettingsDrift = onSettingsDrift;
        }
    }

    /**
     * Startup-time index initialization modes, bound from kebab-case values.
     */
    public enum AutoInit {

        /** No index interaction at startup; the server must already be provisioned. */
        NONE,

        /** Create and fully project settings for missing indexes; existing ones are only diffed. */
        CREATE_IF_MISSING,

        /** Diff projected settings against every existing index (missing indexes are still created). */
        SYNC_SETTINGS
    }

    /**
     * Strategies applied when projected settings drift from the server's current settings.
     */
    public enum Drift {

        /** Log a warning listing the drifted keys; change nothing. */
        WARN,

        /** Push the projected settings (may trigger a server-side full reindex; warns when it can). */
        APPLY,

        /** Fail startup with a mapping exception without writing anything. */
        FAIL
    }
}
