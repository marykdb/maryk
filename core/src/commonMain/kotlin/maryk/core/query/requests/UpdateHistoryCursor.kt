package maryk.core.query.requests

import maryk.core.exceptions.RequestException
import maryk.core.models.IsRootDataModel
import maryk.core.models.TypedObjectDataModel
import maryk.core.properties.definitions.contextual.DataModelReference
import maryk.core.properties.definitions.flexBytes
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.protobuf.WriteCache
import maryk.core.query.RequestContext
import maryk.core.values.ObjectValues

/** Opaque continuation for an update-history query. Forward [token] unchanged. */
class UpdateHistoryCursor private constructor(val token: Bytes) {
    override fun equals(other: Any?) = other is UpdateHistoryCursor && token == other.token
    override fun hashCode() = token.hashCode()
    override fun toString() = token.toString()

    object Model : TypedObjectDataModel<UpdateHistoryCursor, Model, RequestContext, RequestContext>() {
        val token by flexBytes(1u, UpdateHistoryCursor::token)
        override fun invoke(values: ObjectValues<UpdateHistoryCursor, Model>) = UpdateHistoryCursor(values(token.index))
    }

    companion object {
        internal fun create(request: IsUpdateHistoryRequest<*>, upperVersion: ULong, version: ULong, key: ByteArray): UpdateHistoryCursor {
            val name = request.dataModel.Meta.name.encodeToByteArray()
            require(name.size <= UShort.MAX_VALUE.toInt() && key.size <= UShort.MAX_VALUE.toInt())
            val bytes = ByteArray(HEADER_SIZE + name.size + key.size)
            MAGIC.copyInto(bytes)
            bytes[4] = 1
            writeSize(bytes, 5, name.size)
            writeSize(bytes, 7, key.size)
            writeVersion(bytes, 9, request.historyQueryFingerprint())
            writeVersion(bytes, 17, request.dataModel.schemaFingerprint())
            writeVersion(bytes, 25, upperVersion)
            writeVersion(bytes, 33, version)
            name.copyInto(bytes, HEADER_SIZE)
            key.copyInto(bytes, HEADER_SIZE + name.size)
            return UpdateHistoryCursor(Bytes(bytes))
        }
    }
}

/** The validated, exclusive version/key boundary and inclusive original upper bound. */
class UpdateHistoryContinuation internal constructor(
    val upperVersion: ULong,
    val version: ULong,
    val key: Bytes,
)

fun IsUpdateHistoryRequest<*>.resolveHistoryCursor(): UpdateHistoryContinuation? {
    val bytes = cursor?.token?.bytes ?: return null
    fun invalid(reason: String): Nothing = throw RequestException("Invalid update history cursor: $reason")
    if (bytes.size < HEADER_SIZE || !bytes.copyOfRange(0, 4).contentEquals(MAGIC) || bytes[4] != 1.toByte()) {
        invalid("unknown or truncated format")
    }
    val nameSize = readSize(bytes, 5)
    val keySize = readSize(bytes, 7)
    if (bytes.size != HEADER_SIZE + nameSize + keySize) invalid("invalid payload length")
    val modelName = bytes.copyOfRange(HEADER_SIZE, HEADER_SIZE + nameSize).decodeToString()
    if (modelName != dataModel.Meta.name) invalid("model does not match")
    if (readVersion(bytes, 9) != historyQueryFingerprint()) invalid("query does not match")
    if (readVersion(bytes, 17) != dataModel.schemaFingerprint()) invalid("model schema does not match")
    if (keySize != dataModel.Meta.keyByteSize) invalid("record key size does not match")
    val upperVersion = readVersion(bytes, 25)
    val version = readVersion(bytes, 33)
    if (version > upperVersion || version < fromVersion || (toVersion != null && upperVersion > toVersion!!)) {
        invalid("version is outside the query bounds")
    }
    return UpdateHistoryContinuation(upperVersion, version, Bytes(bytes.copyOfRange(HEADER_SIZE + nameSize, bytes.size)))
}

fun <DM : IsRootDataModel> IsUpdateHistoryRequest<DM>.createHistoryCursor(
    upperVersion: ULong,
    version: ULong,
    key: Key<DM>,
) = UpdateHistoryCursor.create(this, upperVersion, version, key.bytes)

private fun IsUpdateHistoryRequest<*>.historyQueryFingerprint(): ULong {
    val context = RequestContext(
        mapOf(dataModel.Meta.name to DataModelReference(dataModel)), dataModel = dataModel,
    )
    val cache = WriteCache()
    val requestType = when (this) {
        is ScanUpdateHistoryRequest<*> -> RequestType.ScanUpdateHistory
        is GetUpdateHistoryRequest<*> -> RequestType.GetUpdateHistory
        else -> throw RequestException("Unsupported update history request")
    }
    var hash = 14695981039346656037uL xor requestType.index.toULong()
    val writer: (Byte) -> Unit = { hash = (hash xor it.toUByte().toULong()) * 1099511628211uL }
    when (this) {
        is ScanUpdateHistoryRequest<*> -> {
            val query = copy(cursor = null, limit = 1u)
            ScanUpdateHistoryRequest.Serializer.calculateObjectProtoBufLength(query, cache, context)
            ScanUpdateHistoryRequest.Serializer.writeObjectProtoBuf(query, cache, writer, context)
        }
        is GetUpdateHistoryRequest<*> -> {
            val query = copy(cursor = null, limit = 1u)
            GetUpdateHistoryRequest.Serializer.calculateObjectProtoBufLength(query, cache, context)
            GetUpdateHistoryRequest.Serializer.writeObjectProtoBuf(query, cache, writer, context)
        }
        else -> throw RequestException("Unsupported update history request")
    }
    return hash
}

private val MAGIC = byteArrayOf(0x4d, 0x4b, 0x55, 0x48)
private const val HEADER_SIZE = 41
private fun writeSize(bytes: ByteArray, offset: Int, value: Int) {
    bytes[offset] = (value ushr 8).toByte()
    bytes[offset + 1] = value.toByte()
}
private fun readSize(bytes: ByteArray, offset: Int) =
    (bytes[offset].toUByte().toInt() shl 8) or bytes[offset + 1].toUByte().toInt()

private fun writeVersion(bytes: ByteArray, offset: Int, value: ULong) {
    repeat(8) { bytes[offset + it] = (value shr (56 - it * 8)).toByte() }
}
private fun readVersion(bytes: ByteArray, offset: Int): ULong {
    var result = 0uL
    repeat(8) { result = (result shl 8) or bytes[offset + it].toUByte().toULong() }
    return result
}
