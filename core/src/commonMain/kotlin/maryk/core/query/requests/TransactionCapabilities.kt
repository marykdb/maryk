package maryk.core.query.requests

import maryk.core.models.TypedObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.embedObject
import maryk.core.properties.definitions.number
import maryk.core.properties.types.numeric.UInt32
import maryk.core.values.ObjectValues

/** Fixed ceilings for serializable, optimistic transactions. */
data class TransactionCapabilities(
    val storage: AtomicCapabilities,
    val maxOperations: UInt = MAX_TRANSACTION_OPERATIONS,
    val maxLifetimeMillis: UInt = 30_000u,
    val maxSavepoints: UInt = 64u,
    val maxReadKeys: UInt = MAX_TRANSACTION_READ_KEYS,
    val maxReadRows: UInt = MAX_TRANSACTION_READ_ROWS,
) {
    init {
        require(maxOperations in 1u..MAX_TRANSACTION_OPERATIONS)
        require(maxLifetimeMillis in 1u..30_000u)
        require(maxSavepoints in 1u..64u)
        require(maxReadKeys in 1u..MAX_TRANSACTION_READ_KEYS)
        require(maxReadRows in 1u..MAX_TRANSACTION_READ_ROWS)
    }
    companion object : TypedObjectDataModel<TransactionCapabilities, TransactionCapabilities.Companion, IsPropertyContext, IsPropertyContext>() {
        val storage by embedObject(1u, TransactionCapabilities::storage, dataModel = { AtomicCapabilities })
        val maxOperations by number(2u, TransactionCapabilities::maxOperations, UInt32, default = MAX_TRANSACTION_OPERATIONS)
        val maxLifetimeMillis by number(3u, TransactionCapabilities::maxLifetimeMillis, UInt32, default = 30_000u)
        val maxSavepoints by number(4u, TransactionCapabilities::maxSavepoints, UInt32, default = 64u)
        val maxReadKeys by number(5u, TransactionCapabilities::maxReadKeys, UInt32, default = MAX_TRANSACTION_READ_KEYS)
        val maxReadRows by number(6u, TransactionCapabilities::maxReadRows, UInt32, default = MAX_TRANSACTION_READ_ROWS)
        override fun invoke(values: ObjectValues<TransactionCapabilities, TransactionCapabilities.Companion>) = TransactionCapabilities(
            values(storage.index), values(maxOperations.index), values(maxLifetimeMillis.index), values(maxSavepoints.index),
            values(maxReadKeys.index), values(maxReadRows.index),
        )
    }
}
