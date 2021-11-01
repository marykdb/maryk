package maryk.lib.time

/** Hours per day. */
internal const val HOURS_PER_DAY = 24
/** Minutes per hour. */
internal const val MINUTES_PER_HOUR = 60
/** Seconds per minute. */
internal const val SECONDS_PER_MINUTE = 60
/** Seconds per hour. */
internal const val SECONDS_PER_HOUR = SECONDS_PER_MINUTE * MINUTES_PER_HOUR
/** Seconds per hour. */
internal const val SECONDS_PER_DAY = SECONDS_PER_HOUR * HOURS_PER_DAY
/** Millis per second. */
internal const val MILLIS_PER_SECOND = 1000
/** Millis per minute. */
internal const val MILLIS_PER_MINUTE = MILLIS_PER_SECOND * SECONDS_PER_MINUTE
/** Millis per hour. */
internal const val MILLIS_PER_HOUR = MILLIS_PER_MINUTE * MINUTES_PER_HOUR
/** Millis per hour. */
internal const val MILLIS_PER_DAY = MILLIS_PER_HOUR * HOURS_PER_DAY
