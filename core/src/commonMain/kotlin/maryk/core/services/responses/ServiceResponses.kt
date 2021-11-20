package maryk.core.services.responses

import maryk.core.exceptions.DefNotFoundException
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.enum.MultiTypeEnumDefinition
import maryk.core.properties.types.TypedValue
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.services.ServiceDataModel
import kotlin.reflect.KClass

/** Describes service responses for communication */
class ServiceResponses(
    private val responsesMap: Map<UInt, ServiceDataModel<*, *>>
) : MultiTypeEnumDefinition<ServiceResponseType<out IsServiceResponse>>(
    ServiceResponseType::class,
    values = {
        responsesMap.map { entry ->
            ServiceResponseType(entry.key, EmbeddedObjectDefinition(dataModel = {
                @Suppress("UNCHECKED_CAST")
                entry.value as ServiceDataModel<IsServiceResponse, ObjectPropertyDefinitions<IsServiceResponse>>
            }))
        }.toTypedArray()
    },
    unknownCreator = ::UnknownServiceResponse
) {
    val definition: MultiTypeDefinition<ServiceResponseType<*>, IsServiceResponse> = MultiTypeDefinition(typeEnum = this)

    private val valuesByClass = this.cases().associateBy {
        it.definition!!.dataModel.serviceClass
    }

    fun <ISR: IsServiceResponse> getTypedResponse(response: ISR): TypedValue<ServiceResponseType<ISR>, ISR> {
        @Suppress("UNCHECKED_CAST")
        val value = this.valuesByClass[response::class as KClass<IsServiceResponse>] as ServiceResponseType<ISR>?
            ?: throw DefNotFoundException("Cannot find Service Type for $response, was it registered in responses map?")

        return TypedValue(value, response)
    }

    fun toTransportByteArray(response: IsServiceResponse, context: ContainsDefinitionsContext? = null): ByteArray {
        val typedResponse = this.getTypedResponse(response)

        return this.definition.toTransportByteArray(typedResponse, context)
    }
}
