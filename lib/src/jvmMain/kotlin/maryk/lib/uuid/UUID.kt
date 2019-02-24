package maryk.lib.uuid

import java.util.UUID

actual fun generateUUID(): Pair<Long, Long> {
    val uuid = UUID.randomUUID()
    return Pair(uuid.mostSignificantBits, uuid.leastSignificantBits)
}
