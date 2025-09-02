package maryk.datastore.foundationdb.processors.helpers

import maryk.lib.bytes.combineToByteArray

internal fun packKey(vararg segments: ByteArray): ByteArray =
    combineToByteArray(*segments)
