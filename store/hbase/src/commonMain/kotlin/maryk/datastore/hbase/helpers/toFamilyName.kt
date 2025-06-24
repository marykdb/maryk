package maryk.datastore.hbase.helpers

import maryk.core.base64.Base64Maryk
import maryk.core.properties.definitions.index.IsIndexable

fun IsIndexable.toFamilyName() =
    "i${Base64Maryk.encode(source = referenceStorageByteArray.bytes).trimEnd('=')}".encodeToByteArray()
