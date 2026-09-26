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
package io.github.lamspace.meili.core.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import com.meilisearch.sdk.exceptions.MeilisearchApiException;
import com.meilisearch.sdk.model.Task;
import io.github.lamspace.meili.core.exception.MeiliIndexAccessException;
import io.github.lamspace.meili.core.exception.MeiliTaskTimeoutException;
import io.github.lamspace.meili.core.query.DocumentsFetchQuery;
import io.github.lamspace.meili.core.query.MeiliQuery;
import io.github.lamspace.meili.core.task.MeiliTask;
import io.github.lamspace.meili.core.task.MeiliTaskStatus;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * {@link MeiliRawGateway} over the official Java client plus a narrow direct-HTTP helper.
 *
 * <p>Channel split (fixed architecture decision, see class docs of the operations layer):
 * document writes/reads/searches and task/index lifecycle ride the client's raw-string
 * APIs ({@code updateDocuments(String)}, {@code getRawDocument}, {@code rawSearch},
 * {@code getTask}) so no entity ever passes through the client's default Gson Map path —
 * the lossy channel for 64-bit integers. The endpoints the client either omits or exposes
 * only through typed models ({@code documents/count}, {@code documents/fetch} with sort,
 * settings raw read/patch) go through an OkHttp helper using the very same connection
 * material ({@code client.getConfig()} host/key). Settings therefore return the server's
 * real JSON, byte for byte — the drift-diff ground truth — instead of a re-serialized
 * model round-trip.
 *
 * <p>Errors from either channel pass through {@link MeiliErrors} exclusively; task uids
 * are {@code int} end to end. Thread model: {@link Client} and {@link OkHttpClient} are
 * both shared-safe; this gateway adds no mutable state.
 */
public final class SdkMeiliRawGateway implements MeiliRawGateway {

    /** JSON content type for helper bodies. */
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    /** Helper for request bodies and response envelopes (never for entities). */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Task polling cadence for {@link #awaitTask}. */
    private static final long POLL_INTERVAL_MS = 50;

    /** Server error code for a missing document. */
    private static final String DOCUMENT_NOT_FOUND = "document_not_found";

    /** Server error code for a missing index. */
    private static final String INDEX_NOT_FOUND = "index_not_found";

    /** Shared official client (default Gson handler retained — raw APIs are the contract). */
    private final Client client;
    /** HTTP helper client for the non-client-exposed endpoints. */
    private final OkHttpClient http;
    /** Base URL of the server, from the client config. */
    private final String baseUrl;
    /** API key of the server, from the client config. */
    private final String apiKey;

    /**
     * Wraps one configured client. The config must be the exact instance the client was
     * built from (the client itself exposes no accessor for it); it supplies the direct
     * HTTP helper with url and key.
     *
     * @param client configured official client (url + key, default JSON handler)
     * @param config the same config handed to the client constructor
     */
    public SdkMeiliRawGateway(Client client, Config config) {
        this.client = client;
        String url = config.getHostUrl();
        this.baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.apiKey = config.getApiKey();
        this.http = new OkHttpClient();
    }

    @Override
    public int updateDocuments(String indexUid, String documentsJson) {
        try {
            return client.index(indexUid).updateDocuments(documentsJson).getTaskUid();
        } catch (RuntimeException e) {
            throw MeiliErrors.translate(e);
        }
    }

    @Override
    public Optional<String> fetchRawDocument(String indexUid, String id) {
        try {
            return Optional.of(client.index(indexUid).getRawDocument(id));
        } catch (MeilisearchApiException e) {
            if (DOCUMENT_NOT_FOUND.equals(e.getCode())) {
                return Optional.empty();
            }
            throw MeiliErrors.translate(e);
        } catch (RuntimeException e) {
            throw MeiliErrors.translate(e);
        }
    }

    @Override
    public List<String> fetchRawDocuments(String indexUid, DocumentsFetchQuery query) {
        ObjectNode body = MAPPER.createObjectNode();
        if (query.getFilterDsl() != null) {
            body.put("filter", query.getFilterDsl());
        }
        if (!query.getSort().isEmpty()) {
            body.putArray("sort").addAll(stringArray(query.getSort()));
        }
        if (!query.getFields().isEmpty()) {
            body.putArray("fields").addAll(stringArray(query.getFields()));
        }
        if (query.getOffset() != null) {
            body.put("offset", query.getOffset());
        }
        if (query.getLimit() != null) {
            body.put("limit", query.getLimit());
        }
        String response = execute("POST", new String[]{"indexes", indexUid, "documents", "fetch"},
                null, body.toString());
        List<String> out = new ArrayList<>();
        for (JsonNode doc : readTree(response, indexUid).path("results")) {
            out.add(doc.toString());
        }
        return out;
    }

    @Override
    public long count(String indexUid) {
        // documents/count route is unavailable on this server generation (observed to be
        // captured by documents/{id} as document_not_found); stats is the equivalent integer source of truth
        String response = execute("GET", new String[]{"indexes", indexUid, "stats"}, null, null);
        return readTree(response, indexUid).path("numberOfDocuments").asLong();
    }

    @Override
    public String rawSearch(String indexUid, MeiliQuery query) {
        try {
            return client.index(indexUid).rawSearch(SdkQueryTranslator.toSearchRequest(query));
        } catch (RuntimeException e) {
            throw MeiliErrors.translate(e);
        }
    }

    @Override
    public boolean indexExists(String indexUid) {
        try {
            client.getIndex(indexUid);
            return true;
        } catch (MeilisearchApiException e) {
            if (INDEX_NOT_FOUND.equals(e.getCode())) {
                return false;
            }
            throw MeiliErrors.translate(e);
        } catch (RuntimeException e) {
            throw MeiliErrors.translate(e);
        }
    }

    @Override
    public int createIndex(String indexUid, String primaryKey) {
        try {
            return client.createIndex(indexUid, primaryKey).getTaskUid();
        } catch (RuntimeException e) {
            throw MeiliErrors.translate(e);
        }
    }

    @Override
    public int deleteIndex(String indexUid) {
        try {
            return client.deleteIndex(indexUid).getTaskUid();
        } catch (RuntimeException e) {
            throw MeiliErrors.translate(e);
        }
    }

    @Override
    public int updateSettings(String indexUid, String settingsJson) {
        String response = execute("PATCH", new String[]{"indexes", indexUid, "settings"},
                null, settingsJson);
        return readTree(response, indexUid).path("taskUid").asInt();
    }

    @Override
    public String getSettings(String indexUid) {
        return execute("GET", new String[]{"indexes", indexUid, "settings"}, null, null);
    }

    @Override
    public int deleteDocument(String indexUid, String id) {
        try {
            return client.index(indexUid).deleteDocument(id).getTaskUid();
        } catch (RuntimeException e) {
            throw MeiliErrors.translate(e);
        }
    }

    @Override
    public int deleteAllDocuments(String indexUid) {
        try {
            return client.index(indexUid).deleteAllDocuments().getTaskUid();
        } catch (RuntimeException e) {
            throw MeiliErrors.translate(e);
        }
    }

    @Override
    public void awaitTask(int taskUid, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            MeiliTask task = getTask(taskUid);
            if (task.isTerminal()) {
                if (task.status() == MeiliTaskStatus.SUCCEEDED) {
                    return;
                }
                throw new MeiliIndexAccessException("task_" + task.status().name().toLowerCase(),
                        "task did not succeed: uid=" + task.uid() + ", status=" + task.status()
                                + ", error=" + task.errorMessage(), null);
            }
            if (System.nanoTime() >= deadline) {
                throw new MeiliTaskTimeoutException(taskUid, timeout.toMillis());
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MeiliIndexAccessException(null, "waiting for task interrupted: uid=" + taskUid, e);
            }
        }
    }

    @Override
    public MeiliTask getTask(int taskUid) {
        try {
            Task task = client.getTask(taskUid);
            return new MeiliTask(task.getUid(),
                    MeiliTaskStatus.valueOf(task.getStatus().name()),
                    task.getType(),
                    task.getIndexUid(),
                    task.getError() == null ? null : task.getError().getMessage());
        } catch (RuntimeException e) {
            throw MeiliErrors.translate(e);
        }
    }

    /**
     * Performs one direct HTTP call on the helper channel and returns the response body.
     *
     * @param method    HTTP method
     * @param segments  path segments (each URL-encoded by {@link HttpUrl})
     * @param queries   query parameter name/value pairs, or {@code null}
     * @param jsonBody  request body JSON, or {@code null} for bodyless methods
     * @return verbatim response body text
     * @throws MeiliIndexAccessException on transport failure or a non-2xx response (server
     *         error code parsed from the standard error envelope when present)
     */
    private String execute(String method, String[] segments, String[] queries, String jsonBody) {
        HttpUrl.Builder url = HttpUrl.get(baseUrl).newBuilder();
        for (String segment : segments) {
            url.addPathSegment(segment);
        }
        if (queries != null) {
            for (int i = 0; i + 1 < queries.length; i += 2) {
                url.addQueryParameter(queries[i], queries[i + 1]);
            }
        }
        RequestBody body = jsonBody == null ? null : RequestBody.create(jsonBody, JSON);
        Request request = new Request.Builder()
                .url(url.build())
                .header("Authorization", "Bearer " + apiKey)
                .method(method, body)
                .build();
        try (Response response = http.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            String text = responseBody == null ? "" : responseBody.string();
            if (!response.isSuccessful()) {
                throw httpError(response.code(), text);
            }
            return text;
        } catch (IOException e) {
            throw new MeiliIndexAccessException(null,
                    "direct-channel access failure: " + method + " /" + String.join("/", segments), e);
        }
    }

    /**
     * Turns a non-2xx direct-channel response into the public exception, extracting the
     * standard {@code code}/{@code message} error envelope when parseable.
     *
     * @param status HTTP status code
     * @param body   response text
     * @return the exception to throw
     */
    private static MeiliIndexAccessException httpError(int status, String body) {
        String code = null;
        String message = body;
        try {
            JsonNode error = MAPPER.readTree(body);
            code = error.path("code").asText(null);
            String serverMessage = error.path("message").asText(null);
            if (serverMessage != null) {
                message = serverMessage;
            }
        } catch (Exception ignored) {
            // body not the standard error envelope — keep raw text as message
        }
        return new MeiliIndexAccessException(code, "MeiliSearch HTTP " + status + ": " + message, null);
    }

    /**
     * Parses a helper-channel response body as JSON.
     *
     * @param json     response text
     * @param indexUid context for the failure message
     * @return the parsed tree
     */
    private static JsonNode readTree(String json, String indexUid) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new MeiliIndexAccessException(null,
                    "failed to parse direct-channel response: index=" + indexUid, e);
        }
    }

    /**
     * Copies a list into a JSON array node source.
     *
     * @param values string list
     * @return mutable array node with the values
     */
    private static ArrayNode stringArray(List<String> values) {
        ArrayNode array = MAPPER.createArrayNode();
        values.forEach(array::add);
        return array;
    }
}
