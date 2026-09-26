package io.github.lamspace.meili.testcontainers;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Typed Testcontainers service container for a MeiliSearch server.
 *
 * <p>Responsibility: encapsulate the connection facts of a disposable MeiliSearch instance
 * (image, port, master key, readiness) so tests declare one container instead of hand-wiring
 * properties. It is a plain container definition — usable on its own via
 * {@link #getUrl()}/{@link #getApiKey()}, and additionally consumed by
 * {@link MeiliContainerConnectionDetailsFactory} when a field is annotated with
 * {@code @ServiceConnection}.
 *
 * <p>Defaults, all overridable through the standard Testcontainers API or the methods below:
 * image {@code getmeili/meilisearch:v1.49.0} (pinned through the default constructors; any
 * explicit image name of the {@code getmeili/meilisearch} repository is accepted as-is, and a
 * relocated mirror declares that via
 * {@link DockerImageName#asCompatibleSubstituteFor(String)}), exposed port
 * {@value #MEILISEARCH_PORT}, master key {@value #DEFAULT_MASTER_KEY}, and a readiness wait on
 * HTTP 200 from {@code /health}.
 *
 * <p>State machine: {@code created -> running} (start), then terminal after {@code stop()};
 * {@link #getUrl()} requires the running state, as any Testcontainers mapped-port read does.
 *
 * <p>Thread model: construction and configuration are expected on a single thread before
 * {@code start()}; reads after startup are safe. Under {@code @ServiceConnection} the start is
 * performed by the Spring Boot lifecycle on first connection-fact access, not by this class.
 *
 * <p>The master key is a throwaway test credential valid only inside this disposable
 * container; never point production configuration at values from this class.
 */
public class MeiliSearchContainer extends GenericContainer<MeiliSearchContainer> {

    /** Default image repository; the pinned tag lives in {@link #DEFAULT_TAG}. */
    public static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("getmeili/meilisearch");

    /** Pinned default image tag, applied by the default constructors. */
    public static final String DEFAULT_TAG = "v1.49.0";

    /** Container-side MeiliSearch HTTP port, mapped to a random host port. */
    public static final int MEILISEARCH_PORT = 7700;

    /** Default master key injected as {@code MEILI_MASTER_KEY}; test-only credential. */
    public static final String DEFAULT_MASTER_KEY = "masterKey-test-123456";

    /**
     * The effective master key, kept in sync with the {@code MEILI_MASTER_KEY} environment
     * value by {@link #withMasterKey(String)}.
     */
    private String masterKey;

    /**
     * The image name this container was configured with, recorded for reads that must not
     * trigger image resolution (see {@link #getConfiguredImage()}).
     */
    private final DockerImageName configuredImage;

    /**
     * Creates a container with the pinned default image
     * {@code getmeili/meilisearch:}{@value #DEFAULT_TAG}.
     */
    public MeiliSearchContainer() {
        this(DEFAULT_IMAGE_NAME.withTag(DEFAULT_TAG));
    }

    /**
     * Creates a container from an image-name string such as
     * {@code "getmeili/meilisearch:v1.49.0"}.
     *
     * @param dockerImageName full image name to run
     */
    public MeiliSearchContainer(String dockerImageName) {
        this(DockerImageName.parse(dockerImageName));
    }

    /**
     * Creates a container from an explicit image name.
     *
     * @param dockerImageName image to run; must be {@code getmeili/meilisearch} (any tag) or a
     *                        mirror that declared compatibility via
     *                        {@link DockerImageName#asCompatibleSubstituteFor(String)}
     */
    public MeiliSearchContainer(DockerImageName dockerImageName) {
        super(dockerImageName);
        dockerImageName.assertCompatibleWith(DEFAULT_IMAGE_NAME);
        this.configuredImage = dockerImageName;
        this.masterKey = DEFAULT_MASTER_KEY;
        withExposedPorts(MEILISEARCH_PORT);
        withEnv("MEILI_MASTER_KEY", this.masterKey);
        withEnv("MEILI_ENV", "development");
        waitingFor(Wait.forHttp("/health").forPort(MEILISEARCH_PORT).forStatusCode(200));
    }

    /**
     * Overrides the master key injected into the container.
     *
     * <p>Configuration-time only: call before the container starts. Subsequent
     * {@link #getApiKey()} reads return this value, and the server authenticates with it.
     *
     * @param masterKey the master key to set; must not be {@code null}
     * @return this container, for chaining
     */
    public MeiliSearchContainer withMasterKey(String masterKey) {
        this.masterKey = masterKey;
        return withEnv("MEILI_MASTER_KEY", masterKey);
    }

    /**
     * The external base URL of the running container, including scheme and mapped host port
     * (e.g. {@code http://localhost:49152}).
     *
     * @return the service URL for SDK {@code Config} or property injection
     * @throws IllegalStateException if the container is not running
     */
    public String getUrl() {
        return "http://" + getHost() + ":" + getMappedPort(MEILISEARCH_PORT);
    }

    /**
     * The configured master key, readable before startup (for assertions and bridging).
     *
     * @return the API key in effect for this container; never {@code null}
     */
    public String getApiKey() {
        return this.masterKey;
    }

    /**
     * The image name this container was constructed with, exactly as declared. Unlike Testcontainers'
     * {@code getDockerImageName()} this read performs no image resolution or pull, so it is safe
     * before (and without) startup.
     *
     * @return the configured image name; never {@code null}
     */
    public DockerImageName getConfiguredImage() {
        return this.configuredImage;
    }
}
