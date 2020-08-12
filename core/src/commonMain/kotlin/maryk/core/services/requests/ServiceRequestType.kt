package maryk.core.services.requests

import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.enum.IndexedEnumImpl
import maryk.core.properties.enum.MultiTypeEnum
import maryk.core.services.ServiceDataModel

/** An indexed service request type so instances can be identified in serialized transport */
open class ServiceRequestType<ISR: IsServiceRequest>(
    index: UInt,
    override val definition: EmbeddedObjectDefinition<ISR, ObjectPropertyDefinitions<ISR>, ServiceDataModel<ISR, ObjectPropertyDefinitions<ISR>>, *, *>?
) : IndexedEnumImpl<ServiceRequestType<ISR>>(index), MultiTypeEnum<ISR>
