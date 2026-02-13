package maryk.datastore.foundationdb.clusterlog

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.models.serializers.IsDataModelSerializer
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.types.Key
import maryk.core.properties.types.Bytes
import maryk.core.protobuf.WriteCache
import maryk.core.query.DefinitionsContext
import maryk.core.query.RequestContext
import maryk.core.query.changes.VersionedChanges
import maryk.datastore.foundationdb.processors.helpers.awaitResult
import maryk.datastore.foundationdb.processors.helpers.nextBlocking
import maryk.datastore.shared.updates.Update
import maryk.foundationdb.MutationType
import maryk.foundationdb.Range
import maryk.foundationdb.Transaction
import maryk.foundationdb.tuple.Tuple
import maryk.foundationdb.tuple.Versionstamp
import maryk.lib.extensions.compare.compareTo
import maryk.lib.bytes.combineToByteArray
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

internal class ClusterUpdateLog(
    private val logPrefix: ByteArray,
    private val consumerPrefix: ByteArray,
    private val headPrefix: ByteArray?,
    private val headGroupCount: Int,
    private val hlcPrefix: ByteArray?,
    private val hlcMaxPrefix: ByteArray?,
    private val shardCount: Int,
    private val originId: String,
    private val dataModelsById: Map<UInt, IsRootDataModel>,
    private val consumerId: String,
    private val retention: Duration,
) {
    private val originBytes = originId.encodeToByteArray()

    private val definitionsContext = DefinitionsContext(
        dataModels = dataModelsById.values
            .associateBy { it.Meta.name }
            .mapValues { DataModelReference(it.value) }
            .toMutableMap()
    )

    fun append(tr: Transaction, modelId: UInt, update: ClusterLogUpdate) {
        val shard = shardFor(modelId, update.keyBytes.bytes)
        val hlcBytes = update.version.toBigEndianBytes()
        val tuple = Tuple.from(
            shard,
            modelId.toLong(),
            hlcBytes,
            update.keyBytes.bytes,
            originBytes,
            Versionstamp.incomplete()
        )
        val packedWithVersionstamp = tuple.packWithVersionstamp()
        val key = packWithAdjustedVersionstampOffset(logPrefix, packedWithVersionstamp)
        val value = encodeValue(modelId, update, dataModelsById.getValue(modelId))
        tr.mutate(MutationType.SET_VERSIONSTAMPED_KEY, key, value)

        hlcPrefix?.also {
            tr.set(hlcNodeKey(), hlcBytes)
        }
        hlcMaxPrefix?.also {
            tr.mutate(MutationType.BYTE_MAX, hlcMaxShardKey(shard), hlcBytes)
        }

        // Wake up tailers without polling (optional).
        if (headPrefix != null && headGroupCount > 0) {
            val group = shard % headGroupCount
            tr.mutate(MutationType.SET_VERSIONSTAMPED_VALUE, headKey(group), versionstampedValuePlaceholder())
        }
    }

    fun tailOnce(
        tr: Transaction,
        shard: Int,
        modelId: UInt,
        cursorKey: ByteArray?,
        limit: Int,
    ): TailResult {
        val shardModelPrefix = shardModelPrefix(shard, modelId)
        val shardModelRange = Range.startsWith(shardModelPrefix)

        val begin = cursorKey?.let { combineToByteArray(it, byteArrayOf(0)) } ?: shardModelRange.begin
        val range = Range(begin, shardModelRange.end)

        val it = tr.getRange(range, limit, false).iterator()

        var lastKey: ByteArray? = null
        val updates = mutableListOf<DecodedUpdate>()
        while (it.hasNext()) {
            val kv = it.nextBlocking()
            lastKey = kv.key
            decodeValue(kv.value)?.also { decoded ->
                if (decoded.header.origin != originId) {
                    updates += decoded
                }
            }
        }

        return TailResult(
            lastKey = lastKey,
            decoded = updates
        )
    }

    fun readCursorKey(tr: Transaction, shard: Int, modelId: UInt): ByteArray? =
        tr.get(cursorKey(shard, modelId)).awaitResult()

    fun writeCursorKey(tr: Transaction, shard: Int, modelId: UInt, lastKey: ByteArray) {
        tr.set(cursorKey(shard, modelId), lastKey)
    }

    fun clearBefore(tr: Transaction, shard: Int, modelId: UInt, cutoff: ULong) {
        val shardModelPrefix = shardModelPrefix(shard, modelId)
        val end = minimalKeyAtOrAfter(shard, modelId, cutoff)
        tr.clear(Range(shardModelPrefix, end))
    }

    fun cursorIsBeforeCutoff(shard: Int, modelId: UInt, cursorKey: ByteArray, cutoff: ULong): Boolean {
        val cutoffKey = minimalKeyAtOrAfter(shard, modelId, cutoff)
        return cursorKey < cutoffKey
    }

    fun shardModelPrefix(shard: Int, modelId: UInt): ByteArray =
        combineToByteArray(logPrefix, Tuple.from(shard, modelId.toLong()).pack())

    fun cursorKey(shard: Int, modelId: UInt): ByteArray =
        combineToByteArray(consumerPrefix, Tuple.from(modelId.toLong(), shard).pack())

    fun minimalKeyAtOrAfter(shard: Int, modelId: UInt, hlc: ULong): ByteArray =
        combineToByteArray(logPrefix, Tuple.from(shard, modelId.toLong(), hlc.toBigEndianBytes()).pack())

    fun headKey(group: Int): ByteArray {
        require(headPrefix != null) { "headPrefix missing" }
        return combineToByteArray(headPrefix, Tuple.from(group).pack())
    }

    fun hlcNodeKey(): ByteArray {
        require(hlcPrefix != null) { "hlcPrefix missing" }
        return combineToByteArray(hlcPrefix, Tuple.from(consumerId).pack())
    }

    fun hlcRange(): Range {
        require(hlcPrefix != null) { "hlcPrefix missing" }
        return Range.startsWith(hlcPrefix)
    }

    fun hlcMaxShardKey(shard: Int): ByteArray {
        require(hlcMaxPrefix != null) { "hlcMaxPrefix missing" }
        return combineToByteArray(hlcMaxPrefix, Tuple.from(shard).pack())
    }

    fun hlcMaxRange(): Range {
        require(hlcMaxPrefix != null) { "hlcMaxPrefix missing" }
        return Range.startsWith(hlcMaxPrefix)
    }

    data class TailResult(
        val lastKey: ByteArray?,
        val decoded: List<DecodedUpdate>,
    )

    data class DecodedUpdate(
        val header: ClusterLogHeader,
        val update: ClusterLogUpdate,
    ) {
        fun toInternalUpdate(dataModel: IsRootDataModel): Update<*> {
            return when (update) {
                is ClusterLogAddition -> {
                    @Suppress("UNCHECKED_CAST")
                    val values = update.values as maryk.core.values.Values<IsRootDataModel>
                    Update.Addition(
                        dataModel = dataModel,
                        key = Key(update.keyBytes.bytes),
                        version = update.version,
                        values = values
                    )
                }
                is ClusterLogChange -> Update.Change(
                    dataModel = dataModel,
                    key = Key(update.keyBytes.bytes),
                    version = update.version,
                    changes = update.changes
                )
                is ClusterLogDeletion -> Update.Deletion(
                    dataModel = dataModel,
                    key = Key(update.keyBytes.bytes),
                    version = update.version,
                    isHardDelete = update.hardDelete
                )
            }
        }
    }

    internal fun encodeValue(modelId: UInt, update: ClusterLogUpdate, dataModel: IsRootDataModel): ByteArray {
        val originLen = originBytes.size
        val keyBytes = update.keyBytes.bytes
        val keyLen = keyBytes.size

        val payloadBytes = when (update) {
            is ClusterLogAddition -> encodeValuesBytes(dataModel, update.values)
            is ClusterLogChange -> encodeChangesBytes(dataModel, update.changes)
            is ClusterLogDeletion -> byteArrayOf(if (update.hardDelete) 1 else 0)
        }

        val total =
            2 + originLen + // origin
                4 + // modelId
                1 + // type
                8 + // version
                2 + keyLen + // key
                4 + payloadBytes.size // payload length + bytes

        val out = ByteArray(total)
        var o = 0

        out[o++] = ((originLen ushr 8) and 0xFF).toByte()
        out[o++] = (originLen and 0xFF).toByte()
        originBytes.copyInto(out, o)
        o += originLen

        val mid = modelId.toInt()
        out[o++] = ((mid ushr 24) and 0xFF).toByte()
        out[o++] = ((mid ushr 16) and 0xFF).toByte()
        out[o++] = ((mid ushr 8) and 0xFF).toByte()
        out[o++] = (mid and 0xFF).toByte()

        out[o++] = update.type

        val v = update.version
        out[o++] = ((v shr 56) and 0xFFu).toByte()
        out[o++] = ((v shr 48) and 0xFFu).toByte()
        out[o++] = ((v shr 40) and 0xFFu).toByte()
        out[o++] = ((v shr 32) and 0xFFu).toByte()
        out[o++] = ((v shr 24) and 0xFFu).toByte()
        out[o++] = ((v shr 16) and 0xFFu).toByte()
        out[o++] = ((v shr 8) and 0xFFu).toByte()
        out[o++] = (v and 0xFFu).toByte()

        out[o++] = ((keyLen ushr 8) and 0xFF).toByte()
        out[o++] = (keyLen and 0xFF).toByte()
        keyBytes.copyInto(out, o)
        o += keyLen

        val pl = payloadBytes.size
        out[o++] = ((pl ushr 24) and 0xFF).toByte()
        out[o++] = ((pl ushr 16) and 0xFF).toByte()
        out[o++] = ((pl ushr 8) and 0xFF).toByte()
        out[o++] = (pl and 0xFF).toByte()
        payloadBytes.copyInto(out, o)

        return out
    }

    internal fun decodeValue(value: ByteArray): DecodedUpdate? {
        if (value.size < 2 + 4 + 1 + 8 + 2 + 4) return null
        var o = 0

        val originLen = ((value[o++].toInt() and 0xFF) shl 8) or (value[o++].toInt() and 0xFF)
        if (originLen < 0 || value.size < o + originLen + 4 + 1 + 8 + 2 + 4) return null
        val origin = value.copyOfRange(o, o + originLen).decodeToString()
        o += originLen

        val modelId = (
            ((value[o++].toInt() and 0xFF) shl 24) or
                ((value[o++].toInt() and 0xFF) shl 16) or
                ((value[o++].toInt() and 0xFF) shl 8) or
                (value[o++].toInt() and 0xFF)
            ).toUInt()

        val dataModel = dataModelsById[modelId] ?: return null
        val ctx = RequestContext(definitionsContext, dataModel = dataModel)

        val type = value[o++]
        if (value.size < o + 8 + 2 + 4) return null

        val version =
            ((value[o++].toULong() and 0xFFuL) shl 56) or
                ((value[o++].toULong() and 0xFFuL) shl 48) or
                ((value[o++].toULong() and 0xFFuL) shl 40) or
                ((value[o++].toULong() and 0xFFuL) shl 32) or
                ((value[o++].toULong() and 0xFFuL) shl 24) or
                ((value[o++].toULong() and 0xFFuL) shl 16) or
                ((value[o++].toULong() and 0xFFuL) shl 8) or
                (value[o++].toULong() and 0xFFuL)

        val keyLen = ((value[o++].toInt() and 0xFF) shl 8) or (value[o++].toInt() and 0xFF)
        if (keyLen < 0 || value.size < o + keyLen + 4) return null
        val keyBytes = Bytes(value.copyOfRange(o, o + keyLen))
        o += keyLen

        val payloadLen = (
            ((value[o++].toInt() and 0xFF) shl 24) or
                ((value[o++].toInt() and 0xFF) shl 16) or
                ((value[o++].toInt() and 0xFF) shl 8) or
                (value[o++].toInt() and 0xFF)
            )
        if (payloadLen < 0 || value.size < o + payloadLen) return null
        val payload = value.copyOfRange(o, o + payloadLen)

        val decodedUpdate = when (type) {
            ClusterLogUpdate.TYPE_ADDITION -> {
                val valuesDecoded = decodeValuesBytes(ctx, dataModel, payload)
                    ?: return null
                ClusterLogAddition(keyBytes = keyBytes, version = version, values = valuesDecoded)
            }
            ClusterLogUpdate.TYPE_CHANGE -> {
                val changes = decodeChangesBytes(ctx, payload) ?: return null
                ClusterLogChange(keyBytes = keyBytes, version = version, changes = changes)
            }
            ClusterLogUpdate.TYPE_DELETION -> {
                if (payload.size != 1) return null
                ClusterLogDeletion(keyBytes = keyBytes, version = version, hardDelete = payload[0].toInt() != 0)
            }
            else -> return null
        }

        return DecodedUpdate(
            header = ClusterLogHeader(origin = origin, modelId = modelId),
            update = decodedUpdate
        )
    }

    private fun shardFor(modelId: UInt, keyBytes: ByteArray): Int {
        var h = 0x811C9DC5u
        fun mix(b: Int) {
            h = (h xor (b.toUInt() and 0xFFu)) * 0x01000193u
        }
        val mid = modelId.toInt()
        mix(mid ushr 24)
        mix(mid ushr 16)
        mix(mid ushr 8)
        mix(mid)
        for (b in keyBytes) mix(b.toInt())
        return (h % shardCount.toUInt()).toInt()
    }

    private fun encodeValuesBytes(dataModel: IsRootDataModel, values: maryk.core.values.Values<*>): ByteArray {
        val ctx = RequestContext(definitionsContext, dataModel = dataModel)
        @Suppress("UNCHECKED_CAST")
        val typedValues = values as maryk.core.values.Values<IsRootDataModel>
        @Suppress("UNCHECKED_CAST")
        val serializer =
            dataModel.Serializer as IsDataModelSerializer<maryk.core.values.Values<IsRootDataModel>, IsRootDataModel, RequestContext>
        val cache = WriteCache()
        val len = serializer.calculateProtoBufLength(typedValues, cache, ctx)
        val out = ByteArray(len)
        var i = 0
        serializer.writeProtoBuf(typedValues, cache, { b -> out[i++] = b }, ctx)
        return out
    }

    private fun decodeValuesBytes(ctx: RequestContext, dataModel: IsRootDataModel, bytes: ByteArray): maryk.core.values.Values<*>? {
        var i = 0
        @Suppress("UNCHECKED_CAST")
        val serializer =
            dataModel.Serializer as IsDataModelSerializer<maryk.core.values.Values<IsRootDataModel>, IsRootDataModel, RequestContext>
        return try {
            serializer.readProtoBuf(bytes.size, { bytes[i++] }, ctx).also {
                if (i != bytes.size) return null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun encodeChangesBytes(dataModel: IsRootDataModel, changes: List<maryk.core.query.changes.IsChange>): ByteArray {
        val ctx = RequestContext(definitionsContext, dataModel = dataModel)
        val cache = WriteCache()
        val vc = VersionedChanges(version = 0uL, changes = changes)
        val serializer = VersionedChanges.Serializer
        val len = serializer.calculateObjectProtoBufLength(vc, cache, ctx)
        val out = ByteArray(len)
        var i = 0
        serializer.writeObjectProtoBuf(vc, cache, { b -> out[i++] = b }, ctx)
        return out
    }

    private fun decodeChangesBytes(ctx: RequestContext, bytes: ByteArray): List<maryk.core.query.changes.IsChange>? {
        var i = 0
        return try {
            val serializer = VersionedChanges.Serializer
            val values = serializer.readProtoBuf(bytes.size, { bytes[i++] }, ctx).toDataObject()
            if (i != bytes.size) return null
            values.changes
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        fun retentionDefault(): Duration = 60.minutes
        fun skewMarginDefault(): Duration = 5.minutes

        fun cutoffTimestamp(retention: Duration): ULong {
            val nowMs = HLC().toPhysicalUnixTime()
            // Keep a skew margin to avoid early deletion when writers/readers have clock drift.
            val cutoffMs = nowMs - (retention + skewMarginDefault()).inWholeMilliseconds.toULong()
            return HLC(cutoffMs, 0u).timestamp
        }
    }
}

private fun versionstampedValuePlaceholder(): ByteArray {
    val vsLen = Versionstamp.LENGTH
    val out = ByteArray(vsLen + 4) { 0xFF.toByte() }
    out.writeIntLittleEndian(vsLen, 0)
    return out
}

private fun packWithAdjustedVersionstampOffset(prefix: ByteArray, packedWithVersionstamp: ByteArray): ByteArray {
    require(packedWithVersionstamp.size >= 4) { "Versionstamped tuple must include 4-byte offset trailer" }
    val payloadLen = packedWithVersionstamp.size - 4
    val offset = packedWithVersionstamp.readIntLittleEndian(payloadLen)
    val newOffset = offset + prefix.size

    val out = ByteArray(prefix.size + payloadLen + 4)
    prefix.copyInto(out, 0)
    packedWithVersionstamp.copyInto(out, prefix.size, 0, payloadLen)
    out.writeIntLittleEndian(prefix.size + payloadLen, newOffset)
    return out
}

private fun ULong.toBigEndianBytes(): ByteArray {
    val out = ByteArray(8)
    out[0] = ((this shr 56) and 0xFFu).toByte()
    out[1] = ((this shr 48) and 0xFFu).toByte()
    out[2] = ((this shr 40) and 0xFFu).toByte()
    out[3] = ((this shr 32) and 0xFFu).toByte()
    out[4] = ((this shr 24) and 0xFFu).toByte()
    out[5] = ((this shr 16) and 0xFFu).toByte()
    out[6] = ((this shr 8) and 0xFFu).toByte()
    out[7] = (this and 0xFFu).toByte()
    return out
}

private fun ByteArray.readIntLittleEndian(offset: Int): Int {
    return (this[offset].toInt() and 0xFF) or
        ((this[offset + 1].toInt() and 0xFF) shl 8) or
        ((this[offset + 2].toInt() and 0xFF) shl 16) or
        ((this[offset + 3].toInt() and 0xFF) shl 24)
}

private fun ByteArray.writeIntLittleEndian(offset: Int, value: Int) {
    this[offset] = (value and 0xFF).toByte()
    this[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    this[offset + 2] = ((value ushr 16) and 0xFF).toByte()
    this[offset + 3] = ((value ushr 24) and 0xFF).toByte()
}
