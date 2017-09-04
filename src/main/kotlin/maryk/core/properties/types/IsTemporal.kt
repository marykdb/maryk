package maryk.core.properties.types

abstract class IsTemporal<T> : Comparable<T> {
    /**
     * Get value as string
     * @param iso8601: true for more readable ISO8601, false if optimized for parsing
     */
    abstract fun toString(iso8601: Boolean):String
}

abstract class IsTemporalObject<T> {
    /**
     * Parse a date from a string. It knows 2 formats: first the more human readable ISO8601 string
     * and the other is an optimized string based on the days since 01-01-1970.
     * @param value: Date represented as a string
     */
    fun parse(value: String) = parse(value, iso8601 = true)

    /**
     * Parse a date from a string. It knows 2 formats: first the more human readable ISO8601 string
     * and the other is an optimized string
     * @param value: Date represented as a string
     * @param iso8601: true if to iso8601, false if more optimal format for parsing
     */
    abstract fun parse(value: String, iso8601: Boolean): T

    /** Get a new Instance with the current time at UTC timezone */
    abstract fun nowUTC(): T
}