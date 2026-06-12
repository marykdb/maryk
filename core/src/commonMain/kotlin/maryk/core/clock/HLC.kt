package maryk.core.clock

import maryk.core.extensions.bytes.initULong
import maryk.core.extensions.bytes.writeBytes
import maryk.core.properties.definitions.IsStorageBytesEncodable
import maryk.lib.exceptions.ParseException
import kotlin.jvm.JvmInline
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val LOGICAL_BYTE_SIZE = 20

private const val LOGICAL_MASK = 0xFFFFFuL
private val maxPhysicalTime = ULong.MAX_VALUE.shr(LOGICAL_BYTE_SIZE)

private fun createTimestamp(physical: ULong, logical: UInt): ULong {
    require(physical <= maxPhysicalTime) { "Physical time does not fit in 44 bits" }
    require(logical.toULong() <= LOGICAL_MASK) { "Logical time does not fit in 20 bits" }

    return physical.shl(LOGICAL_BYTE_SIZE) or logical.toULong()
}

private fun incrementTimestamp(timestamp: ULong): ULong {
    check(timestamp != ULong.MAX_VALUE) { "HLC timestamp overflow" }
    return timestamp + 1uL
}

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
@JvmInline
value class HLC(
    val timestamp: ULong
) {
    /** Create HLC by setting [physical] and [logical] time specifically */
    constructor(
        physical: ULong,
        logical: UInt
    ) : this(
        createTimestamp(physical, logical)
    )

    /** Create HLC for the current time */
    @OptIn(ExperimentalTime::class)
    constructor() : this(
        physical = Clock.System.now().toEpochMilliseconds().toULong(),
        logical = 0u
    )

    /** Increment the logical clock by 1 */
    fun increment() =
        HLC(incrementTimestamp(this.timestamp))

    /**
     * Calculate the current most recent timestamp. If it was a given timestamp higher logical clock by 1.
     * By default it will get a new time by `Instant.getCurrentEpochTimeInMillis()`, override [newTimeCreator]
     * for custom new time. Useful for tests or custom clocks.
     */
    fun calculateMaxTimeStamp(other: HLC? = null, newTimeCreator: () -> HLC = ::HLC) = HLC(
        maxOf(
            other?.let {
                incrementTimestamp(maxOf(this.timestamp, other.timestamp))
            } ?: incrementTimestamp(this.timestamp),
            newTimeCreator().timestamp
        )
    )

    /** Get the physical time of this HLC */
    fun toPhysicalUnixTime() =
        timestamp.shr(LOGICAL_BYTE_SIZE)

    /** Get the logical time of this HLC */
    fun toLogicalTime() =
        timestamp.and(LOGICAL_MASK).toUInt()

    /** Compare against [other] HLC and return difference */
    operator fun compareTo(other: HLC) =
        this.timestamp compareTo other.timestamp

    /** Compare against [other] timestamps and return difference */
    operator fun compareTo(other: ULong) =
        this.timestamp compareTo other

    companion object: IsStorageBytesEncodable<HLC> {
        override fun readStorageBytes(length: Int, reader: () -> Byte): HLC {
            if (length != ULong.SIZE_BYTES) {
                throw ParseException("Invalid storage byte length for HLC: $length != ${ULong.SIZE_BYTES}")
            }
            return HLC(initULong(reader))
        }

        override fun calculateStorageByteLength(value: HLC) =
            ULong.SIZE_BYTES

        override fun writeStorageBytes(value: HLC, writer: (byte: Byte) -> Unit) {
            value.timestamp.writeBytes(writer)
        }
    }
}
