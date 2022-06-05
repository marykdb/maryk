package maryk.lib.uuid

import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import platform.Foundation.NSUUID

actual fun generateUUID(): Pair<Long, Long> {
    memScoped {
        val uuidBytes = allocArray<UByteVar>(16)
        NSUUID().getUUIDBytes(uuidBytes.getPointer(this))

        var msb = 0L
        var lsb = 0L

        for (i in 0..7) {
            msb = msb shl 8 or (uuidBytes[i].toLong() and 0xff)
        }
        for (i in 8..15) {
            lsb = lsb shl 8 or (uuidBytes[i].toLong() and 0xff)
        }

        return Pair(msb, lsb)
    }
}
