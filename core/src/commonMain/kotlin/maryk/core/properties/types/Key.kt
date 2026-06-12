package maryk.core.properties.types

import maryk.core.models.IsValuesDataModel

class Key<out P : IsValuesDataModel>(bytes: ByteArray) : Bytes(bytes) {
    constructor(base64: String) : this(parseBase64Bytes(base64))

    companion object : BytesDescriptor<Key<*>>() {
        override fun invoke(bytes: ByteArray) = Key<IsValuesDataModel>(bytes)
    }
}
