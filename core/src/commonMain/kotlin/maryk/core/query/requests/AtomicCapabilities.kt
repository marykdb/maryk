package maryk.core.query.requests

import maryk.core.models.TypedObjectDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.number
import maryk.core.properties.types.numeric.UInt32
import maryk.core.values.ObjectValues

/** Reported ceilings, not a promise that every request below them can commit. */
data class AtomicCapabilities(
    val maxMutations: UInt = MAX_ATOMIC_OPERATIONS,
    val maxGuards: UInt = MAX_ATOMIC_OPERATIONS,
    val maxRequestBytes: UInt = 1_048_576u,
    val maxResponseBytes: UInt = 1_048_576u,
    val maxWorkBytes: UInt? = null,
    val maxStagedNodes: UInt? = null,
    val maxScanRows: UInt? = null,
    val maxTransactionBytes: UInt? = null,
    val commitTimeoutMillis: UInt? = null,
    val maxRetries: UInt? = null,
    val maxStorageKeyBytes: UInt? = null,
    val maxStorageValueBytes: UInt? = null,
) {
    companion object : TypedObjectDataModel<AtomicCapabilities, AtomicCapabilities.Companion, IsPropertyContext, IsPropertyContext>() {
        val maxMutations by number(1u, AtomicCapabilities::maxMutations, UInt32, default = MAX_ATOMIC_OPERATIONS)
        val maxGuards by number(2u, AtomicCapabilities::maxGuards, UInt32, default = MAX_ATOMIC_OPERATIONS)
        val maxRequestBytes by number(3u, AtomicCapabilities::maxRequestBytes, UInt32, default = 1_048_576u)
        val maxResponseBytes by number(4u, AtomicCapabilities::maxResponseBytes, UInt32, default = 1_048_576u)
        val maxWorkBytes by number(5u, AtomicCapabilities::maxWorkBytes, UInt32, required = false)
        val maxStagedNodes by number(6u, AtomicCapabilities::maxStagedNodes, UInt32, required = false)
        val maxScanRows by number(7u, AtomicCapabilities::maxScanRows, UInt32, required = false)
        val maxTransactionBytes by number(8u, AtomicCapabilities::maxTransactionBytes, UInt32, required = false)
        val commitTimeoutMillis by number(9u, AtomicCapabilities::commitTimeoutMillis, UInt32, required = false)
        val maxRetries by number(10u, AtomicCapabilities::maxRetries, UInt32, required = false)
        val maxStorageKeyBytes by number(11u, AtomicCapabilities::maxStorageKeyBytes, UInt32, required = false)
        val maxStorageValueBytes by number(12u, AtomicCapabilities::maxStorageValueBytes, UInt32, required = false)
        override fun invoke(values: ObjectValues<AtomicCapabilities, AtomicCapabilities.Companion>) = AtomicCapabilities(
            maxMutations = values(maxMutations.index),
            maxGuards = values(maxGuards.index),
            maxRequestBytes = values(maxRequestBytes.index),
            maxResponseBytes = values(maxResponseBytes.index),
            maxWorkBytes = values(maxWorkBytes.index),
            maxStagedNodes = values(maxStagedNodes.index),
            maxScanRows = values(maxScanRows.index),
            maxTransactionBytes = values(maxTransactionBytes.index),
            commitTimeoutMillis = values(commitTimeoutMillis.index),
            maxRetries = values(maxRetries.index),
            maxStorageKeyBytes = values(maxStorageKeyBytes.index),
            maxStorageValueBytes = values(maxStorageValueBytes.index),
        )
    }
}
