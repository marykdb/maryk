package maryk.datastore.foundationdb.clusterlog

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.models.serializers.IsDataModelSerializer
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
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
import maryk.lib.bytes.combineToByteArray
import maryk.lib.extensions.compare.compareTo
import maryk.lib.exceptions.ParseException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private const val maxUShortLength = 0xFFFF
private val nextKeySuffix = byteArrayOf(0)
private const val retentionIndexMarker = "retention-v2"
private const val cursorMarker = "cluster-log-cursor-v2"

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

    init {
        require(shardCount > 0) { "shardCount should be positive" }
        require(originBytes.size <= maxUShortLength) { "originId encoded length should fit in 16 bits" }
    }

    private val definitionsContext = DefinitionsContext(
        dataModels = dataModelsById.values
            .associateBy { it.Meta.name }
            .mapValues { DataModelReference(it.value) }
            .toMutableMap()
    )

    fun append(tr: Transaction, modelId: UInt, update: ClusterLogUpdate) {
        val shard = shardFor(modelId, update.keyBytes.bytes)
        val hlcBytes = update.version.toBigEndianBytes()
        val versionstamp = Versionstamp.incomplete()
        val key = buildLogKey(modelId, update, versionstamp)
        val retentionKey = buildRetentionKey(shard, modelId, hlcBytes, update.keyBytes.bytes, originBytes, versionstamp)
        val value = encodeValue(modelId, update, dataModelsById.getValue(modelId))
        tr.mutate(MutationType.SET_VERSIONSTAMPED_KEY, key, value)
        tr.mutate(MutationType.SET_VERSIONSTAMPED_KEY, retentionKey, byteArrayOf())

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
        require(limit > 0) { "Tail limit should be positive" }
        val shardModelPrefix = shardModelPrefix(shard, modelId)
        val shardModelRange = Range.startsWith(shardModelPrefix)
        val v2RangeStart = v2RangeStart(shard, modelId)
        var cursor = decodeCursor(cursorKey, shard, modelId)
        val updates = ArrayList<DecodedUpdate>(limit)
        val range = if (cursor.v2Active) {
            val begin = cursor.v2Key?.let { combineToByteArray(it, nextKeySuffix) } ?: v2RangeStart
            Range(begin, shardModelRange.end)
        } else {
            val begin = cursor.legacyKey?.let { combineToByteArray(it, nextKeySuffix) } ?: shardModelRange.begin
            Range(begin, v2RangeStart)
        }
        val entries = tr.getRange(range, limit, false).iterator()
        var processed = 0
        while (entries.hasNext()) {
            val kv = entries.nextBlocking()
            cursor = if (cursor.v2Active) cursor.copy(v2Key = kv.key) else cursor.copy(legacyKey = kv.key)
            processed++
            decodeEntry(kv.key, kv.value)?.also { decoded ->
                if (decoded.header.origin != originId && cursor.includes(decoded)) {
                    updates += decoded
                }
            }
        }

        val activatedV2 = !cursor.v2Active && processed == 0
        if (activatedV2) cursor = cursor.copy(v2Active = true)

        return TailResult(
            lastKey = if (processed > 0 || activatedV2) encodeCursor(cursor) else null,
            decoded = updates
        )
    }

    fun readCursorKey(tr: Transaction, shard: Int, modelId: UInt): ByteArray? =
        tr.get(cursorKey(shard, modelId)).awaitResult()

    fun writeCursorKey(tr: Transaction, shard: Int, modelId: UInt, lastKey: ByteArray) {
        tr.set(cursorKey(shard, modelId), lastKey)
    }

    fun clearBefore(
        tr: Transaction,
        shard: Int,
        modelId: UInt,
        cutoff: ULong,
        limit: Int = 128,
        maxAffectedKeyBytes: Int = 1_048_576,
    ): Boolean {
        require(limit > 0) { "Retention clear limit should be positive" }
        require(maxAffectedKeyBytes > 0) { "Retention clear byte limit should be positive" }
        val shardModelPrefix = shardModelPrefix(shard, modelId)
        val end = minimalKeyAtOrAfter(shard, modelId, cutoff)
        tr.clear(Range(shardModelPrefix, end))

        val retentionRange = retentionRangeBefore(shard, modelId, cutoff)
        val retentionEntries = tr.getRange(retentionRange, limit, false).iterator()
        var cleared = 0
        var affectedKeyBytes = 0
        var stoppedForBytes = false
        while (retentionEntries.hasNext()) {
            val retentionKey = retentionEntries.nextBlocking().key
            val mainKey = mainLogKeyFromRetentionKey(retentionKey)
            val entryBytes = retentionKey.size + (mainKey?.size ?: 0)
            if (cleared > 0 && affectedKeyBytes + entryBytes > maxAffectedKeyBytes) {
                stoppedForBytes = true
                break
            }
            mainKey?.also(tr::clear)
            tr.clear(retentionKey)
            cleared++
            affectedKeyBytes += entryBytes
        }
        return stoppedForBytes || cleared == limit
    }

    fun cursorIsBeforeCutoff(shard: Int, modelId: UInt, cursorKey: ByteArray, cutoff: ULong): Boolean {
        val cursor = decodeCursor(cursorKey, shard, modelId)
        // A V2 cursor already carries an explicit startup floor and advances through both key regions.
        // Retention GC removes expired rows behind it, so repeatedly moving this floor would add a
        // consumer write transaction on every small cutoff-clock advance.
        if (cursor.minimumHlc != null) return false
        val legacyKey = cursor.legacyKey ?: return true
        return legacyKey < minimalKeyAtOrAfter(shard, modelId, cutoff)
    }

    fun initialCursorAtOrAfter(shard: Int, modelId: UInt, cutoff: ULong): ByteArray = encodeCursor(
        ClusterLogCursor(
            legacyKey = minimalKeyAtOrAfter(shard, modelId, cutoff),
            minimumHlc = cutoff,
        )
    )

    fun advanceCursorToCutoff(
        shard: Int,
        modelId: UInt,
        cursorKey: ByteArray,
        cutoff: ULong,
    ): ByteArray {
        val cursor = decodeCursor(cursorKey, shard, modelId)
        val legacyCutoff = minimalKeyAtOrAfter(shard, modelId, cutoff)
        return encodeCursor(
            cursor.copy(
                legacyKey = cursor.legacyKey?.let { if (it >= legacyCutoff) it else legacyCutoff } ?: legacyCutoff,
                minimumHlc = maxOf(cursor.minimumHlc ?: 0uL, cutoff),
            )
        )
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
        val commitVersion: Long? = null,
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
        require(keyLen <= maxUShortLength) { "key encoded length should fit in 16 bits" }

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
        val origin = value.decodeToString(o, o + originLen)
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
        if (payloadLen < 0 || payloadLen != value.size - o) return null
        val decodedUpdate = when (type) {
            ClusterLogUpdate.TYPE_ADDITION -> {
                val valuesDecoded = decodeValuesBytes(ctx, dataModel, value, o, payloadLen)
                    ?: return null
                ClusterLogAddition(keyBytes = keyBytes, version = version, values = valuesDecoded)
            }
            ClusterLogUpdate.TYPE_CHANGE -> {
                val changes = decodeChangesBytes(ctx, value, o, payloadLen) ?: return null
                ClusterLogChange(keyBytes = keyBytes, version = version, changes = changes)
            }
            ClusterLogUpdate.TYPE_DELETION -> {
                if (payloadLen != 1) return null
                ClusterLogDeletion(keyBytes = keyBytes, version = version, hardDelete = value[o].toInt() != 0)
            }
            else -> return null
        }

        return DecodedUpdate(
            header = ClusterLogHeader(origin = origin, modelId = modelId),
            update = decodedUpdate
        )
    }

    internal fun decodeEntry(key: ByteArray, value: ByteArray): DecodedUpdate? =
        decodeValue(value)?.copy(commitVersion = commitVersionFromLogKey(key))

    internal fun buildLogKey(
        modelId: UInt,
        update: ClusterLogUpdate,
        versionstamp: Versionstamp,
    ): ByteArray = buildLogKey(
        shard = shardFor(modelId, update.keyBytes.bytes),
        modelId = modelId,
        hlcBytes = update.version.toBigEndianBytes(),
        keyBytes = update.keyBytes.bytes,
        origin = originBytes,
        versionstamp = versionstamp,
    )

    internal fun buildLegacyLogKey(modelId: UInt, update: ClusterLogUpdate): ByteArray =
        packVersionstampedTuple(
            Tuple.from(
                shardFor(modelId, update.keyBytes.bytes),
                modelId.toLong(),
                update.version.toBigEndianBytes(),
                update.keyBytes.bytes,
                originBytes,
                Versionstamp.incomplete(),
            )
        )

    private fun buildLogKey(
        shard: Int,
        modelId: UInt,
        hlcBytes: ByteArray,
        keyBytes: ByteArray,
        origin: ByteArray,
        versionstamp: Versionstamp,
    ): ByteArray = packVersionstampedTuple(
        Tuple.from(shard, modelId.toLong(), versionstamp, hlcBytes, keyBytes, origin),
    )

    private fun buildRetentionKey(
        shard: Int,
        modelId: UInt,
        hlcBytes: ByteArray,
        keyBytes: ByteArray,
        origin: ByteArray,
        versionstamp: Versionstamp,
    ): ByteArray = packVersionstampedTuple(
        Tuple.from(retentionIndexMarker, shard, modelId.toLong(), hlcBytes, keyBytes, origin, versionstamp),
    )

    private fun packVersionstampedTuple(tuple: Tuple): ByteArray =
        if (tuple.items.any { it is Versionstamp && !it.isComplete }) {
            packWithAdjustedVersionstampOffset(logPrefix, tuple.packWithVersionstamp())
        } else {
            combineToByteArray(logPrefix, tuple.pack())
        }

    private fun commitVersionFromLogKey(key: ByteArray): Long? {
        val versionstamp = versionstampFromLogKey(key) ?: return null
        return versionstamp.transactionVersion().readLongBigEndian()
    }

    private fun versionstampFromLogKey(key: ByteArray): Versionstamp? {
        val tuple = unpackLogTuple(key) ?: return null
        if (tuple.size < 3) return null
        val orderField = tuple[2]
        val versionstamp = when (orderField) {
            is Versionstamp -> orderField
            is ByteArray -> if (tuple.size > 5) tuple[5] as? Versionstamp else null
            else -> null
        }
        return versionstamp?.takeIf { it.isComplete }
    }

    private fun v2RangeStart(shard: Int, modelId: UInt): ByteArray = combineToByteArray(
        logPrefix,
        Tuple.from(shard, modelId.toLong(), Versionstamp.complete(ByteArray(10))).pack(),
    )

    private fun decodeCursor(cursorKey: ByteArray?, shard: Int, modelId: UInt): ClusterLogCursor {
        if (cursorKey == null) return ClusterLogCursor()
        val encodedCursor = try {
            Tuple.fromBytes(cursorKey)
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IndexOutOfBoundsException) {
            null
        }
        if (encodedCursor != null && encodedCursor.size > 0 && encodedCursor[0] == cursorMarker) {
            require(encodedCursor.size == 5) { "Invalid cluster-log cursor field count" }
            val legacyKey = encodedCursor[1]
            val v2Key = encodedCursor[2]
            val minimumHlcBytes = encodedCursor[3]
            val v2Active = encodedCursor[4]
            require(legacyKey == null || legacyKey is ByteArray) { "Invalid legacy cursor key" }
            require(v2Key == null || v2Key is ByteArray) { "Invalid V2 cursor key" }
            require(minimumHlcBytes == null || minimumHlcBytes is ByteArray) { "Invalid cursor HLC" }
            require(minimumHlcBytes == null || minimumHlcBytes.size == ULong.SIZE_BYTES) {
                "Invalid cursor HLC length"
            }
            require(v2Active is Boolean) { "Invalid cursor phase" }
            return ClusterLogCursor(
                legacyKey = legacyKey,
                v2Key = v2Key,
                minimumHlc = minimumHlcBytes?.readULongBigEndian(),
                v2Active = v2Active,
            ).also { validateCursor(it, shard, modelId) }
        }

        val logTuple = unpackLogTuple(cursorKey)
        return if (logTuple?.let { it.size >= 3 && it[2] is Versionstamp } == true) {
            ClusterLogCursor(v2Key = cursorKey, v2Active = true)
        } else {
            ClusterLogCursor(legacyKey = cursorKey)
        }.also { validateCursor(it, shard, modelId) }
    }

    private fun validateCursor(cursor: ClusterLogCursor, shard: Int, modelId: UInt) {
        cursor.legacyKey?.also { validateRegionCursor(it, shard, modelId, expectsV2 = false) }
        cursor.v2Key?.also { validateRegionCursor(it, shard, modelId, expectsV2 = true) }
    }

    private fun validateRegionCursor(key: ByteArray, shard: Int, modelId: UInt, expectsV2: Boolean) {
        val tuple = unpackLogTuple(key)
        require(tuple != null && tuple.size >= 3) { "Cluster-log cursor key is outside the log prefix" }
        require(tuple[0] == shard.toLong() && tuple[1] == modelId.toLong()) {
            "Cluster-log cursor key belongs to another shard or model"
        }
        require((tuple[2] is Versionstamp) == expectsV2) { "Cluster-log cursor key belongs to the wrong format region" }
        if (!expectsV2) require(tuple[2] is ByteArray) { "Invalid legacy cursor ordering field" }
    }

    private fun encodeCursor(cursor: ClusterLogCursor): ByteArray = Tuple.from(
        cursorMarker,
        cursor.legacyKey,
        cursor.v2Key,
        cursor.minimumHlc?.toBigEndianBytes(),
        cursor.v2Active,
    ).pack()

    private data class ClusterLogCursor(
        val legacyKey: ByteArray? = null,
        val v2Key: ByteArray? = null,
        val minimumHlc: ULong? = null,
        // One-way transition: drain the persisted V1 backlog, then tail only commit-ordered V2 keys.
        // Operators must quiesce and upgrade every writer/reader before activation. Old binaries cannot
        // observe or enforce this marker, so concurrent V1 writes after activation are unsupported.
        val v2Active: Boolean = false,
    ) {
        fun includes(decoded: DecodedUpdate): Boolean =
            minimumHlc?.let { decoded.update.version >= it } ?: true
    }

    private fun retentionRangeBefore(shard: Int, modelId: UInt, cutoff: ULong): Range {
        val prefix = combineToByteArray(
            logPrefix,
            Tuple.from(retentionIndexMarker, shard, modelId.toLong()).pack(),
        )
        val end = combineToByteArray(
            logPrefix,
            Tuple.from(retentionIndexMarker, shard, modelId.toLong(), cutoff.toBigEndianBytes()).pack(),
        )
        return Range(prefix, end)
    }

    private fun mainLogKeyFromRetentionKey(key: ByteArray): ByteArray? {
        val tuple = unpackLogTuple(key) ?: return null
        if (tuple.size < 7 || tuple[0] != retentionIndexMarker) return null
        val shard = tuple[1] as? Long ?: return null
        val modelId = tuple[2] as? Long ?: return null
        val hlcBytes = tuple[3] as? ByteArray ?: return null
        val keyBytes = tuple[4] as? ByteArray ?: return null
        val origin = tuple[5] as? ByteArray ?: return null
        val versionstamp = tuple[6] as? Versionstamp ?: return null
        if (shard !in 0..Int.MAX_VALUE.toLong() || modelId !in 0..UInt.MAX_VALUE.toLong()) return null
        return buildLogKey(shard.toInt(), modelId.toUInt(), hlcBytes, keyBytes, origin, versionstamp)
    }

    private fun unpackLogTuple(key: ByteArray): Tuple? {
        if (key.size <= logPrefix.size || !key.startsWith(logPrefix)) return null
        return try {
            Tuple.fromBytes(key.copyOfRange(logPrefix.size, key.size))
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IndexOutOfBoundsException) {
            null
        }
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

    private fun decodeValuesBytes(
        ctx: RequestContext,
        dataModel: IsRootDataModel,
        bytes: ByteArray,
        offset: Int,
        length: Int
    ): maryk.core.values.Values<*>? {
        var i = offset
        val end = offset + length
        @Suppress("UNCHECKED_CAST")
        val serializer =
            dataModel.Serializer as IsDataModelSerializer<maryk.core.values.Values<IsRootDataModel>, IsRootDataModel, RequestContext>
        return try {
            serializer.readProtoBuf(length, { bytes[i++] }, ctx).also {
                if (i != end) return null
            }
        } catch (_: ParseException) {
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

    private fun decodeChangesBytes(
        ctx: RequestContext,
        bytes: ByteArray,
        offset: Int,
        length: Int
    ): List<maryk.core.query.changes.IsChange>? {
        var i = offset
        val end = offset + length
        return try {
            val serializer = VersionedChanges.Serializer
            val values = serializer.readProtoBuf(length, { bytes[i++] }, ctx).toDataObject()
            if (i != end) return null
            values.changes
        } catch (_: ParseException) {
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

internal fun packWithAdjustedVersionstampOffset(prefix: ByteArray, packedWithVersionstamp: ByteArray): ByteArray {
    require(packedWithVersionstamp.size >= 4) { "Versionstamped tuple must include 4-byte offset trailer" }
    val payloadLen = packedWithVersionstamp.size - 4
    val offset = packedWithVersionstamp.readIntLittleEndian(payloadLen)
    require(offset >= 0) { "Versionstamp offset cannot be negative: $offset" }
    require(offset <= Int.MAX_VALUE - prefix.size) { "Versionstamp offset exceeds Int range" }
    val newOffset = offset + prefix.size
    val outputSize = prefix.size
        .checkedClusterLogByteLengthPlus(payloadLen)
        .checkedClusterLogByteLengthPlus(4)

    val out = ByteArray(outputSize)
    prefix.copyInto(out, 0)
    packedWithVersionstamp.copyInto(out, prefix.size, 0, payloadLen)
    out.writeIntLittleEndian(prefix.size + payloadLen, newOffset)
    return out
}

internal fun Int.checkedClusterLogByteLengthPlus(addend: Int): Int {
    require(addend >= 0) { "Cluster log byte length cannot be negative: $addend" }
    require(this <= Int.MAX_VALUE - addend) { "Cluster log byte length exceeds Int range" }
    return this + addend
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

private fun ByteArray.readLongBigEndian(): Long {
    require(size >= Long.SIZE_BYTES) { "Transaction version should contain at least 8 bytes" }
    var value = 0L
    for (index in 0 until Long.SIZE_BYTES) {
        value = (value shl Byte.SIZE_BITS) or (this[index].toLong() and 0xFFL)
    }
    return value
}

private fun ByteArray.readULongBigEndian(): ULong {
    require(size >= ULong.SIZE_BYTES) { "HLC should contain at least 8 bytes" }
    var value = 0uL
    for (index in 0 until ULong.SIZE_BYTES) {
        value = (value shl Byte.SIZE_BITS) or (this[index].toULong() and 0xFFuL)
    }
    return value
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
    if (size < prefix.size) return false
    for (index in prefix.indices) {
        if (this[index] != prefix[index]) return false
    }
    return true
}
