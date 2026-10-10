package maryk.core.query.responses

import maryk.core.models.SimpleQueryModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.InternalMultiTypeDefinition
import maryk.core.properties.definitions.boolean
import maryk.core.properties.definitions.enum
import maryk.core.properties.definitions.list
import maryk.core.properties.definitions.number
import maryk.core.properties.definitions.string
import maryk.core.properties.enum.IndexedEnumComparable
import maryk.core.properties.enum.IndexedEnumDefinition
import maryk.core.properties.enum.IsCoreEnum
import maryk.core.properties.types.TypedValue
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.requests.RequestType
import maryk.core.values.SimpleObjectValues

enum class AtomicStatus(override val index: UInt, override val alternativeNames: Set<String>? = null) :
    IndexedEnumComparable<AtomicStatus>, IsCoreEnum {
    Committed(1u), Rejected(2u), Unsupported(3u), OutcomeUnknown(4u);
    companion object : IndexedEnumDefinition<AtomicStatus>(AtomicStatus::class, { entries })
}

/** Only committed responses describe successful mutations. Rejections contain only the failed member. */
data class AtomicResponse(
    val status: AtomicStatus,
    val responses: List<IsDataModelResponse<*>> = emptyList(),
    val failedRequestIndex: UInt? = null,
    val failedGuard: Boolean = false,
    val message: String? = null,
) : IsResponse {
    companion object : SimpleQueryModel<AtomicResponse>() {
        val status by enum(1u, AtomicResponse::status, enum = AtomicStatus)
        val responses by list(
            index = 2u,
            getter = { value -> value.responses.map { response ->
                val type = when (response) {
                    is AddResponse<*> -> RequestType.Add
                    is ChangeResponse<*> -> RequestType.Change
                    is DeleteResponse<*> -> RequestType.Delete
                    else -> error("Invalid atomic member response")
                }
                TypedValue(type, response)
            } },
            default = emptyList(),
            valueDefinition = InternalMultiTypeDefinition(
                typeEnum = RequestType,
                definitionMap = mapOf(
                    RequestType.Add to EmbeddedObjectDefinition(dataModel = { AddResponse }),
                    RequestType.Change to EmbeddedObjectDefinition(dataModel = { ChangeResponse }),
                    RequestType.Delete to EmbeddedObjectDefinition(dataModel = { DeleteResponse }),
                ),
            ),
        )
        val failedRequestIndex by number(3u, AtomicResponse::failedRequestIndex, UInt32, required = false)
        val failedGuard by boolean(4u, AtomicResponse::failedGuard, default = false)
        val message by string(5u, AtomicResponse::message, required = false)
        override fun invoke(values: SimpleObjectValues<AtomicResponse>) = AtomicResponse(
            status = values(status.index),
            responses = values<List<TypedValue<RequestType, Any>>>(responses.index).map { it.value as IsDataModelResponse<*> },
            failedRequestIndex = values(failedRequestIndex.index),
            failedGuard = values(failedGuard.index),
            message = values(message.index),
        )
    }
}
