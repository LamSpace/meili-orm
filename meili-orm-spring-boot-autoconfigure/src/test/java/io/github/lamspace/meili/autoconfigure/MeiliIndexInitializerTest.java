package io.github.lamspace.meili.autoconfigure;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.lamspace.meili.core.exception.MeiliIndexAccessException;
import io.github.lamspace.meili.core.exception.MeiliMappingException;
import io.github.lamspace.meili.core.internal.MeiliRawGateway;
import io.github.lamspace.meili.core.mapping.MeiliDocument;
import io.github.lamspace.meili.core.mapping.MeiliField;
import io.github.lamspace.meili.core.mapping.MeiliId;
import io.github.lamspace.meili.core.mapping.MeiliMappingContext;
import io.github.lamspace.meili.core.settings.MeiliSettingsProjection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Behavior-contract unit tests for {@link MeiliIndexInitializer} against a mocked raw gateway
 * (taskUid is {@code int} end to end). Covers the three auto-init modes, the three drift
 * strategies, diff scoping to declared keys, and the startup fail-fast edges.
 */
class MeiliIndexInitializerTest {

    /** Standard probe entity: searchable title + filterable genre. */
    @MeiliDocument(indexName = "drift_books")
    static class Book {
        @MeiliId Long id;
        @MeiliField(searchable = true, searchableOrder = 1) String title;
        @MeiliField(filterable = true) String genre;
    }

    /** Entity with two searchable fields whose projection order must be preserved by diff. */
    @MeiliDocument(indexName = "order_books")
    static class OrderedBook {
        @MeiliId Long id;
        @MeiliField(searchable = true, searchableOrder = 1) String title;
        @MeiliField(searchable = true, searchableOrder = 2) String overview;
    }

    /** Entity declaring no roles at all. */
    @MeiliDocument(indexName = "bare_books")
    static class Bare {
        @MeiliId Long id;
    }

    /** Duplicate-index pair A. */
    @MeiliDocument(indexName = "dupe")
    static class DupA {
        @MeiliId Long id;
    }

    /** Duplicate-index pair B. */
    @MeiliDocument(indexName = "dupe")
    static class DupB {
        @MeiliId Long id;
    }

    /** Server settings where genre is NOT filterable and searchable is the wildcard default. */
    private static final String SERVER_UNSYNCED =
            "{\"searchableAttributes\":[\"*\"],\"filterableAttributes\":[],\"rankingRules\":[\"words\",\"typo\"]}";

    /** Server settings exactly matching Book's projection on its declared keys. */
    private static final String SERVER_IN_SYNC =
            "{\"searchableAttributes\":[\"title\"],\"filterableAttributes\":[\"genre\"],\"rankingRules\":[\"words\"]}";

    /** Fresh mock gateway per test. */
    private MeiliRawGateway gateway;

    /** Appender capturing initializer warnings. */
    private ListAppender<ILoggingEvent> appender;

    /** Detached logback logger. */
    private Logger logger;

    @BeforeEach
    void captureLogs() {
        gateway = mock(MeiliRawGateway.class);
        logger = (Logger) LoggerFactory.getLogger(MeiliIndexInitializer.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(appender);
    }

    /**
     * @param entities entity classes to initialize
     * @param mode     auto-init mode under test
     * @param drift    drift strategy under test
     * @return initializer wired to the mock gateway
     */
    private MeiliIndexInitializer init(List<Class<?>> entities, MeiliProperties.AutoInit mode,
                                       MeiliProperties.Drift drift) {
        return new MeiliIndexInitializer(gateway, new MeiliMappingContext(), new MeiliSettingsProjection(),
                entities, mode, drift, Duration.ofSeconds(5));
    }

    /** @return all captured log messages at WARN or above */
    private List<String> warnings() {
        return appender.list.stream()
                .filter(e -> e.getLevel().isGreaterOrEqual(Level.WARN))
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    @Test
    void emptyEntitiesDoNothing() {
        init(List.of(), MeiliProperties.AutoInit.SYNC_SETTINGS, MeiliProperties.Drift.FAIL).initialize();
        verifyNoInteractions(gateway);
    }

    @Test
    void noneModeInteractsWithNothing() {
        init(List.of(Book.class), MeiliProperties.AutoInit.NONE, MeiliProperties.Drift.FAIL).initialize();
        verifyNoInteractions(gateway);
    }

    @Test
    void createIfMissingCreatesPushesAndWaitsLastTask() {
        when(gateway.indexExists("drift_books")).thenReturn(false);
        when(gateway.createIndex("drift_books", "id")).thenReturn(1);
        when(gateway.updateSettings(eq("drift_books"), anyString())).thenReturn(2);
        init(List.of(Book.class), MeiliProperties.AutoInit.CREATE_IF_MISSING, MeiliProperties.Drift.WARN)
                .initialize();
        verify(gateway).createIndex("drift_books", "id");
        verify(gateway).updateSettings(eq("drift_books"),
                org.mockito.ArgumentMatchers.argThat(json ->
                        json.contains("searchableAttributes") && json.contains("filterableAttributes")));
        verify(gateway).awaitTask(2, Duration.ofSeconds(5));
    }

    @Test
    void createIfMissingOnExistingNeverWritesButReportsDrift() {
        when(gateway.indexExists("drift_books")).thenReturn(true);
        when(gateway.getSettings("drift_books")).thenReturn(SERVER_UNSYNCED);
        init(List.of(Book.class), MeiliProperties.AutoInit.CREATE_IF_MISSING, MeiliProperties.Drift.WARN)
                .initialize();
        verify(gateway, never()).updateSettings(any(), any());
        verify(gateway, never()).createIndex(any(), any());
        assertThat(warnings()).anySatisfy(msg -> assertThat(msg).contains("filterableAttributes"));
    }

    @Test
    void applyStrategyUnderCreateIfMissingIsSuppressedNotPushed() {
        when(gateway.indexExists("drift_books")).thenReturn(true);
        when(gateway.getSettings("drift_books")).thenReturn(SERVER_UNSYNCED);
        init(List.of(Book.class), MeiliProperties.AutoInit.CREATE_IF_MISSING, MeiliProperties.Drift.APPLY)
                .initialize();
        verify(gateway, never()).updateSettings(any(), any());
        assertThat(warnings()).anySatisfy(msg -> assertThat(msg).contains("apply"));
    }

    @Test
    void failStrategyUnderCreateIfMissingStillFails() {
        when(gateway.indexExists("drift_books")).thenReturn(true);
        when(gateway.getSettings("drift_books")).thenReturn(SERVER_UNSYNCED);
        assertThatThrownBy(() -> init(List.of(Book.class), MeiliProperties.AutoInit.CREATE_IF_MISSING,
                MeiliProperties.Drift.FAIL).initialize())
                .isInstanceOf(MeiliMappingException.class);
        verify(gateway, never()).updateSettings(any(), any());
    }

    @Test
    void syncWithoutDriftWritesNothing() {
        when(gateway.indexExists("drift_books")).thenReturn(true);
        when(gateway.getSettings("drift_books")).thenReturn(SERVER_IN_SYNC);
        init(List.of(Book.class), MeiliProperties.AutoInit.SYNC_SETTINGS, MeiliProperties.Drift.APPLY)
                .initialize();
        verify(gateway, never()).updateSettings(any(), any());
        assertThat(warnings()).isEmpty();
    }

    @Test
    void diffIgnoresKeysTheProjectionDoesNotDeclare() {
        // server rankingRules differs from any conceivable default; projection never declares it
        when(gateway.indexExists("drift_books")).thenReturn(true);
        when(gateway.getSettings("drift_books")).thenReturn(
                "{\"searchableAttributes\":[\"title\"],\"filterableAttributes\":[\"genre\"],"
                        + "\"rankingRules\":[\"typo\",\"words\",\"exactness\"]}");
        init(List.of(Book.class), MeiliProperties.AutoInit.SYNC_SETTINGS, MeiliProperties.Drift.FAIL)
                .initialize();
        verify(gateway, never()).updateSettings(any(), any());
    }

    @Test
    void searchableDiffIsOrderSensitive() {
        when(gateway.indexExists("order_books")).thenReturn(true);
        when(gateway.getSettings("order_books")).thenReturn(
                "{\"searchableAttributes\":[\"overview\",\"title\"],\"filterableAttributes\":[]}");
        init(List.of(OrderedBook.class), MeiliProperties.AutoInit.SYNC_SETTINGS, MeiliProperties.Drift.WARN)
                .initialize();
        assertThat(warnings()).anySatisfy(msg -> assertThat(msg).contains("searchableAttributes"));
    }

    @Test
    void filterableDiffUnderApplyPushesAndWarnsRebuild() {
        when(gateway.indexExists("drift_books")).thenReturn(true);
        when(gateway.getSettings("drift_books")).thenReturn(SERVER_UNSYNCED);
        when(gateway.updateSettings(eq("drift_books"), anyString())).thenReturn(3);
        init(List.of(Book.class), MeiliProperties.AutoInit.SYNC_SETTINGS, MeiliProperties.Drift.APPLY)
                .initialize();
        verify(gateway).updateSettings(eq("drift_books"),
                org.mockito.ArgumentMatchers.argThat(json -> json.contains("filterableAttributes")));
        verify(gateway).awaitTask(3, Duration.ofSeconds(5));
        assertThat(warnings()).anySatisfy(msg -> assertThat(msg).contains("重建"));
    }

    @Test
    void failStrategyThrowsWithoutWriting() {
        when(gateway.indexExists("drift_books")).thenReturn(true);
        when(gateway.getSettings("drift_books")).thenReturn(SERVER_UNSYNCED);
        assertThatThrownBy(() -> init(List.of(Book.class), MeiliProperties.AutoInit.SYNC_SETTINGS,
                MeiliProperties.Drift.FAIL).initialize())
                .isInstanceOf(MeiliMappingException.class)
                .hasMessageContaining("drift_books");
        verify(gateway, never()).updateSettings(any(), any());
    }

    @Test
    void warnStrategyLogsDriftWithoutWriting() {
        when(gateway.indexExists("drift_books")).thenReturn(true);
        when(gateway.getSettings("drift_books")).thenReturn(SERVER_UNSYNCED);
        init(List.of(Book.class), MeiliProperties.AutoInit.SYNC_SETTINGS, MeiliProperties.Drift.WARN)
                .initialize();
        verify(gateway, never()).updateSettings(any(), any());
        assertThat(warnings()).anySatisfy(msg -> assertThat(msg).contains("drift_books"));
    }

    @Test
    void duplicateIndexNameFailsBeforeAnyServerCall() {
        assertThatThrownBy(() -> init(List.of(DupA.class, DupB.class),
                MeiliProperties.AutoInit.CREATE_IF_MISSING, MeiliProperties.Drift.WARN).initialize())
                .isInstanceOf(MeiliMappingException.class)
                .hasMessageContaining("dupe");
        verifyNoInteractions(gateway);
    }

    @Test
    void bareEntityCreatesIndexWithoutSettingsPush() {
        when(gateway.indexExists("bare_books")).thenReturn(false);
        when(gateway.createIndex("bare_books", "id")).thenReturn(9);
        init(List.of(Bare.class), MeiliProperties.AutoInit.CREATE_IF_MISSING, MeiliProperties.Drift.WARN)
                .initialize();
        verify(gateway).createIndex("bare_books", "id");
        verify(gateway, never()).updateSettings(any(), any());
        verify(gateway).awaitTask(9, Duration.ofSeconds(5));
    }

    @Test
    void unreachableServerPropagatesAsIndexAccessFailure() {
        when(gateway.indexExists("drift_books")).thenThrow(
                new MeiliIndexAccessException("econnrefused", "connect refused", null));
        assertThatThrownBy(() -> init(List.of(Book.class), MeiliProperties.AutoInit.CREATE_IF_MISSING,
                MeiliProperties.Drift.WARN).initialize())
                .isInstanceOf(MeiliIndexAccessException.class);
    }
}
