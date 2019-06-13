package maryk.core.clock

import maryk.lib.time.Instant

private const val LOGICAL_BYTE_SIZE = 20

private const val LOGICAL_MASK = 0xFFFFFuL

/**
 * Hybrid Logical Clock value.
 *
 * Encodes both a physical clock in the upper 44 bits and a logic clock in the lower
 * 20 bits. This allows for timestamps until the year 2527 and a logical clock of over
 * 1 million ticks.
 *
 * See original proposal for more details:
 * https://cse.buffalo.edu/tech-reports/2014-04.pdf
 */
@Suppress("EXPERIMENTAL_FEATURE_WARNING")
inline class HLC constructor(
    val timestamp: ULong
) {
    /** Create HLC by setting [physical] and [logical] time specifically */
    constructor(
        physical: ULong,
        logical: UInt
    ) : this(
        physical.toULong().shl(LOGICAL_BYTE_SIZE) + logical
    )

    /** Create HLC for the current time */
    constructor() : this(
        physical = Instant.getCurrentEpochTimeInMillis().toULong(),
        logical = 0u
    )

    /** Increment the logical clock by 1 */
    fun increment() =
        HLC(this.timestamp + 1u)

    /**
     * Calculate the current most recent timestamp. If it was a given timestamp higher logical clock by 1.
     * By default it will get a new time by `Instant.getCurrentEpochTimeInMillis()`, override [newTimeCreator]
     * for custom new time. Useful for tests or custom clocks.
     */
    fun calculateMaxTimeStamp(other: HLC? = null, newTimeCreator: () -> HLC = ::HLC) = HLC(
        maxOf(
            other?.let {
                maxOf(this.timestamp, other.timestamp) + 1u
            } ?: this.timestamp + 1u,
            newTimeCreator().timestamp
        )
    )

    /** Get the physical time of this HLC */
    fun toPhysicalUnixTime(): ULong {
        return timestamp.shr(LOGICAL_BYTE_SIZE)
    }

    /** Get the logical time of this HLC */
    fun toLogicalTime() =
        timestamp.and(LOGICAL_MASK).toUInt()

    /** Compare against [other] HLC and return difference */
    operator fun compareTo(other: HLC) =
        this.timestamp.compareTo(other.timestamp)

    /** Compare against [other] timestamps and return difference */
    operator fun compareTo(other: ULong) =
        this.timestamp.compareTo(other)
}
