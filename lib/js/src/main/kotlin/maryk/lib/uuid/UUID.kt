package maryk.lib.uuid

import maryk.lib.extensions.randomBytes
import kotlin.experimental.and
import kotlin.experimental.or

actual fun generateUUID(): Pair<Long, Long> {
    val randomBytes = randomBytes(16)

    randomBytes[6] = randomBytes[6] and 0x0f  // clear version
    randomBytes[6] = randomBytes[6] or 0x40  // set to version 4
    randomBytes[8] = randomBytes[8] and 0x3f  // clear variant
    randomBytes[8] = randomBytes[8] or 0x80.toByte()  // set to IETF variant

    var msb = 0L
    var lsb = 0L

    for (i in 0..7) {
        msb = msb shl 8 or (randomBytes[i].toLong() and 0xff)
    }
    for (i in 8..15) {
        lsb = lsb shl 8 or (randomBytes[i].toLong() and 0xff)
    }

    return Pair(msb, lsb)
}
