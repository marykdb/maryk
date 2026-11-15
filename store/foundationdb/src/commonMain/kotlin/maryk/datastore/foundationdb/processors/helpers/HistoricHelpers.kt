package maryk.datastore.foundationdb.processors.helpers

import maryk.core.extensions.bytes.invert
import maryk.foundationdb.Transaction
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories

internal fun writeHistoricTable(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    keyBytes: ByteArray,
    qualifier: ByteArray,
    version: ByteArray,
    value: ByteArray,
) {
    if (tableDirs is HistoricTableDirectories) {
        tr.set(packHistoricTableKey(tableDirs.historicTablePrefix, keyBytes, qualifier, version), value)
    }
}

internal fun writeHistoricUnique(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    keyBytes: ByteArray,
    qualifier: ByteArray,
    version: ByteArray,
) {
    if (tableDirs is HistoricTableDirectories) {
        val encodedQualifier = encodeZeroFreeUsing01(qualifier)
        tr.set(packVersionedKey(tableDirs.historicUniquePrefix, encodedQualifier, version = version), keyBytes)
    }
}

internal fun writeHistoricIndex(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    indexRefBytes: ByteArray,
    keyAndValue: ByteArray,
    version: ByteArray,
    value: ByteArray,
) {
    if (tableDirs is HistoricTableDirectories) {
        // Encode the full qualifier (indexRef || valueAndKey) directly into the final zero-free buffer.
        val encodedQualifier = encodeZeroFreeUsing01(indexRefBytes, keyAndValue)
        tr.set(packVersionedKey(tableDirs.historicIndexPrefix, encodedQualifier, version = version), value)
    }
}

/** Encode the historic qualifier directly into the final key, preserving the released layout. */
internal fun packHistoricTableKey(
    directory: ByteArray,
    key: ByteArray,
    qualifier: ByteArray,
    version: ByteArray,
): ByteArray {
    val qualifierOffset = directory.size.checkedZeroFreeLengthPlus(key.size)
    val versionOffset = qualifierOffset.checkedZeroFreeLengthPlus(qualifier.zeroFreeEncodedLength())
        .checkedZeroFreeLengthPlus(1)
    return ByteArray(versionOffset.checkedZeroFreeLengthPlus(version.size)).also { packed ->
        directory.copyInto(packed)
        key.copyInto(packed, directory.size)
        encodeZeroFreeUsing01Into(qualifier, 0, qualifier.size, packed, qualifierOffset)
        version.copyInto(packed, versionOffset)
        packed.invert(versionOffset)
    }
}
