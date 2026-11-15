package maryk.datastore.shared

import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsRootDataModel
import maryk.core.models.TypedObjectDataModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.list
import maryk.core.protobuf.WriteCache
import maryk.core.query.RequestContext
import maryk.core.query.requests.MAX_ATOMIC_OPERATIONS
import maryk.core.query.requests.AtomicCapabilities
import maryk.core.query.responses.UpdateResponse
import maryk.core.values.ObjectValues

/** The version-one restore envelope, shared by transport adapters and request ownership. */
object RestoreVersionTransportCodec {
    fun encode(request: RestoreVersionRequest, context: RequestContext, maxBytes: Int): ByteArray {
        context.rejectUnknownProtoBufFields = true
        val value = RestoreVersionPayload(request.updates)
        val cache = WriteCache()
        val length = RestoreVersionPayload.Serializer.calculateObjectProtoBufLength(value, cache, context)
        check(length >= 0) { "Proto payload length is negative: $length" }
        check(length <= maxBytes) { "Proto payload exceeds max size: $length > $maxBytes" }
        val bytes = ByteArray(length)
        var offset = 0
        RestoreVersionPayload.Serializer.writeObjectProtoBuf(value, cache, { byte ->
            check(offset < bytes.size) { "Proto length mismatch: attempted to write past ${bytes.size} bytes." }
            bytes[offset++] = byte
        }, context)
        check(offset == bytes.size) { "Proto length mismatch: wrote $offset of ${bytes.size} bytes." }
        return bytes
    }

    /** Decode once with strict schema checks; the resulting request still owns its containers. */
    fun decode(bytes: ByteArray, context: RequestContext, maxBytes: Int, capabilities: AtomicCapabilities = AtomicCapabilities()): RestoreVersionRequest {
        check(bytes.size <= maxBytes) { "Proto payload exceeds max size: ${bytes.size} > $maxBytes" }
        context.rejectUnknownProtoBufFields = true
        var offset = 0
        val payload = try {
            RestoreVersionPayload.Serializer.readProtoBuf(bytes.size, { bytes[offset++] }, context).toDataObject()
        } catch (error: Throwable) {
            error.rethrowIfFatal()
            throw IllegalStateException("Invalid proto payload or trailing bytes", error)
        }
        check(offset == bytes.size) { "Proto payload has trailing bytes: consumed=$offset total=${bytes.size}" }
        @Suppress("UNCHECKED_CAST")
        val request = RestoreVersionRequest.staged(payload.updates as List<UpdateResponse<IsRootDataModel>>)
        val limits = if (capabilities.maxRequestBytes > MAX_ATOMIC_BYTES.toUInt()) {
            capabilities.copy(maxRequestBytes = MAX_ATOMIC_BYTES.toUInt())
        } else capabilities
        validateRestoreVersion(request, limits)
        return request
    }
}

private data class RestoreVersionPayload(val updates: List<UpdateResponse<*>>) {
    companion object : TypedObjectDataModel<RestoreVersionPayload, IsObjectDataModel<RestoreVersionPayload>, RequestContext, RequestContext>() {
        val updates by list(1u, RestoreVersionPayload::updates, maxSize = MAX_ATOMIC_OPERATIONS,
            valueDefinition = EmbeddedObjectDefinition(dataModel = { UpdateResponse }))
        override fun invoke(values: ObjectValues<RestoreVersionPayload, IsObjectDataModel<RestoreVersionPayload>>) =
            RestoreVersionPayload(values(updates.index))
    }
}
