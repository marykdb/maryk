package maryk.core.extensions;

/** Creates a Pair to a safer Unit lambda */
infix fun <T, L> T.toUnitLambda(lambda: Unit.() -> L) =
    Pair(this, lambda)
