package maryk.lib.time

/** Object to convert native ISO8601 */
expect object ISO8601 {
    /** Decode [iso8601] string into DateTime */
    fun toDate(iso8601: String): DateTime
}
