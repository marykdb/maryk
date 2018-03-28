package maryk.core.properties.types

import java.util.*

actual fun generateUUID(): Pair<Long, Long> {
    val uuid = UUID.randomUUID()
    return Pair(uuid.mostSignificantBits, uuid.leastSignificantBits)
}