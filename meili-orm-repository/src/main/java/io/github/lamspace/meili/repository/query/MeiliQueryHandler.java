package io.github.lamspace.meili.repository.query;

/**
 * A fully-resolved repository query method: all grammar parsing, property bridging,
 * role pre-checks and shape validation happened at bootstrap; invocation only binds
 * runtime arguments and executes.
 */
@FunctionalInterface
public interface MeiliQueryHandler {

    /**
     * Runs the resolved query against runtime arguments.
     *
     * @param args method arguments in declaration order (never {@code null}, may be empty)
     * @return the adapted return value ({@code List}/{@code Optional}/{@code Page})
     * @throws Throwable application failures propagate from the operations layer
     */
    Object invoke(Object[] args) throws Throwable;
}
