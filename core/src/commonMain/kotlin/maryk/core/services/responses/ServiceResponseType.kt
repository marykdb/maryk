package maryk.core.services.responses

import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.enum.IndexedEnumImpl
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.services.ServiceDataModel

/** An indexed service request type so instances can be identified in serialized transport */
open class ServiceResponseType<ISR: IsServiceResponse>(
    index: UInt,
    override val definition: EmbeddedObjectDefinition<ISR, ObjectPropertyDefinitions<ISR>, ServiceDataModel<ISR, ObjectPropertyDefinitions<ISR>>, *, *>?
) : IndexedEnumImpl<ServiceResponseType<ISR>>(index), MultiTypeEnum<ISR>
