package maryk.core.properties.types

import maryk.lib.bytes.Base64
import maryk.lib.exceptions.ParseException

@Suppress("unused")
class Key<out DO: Any>(bytes: ByteArray) : Bytes(bytes) {
    constructor(base64: String): this(
        try {
            Base64.decode(base64)
        } catch (e: Throwable) {
            throw ParseException(base64)
        }
    )

    companion object: BytesDescriptor<Key<*>>() {
        override fun invoke(bytes: ByteArray) = Key<Any>(bytes)
    }
}
