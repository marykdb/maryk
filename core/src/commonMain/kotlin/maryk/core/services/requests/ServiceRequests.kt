package maryk.core.services.requests

import maryk.core.exceptions.DefNotFoundException
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.MultiTypeDefinition
import maryk.core.properties.enum.MultiTypeEnumDefinition
import maryk.core.properties.types.TypedValue
import maryk.core.query.ContainsDefinitionsContext
import maryk.core.services.ServiceDataModel
import kotlin.reflect.KClass

/** Describes service requests for communication */
class ServiceRequests(
    private val requestsMap: Map<UInt, ServiceDataModel<*, *>>
) : MultiTypeEnumDefinition<ServiceRequestType<out IsServiceRequest>>(
    ServiceRequestType::class,
    values = {
        requestsMap.map { entry ->
            ServiceRequestType(entry.key, EmbeddedObjectDefinition(dataModel = {
                @Suppress("UNCHECKED_CAST")
                entry.value as ServiceDataModel<IsServiceRequest, ObjectPropertyDefinitions<IsServiceRequest>>
            }))
        }.toTypedArray()
    },
    unknownCreator = ::UnknownServiceRequest
) {
    val definition: MultiTypeDefinition<ServiceRequestType<*>, IsServiceRequest> = MultiTypeDefinition(typeEnum = this)

    private val valuesByClass = this.cases().map {
        Pair(it.definition!!.dataModel.serviceClass, it)
    }.toMap()

    fun <ISR: IsServiceRequest> getTypedRequest(request: ISR): TypedValue<ServiceRequestType<ISR>, ISR> {
        @Suppress("UNCHECKED_CAST")
        val value = this.valuesByClass[request::class as KClass<IsServiceRequest>] as ServiceRequestType<ISR>?
            ?: throw DefNotFoundException("Cannot find Service Type for $request, was it registered in requests map?")

        return TypedValue(value, request)
    }

    fun toTransportByteArray(request: IsServiceRequest, context: ContainsDefinitionsContext? = null): ByteArray {
        val typedRequest = this.getTypedRequest(request)

        return this.definition.toTransportByteArray(typedRequest, context)
    }
}
