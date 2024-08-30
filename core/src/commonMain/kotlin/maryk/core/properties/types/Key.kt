package maryk.core.properties.types

import maryk.core.base64.Base64Maryk
import maryk.core.models.IsValuesDataModel
import maryk.lib.exceptions.ParseException
import kotlin.io.encoding.ExperimentalEncodingApi

class Key<out P : IsValuesDataModel>(bytes: ByteArray) : Bytes(bytes) {
    @OptIn(ExperimentalEncodingApi::class)
    constructor(base64: String) : this(
        try {
            Base64Maryk.decode(base64)
        } catch (e: Throwable) {
            throw ParseException(base64, e)
        }
    )

    companion object : BytesDescriptor<Key<*>>() {
        override fun invoke(bytes: ByteArray) = Key<IsValuesDataModel>(bytes)
    }
}
