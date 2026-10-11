package maryk.core.query.responses

import maryk.core.models.SimpleQueryModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.embedObject
import maryk.core.properties.definitions.boolean
import maryk.core.properties.definitions.enum
import maryk.core.properties.definitions.flexBytes
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.string
import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IsCoreEnum
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.requests.RequestType
import maryk.core.values.SimpleObjectValues

enum class TransactionStatus(override val index: UInt, override val alternativeNames: Set<String>? = null) :
    IndexedEnumComparable<TransactionStatus>, IsCoreEnum {
    Staged(1u), Committed(2u), Aborted(3u), OutcomeUnknown(4u), Unsupported(5u);
    companion object : IndexedEnumDefinition<TransactionStatus>(TransactionStatus::class, { entries })
}

/** Staged versions are transaction-local. Only Committed reports durable mutation results. */
data class TransactionResponse(
    val status: TransactionStatus,
    val responses: List<IsDataModelResponse<*>> = emptyList(),
    val revision: Bytes? = null,
    val version: ULong? = null,
    val failedRequestIndex: UInt? = null,
    val message: String? = null,
    val failure: AtomicFailure? = null,
    val failedGuard: Boolean = false,
) : IsResponse {
    companion object : SimpleQueryModel<TransactionResponse>() {
        val status by enum(1u, TransactionResponse::status, enum = TransactionStatus)
        val responses by list(2u, getter = { value -> value.responses.map { response ->
            TypedValue(when (response) {
                is AddResponse<*> -> RequestType.Add
                is ChangeResponse<*> -> RequestType.Change
                is DeleteResponse<*> -> RequestType.Delete
                is ValuesResponse<*> -> RequestType.Get
                else -> error("Invalid transaction response")
            }, response)
        } }, default = emptyList(), valueDefinition = InternalMultiTypeDefinition(typeEnum = RequestType, definitionMap = mapOf(
            RequestType.Add to EmbeddedObjectDefinition(dataModel = { AddResponse }),
            RequestType.Change to EmbeddedObjectDefinition(dataModel = { ChangeResponse }),
            RequestType.Delete to EmbeddedObjectDefinition(dataModel = { DeleteResponse }),
            RequestType.Get to EmbeddedObjectDefinition(dataModel = { ValuesResponse }),
        )))
        val revision by flexBytes(3u, TransactionResponse::revision, required = false, maxSize = 32u)
        val version by number(4u, TransactionResponse::version, UInt64, required = false)
        val failedRequestIndex by number(5u, TransactionResponse::failedRequestIndex, UInt32, required = false)
        val message by string(6u, TransactionResponse::message, required = false)
        val failure by embedObject(7u, TransactionResponse::failure, dataModel = { AtomicFailure }, required = false)
        val failedGuard by boolean(8u, TransactionResponse::failedGuard, default = false)
        override fun invoke(values: SimpleObjectValues<TransactionResponse>) = TransactionResponse(
            values(status.index), values<List<TypedValue<RequestType, Any>>>(responses.index).map { it.value as IsDataModelResponse<*> },
            values(revision.index), values(version.index), values(failedRequestIndex.index), values(message.index), values(failure.index), values(failedGuard.index),
        )
    }
}
