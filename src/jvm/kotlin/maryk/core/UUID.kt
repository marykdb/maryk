package maryk.core

import java.util.*

fun generateUUID(): Pair<Long, Long> {
    val uuid = UUID.randomUUID()
    return Pair(uuid.mostSignificantBits, uuid.leastSignificantBits)
}