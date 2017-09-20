package maryk.core.properties.types

abstract class IsTemporal<T> : Comparable<T> {
}

abstract class IsTemporalObject<T> {
    /**
     * Parse a date from a string.
     * @param value: Date represented as a string
     */
    abstract fun parse(value: String): T

    /** Get a new Instance with the current time at UTC timezone */
    abstract fun nowUTC(): T
}