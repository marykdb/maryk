package maryk.core.models

import maryk.core.extensions.bytes.initByteArray
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.types.Key
import maryk.lib.bytes.Base64
import maryk.lib.exceptions.ParseException

interface IsTypedRootDataModel<DM : IsRootDataModel<P>, P : IsPropertyDefinitions> : IsRootDataModel<P> {
    override fun key(base64: String): Key<DM> = this.key(Base64.decode(base64))

    override fun key(reader: () -> Byte) = Key<DM>(
        initByteArray(this.keyByteSize, reader)
    )

    override fun key(bytes: ByteArray): Key<DM> {
        if (bytes.size != this.keyByteSize) {
            throw ParseException("Invalid byte length for key. Expected $keyByteSize instead of ${bytes.size}")
        }
        return Key(bytes)
    }
}
