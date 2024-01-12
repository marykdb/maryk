package maryk.datastore.hbase.helpers

import maryk.core.properties.definitions.index.IsIndexable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
fun IsIndexable.toFamilyName() =
    "i${Base64.UrlSafe.encode(source = referenceStorageByteArray.bytes).trimEnd('=')}".encodeToByteArray()
