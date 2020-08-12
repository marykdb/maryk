package maryk.core.services

import maryk.core.models.ObjectDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import kotlin.reflect.KClass

/**
 * A data model describing a service packet
 */
abstract class ServiceDataModel<DO: IsServicePacket, P: ObjectPropertyDefinitions<DO>>(
    val serviceClass: KClass<DO>,
    properties: P
) : ObjectDataModel<DO, P>(serviceClass.simpleName!!, properties)
