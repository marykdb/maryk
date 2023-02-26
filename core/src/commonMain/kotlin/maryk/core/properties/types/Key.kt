package maryk.core.properties.types

import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.lib.exceptions.ParseException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Suppress("unused")
class Key<out P : IsValuesPropertyDefinitions>(bytes: ByteArray) : Bytes(bytes) {
    @OptIn(ExperimentalEncodingApi::class)
    constructor(base64: String) : this(
        try {
            Base64.Mime.decode(base64)
        } catch (e: Throwable) {
            throw ParseException(base64)
        }
    )

    companion object : BytesDescriptor<Key<*>>() {
        override fun invoke(bytes: ByteArray) = Key<IsValuesPropertyDefinitions>(bytes)
    }
}
