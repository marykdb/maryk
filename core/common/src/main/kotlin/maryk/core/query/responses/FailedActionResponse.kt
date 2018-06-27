package maryk.core.query.responses

import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.DataObjectMap
import maryk.core.properties.definitions.EnumDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.StringDefinition
import maryk.core.properties.enum.IndexedEnum
import maryk.core.properties.enum.IndexedEnumDefinition

/** Types of failures */
enum class FailType(
    override val index: Int
) : IndexedEnum<FailType> {
    CONNECTION(0), // Problems with Connection at the server
    STORE_STATE(1), // Problems with the state of the store
    REQUEST(2), // Problems with the request content
    AUTH(3); // Problems with the Authentication

    companion object: IndexedEnumDefinition<FailType>("FailType", FailType::values)
}

/** Response with [message] and [failType] for failed actions. */
data class FailedActionResponse(
    val message: String,
    val failType: FailType
) : IsResponse {
    internal companion object: SimpleQueryDataModel<FailedActionResponse>(
        properties = object : PropertyDefinitions<FailedActionResponse>() {
            init {
                add(0, "message", StringDefinition(), FailedActionResponse::message)
                add(1, "failType", EnumDefinition(enum = FailType), FailedActionResponse::failType)
            }
        }
    ) {
        override fun invoke(map: DataObjectMap<FailedActionResponse>) = FailedActionResponse(
            message = map(0),
            failType = map(1)
        )
    }
}
