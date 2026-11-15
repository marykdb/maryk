package maryk.datastore.foundationdb.processors.helpers

import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.references.IsPropertyReferenceWithParent
import maryk.core.properties.references.ValueWithFixedBytesPropertyReference
import maryk.core.properties.references.ValueWithFlexBytesPropertyReference
import maryk.core.query.filters.IsFilter
import maryk.core.query.filters.IsFilterList
import maryk.core.query.filters.IsReferenceAnyPairsFilter
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.SOFT_DELETE_INDICATOR
import maryk.foundationdb.FdbFuture
import maryk.foundationdb.Transaction

/** Bounded current-value read-ahead; filtering still uses the ordinary value decoder. */
internal class PreparedFilterRead(
    transaction: Transaction,
    directories: IsTableDirectories,
    key: ByteArray,
    private val qualifiers: List<ByteArray>,
) {
    private val values = ArrayList<FdbFuture<ByteArray?>>(qualifiers.size)

    init {
        try {
            for (qualifier in qualifiers) values += transaction.get(packKey(directories.tablePrefix, key, qualifier))
        } catch (error: Throwable) {
            cancel()
            throw error
        }
    }

    fun get(qualifier: ByteArray): FdbFuture<ByteArray?>? {
        val index = qualifiers.indexOfFirst { it.contentEquals(qualifier) }
        return if (index < 0) null else values[index]
    }

    fun cancel() { values.forEach { it.cancel() } }
}

internal fun filterReadQualifiers(filter: IsFilter?, filterSoftDeleted: Boolean): List<ByteArray> {
    val qualifiers = ArrayList<ByteArray>(4)
    if (filterSoftDeleted) qualifiers += byteArrayOf(SOFT_DELETE_INDICATOR)
    fun collect(current: IsFilter?, depth: Int) {
        if (depth >= 16 || qualifiers.size >= 3) return
        when (current) {
            is IsFilterList -> current.filters.forEach { collect(it, depth + 1) }
            is IsReferenceAnyPairsFilter<*> -> for (pair in current.referenceValuePairs) {
                if (qualifiers.size >= 3) break
                val reference = pair.reference
                if ((reference::class == ValueWithFixedBytesPropertyReference::class ||
                        reference::class == ValueWithFlexBytesPropertyReference::class) &&
                    (reference as? IsPropertyReferenceWithParent<*, *, *, *>)?.parentReference == null && reference.comparablePropertyDefinition is IsSimpleValueDefinition<*, *>
                ) {
                    val qualifier = reference.toStorageByteArray()
                    if (qualifiers.none { it.contentEquals(qualifier) }) qualifiers += qualifier
                }
            }
        }
    }
    collect(filter, 0)
    return qualifiers
}
