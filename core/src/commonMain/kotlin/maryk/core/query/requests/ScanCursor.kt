package maryk.core.query.requests

import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsValueDataModel
import maryk.core.models.TypedObjectDataModel
import maryk.core.properties.definitions.DateTimeDefinition
import maryk.core.properties.definitions.DecimalDefinition
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.properties.definitions.IsFixedStorageBytesEncodable
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsStorageBytesEncodable
import maryk.core.properties.definitions.IsTransportablePropertyDefinitionType
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.ReferenceDefinition
import maryk.core.properties.definitions.TimeDefinition
import maryk.core.properties.definitions.ValueObjectDefinition
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.definitions.flexBytes
import maryk.core.properties.definitions.index.AnyOf
import maryk.core.properties.definitions.index.GeoHash
import maryk.core.properties.definitions.index.IndexKeyPartType
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.Normalize
import maryk.core.properties.definitions.index.ReferenceToMax
import maryk.core.properties.definitions.index.Reversed
import maryk.core.properties.definitions.index.Split
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.MapAnyKeyReference
import maryk.core.properties.references.SetAnyValueReference
import maryk.core.properties.references.TypeReference
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.protobuf.WriteCache
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues

/**
 * Opaque, versioned continuation token for a [ScanRequest].
 *
 * Applications should persist or forward [token] unchanged.
 */
class ScanCursor private constructor(
    val token: Bytes,
) {
    override fun equals(other: Any?) = other is ScanCursor && token == other.token
    override fun hashCode() = token.hashCode()
    override fun toString() = token.toString()

    object Model : TypedObjectDataModel<ScanCursor, Model, RequestContext, RequestContext>() {
        val token by flexBytes(1u, ScanCursor::token)

        override fun invoke(values: ObjectValues<ScanCursor, Model>) =
            ScanCursor(values(token.index))
    }

    companion object {
        internal fun create(
            modelName: String,
            queryFingerprint: ULong,
            schemaFingerprint: ULong,
            key: ByteArray,
            orderKey: ByteArray?,
        ) = ScanCursor(
            Bytes(encodeCursor(modelName, queryFingerprint, schemaFingerprint, key, orderKey))
        )
    }
}

/**
 * Validated cursor boundary for datastore implementations.
 *
 * Applications should treat [ScanCursor] itself as opaque.
 */
class ScanContinuation internal constructor(
    val key: Bytes,
    val orderKey: Bytes?,
)

/** Creates the next-page cursor after a datastore emitted [key]. */
fun <DM : IsRootDataModel> ScanRequest<DM>.createCursor(
    key: Key<DM>,
    orderKey: ByteArray?,
): ScanCursor = ScanCursor.create(
    modelName = dataModel.Meta.name,
    queryFingerprint = queryFingerprint(),
    schemaFingerprint = dataModel.schemaFingerprint(),
    key = key.bytes,
    orderKey = orderKey,
)

/** Decodes and validates the cursor for use by a datastore implementation. */
fun ScanRequest<*>.resolveCursor(): ScanContinuation? {
    val cursor = cursor ?: return null
    val payload = decodeCursor(cursor.token.bytes)
    if (payload.modelName != dataModel.Meta.name) {
        throw RequestException(
            "Scan cursor model `${payload.modelName}` does not match `${dataModel.Meta.name}`"
        )
    }
    if (payload.queryFingerprint != queryFingerprint()) {
        throw RequestException("Scan cursor does not match this query")
    }
    if (payload.schemaFingerprint != dataModel.schemaFingerprint()) {
        throw RequestException("Scan cursor does not match this model schema")
    }
    if (payload.key.size != dataModel.Meta.keyByteSize) {
        throw RequestException(
            "Scan cursor key size ${payload.key.size} does not match ${dataModel.Meta.keyByteSize}"
        )
    }
    return ScanContinuation(
        key = Bytes(payload.key),
        orderKey = payload.orderKey?.let(::Bytes),
    )
}

private data class CursorPayload(
    val modelName: String,
    val queryFingerprint: ULong,
    val schemaFingerprint: ULong,
    val key: ByteArray,
    val orderKey: ByteArray?,
)

private fun ScanRequest<*>.queryFingerprint(): ULong {
    val fingerprintRequest = copyForFingerprint()
    val context = RequestContext(
        mapOf(dataModel.Meta.name to DataModelReference(dataModel)),
        dataModel = dataModel,
    )
    val cache = WriteCache()
    val length = ScanRequest.Serializer.calculateObjectProtoBufLength(fingerprintRequest, cache, context)
    var hash = FNV_OFFSET_BASIS
    var written = 0
    ScanRequest.Serializer.writeObjectProtoBuf(
        fingerprintRequest,
        cache,
        { byte ->
            written++
            hash = (hash xor byte.toUByte().toULong()) * FNV_PRIME
        },
        context,
    )
    check(written == length)
    return hash
}

private fun IsRootDataModel.schemaFingerprint(): ULong {
    val encoder = ScanLayoutEncoder()
    encoder.writeByte(SCAN_LAYOUT_FINGERPRINT_VERSION)
    encoder.writeBytes(
        encodeIndexLayout(
            Meta.keyDefinition,
            LayoutTraversal(rootModelStack = mutableListOf(Meta.name)),
        )
    )
    val indexLayouts = Meta.indexes.orEmpty()
        .map {
            encodeIndexLayout(
                it,
                LayoutTraversal(rootModelStack = mutableListOf(Meta.name)),
            )
        }
        .sortedWith(Comparator(::compareLayoutBytes))
    encoder.writeUInt(indexLayouts.size.toUInt())
    indexLayouts.forEach(encoder::writeBytes)
    return encoder.fingerprint()
}

private class LayoutTraversal(
    val rootModelStack: MutableList<String>,
    val valueModelStack: MutableList<String> = mutableListOf(),
)

private fun encodeIndexLayout(
    indexable: IsIndexable,
    traversal: LayoutTraversal,
): ByteArray = ScanLayoutEncoder().apply {
    writeIndexLayout(indexable, traversal)
}.toByteArray()

private fun ScanLayoutEncoder.writeIndexLayout(
    indexable: IsIndexable,
    traversal: LayoutTraversal,
) {
    writeBytes(indexable.referenceStorageByteArray.bytes)
    when (indexable) {
        is Multiple -> indexable.references.forEach { writeIndexLayout(it, traversal) }
        is AnyOf -> indexable.references.forEach { writeIndexLayout(it, traversal) }
        is Normalize -> writeIndexLayout(indexable.reference, traversal)
        is Split -> writeIndexLayout(indexable.reference, traversal)
        is Reversed<*> -> writePropertyLayout(indexable.reference, traversal)
        is ReferenceToMax<*> -> {
            writePropertyLayout(indexable.reference, traversal)
            writeExplicitMaximum(indexable.reference.comparablePropertyDefinition)
        }
        is GeoHash -> writeIndexLayout(indexable.reference, traversal)
        is SetAnyValueReference<*, *> -> writeStorageDefinition(
            indexable.setDefinition.valueDefinition,
            traversal,
        )
        is MapAnyKeyReference<*, *, *> -> writeStorageDefinition(
            indexable.mapDefinition.keyDefinition,
            traversal,
        )
        is TypeReference<*, *, *> -> {
            writeUInt(TYPE_REFERENCE_LAYOUT)
            writeUInt(indexable.propertyDefinition.byteSize.toUInt())
        }
        is IsPropertyReference<*, *, *> -> writePropertyLayout(indexable, traversal)
        else -> when (indexable.indexKeyPartType) {
            IndexKeyPartType.UUIDv4, IndexKeyPartType.UUIDv7 -> Unit
            else -> throw RequestException(
                "Cannot fingerprint unsupported scan index layout ${indexable.indexKeyPartType.name}"
            )
        }
    }
}

private fun ScanLayoutEncoder.writePropertyLayout(
    reference: IsPropertyReference<*, *, *>,
    traversal: LayoutTraversal,
) = writeStorageDefinition(reference.comparablePropertyDefinition, traversal)

private fun ScanLayoutEncoder.writeStorageDefinition(
    definition: IsPropertyDefinition<*>,
    traversal: LayoutTraversal,
) {
    val transportable = definition as? IsTransportablePropertyDefinitionType<*>
        ?: throw RequestException("Cannot fingerprint unsupported scan property layout")
    writeUInt(transportable.propertyDefinitionType.index)
    val fixedDefinition = definition as? IsFixedStorageBytesEncodable<*>
    writeByte(if (fixedDefinition == null) 0 else 1)
    fixedDefinition?.let { writeUInt(it.byteSize.toUInt()) }

    when (definition) {
        is NumberDefinition<*> -> {
            writeUInt(definition.type.type.index)
            writeByte(if (definition.reversedStorage == true) 1 else 0)
        }
        is DecimalDefinition -> {
            writeUInt(definition.scale)
            writeByte(if (definition.reversedStorage == true) 1 else 0)
        }
        is DateTimeDefinition -> writeUInt(definition.precision.index)
        is TimeDefinition -> writeUInt(definition.precision.index)
        is ReferenceDefinition<*> -> writeRootKeyLayout(definition.dataModel, traversal)
        is ValueObjectDefinition<*, *> -> writeValueModelLayout(definition.dataModel, traversal)
    }
}

private fun ScanLayoutEncoder.writeRootKeyLayout(
    model: IsRootDataModel,
    traversal: LayoutTraversal,
) {
    val cycleIndex = traversal.rootModelStack.indexOf(model.Meta.name)
    if (cycleIndex >= 0) {
        writeByte(LAYOUT_CYCLE)
        writeUInt((traversal.rootModelStack.lastIndex - cycleIndex).toUInt())
        return
    }
    writeByte(LAYOUT_VALUE)
    traversal.rootModelStack += model.Meta.name
    writeIndexLayout(model.Meta.keyDefinition, traversal)
    traversal.rootModelStack.removeAt(traversal.rootModelStack.lastIndex)
}

private fun ScanLayoutEncoder.writeValueModelLayout(
    model: IsValueDataModel<*, *>,
    traversal: LayoutTraversal,
) {
    val definitions = model.map {
        it as? IsDefinitionWrapper<*, *, *, *>
            ?: throw RequestException("Cannot fingerprint unsupported value model layout")
    }
    val modelName = model.Meta.name
    val cycleIndex = traversal.valueModelStack.indexOf(modelName)
    if (cycleIndex >= 0) {
        writeByte(LAYOUT_CYCLE)
        writeUInt((traversal.valueModelStack.lastIndex - cycleIndex).toUInt())
        return
    }
    writeByte(LAYOUT_VALUE)
    traversal.valueModelStack += modelName
    writeUInt(definitions.size.toUInt())
    definitions.forEach { writeStorageDefinition(it.definition, traversal) }
    traversal.valueModelStack.removeAt(traversal.valueModelStack.lastIndex)
}

@Suppress("UNCHECKED_CAST")
private fun ScanLayoutEncoder.writeExplicitMaximum(definition: IsPropertyDefinition<*>) {
    val maximum = (definition as? IsComparableDefinition<*, *>)?.maxValue
    if (maximum == null) {
        writeByte(0)
        return
    }
    val storageDefinition = definition as? IsStorageBytesEncodable<Any>
        ?: throw RequestException("Cannot fingerprint scan index maximum")
    writeByte(1)
    writeBytes(storageDefinition.toStorageBytes(maximum))
}

private class ScanLayoutEncoder {
    private val bytes = mutableListOf<Byte>()

    fun writeByte(value: Byte) {
        bytes += value
    }

    fun writeByte(value: Int) = writeByte(value.toByte())

    fun writeUInt(value: UInt) {
        repeat(UInt.SIZE_BYTES) { index ->
            writeByte((value shr (24 - index * 8)).toByte())
        }
    }

    fun writeBytes(value: ByteArray) {
        writeUInt(value.size.toUInt())
        value.forEach(::writeByte)
    }

    fun toByteArray() = bytes.toByteArray()

    fun fingerprint(): ULong = bytes.fold(FNV_OFFSET_BASIS) { hash, byte ->
        (hash xor byte.toUByte().toULong()) * FNV_PRIME
    }
}

private fun compareLayoutBytes(left: ByteArray, right: ByteArray): Int {
    val length = minOf(left.size, right.size)
    repeat(length) { index ->
        val comparison = left[index].toUByte().compareTo(right[index].toUByte())
        if (comparison != 0) return comparison
    }
    return left.size.compareTo(right.size)
}

@Suppress("UNCHECKED_CAST")
private fun ScanRequest<*>.copyForFingerprint(): ScanRequest<IsRootDataModel> {
    val request = this as ScanRequest<IsRootDataModel>
    return request.copy(
        startKey = null,
        cursor = null,
        limit = 1u,
        includeStart = true,
    )
}

private fun encodeCursor(
    modelName: String,
    queryFingerprint: ULong,
    schemaFingerprint: ULong,
    key: ByteArray,
    orderKey: ByteArray?,
): ByteArray {
    val modelBytes = modelName.encodeToByteArray()
    require(modelBytes.size <= UShort.MAX_VALUE.toInt()) { "Scan cursor model name is too long" }
    require(key.size <= UShort.MAX_VALUE.toInt()) { "Scan cursor key is too long" }
    val orderSize = orderKey?.size ?: -1
    val result = ByteArray(
        CURSOR_HEADER_SIZE + modelBytes.size + key.size + if (orderSize < 0) 0 else orderSize
    )
    var offset = 0
    CURSOR_MAGIC.copyInto(result, offset)
    offset += CURSOR_MAGIC.size
    result[offset++] = CURSOR_FORMAT_VERSION
    writeUShort(result, offset, modelBytes.size)
    offset += 2
    modelBytes.copyInto(result, offset)
    offset += modelBytes.size
    writeULong(result, offset, queryFingerprint)
    offset += ULong.SIZE_BYTES
    writeULong(result, offset, schemaFingerprint)
    offset += ULong.SIZE_BYTES
    writeUShort(result, offset, key.size)
    offset += 2
    key.copyInto(result, offset)
    offset += key.size
    writeInt(result, offset, orderSize)
    offset += Int.SIZE_BYTES
    orderKey?.copyInto(result, offset)
    return result
}

private fun decodeCursor(bytes: ByteArray): CursorPayload {
    fun invalid(message: String): Nothing = throw RequestException("Invalid scan cursor: $message")
    if (bytes.size < CURSOR_HEADER_SIZE) invalid("token is truncated")
    if (!bytes.copyOfRange(0, CURSOR_MAGIC.size).contentEquals(CURSOR_MAGIC)) {
        invalid("unknown token")
    }
    var offset = CURSOR_MAGIC.size
    val version = bytes[offset++]
    if (version != CURSOR_FORMAT_VERSION) invalid("unsupported format version ${version.toUByte()}")
    val modelSize = readUShort(bytes, offset)
    offset += 2
    if (offset > bytes.size - modelSize - ULong.SIZE_BYTES * 2 - 2 - Int.SIZE_BYTES) {
        invalid("model payload is truncated")
    }
    val modelName = bytes.copyOfRange(offset, offset + modelSize).decodeToString()
    offset += modelSize
    val queryFingerprint = readULong(bytes, offset)
    offset += ULong.SIZE_BYTES
    val schemaFingerprint = readULong(bytes, offset)
    offset += ULong.SIZE_BYTES
    val keySize = readUShort(bytes, offset)
    offset += 2
    if (offset > bytes.size - keySize - Int.SIZE_BYTES) invalid("key payload is truncated")
    val key = bytes.copyOfRange(offset, offset + keySize)
    offset += keySize
    val orderSize = readInt(bytes, offset)
    offset += Int.SIZE_BYTES
    if (orderSize < -1 || orderSize > bytes.size - offset) invalid("invalid ordering payload size")
    if (orderSize >= 0 && offset + orderSize != bytes.size) invalid("trailing token bytes")
    if (orderSize == -1 && offset != bytes.size) invalid("trailing token bytes")
    val orderKey = if (orderSize < 0) null else bytes.copyOfRange(offset, offset + orderSize)
    return CursorPayload(modelName, queryFingerprint, schemaFingerprint, key, orderKey)
}

private fun writeUShort(target: ByteArray, offset: Int, value: Int) {
    target[offset] = (value ushr 8).toByte()
    target[offset + 1] = value.toByte()
}

private fun readUShort(source: ByteArray, offset: Int): Int =
    (source[offset].toUByte().toInt() shl 8) or source[offset + 1].toUByte().toInt()

private fun writeInt(target: ByteArray, offset: Int, value: Int) {
    repeat(Int.SIZE_BYTES) { index ->
        target[offset + index] = (value ushr (24 - index * 8)).toByte()
    }
}

private fun readInt(source: ByteArray, offset: Int): Int {
    var value = 0
    repeat(Int.SIZE_BYTES) { index ->
        value = (value shl 8) or source[offset + index].toUByte().toInt()
    }
    return value
}

private fun writeULong(target: ByteArray, offset: Int, value: ULong) {
    repeat(ULong.SIZE_BYTES) { index ->
        target[offset + index] = (value shr (56 - index * 8)).toByte()
    }
}

private fun readULong(source: ByteArray, offset: Int): ULong {
    var value = 0uL
    repeat(ULong.SIZE_BYTES) { index ->
        value = (value shl 8) or source[offset + index].toUByte().toULong()
    }
    return value
}

private val CURSOR_MAGIC = byteArrayOf(0x4d, 0x4b, 0x53, 0x43)
private const val CURSOR_FORMAT_VERSION: Byte = 2
private const val CURSOR_HEADER_SIZE = 4 + 1 + 2 + ULong.SIZE_BYTES * 2 + 2 + Int.SIZE_BYTES
private const val SCAN_LAYOUT_FINGERPRINT_VERSION: Byte = 1
private const val LAYOUT_CYCLE: Byte = 0
private const val LAYOUT_VALUE: Byte = 1
private const val TYPE_REFERENCE_LAYOUT: UInt = 0u
private const val FNV_OFFSET_BASIS = 14695981039346656037uL
private const val FNV_PRIME = 1099511628211uL
