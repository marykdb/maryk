package maryk.core.properties.types

import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsValuesDataModel

class Key<out P : IsValuesDataModel>(bytes: ByteArray) : Bytes(bytes) {
    constructor(base64: String) : this(parseBase64Bytes(base64))

    companion object : BytesDescriptor<Key<*>>() {
        override fun invoke(bytes: ByteArray) = Key<IsValuesDataModel>(bytes)
    }
}

internal fun IsRootDataModel.validateKey(key: Key<*>) {
    if (key.bytes.size != Meta.keyByteSize) {
        throw RequestException("Invalid key byte length ${key.bytes.size}; expected ${Meta.keyByteSize} for ${Meta.name}")
    }
}

internal fun IsRootDataModel.validateKeys(keys: Iterable<Key<*>>) {
    keys.forEach { validateKey(it) }
}
