package maryk.core.properties.types

import maryk.core.properties.exceptions.ParseException

abstract class IsTemporal<T> : Comparable<T> {}

abstract class IsTemporalObject<T> {
    /**
     * Parse a date from a string [value].
     * @throws ParseException on parse issues
     */
    internal abstract fun parse(value: String): T

    /** Get a new Instance with the current time at UTC timezone */
    internal abstract fun nowUTC(): T
}