package maryk.core.models

import maryk.core.models.definitions.ObjectDataModelDefinition
import maryk.core.properties.IsPropertyContext
import kotlin.reflect.KClass

/**
 * Object Data Model for defining custom Data Models which can serialize
 * to and from Kotlin objects of type [DO].
 */
abstract class ObjectDataModel<DO: Any, DM: IsObjectDataModel<DO>>(
    objClass: KClass<DO>,
): TypedObjectDataModel<DO, DM, IsPropertyContext, IsPropertyContext>(),
    IsStorableDataModel<DO> {
    override val Meta = ObjectDataModelDefinition(objClass.simpleName!!)
}
