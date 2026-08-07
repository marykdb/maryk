package maryk.datastore.foundationdb.model

import maryk.core.exceptions.StorageException
import maryk.core.models.IsRootDataModel
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.foundationdb.Transaction
import maryk.foundationdb.TransactionContext
import kotlin.random.Random

/** Persisted writer fence for a single model schema transition. */
internal sealed interface FoundationDBSchemaState {
    data class Ready(val epoch: ByteArray) : FoundationDBSchemaState
    data class Rebuilding(
        val previousEpoch: ByteArray?,
        val nextEpoch: ByteArray,
        val transitionId: String,
        val owner: String,
        val target: String,
    ) : FoundationDBSchemaState
}

internal data class FoundationDBSchemaFence(
    val epoch: ByteArray,
    val transitionId: String,
    val owner: String,
    val target: String,
)

/** Stable target identity, including the serialized definition rather than just version. */
internal fun modelSchemaTarget(dataModel: IsRootDataModel): String {
    val definition = encodeModelDefinition(dataModel)
    return "${dataModel.Meta.name}@${dataModel.Meta.version}#${definition.model.schemaFingerprint()}"
}

internal fun readModelSchemaState(
    transaction: Transaction,
    modelPrefix: ByteArray,
): FoundationDBSchemaState? = transaction.get(packKey(modelPrefix, modelSchemaStateKey))
    .awaitResult()
    ?.let(::decodeSchemaState)

/**
 * Start or take over a rebuild. A matching persisted Rebuilding state is safe
 * to resume after a process crash because the distributed migration lease
 * establishes the single live owner before this call.
 */
internal fun beginModelSchemaRebuild(
    tc: TransactionContext,
    modelPrefix: ByteArray,
    dataModel: IsRootDataModel,
): FoundationDBSchemaFence {
    val target = modelSchemaTarget(dataModel)
    val owner = Random.nextLong().toString(16)
    val transitionId = Random.nextLong().toString(16)
    return tc.run { transaction ->
        val current = readModelSchemaState(transaction, modelPrefix)
        val fence = when (current) {
            is FoundationDBSchemaState.Rebuilding -> {
                if (current.target != target) {
                    throw StorageException(
                        "Model ${dataModel.Meta.name} is rebuilding for ${current.target}; cannot take over $target"
                    )
                }
                FoundationDBSchemaFence(current.nextEpoch, transitionId, owner, target)
            }
            is FoundationDBSchemaState.Ready ->
                FoundationDBSchemaFence(Random.nextBytes(16), transitionId, owner, target)
            null -> FoundationDBSchemaFence(Random.nextBytes(16), transitionId, owner, target)
        }
        transaction.set(
            packKey(modelPrefix, modelSchemaStateKey),
            FoundationDBSchemaState.Rebuilding(
                previousEpoch = (current as? FoundationDBSchemaState.Ready)?.epoch
                    ?: (current as? FoundationDBSchemaState.Rebuilding)?.previousEpoch,
                nextEpoch = fence.epoch,
                transitionId = fence.transitionId,
                owner = fence.owner,
                target = fence.target,
            ).encode(),
        )
        fence
    }
}

/** Read inside each cleanup/rebuild write transaction. */
internal fun Transaction.requireModelSchemaRebuildOwner(
    modelPrefix: ByteArray,
    fence: FoundationDBSchemaFence,
) {
    val actual = readModelSchemaState(this, modelPrefix)
    if (actual !is FoundationDBSchemaState.Rebuilding ||
        !actual.nextEpoch.contentEquals(fence.epoch) ||
        actual.transitionId != fence.transitionId ||
        actual.owner != fence.owner ||
        actual.target != fence.target
    ) {
        throw StorageException("Index rebuild fence was lost for ${fence.target}")
    }
}

/** Read inside every normal writer transaction. */
internal fun Transaction.requireModelSchemaReady(
    modelPrefix: ByteArray,
    expectedEpoch: ByteArray?,
) {
    when (val actual = readModelSchemaState(this, modelPrefix)) {
        null -> if (expectedEpoch != null) {
            throw StorageException("Model schema epoch was removed while this store was open")
        }
        is FoundationDBSchemaState.Ready -> if (expectedEpoch == null || !actual.epoch.contentEquals(expectedEpoch)) {
            throw StorageException("Model schema changed while this store was open; reopen the datastore")
        }
        is FoundationDBSchemaState.Rebuilding -> throw StorageException(
            "Model schema is rebuilding for ${actual.target}; retry after migration completes"
        )
    }
}

internal fun Transaction.publishModelSchemaReady(
    modelPrefix: ByteArray,
    fence: FoundationDBSchemaFence,
) {
    requireModelSchemaRebuildOwner(modelPrefix, fence)
    set(packKey(modelPrefix, modelSchemaStateKey), FoundationDBSchemaState.Ready(fence.epoch).encode())
}

private fun FoundationDBSchemaState.encode(): ByteArray = buildString {
    append("v=1\n")
    when (this@encode) {
        is FoundationDBSchemaState.Ready -> {
            append("state=ready\n")
            append("epoch=").append(epoch.toHex()).append('\n')
        }
        is FoundationDBSchemaState.Rebuilding -> {
            append("state=rebuilding\n")
            append("previous=").append(previousEpoch?.toHex().orEmpty()).append('\n')
            append("next=").append(nextEpoch.toHex()).append('\n')
            append("transition=").append(transitionId).append('\n')
            append("owner=").append(owner).append('\n')
            append("target=").append(target).append('\n')
        }
    }
}.encodeToByteArray()

private fun decodeSchemaState(bytes: ByteArray): FoundationDBSchemaState {
    val values = bytes.decodeToString()
        .lineSequence()
        .mapNotNull { line -> line.indexOf('=').takeIf { it > 0 }?.let { index -> line.substring(0, index) to line.substring(index + 1) } }
        .toMap()
    if (values["v"] != "1") throw StorageException("Invalid model schema fence")
    return when (values["state"]) {
        "ready" -> FoundationDBSchemaState.Ready(values["epoch"]?.fromHex() ?: throw StorageException("Invalid ready schema fence"))
        "rebuilding" -> FoundationDBSchemaState.Rebuilding(
            previousEpoch = values["previous"]?.takeIf { it.isNotEmpty() }?.fromHex(),
            nextEpoch = values["next"]?.fromHex() ?: throw StorageException("Invalid rebuilding schema fence"),
            transitionId = values["transition"] ?: throw StorageException("Invalid rebuilding schema fence"),
            owner = values["owner"] ?: throw StorageException("Invalid rebuilding schema fence"),
            target = values["target"] ?: throw StorageException("Invalid rebuilding schema fence"),
        )
        else -> throw StorageException("Invalid model schema fence")
    }
}

private fun ByteArray.toHex() = joinToString(separator = "") { byte ->
    byte.toUByte().toString(16).padStart(2, '0')
}

private fun String.fromHex(): ByteArray {
    if (length % 2 != 0) throw StorageException("Invalid model schema fence epoch")
    return ByteArray(length / 2) { index ->
        substring(index * 2, index * 2 + 2).toIntOrNull(16)?.toByte()
            ?: throw StorageException("Invalid model schema fence epoch")
    }
}

private fun ByteArray.schemaFingerprint(): String {
    var hash = 0xcbf29ce484222325uL
    forEach { byte ->
        hash = (hash xor byte.toUByte().toULong()) * 0x100000001b3uL
    }
    return hash.toString(16)
}
