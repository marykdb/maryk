package maryk.datastore.rocksdb.processors.helpers

import maryk.core.extensions.bytes.invert
import maryk.core.extensions.bytes.writeBytes

/** Create Version bytes out of a ULong */
internal fun ULong.createReversedVersionBytes() =
    ByteArray(ULong.SIZE_BYTES).let { versionBytes ->
        var index = 0
        this.writeBytes({ versionBytes[index++] = it })
        versionBytes.invert()
    }
